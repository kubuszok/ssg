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

  test("vlqEncode encodes signed integers per source map v3 spec") {
    // Reference values per source-map spec
    assertEquals(SerializeVisitor.vlqEncode(0), "A")
    assertEquals(SerializeVisitor.vlqEncode(1), "C")
    assertEquals(SerializeVisitor.vlqEncode(-1), "D")
    assertEquals(SerializeVisitor.vlqEncode(15), "e")
    assertEquals(SerializeVisitor.vlqEncode(16), "gB")
    assertEquals(SerializeVisitor.vlqEncode(-16), "hB")
  }

  test("serialize without sourceMap flag returns empty source map") {
    val rule   = styleRule("a", List(declaration("color", "red")))
    val s      = stylesheet(List(rule))
    val result = new SerializeVisitor(sourceMap = false).serialize(s)
    assert(result.sourceMap.isEmpty)
  }

  test("serialize with sourceMap flag returns v3 JSON") {
    val rule   = styleRule("a", List(declaration("color", "red")))
    val s      = stylesheet(List(rule))
    val result = new SerializeVisitor(sourceMap = true).serialize(s)
    assert(result.sourceMap.isDefined)
    val json = result.sourceMap.get
    assert(json.contains("\"version\":3"), s"json=$json")
    assert(json.contains("\"sources\":["), s"json=$json")
    assert(json.contains("\"names\":[]"), s"json=$json")
    assert(json.contains("\"mappings\":\""), s"json=$json")
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

  // ---- Invisible-parent skipping (wrong-output parity fixes) ---------------

  test("serialize skips an empty style rule") {
    val empty  = styleRule("a", Nil)
    val s      = stylesheet(List(empty))
    val result = SerializeVisitor.serialize(s)
    assertEquals(result.css, "")
  }

  test("serialize skips a style rule whose only children are empty rules") {
    val innerEmpty = styleRule("b", Nil)
    val outer      = styleRule("a", List(innerEmpty))
    val s          = stylesheet(List(outer))
    val result     = SerializeVisitor.serialize(s)
    assertEquals(result.css, "")
  }

  test("serialize skips an empty @media at-rule") {
    val media = new ModifiableCssAtRule(str("media"), span, value = Nullable(str("print")))
    val s     = stylesheet(List(media))
    assertEquals(SerializeVisitor.serialize(s).css, "")
  }

  test("serialize keeps a non-empty rule next to an empty sibling") {
    val empty  = styleRule("a", Nil)
    val real   = styleRule("b", List(declaration("color", "red")))
    val s      = stylesheet(List(empty, real))
    val result = SerializeVisitor.serialize(s)
    assert(!result.css.contains("a {"), s"css=${result.css}")
    assert(result.css.contains("b {"), s"css=${result.css}")
    assert(result.css.contains("color: red;"), s"css=${result.css}")
  }

  test("serialize keeps a childless at-rule (e.g. @charset)") {
    val atRule = new ModifiableCssAtRule(
      str("charset"),
      span,
      childless = true,
      value = Nullable(str("\"UTF-8\""))
    )
    val s = stylesheet(List(atRule))
    assertEquals(SerializeVisitor.serialize(s).css.trim, "@charset \"UTF-8\";")
  }

  test("serialize keeps a rule that contains only a loud comment") {
    val comment = new ModifiableCssComment("/* hi */", span)
    val rule    = styleRule("a", List(comment))
    val s       = stylesheet(List(rule))
    val result  = SerializeVisitor.serialize(s)
    assert(result.css.contains("/* hi */"), s"css=${result.css}")
    assert(result.css.contains("a {"), s"css=${result.css}")
  }

  test("compressed: empty rule is skipped") {
    val empty = styleRule("a", Nil)
    val s     = stylesheet(List(empty))
    assertEquals(SerializeVisitor.serializeCompressed(s).css, "")
  }

  test("compressed: rule with only a non-preserved comment is skipped") {
    val comment = new ModifiableCssComment("/* hi */", span)
    val rule    = styleRule("a", List(comment))
    val s       = stylesheet(List(rule))
    assertEquals(SerializeVisitor.serializeCompressed(s).css, "")
  }
}
