import { z } from "zod"

// The dashboard is served with a Content-Security-Policy that forbids eval. Without this, zod probes
// for `new Function` when the first schema is built and the browser reports the probe as a violation.
z.config({ jitless: true })
