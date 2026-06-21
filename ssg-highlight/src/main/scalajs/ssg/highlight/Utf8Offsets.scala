/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Covenant: original
 *
 * Converts UTF-16 code-unit indices (as reported by web-tree-sitter's
 * Node.startIndex/endIndex on the Scala.js platform) into UTF-8 byte offsets,
 * so HighlightSpan carries the same offset semantics on JS as it does on the
 * JVM/Native FFI platforms (ts_node_start_byte over getBytes("UTF-8")). See
 * ISS-1092. */
package ssg
package highlight

/** Pure UTF-16-code-unit-index to UTF-8-byte-offset conversion for the Scala.js platform (ISS-1092).
  *
  * web-tree-sitter indexes nodes by UTF-16 code units into the JS source string. The renderer and the JVM/Native platforms index by UTF-8 bytes. This object bridges the two: [[prefix]] builds, in a
  * single pass, a prefix array mapping each UTF-16 index `i` (0..length) to the number of UTF-8 bytes occupied by the first `i` code units; [[at]] then reads any span index in O(1).
  *
  * The oracle is exactly `substring(0, i).getBytes("UTF-8").length`: the renderer slices `source.getBytes("UTF-8")` (HtmlHighlightRenderer.scala line 31) and the JVM/Native FFI feeds the same bytes,
  * so the JS span offsets must index that byte array identically. UTF-8 byte width per code unit, matching that JVM oracle:
  *   - U+0000..U+007F: 1 byte;
  *   - U+0080..U+07FF: 2 bytes;
  *   - a high surrogate (U+D800..U+DBFF) immediately followed by a low surrogate (U+DC00..U+DFFF) forms one supplementary code point: 4 bytes total, but an index landing *between* the two code units
  *     ends a substring on a lone high surrogate, which `getBytes("UTF-8")` encodes as a single `?` byte; so the prefix value at the mid-pair index is `(bytes-so-far) + 1`, and the index after the
  *     pair is `(bytes-so-far) + 4`. tree-sitter is not expected to split a code point, but the function stays total and consistent with the renderer's slicing regardless;
  *   - an unpaired surrogate (lone high or lone low): 1 byte: `getBytes("UTF-8")` substitutes a single `?` for an isolated surrogate (it is *not* the 3-byte U+FFFD replacement -- that width only
  *     appears for a real U+FFFD code point in the source);
  *   - any other BMP code unit (U+0800..U+FFFF, non-surrogate): 3 bytes.
  */
object Utf8Offsets {

  /** Build the cumulative UTF-8 byte-length prefix for `source`. `result(i)` is the UTF-8 byte offset of UTF-16 code-unit index `i`, for `i` in `0..source.length` (so `result` has `length + 1`
    * entries and `result(length)` is the total UTF-8 byte length).
    */
  def prefix(source: String): Array[Int] = {
    val n      = source.length
    val result = new Array[Int](n + 1)
    var bytes  = 0
    var i      = 0
    while (i < n) {
      result(i) = bytes
      val c = source.charAt(i)
      if (c < 0x80) {
        bytes += 1
        i += 1
      } else if (c < 0x800) {
        bytes += 2
        i += 1
      } else if (isHighSurrogate(c) && i + 1 < n && isLowSurrogate(source.charAt(i + 1))) {
        // Supplementary code point: 4 UTF-8 bytes for the pair. An index *between* the two
        // code units ends a substring on a lone high surrogate, which getBytes("UTF-8")
        // encodes as a single '?' byte -> prefix at the mid-pair index is bytes + 1.
        result(i + 1) = bytes + 1
        bytes += 4
        i += 2
      } else if (isHighSurrogate(c) || isLowSurrogate(c)) {
        // Unpaired surrogate: getBytes("UTF-8") substitutes a single '?' byte.
        bytes += 1
        i += 1
      } else {
        bytes += 3
        i += 1
      }
    }
    result(n) = bytes
    result
  }

  /** UTF-8 byte offset for UTF-16 code-unit index `idx`, clamped into the valid range `0..length` of the prefix built by [[prefix]]. */
  def at(prefixArray: Array[Int], idx: Int): Int = {
    val clamped =
      if (idx < 0) 0
      else if (idx >= prefixArray.length) prefixArray.length - 1
      else idx
    prefixArray(clamped)
  }

  private def isHighSurrogate(c: Char): Boolean = c >= 0xd800 && c <= 0xdbff

  private def isLowSurrogate(c: Char): Boolean = c >= 0xdc00 && c <= 0xdfff
}
