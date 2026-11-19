import { createFileRoute } from "@tanstack/react-router"
import { z } from "zod"
import { LoginPage } from "@/features/auth/LoginPage"

const loginSearchSchema = z.object({
  redirect: z.string().optional(),
})

export const Route = createFileRoute("/login")({
  validateSearch: loginSearchSchema,
  component: LoginPage,
})
