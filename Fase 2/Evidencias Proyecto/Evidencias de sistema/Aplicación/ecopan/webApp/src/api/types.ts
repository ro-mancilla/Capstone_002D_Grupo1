/**
 * Wire DTOs, hand-mirrored against the real backend Kotlin DTOs (ground truth, not plan 07 §9's
 * summary table) -- see:
 *   backend/app/src/main/kotlin/com/f776/ecopan/api/feature/auth/controller/dto/AuthDtos.kt
 *   backend/app/src/main/kotlin/com/f776/ecopan/api/feature/tenant/controller/dto/TenantDtos.kt
 *   backend/app/src/main/kotlin/com/f776/ecopan/api/core/Auth.kt (ErrorBody)
 *   backend/data/src/main/kotlin/com/f776/ecopan/data/domain/Enums.kt (Role/Permission/Module)
 *
 * Conventions (backend-mirrored, not approximated): integers for money/weight/bp, ISO-8601
 * strings for instants, string UUIDs. Display-unit conversions live only in `lib/format.ts`
 * (items 4+), never here.
 *
 * `erasableSyntaxOnly` (tsconfig) forbids real TS `enum`s -- string literal unions instead,
 * which also serialize identically to the Kotlin enums' `.name` wire form.
 */

// -- data/domain/Enums.kt --------------------------------------------------

export type Role = "CASHIER" | "BAKER" | "BRANCH_MANAGER" | "GENERAL_MANAGER" | "ADMIN"

export type ModuleId =
  | "POS_SALES"
  | "INVENTORY_FORMULAS"
  | "DEMAND_FORECASTING"
  | "MULTI_BRANCH_ROLES"
  | "ANALYTICS_DASHBOARD"

export type Permission =
  | "SALES_RECORD"
  | "BATCH_RECORD"
  | "STOCK_ADJUST"
  | "STOCK_READ"
  | "CATALOG_READ"
  | "CATALOG_WRITE"
  | "ANALYTICS_READ"
  | "FORECAST_READ"
  | "USERS_MANAGE"
  | "MODULES_TOGGLE"
  | "SYNC"

/** Roles allowed to use this manager dashboard at all (plan 07 §3). CASHIER/BAKER are rejected
 * client-side after a successful login -- they belong to the POS/baker apps. */
export const MANAGER_APP_ROLES: readonly Role[] = [
  "BRANCH_MANAGER",
  "GENERAL_MANAGER",
  "ADMIN",
]

// -- auth/controller/dto/AuthDtos.kt ----------------------------------------

export interface LoginRequest {
  tenantSlug: string
  username: string
  password: string
  deviceId?: string
  deviceName?: string
}

export interface UserSummaryDto {
  id: string
  displayName: string
  role: Role
  branchId: string | null
}

export interface TenantSummaryDto {
  id: string
  name: string
  currencyCode: string
  enabledModules: ModuleId[]
}

export interface BranchSummaryDto {
  id: string
  name: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: UserSummaryDto
  tenant: TenantSummaryDto
  branches: BranchSummaryDto[]
}

export interface RefreshRequest {
  refreshToken: string
}

export interface RefreshResponse {
  accessToken: string
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken: string
}

// -- tenant/controller/dto/TenantDtos.kt ------------------------------------

export interface BranchDto {
  id: string
  name: string
  active: boolean
}

export interface TenantProfileResponse {
  id: string
  name: string
  slug: string
  currencyCode: string
  enabledModules: ModuleId[]
  branches: BranchDto[]
}

export interface UpdateModulesRequest {
  enabledModules: ModuleId[]
}

export interface UpsertBranchRequest {
  name: string
  active?: boolean
}

export interface UserListItemDto {
  id: string
  username: string
  displayName: string
  role: Role
  branchId: string | null
  active: boolean
}

export interface UpsertUserRequest {
  username: string
  password?: string | null
  pin?: string | null
  displayName: string
  role: Role
  branchId?: string | null
  active?: boolean
}

// -- catalog/controller/dto/CatalogDtos.kt ----------------------------------

/** `productType` is one of Pan, Pastelería, Almacén (fixed; see `lib/productTypes`). The API
 * matches it case- and accent-insensitively and answers with the canonical spelling. */
export type ProductOrigin = "PANADERIA" | "ALMACEN"

export interface ProductDto {
  id: string
  name: string
  productType: string
  /** Read-only, derived by the API from `productType` (ALMACEN iff Almacén). */
  origin: ProductOrigin
  soldBy: "WEIGHT" | "UNIT"
  pricePerKgMinor: number | null
  priceMinor: number | null
  barcode: string | null
  /** Absolute URL of a product photo, or null. */
  imageUrl: string | null
  active: boolean
  serverSeq: number
}

export interface CreateProductRequest {
  id: string
  name: string
  productType: string
  soldBy: "WEIGHT" | "UNIT"
  pricePerKgMinor: number | null
  priceMinor: number | null
  barcode: string | null
  /** Almacén only, and required there (0 allowed): units on the shelf now, stored as the branch's opening count. */
  openingStock?: number | null
  /** Branch the opening count belongs to; the API defaults to the caller's own branch. */
  openingBranchId?: string | null
}

/** No `imageUrl` here on purpose: photos go through the upload-grant flow below, never a pasted
 * URL -- the API owns where pictures live (CatalogDtos.kt). */
export interface UpdateProductRequest {
  name: string
  productType: string
  soldBy: "WEIGHT" | "UNIT"
  pricePerKgMinor: number | null
  priceMinor: number | null
  barcode: string | null
}

// -- product photo upload grants (CatalogDtos.kt) ------------------------------

export interface ImageUploadGrantRequest {
  contentType: string
}

/** Step 1 of a photo upload: `PUT uploadUrl` with the file body and headers
 * `x-ms-blob-type: BlockBlob` + `Content-Type: contentType`, before `expiresAt` (epoch ms);
 * then step 2, `PUT /catalog/products/{id}/image` with `{ key }`. */
export interface ImageUploadGrantDto {
  key: string
  uploadUrl: string
  expiresAt: number
  contentType: string
}

export interface ConfirmImageRequest {
  key: string
}

/** `id` is absent for a brand-new line -- the server mints one (`CatalogServiceImpl.putFormula`).
 * `PutFormulaRequest` is a full replace-all of a product's formula lines (C6). */
export interface FormulaLineDto {
  id?: string | null
  ingredientId: string
  basisPoints: number
}

export interface FormulaDto {
  id: string
  productId: string
  lines: FormulaLineDto[]
}

export interface PutFormulaRequest {
  id?: string | null
  lines: FormulaLineDto[]
}

// -- inventory/controller/dto/InventoryDtos.kt ------------------------------

export interface IngredientDto {
  id: string
  name: string
  lowStockThresholdGrams: number | null
  active: boolean
}

export interface CreateIngredientRequest {
  id: string
  name: string
  lowStockThresholdGrams?: number | null
}

export interface UpdateIngredientRequest {
  name: string
  lowStockThresholdGrams?: number | null
  active?: boolean
}

export interface StockLevelDto {
  ingredientId: string
  name: string
  currentGrams: number
  lowStock: boolean
}

/** `data/database/entity/StockMovementEntity.kt`'s `StockMovementKind`: `FORMULA_DECREMENT` rows
 * are worker-minted only (item 6/formula territory, out of this item's scope) -- the movement
 * ledger can display one, but `POST /inventory/movements` (below) only ever accepts the other
 * two from this client. */
export type StockMovementKind = "PURCHASE" | "FORMULA_DECREMENT" | "MANUAL_ADJUSTMENT"

export interface StockMovementDto {
  id: string
  branchId: string
  ingredientId: string
  deltaGrams: number
  kind: StockMovementKind
  sourceBatchId: string | null
  note: string | null
  createdBy: string | null
  occurredAt: number
  serverSeq: number
}

export interface CreateStockMovementRequest {
  id: string
  branchId: string
  ingredientId: string
  deltaGrams: number
  kind: "PURCHASE" | "MANUAL_ADJUSTMENT"
  note?: string | null
  occurredAt: number
}

export interface CreateStockMovementResponse {
  serverSeq: number
}

// -- resold-product unit stock (InventoryDtos.kt) ---------------------------

/** One Almacén product's stock in one branch: `currentUnits = receivedUnits - soldUnits`, where
 * `receivedUnits` is the opening count plus purchases and adjustments, and sales count from
 * `trackedSince` (epoch ms of the branch's opening count) onward. Negative is legal: it is a
 * warning to reconcile, never a block. */
export interface ProductStockLevelDto {
  productId: string
  name: string
  receivedUnits: number
  soldUnits: number
  currentUnits: number
  trackedSince: number
  /** Reorder level, or null when none is set. */
  lowStockThresholdUnits: number | null
  /** True when `currentUnits` is below the reorder level; false without one. */
  lowStock: boolean
  /** False for a retired product: its row is kept for history but it needs no restocking. */
  active: boolean
}

export type ProductStockMovementKind = "OPENING_COUNT" | "PURCHASE" | "MANUAL_ADJUSTMENT"

export interface ProductStockMovementDto {
  id: string
  branchId: string
  productId: string
  deltaUnits: number
  kind: ProductStockMovementKind
  note: string | null
  createdBy: string | null
  occurredAt: number
}

export interface CreateProductStockMovementRequest {
  id: string
  branchId: string
  productId: string
  deltaUnits: number
  /** `OPENING_COUNT` is written by product creation only; the movement endpoint refuses it. */
  kind: Exclude<ProductStockMovementKind, "OPENING_COUNT">
  note?: string | null
  occurredAt: number
}

// -- analytics/controller/dto/AnalyticsDtos.kt (K1-K8, plan 07 §9) ----------

/** K1. */
export interface SalesByDayDto {
  date: string
  branchId: string | null
  productType: string | null
  salesCount: number
  weightGrams: number
  units: number
  revenueMinor: number
}

/** K2. */
export interface RevenueByPeriodDto {
  period: string
  revenueMinor: number
  salesCount: number
}

/** K3. */
export interface ProductMixDto {
  productType: string
  revenueMinor: number
  shareBp: number
}

/** `GET /inventory/reconciliation` (HU-ANA-0006), `InventoryDtos.kt`'s `ReconciliationRowDto`: one
 * ingredient's ledger over a closed day range. Every figure is a signed grams Σ, so
 * `decrementedGrams` is <= 0 and `opening + purchased + decremented + adjusted === closing`. */
export interface ReconciliationRowDto {
  /** A React key only -- never rendered. */
  ingredientId: string
  /** Empty when the ledger row's ingredient is missing from the catalog; show a label, not an id. */
  name: string
  openingGrams: number
  purchasedGrams: number
  decrementedGrams: number
  adjustedGrams: number
  closingGrams: number
  /** Production batches behind `decrementedGrams`. Per ingredient: do NOT sum these -- one batch
   * decrements every ingredient of its formula. Use `ReconciliationReportDto.batchCount`. */
  batchCount: number
}

/** `GET /inventory/reconciliation` response (`ReconciliationReportDto`): the rows plus the
 * period-level count of distinct production batches (what the total row and CSV show). */
export interface ReconciliationReportDto {
  batchCount: number
  rows: ReconciliationRowDto[]
}

/** K5: I3's shape plus `negativeStock` (01 §6.4 reconciliation warning). */
export interface StockLevelWarningDto {
  ingredientId: string
  name: string
  currentGrams: number
  lowStock: boolean
  negativeStock: boolean
}

/** K6: one row per reporting-tz day x branch x product. `mixWeightGrams` is *dough* weight, not
 * finished bread. */
export interface ProductionSummaryDto {
  date: string
  branchId: string
  productId: string
  productName: string
  productType: string
  batchCount: number
  mixWeightGrams: number
}

/** K9 (HU-ANA-0005). `producedGrams` is dough weight and `soldGrams` finished bread, so the
 * difference is an upper bound; `estimatedWasteGrams` is `null` when the type sells by unit. */
export interface WasteEstimateDto {
  date: string
  productType: string
  producedGrams: number
  soldGrams: number
  soldUnits: number
  estimatedWasteGrams: number | null
  estimated: boolean
}

/** K7. */
export interface PaymentMethodDto {
  paymentMethod: string
  salesCount: number
  revenueMinor: number
}

/** K8. */
export interface ForecastVsActualDto {
  date: string
  /** The series: one (branch, Panadería product). Almacén is never forecast. */
  productId: string
  productName: string
  /** The product's type -- grouping and filtering only, never a series key. */
  productType: string
  predictedGrams: number | null
  predictedUnits: number | null
  actualGrams: number
  actualUnits: number
  /** Estimator behind the prediction (`lgbm-*` model, `baseline-*` cold start); absent when the day has no forecast. */
  modelVersion?: string | null
}

// -- forecasts/controller/dto/ForecastDtos.kt (F1, plan 07 §9) --------------

/** F1 -- one row per (branch, Panadería product, day). The page reads it for tomorrow's
 * *Producción sugerida*; the chart overlay comes from K8, which joins this cache with actuals. */
export interface ForecastDto {
  id: string
  branchId: string
  productId: string
  productName: string
  productType: string
  forecastDate: string
  predictedGrams: number | null
  predictedUnits: number | null
  modelVersion: string
  generatedAt: number
}

// -- api/core/Auth.kt (ErrorBody) --------------------------------------------

/** Mirrors `ErrorBody` exactly: `{ code, message?, module? }` -- the `module` field is only
 * populated for 403 `MODULE_DISABLED` responses (`Route.requireModule`). */
export interface ErrorBodyDto {
  code: string
  message?: string
  module?: ModuleId | string
}

// -- Client-assembled session (not a wire DTO) -------------------------------

/** Assembled client-side from `LoginResponse.user` + `.tenant` + `.branches` (plan 07 §3). */
export interface Session {
  userId: string
  tenantId: string
  tenantSlug: string
  branchId: string | null
  role: Role
  displayName: string
  tenantName: string
  currencyCode: string
  enabledModules: ModuleId[]
  branches: BranchSummaryDto[]
}
