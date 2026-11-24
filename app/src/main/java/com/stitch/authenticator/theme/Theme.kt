package com.stitch.authenticator.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object StitchReducedMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = androidx.compose.animation.core.snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = androidx.compose.animation.core.snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = androidx.compose.animation.core.snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = androidx.compose.animation.core.snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = androidx.compose.animation.core.snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = androidx.compose.animation.core.snap()
}

private val StitchShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StitchAuthenticatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    reducedMotion: Boolean = isReducedMotionEnabled(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> StitchDarkColorScheme
        else -> StitchLightColorScheme
    }

    val motionScheme = if (reducedMotion) StitchReducedMotionScheme else MotionScheme.expressive()

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = StitchShapes,
            motionScheme = motionScheme,
            content = content,
        )
    }
}

@Composable
private fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) {
        val resolver = context.contentResolver
        val scale = runCatching {
            android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        scale == 0f
    }
}

