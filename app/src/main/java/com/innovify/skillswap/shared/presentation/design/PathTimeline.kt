package com.innovify.skillswap.shared.presentation.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.TextSecondary

/**
 * Timeline row whose connector stretches to the height of [content], so a tall row (the "Siguiente paso"
 * card) stays joined to the next marker as in the Figma "Tu ruta". The connector below a completed step is
 * blue; the rest are grey. Rows are at least 56dp tall to be easy to tap.
 */
@Composable
fun StretchTimelineRow(
    state: TimelineState,
    number: Int?,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) {
        modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else modifier
    Row(clickable.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 56.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp).fillMaxHeight()
        ) {
            StepCircle(state, number)
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (state == TimelineState.Done) SkillBlueDark else NeutralBorder)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f).padding(top = 6.dp, bottom = if (isLast) 0.dp else 20.dp),
            content = content
        )
    }
}

@Composable
private fun StepCircle(state: TimelineState, number: Int?) {
    val (container, tint) = when (state) {
        TimelineState.Done -> Success to Color.White
        TimelineState.Current -> SkillBlueDark to Color.White
        TimelineState.Locked -> Color(0xFFE5E7EB) to TextSecondary
        TimelineState.Pending -> Color.White to TextSecondary
    }
    val base = Modifier.size(36.dp).background(container, CircleShape)
    val circle = if (state == TimelineState.Pending) base.border(2.dp, NeutralBorder, CircleShape) else base
    val ring = if (state == TimelineState.Current) {
        Modifier.size(44.dp).border(4.dp, SkillBlueContainer, CircleShape)
    } else Modifier.size(44.dp)
    Box(ring, contentAlignment = Alignment.Center) {
        Box(circle, contentAlignment = Alignment.Center) {
            when (state) {
                TimelineState.Done -> Icon(Icons.Filled.Check, contentDescription = "Completado", tint = tint, modifier = Modifier.size(20.dp))
                TimelineState.Locked -> Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = tint, modifier = Modifier.size(18.dp))
                else -> Text(number?.toString() ?: "", color = tint, fontWeight = FontWeight.Bold)
            }
        }
    }
}
