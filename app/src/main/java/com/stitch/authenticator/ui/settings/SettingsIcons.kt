package com.stitch.authenticator.ui.settings

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings glyphs, hand-drawn: the core icon set has no palette, shield, text-lines or
 * code-bracket shapes, and `material-icons-extended` stays excluded to keep the APK small —
 * the same trade [com.stitch.authenticator.ui.update.SystemUpdateIcon] already made.
 *
 * Every path here is built from `moveTo`/`lineTo`/`curveTo`/`close` only (the arguments
 * `materialPath` actually has; it fills black by default and the `Icon` tint colors it). The
 * two "hollow" glyphs — [PaletteIcon] and [ShieldIcon] — get their holes the winding way: a
 * reversed inner subpath cancels the outer one under non-zero fill, so no even-odd flag is
 * needed. Built once and cached, like every generated icon.
 */

/** Brightness itself: a disc with four detached rays — the theme mode's own row. */
internal val SunIcon: ImageVector by lazy {
  materialIcon(name = "Sun") {
    materialPath {
      // The disc, drawn clockwise: four cubic segments of a r=4.4 circle around (12, 12).
      moveTo(12.0f, 7.6f)
      curveTo(14.43f, 7.6f, 16.4f, 9.57f, 16.4f, 12.0f)
      curveTo(16.4f, 14.43f, 14.43f, 16.4f, 12.0f, 16.4f)
      curveTo(9.57f, 16.4f, 7.6f, 14.43f, 7.6f, 12.0f)
      curveTo(7.6f, 9.57f, 9.57f, 7.6f, 12.0f, 7.6f)
      close()
      // Four rays, floating just outside the disc so the pair reads as light, not as a gear.
      moveTo(11.1f, 5.8f)
      lineTo(11.1f, 3.4f)
      lineTo(12.9f, 3.4f)
      lineTo(12.9f, 5.8f)
      close()
      moveTo(11.1f, 18.2f)
      lineTo(12.9f, 18.2f)
      lineTo(12.9f, 20.6f)
      lineTo(11.1f, 20.6f)
      close()
      moveTo(5.8f, 11.1f)
      lineTo(3.4f, 11.1f)
      lineTo(3.4f, 12.9f)
      lineTo(5.8f, 12.9f)
      close()
      moveTo(18.2f, 11.1f)
      lineTo(20.6f, 11.1f)
      lineTo(20.6f, 12.9f)
      lineTo(18.2f, 12.9f)
      close()
    }
  }
}

/**
 * The wallpaper palette: a ring (outer circle clockwise, inner circle counter-clockwise, so the
 * band between them punches itself out) with three paint dots floating in the open centre —
 * inside the hole their winding adds back up, so they fill. Dynamic color's own row.
 */
internal val PaletteIcon: ImageVector by lazy {
  materialIcon(name = "Palette") {
    materialPath {
      // Outer disc, r=8.2.
      moveTo(12.0f, 3.8f)
      curveTo(16.529f, 3.8f, 20.2f, 7.471f, 20.2f, 12.0f)
      curveTo(20.2f, 16.529f, 16.529f, 20.2f, 12.0f, 20.2f)
      curveTo(7.471f, 20.2f, 3.8f, 16.529f, 3.8f, 12.0f)
      curveTo(3.8f, 7.471f, 7.471f, 3.8f, 12.0f, 3.8f)
      close()
      // Inner disc, r=6, traversed the other way around: top → left → bottom → right.
      moveTo(12.0f, 6.0f)
      curveTo(8.686f, 6.0f, 6.0f, 8.686f, 6.0f, 12.0f)
      curveTo(6.0f, 15.314f, 8.686f, 18.0f, 12.0f, 18.0f)
      curveTo(15.314f, 18.0f, 18.0f, 15.314f, 18.0f, 12.0f)
      curveTo(18.0f, 8.686f, 15.314f, 6.0f, 12.0f, 6.0f)
      close()
      // Three dots of paint, clockwise like the outer disc.
      moveTo(12.0f, 7.1f)
      curveTo(13.049f, 7.1f, 13.9f, 7.951f, 13.9f, 9.0f)
      curveTo(13.9f, 10.049f, 13.049f, 10.9f, 12.0f, 10.9f)
      curveTo(10.951f, 10.9f, 10.1f, 10.049f, 10.1f, 9.0f)
      curveTo(10.1f, 7.951f, 10.951f, 7.1f, 12.0f, 7.1f)
      close()
      moveTo(9.0f, 12.3f)
      curveTo(10.049f, 12.3f, 10.9f, 13.151f, 10.9f, 14.2f)
      curveTo(10.9f, 15.249f, 10.049f, 16.1f, 9.0f, 16.1f)
      curveTo(7.951f, 16.1f, 7.1f, 15.249f, 7.1f, 14.2f)
      curveTo(7.1f, 13.151f, 7.951f, 12.3f, 9.0f, 12.3f)
      close()
      moveTo(15.0f, 12.3f)
      curveTo(16.049f, 12.3f, 16.9f, 13.151f, 16.9f, 14.2f)
      curveTo(16.9f, 15.249f, 16.049f, 16.1f, 15.0f, 16.1f)
      curveTo(13.951f, 16.1f, 13.1f, 15.249f, 13.1f, 14.2f)
      curveTo(13.1f, 13.151f, 13.951f, 12.3f, 15.0f, 12.3f)
      close()
    }
  }
}

/**
 * The privacy shield: an outer outline with its inner outline wound backwards, so the middle
 * cancels to transparency and the glyph reads as a guard, not a sticker. The biometric lock's row.
 */
internal val ShieldIcon: ImageVector by lazy {
  materialIcon(name = "Shield") {
    materialPath {
      // Outer wall: top edge, right flank, point, left flank — clockwise.
      moveTo(4.0f, 2.5f)
      lineTo(20.0f, 2.5f)
      lineTo(20.0f, 11.0f)
      lineTo(12.0f, 21.5f)
      lineTo(4.0f, 11.0f)
      close()
      // The same outline, inset ~2.6dp and walked in reverse: bottom → right → top → left.
      moveTo(6.8f, 10.6f)
      lineTo(12.0f, 18.6f)
      lineTo(17.2f, 10.6f)
      lineTo(17.2f, 5.2f)
      lineTo(6.8f, 5.2f)
      close()
    }
  }
}

/** Three lines of text, the last one short — a licenses page, without the paper. */
internal val LinesIcon: ImageVector by lazy {
  materialIcon(name = "TextLines") {
    materialPath {
      moveTo(4.0f, 5.5f)
      lineTo(20.0f, 5.5f)
      lineTo(20.0f, 8.5f)
      lineTo(4.0f, 8.5f)
      close()
      moveTo(4.0f, 10.75f)
      lineTo(20.0f, 10.75f)
      lineTo(20.0f, 13.75f)
      lineTo(4.0f, 13.75f)
      close()
      moveTo(4.0f, 16.0f)
      lineTo(16.0f, 16.0f)
      lineTo(16.0f, 19.0f)
      lineTo(4.0f, 19.0f)
      close()
    }
  }
}

/** Angle brackets around a slash — the GitHub row's "this is where the code lives." */
internal val CodeIcon: ImageVector by lazy {
  materialIcon(name = "CodeBrackets") {
    materialPath {
      // `<`: outer edge left, inner edge back, ends cut flat.
      moveTo(9.6f, 4.5f)
      lineTo(4.2f, 12.0f)
      lineTo(9.6f, 19.5f)
      lineTo(12.2f, 19.5f)
      lineTo(6.8f, 12.0f)
      lineTo(12.2f, 4.5f)
      close()
      // `/`: a leaning bar between the brackets.
      moveTo(14.9f, 4.5f)
      lineTo(17.1f, 4.5f)
      lineTo(9.6f, 19.5f)
      lineTo(7.4f, 19.5f)
      close()
      // `>`: the mirror of `<` across x = 12.
      moveTo(14.4f, 4.5f)
      lineTo(19.8f, 12.0f)
      lineTo(14.4f, 19.5f)
      lineTo(11.8f, 19.5f)
      lineTo(17.2f, 12.0f)
      lineTo(11.8f, 4.5f)
      close()
    }
  }
}
