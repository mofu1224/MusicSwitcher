package dev.musicswitcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadDialog(onDismiss: () -> Unit, onDownload: (String) -> Boolean) {
    var url by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("URLから音楽を保存") },
        text = {
            Column {
                Text("ダウンロードが許可されたコンテンツのHTTPSリンクを貼り付けてください。")
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("音楽のURL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = url.isNotBlank(),
                onClick = {
                    error = when {
                        parseDownloadUrl(url) == null -> "有効なHTTPSのURLを入力してください。"
                        !onDownload(url) -> "保存先を確認して、もう一度試してください。"
                        else -> null
                    }
                },
            ) { Text("保存する") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
internal fun DownloadPanel(status: DownloadUi, onCancel: () -> Unit, onDismiss: () -> Unit) {
    when (status) {
        DownloadUi.Idle -> Unit
        is DownloadUi.Working -> {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            status.saving -> "フォルダに保存中…"
                            status.progress == null -> "音声を取得中…"
                            else -> "音声を取得中… ${status.progress}%"
                        },
                        Modifier.weight(1f),
                    )
                    if (!status.saving) TextButton(onClick = onCancel) { Text("キャンセル") }
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
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
            HorizontalDivider()
        }
    }
}
