package com.f776.ecopan.feature.settings.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.core.designsystem.theme.EcopanTheme
import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.about_title
import ecopan.feature.settings.impl.generated.resources.close_button
import ecopan.feature.settings.impl.generated.resources.help_message
import ecopan.feature.settings.impl.generated.resources.illustrations_by_label
import ecopan.feature.settings.impl.generated.resources.privacy_policy_link
import ecopan.feature.settings.impl.generated.resources.privacy_policy_title
import ecopan.feature.settings.impl.generated.resources.storyset_name
import ecopan.feature.settings.impl.generated.resources.storyset_url
import ecopan.feature.settings.impl.generated.resources.support_email
import ecopan.feature.settings.impl.generated.resources.thanks_message
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AboutDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val supportEmail = stringResource(Res.string.support_email)
    val helpMessage = stringResource(Res.string.help_message)
    val illustrationsByLabel = stringResource(Res.string.illustrations_by_label)
    val storysetName = stringResource(Res.string.storyset_name)
    val storysetUrl = stringResource(Res.string.storyset_url)
    val thanksMessage = stringResource(Res.string.thanks_message)
    val privacyPolicyTitle = stringResource(Res.string.privacy_policy_title)
    val privacyPolicyLink = stringResource(Res.string.privacy_policy_link)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(Res.string.about_title))
        },
        text = {
            Column {
                // Storyset attribution
                val storysetLink = buildAnnotatedString {
                    append(illustrationsByLabel)

                    val link = LinkAnnotation.Url(
                        url = storysetUrl,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )

                    withLink(link) {
                        append(storysetName)
                    }
                }

                Text(
                    text = storysetLink,
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    text = thanksMessage,
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )

                // Help message with email link
                val helpEmailLink = buildAnnotatedString {
                    append(helpMessage)

                    val link = LinkAnnotation.Url(
                        url = "mailto:$supportEmail",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )

                    withLink(link) {
                        append(supportEmail)
                    }
                    append(".")
                }

                Text(
                    text = helpEmailLink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                val privacyPolicyLink = buildAnnotatedString {
                    pushLink(
                        LinkAnnotation.Url(
                            url = privacyPolicyLink,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    )
                    append(privacyPolicyTitle)
                    pop()
                }

                Text(
                    text = privacyPolicyLink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.close_button))
            }
        },
        modifier = modifier
    )
}


@LocalizedPreview
@Composable
private fun AboutDialogPreview() {
    EcopanTheme {
        AboutDialog(onDismissRequest = {})
    }
}