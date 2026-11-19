/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Optional in dev (`apiBaseUrl()` falls back to the local stack); required for a prod build. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
