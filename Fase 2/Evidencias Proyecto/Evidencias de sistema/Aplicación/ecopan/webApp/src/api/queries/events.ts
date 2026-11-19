import { createElement, useEffect, useSyncExternalStore } from "react"
import { Croissant } from "lucide-react"
import type { QueryClient } from "@tanstack/react-query"
import { useQueryClient } from "@tanstack/react-query"
import { useRouter } from "@tanstack/react-router"
import { toast } from "sonner"
import { accessTokenNeedsRefresh, apiBaseUrl, getAccessToken, refreshSession, subscribeAccessToken } from "@/api/http"
import { productsQuery } from "@/api/queries/catalog"
import { inventoryKeys } from "@/api/queries/inventory"
import { kpiKeys } from "@/api/queries/kpis"
import { tenantKeys } from "@/api/queries/tenant"
import { useAuth } from "@/features/auth/AuthContext"
import { formatGramsAsKg } from "@/lib/format"
import { t } from "@/lib/i18n"

/**
 * `E1`/plan 07 §6/§10 item 10: SSE is a cache-invalidation signal, not a second data channel --
 * every event type below just invalidates the query keys `api/queries/kpis.ts`/`inventory.ts`
 * actually use (checked against those files, not the plan's illustrative names) and lets the
 * normal `useQuery`s refetch. `batch-ready`'s payload shape is `EventsRouter.kt`'s
 * `BatchReadySnapshotPayload` -- `{ batchId, branchId, productId, mixWeightGrams, batchAt,
 * decrementStatus }` (the `batch-snapshot` replay sent on connect has the same shape but never
 * toasts; only the live `batch-ready` push does), no product/branch *name* -- so the toast resolves names from what's already
 * the products query (served from cache when warm, fetched via `ensureQueryData` when cold) and
 * the session's `branches[]`. A name that still can't be resolved is dropped from the toast --
 * raw ids are never user-visible.
 */
export interface BatchReadyPayload {
  batchId: string
  branchId: string
  productId: string
  mixWeightGrams: number
  batchAt: number
  decrementStatus: string
}

export interface ServerEventNameLookup {
  productName?: (productId: string) => string | undefined | Promise<string | undefined>
  branchName?: (branchId: string) => string | undefined | Promise<string | undefined>
  /** "Ver producción": open the production page on today, for that branch, marking that product. */
  openProduction?: (target: { branchId?: string; productId?: string }) => void
}

/** Batches that arrive within this window of the first one share a single notice. */
const GROUP_WINDOW_MS = 10_000
const NOTICE_DURATION_MS = 6_000
const NOTICE_ID = "batch-ready"

interface NoticeItem {
  productId: string
  productName?: string
  branchId: string
  branchName?: string
  mixWeightGrams: number
}

let noticeGroup: { startedAt: number; items: NoticeItem[] } | null = null

/** Forgets the open group (tests; a real session just lets the window lapse). */
export function resetBatchNoticeGroup() {
  noticeGroup = null
}

function showBatchNotice(items: NoticeItem[], lookup: ServerEventNameLookup) {
  const strings = t.events.batchReady
  let title: string
  let description: string | undefined
  if (items.length === 1) {
    const [item] = items
    if (item.productName) {
      title = strings.titleFull(item.productName, formatGramsAsKg(item.mixWeightGrams))
      description = item.branchName
    } else if (item.branchName) {
      title = strings.titleBranchOnly(item.branchName)
    } else {
      title = strings.titleNothingKnown
    }
  } else {
    title = strings.titleMany(items.length)
    const branches = new Set(items.map((item) => item.branchId))
    const branchName = branches.size === 1 ? items[0].branchName : undefined
    const products = [...new Set(items.map((item) => item.productName).filter((name): name is string => Boolean(name)))]
    description = [branchName, products.length > 0 ? strings.productList(products) : undefined]
      .filter(Boolean)
      .join(" · ") || undefined
  }
  const branchIds = new Set(items.map((item) => item.branchId))
  const productIds = new Set(items.map((item) => item.productId))
  toast(title, {
    id: NOTICE_ID,
    icon: createElement(Croissant, { className: "size-4", "aria-hidden": true }),
    description,
    duration: NOTICE_DURATION_MS,
    action: lookup.openProduction
      ? {
          label: strings.action,
          onClick: () =>
            lookup.openProduction?.({
              branchId: branchIds.size === 1 ? items[0].branchId : undefined,
              productId: productIds.size === 1 ? items[0].productId : undefined,
            }),
        }
      : undefined,
  })
}

/**
 * Exported standalone (not just inlined in the hook) so tests can drive the invalidation/toast
 * logic with a fake event, without needing a real `EventSource` in jsdom (plan 07 §10 AC: "if you
 * can't cleanly test the actual browser reconnect behavior, that's fine -- just prove the
 * invalidation-on-event-type logic works given a fake event dispatch").
 */
export async function applyServerEvent(
  queryClient: QueryClient,
  type: string,
  rawData: string,
  lookup: ServerEventNameLookup = {}
): Promise<void> {
  switch (type) {
    case "batch-snapshot":
      // Replay of the last 24 h sent on every (re)connect -- repaints state, never toasts: these
      // batches are not news, and `batch-ready` is reserved for live pushes.
      invalidateBatchQueries(queryClient)
      break
    case "batch-ready": {
      invalidateBatchQueries(queryClient)
      try {
        const payload = JSON.parse(rawData) as BatchReadyPayload
        const productName = await resolveName(lookup.productName, payload.productId)
        const branchName = await resolveName(lookup.branchName, payload.branchId)
        const now = Date.now()
        if (!noticeGroup || now - noticeGroup.startedAt > GROUP_WINDOW_MS) {
          noticeGroup = { startedAt: now, items: [] }
        }
        noticeGroup.items.push({
          productId: payload.productId,
          productName,
          branchId: payload.branchId,
          branchName,
          mixWeightGrams: payload.mixWeightGrams,
        })
        showBatchNotice(noticeGroup.items, lookup)
      } catch {
        // Malformed payload -- the invalidations above already happened; the toast is best-effort.
      }
      break
    }
    case "sync-available":
      // Tenant profile changed server-side (e.g. another session toggled a module) -- re-fetching
      // `["tenant"]` is exactly what `useTenantProfileSync` already reacts to.
      queryClient.invalidateQueries({ queryKey: tenantKeys.profile })
      break
    case "heartbeat":
    default:
      break
  }
}

/** What a production batch changes: every analytics query, ingredient stock and its movements. */
const BATCH_KEYS = [kpiKeys.all, inventoryKeys.stock, inventoryKeys.movements] as const

/** Everything the stream keeps live; refetched once when it reopens after a drop. */
const LIVE_KEYS = [...BATCH_KEYS, tenantKeys.profile] as const

function invalidateBatchQueries(queryClient: QueryClient): void {
  for (const queryKey of BATCH_KEYS) queryClient.invalidateQueries({ queryKey })
}

/** A failing/absent lookup yields `undefined` -- the toast then uses neutral wording, never the id. */
async function resolveName(
  lookup: ((id: string) => string | undefined | Promise<string | undefined>) | undefined,
  id: string
): Promise<string | undefined> {
  try {
    return (await lookup?.(id)) || undefined
  } catch {
    return undefined
  }
}

export interface EventSourceLike {
  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void
  removeEventListener(type: string, listener: (event: MessageEvent<string>) => void): void
  close(): void
}

interface EventStreamOptions {
  url: (token: string) => string
  getToken: () => string | null
  refresh: () => Promise<"refreshed" | "rejected" | "unreachable">
  /** Whether [token] is too close to expiry to reconnect with. Default: always refresh. */
  needsRefresh?: (token: string) => boolean
  create: (url: string) => EventSourceLike
  listeners: Record<string, (event: MessageEvent<string>) => void>
  /** Fired when a stream opens after having dropped, so data missed meanwhile is refetched. */
  onReconnect: () => void
  /** `paused` while the stream cannot come back (refresh throttled, failing or unreachable, or a
   * reconnect that fails again); `live` on the next successful open. Only changes are reported. */
  onStatus?: (status: LiveStatus) => void
}

export type LiveStatus = "live" | "paused"

const RECONNECT_BASE_MS = 1_000
const RECONNECT_MAX_MS = 30_000

/**
 * One SSE stream that survives a dropped connection. `EventSource` can't set headers, so the
 * access token rides the URL -- and the browser's own auto-reconnect would replay that same URL,
 * which once the 15-minute token expires is a 401 that closes the stream for good. So on error we
 * close it ourselves and open a new one with the token read at that moment -- refreshing first
 * only when that token is within a minute of expiry (`needsRefresh`), since every refresh rotates
 * the session for every tab.
 * A rejected refresh ends the session (via `refresh`) and stops; an unreachable server retries with
 * a capped exponential delay. Returns the function that closes everything.
 */
export function openEventStream(options: EventStreamOptions): () => void {
  let source: EventSourceLike | null = null
  let timer: ReturnType<typeof setTimeout> | undefined
  let stopped = false
  let attempt = 0
  let reconnecting = false
  let status: LiveStatus = "live"

  const setStatus = (next: LiveStatus) => {
    if (next === status) return
    status = next
    options.onStatus?.(next)
  }

  const detach = () => {
    if (!source) return
    for (const [type, listener] of Object.entries(options.listeners)) source.removeEventListener(type, listener)
    source.removeEventListener("open", onOpen as never)
    source.removeEventListener("error", onError as never)
    source.close()
    source = null
  }

  const onOpen = () => {
    attempt = 0
    setStatus("live")
    if (reconnecting) {
      reconnecting = false
      options.onReconnect()
    }
  }

  const schedule = () => {
    const delay = Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS * 2 ** attempt)
    attempt += 1
    timer = setTimeout(async () => {
      if (stopped) return
      const token = options.getToken()
      if (token && !(options.needsRefresh?.(token) ?? true)) return connect()
      const outcome = await options.refresh()
      if (stopped || outcome === "rejected") return
      if (outcome === "unreachable") {
        setStatus("paused")
        return schedule()
      }
      connect()
    }, delay)
  }

  const onError = () => {
    detach()
    // The first drop is routine (a deploy, a proxy timeout); a reconnect that fails too is not.
    if (reconnecting) setStatus("paused")
    reconnecting = true
    schedule()
  }

  const connect = () => {
    const token = options.getToken()
    if (!token) return
    source = options.create(options.url(token))
    for (const [type, listener] of Object.entries(options.listeners)) source.addEventListener(type, listener)
    source.addEventListener("open", onOpen as never)
    source.addEventListener("error", onError as never)
  }

  connect()
  return () => {
    stopped = true
    clearTimeout(timer)
    detach()
  }
}

let liveUpdatesPaused = false
const pausedListeners = new Set<() => void>()

/** Whether the shell should say live updates are paused (#140). Set by `useServerEvents`. */
export function setLiveUpdatesPaused(paused: boolean): void {
  if (paused === liveUpdatesPaused) return
  liveUpdatesPaused = paused
  for (const listener of pausedListeners) listener()
}

function subscribeLiveUpdatesPaused(listener: () => void): () => void {
  pausedListeners.add(listener)
  return () => pausedListeners.delete(listener)
}

/** True while the stream is down and retrying, so a dashboard that is not live never looks live. */
export function useLiveUpdatesPaused(): boolean {
  return useSyncExternalStore(subscribeLiveUpdatesPaused, () => liveUpdatesPaused)
}

/**
 * Opens the backend's SSE stream (`GET /events/sse?access_token=<jwt>`, per 02 §2/§5) and wires
 * each named event to `applyServerEvent`. Mounted exactly once, from `AppShell` (plan 07 §6): a
 * per-page mount would open one connection per open tab-section instead of one per session.
 *
 * Reconnection is `openEventStream`'s; `kpis.ts`'s `SSE_POLLING_FALLBACK` (opted into by the
 * affected dashboard/stock queries) remains the fallback for a connection that's silently dead.
 */
export function useServerEvents(): void {
  const queryClient = useQueryClient()
  const { session } = useAuth()
  const router = useRouter()
  // After a reload the session is restored before the access token is (that waits on the first
  // refresh), so the stream must open when the token *arrives*, not only when the session does.
  // Keyed on presence, not value: a rotated token leaves an open stream alone.
  const hasAccessToken = useSyncExternalStore(subscribeAccessToken, () => getAccessToken() !== null)

  useEffect(() => {
    if (!session || !hasAccessToken) return
    // jsdom (the test environment) has no `EventSource` global -- every authed-route test mounts
    // `AppShell`, so this hook must no-op there rather than throw. Real browsers always have it.
    if (typeof EventSource === "undefined") return

    const lookup: ServerEventNameLookup = {
      productName: async (productId) => {
        const products = await queryClient.ensureQueryData(productsQuery(false))
        return products.find((product) => product.id === productId)?.name
      },
      branchName: (branchId) => session.branches.find((branch) => branch.id === branchId)?.name,
      openProduction: ({ branchId, productId }) =>
        void router.navigate({
          to: "/analytics/production",
          search: { today: true, branchId, highlight: productId },
        }),
    }

    const listen = (type: "batch-ready" | "batch-snapshot" | "sync-available") =>
      (event: MessageEvent<string>) => void applyServerEvent(queryClient, type, event.data, lookup)

    const close = openEventStream({
      url: (token) => `${apiBaseUrl()}/events/sse?access_token=${encodeURIComponent(token)}`,
      getToken: getAccessToken,
      refresh: refreshSession,
      needsRefresh: (token) => accessTokenNeedsRefresh(token),
      create: (url) => new EventSource(url),
      listeners: {
        "batch-ready": listen("batch-ready"),
        "batch-snapshot": listen("batch-snapshot"),
        "sync-available": listen("sync-available"),
      },
      onReconnect: () => {
        for (const queryKey of LIVE_KEYS) queryClient.invalidateQueries({ queryKey })
      },
      onStatus: (status) => setLiveUpdatesPaused(status === "paused"),
    })
    return () => {
      close()
      setLiveUpdatesPaused(false)
    }
  }, [queryClient, router, session, hasAccessToken])
}
