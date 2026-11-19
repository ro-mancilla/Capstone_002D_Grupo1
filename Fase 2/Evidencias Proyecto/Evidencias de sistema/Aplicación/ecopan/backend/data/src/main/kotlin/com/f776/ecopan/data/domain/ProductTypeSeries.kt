package com.f776.ecopan.data.domain

/**
 * One analytics series per product type, whatever spelling the rows carry (ticket #27). Rows are
 * grouped on `ProductTypeKey`, so "Pan" and "PAN" are one series; the label shown is the most
 * frequent spelling among [names] (ties prefer mixed case, then accents, then alphabetical), so it is stable across calls. Rows
 * written through the managed list all share one spelling already -- this is the guard for rows
 * that predate it.
 */
class ProductTypeSeries(names: Collection<String>) {
    private val display: Map<String, String> = names.groupBy { ProductTypeKey.of(it) }.mapValues { (_, spellings) ->
        spellings.groupingBy { it }.eachCount().entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    // On a tie prefer a hand-written spelling over SHOUTING, then the accented one.
                    .thenByDescending { it.key != it.key.uppercase() }
                    .thenByDescending { entry -> entry.key.any { it.code > 127 } }
                    .thenBy { it.key }
            )
            .first().key
    }

    fun key(name: String): String = ProductTypeKey.of(name)

    fun label(name: String): String = display[key(name)] ?: name
}
