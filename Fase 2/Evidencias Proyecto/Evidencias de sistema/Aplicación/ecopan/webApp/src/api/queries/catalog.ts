import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query"
import { http } from "@/api/http"
import { ApiError } from "@/api/http"
import type {
  ConfirmImageRequest,
  CreateProductRequest,
  ImageUploadGrantDto,
  ImageUploadGrantRequest,
  ProductDto,
  UpdateProductRequest,
} from "@/api/types"

/** C1-C4 (02 §3 / plan 07 §9). Query key includes `includeInactive` since it changes the
 * server-returned set, per plan 07 §2's parameterized-key convention. */
export function productsQuery(includeInactive: boolean) {
  return queryOptions({
    queryKey: ["products", { includeInactive }] as const,
    queryFn: () =>
      http.get<ProductDto[]>(`/catalog/products?includeInactive=${includeInactive}`),
    staleTime: 30_000,
  })
}

function invalidateProducts(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: ["products"] })
}

/**
 * Almacén stock is derived from the product row (it exists from creation, with its opening count),
 * so a product create/edit/delete also stales the stock list and ledger. Without this a new
 * Almacén product shows no stock until the next poll.
 */
function invalidateProductsAndStock(queryClient: ReturnType<typeof useQueryClient>) {
  return Promise.all([
    invalidateProducts(queryClient),
    queryClient.invalidateQueries({ queryKey: ["product-stock"] }),
    queryClient.invalidateQueries({ queryKey: ["product-movements"] }),
  ])
}

/** C2: create. */
export function useCreateProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateProductRequest) => http.post<ProductDto>("/catalog/products", body),
    onSuccess: () => invalidateProductsAndStock(queryClient),
  })
}

/** C3: update. */
export function useUpdateProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateProductRequest }) =>
      http.put<ProductDto>(`/catalog/products/${id}`, body),
    onSuccess: () => invalidateProductsAndStock(queryClient),
  })
}

/** C4: soft-delete (`active=false` server-side, per `CatalogRouter.kt`'s doc comment). */
export function useDeleteProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => http.delete<void>(`/catalog/products/${id}`),
    onSuccess: () => invalidateProductsAndStock(queryClient),
  })
}

/**
 * Product photo upload, the direct-to-storage way: the API never sees the bytes. (1) ask it for
 * a one-blob, minutes-long write grant; (2) `PUT` the file straight to blob storage (Azurite
 * locally, the Azure account in production) with the headers the grant expects; (3) tell the API
 * which key landed so the product starts pointing at the blob's public URL. A failed (2) leaves
 * nothing to clean up: the grant expires and the product is unchanged.
 */
export function useUploadProductImage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, file }: { id: string; file: File }) => {
      const grantBody: ImageUploadGrantRequest = { contentType: file.type }
      const grant = await http.post<ImageUploadGrantDto>(
        `/catalog/products/${id}/image/upload-url`,
        grantBody
      )
      let stored: Response
      try {
        // Read the bytes first rather than handing fetch the File: a photo is a few MB at most,
        // and an ArrayBuffer body behaves the same in every fetch implementation (jsdom's fetch
        // serialises a File as the string "undefined" -- the tests caught exactly that).
        stored = await fetch(grant.uploadUrl, {
          method: "PUT",
          headers: { "x-ms-blob-type": "BlockBlob", "Content-Type": grant.contentType },
          body: await file.arrayBuffer(),
        })
      } catch {
        throw new ApiError(0, { code: "STORAGE_UNREACHABLE" })
      }
      if (!stored.ok) throw new ApiError(stored.status, { code: "STORAGE_REJECTED_UPLOAD" })
      const confirmBody: ConfirmImageRequest = { key: grant.key }
      return http.put<ProductDto>(`/catalog/products/${id}/image`, confirmBody)
    },
    onSuccess: () => invalidateProducts(queryClient),
  })
}

/** Revoke a photo: the API deletes the blob and clears `imageUrl`. */
export function useDeleteProductImage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => http.delete<ProductDto>(`/catalog/products/${id}/image`),
    onSuccess: () => invalidateProducts(queryClient),
  })
}
