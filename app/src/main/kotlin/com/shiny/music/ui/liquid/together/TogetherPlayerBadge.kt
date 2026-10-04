package com.shiny.music.ui.liquid.together

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.R
import com.shiny.music.together.TogetherSession
import com.shiny.music.ui.liquid.GlassCapsule
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.LiquidTypography

/**
 * Now Playing's sign that you're listening with people: the faces in it, as a glass capsule
 * over the player's own background, with a reaction key beside it that opens the reaction
 * strip. Tapping the capsule opens the session. Until someone else is in the session there
 * is nobody to show or react with, so there is no badge — the player stays as it was.
 */
@Composable
fun TogetherPlayerBadge(
    session: TogetherSession,
    state: TogetherSession.State,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val room = state.room ?: return
    var reacting by remember { mutableStateOf(false) }
    val others = room.members.filter { it.connected && it.id != state.meId }
    if (others.isEmpty()) return
    val label = if (others.size == 1) {
        stringResource(R.string.together_badge_one, others[0].name)
    } else {
        stringResource(R.string.together_badge_many, others[0].name, others.size - 1)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassCapsule(
                kind = GlassKind.Clear,
                onClick = onOpen,
                contentPadding = PaddingValues(start = 6.dp, end = 14.dp),
                modifier = Modifier.height(36.dp).widthIn(max = 260.dp),
            ) {
                AvatarStack(members = room.members.filter { it.connected }, ring = Color.Black.copy(alpha = 0.35f), size = 24.dp, max = 4)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            GlassCapsule(
                kind = GlassKind.Clear,
                onClick = { reacting = !reacting },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(36.dp),
            ) {
                Text(if (reacting) "✕" else "🔥", fontSize = if (reacting) 15.sp else 17.sp, color = Color.White)
            }
        }
        AnimatedVisibility(
            visible = reacting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            GlassCapsule(
                kind = GlassKind.Clear,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 360.dp),
            ) {
                ReactionBar(
                    onReact = session::react,
                    keyColor = Color.White.copy(alpha = 0.10f),
                    keySize = 36.dp,
                )
            }
        }
    }
}
