package com.stitch.authenticator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stitch.authenticator.theme.LocalReducedMotion
import com.stitch.authenticator.theme.StitchMotion

@Composable
fun ExpressiveSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val reduced = LocalReducedMotion.current
  val thumbOffset by animateDpAsState(
    targetValue = if (checked) 20.dp else 0.dp,
    animationSpec = if (reduced) StitchMotion.standard() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
    label = "thumbOffset",
  )
  val thumbSize by animateDpAsState(
    targetValue = if (checked) 24.dp else 16.dp,
    animationSpec = if (reduced) StitchMotion.standard() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
    label = "thumbSize",
  )
  val trackColor by animateColorAsState(
    targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
    animationSpec = StitchMotion.standard(),
    label = "trackColor",
  )
  val thumbColor by animateColorAsState(
    targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
    animationSpec = StitchMotion.standard(),
    label = "thumbColor",
  )
  val borderColor by animateColorAsState(
    targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    animationSpec = StitchMotion.standard(),
    label = "borderColor",
  )
  val iconProgress by animateFloatAsState(
    targetValue = if (checked) 1f else 0f,
    animationSpec = StitchMotion.standard(),
    label = "iconProgress",
  )

  val density = LocalDensity.current
  val offsetPx = with(density) { thumbOffset.roundToPx() }
  val interactionSource = remember { MutableInteractionSource() }

  Box(
    modifier = modifier
      .size(width = 52.dp, height = 32.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(trackColor)
      .border(2.dp, borderColor, RoundedCornerShape(16.dp))
      .then(
        if (onCheckedChange != null) {
          Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
          ) { onCheckedChange(!checked) }
        } else Modifier
      ),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      modifier = Modifier
        .offset { IntOffset(x = offsetPx + with(density) { 4.dp.roundToPx() }, y = 0) }
        .size(thumbSize)
        .clip(RoundedCornerShape(100))
        .background(thumbColor),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.size(12.dp)) {
        if (iconProgress > 0.5f) {
          val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.72f)
            lineTo(size.width * 0.8f, size.height * 0.3f)
          }
          drawPath(
            path = path,
            color = trackColor,
            style = Stroke(width = size.width * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        } else {
          val inset = size.width * 0.22f
          drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = size.width * 0.14f,
            cap = StrokeCap.Round,
          )
          drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = size.width * 0.14f,
            cap = StrokeCap.Round,
          )
        }
      }
    }
  }
}
