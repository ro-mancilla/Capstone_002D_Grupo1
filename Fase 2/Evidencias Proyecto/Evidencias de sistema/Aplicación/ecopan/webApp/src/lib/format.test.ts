import { describe, expect, it } from "vitest"
import { formatGramsAsKg, formatSuggestedKg } from "./format"

describe("formatGramsAsKg", () => {
  it("shows the exact gram, never truncating or dropping digits", () => {
    expect(formatGramsAsKg(3869)).toBe("3,869 kg")
    expect(formatGramsAsKg(194_048)).toBe("194,048 kg")
  })

  it("keeps three decimals so a list never mixes precisions", () => {
    expect(formatGramsAsKg(4940)).toBe("4,940 kg")
    expect(formatGramsAsKg(20_000)).toBe("20,000 kg")
  })

  it("makes a preview's lines add up to the batch weight", () => {
    const lines = [6000, 3869, 131]
    const total = lines.reduce((a, b) => a + b, 0)
    expect(total).toBe(10_000)
    expect(formatGramsAsKg(total)).toBe("10,000 kg")
  })
})

describe("formatSuggestedKg", () => {
  it("rounds half up to 0,1 kg and always shows the tenth", () => {
    expect(formatSuggestedKg(17_250)).toBe("17,3 kg")
    expect(formatSuggestedKg(3869)).toBe("3,9 kg")
    expect(formatSuggestedKg(22_900)).toBe("22,9 kg")
    expect(formatSuggestedKg(12_000)).toBe("12,0 kg")
  })
})
