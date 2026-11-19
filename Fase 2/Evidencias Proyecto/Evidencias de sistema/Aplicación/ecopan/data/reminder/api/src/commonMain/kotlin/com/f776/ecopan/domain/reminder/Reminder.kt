package com.f776.ecopan.domain.reminder

import kotlin.time.Instant

/**
 * A single one-shot reminder, scoped by [id] so platform schedulers can address, replace, or
 * cancel it independently of whatever domain object it's attached to.
 */
data class Reminder(
    val id: String,
    val triggerAt: Instant,
    val title: String,
    val message: String,
)
