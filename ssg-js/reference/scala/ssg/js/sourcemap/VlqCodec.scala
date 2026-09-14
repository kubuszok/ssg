/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Base64 VLQ (Variable Length Quantity) codec for source map mappings.
 *
 * Source maps encode position deltas as VLQ sequences in base64. Each
 * integer is encoded as: sign bit in LSB of first 6-bit group, continuation
 * bit in MSB (bit 5), least-significant digits first.
 *
 * Reference: Source Map Revision 3 Proposal (now ECMA-426).
 *
 * Original source: @jridgewell/sourcemap-codec (used by terser via @jridgewell/source-map)
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-js-reference: @jridgewell/sourcemap-codec (used by terser via @jridgewell/source-map)
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package sourcemap

import scala.collection.mutable.ArrayBuffer

/** Base64 VLQ encoder/decoder for source map mappings. */
object VlqCodec {

  // Base64 alphabet: A-Z a-z 0-9 + /
  private val Base64Chars: Array[Char] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray

  // Reverse lookup: char -> index (lazily built)
  private val Base64Index: Array[Int] = {
    val arr = Array.fill(128)(-1)
    var i   = 0
    while (i < Base64Chars.length) {
      arr(Base64Chars(i).toInt) = i
      i += 1
    }
    arr
  }

  private val VlqBaseShift    = 5
  private val VlqBase         = 1 << VlqBaseShift // 32
  private val VlqBaseMask     = VlqBase - 1 // 0x1F
  private val VlqContinuation = VlqBase // 0x20

  /** Encode a signed integer to Base64 VLQ. */
  def encode(value: Int): String = {
    val sb = new StringBuilder
    // Convert to unsigned with sign bit in LSB
    var vlq = if (value < 0) ((-value) << 1) + 1 else value << 1
    while (true) {
      var digit = vlq & VlqBaseMask
      vlq >>>= VlqBaseShift
      if (vlq > 0) digit |= VlqContinuation
      sb.append(Base64Chars(digit))
      if (vlq == 0) return sb.toString() // @nowarn
    }
    sb.toString() // unreachable
  }

  /** Decode one VLQ integer from a string starting at `offset`.
    *
    * @return
    *   (decoded value, new offset after consumed characters)
    */
  def decode(str: String, offset: Int): (Int, Int) = {
    var result       = 0
    var shift        = 0
    var continuation = true
    var i            = offset
    while (continuation && i < str.length) {
      val charCode = str.charAt(i).toInt
      val digit    = if (charCode < 128) Base64Index(charCode) else -1
      if (digit < 0) throw new IllegalArgumentException(s"Invalid base64 character: ${str.charAt(i)}")
      continuation = (digit & VlqContinuation) != 0
      result += (digit & VlqBaseMask) << shift
      shift += VlqBaseShift
      i += 1
    }
    // Sign is in LSB
    val isNegative = (result & 1) != 0
    result >>= 1
    if (isNegative) result = -result
    (result, i)
  }

  /** Encode a segment of delta values (1, 4, or 5 integers). */
  def encodeSegment(values: Array[Int]): String = {
    val sb = new StringBuilder
    var i  = 0
    while (i < values.length) {
      sb.append(encode(values(i)))
      i += 1
    }
    sb.toString()
  }

  /** Decode a full mappings string into an array of line arrays.
    *
    * Each line is separated by `;`. Each segment within a line is separated by `,`. Each segment is a sequence of VLQ-encoded integers (1, 4, or 5 values).
    *
    * @return
    *   Array of lines, where each line is an Array of segments, and each segment is an Array[Int] of decoded values.
    */
  def decodeMappings(mappings: String): Array[Array[Array[Int]]] = {
    val lines        = ArrayBuffer.empty[Array[Array[Int]]]
    var lineSegments = ArrayBuffer.empty[Array[Int]]
    var i            = 0
    val len          = mappings.length

    while (i < len) {
      val c = mappings.charAt(i)
      if (c == ';') {
        lines.addOne(lineSegments.toArray)
        lineSegments = ArrayBuffer.empty
        i += 1
      } else if (c == ',') {
        i += 1
      } else {
        val segment = ArrayBuffer.empty[Int]
        while (i < len && mappings.charAt(i) != ',' && mappings.charAt(i) != ';') {
          val (value, newI) = decode(mappings, i)
          segment.addOne(value)
          i = newI
        }
        lineSegments.addOne(segment.toArray)
      }
    }
    // Add last line
    lines.addOne(lineSegments.toArray)
    lines.toArray
  }

  /** Encode a decoded mappings structure back to a VLQ string. */
  def encodeMappings(decoded: Array[Array[Array[Int]]]): String = {
    val sb      = new StringBuilder
    var lineIdx = 0
    while (lineIdx < decoded.length) {
      if (lineIdx > 0) sb.append(';')
      val segments = decoded(lineIdx)
      var segIdx   = 0
      while (segIdx < segments.length) {
        if (segIdx > 0) sb.append(',')
        sb.append(encodeSegment(segments(segIdx)))
        segIdx += 1
      }
      lineIdx += 1
    }
    sb.toString()
  }
}
