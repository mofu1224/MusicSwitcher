package dev.musicswitcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: MusicViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val connection = rememberPlaybackConnection()
            val controller = connection.controller
            val playing = rememberNowPlaying(controller)
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                if (result.resultCode == Activity.RESULT_OK && data != null) {
                    data.data?.let { tree ->
                        model.selectFolder(tree, data.flags) {
                            controller?.stop()
                            controller?.clearMediaItems()
                        }
                    }
                }
            }
            val dark = state.darkMode ?: true
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MusicTheme(dark) {
                MusicScreen(
                    state = state,
                    playing = playing,
                    controller = controller,
                    connectionFailed = connection.failed,
                    darkMode = dark,
                    onToggleTheme = { model.setDarkMode(!dark) },
                    onChooseFolder = {
                        folderPicker.launch(
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                            ),
                        )
                    },
                    onRefresh = model::refresh,
                    onDownload = model::download,
                    onCancelDownload = model::cancelDownload,
                    onDismissDownload = model::dismissDownload,
                )
            }
        }
    }
}
