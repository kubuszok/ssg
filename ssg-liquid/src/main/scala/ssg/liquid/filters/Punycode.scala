/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Pure-Scala implementation of RFC 3492 Punycode encoding.
 * Used by Absolute_Url as a cross-platform replacement for java.net.IDN.toASCII.
 *
 * Migration notes:
 *   Convention: RFC 3492 section 6.3 (encoding algorithm) faithfully implemented
 *   Idiom: Long delta to prevent overflow; boundary/break for inner digit loop
 */
package ssg
package liquid
package filters

import scala.util.boundary
import scala.util.boundary.break

/** RFC 3492 Punycode encoding — pure Scala, no JVM-only dependencies.
  *
  * Provides `encode` (code-point array to Punycode string) and `toAscii` (internationalized hostname to ASCII-Compatible Encoding per RFC 3490).
  */
private[liquid] object Punycode {

  // RFC 3492 section 5 — Bootstring parameters for Punycode
  private val Base:        Int  = 36
  private val TMin:        Int  = 1
  private val TMax:        Int  = 26
  private val Skew:        Int  = 38
  private val Damp:        Int  = 700
  private val InitialBias: Int  = 72
  private val InitialN:    Int  = 0x80
  private val Delimiter:   Char = '-'

  /** Bias adaptation function — RFC 3492 section 6.1. */
  private def adapt(delta0: Long, numPoints: Int, firstTime: Boolean): Int = {
    var delta = if (firstTime) delta0 / Damp else delta0 / 2
    delta += delta / numPoints
    var k = 0
    while (delta > ((Base - TMin) * TMax) / 2) {
      delta /= (Base - TMin)
      k += Base
    }
    k + ((Base - TMin + 1) * delta / (delta + Skew)).toInt
  }

  /** Encode a single digit value to a Punycode character — RFC 3492 section 5. */
  private def encodeDigit(d: Int): Char =
    if (d < 26) ('a' + d).toChar
    else ('0' + d - 26).toChar

  /** Punycode-encode an array of Unicode code points — RFC 3492 section 6.3. */
  def encode(input: Array[Int]): String = {
    var n:     Int  = InitialN
    var delta: Long = 0L
    var bias:  Int  = InitialBias
    val output = new StringBuilder

    // Handle the basic code points: copy them verbatim
    for (cp <- input)
      if (cp < 0x80) {
        output.append(cp.toChar)
      }

    val b = output.length
    var h = b

    if (b > 0) {
      output.append(Delimiter)
    }

    while (h < input.length) {
      // Find the minimum code point >= n among the input
      var m = Int.MaxValue
      for (cp <- input)
        if (cp >= n && cp < m) {
          m = cp
        }

      delta += (m.toLong - n.toLong) * (h.toLong + 1L)
      n = m

      for (cp <- input) {
        if (cp < n) {
          delta += 1L
        }
        if (cp == n) {
          var q = delta
          var k = Base
          boundary {
            while (true) {
              val t =
                if (k <= bias) TMin
                else if (k >= bias + TMax) TMax
                else k - bias
              if (q < t) {
                break()
              }
              output.append(encodeDigit((t + ((q - t) % (Base - t))).toInt))
              q = (q - t) / (Base - t)
              k += Base
            }
          }
          output.append(encodeDigit(q.toInt))
          bias = adapt(delta, h + 1, h == b)
          delta = 0L
          h += 1
        }
      }

      delta += 1L
      n += 1
    }

    output.toString
  }

  /** Convert an internationalized hostname to ASCII-Compatible Encoding.
    *
    * Splits the host on '.', Punycode-encodes each non-ASCII label with the "xn--" ACE prefix, and reassembles. Pure-ASCII labels pass through unchanged.
    */
  def toAscii(host: String): String = {
    val labels = host.split('.')
    val result = new StringBuilder
    var i      = 0
    while (i < labels.length) {
      if (i > 0) {
        result.append('.')
      }
      val label = labels(i)
      // Check if every character is basic (< 0x80)
      var allAscii = true
      var j        = 0
      while (j < label.length) {
        if (label.charAt(j) >= 0x80) {
          allAscii = false
        }
        j += 1
      }
      if (allAscii) {
        result.append(label)
      } else {
        // Convert label to code points for full Unicode support (surrogate pairs)
        val codePoints = {
          val buf = new scala.collection.mutable.ArrayBuffer[Int]
          var ci  = 0
          while (ci < label.length) {
            val cp = label.codePointAt(ci)
            buf += cp
            ci += Character.charCount(cp)
          }
          buf.toArray
        }
        result.append("xn--")
        result.append(encode(codePoints))
      }
      i += 1
    }
    // Preserve trailing dot if the original host ended with one
    if (host.endsWith(".")) {
      result.append('.')
    }
    result.toString
  }
}
