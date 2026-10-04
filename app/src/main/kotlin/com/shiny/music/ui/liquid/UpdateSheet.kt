package com.shiny.music.ui.liquid

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.shiny.music.R
import com.shiny.music.shinymusic.updater.ChangelogSection
import com.shiny.music.social.ShinyLinks

/**
 * Tells the listener a newer Shiny is out: the version, what the release says about itself and
 * its changelog. "Update" opens the download page; the app does not install anything itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    version: String,
    description: String?,
    changelog: List<ChangelogSection>,
    onDismiss: () -> Unit,
) {
    val colors = Liquid.colors
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (colors.isDark) Color(0xFF1C1C1E) else colors.groupedBackground,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(colors.tertiaryLabel)
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PageMargin)
                    .padding(top = 14.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.update_available_title), style = LiquidTypography.title2, color = colors.label)
                if (version.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text("Shiny $version", style = LiquidTypography.subheadline, color = colors.secondaryLabel)
                }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    LinkedText(
                        markdownToAnnotatedString(description.trim()),
                        LiquidTypography.subheadline.copy(color = colors.label),
                        open,
                    )
                }
                changelog.filter { it.items.isNotEmpty() }.forEachIndexed { index, section ->
                    Spacer(Modifier.height(if (index == 0 && description.isNullOrBlank()) 14.dp else 20.dp))
                    Text(
                        section.title.ifBlank { stringResource(R.string.changelog) },
                        style = LiquidTypography.headline,
                        color = colors.label,
                    )
                    section.items.forEach { item ->
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text("•", style = LiquidTypography.subheadline, color = colors.secondaryLabel)
                            Spacer(Modifier.size(10.dp))
                            LinkedText(
                                markdownToAnnotatedString(item),
                                LiquidTypography.subheadline.copy(color = colors.label),
                                open,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiquidButton(text = "Next time", onClick = onDismiss, modifier = Modifier.weight(1f))
                LiquidButton(
                    text = "Update",
                    onClick = {
                        onDismiss()
                        open(ShinyLinks.DOWNLOAD)
                    },
                    modifier = Modifier.weight(1f),
                    tone = ButtonTone.Filled,
                )
            }
        }
    }
}

/** Text whose links ([MARKDOWN_URL_TAG] annotations) open when tapped. */
@Composable
private fun LinkedText(text: AnnotatedString, style: TextStyle, open: (String) -> Unit) {
    @Suppress("DEPRECATION")
    ClickableText(
        text = text,
        style = style,
        onClick = { offset ->
            text.getStringAnnotations(MARKDOWN_URL_TAG, offset, offset).firstOrNull()?.let { open(it.item) }
        },
    )
}
