package dev.communicator.helmchatrisen.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.communicator.helmchatrisen.BuildConfig
import dev.communicator.helmchatrisen.R
import dev.communicator.helmchatrisen.ui.theme.HelmChatRisenTheme

/**
 * Dialog presenting the release metadata shipped with this build.
 *
 * Values are read from [BuildConfig], so they always match `app/build.gradle.kts` and
 * `RELEASE_NOTES.md`. The labels and formatting stay in the translatable string catalogs.
 *
 * @param onDismiss called when the rider closes the dialog or taps outside it.
 */
@Composable
fun AppInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_info_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoLine(
                    stringResource(R.string.app_info_name, stringResource(R.string.app_name)),
                )
                InfoLine(stringResource(R.string.app_info_version, BuildConfig.VERSION_NAME))
                InfoLine(
                    stringResource(
                        R.string.app_info_version_code,
                        BuildConfig.VERSION_CODE.toString(),
                    ),
                )
                InfoLine(stringResource(R.string.app_info_tag, BuildConfig.RELEASE_TAG))
                InfoLine(stringResource(R.string.app_info_release_date, BuildConfig.RELEASE_DATE))
                InfoLine(stringResource(R.string.app_info_min_android, BuildConfig.MIN_ANDROID))
                InfoLine(stringResource(R.string.app_info_sdk, BuildConfig.SDK_LEVELS))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_info_close))
            }
        },
    )
}

/**
 * One formatted, localized label/value line of the dialog.
 *
 * @param text the complete line, including the translated label and its value.
 */
@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Preview of the release-metadata dialog. */
@Preview(showBackground = true)
@Composable
private fun AppInfoDialogPreview() {
    HelmChatRisenTheme {
        AppInfoDialog {}
    }
}
