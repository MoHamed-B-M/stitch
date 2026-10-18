package com.stitch.authenticator.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Stitch motion foundation — the single source of truth for how things move.
 *
 * **Rule: no `tween` for core interactions, springs only.** Physics-based motion is what makes the
 * expressive language feel alive: springs respond to interruptions mid-flight instead of
 * restarting a fixed-duration curve, which is exactly what happens when a user taps twice quickly
 * or a code rolls over while a card is animating.
 *
 * Two layers exist on purpose:
 * 1. **Component motion** — `MaterialTheme.motionScheme` ([MotionScheme.expressive] from the theme)
 *    drives every Material component automatically; nothing to author and nothing to keep in sync.
 * 2. **Authored motion** — the tokens below: our own animations speak the house language rather
 *    than the scheme's, chosen once here so every entrance, press and slide in the app matches.
 *
 * Bounciness is deliberately kept in the 0.5–0.7 damping band: below 0.5 reads as unstable for a
 * security app, above 0.8 loses the expressive character.
 */
object StitchMotion {

  /** Entrances/exits, list item add/remove, screen transitions. The "default" bounce. */
  val Bouncy: FiniteAnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

  /** Same bounce, faster: press feedback and small state changes. */
  val BouncyQuick: FiniteAnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

  /** Press feedback on buttons, cards and the FAB. */
  val Press: FiniteAnimationSpec<Float> = BouncyQuick

  /** Swipe released below the dismiss threshold — no overshoot, just a confident return. */
  val Settle: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)

  /** Progress/countdown style motion where a bounce would misreport state. */
  val Smooth: FiniteAnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * A spring typed to whatever the caller needs (`IntOffset`, `IntSize`, `Dp`, …).
 *
 * Animation specs are generic over their value type, so one helper covers slides, resizes and
 * fades instead of a constant per type.
 *
 * [visibilityThreshold] matters for integer-position animations such as list placement: without it a
 * placement spring keeps chasing sub-pixel differences that no one can see, so IntOffset callers
 * pass `IntOffset.VisibilityThreshold` the same way the framework's own defaults do.
 */
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

/**
 * `animateContentSize` with the house spring.
 *
 * Layout changes (revealing a code, swapping labels, expanding the FAB) should never jump; this is
 * the one-liner version of the rule so call sites cannot accidentally reach for `tween`.
 */
fun Modifier.bouncyContentSize(): Modifier =
  animateContentSize(
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
  )

/**
 * Scale a surface down while it is being pressed, released back with a spring.
 *
 * Kept separate from [springClickable] so a component whose click handling lives elsewhere (e.g. a
 * Material `Button` that owns its [InteractionSource]) can still get the press feel.
 */
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

/**
 * `clickable` plus the expressive press feel: the whole surface scales to [pressedScale] while held
 * and springs back on release.
 *
 * Returns the pressed scale to the caller's own [onClick].
 */
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
  // Hoist the source so the click and the scale animation observe the same press signal. The
  // fallback is created unconditionally so the composition's slot table stays stable.
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
