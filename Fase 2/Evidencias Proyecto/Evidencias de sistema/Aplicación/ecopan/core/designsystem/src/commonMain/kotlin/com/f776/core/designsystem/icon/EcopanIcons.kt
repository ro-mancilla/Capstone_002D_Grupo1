package com.f776.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icon set. The design draws Lucide line icons (2px stroke, round caps, no fill); the closest
 * set the repository already ships is Material Icons Extended's **outlined** family, so that is the
 * only family used -- never `Filled`, never `Rounded`. Icons are decoration next to a word; an
 * icon-only control needs a `contentDescription` at the call site.
 */
object EcopanIcons {
    val Add: ImageVector get() = Icons.Outlined.Add
    val Remove: ImageVector get() = Icons.Outlined.Remove
    val Check: ImageVector get() = Icons.Outlined.Check
    val Close: ImageVector get() = Icons.Outlined.Close
    val Delete: ImageVector get() = Icons.Outlined.Delete
    val Search: ImageVector get() = Icons.Outlined.Search
    val SearchOff: ImageVector get() = Icons.Outlined.SearchOff
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val ExpandMore: ImageVector get() = Icons.Outlined.ExpandMore
    val ExpandLess: ImageVector get() = Icons.Outlined.ExpandLess
    val ArrowDown: ImageVector get() = Icons.Outlined.ArrowDownward
    val TrendingUp: ImageVector get() = Icons.Outlined.TrendingUp
    /** The croissant of the design's production tab. */
    val Croissant: ImageVector get() = Icons.Outlined.BakeryDining
    val ArrowBack: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    val ArrowForward: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowForward
    val Backspace: ImageVector get() = Icons.AutoMirrored.Outlined.Backspace

    // Status: always paired with a word.
    val CheckCircle: ImageVector get() = Icons.Outlined.CheckCircle
    val Warning: ImageVector get() = Icons.Outlined.WarningAmber
    val Error: ImageVector get() = Icons.Outlined.ErrorOutline
    val Info: ImageVector get() = Icons.Outlined.Info
    val Neutral: ImageVector get() = Icons.Outlined.Circle
    val Simulated: ImageVector get() = Icons.Outlined.Science

    // Sync.
    val CloudCheck: ImageVector get() = Icons.Outlined.CloudDone
    val CloudOff: ImageVector get() = Icons.Outlined.CloudOff
    val Refresh: ImageVector get() = Icons.Outlined.Sync

    // Domain.
    val Scale: ImageVector get() = Icons.Outlined.Scale
    val Receipt: ImageVector get() = Icons.AutoMirrored.Outlined.ReceiptLong
    val Print: ImageVector get() = Icons.Outlined.Print
    val Keyboard: ImageVector get() = Icons.Outlined.Keyboard
    val Inventory: ImageVector get() = Icons.Outlined.Inventory2
    val CashDrawer: ImageVector get() = Icons.Outlined.Inbox
    val CreditCard: ImageVector get() = Icons.Outlined.CreditCard
    val Cash: ImageVector get() = Icons.Outlined.Payments
    val BankTransfer: ImageVector get() = Icons.Outlined.AccountBalance
    val MoreOptions: ImageVector get() = Icons.Outlined.MoreHoriz
    val Bakery: ImageVector get() = Icons.Outlined.BakeryDining
}
