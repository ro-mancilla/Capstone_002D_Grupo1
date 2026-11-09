package com.f776.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanElevation
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags
import com.f776.core.designsystem.theme.ecopanShadow

/** Ground of an [EcopanCard]. Contrast comes from card-on-page, not from borders. */
enum class EcopanCardTone {
    /** White card with the soft warm shadow. */
    Default,

    /** Flat, on the sunken ground: grouped content inside another card. */
    Sunken,

    /** Soft crust fill: the selected or highlighted card. */
    Accent,

    /** Flat with a line instead of a shadow. */
    Outlined,

    /** Honey fill with a dashed honey border: a device or a feature that is a demo, not real. */
    Simulated,
}

/**
 * The 22dp card. Pass [onClick] to make it a button-like surface (hover moves the fill one step,
 * keyboard focus draws the ring); without it the card is inert content.
 */
@Composable
fun EcopanCard(
    modifier: Modifier = Modifier,
    tone: EcopanCardTone = EcopanCardTone.Default,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(EcopanSpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = EcopanTheme.colors
    val shape = EcopanShapes.card
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val rest = when (tone) {
        EcopanCardTone.Default, EcopanCardTone.Outlined -> c.card
        EcopanCardTone.Simulated -> c.honey1
        EcopanCardTone.Sunken -> c.sunken
        EcopanCardTone.Accent -> c.crustSoft
    }
    val hover = when (tone) {
        EcopanCardTone.Accent -> c.crustLine
        else -> c.sunken
    }
    val fill = if (onClick != null && flags.hovered) hover else rest
    val border = when (tone) {
        EcopanCardTone.Default -> c.hairline
        EcopanCardTone.Sunken -> c.hairline
        EcopanCardTone.Accent -> c.crustLine
        EcopanCardTone.Outlined -> c.line
        EcopanCardTone.Simulated -> c.honey3
    }
    val simulated = tone == EcopanCardTone.Simulated
    Column(
        modifier = modifier
            .then(if (tone == EcopanCardTone.Default) Modifier.ecopanShadow(EcopanElevation.Card, shape) else Modifier)
            .focusRing(onClick != null && flags.focused, shape, c.focusRing)
            .clip(shape)
            .background(fill)
            .then(
                if (simulated) {
                    Modifier.drawBehind {
                        val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        drawOutline(shape.createOutline(size, layoutDirection, this), border, style = stroke)
                    }
                } else {
                    Modifier.border(BorderStroke(1.dp, border), shape)
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.inkBody) { content() }
    }
}
