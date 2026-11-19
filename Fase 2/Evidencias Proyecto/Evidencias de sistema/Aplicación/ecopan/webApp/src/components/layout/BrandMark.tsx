import { cn } from "@/lib/utils"

/**
 * The wheat-ear mark. Drawn inline rather than shipped as an asset so it inherits `currentColor`
 * and therefore re-themes with the sidebar in both modes without a second file or a filter hack.
 *
 * Geometry is a central stalk with five paired grains, scaled off a 24-unit box so it stays
 * crisp at the 16px it renders at in a collapsed sidebar.
 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={cn("size-5", className)}
    >
      {/* stalk */}
      <path d="M12 21V7" />
      {/* grain pairs, tip to base */}
      <path d="M12 7c0-1.9 1-3.4 2.4-4.2C14.7 4.5 13.8 6.2 12 7Z" />
      <path d="M12 7c0-1.9-1-3.4-2.4-4.2C9.3 4.5 10.2 6.2 12 7Z" />
      <path d="M12 11.5c.4-1.9 1.7-3.1 3.3-3.5-.1 1.8-1.4 3.2-3.3 3.5Z" />
      <path d="M12 11.5c-.4-1.9-1.7-3.1-3.3-3.5.1 1.8 1.4 3.2 3.3 3.5Z" />
      <path d="M12 16c.4-1.9 1.7-3.1 3.3-3.5-.1 1.8-1.4 3.2-3.3 3.5Z" />
      <path d="M12 16c-.4-1.9-1.7-3.1-3.3-3.5.1 1.8 1.4 3.2 3.3 3.5Z" />
    </svg>
  )
}
