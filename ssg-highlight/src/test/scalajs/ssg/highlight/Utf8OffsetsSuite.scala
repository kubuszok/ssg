/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** Unit tests for [[Utf8Offsets]] (ISS-1092): UTF-16 code-unit index to UTF-8 byte offset conversion on the Scala.js platform.
  *
  * Expected byte offsets are hand-computed per UTF-8 width class and cross-checked against `String.getBytes("UTF-8")` semantics (the JVM/Native FFI contract HighlightSpan must match). Cases cover
  * each width: 1-byte ASCII, 2-byte (alpha = U+03B1), 3-byte (euro = U+20AC), 4-byte via a surrogate pair (grinning face = U+1F600, two UTF-16 code units), an index at string end, an unpaired
  * surrogate, and the ISS-1092 fixture's two boundary indices.
  */
final class Utf8OffsetsSuite extends munit.FunSuite {

  /** `prefix(s)(i)` must equal `s.substring(0, i).getBytes("UTF-8").length` for every valid `i`, the same oracle the JVM/Native platforms slice by. */
  private def assertMatchesGetBytes(s: String): Unit = {
    val p = Utf8Offsets.prefix(s)
    assertEquals(p.length, s.length + 1, s"prefix length for ${escape(s)}")
    var i = 0
    while (i <= s.length) {
      val expected = s.substring(0, i).getBytes("UTF-8").length
      assertEquals(Utf8Offsets.at(p, i), expected, s"index $i of ${escape(s)}")
      i += 1
    }
  }

  private def escape(s: String): String = s.map(c => if (c < 0x80) c.toString else f"\\u${c.toInt}%04x").mkString

  test("ASCII: each code unit is one byte") {
    val p = Utf8Offsets.prefix("abc")
    assertEquals(p.toSeq, Seq(0, 1, 2, 3))
  }

  test("2-byte: alpha (U+03B1) occupies two UTF-8 bytes") {
    val p = Utf8Offsets.prefix("α")
    assertEquals(p.toSeq, Seq(0, 2))
  }

  test("3-byte: euro (U+20AC) occupies three UTF-8 bytes") {
    val p = Utf8Offsets.prefix("€")
    assertEquals(p.toSeq, Seq(0, 3))
  }

  test("4-byte: grinning face (U+1F600) is a surrogate pair = 2 code units = 4 UTF-8 bytes") {
    val p = Utf8Offsets.prefix("😀")
    // two UTF-16 code units, total 4 bytes; the mid-pair index ends a substring on a lone
    // high surrogate, which getBytes("UTF-8") encodes as a single '?' byte -> 1.
    assertEquals(p.toSeq, Seq(0, 1, 4))
    // cross-check against the JVM oracle the renderer slices by.
    assertEquals("😀".substring(0, 1).getBytes("UTF-8").length, 1)
    assertEquals("😀".getBytes("UTF-8").length, 4)
  }

  test("index at string end maps to total byte length") {
    val s = "a€b"
    val p = Utf8Offsets.prefix(s)
    assertEquals(Utf8Offsets.at(p, s.length), 5) // 1 + 3 + 1
  }

  test("ISS-1092 fixture boundary indices: UTF-16 18 maps to byte 23, 19 maps to byte 24") {
    val source = "// α€😀\nconst x = 1;"
    val p      = Utf8Offsets.prefix(source)
    assertEquals(Utf8Offsets.at(p, 18), 23)
    assertEquals(Utf8Offsets.at(p, 19), 24)
    assertEquals(Utf8Offsets.at(p, source.length), 25) // total UTF-8 byte length
  }

  test("out-of-range indices are clamped into [0, length]") {
    val p = Utf8Offsets.prefix("α")
    assertEquals(Utf8Offsets.at(p, -5), 0)
    assertEquals(Utf8Offsets.at(p, 99), 2)
  }

  test("matches getBytes(UTF-8) across all width classes") {
    assertMatchesGetBytes("// α€😀\nconst x = 1;")
    assertMatchesGetBytes("plain ascii only")
    assertMatchesGetBytes("αβγ€😀😀x")
    assertMatchesGetBytes("")
  }

  test("unpaired high surrogate encodes as a single byte (matches getBytes)") {
    val s = "a\ud83dz" // lone high surrogate between ASCII
    val p = Utf8Offsets.prefix(s)
    // 'a' = 1 byte, lone surrogate = 1 '?' byte, 'z' = 1 byte
    assertEquals(p.toSeq, Seq(0, 1, 2, 3))
    assertEquals(Utf8Offsets.at(p, s.length), s.getBytes("UTF-8").length)
    assertEquals(s.getBytes("UTF-8").length, 3) // pin the JVM oracle
  }
}
