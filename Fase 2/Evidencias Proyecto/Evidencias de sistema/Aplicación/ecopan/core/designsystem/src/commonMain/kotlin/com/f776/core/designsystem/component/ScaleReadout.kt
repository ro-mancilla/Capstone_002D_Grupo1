package com.f776.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanTheme
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.scale_driver_manual
import ecopan.core.designsystem.generated.resources.scale_driver_real
import ecopan.core.designsystem.generated.resources.scale_driver_simulated
import ecopan.core.designsystem.generated.resources.scale_readout_cd
import ecopan.core.designsystem.generated.resources.scale_stable
import ecopan.core.designsystem.generated.resources.scale_stabilizing
import org.jetbrains.compose.resources.stringResource

/** Whether the reading has settled. Only a [Stable] reading may be added to a sale. */
enum class ScaleState { Stabilizing, Stable }

/** Where the reading comes from. [Simulated] is never real data and says so with the honey chip. */
enum class ScaleDriver { Real, Manual, Simulated }

/**
 * The live scale reading: a 68sp tabular weight, a state row with an icon **and** a word
 * ("Estabilizando…" or "Estable"), the driver chip and, when known, the price in the price colour.
 * A stabilising reading is muted; the icon only spins when motion is not reduced.
 *
 * [weight] is the already-formatted number ("0,842"); formatting and locale belong to the caller.
 */
@Composable
fun ScaleReadout(
    weight: String,
    unit: String,
    state: ScaleState,
    driver: ScaleDriver,
    modifier: Modifier = Modifier,
    label: String? = null,
    price: String? = null,
    /** Overrides the words of the driver chip, e.g. "Balanza simulada · lectura de demo". */
    driverLabel: String? = null,
) {
    val c = EcopanTheme.colors
    val typography = EcopanTheme.typography
    val stable = state == ScaleState.Stable
    val stateWord = stringResource(if (stable) Res.string.scale_stable else Res.string.scale_stabilizing)
    val description = stringResource(Res.string.scale_readout_cd, weight, unit, stateWord)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.sunken, EcopanShapes.large)
            .padding(20.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            if (label != null) Text(label, style = typography.label, color = c.inkMuted)
            DriverChip(driver, driverLabel)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                weight,
                style = typography.scaleReadout,
                color = if (stable) c.inkStrong else c.inkMuted,
                maxLines = 1,
            )
            Text(
                unit,
                style = typography.title.copy(fontSize = 28.sp, lineHeight = 36.sp),
                color = c.inkMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            StateRow(stable, stateWord)
            if (price != null) Text(price, style = typography.numeric, color = c.price, maxLines = 1)
        }
    }
}

@Composable
private fun StateRow(stable: Boolean, word: String) {
    val c = EcopanTheme.colors
    val ink = if (stable) c.success.fg else c.inkBody
    val rotation = if (!stable && !EcopanTheme.motion.reduced) {
        val transition = rememberInfiniteTransition(label = "stabilizing")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        )
        angle
    } else {
        0f
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = if (stable) EcopanIcons.CheckCircle else EcopanIcons.Refresh,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
        Text(word, style = EcopanTheme.typography.label.copy(fontSize = 15.sp, lineHeight = 20.sp), color = ink)
    }
}

@Composable
private fun DriverChip(driver: ScaleDriver, labelOverride: String?) {
    when (driver) {
        ScaleDriver.Real -> StatusPill(labelOverride ?: stringResource(Res.string.scale_driver_real), StatusTone.Neutral, icon = EcopanIcons.Scale)
        ScaleDriver.Manual -> StatusPill(labelOverride ?: stringResource(Res.string.scale_driver_manual), StatusTone.Neutral, icon = EcopanIcons.Keyboard)
        ScaleDriver.Simulated -> SimulatedChip(label = labelOverride ?: stringResource(Res.string.scale_driver_simulated))
    }
}
