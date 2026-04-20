package ssg
package sass

import munit.FunSuite

/** Regression test for selector lineBreak detection. */
class LineBreakDebugSuite extends FunSuite {
  test("selector lineBreak from newline before comma") {
    val parsed = ssg.sass.parse.SelectorParser.tryParse("a\n, b")
    assert(parsed.isDefined, "parsing should succeed")
    val sl = parsed.get
    assertEquals(sl.components.length, 2)
    assertEquals(sl.components(0).lineBreak, false, "first complex should not have lineBreak")
    assertEquals(sl.components(1).lineBreak, true, "second complex should have lineBreak (newline before comma)")
  }

  test("selector lineBreak from newline after comma") {
    val parsed = ssg.sass.parse.SelectorParser.tryParse("a,\nb")
    assert(parsed.isDefined, "parsing should succeed")
    val sl = parsed.get
    assertEquals(sl.components.length, 2)
    assertEquals(sl.components(1).lineBreak, true, "second complex should have lineBreak (newline after comma)")
  }

  test("no lineBreak when on same line") {
    val parsed = ssg.sass.parse.SelectorParser.tryParse("a, b")
    assert(parsed.isDefined, "parsing should succeed")
    val sl = parsed.get
    assertEquals(sl.components.length, 2)
    assertEquals(sl.components(1).lineBreak, false, "second complex should not have lineBreak")
  }
}
