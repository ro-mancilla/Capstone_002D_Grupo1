import { afterEach, describe, expect, it, vi } from "vitest"
import { act, renderHook } from "@testing-library/react"
import { useReportingToday } from "./useReportingToday"

afterEach(() => vi.useRealTimers())

describe("useReportingToday", () => {
  it("rolls over to the next reporting day without a remount", () => {
    vi.useFakeTimers()
    vi.setSystemTime(Date.parse("2026-07-29T03:59:30Z")) // 23:59:30 on the 28th in Santiago
    const { result } = renderHook(() => useReportingToday())
    expect(result.current).toBe("2026-07-28")

    act(() => {
      vi.advanceTimersByTime(60_000)
    })
    expect(result.current).toBe("2026-07-29")
  })
})
