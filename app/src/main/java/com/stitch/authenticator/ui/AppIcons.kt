package com.stitch.authenticator.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The bottom bar's Authenticator glyph, hand-drawn: the core icon set ships a lock for the
 * vault tab but no key for this one, and `material-icons-extended` stays excluded to keep the
 * APK small — the same trade the settings glyphs and the update page's system icon already
 * made.
 *
 * A ringed bow with a hole and a toothed shaft. The hole uses the winding trick from the
 * settings glyphs: a reversed inner subpath cancels the outer disc under non-zero fill, so no
 * even-odd flag is needed — and the shaft starts past the hole (x = 10.6) so it overlaps the
 * bow's rim and the pair reads as one piece. Built once and cached, like every generated icon.
 */
internal val KeyGlyph: ImageVector by lazy {
  materialIcon(name = "Key") {
    materialPath {
      // The bow: a r=4.4 disc around (7.5, 12), drawn clockwise.
      moveTo(7.5f, 7.6f)
      curveTo(9.93f, 7.6f, 11.9f, 9.57f, 11.9f, 12.0f)
      curveTo(11.9f, 14.43f, 9.93f, 16.4f, 7.5f, 16.4f)
      curveTo(5.07f, 16.4f, 3.1f, 14.43f, 3.1f, 12.0f)
      curveTo(3.1f, 9.57f, 5.07f, 7.6f, 7.5f, 7.6f)
      close()
      // Its hole: the same circle at r=1.9, drawn the other way so the winding cancels it.
      moveTo(7.5f, 10.1f)
      curveTo(6.45f, 10.1f, 5.6f, 10.95f, 5.6f, 12.0f)
      curveTo(5.6f, 13.05f, 6.45f, 13.9f, 7.5f, 13.9f)
      curveTo(8.55f, 13.9f, 9.4f, 13.05f, 9.4f, 12.0f)
      curveTo(9.4f, 10.95f, 8.55f, 10.1f, 7.5f, 10.1f)
      close()
      // The shaft, wide enough (2.2) to read against the bow's ring.
      moveTo(10.6f, 10.9f)
      lineTo(21.4f, 10.9f)
      lineTo(21.4f, 13.1f)
      lineTo(10.6f, 13.1f)
      close()
      // Two teeth hanging off the far end, same winding as the shaft so they merge into it.
      moveTo(16.6f, 13.1f)
      lineTo(18.6f, 13.1f)
      lineTo(18.6f, 16.9f)
      lineTo(16.6f, 16.9f)
      close()
      moveTo(19.6f, 13.1f)
      lineTo(21.4f, 13.1f)
      lineTo(21.4f, 16.4f)
      lineTo(19.6f, 16.4f)
      close()
    }
  }
}
