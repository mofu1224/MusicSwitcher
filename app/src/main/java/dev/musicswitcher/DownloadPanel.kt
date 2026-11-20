package dev.musicswitcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadDialog(onDismiss: () -> Unit, onDownload: (String) -> Boolean) {
    var url by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val submit = {
        val link = parseDownloadUrl(url)
        error = when {
            link == null -> "Use an HTTPS link."
            !onDownload(link) -> "Check the folder and retry."
            else -> null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        title = { Text("Import link") },
        text = {
            Column {
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("URL") },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) submit() }),
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = url.isNotBlank(),
                onClick = submit,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun DownloadPanel(item: DownloadItem, onCancel: () -> Unit, onDismiss: () -> Unit) {
    when (val status = item.status) {
        DownloadUi.Queued -> {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Queued #${item.id}", Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            HorizontalDivider()
        }
        is DownloadUi.Working -> {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            status.saving -> "Saving"
                            status.progress == null -> "Fetching"
                            else -> "Fetching ${status.progress}%"
                        } + " #${item.id}",
                        Modifier.weight(1f),
                    )
                    if (!status.saving) TextButton(onClick = onCancel) { Text("Cancel") }
                }
                if (status.saving || status.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { status.progress / 100f }, Modifier.fillMaxWidth())
            }
            HorizontalDivider()
        }
        is DownloadUi.Message -> {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(status.text, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            HorizontalDivider()
        }
    }
}
