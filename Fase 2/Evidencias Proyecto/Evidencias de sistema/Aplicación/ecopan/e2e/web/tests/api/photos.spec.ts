import { randomUUID } from "node:crypto"
import { expect, test } from "@playwright/test"
import { API, createProduct, login } from "../../../shared/api"

// #138 on the real stack: the API checks what actually landed in blob storage (Azurite here) before it
// points a product at it. The router tests use an in-memory store; this is the real bucket.
const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(4_000, 1)])
const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(4_000, 2)])
const WEBP = Buffer.concat([Buffer.from("RIFF"), Buffer.from([0x10, 0x0f, 0, 0]), Buffer.from("WEBP"), Buffer.alloc(4_000, 3)])
const FIVE_MB_PLUS_ONE = 5 * 1024 * 1024 + 1

async function api(token: string, method: string, path: string, body?: unknown) {
  const res = await fetch(API + path, {
    method,
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  return { status: res.status, json: text ? JSON.parse(text) : null }
}

/** Grants a write, then uploads [bytes] straight to storage the way the dashboard does. */
async function upload(token: string, productId: string, contentType: string, bytes: Buffer) {
  const grant = await api(token, "POST", `/catalog/products/${productId}/image/upload-url`, { contentType })
  expect(grant.status).toBe(200)
  const put = await fetch(grant.json.uploadUrl, { method: "PUT", headers: { "x-ms-blob-type": "BlockBlob", "Content-Type": contentType }, body: new Uint8Array(bytes) })
  expect(put.ok).toBe(true)
  return grant.json.key as string
}

const blobExists = async (token: string, productId: string, key: string) => {
  // The public read URL is the one the product would carry once confirmed; build it from a fresh grant's host.
  const grant = await api(token, "POST", `/catalog/products/${productId}/image/upload-url`, { contentType: "image/png" })
  const base = (grant.json.uploadUrl as string).split("?")[0].replace(grant.json.key as string, "")
  return (await fetch(base + key, { method: "HEAD" })).ok
}

test.describe("product photos on real storage (#138)", () => {
  test("JPEG, PNG and WebP confirm, and the product carries the blob's URL", async () => {
    const { accessToken } = await login("manager")
    for (const [type, bytes] of [["image/jpeg", JPEG], ["image/png", PNG], ["image/webp", WEBP]] as const) {
      const product = await createProduct(accessToken, { name: `e2e foto ${type} ${randomUUID().slice(0, 6)}` })
      const key = await upload(accessToken, product.id, type, bytes)
      const confirmed = await api(accessToken, "PUT", `/catalog/products/${product.id}/image`, { key })
      expect(confirmed.status, type).toBe(200)
      expect(confirmed.json.imageUrl).toContain(key)
      expect((await fetch(confirmed.json.imageUrl)).status).toBe(200)
      // The probe the deletion tests rely on does see a blob that is there.
      expect(await blobExists(accessToken, product.id, key), `${type} blob visible to the probe`).toBe(true)
    }
  })

  test("non-image bytes uploaded as an image are a 422 and the blob is deleted", async () => {
    const { accessToken } = await login("manager")
    const product = await createProduct(accessToken, { name: `e2e foto html ${randomUUID().slice(0, 6)}` })
    const key = await upload(accessToken, product.id, "image/png", Buffer.from("<html><script>alert(1)</script></html>"))
    const res = await api(accessToken, "PUT", `/catalog/products/${product.id}/image`, { key })
    expect(res.status).toBe(422)
    expect(res.json.code).toBe("IMAGE_NOT_AN_IMAGE")
    expect(await blobExists(accessToken, product.id, key)).toBe(false)
  })

  test("a photo over 5 MB is a 422, the blob is deleted and the previous photo stays", async () => {
    const { accessToken } = await login("manager")
    const product = await createProduct(accessToken, { name: `e2e foto grande ${randomUUID().slice(0, 6)}` })
    const good = await upload(accessToken, product.id, "image/jpeg", JPEG)
    const first = await api(accessToken, "PUT", `/catalog/products/${product.id}/image`, { key: good })
    expect(first.status).toBe(200)

    const huge = await upload(accessToken, product.id, "image/jpeg", Buffer.concat([JPEG, Buffer.alloc(FIVE_MB_PLUS_ONE, 4)]))
    const res = await api(accessToken, "PUT", `/catalog/products/${product.id}/image`, { key: huge })
    expect(res.status).toBe(422)
    expect(res.json.code).toBe("IMAGE_TOO_LARGE")
    expect(await blobExists(accessToken, product.id, huge)).toBe(false)
    expect((await fetch(first.json.imageUrl)).status).toBe(200)
  })
})
