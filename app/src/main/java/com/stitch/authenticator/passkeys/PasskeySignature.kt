package com.stitch.authenticator.passkeys

/**
 * WebAuthn wants ECDSA signatures as raw `r || s` (32 + 32 bytes for P-256); the Android
 * Keystore — like every other JCA provider — produces ASN.1 DER. This converts, strictly.
 *
 * Pure on purpose: the parse is small enough to unit-test against hand-built vectors, which is
 * exactly where security-relevant bytes belong.
 */
internal object PasskeySignature {

  private const val SEQUENCE_TAG = 0x30
  private const val INTEGER_TAG = 0x02
  private const val COMPONENT_LENGTH = 32

  /**
   * Converts a DER-encoded P-256 ECDSA signature to WebAuthn's raw `r || s` form.
   *
   * @throws IllegalArgumentException when [der] is not a well-formed P-256 signature
   */
  fun toRaw(der: ByteArray): ByteArray {
    var index = 0
    if (der.getOrNull(index++)?.toInt() != SEQUENCE_TAG) {
      throw IllegalArgumentException("not a DER sequence")
    }
    // P-256 signatures are always under 128 bytes, so DER's short length form is the only legal
    // one; a long-form length fails the size check below anyway.
    val sequenceLength =
      (der.getOrNull(index++) ?: throw IllegalArgumentException("truncated DER sequence"))
        .toInt() and 0xFF
    if (sequenceLength != der.size - 2) throw IllegalArgumentException("DER length mismatch")
    val r = readInteger(der, index)
    index = r.end
    val s = readInteger(der, index)
    index = s.end
    if (index != der.size) throw IllegalArgumentException("trailing bytes after DER signature")
    return r.bytes + s.bytes
  }

  private class Component(val bytes: ByteArray, val end: Int)

  private fun readInteger(der: ByteArray, start: Int): Component {
    var index = start
    if (der.getOrNull(index++)?.toInt() != INTEGER_TAG) {
      throw IllegalArgumentException("expected DER integer")
    }
    val length =
      (der.getOrNull(index++) ?: throw IllegalArgumentException("truncated DER integer"))
        .toInt() and 0xFF
    if (length == 0) throw IllegalArgumentException("empty DER integer")
    if (index + length > der.size) throw IllegalArgumentException("DER integer exceeds input")
    val magnitude = der.copyOfRange(index, index + length)
    index += length
    // DER integers are minimally encoded: at most one leading zero guards the sign bit, and a
    // value larger than the curve allows is an error rather than something to truncate.
    val unpadded =
      if (magnitude.size > 1 && magnitude[0] == 0.toByte()) {
        magnitude.copyOfRange(1, magnitude.size)
      } else {
        magnitude
      }
    if (unpadded.size > COMPONENT_LENGTH) throw IllegalArgumentException("integer exceeds P-256")
    val component = ByteArray(COMPONENT_LENGTH)
    unpadded.copyInto(component, destinationOffset = COMPONENT_LENGTH - unpadded.size)
    return Component(component, index)
  }
}
