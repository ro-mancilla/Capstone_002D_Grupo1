import { useEffect, useState } from "react"
import { reportingDate } from "./reportingTime"

/** The reporting-zone date (`yyyy-MM-dd`), re-evaluated every minute so a page left open across
 * midnight rolls over instead of keeping yesterday's windows. */
export function useReportingToday(): string {
  const [today, setToday] = useState(() => reportingDate(Date.now()))
  useEffect(() => {
    const id = setInterval(() => setToday(reportingDate(Date.now())), 60_000)
    return () => clearInterval(id)
  }, [])
  return today
}
