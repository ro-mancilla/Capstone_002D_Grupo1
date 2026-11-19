import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
import { z } from "zod"
import { useNavigate, useRouter, useRouterState } from "@tanstack/react-router"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { ApiError } from "@/api/http"
import { resolveLanding } from "@/lib/destinations"
import { MANAGER_APP_ROLES } from "@/api/types"
import { useAuth } from "@/features/auth/AuthContext"
import {
  DEV_QUICK_LOGIN_PROFILES,
  DEV_QUICK_LOGIN_TENANT,
  devQuickLoginEnabled,
} from "@/features/auth/devQuickLogin"
import { classify, errorMessage } from "@/lib/apiErrors"
import { t } from "@/lib/i18n"
import { roleName } from "@/lib/labels"

const loginSchema = z.object({
  tenantSlug: z.string().min(1, t.auth.login.validation.tenantSlugRequired),
  username: z.string().min(1, t.auth.login.validation.usernameRequired),
  password: z.string().min(1, t.auth.login.validation.passwordRequired),
})

type LoginFormValues = z.infer<typeof loginSchema>

/** `/login` (plan 07 §3): tenant slug + username + password against the real `POST
 * /auth/login` shape. Role check happens client-side, after a successful auth call --
 * CASHIER/BAKER get a friendly rejection instead of the dashboard (the backend also 403s
 * manager-only routes, this is just so they never see a confusing empty shell first). */
export function LoginPage() {
  const auth = useAuth()
  const router = useRouter()
  const navigate = useNavigate()
  const search = useRouterState({ select: (state) => state.location.search as { redirect?: string } })
  const [formError, setFormError] = useState<string | null>(null)
  const [roleRejected, setRoleRejected] = useState(false)

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { tenantSlug: "", username: "", password: "" },
  })

  async function onSubmit(values: LoginFormValues) {
    setFormError(null)
    setRoleRejected(false)
    try {
      const session = await auth.login(values)
      if (!MANAGER_APP_ROLES.includes(session.role)) {
        setRoleRejected(true)
        return
      }
      await router.invalidate()
      const landing = resolveLanding(session, search.redirect)
      // Entitled to nothing: fall through to the rejection card below rather than navigating
      // into a route the guards would refuse. `auth.session` is set by now, so `stranded`
      // picks it up on the re-render.
      if (landing) {
        // `landing` is just the pathname; when it is the page they were sent away from, go back
        // to the exact href so its query string (filters, date range) survives the round trip.
        const requested = search.redirect
        if (
          requested !== undefined &&
          requested.startsWith("/") &&
          !requested.startsWith("//") &&
          requested.split(/[?#]/)[0] === landing
        ) {
          router.history.push(requested)
        } else {
          navigate({ to: landing })
        }
      }
    } catch (error) {
      // Deliberately kept out of `errorMessage`'s table: the login form names the tenant field
      // ("Usuario, contraseña o panadería incorrectos"), which the shared sentence for this code
      // cannot, since everywhere else there is no tenant field to point at.
      if (error instanceof ApiError && error.code === "INVALID_CREDENTIALS") {
        setFormError(t.auth.login.invalidCredentials)
      } else if (classify(error) === "rateLimited") {
        setFormError(errorMessage(error))
      } else {
        setFormError(t.auth.login.genericError)
      }
    }
  }

  // A signed-in session with nowhere to go: every section is gated behind a module this tenant
  // has switched off. Same surface as the wrong-role rejection, which `_authed` and the route
  // guards both redirect here for.
  const stranded = auth.session !== null && resolveLanding(auth.session) === null
  const rejection = roleRejected ? t.auth.roleRejected : stranded ? t.auth.noDestinations : null

  if (rejection) {
    return (
      <div className="flex min-h-svh items-center justify-center p-6">
        <Card className="w-full max-w-sm">
          <CardHeader>
            <CardTitle>{rejection.title}</CardTitle>
            <CardDescription>{rejection.body}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button
              className="w-full"
              variant="outline"
              onClick={() => {
                auth.clearSession()
                setRoleRejected(false)
              }}
            >
              {rejection.backToLogin}
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="font-display text-2xl font-semibold">{t.auth.login.title}</CardTitle>
          <CardDescription>{t.auth.login.subtitle}</CardDescription>
        </CardHeader>
        <CardContent>
          {devQuickLoginEnabled && (
            <div className="mb-6">
              <p className="eyebrow mb-2">{t.auth.login.devQuickLogin}</p>
              <div className="flex flex-wrap gap-2">
                {DEV_QUICK_LOGIN_PROFILES.map((profile) => (
                  <Button
                    key={profile.username}
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={form.formState.isSubmitting}
                    onClick={() =>
                      onSubmit({
                        tenantSlug: DEV_QUICK_LOGIN_TENANT,
                        username: profile.username,
                        password: profile.password,
                      })
                    }
                  >
                    {roleName(profile.role)}
                  </Button>
                ))}
              </div>
            </div>
          )}
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <FieldGroup>
              <Controller
                name="tenantSlug"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="login-tenant-slug">
                      {t.auth.login.tenantSlugLabel}
                    </FieldLabel>
                    <Input
                      {...field}
                      id="login-tenant-slug"
                      placeholder={t.auth.login.tenantSlugPlaceholder}
                      autoComplete="organization"
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
              <Controller
                name="username"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="login-username">{t.auth.login.usernameLabel}</FieldLabel>
                    <Input
                      {...field}
                      id="login-username"
                      placeholder={t.auth.login.usernamePlaceholder}
                      autoComplete="username"
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
              <Controller
                name="password"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="login-password">{t.auth.login.passwordLabel}</FieldLabel>
                    <Input
                      {...field}
                      id="login-password"
                      type="password"
                      placeholder={t.auth.login.passwordPlaceholder}
                      autoComplete="current-password"
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
              {formError && (
                <p role="alert" className="text-destructive text-sm">
                  {formError}
                </p>
              )}
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? t.auth.login.submitting : t.auth.login.submit}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
