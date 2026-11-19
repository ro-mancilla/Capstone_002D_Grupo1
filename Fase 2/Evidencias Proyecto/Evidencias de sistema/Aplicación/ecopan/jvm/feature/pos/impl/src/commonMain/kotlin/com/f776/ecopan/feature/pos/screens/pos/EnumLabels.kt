package com.f776.ecopan.feature.pos.screens.pos

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import ecopan.jvm.feature.pos.impl.generated.resources.Res
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_card
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_cash
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_other
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_transfer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one place a [PaymentMethod] becomes user-visible text. Enum constant names are an English
 * wire/storage detail (`CASH`, `TRANSFER`) and this app's UI is Spanish-only — rendering `.name`
 * anywhere leaks the former into the latter, which is exactly what the till list used to do.
 */
@Composable
internal fun PaymentMethod.label(): String = stringResource(labelResource())

/**
 * The same mapping for non-composable callers — notably the printed receipt, which is built inside
 * `PosViewModel` and is just as user-visible as the screen, only on paper.
 */
internal fun PaymentMethod.labelResource(): StringResource = when (this) {
    PaymentMethod.CASH -> Res.string.pos_payment_method_cash
    PaymentMethod.CARD -> Res.string.pos_payment_method_card
    PaymentMethod.TRANSFER -> Res.string.pos_payment_method_transfer
    PaymentMethod.OTHER -> Res.string.pos_payment_method_other
}

/** The icon that says what the method is; the chip and the selector share it so they cannot drift. */
internal fun PaymentMethod.icon(): ImageVector = when (this) {
    PaymentMethod.CASH -> EcopanIcons.Cash
    PaymentMethod.CARD -> EcopanIcons.CreditCard
    PaymentMethod.TRANSFER -> EcopanIcons.BankTransfer
    PaymentMethod.OTHER -> EcopanIcons.MoreOptions
}
