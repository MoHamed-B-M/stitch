package com.stitch.authenticator.ui.update

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The page's own glyph: an up-arrow rising out of an open tray — "this build goes out of the
 * box and up to the next one."
 *
 * Hand-drawn because the core icon set has no `SystemUpdate` glyph and `material-icons-extended`
 * stays excluded to keep the APK small; two filled polygons (tray, arrow) are all it needs, so
 * there is nothing here to mis-round. Built once and cached, like every generated icon.
 */
internal val SystemUpdateIcon: ImageVector by lazy {
  materialIcon(name = "SystemUpdate") {
    materialPath(fill = SolidColor(Color.Black)) {
      // The tray: an open-topped container, arms rising to where the arrow passes through.
      moveTo(3.6f, 12.4f)
      lineTo(3.6f, 20.4f)
      lineTo(20.4f, 20.4f)
      lineTo(20.4f, 12.4f)
      lineTo(17.0f, 12.4f)
      lineTo(17.0f, 16.6f)
      lineTo(7.0f, 16.6f)
      lineTo(7.0f, 12.4f)
      close()
      // The shaft, sunk a little into the tray so the two shapes read as one glyph.
      moveTo(11.0f, 3.4f)
      lineTo(13.0f, 3.4f)
      lineTo(13.0f, 14.2f)
      lineTo(11.0f, 14.2f)
      close()
      // The head.
      moveTo(6.9f, 8.0f)
      lineTo(12.0f, 3.0f)
      lineTo(17.1f, 8.0f)
      close()
    }
  }
}
