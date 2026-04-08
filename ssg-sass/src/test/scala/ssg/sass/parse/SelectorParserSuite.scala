/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package parse

import ssg.sass.ast.selector.{ AttributeSelector, PseudoSelector, SelectorList, TypeSelector }

final class SelectorParserSuite extends munit.FunSuite {

  private def parse(text: String): SelectorList =
    new SelectorParser(text).parse()

  test("parses a single type selector") {
    val list = parse("a")
    assertEquals(list.components.length, 1)
    val complex  = list.components.head
    val compound = complex.components.head.selector
    assertEquals(compound.components.length, 1)
    assert(compound.components.head.isInstanceOf[TypeSelector])
  }

  test("parses class, id, universal, parent, placeholder") {
    val list     = parse(".foo#bar*&%placeholder")
    val compound = list.components.head.components.head.selector
    val kinds    = compound.components.map(_.getClass.getSimpleName)
    assert(kinds.contains("ClassSelector"), kinds.toString)
    assert(kinds.contains("IDSelector"), kinds.toString)
    assert(kinds.contains("UniversalSelector"), kinds.toString)
    assert(kinds.contains("ParentSelector"), kinds.toString)
    assert(kinds.contains("PlaceholderSelector"), kinds.toString)
  }

  test("parses comma-separated list") {
    val list = parse(".a, .b, .c")
    assertEquals(list.components.length, 3)
  }

  test("parses descendant combinator") {
    val list    = parse(".a .b")
    val complex = list.components.head
    assertEquals(complex.components.length, 2)
    // Descendant is implicit — first component has no explicit combinators.
    assert(complex.components.head.combinators.isEmpty)
  }

  test("parses child combinator") {
    val list    = parse(".a > .b")
    val complex = list.components.head
    assertEquals(complex.components.length, 2)
    assertEquals(complex.components.head.combinators.length, 1)
    assertEquals(complex.components.head.combinators.head.value.text, ">")
  }

  test("parses next-sibling and following-sibling combinators") {
    val plus = parse(".a + .b").components.head
    assertEquals(plus.components.head.combinators.head.value.text, "+")
    val tilde = parse(".a ~ .b").components.head
    assertEquals(tilde.components.head.combinators.head.value.text, "~")
  }

  test("parses pseudo-class with raw argument") {
    val list   = parse(":nth-child(2n+1)")
    val pseudo = list.components.head.components.head.selector.components.head.asInstanceOf[PseudoSelector]
    assertEquals(pseudo.name, "nth-child")
    assert(pseudo.argument.isDefined)
    assertEquals(pseudo.argument.get, "2n+1")
    assert(pseudo.isClass)
  }

  test("parses pseudo-element with double colon") {
    val list   = parse("::before")
    val pseudo = list.components.head.components.head.selector.components.head.asInstanceOf[PseudoSelector]
    assertEquals(pseudo.name, "before")
    assert(pseudo.isElement)
  }

  test("parses :not(...) as a selector pseudo with parsed argument") {
    val list   = parse(":not(.foo)")
    val pseudo = list.components.head.components.head.selector.components.head.asInstanceOf[PseudoSelector]
    assertEquals(pseudo.name, "not")
    assert(pseudo.selector.isDefined)
  }

  test("parses attribute selector with operator and value") {
    val list = parse("""[name="value"]""")
    val attr = list.components.head.components.head.selector.components.head.asInstanceOf[AttributeSelector]
    assertEquals(attr.name.name, "name")
    assert(attr.op.isDefined)
    assertEquals(attr.value.get, "value")
  }

  test("parses bare attribute selector") {
    val list = parse("[disabled]")
    val attr = list.components.head.components.head.selector.components.head.asInstanceOf[AttributeSelector]
    assertEquals(attr.name.name, "disabled")
    assert(attr.op.isEmpty)
  }

  test("toString round-trip preserves common selectors") {
    val cases = List(
      "a",
      ".foo",
      "#bar",
      ".a.b",
      ".a > .b",
      ".a + .b",
      ".a ~ .b",
      ".foo:hover",
      "::before"
    )
    for (text <- cases) {
      val list = parse(text)
      assertEquals(list.toString, text, s"round-trip failed for: $text")
    }
  }

  test("tryParse returns Null on invalid input") {
    val r = SelectorParser.tryParse("@@@")
    assert(r.isEmpty)
  }

  // ---------------------------------------------------------------------------
  // Unification
  // ---------------------------------------------------------------------------

  test("unify of two class selectors merges them into one compound") {
    val a       = parse(".a")
    val b       = parse(".b")
    val unified = a.unify(b)
    assert(unified.isDefined)
    val s = unified.get.toString
    assert(s == ".a.b" || s == ".b.a", s)
  }

  test("unify of incompatible IDs returns Null") {
    val a       = parse("#x")
    val b       = parse("#y")
    val unified = a.unify(b)
    assert(unified.isEmpty)
  }

  test("unify of class and id merges into one compound") {
    val unified = parse(".foo").unify(parse("#bar"))
    assert(unified.isDefined)
  }
}
