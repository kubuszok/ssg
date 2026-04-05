/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package value

import scala.collection.immutable.ListMap

final class ValueSuite extends munit.FunSuite {

  // --- SassBoolean ---

  test("SassBoolean.sassTrue is truthy") {
    assert(SassBoolean.sassTrue.isTruthy)
    assert(!SassBoolean.sassFalse.isTruthy)
  }

  test("SassBoolean factory returns singletons") {
    assert(SassBoolean(true) eq SassBoolean.sassTrue)
    assert(SassBoolean(false) eq SassBoolean.sassFalse)
  }

  test("SassBoolean.unaryNot inverts") {
    assertEquals(SassBoolean.sassTrue.unaryNot(), SassBoolean.sassFalse)
    assertEquals(SassBoolean.sassFalse.unaryNot(), SassBoolean.sassTrue)
  }

  test("SassBoolean.assertBoolean returns itself") {
    assertEquals(SassBoolean.sassTrue.assertBoolean(), SassBoolean.sassTrue)
  }

  // --- SassNull ---

  test("SassNull is falsy") {
    assert(!SassNull.isTruthy)
  }

  test("SassNull is blank") {
    assert(SassNull.isBlank)
  }

  test("SassNull.realNull returns Nullable.Null") {
    assert(SassNull.realNull.isEmpty)
  }

  test("SassNull.unaryNot returns true") {
    assertEquals(SassNull.unaryNot(), SassBoolean.sassTrue)
  }

  // --- SassString ---

  test("SassString stores text and quotes") {
    val quoted = SassString("hello", hasQuotes = true)
    val unquoted = SassString("hello", hasQuotes = false)
    assertEquals(quoted.text, "hello")
    assert(quoted.hasQuotes)
    assert(!unquoted.hasQuotes)
  }

  test("SassString equality is by text only") {
    val a = SassString("hello", hasQuotes = true)
    val b = SassString("hello", hasQuotes = false)
    assertEquals(a, b)
  }

  test("SassString.sassLength counts codepoints") {
    val s = SassString("café")
    assertEquals(s.sassLength, 4)
  }

  test("SassString.isBlank for unquoted empty") {
    assert(SassString("", hasQuotes = false).isBlank)
    assert(!SassString("", hasQuotes = true).isBlank)
    assert(!SassString("x", hasQuotes = false).isBlank)
  }

  test("SassString.empty returns singletons") {
    assert(SassString.empty(quotes = true).text.isEmpty)
    assert(SassString.empty(quotes = false).text.isEmpty)
  }

  test("SassString.plus concatenates") {
    val a = SassString("hello", hasQuotes = true)
    val b = SassString(" world", hasQuotes = false)
    val result = a.plus(b)
    assert(result.isInstanceOf[SassString])
    assertEquals(result.asInstanceOf[SassString].text, "hello world")
  }

  // --- SassFunction ---

  test("SassFunction stores callable") {
    val f = SassFunction("myFunc")
    assertEquals(f.callable, "myFunc")
    assertEquals(f.assertFunction(), f)
  }

  test("SassFunction equality by callable") {
    assertEquals(SassFunction("a"), SassFunction("a"))
    assertNotEquals(SassFunction("a"), SassFunction("b"))
  }

  // --- SassMixin ---

  test("SassMixin stores callable") {
    val m = SassMixin("myMixin")
    assertEquals(m.callable, "myMixin")
    assertEquals(m.assertMixin(), m)
  }

  // --- SassList ---

  test("SassList stores contents with separator") {
    val list = SassList(
      List(SassString("a"), SassString("b")),
      ListSeparator.Comma
    )
    assertEquals(list.asList.length, 2)
    assertEquals(list.separator, ListSeparator.Comma)
    assert(!list.hasBrackets)
  }

  test("SassList.empty creates empty list") {
    val empty = SassList.empty()
    assertEquals(empty.asList.length, 0)
    assertEquals(empty.separator, ListSeparator.Undecided)
  }

  test("SassList empty assertMap returns empty map") {
    val empty = SassList.empty()
    val map = empty.assertMap()
    assert(map.contents.isEmpty)
  }

  test("SassList.isBlank for empty unbracketed") {
    assert(SassList.empty().isBlank)
    assert(!SassList.empty(brackets = true).isBlank)
  }

  // --- SassMap ---

  test("SassMap stores key-value pairs") {
    val map = SassMap(ListMap(
      SassString("a") -> SassString("1"),
      SassString("b") -> SassString("2")
    ))
    assertEquals(map.contents.size, 2)
    assertEquals(map.separator, ListSeparator.Comma)
  }

  test("SassMap.empty has undecided separator") {
    assertEquals(SassMap.empty.separator, ListSeparator.Undecided)
  }

  test("SassMap.asList returns key-value pairs as lists") {
    val map = SassMap(ListMap(
      SassString("a") -> SassString("1")
    ))
    val asList = map.asList
    assertEquals(asList.length, 1)
    assertEquals(asList.head.asList.length, 2)
  }

  test("SassMap.assertMap returns itself") {
    assertEquals(SassMap.empty.assertMap(), SassMap.empty)
  }

  test("Empty SassMap equals empty SassList") {
    assertEquals(SassMap.empty: Value, SassList.empty(): Value)
  }

  // --- SassArgumentList ---

  test("SassArgumentList has keywords") {
    val args = SassArgumentList(
      List(SassString("a")),
      ListMap("key" -> SassString("val")),
      ListSeparator.Comma
    )
    assertEquals(args.asList.length, 1)
    assert(!args.wereKeywordsAccessed)
    assertEquals(args.keywords.size, 1)
    assert(args.wereKeywordsAccessed)
  }

  test("SassArgumentList.keywordsWithoutMarking doesn't mark") {
    val args = SassArgumentList(
      List.empty,
      ListMap("k" -> SassString("v")),
      ListSeparator.Comma
    )
    assertEquals(args.keywordsWithoutMarking.size, 1)
    assert(!args.wereKeywordsAccessed)
  }

  // --- Value base class ---

  test("Value.asList wraps single values") {
    val s = SassString("hello")
    assertEquals(s.asList, List(s))
    assertEquals(s.lengthAsList, 1)
  }

  test("Value.assertNumber throws for non-numbers") {
    intercept[SassScriptException] {
      SassString("hello").assertNumber()
    }
  }

  test("Value.assertColor throws for non-colors") {
    intercept[SassScriptException] {
      SassString("hello").assertColor()
    }
  }

  test("Value.unaryNot returns false for truthy values") {
    assertEquals(SassString("hello").unaryNot(), SassBoolean.sassFalse)
  }

  // --- ListSeparator ---

  test("ListSeparator has correct separator chars") {
    assertEquals(ListSeparator.Space.separatorChar.get, " ")
    assertEquals(ListSeparator.Comma.separatorChar.get, ",")
    assertEquals(ListSeparator.Slash.separatorChar.get, "/")
    assert(ListSeparator.Undecided.separatorChar.isEmpty)
  }
}
