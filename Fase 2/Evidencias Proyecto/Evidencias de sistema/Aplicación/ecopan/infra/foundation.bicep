// ============================================================================================
// PHASE 1 — foundation: everything that must exist before any image can be built or run.
//
// Split from apps.bicep because of an ordering constraint, not tidiness: the web apps need images,
// images need a registry to be pushed to, and the registry is created here. One template would
// have to invent a placeholder image for the first deploy and then be re-run — two phases state
// the dependency honestly and let `deploy.sh` build in between.
//
// Scope is the resource group. deploy.sh creates it, so a full teardown is one `az group delete`
// with nothing stranded at subscription scope.
// ============================================================================================

targetScope = 'resourceGroup'

// Brazil South, not Chile Central. Chile Central is nearer the client and ~32% cheaper for
// PostgreSQL, but the tenant carries a management-group policy ("Ubicaciones permitidas" / Allowed
// locations) whose allowlist excludes it, so every resource is denied at deploy time. Verified by
// running `az deployment group validate` against this template — it compiled fine and failed on
// policy, which is why the region default is what it is.
@description('Azure region. Brazil South by default; Chile Central is blocked by tenant policy (see infra/README.md).')
param location string = resourceGroup().location

@description('Short name stem for all resources. Must be lowercase alphanumeric — it seeds the globally-unique ACR and Service Bus names.')
@minLength(3)
@maxLength(12)
param namePrefix string = 'ecopan'

@description('PostgreSQL administrator login.')
param postgresAdminUser string = 'ecopanadmin'

@description('PostgreSQL administrator password. Never committed — deploy.sh generates or prompts for it.')
@secure()
param postgresAdminPassword string

@description('PostgreSQL SKU. B1ms (1 vCore burstable) is the cheapest tier that supports Flexible Server.')
param postgresSkuName string = 'Standard_B1ms'

@description('Storage in GB. 32 is the floor for Flexible Server.')
param postgresStorageGb int = 32

@description('Deliveries before Service Bus dead-letters a message by itself. Must match SERVICE_BUS_MAX_DELIVERY_COUNT on the apps.')
param maxDeliveryCount int = 5

// Deterministic suffix so re-running the template is idempotent, while the two resources that need
// globally unique names (ACR, Service Bus) do not collide with another student's `ecopanacr`.
var suffix = uniqueString(resourceGroup().id)
var registryName = '${namePrefix}acr${suffix}'
var databaseName = 'ecopan'

// Entity names deliberately identical to the RabbitMQ queue names in docker-compose, so a message
// flowing through the local stack and the Azure stack is traceable under the same name in both.
// Service Bus permits '.' in entity names, which is what makes that possible.
var workQueueNames = [
  'production'
  'batch.events'
  'forecast.refresh'
]
var sseTopicName = 'notifications.sse'

// ---------------------------------------------------------------------------------------------
// Container registry
// ---------------------------------------------------------------------------------------------
// Basic SKU: $0.1666/day (~$5/mo), 10 GiB included — far beyond what four small images need.
// adminUserEnabled is the deliberate cheap path: App Service pulls with the admin username and
// password stored in app settings. The better practice is a managed identity with AcrPull, which
// costs nothing extra but needs a role assignment, and role assignment requires Owner/User Access
// Administrator on the subscription. Azure for Students accounts frequently cannot do that, and a
// deployment that fails on RBAC is worse than one that uses a registry password. Recorded as a
// known tradeoff in infra/README.md.
resource registry 'Microsoft.ContainerRegistry/registries@2025-11-01' = {
  name: registryName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: true
  }
}

// ---------------------------------------------------------------------------------------------
// Service Bus — the managed broker
// ---------------------------------------------------------------------------------------------
// Standard, not Basic, and the reason is the SSE fanout. Basic supports queues only; topics and
// subscriptions — which is how one notifier broadcast reaches every app instance — start at
// Standard. Standard is $10/month flat with 12.5M operations included, and this workload will not
// come close to that ceiling.
//
// Premium ($0.93/hour, ~$677/month) buys dedicated capacity, VNet integration and predictable
// latency. Nothing here needs any of it.
resource serviceBus 'Microsoft.ServiceBus/namespaces@2024-01-01' = {
  name: '${namePrefix}-sb-${suffix}'
  location: location
  sku: {
    name: 'Standard'
    tier: 'Standard'
  }
  properties: {
    minimumTlsVersion: '1.2'
    // SAS keys stay enabled: the apps authenticate with a namespace connection string rather than
    // a managed identity, for the same Owner/UAA reason as the registry above.
    disableLocalAuth: false
    publicNetworkAccess: 'Enabled'
  }
}

// The three work queues. Each is durable, competing-consumer and dead-lettering — the same
// contract the RabbitMQ implementation builds by hand out of an exchange, a queue, a dead-letter
// exchange and a dead-letter queue. Here the DLQ is implicit: every queue has one.
resource queues 'Microsoft.ServiceBus/namespaces/queues@2024-01-01' = [
  for queueName in workQueueNames: {
    parent: serviceBus
    name: queueName
    properties: {
      // Held for the whole of the handler, and a formula decrement touches several rows.
      // PT5M is the Service Bus maximum.
      lockDuration: 'PT5M'
      maxDeliveryCount: maxDeliveryCount
      deadLetteringOnMessageExpiration: true
      // Two weeks. Long enough that a worker outage over a weekend loses nothing; short enough
      // that a forgotten queue cannot grow without bound.
      defaultMessageTimeToLive: 'P14D'
      requiresDuplicateDetection: false
      requiresSession: false
      enablePartitioning: false
    }
  }
]

// The SSE fanout topic. Subscriptions are *not* declared here: each backend-app instance creates
// its own at startup, named after a random id, and deletes it on shutdown. That is what makes the
// broadcast reach every instance without shared state — see ServiceBusSsePushBroker.
resource sseTopic 'Microsoft.ServiceBus/namespaces/topics@2024-01-01' = {
  parent: serviceBus
  name: sseTopicName
  properties: {
    // Stale pushes are worse than missing ones; the persisted tray is the durable record.
    defaultMessageTimeToLive: 'PT5M'
    enablePartitioning: false
  }
}

// The namespace-level SAS rule ARM creates automatically. Read rather than declared, so the
// template does not fight the platform over ownership of a built-in resource.
//
// It grants Manage, which is more than a pure producer/consumer needs, and that is a consequence
// of the fanout design rather than laziness: creating and deleting per-instance subscriptions at
// runtime *is* a management operation. A Send+Listen rule would be tighter and would break SSE
// scale-out. Noted in infra/README.md as a deliberate tradeoff.
resource serviceBusAuthRule 'Microsoft.ServiceBus/namespaces/authorizationRules@2024-01-01' existing = {
  parent: serviceBus
  name: 'RootManageSharedAccessKey'
}

// ---------------------------------------------------------------------------------------------
// PostgreSQL Flexible Server
// ---------------------------------------------------------------------------------------------
// Burstable B1ms, single AZ, no HA, no read replicas. Public endpoint with a firewall allowlist
// rather than a private endpoint: private networking would need a VNet, a private DNS zone and
// VNet integration on the App Service Plan, which triples the resource count, forces the plan up
// to Standard tier, and adds cost for zero capstone value.
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2025-08-01' = {
  name: '${namePrefix}-pg-${suffix}'
  location: location
  sku: {
    name: postgresSkuName
    tier: 'Burstable'
  }
  properties: {
    version: '17'
    administratorLogin: postgresAdminUser
    administratorLoginPassword: postgresAdminPassword
    storage: {
      storageSizeGB: postgresStorageGb
      // Autogrow OFF on purpose. Storage can only ever grow, and each step up is permanent and
      // billable — on a credit-limited subscription an unattended autogrow is a one-way cost
      // increase. 32 GB is ample for a demo dataset.
      autoGrow: 'Disabled'
    }
    backup: {
      // 7 = the minimum. Backup storage equal to the DB size is free; beyond that it bills.
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }

  resource database 'databases' = {
    name: databaseName
    properties: {
      charset: 'UTF8'
      collation: 'en_US.utf8'
    }
  }
}

// Allows other Azure services — which is how the App Service Plan reaches the database without a
// VNet. It is broader than one would want in production (any Azure tenant's outbound IPs satisfy
// it), which is precisely why the database password is a generated secret and the server holds
// nothing but demo data. deploy.sh adds a second rule for the operator's own IP so psql and
// migrations work from a laptop.
resource allowAzureServices 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2025-08-01' = {
  parent: postgres
  name: 'AllowAllAzureServicesAndResourcesWithinAzureIps'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// ---------------------------------------------------------------------------------------------
// Storage — product photos
// ---------------------------------------------------------------------------------------------
// One general-purpose v2 account, locally redundant (a photo is re-uploadable, not a ledger), hot
// tier because every till and the dashboard read these constantly. The app never touches the
// account key: `allowSharedKeyAccess: false` turns keys off at the account, and apps.bicep gives
// the backend's managed identity the two data-plane roles it needs (Blob Data Contributor to
// delete, Blob Delegator to sign user-delegation SAS for client uploads). Clients upload to and
// read from the account directly -- the API only mints short-lived write grants.
//
// Public *read* on the one container is deliberate and scoped: product photos are shown to every
// customer at the till and the dashboard's <img> tags carry no token, so the container is
// `Blob`-level anonymous read (list is still denied). Nothing else on the account is public.
// Storage account names are 3-24 lowercase alphanumerics; the prefix may be up to 12, so clip.
var storageAccountName = take(toLower('${namePrefix}st${suffix}'), 24)
var productImagesContainer = 'product-images'

resource storage 'Microsoft.Storage/storageAccounts@2025-01-01' = {
  name: storageAccountName
  location: location
  kind: 'StorageV2'
  sku: {
    name: 'Standard_LRS'
  }
  properties: {
    accessTier: 'Hot'
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    allowSharedKeyAccess: false
    // Account-level switch that *permits* a container to opt into anonymous read; the container
    // below is the only one that does.
    allowBlobPublicAccess: true
    allowCrossTenantReplication: false
    defaultToOAuthAuthentication: true
    publicNetworkAccess: 'Enabled'
    networkAcls: {
      defaultAction: 'Allow'
      bypass: 'AzureServices'
    }
  }

  resource blobServices 'blobServices' = {
    name: 'default'
    properties: {
      deleteRetentionPolicy: {
        // A photo deleted by mistake can be restored for a week; costs nothing at this volume.
        enabled: true
        days: 7
      }
    }

    resource productImages 'containers' = {
      name: productImagesContainer
      properties: {
        publicAccess: 'Blob'
      }
    }
  }
}

output registryName string = registry.name
output storageAccountName string = storage.name
output storageBlobEndpoint string = storage.properties.primaryEndpoints.blob
output productImagesContainer string = productImagesContainer
output registryLoginServer string = registry.properties.loginServer
output postgresFqdn string = postgres.properties.fullyQualifiedDomainName
output postgresServerName string = postgres.name
output postgresDatabaseName string = databaseName
output serviceBusNamespace string = serviceBus.name

@secure()
output serviceBusConnectionString string = serviceBusAuthRule.listKeys().primaryConnectionString
