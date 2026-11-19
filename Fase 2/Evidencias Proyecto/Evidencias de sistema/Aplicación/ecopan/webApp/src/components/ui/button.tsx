import { Button as ButtonPrimitive } from "@base-ui/react/button"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/** Ecopan design system, `button-*`.
 *
 * Pill geometry, `label` type (14px/500), and a 40px default height — the touch-capable floor is
 * the baseline here, not an afterthought, because the same system dresses a till. `lg` is the
 * design system's `button-primary-touch` at 54px.
 *
 * Flat: a primary button carries no shadow and does not lift on press. The whole press ladder is
 * colour — crust → crust-hover → crust-active — which is why `active:translate-y-px` is gone. */
const buttonVariants = cva(
  "group/button ds-control inline-flex shrink-0 items-center justify-center rounded-full border border-transparent bg-clip-padding text-sm font-medium tracking-[0.01em] whitespace-nowrap outline-none select-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default:
          "bg-primary text-primary-foreground hover:bg-crust-hover active:bg-crust-active aria-expanded:bg-crust-hover",
        outline:
          "border-border bg-card text-foreground hover:border-flour-line-strong hover:bg-secondary aria-expanded:border-flour-line-strong aria-expanded:bg-secondary",
        secondary:
          "bg-secondary text-secondary-foreground hover:bg-flour-inset aria-expanded:bg-flour-inset",
        ghost:
          "text-ink-muted hover:bg-muted hover:text-foreground aria-expanded:bg-muted aria-expanded:text-foreground",
        destructive:
          "bg-danger-bg text-destructive hover:bg-danger-line focus-visible:ring-destructive/25",
        // The accent fill. Sparingly, and never for a destructive or a primary action.
        accent:
          "bg-peach text-ink-strong hover:bg-peach-hover active:bg-peach-hover",
        link: "text-primary underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 gap-2 px-5 has-data-[icon=inline-end]:pr-4 has-data-[icon=inline-start]:pl-4",
        xs: "h-7 gap-1 px-3 text-xs [&_svg:not([class*='size-'])]:size-3",
        sm: "h-8 gap-1.5 px-4 text-[0.8rem] [&_svg:not([class*='size-'])]:size-3.5",
        // `button-primary-touch` — the till's primary action.
        lg: "h-[54px] gap-2 px-7 text-base",
        icon: "size-10",
        "icon-xs": "size-7 [&_svg:not([class*='size-'])]:size-3",
        "icon-sm": "size-8 [&_svg:not([class*='size-'])]:size-3.5",
        "icon-lg": "size-[54px]",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  ...props
}: ButtonPrimitive.Props & VariantProps<typeof buttonVariants>) {
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
