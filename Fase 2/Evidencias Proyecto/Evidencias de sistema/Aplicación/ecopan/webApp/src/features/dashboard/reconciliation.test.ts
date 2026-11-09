import { afterEach, describe, expect, it } from "vitest"
import type { ReconciliationRowDto } from "@/api/types"
import {
  dateRangeProblem,
  defaultRange,
  formatLedgerKg,
  reconciliationCsv,
  reconciliationTotals,
  reportingToday,
} from "./reconciliation"

const flour: ReconciliationRowDto = {
  ingredientId: "i-1",
  name: "Harina",
  openingGrams: 9_000,
  purchasedGrams: 4_300,
  decrementedGrams: -2_000,
  adjustedGrams: -200,
  closingGrams: 11_100,
  batchCount: 2,
}
const salt: ReconciliationRowDto = {
  ingredientId: "i-2",
  name: 'Sal, "fina"',
  openingGrams: 500,
  purchasedGrams: 0,
  decrementedGrams: -120,
  adjustedGrams: 0,
  closingGrams: 380,
  batchCount: 1,
}

describe("reconciliation helpers", () => {
  it("sums every column, and the totals row balances too", () => {
    const totals = reconciliationTotals([flour, salt], 2)
    expect(totals).toEqual({
      openingGrams: 9_500,
      purchasedGrams: 4_300,
      decrementedGrams: -2_120,
      adjustedGrams: -200,
      closingGrams: 11_480,
      // The period's distinct batches as the API reports them -- not flour's 2 + salt's 1.
      batchCount: 2,
    })
    expect(
      totals.openingGrams + totals.purchasedGrams + totals.decrementedGrams + totals.adjustedGrams
    ).toBe(totals.closingGrams)
  })

  it("shows kilos to the gram so a 1 g discrepancy is never rounded away", () => {
    expect(formatLedgerKg(11_101)).toBe("11,101 kg")
    expect(formatLedgerKg(-2_000)).toBe("-2 kg")
  })

  it("exports CSV in exact grams with quoting, a BOM for Excel, and a totals row", () => {
    const csv = reconciliationCsv(
      { batchCount: 2, rows: [flour, salt] },
      ["Insumo", "Apertura (g)", "Compras (g)", "Descuento (g)", "Ajustes (g)", "Cierre (g)", "Lotes"],
      "Total",
      "Insumo sin nombre"
    )
    const lines = csv.replace(/^﻿/, "").split("\r\n")
    expect(csv.startsWith("﻿")).toBe(true)
    expect(lines[0]).toBe("Insumo,Apertura (g),Compras (g),Descuento (g),Ajustes (g),Cierre (g),Lotes")
    expect(lines[1]).toBe("Harina,9000,4300,-2000,-200,11100,2")
    expect(lines[2]).toBe('"Sal, ""fina""",500,0,-120,0,380,1')
    expect(lines[3]).toBe("Total,9500,4300,-2120,-200,11480,2")
  })
})

describe("unnamed ingredients", () => {
  it("writes the localized label, never the id, for a row with an empty name", () => {
    const orphan: ReconciliationRowDto = { ...flour, ingredientId: "6f1c0c0e-0000-4000-8000-00000000abcd", name: "" }
    const csv = reconciliationCsv({ batchCount: 2, rows: [orphan] }, ["a"], "Total", "Insumo sin nombre")
    expect(csv).toContain("\r\nInsumo sin nombre,9000,")
    expect(csv).not.toContain(orphan.ingredientId)
  })
})

describe("CSV formula injection", () => {
  const headers = ["a", "b", "c", "d", "e", "f", "g"]
  const dataLine = (name: string) =>
    reconciliationCsv({ batchCount: 2, rows: [{ ...flour, name }] }, headers, "Total", "Insumo sin nombre").replace(/^﻿/, "").split("\r\n")[1]

  it.each(["=SUM(A1)", "+1+1", "-2+3", "@cmd"])(
    "prefixes an apostrophe to a name starting with %j so a spreadsheet reads it as text",
    (name) => {
      expect(dataLine(name).startsWith(`'${name},`)).toBe(true)
    }
  )

  it("also covers a leading tab or carriage return (the latter then needs quoting)", () => {
    expect(dataLine("\tTab")).toMatch(/^'\tTab,/)
    expect(dataLine("\rCR")).toMatch(/^"'\rCR",/)
  })

  it("keeps the apostrophe outside the quotes' escaping when the name also needs quoting", () => {
    expect(dataLine('=A,"b"')).toMatch(/^"'=A,""b""",/)
  })

  it("leaves numeric cells raw, so a negative balance stays a number", () => {
    expect(dataLine("Harina")).toBe("Harina,9000,4300,-2000,-200,11100,2")
  })
})

describe("reportingToday and defaultRange", () => {
  const originalTz = process.env.TZ
  afterEach(() => {
    if (originalTz === undefined) delete process.env.TZ
    else process.env.TZ = originalTz
  })

  it("uses the reporting-timezone calendar day, not the viewer's or UTC's", () => {
    // 02:30Z on 14 Mar is still 23:30 on 13 Mar in Santiago (UTC-3).
    const instant = new Date("2026-03-14T02:30:00Z")
    for (const viewerTz of ["UTC", "Pacific/Kiritimati", "America/Los_Angeles"]) {
      process.env.TZ = viewerTz
      expect(reportingToday(instant)).toBe("2026-03-13")
    }
  })

  it("spans the last 30 reporting days, today included", () => {
    expect(defaultRange(new Date("2026-03-14T15:00:00Z"))).toEqual({
      from: "2026-02-13",
      to: "2026-03-14",
    })
  })

  it("crosses a month and a year boundary by calendar days", () => {
    expect(defaultRange(new Date("2026-01-10T15:00:00Z"))).toEqual({
      from: "2025-12-12",
      to: "2026-01-10",
    })
  })
})

describe("dateRangeProblem", () => {
  it("tells a cleared input from an inverted range", () => {
    expect(dateRangeProblem("", "2026-03-01")).toBe("incomplete")
    expect(dateRangeProblem("2026-03-01", "")).toBe("incomplete")
    expect(dateRangeProblem("2026-03-05", "2026-03-01")).toBe("inverted")
    expect(dateRangeProblem("2026-03-01", "2026-03-05")).toBeNull()
    expect(dateRangeProblem("2026-03-01", "2026-03-01")).toBeNull()
  })

  it("allows exactly 366 days and refuses one more", () => {
    expect(dateRangeProblem("2025-03-01", "2026-03-01")).toBeNull() // 366 days inclusive
    expect(dateRangeProblem("2025-02-28", "2026-03-01")).toBe("tooLong")
  })
})
