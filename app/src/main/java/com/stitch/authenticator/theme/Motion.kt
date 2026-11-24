package com.stitch.authenticator.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Stitch motion foundation - the single source of truth for how things move.
 *
 * **Rule: no `tween` for core interactions, springs only.** Physics-based motion is what makes the
 * expressive language feel alive: springs respond to interruptions mid-flight instead of
 * restarting a fixed-duration curve, which is exactly what happens when a user taps twice quickly
 * or a code rolls over while a card is animating.
 *
 * [LocalReducedMotion] lets authored motion follow the system/accessibility reduced-motion setting
 * when the theme resolves to a calmer motion scheme.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

object StitchMotion {
  const val PressScale = 0.97f
  const val RestScale = 1f
  const val HoldDurationMillis = 1_500L

  /** Controlled motion for entrances/exits and content resizing: subtle, no bounce. */
  val Bouncy: FiniteAnimationSpec<Float> =
    spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

  /** Quicker feedback for small state changes. */
  val BouncyQuick: FiniteAnimationSpec<Float> =
    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)

  /** Press feedback on buttons, cards and the FAB. */
  val Press: FiniteAnimationSpec<Float> = BouncyQuick

  /** Released below threshold - confident return, no overshoot. */
  val Settle: FiniteAnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

  /** Progress/countdown style motion where a bounce would misreport state. */
  val Smooth: FiniteAnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

  @Composable
  fun <T> emphasized(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow,
    )

  @Composable
  fun <T> standard(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMedium,
    )

  @Composable
  fun <T> gentle(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessLow,
    )

  @Composable
  fun <T> pressSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
      dampingRatio = 0.72f,
      stiffness = Spring.StiffnessMediumLow,
    )

  @Composable
  fun <T> shapeMorphSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow,
    )
}

/** [StitchMotion.Bouncy] typed for non-`Float` values. */
fun <T> bouncySpring(
  dampingRatio: Float = Spring.DampingRatioMediumBouncy,
  stiffness: Float = Spring.StiffnessLow,
  visibilityThreshold: T? = null,
): FiniteAnimationSpec<T> =
  spring(
    dampingRatio = dampingRatio,
    stiffness = stiffness,
    visibilityThreshold = visibilityThreshold,
  )

/** [StitchMotion.Settle] typed for non-`Float` values. */
fun <T> settleSpring(): FiniteAnimationSpec<T> =
  spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)

/** `animateContentSize` with the house spring. */
fun Modifier.bouncyContentSize(): Modifier =
  animateContentSize(
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
  )

/** Scale a surface down while it is being pressed, released back with a spring. */
@Composable
fun Modifier.pressScale(
  interactionSource: InteractionSource,
  pressedScale: Float = 0.96f,
  animationSpec: FiniteAnimationSpec<Float> = StitchMotion.Press,
): Modifier {
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) pressedScale else 1f,
      animationSpec = animationSpec,
      label = "pressScale",
    )

  return this.graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

/** `clickable` plus the expressive press feel. */
@Composable
fun Modifier.springClickable(
  enabled: Boolean = true,
  pressedScale: Float = 0.96f,
  onClickLabel: String? = null,
  role: Role? = null,
  indication: Indication? = LocalIndication.current,
  interactionSource: MutableInteractionSource? = null,
  onClick: () -> Unit,
): Modifier {
  val ownSource = remember { MutableInteractionSource() }
  val source = interactionSource ?: ownSource

  return this
    .clickable(
      interactionSource = source,
      indication = indication,
      enabled = enabled,
      onClickLabel = onClickLabel,
      role = role,
      onClick = onClick,
    )
    .pressScale(interactionSource = source, pressedScale = pressedScale)
}
