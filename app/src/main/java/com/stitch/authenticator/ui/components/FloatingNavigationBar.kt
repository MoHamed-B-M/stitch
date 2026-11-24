package com.stitch.authenticator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stitch.authenticator.theme.LocalReducedMotion
import com.stitch.authenticator.theme.StitchMotion

data class AppDestination(
  val key: Any,
  val label: String,
  val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StitchFloatingNavigationBar(
  destinations: List<AppDestination>,
  selectedKey: Any?,
  onSelect: (Any) -> Unit,
  modifier: Modifier = Modifier,
) {
  val reduced = LocalReducedMotion.current
  HorizontalFloatingToolbar(
    expanded = true,
    modifier = modifier.navigationBarsPadding().padding(bottom = 8.dp),
    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
      toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    expandedShadowElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      destinations.forEachIndexed { index, dest ->
        val selected = selectedKey == dest.key
        val corner by animateDpAsState(
          targetValue = if (selected) 20.dp else 14.dp,
          animationSpec = StitchMotion.shapeMorphSpring(),
          label = "navCorner",
        )
        val itemModifier = if (reduced) Modifier else Modifier.animateContentSize(StitchMotion.standard())
        Surface(
          onClick = { onSelect(dest.key) },
          shape = RoundedCornerShape(corner),
          color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.height(48.dp),
        ) {
          Row(
            modifier = itemModifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(dest.icon, contentDescription = if (selected) null else dest.label, modifier = Modifier.size(24.dp))
            AnimatedVisibility(
              visible = selected,
              enter = if (reduced) fadeIn(StitchMotion.standard()) else fadeIn(StitchMotion.standard()) + expandHorizontally(StitchMotion.standard()),
              exit = if (reduced) fadeOut(StitchMotion.standard()) else fadeOut(StitchMotion.standard()) + shrinkHorizontally(StitchMotion.standard()),
            ) {
              Row {
                Spacer(Modifier.width(8.dp))
                Text(dest.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
        if (index < destinations.lastIndex) Spacer(Modifier.width(4.dp))
      }
    }
  }
}
