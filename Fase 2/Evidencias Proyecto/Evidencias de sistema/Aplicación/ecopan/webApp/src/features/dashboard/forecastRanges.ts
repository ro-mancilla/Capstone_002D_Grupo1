import { reportingDayStart } from "@/lib/reportingTime"
import { ACCURACY_WINDOW_DAYS } from "./forecastAccuracy"

/**
 * The two K8 windows of the forecast page, both aligned to reporting-zone day boundaries so the
 * overlay and the accuracy scoring cover whole days and the query keys only change at midnight:
 *
 *  * `overlay` -- the last 7 days of actuals plus the next 7 days of forecast;
 *  * `accuracy` -- the last [ACCURACY_WINDOW_DAYS] whole days up to the end of yesterday (today
 *    is still being sold, so it is never scored).
 */
export function forecastRanges(now: number) {
  return {
    overlay: { from: reportingDayStart(now, -6), to: reportingDayStart(now, 7) },
    accuracy: {
      from: reportingDayStart(now, -ACCURACY_WINDOW_DAYS),
      to: reportingDayStart(now) - 1,
    },
  }
}
