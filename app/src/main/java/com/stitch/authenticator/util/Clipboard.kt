package com.stitch.authenticator.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * The clipboard, for the only two things this app does with it: putting a code where the user can
 * paste it somewhere, and taking a pasted link.
 */
object Clipboard {

  /** `ClipDescription.EXTRA_IS_SENSITIVE`, spelled out so the constant is not a version-gated reference. */
  private const val SensitiveClipExtra = "android.content.extra.IS_SENSITIVE"

  /**
   * Puts [text] on the clipboard under the readable [label].
   *
   * On Android 13+ the clip is marked sensitive so the system's own copy confirmation does not render
   * the secret in a preview — a code on screen for a moment is a shoulder-surfing risk, which is why
   * codes stay hidden on screen until they are tapped.
   */
  fun copySensitive(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(label, text)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      clip.description.setExtras(PersistableBundle().apply { putBoolean(SensitiveClipExtra, true) })
    }

    manager.setPrimaryClip(clip)
  }

  /**
   * The most recent text on the clipboard, or `null` when the clip is empty or holds something else.
   *
   * Called only from a button the user just pressed: the system raises its own confirmation banner
   * whenever an app reads the clip, so the read is always something the user asked for.
   */
  fun readText(context: Context): String? {
    val manager =
      context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null

    return manager.primaryClip
      ?.takeIf { it.itemCount > 0 }
      ?.getItemAt(0)
      ?.coerceToText(context)
      ?.toString()
      ?.takeIf { it.isNotBlank() }
  }
}
