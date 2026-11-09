"use client"

import { useEffect, useState } from "react"
import { Toaster as Sonner, type ToasterProps } from "sonner"
import { t } from "@/lib/i18n"
import { CircleCheckIcon, InfoIcon, TriangleAlertIcon, OctagonXIcon, Loader2Icon } from "lucide-react"

/** This app has its own three-state theme system (`lib/theme.ts`), not next-themes -- reading
 * the `.dark` class directly (rather than pulling in a second theme library just for this one
 * shadcn-generated component) keeps sonner's toasts in sync with the real toggle, including the
 * "system" case where the OS preference can differ from what `next-themes` would have guessed. */
function useResolvedTheme(): "light" | "dark" {
  const [isDark, setIsDark] = useState(
    () => typeof document !== "undefined" && document.documentElement.classList.contains("dark")
  )

  useEffect(() => {
    const target = document.documentElement
    const observer = new MutationObserver(() => setIsDark(target.classList.contains("dark")))
    observer.observe(target, { attributes: true, attributeFilter: ["class"] })
    return () => observer.disconnect()
  }, [])

  return isDark ? "dark" : "light"
}

const Toaster = ({ ...props }: ToasterProps) => {
  const theme = useResolvedTheme()

  return (
    <Sonner
      theme={theme}
      className="toaster group"
      closeButton
      mobileOffset={16}
      containerAriaLabel={t.shell.notifications}
      icons={{
        success: (
          <CircleCheckIcon className="size-4" />
        ),
        info: (
          <InfoIcon className="size-4" />
        ),
        warning: (
          <TriangleAlertIcon className="size-4" />
        ),
        error: (
          <OctagonXIcon className="size-4" />
        ),
        loading: (
          <Loader2Icon className="size-4 animate-spin" />
        ),
      }}
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius)",
        } as React.CSSProperties
      }
      toastOptions={{
        classNames: {
          toast: "cn-toast",
          actionButton: "!bg-primary !text-primary-foreground",
          closeButton: "!bg-popover !text-popover-foreground !border-border",
        },
        closeButtonAriaLabel: t.common.close,
      }}
      {...props}
    />
  )
}

export { Toaster }
