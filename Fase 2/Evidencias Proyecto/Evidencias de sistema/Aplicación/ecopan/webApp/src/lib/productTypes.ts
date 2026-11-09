import { t } from "./i18n"

/**
 * A product's one classification, a fixed list owned by the backend (`ProductKind`): Pan and
 * Pastelería are baked; Almacén is bought in and counted on the product. The wire value is the
 * label itself. Whether a product is Almacén (unit-only, product stock, never a formula) is read
 * from its Tipo -- there is no separate origin to choose.
 */
const names = t.admin.products.types

export const ALMACEN_TYPE = names.almacen
const BAKED_TYPES = [names.pan, names.pasteleria]

export const ALL_PRODUCT_TYPES = [...BAKED_TYPES, ALMACEN_TYPE]

export function isAlmacenType(productType: string): boolean {
  return productType === ALMACEN_TYPE
}

/** Options for the Tipo select. Editing cannot cross between Almacén and Pan / Pastelería (the
 * API rejects it), so an existing product only offers its own side. */
export function productTypeOptions(editing?: string): { value: string; label: string }[] {
  const allowed = editing === undefined ? ALL_PRODUCT_TYPES : isAlmacenType(editing) ? [ALMACEN_TYPE] : BAKED_TYPES
  return allowed.map((value) => ({ value, label: value }))
}
