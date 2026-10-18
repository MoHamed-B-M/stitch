package com.stitch.authenticator.passkeys

import java.math.BigInteger

/**
 * COSE (RFC 8152) encoding for the one key type this app generates: an EC2 P-256 public key.
 *
 * Written by hand instead of pulling in a CBOR library — the structure is a fixed five-entry
 * map, so a dependency would cost more than the ~75 bytes it encodes:
 *
 *     {1: 2, 3: -7, -1: 1, -2: x, -3: y}   // kty EC2, alg ES256, crv P-256, x, y
 *
 * Pure on purpose: no Android types, fully unit-testable, and the relying party sees exactly the
 * bytes WebAuthn's `credentialPublicKey` expects.
 */
internal object PasskeyCose {

  /** CBOR map(5) header. */
  private const val MAP_HEADER = 0xA5
  private const val KEY_KTY = 0x01
  private const val KEY_ALG = 0x03
  private const val KEY_CRV = 0x20 // -1, CBOR negative int
  private const val KEY_X = 0x21 // -2
  private const val KEY_Y = 0x22 // -3
  private const val VALUE_KTY_EC2 = 0x02
  private const val VALUE_ALG_ES256 = 0x26 // -7
  private const val VALUE_CRV_P256 = 0x01
  private const val BYTE_STRING_32 = 0x58
  private const val LENGTH_32 = 0x20

  /** Total encoded size: header + five (key, value) pairs, two of which carry 32-byte strings. */
  private const val ENCODED_SIZE = 1 + 2 + 2 + 2 + 34 + 34

  /** Encodes affine P-256 coordinates as a COSE_Key byte string. */
  fun encodeP256(affineX: BigInteger, affineY: BigInteger): ByteArray {
    val x = coordinate32(affineX)
    val y = coordinate32(affineY)
    val out = ByteArray(ENCODED_SIZE)
    var i = 0
    out[i++] = MAP_HEADER.toByte()
    out[i++] = KEY_KTY.toByte()
    out[i++] = VALUE_KTY_EC2.toByte()
    out[i++] = KEY_ALG.toByte()
    out[i++] = VALUE_ALG_ES256.toByte()
    out[i++] = KEY_CRV.toByte()
    out[i++] = VALUE_CRV_P256.toByte()
    out[i++] = BYTE_STRING_32.toByte()
    out[i++] = LENGTH_32.toByte()
    x.copyInto(out, destinationOffset = i)
    i += x.size
    out[i++] = BYTE_STRING_32.toByte()
    out[i++] = LENGTH_32.toByte()
    y.copyInto(out, destinationOffset = i)
    return out
  }

  /**
   * Big-endian 32-byte encoding of one coordinate. CBOR integers arrive in their shortest form,
   * which strips leading zeros and lets [BigInteger] prepend a sign byte for high values — the
   * curve's fixed width wins: always exactly 32 bytes.
   */
  private fun coordinate32(value: BigInteger): ByteArray {
    val bytes = value.toByteArray()
    val out = ByteArray(LENGTH_32)
    val start = if (bytes.size > LENGTH_32) bytes.size - LENGTH_32 else 0
    bytes.copyInto(out, destinationOffset = LENGTH_32 - (bytes.size - start), startIndex = start)
    return out
  }
}
