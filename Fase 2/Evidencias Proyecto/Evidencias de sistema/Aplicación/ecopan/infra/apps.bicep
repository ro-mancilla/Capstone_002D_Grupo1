// ============================================================================================
// PHASE 2 — four Linux container web apps on one App Service Plan, deployed once images exist.
//
//   ecopan-app       public HTTPS + SSE   the API; the only app anyone browses to. Holds the
//                                         managed identity that signs product-photo uploads
//   ecopan-worker    health port only     Service Bus consumer; decrements stock, calls ML
//   ecopan-notifier  health port only     aggregates batch progress, publishes SSE pushes
//   ecopan-ml        HTTPS + API key      FastAPI forecast service, called by the worker
//
// The thing that makes App Service cheap here, and the reason it is worth preferring: **a plan is
// billed per plan, not per app.** Four apps on one B2 is $29.93/month total. Container Apps bills
// every replica separately and would run $22–73 for the same four, plus a fifth replica for a
// self-hosted broker. Moving the broker to Service Bus is what unlocked that — see infra/README.md.
//
// Two App Service facts drive most of the shapes below:
//
//   1. A Linux container app is polled on its port at startup and restarted if nothing answers.
//      The worker and the notifier are headless queue consumers with no HTTP surface, so they run
//      a ten-line health listener purely to satisfy this (see backend/data ... HealthEndpoint.kt).
//   2. Exactly one port per container is exposed, and only over HTTP. That is fine for all four —
//      and it is precisely what made a self-hosted RabbitMQ on AMQP 5672 impossible here.
// ============================================================================================

targetScope = 'resourceGroup'

param location string = resourceGroup().location
param namePrefix string = 'ecopan'

@description('ACR login server, e.g. ecopanacrxxxx.azurecr.io')
param registryLoginServer string

@description('ACR resource name, used to read the admin credentials.')
param registryName string

@description('Image tag applied to all four images — the git SHA, so a deploy is traceable to a commit.')
param imageTag string

@description('App Service Plan SKU. B1 is cheaper but has 1.75 GB for three JVMs and a LightGBM process; B2 (3.5 GB) is the realistic floor. See infra/README.md.')
@allowed([
  'B1'
  'B2'
  'B3'
])
param planSku string = 'B2'

param postgresFqdn string
param postgresDatabaseName string
param postgresAdminUser string

@secure()
param postgresAdminPassword string

@secure()
param jwtSecret string

@secure()
param serviceBusConnectionString string

@secure()
param mlApiKey string

@description('Comma-separated browser origins allowed to call the API (the Static Web App URL). Empty on the first deploy, filled by deploy.sh once the SWA hostname exists.')
param corsAllowedOrigins string = ''

param seedProfile string = 'reference'
param reportingTimezone string = 'America/Santiago'

@description('Storage account created by foundation.bicep that holds product photos.')
param storageAccountName string

@description('Comma-separated browser origins allowed to upload to blob storage directly (the Static Web App URL). Same value as corsAllowedOrigins; empty on the first deploy.')
param storageCorsAllowedOrigins string = ''

var mlAppName = '${namePrefix}-ml'
var backendAppName = '${namePrefix}-app'

resource registry 'Microsoft.ContainerRegistry/registries@2025-11-01' existing = {
  name: registryName
}

resource storage 'Microsoft.Storage/storageAccounts@2025-01-01' existing = {
  name: storageAccountName
}

// Built-in role ids (https://learn.microsoft.com/azure/role-based-access-control/built-in-roles).
// Data Contributor: read/write/delete blobs. Delegator: obtain user-delegation keys, which is what
// signs the per-upload SAS the API hands to clients. Neither grants anything at account level.
var storageBlobDataContributorRoleId = 'ba92f5b4-2d11-453d-a403-e96b0029c9fe'
var storageBlobDelegatorRoleId = 'db58b8e5-c6ad-4a2a-8342-4190687cbf4a'

// ---------------------------------------------------------------------------------------------
// The plan
// ---------------------------------------------------------------------------------------------
// `reserved: true` is what makes a plan Linux. It is not optional and it is not inferable from
// `kind` — a plan created without it silently becomes a Windows plan that then refuses every
// Linux container app assigned to it.
//
// Basic tier, one instance. Basic is the cheapest tier that offers `alwaysOn`, and `alwaysOn` is
// mandatory here: without it App Service unloads an app after 20 minutes idle and only wakes it on
// an inbound HTTP request. An HTTP API survives that. A queue consumer does not — the worker would
// be unloaded overnight and no message would ever wake it, because messages do not arrive over
// HTTP. Free and Shared tiers are therefore unusable for this stack at any price.
resource plan 'Microsoft.Web/serverfarms@2024-11-01' = {
  name: '${namePrefix}-plan'
  location: location
  kind: 'linux'
  sku: {
    name: planSku
    tier: 'Basic'
    capacity: 1
  }
  properties: {
    reserved: true
    // All four apps share the single instance's CPU and memory rather than each getting a slice.
    // That is the whole economy of this design, and also its main constraint: see the JVM heap
    // caps below, which exist because 3.5 GB does not divide comfortably four ways by default.
    perSiteScaling: false
    zoneRedundant: false
  }
}

// Registry credentials, repeated into every app's settings. `listCredentials()` runs at deploy
// time; the value lands in app settings, never in a parameter file.
var registrySettings = [
  {
    name: 'DOCKER_REGISTRY_SERVER_URL'
    value: 'https://${registryLoginServer}'
  }
  {
    name: 'DOCKER_REGISTRY_SERVER_USERNAME'
    value: registry.listCredentials().username
  }
  {
    name: 'DOCKER_REGISTRY_SERVER_PASSWORD'
    value: registry.listCredentials().passwords[0].value
  }
  {
    // Off. Left on, App Service mounts /home on shared storage for every app, which costs startup
    // time and buys nothing: these containers are stateless and write no files worth keeping.
    name: 'WEBSITES_ENABLE_APP_SERVICE_STORAGE'
    value: 'false'
  }
]

// Database and broker coordinates, identical for app, worker and notifier. Built once so the three
// cannot drift apart — the class of bug that has already cost this project a day when two sides of
// a contract were maintained by hand.
// The app (reporting.tz <- REPORTING_TZ) and the worker (REPORTING_TZ) must bucket days in the same
// zone, or the forecast-vs-actual join silently misaligns (#139). One parameter, one setting, both
// apps: there is no way to override one without the other.
var reportingSettings = [
  {
    name: 'REPORTING_TZ'
    value: reportingTimezone
  }
]

var sharedSettings = concat(registrySettings, [
  {
    name: 'DB_HOST'
    value: postgresFqdn
  }
  {
    name: 'DB_PORT'
    value: '5432'
  }
  {
    name: 'DB_NAME'
    value: postgresDatabaseName
  }
  {
    name: 'DB_USERNAME'
    value: postgresAdminUser
  }
  {
    name: 'DB_PASSWORD'
    value: postgresAdminPassword
  }
  // The switch. Everything else about the broker follows from this one setting: with it absent or
  // set to `rabbitmq` these same images look for an AMQP 0-9-1 host, which is exactly what they do
  // under docker-compose. Same build, same tag, different broker.
  {
    name: 'BROKER_BACKEND'
    value: 'service_bus'
  }
  {
    name: 'SERVICE_BUS_CONNECTION_STRING'
    value: serviceBusConnectionString
  }
  // Off: foundation.bicep already declared the queues and the topic, so the app has nothing to
  // provision. Infrastructure owns infrastructure.
  {
    name: 'SERVICE_BUS_PROVISION_TOPOLOGY'
    value: 'false'
  }
  {
    name: 'SERVICE_BUS_MAX_DELIVERY_COUNT'
    value: '5'
  }
])

// ---------------------------------------------------------------------------------------------
// backend-app — the only service anyone browses to
// ---------------------------------------------------------------------------------------------
resource backendApp 'Microsoft.Web/sites@2024-11-01' = {
  name: backendAppName
  location: location
  kind: 'app,linux,container'
  // The app's own identity is its storage credential -- see the role assignments below. Only the
  // API needs one; the worker and notifier never touch photos.
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'DOCKER|${registryLoginServer}/ecopan/backend-app:${imageTag}'
      // Ktor loads application modules under a 10 s timeout by default (ktor.application.startupTimeoutMillis),
      // and this app's module does the schema setup and the seed load inside it. The first boot against an
      // empty sales table inserts ~13,000 sales over the network and cannot finish in 10 s ("Timed out
      // waiting for 10000 ms", then a crash loop). Locally the database is on localhost, which hid it.
      // A launch argument rather than a yaml entry: the Dockerfile's ENTRYPOINT is `java -jar`, and App
      // Service passes this as its arguments.
      appCommandLine: '-P:ktor.application.startupTimeoutMillis=180000'
      alwaysOn: true
      ftpsState: 'Disabled'
      minTlsVersion: '1.2'
      healthCheckPath: '/health'
      // SSE is a long-lived chunked HTTP/1.1 response. HTTP/2.0 would multiplex it fine, but
      // App Service's HTTP/2 support and Ktor's SSE writer have no reason to be exercised
      // together for the first time during a defence.
      http20Enabled: false
      // The SSE connection registry is in-process, so a client must keep landing on the instance
      // that owns its connection. The plan runs one instance, which is the actual guarantee;
      // affinity is set so the intent survives someone later scaling the plan out.
      // (Scaling out for real means moving the registry behind a Service Bus topic — the same
      // fanout the notifier already uses. Out of capstone scope, and a known limit.)
      appSettings: concat(sharedSettings, reportingSettings, [
        {
          name: 'WEBSITES_PORT'
          value: '8080'
        }
        {
          // Echoed as the X-Build-Id header on /health, so deploy.sh can tell the new container from the
          // previous one, which keeps serving while the new one boots.
          name: 'BUILD_ID'
          value: imageTag
        }
        {
          // App Service gives a container 230 s to answer its warm-up probe by default and then kills it.
          // The first boot seeds the sales history (see appCommandLine above), so allow it room.
          name: 'WEBSITES_CONTAINER_START_TIME_LIMIT'
          value: '600'
        }
        {
          // Cap the heap explicitly. App Service does not cgroup-limit each app separately — all
          // four share the plan instance's 3.5 GB — so a JVM left to size itself from /proc would
          // claim a quarter of the machine as *its* share and the four of them would overcommit.
          name: 'JAVA_TOOL_OPTIONS'
          value: '-Xmx640m -XX:MaxMetaspaceSize=192m'
        }
        {
          name: 'JWT_SECRET'
          value: jwtSecret
        }
        {
          name: 'JWT_ISSUER'
          value: 'ecopan'
        }
        {
          // App Service's front end appends the real client to X-Forwarded-For; the auth rate
          // limit keys on it only when this is on (#129).
          name: 'BEHIND_TRUSTED_PROXY'
          value: 'true'
        }
        {
          name: 'JWT_AUDIENCE'
          value: 'ecopan-clients'
        }
        {
          name: 'ACCESS_TOKEN_TTL_MIN'
          value: '15'
        }
        {
          name: 'REFRESH_TOKEN_TTL_DAYS'
          value: '30'
        }
        {
          name: 'DEVICE_SESSION_TTL_DAYS'
          value: '180'
        }
        {
          name: 'SEED_PROFILE'
          value: seedProfile
        }
        {
          name: 'CORS_ALLOWED_ORIGINS'
          value: corsAllowedOrigins
        }
        {
          // Managed-identity path: no connection string anywhere. The public base URL defaults to
          // this same endpoint inside the app, so nothing else is needed.
          name: 'STORAGE_ACCOUNT_URL'
          value: storage.properties.primaryEndpoints.blob
        }
        {
          name: 'STORAGE_CONTAINER'
          value: 'product-images'
        }
      ])
    }
    clientAffinityEnabled: true
  }
}

// The two data-plane grants that make the identity above a storage credential. Scoped to the one
// account, not the resource group. Names are deterministic (guid of app+account+role) so a
// re-deploy is a no-op rather than a duplicate. Needs Owner or User Access Administrator on the
// subscription to create -- the same RBAC caveat infra/README.md records for the registry.
resource backendBlobContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(storage.id, backendApp.id, storageBlobDataContributorRoleId)
  scope: storage
  properties: {
    principalId: backendApp.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageBlobDataContributorRoleId)
  }
}

resource backendBlobDelegator 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(storage.id, backendApp.id, storageBlobDelegatorRoleId)
  scope: storage
  properties: {
    principalId: backendApp.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageBlobDelegatorRoleId)
  }
}

// Browsers upload straight to the storage account, so the account -- not just the API -- must
// answer the dashboard origin's CORS preflight. Declared here rather than in foundation.bicep
// because the origin is the Static Web App hostname, which only exists once deploy.sh has created
// it; on the first run the list is empty and the second deploy fills it, exactly like the API's.
resource storageCors 'Microsoft.Storage/storageAccounts/blobServices@2025-01-01' = {
  parent: storage
  name: 'default'
  properties: {
    cors: {
      corsRules: empty(storageCorsAllowedOrigins) ? [] : [
        {
          allowedOrigins: split(storageCorsAllowedOrigins, ',')
          allowedMethods: [
            'GET'
            'HEAD'
            'PUT'
            'OPTIONS'
          ]
          allowedHeaders: [
            '*'
          ]
          exposedHeaders: [
            '*'
          ]
          maxAgeInSeconds: 3600
        }
      ]
    }
    deleteRetentionPolicy: {
      enabled: true
      days: 7
    }
  }
}

// ---------------------------------------------------------------------------------------------
// backend-worker — a queue consumer that App Service insists on treating as a web app
// ---------------------------------------------------------------------------------------------
resource backendWorker 'Microsoft.Web/sites@2024-11-01' = {
  name: '${namePrefix}-worker'
  location: location
  kind: 'app,linux,container'
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'DOCKER|${registryLoginServer}/ecopan/backend-worker:${imageTag}'
      alwaysOn: true
      ftpsState: 'Disabled'
      minTlsVersion: '1.2'
      healthCheckPath: '/health'
      appSettings: concat(sharedSettings, reportingSettings, [
        {
          // Answers only the platform's startup probe. The consumer loop is the real work and it
          // does not go through here.
          name: 'WEBSITES_PORT'
          value: '8081'
        }
        {
          name: 'JAVA_TOOL_OPTIONS'
          value: '-Xmx512m -XX:MaxMetaspaceSize=160m'
        }
        {
          // The ML service over the public internet with a bearer key, because App Service
          // app-to-app traffic has no private path below Standard tier + VNet integration. The
          // network restriction that stands in for it is applied by deploy.sh — see the note in
          // infra/README.md, this is the one real security regression from Container Apps.
          name: 'ML_BASE_URL'
          value: 'https://${mlAppName}.azurewebsites.net'
        }
        {
          name: 'ML_API_KEY'
          value: mlApiKey
        }
        {
          name: 'DECREMENT_ENABLED'
          value: 'true'
        }
        {
          // Bounded concurrency: at most this many batches processed in parallel. Matches the
          // AMQP prefetch the RabbitMQ path uses, so the two backends behave the same under load.
          name: 'SERVICE_BUS_MAX_CONCURRENT_CALLS'
          value: '4'
        }
      ])
    }
    clientAffinityEnabled: false
  }
}

// ---------------------------------------------------------------------------------------------
// backend-notifier — aggregates batch progress and publishes SSE pushes
// ---------------------------------------------------------------------------------------------
resource backendNotifier 'Microsoft.Web/sites@2024-11-01' = {
  name: '${namePrefix}-notifier'
  location: location
  kind: 'app,linux,container'
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'DOCKER|${registryLoginServer}/ecopan/backend-notifier:${imageTag}'
      alwaysOn: true
      ftpsState: 'Disabled'
      minTlsVersion: '1.2'
      healthCheckPath: '/health'
      appSettings: concat(sharedSettings, [
        {
          name: 'WEBSITES_PORT'
          value: '8081'
        }
        {
          name: 'JAVA_TOOL_OPTIONS'
          value: '-Xmx384m -XX:MaxMetaspaceSize=128m'
        }
        {
          // Single-threaded on purpose. Batch-progress aggregation keeps per-batch counters in
          // memory; two concurrent handlers would interleave counts for the same batch and
          // progress would jump around or completion would fire twice. The plan runs one instance,
          // so serialising here makes the aggregation genuinely single-writer.
          name: 'SERVICE_BUS_MAX_CONCURRENT_CALLS'
          value: '1'
        }
      ])
    }
    clientAffinityEnabled: false
  }
}

// ---------------------------------------------------------------------------------------------
// ml-service — FastAPI, called only by the worker
// ---------------------------------------------------------------------------------------------
resource mlService 'Microsoft.Web/sites@2024-11-01' = {
  name: mlAppName
  location: location
  kind: 'app,linux,container'
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'DOCKER|${registryLoginServer}/ecopan/ml-service:${imageTag}'
      // On, despite nothing browsing here: a cold LightGBM import plus model load is slow enough
      // that the worker's first forecast call after an idle night would time out.
      alwaysOn: true
      ftpsState: 'Disabled'
      minTlsVersion: '1.2'
      healthCheckPath: '/health'
      appSettings: concat(registrySettings, [
        {
          name: 'WEBSITES_PORT'
          value: '8000'
        }
        {
          name: 'ML_API_KEY'
          value: mlApiKey
        }
        {
          name: 'ML_ARTIFACT_DIR'
          value: '/app/data/artifacts'
        }
      ])
      // Locked down by deploy.sh to the plan's own outbound IPs once they are known — all four
      // apps share them, so "only this plan may call the ML service" is expressible without a
      // VNet. Declared empty here rather than omitted so the intent is visible in the template.
      ipSecurityRestrictions: []
    }
    clientAffinityEnabled: false
  }
}

output backendAppName string = backendApp.name
output backendAppUrl string = 'https://${backendApp.properties.defaultHostName}'
output backendAppFqdn string = backendApp.properties.defaultHostName
output mlAppName string = mlService.name
output workerName string = backendWorker.name
output notifierName string = backendNotifier.name
output planName string = plan.name
