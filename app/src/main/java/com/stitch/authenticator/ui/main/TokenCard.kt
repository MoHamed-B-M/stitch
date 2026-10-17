package com.stitch.authenticator.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stitch.authenticator.theme.CodeStyleCompact
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncyContentSize
import com.stitch.authenticator.theme.springClickable
import com.stitch.authenticator.theme.stitchColors
import kotlinx.coroutines.delay

/** What a hidden code looks like: the same shape as a code, so the row does not jump when revealed. */
private const val MaskedCode = "••• •••"

/** Below this many seconds the countdown turns from "live" to "act now". */
private const val ExpiringThresholdSeconds = 5

private val RingSize = 44.dp
private val RingStroke = 4.dp
private const val RingStartAngle = -90f
private val CardPadding = 16.dp

/**
 * One account: who it is, its code, and how long that code has left.
 *
 * Stateless apart from the short-lived "Copied" confirmation, so the list can recompose it once a
 * second without any of that state being rebuilt.
 */
@Composable
internal fun TokenCard(
  token: TokenUiModel,
  revealed: Boolean,
  onToggleReveal: () -> Unit,
  onCopy: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var copied by remember { mutableStateOf(false) }
  LaunchedEffect(copied) {
    if (copied) {
      delay(CopiedConfirmationMillis)
      copied = false
    }
  }

  val semanticColors = MaterialTheme.stitchColors
  val codeColor =
    when {
      !token.isUsable -> semanticColors.codeExpired
      token.secondsRemaining <= ExpiringThresholdSeconds -> semanticColors.codeExpiring
      else -> semanticColors.codeValid
    }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(
      modifier = Modifier.padding(CardPadding),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = token.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        if (token.subtitle.isNotEmpty()) {
          Text(
            text = token.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          // Tap the code itself to reveal it: the whole code area is the target, not a tiny icon.
          Box(
            modifier =
              Modifier.springClickable(
                  role = Role.Button,
                  onClickLabel = if (revealed) "Hide code" else "Show code",
                  onClick = onToggleReveal,
                )
                .padding(vertical = 2.dp, horizontal = 2.dp)
          ) {
            AnimatedContent(
              targetState = revealed,
              transitionSpec = {
                (
                  fadeIn(animationSpec = StitchMotion.BouncyQuick) +
                    scaleIn(initialScale = 0.9f, animationSpec = StitchMotion.BouncyQuick)
                ) togetherWith fadeOut(animationSpec = StitchMotion.Press)
              },
              label = "codeReveal",
            ) { isRevealed ->
              Text(
                text =
                  when {
                    !token.isUsable -> "-- -- --"
                    isRevealed -> token.code
                    else -> MaskedCode
                  },
                style = CodeStyleCompact,
                color = codeColor,
              )
            }
          }

          Spacer(Modifier.width(10.dp))

          CopyPill(
            copied = copied,
            enabled = token.isUsable,
            onClick = {
              onCopy()
              copied = true
            },
          )
        }
      }

      Spacer(Modifier.width(12.dp))

      CountdownRing(
        progress = token.progress,
        secondsRemaining = token.secondsRemaining,
        color = codeColor,
        isUsable = token.isUsable,
      )
    }
  }
}

/**
 * Copy affordance. A plain `Text` pill rather than a Material button so the press scale from
 * [StitchMotion] is driven by the same interaction source as the click — no inner ripple to fight.
 */
@Composable
private fun CopyPill(
  copied: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Text(
    text = if (copied) "Copied" else "Copy",
    style = MaterialTheme.typography.labelLarge,
    color =
      if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.outline
      },
    modifier =
      Modifier.bouncyContentSize()
        .clip(MaterialTheme.shapes.extraLarge)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .springClickable(
          enabled = enabled,
          role = Role.Button,
          onClickLabel = "Copy code",
          onClick = onClick,
        )
        .padding(horizontal = 12.dp, vertical = 6.dp),
  )
}

/**
 * Countdown to the next rollover, with the remaining seconds in the middle.
 *
 * Drawn from the *snapshot* progress rather than from a clock of its own: one animation, one truth.
 * The animation is [StitchMotion.Smooth] rather than bouncy on purpose — the ring reports a
 * security-relevant number, and an overshooting spring would briefly claim there is less time left
 * than there is.
 */
@Composable
private fun CountdownRing(
  progress: Float,
  secondsRemaining: Int,
  color: Color,
  isUsable: Boolean,
  modifier: Modifier = Modifier,
) {
  val animatedProgress by
    animateFloatAsState(
      targetValue = progress.coerceIn(0f, 1f),
      animationSpec = StitchMotion.Smooth,
      label = "countdownProgress",
    )
  val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

  Box(modifier = modifier.size(RingSize), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.size(RingSize)) {
      val stroke = RingStroke.toPx()
      val inset = stroke / 2f
      val arcSize = Size(size.width - stroke, size.height - stroke)

      drawArc(
        color = trackColor,
        startAngle = RingStartAngle,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )
      drawArc(
        color = color,
        startAngle = RingStartAngle,
        sweepAngle = -360f * animatedProgress,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )
    }

    Text(
      text = if (isUsable) secondsRemaining.toString() else "!",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private const val CopiedConfirmationMillis = 1_500L
