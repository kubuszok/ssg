/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Standard (RFC 4648) Base64 codec for inline source-map data URIs.
 *
 * Ports the `to_ascii` / `to_base64` helpers terser uses in lib/minify.js:23-31
 * to read/write `//# sourceMappingURL=data:application/json;base64,<...>`
 * comments. Upstream prefers Node's `Buffer` and falls back to
 * `atob`/`btoa` with `decodeURIComponent(escape(...))` to handle non-ASCII.
 * Neither Buffer nor atob/btoa is cross-platform (JVM/JS/Native), so this is a
 * pure-Scala codec operating on the UTF-8 byte representation of the string —
 * which is exactly what `Buffer.from(str)` / `Buffer.from(b64,"base64").toString()`
 * do (UTF-8 round-trip), keeping the output byte-exact with the Node path.
 *
 * Original source: terser lib/minify.js to_ascii+to_base64 (RFC-4648 base64)
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: to_ascii -> decode, to_base64 -> encode.
 *   Idiom: pure-Scala UTF-8 byte codec replaces Node Buffer / atob+btoa
 *     (decodeURIComponent(escape(...))), which are not cross-platform
 *     (JVM/JS/Native); the byte output matches Buffer.from(str) exactly.
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/minify.js to_ascii+to_base64 (RFC-4648 base64)
 * Covenant-verified: 2026-06-20
 */
package ssg
package js
package sourcemap

import java.nio.charset.StandardCharsets

/** Standard Base64 encode/decode over UTF-8 bytes (terser's to_ascii/to_base64). */
object Base64 {

  // RFC 4648 standard alphabet (the data:...;base64 encoding terser emits).
  private val Alphabet: Array[Char] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray

  private val Index: Array[Int] = {
    val arr = Array.fill(128)(-1)
    var i   = 0
    while (i < Alphabet.length) {
      arr(Alphabet(i).toInt) = i
      i += 1
    }
    arr
  }

  /** terser `to_base64(str)` (minify.js:29-31): encode a string's UTF-8 bytes to Base64. */
  def encode(str: String): String = {
    val bytes = str.getBytes(StandardCharsets.UTF_8)
    val sb    = new StringBuilder((bytes.length + 2) / 3 * 4)
    var i     = 0
    while (i + 2 < bytes.length) {
      val b0 = bytes(i) & 0xff
      val b1 = bytes(i + 1) & 0xff
      val b2 = bytes(i + 2) & 0xff
      sb.append(Alphabet((b0 >> 2) & 0x3f))
      sb.append(Alphabet(((b0 << 4) | (b1 >> 4)) & 0x3f))
      sb.append(Alphabet(((b1 << 2) | (b2 >> 6)) & 0x3f))
      sb.append(Alphabet(b2 & 0x3f))
      i += 3
    }
    val remaining = bytes.length - i
    if (remaining == 1) {
      val b0 = bytes(i) & 0xff
      sb.append(Alphabet((b0 >> 2) & 0x3f))
      sb.append(Alphabet((b0 << 4) & 0x3f))
      sb.append('=')
      sb.append('=')
    } else if (remaining == 2) {
      val b0 = bytes(i) & 0xff
      val b1 = bytes(i + 1) & 0xff
      sb.append(Alphabet((b0 >> 2) & 0x3f))
      sb.append(Alphabet(((b0 << 4) | (b1 >> 4)) & 0x3f))
      sb.append(Alphabet((b1 << 2) & 0x3f))
      sb.append('=')
    }
    sb.toString()
  }

  /** terser `to_ascii(b64)` (minify.js:26-28): decode a Base64 string back to its UTF-8 string. */
  def decode(b64: String): String = {
    val bytes = scala.collection.mutable.ArrayBuffer.empty[Byte]
    var acc   = 0
    var bits  = 0
    var i     = 0
    while (i < b64.length) {
      val c = b64.charAt(i)
      if (c == '=') {
        i = b64.length // padding ends the data
      } else {
        val v = if (c.toInt < 128) Index(c.toInt) else -1
        if (v < 0) throw new IllegalArgumentException(s"Invalid base64 character: $c")
        acc = (acc << 6) | v
        bits += 6
        if (bits >= 8) {
          bits -= 8
          bytes.addOne(((acc >> bits) & 0xff).toByte)
        }
        i += 1
      }
    }
    new String(bytes.toArray, StandardCharsets.UTF_8)
  }
}
