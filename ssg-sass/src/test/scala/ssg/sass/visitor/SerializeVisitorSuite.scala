/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.{ CssValue, ModifiableCssAtRule, ModifiableCssComment, ModifiableCssDeclaration, ModifiableCssNode, ModifiableCssStyleRule, ModifiableCssStylesheet }
import ssg.sass.util.{ FileSpan, ModifiableBox }
import ssg.sass.value.{ SassString, Value }
import ssg.sass.Nullable

final class SerializeVisitorSuite extends munit.FunSuite {

  private def span = FileSpan.synthetic("")

  private def str(s: String): CssValue[String] = new CssValue(s, span)

  private def unquoted(s: String): Value = new SassString(s, hasQuotes = false)

  private def declaration(name: String, value: String): ModifiableCssDeclaration =
    new ModifiableCssDeclaration(
      str(name),
      new CssValue[Value](unquoted(value), span),
      span,
      parsedAsSassScript = true
    )

  private def styleRule(selector: String, children: List[ModifiableCssNode]): ModifiableCssStyleRule = {
    val rule = new ModifiableCssStyleRule(
      new ModifiableBox[Any](selector).seal(),
      span
    )
    for (c <- children) rule.addChild(c)
    rule
  }

  private def stylesheet(children: List[ModifiableCssNode]): ModifiableCssStylesheet = {
    val sheet = new ModifiableCssStylesheet(span)
    for (c <- children) sheet.addChild(c)
    sheet
  }

  // --- Tests ---

  test("serialize empty stylesheet") {
    val s      = stylesheet(Nil)
    val result = SerializeVisitor.serialize(s)
    assertEquals(result.css, "")
  }

  test("serialize single declaration in style rule") {
    val decl   = declaration("color", "red")
    val rule   = styleRule("a", List(decl))
    val s      = stylesheet(List(rule))
    val result = SerializeVisitor.serialize(s)
    assert(result.css.contains("a"))
    assert(result.css.contains("color: red;"))
  }

  test("serialize multiple declarations") {
    val decls = List(
      declaration("color", "red"),
      declaration("font-size", "14px"),
      declaration("margin", "10px")
    )
    val rule   = styleRule(".button", decls)
    val s      = stylesheet(List(rule))
    val result = SerializeVisitor.serialize(s)
    assert(result.css.contains("color: red;"))
    assert(result.css.contains("font-size: 14px;"))
    assert(result.css.contains("margin: 10px;"))
  }

  test("serialize multiple style rules") {
    val rule1  = styleRule("a", List(declaration("color", "red")))
    val rule2  = styleRule("b", List(declaration("color", "blue")))
    val s      = stylesheet(List(rule1, rule2))
    val result = SerializeVisitor.serialize(s)
    assert(result.css.contains("a {"))
    assert(result.css.contains("b {"))
  }

  test("serialize expanded style has indentation and newlines") {
    val decl   = declaration("color", "red")
    val rule   = styleRule("a", List(decl))
    val s      = stylesheet(List(rule))
    val result = new SerializeVisitor(style = OutputStyle.Expanded).serialize(s)
    assert(result.css.contains('\n'))
    assert(result.css.contains("  color")) // indented
  }

  test("serialize compressed style has no whitespace") {
    val decl   = declaration("color", "red")
    val rule   = styleRule("a", List(decl))
    val s      = stylesheet(List(rule))
    val result = new SerializeVisitor(style = OutputStyle.Compressed).serialize(s)
    assertEquals(result.css, "a{color:red;}")
  }

  test("serialize preserves /* comments */") {
    val comment = new ModifiableCssComment("/* hello */", span)
    val s       = stylesheet(List(comment))
    val result  = SerializeVisitor.serialize(s)
    assert(result.css.contains("/* hello */"))
  }

  test("serialize skips non-preserved comments in compressed mode") {
    val comment = new ModifiableCssComment("/* hello */", span)
    val s       = stylesheet(List(comment))
    val result  = SerializeVisitor.serializeCompressed(s)
    assert(!result.css.contains("hello"))
  }

  test("serialize preserves /*! comments in compressed mode") {
    val comment = new ModifiableCssComment("/*! keep */", span)
    val s       = stylesheet(List(comment))
    val result  = SerializeVisitor.serializeCompressed(s)
    assert(result.css.contains("keep"))
  }

  test("serialize at-rule with value") {
    val atRule = new ModifiableCssAtRule(
      str("charset"),
      span,
      childless = true,
      value = Nullable(str("\"UTF-8\""))
    )
    val s      = stylesheet(List(atRule))
    val result = SerializeVisitor.serialize(s)
    assert(result.css.contains("@charset \"UTF-8\";"))
  }

  test("serialize at-rule with block") {
    val inner  = styleRule("from", List(declaration("color", "red")))
    val atRule = new ModifiableCssAtRule(
      str("keyframes"),
      span,
      value = Nullable(str("fade"))
    )
    atRule.addChild(inner)
    val s      = stylesheet(List(atRule))
    val result = SerializeVisitor.serialize(s)
    assert(result.css.contains("@keyframes fade"))
  }
}
