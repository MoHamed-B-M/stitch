package com.stitch.authenticator.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stitch.authenticator.data.settings.ThemeMode
import com.stitch.authenticator.theme.LocalReducedMotion
import com.stitch.authenticator.theme.StitchMotion
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveActionButton(
  onClick: () -> Unit,
  text: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  loading: Boolean = false,
  enabled: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val reduced = LocalReducedMotion.current
  var holdProgress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(pressed, enabled, loading, reduced) {
    if (reduced || !pressed || !enabled || loading) {
      holdProgress = 0f
      return@LaunchedEffect
    }
    val start = System.currentTimeMillis()
    while (pressed && holdProgress < 1f) {
      holdProgress = ((System.currentTimeMillis() - start).toFloat() / StitchMotion.HoldDurationMillis.toFloat()).coerceIn(0f, 1f)
      delay(16)
    }
  }

  val corner by animateDpAsState(
    targetValue = when {
      holdProgress > 0f -> 14.dp
      pressed -> 18.dp
      else -> 28.dp
    },
    animationSpec = StitchMotion.shapeMorphSpring(),
    label = "actionCorner",
  )
  val scale by animateFloatAsState(
    targetValue = when {
      holdProgress > 0f -> 0.94f
      pressed -> StitchMotion.PressScale
      else -> 1f
    },
    animationSpec = StitchMotion.pressSpring(),
    label = "actionScale",
  )

  Surface(
    onClick = onClick,
    enabled = enabled && !loading,
    shape = RoundedCornerShape(corner),
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    interactionSource = interactionSource,
    modifier = modifier.graphicsLayer {
      if (!reduced) {
        scaleX = scale
        scaleY = scale
      }
    },
  ) {
    Row(
      modifier = Modifier.padding(contentPadding).heightIn(min = 24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      AnimatedContent(
        targetState = loading,
        transitionSpec = {
          fadeIn(StitchMotion.standard()) togetherWith fadeOut(StitchMotion.standard())
        },
        label = "actionContent",
      ) { isLoading ->
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
          )
        } else if (icon != null) {
          Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
      }
      Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
fun ExpressiveIconButton(
  onClick: () -> Unit,
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val reduced = LocalReducedMotion.current
  var holdProgress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(pressed, enabled, reduced) {
    if (reduced || !pressed || !enabled) {
      holdProgress = 0f
      return@LaunchedEffect
    }
    val start = System.currentTimeMillis()
    while (pressed && holdProgress < 1f) {
      holdProgress = ((System.currentTimeMillis() - start).toFloat() / StitchMotion.HoldDurationMillis.toFloat()).coerceIn(0f, 1f)
      delay(16)
    }
  }

  val corner by animateDpAsState(
    targetValue = when {
      holdProgress > 0f -> 10.dp
      pressed -> 12.dp
      else -> 20.dp
    },
    animationSpec = StitchMotion.shapeMorphSpring(),
    label = "iconCorner",
  )
  val scale by animateFloatAsState(
    targetValue = when {
      holdProgress > 0f -> 0.93f
      pressed -> StitchMotion.PressScale
      else -> 1f
    },
    animationSpec = StitchMotion.pressSpring(),
    label = "iconScale",
  )
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(corner),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    interactionSource = interactionSource,
    modifier = modifier.size(40.dp).graphicsLayer {
      if (!reduced) {
        scaleX = scale
        scaleY = scale
      }
    },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeModeButtonGroup(
  selected: ThemeMode,
  onSelect: (ThemeMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  val options = listOf(
    Triple(ThemeMode.SYSTEM, "System", Icons.Filled.SettingsBrightness),
    Triple(ThemeMode.LIGHT, "Light", Icons.Filled.LightMode),
    Triple(ThemeMode.DARK, "Dark", Icons.Filled.DarkMode),
  )
  Row(
    modifier = modifier.fillMaxWidth().selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
  ) {
    options.forEachIndexed { index, (mode, label, icon) ->
      val isSelected = selected == mode
      ToggleButton(
        checked = isSelected,
        onCheckedChange = { onSelect(mode) },
        shapes = when (index) {
          0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
          options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
          else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        },
        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
        colors = ToggleButtonDefaults.toggleButtonColors(
          checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
          checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveSegmentedShapes(index: Int, count: Int): androidx.compose.material3.ListItemShapes {
  val shape = when {
    count <= 1 -> RoundedCornerShape(28.dp)
    index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    else -> RoundedCornerShape(4.dp)
  }
  return androidx.compose.material3.ListItemDefaults.shapes(shape = shape, selectedShape = shape)
}

@Composable
fun MaterialListSurface(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.clip(RoundedCornerShape(24.dp)),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
  ) {
    Column(content = content)
  }
}
