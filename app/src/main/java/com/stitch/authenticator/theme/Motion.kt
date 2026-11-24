package com.stitch.authenticator.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalReducedMotion = staticCompositionLocalOf { false }

object StitchMotion {
    const val PressScale = 0.98f
    const val RestScale = 1f

    @Composable
    fun <T> emphasized(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
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
}
