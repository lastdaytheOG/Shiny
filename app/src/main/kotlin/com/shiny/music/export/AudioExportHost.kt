package com.shiny.music.export

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidTypography

/**
 * The export's visible half: a quiet "Preparing" panel that can be cancelled, the system save
 * dialog, then "Export complete" with Share. Hosted once at the root of the app, so closing
 * the menu that started the export does not end it.
 */
@Composable
fun AudioExportHost() {
    val context = LocalContext.current
    val state by AudioExporter.state.collectAsState()

    val saveDialog = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AudioExporter.MP3_MIME),
    ) { uri ->
        if (uri == null) {
            AudioExporter.saveCancelled()
            Toast.makeText(context, "Export cancelled", Toast.LENGTH_SHORT).show()
        } else {
            AudioExporter.save(context, uri)
        }
    }

    LaunchedEffect(state) {
        val ready = state as? AudioExporter.State.ReadyToSave ?: return@LaunchedEffect
        runCatching { saveDialog.launch(ready.fileName) }.onFailure {
            AudioExporter.dismiss()
            Toast.makeText(context, "This device doesn't have a way to save files.", Toast.LENGTH_LONG).show()
        }
    }

    when (val current = state) {
        is AudioExporter.State.Preparing -> ProgressPanel(
            // A streamed song arrives first (with a percentage), then is converted.
            title = when (val progress = current.progress) {
                null -> "Preparing MP3"
                else -> if (progress < 1f) "Downloading · ${(progress * 100).toInt()}%" else "Converting to MP3"
            },
            message = current.title,
            progress = current.progress?.takeIf { it < 1f },
            onCancel = { AudioExporter.cancel() },
        )
        is AudioExporter.State.Saving -> ProgressPanel(title = "Saving", message = current.fileName, onCancel = null)
        is AudioExporter.State.Done -> LiquidAlert(
            title = "Export complete",
            message = current.fileName,
            confirmLabel = "Share",
            dismissLabel = "Done",
            onConfirm = {
                runCatching { AudioExporter.share(context, current.file, current.fileName) }.onFailure {
                    Toast.makeText(context, "Nothing on this device can share this file.", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { AudioExporter.dismiss() },
        )
        is AudioExporter.State.Failed -> LiquidAlert(
            title = "Couldn't export",
            message = current.message,
            confirmLabel = "Try again",
            dismissLabel = "Close",
            onConfirm = { AudioExporter.retry(context) },
            onDismiss = { AudioExporter.dismiss() },
        )
        else -> Unit
    }
}

/** A small centred panel with a spinner, in the same shape as Shiny's alerts. */
@Composable
private fun ProgressPanel(title: String, message: String, onCancel: (() -> Unit)?, progress: Float? = null) {
    val colors = Liquid.colors
    val surface = if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9)
    Dialog(
        onDismissRequest = { onCancel?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onCancel != null,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .width(280.dp)
                .background(surface, RoundedCornerShape(30.dp))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.accent,
                    trackColor = colors.fill,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.accent,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = LiquidTypography.headline, color = colors.label)
            Text(
                text = message,
                style = LiquidTypography.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (onCancel != null) {
                Spacer(Modifier.height(18.dp))
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(colors.fill, CircleShape)
                        .clickable(interactionSource = interaction, indication = null, onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", style = LiquidTypography.headline, color = colors.label)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
