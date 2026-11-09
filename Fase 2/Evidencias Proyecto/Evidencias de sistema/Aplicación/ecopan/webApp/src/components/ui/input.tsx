import * as React from "react"

import { Input as InputPrimitive } from "@base-ui/react/input"

import { cn } from "@/lib/utils"

/** Ecopan design system, `input`: 40px tall, 12px radius, 16px of side
 * padding, card fill, `ink-strong` text. The border is `--input` rather than `--border` — a white
 * field on a warm page has nothing but its edge to identify it, so that one token is held to the
 * 3:1 non-text floor while dividers keep the design system's hairline. */
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        "ds-control h-10 w-full min-w-0 rounded-sm border border-input bg-card px-4 py-1 text-base text-ink-strong outline-none file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:disabled:bg-muted dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    />
  )
}

export { Input }
