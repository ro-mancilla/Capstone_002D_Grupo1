import { mergeProps } from "@base-ui/react/merge-props"
import { useRender } from "@base-ui/react/use-render"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/** Ecopan design system, `badge-*`.
 *
 * A pill, 24px tall, 10px of side padding, a solid tinted ground with its matching status
 * foreground. The grounds are literal tokens rather than an alpha tint of the foreground, because
 * the design system measures each pair — `seed on seed-bg` is 5.61, and a `/10` of the same hue is
 * a different, unmeasured colour on every surface it lands on.
 *
 * Status is never carried by hue alone: a badge holds a word, so a red pill and an amber pill stay
 * distinguishable in greyscale and to a colourblind reader. */
const badgeVariants = cva(
  "group/badge ds-control inline-flex h-6 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full border border-transparent px-2.5 text-xs font-medium tracking-[0.01em] whitespace-nowrap focus-visible:ring-3 focus-visible:ring-ring/50 has-data-[icon=inline-end]:pr-2 has-data-[icon=inline-start]:pl-2 aria-invalid:border-destructive aria-invalid:ring-destructive/20 [&>svg]:pointer-events-none [&>svg]:size-3!",
  {
    variants: {
      variant: {
        // The brand badge: crust on its own quiet ground, 6.66:1.
        default: "bg-crust-soft text-crust [a]:hover:bg-crust-line",
        secondary:
          "bg-flour-inset text-ink-body [a]:hover:bg-flour-line",
        destructive:
          "bg-danger-bg text-danger [a]:hover:bg-danger-line focus-visible:ring-destructive/25",
        success: "bg-success-bg text-success [a]:hover:bg-success-line",
        warning: "bg-warning-bg text-warning [a]:hover:bg-warning-line",
        info: "bg-info-bg text-info [a]:hover:bg-info-line",
        outline:
          "border-border text-ink-muted [a]:hover:bg-flour-sunken [a]:hover:text-ink-body",
        ghost: "text-ink-muted hover:bg-flour-sunken hover:text-ink-body",
        link: "text-primary underline-offset-4 hover:underline",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  render,
  ...props
}: useRender.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: "span",
    props: mergeProps<"span">(
      {
        className: cn(badgeVariants({ variant }), className),
      },
      props
    ),
    render,
    state: {
      slot: "badge",
      variant,
    },
  })
}

export { Badge, badgeVariants }
