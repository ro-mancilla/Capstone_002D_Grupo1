package com.f776.ecopan.api.core

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlin.uuid.Uuid

/**
 * The branch a read is allowed to cover (#127). A principal bound to a branch (BRANCH_MANAGER, a
 * device session) only ever reads that branch; a tenant-wide one (GENERAL_MANAGER, ADMIN) reads any
 * branch, or the whole tenant when it names none.
 */
sealed interface BranchScope {
    /** `null` means tenant-wide. */
    val branchId: Uuid?

    data class OneBranch(override val branchId: Uuid) : BranchScope

    data object TenantWide : BranchScope {
        override val branchId: Uuid? = null
    }
}

/** `null` when [requested] is a branch this principal is not bound to. */
fun PrincipalContext.scopeFor(requested: Uuid?): BranchScope? = when {
    branchId == null -> requested?.let(BranchScope::OneBranch) ?: BranchScope.TenantWide
    requested == null || requested == branchId -> BranchScope.OneBranch(branchId)
    else -> null
}

/** The 403 for a branch the principal is not bound to; one body for every route that checks. */
suspend fun ApplicationCall.respondBranchForbidden() =
    respond(HttpStatusCode.Forbidden, ErrorBody(code = "BRANCH_FORBIDDEN", message = "principal is scoped to a different branch"))

/**
 * Resolves the `branchId` query parameter against the caller. Returns `null` after answering 403
 * `BRANCH_FORBIDDEN` (the same code Ingredient inventory uses), so a handler writes
 * `val scope = call.branchScopeOrRespond() ?: return@get`.
 */
suspend fun ApplicationCall.branchScopeOrRespond(): BranchScope? {
    val requested = request.queryParameters["branchId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    return principalContext().scopeFor(requested) ?: run {
        respondBranchForbidden()
        null
    }
}
