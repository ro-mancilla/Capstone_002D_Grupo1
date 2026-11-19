package com.f776.ecopan.domain.hardware

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Thrown from inside a regex match when it ran past its time budget. */
class RegexBudgetExceeded : RuntimeException("regex exceeded its time budget")

/**
 * The frame regex is user-typed and runs on the serial event thread, so a pattern with nested
 * quantifiers (`(a+)+$`) could pin that thread on a single hostile or merely unlucky frame. The
 * matcher reads its input through `CharSequence.get`, so a wrapper that checks a deadline there is
 * a portable way to abort it without threads.
 *
 * @throws RegexBudgetExceeded when the match did not finish within [budgetMs].
 */
fun Regex.findWithinBudget(input: String, budgetMs: Long = DEFAULT_REGEX_BUDGET_MS): MatchResult? =
    find(DeadlineCharSequence(input, budgetMs))

const val DEFAULT_REGEX_BUDGET_MS = 50L

private class DeadlineCharSequence(
    private val delegate: CharSequence,
    budgetMs: Long,
) : CharSequence {
    private val deadline = TimeSource.Monotonic.markNow() + budgetMs.milliseconds
    private var reads = 0

    override val length: Int get() = delegate.length

    override fun get(index: Int): Char {
        // Clock reads are not free; every 256th access is plenty to stop an exponential match.
        if (++reads and 0xFF == 0 && deadline.hasPassedNow()) throw RegexBudgetExceeded()
        return delegate[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        delegate.subSequence(startIndex, endIndex)

    override fun toString(): String = delegate.toString()
}
