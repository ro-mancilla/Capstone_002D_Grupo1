package com.f776.test

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

fun hasRole(role: Role) = SemanticsMatcher("Searches for role: $role") {
    it.config.getOrNull(SemanticsProperties.Role) == role
}