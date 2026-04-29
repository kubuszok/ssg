/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.{ CssValue, ModifiableCssAtRule, ModifiableCssComment, ModifiableCssDeclaration, ModifiableCssNode, ModifiableCssStyleRule, ModifiableCssStylesheet }
import ssg.sass.ast.selector.{ ClassSelector, Combinator, ComplexSelector, ComplexSelectorComponent, CompoundSelector, SelectorList }
import ssg.sass.parse.SelectorParser
import ssg.sass.util.{ FileSpan, ModifiableBox }
import ssg.sass.value.{ ListSeparator, SassColor, SassList, SassMap, SassString, Value }
import ssg.sass.Nullable
import scala.collection.immutable.ListMap

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
    val sel  = new SelectorParser(selector).parse()
    val rule = new ModifiableCssStyleRule(
      new ModifiableBox[SelectorList](sel).seal(),
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
    assertEquals(result.css, "a{color:red}")
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

  // --- Number formatting (stage A.1: _writeNumber port) -------------------

  private def fmtNum(n: Double, compressed: Boolean = false): String = {
    val v = new SerializeVisitor(
      style = if (compressed) OutputStyle.Compressed else OutputStyle.Expanded
    )
    // Exercise through formatSassNumber by wrapping in a SassNumber.
    val num = ssg.sass.value.SassNumber(n)
    // Build a trivial declaration and serialize to extract the value text.
    val decl = new ModifiableCssDeclaration(
      str("x"),
      new CssValue[Value](num, span),
      span,
      parsedAsSassScript = true
    )
    val rule  = styleRule("a", List(decl))
    val sheet = stylesheet(List(rule))
    val css   = v.serialize(sheet).css
    // Extract between "x:" and the next `}` or `;` (compressed mode does
    // not emit a trailing `;` for the last declaration).
    val start = css.indexOf("x:") + 2
    val sIdx  = css.indexOf(';', start)
    val bIdx  = css.indexOf('}', start)
    val end   =
      if (sIdx >= 0 && (bIdx < 0 || sIdx < bIdx)) sIdx
      else bIdx
    val raw = css.substring(start, end).trim
    raw
  }

  test("writeNumber: integer values") {
    assertEquals(fmtNum(0.0), "0")
    assertEquals(fmtNum(1.0), "1")
    assertEquals(fmtNum(-1.0), "-1")
    assertEquals(fmtNum(42.0), "42")
    assertEquals(fmtNum(1000000.0), "1000000")
  }

  test("writeNumber: simple decimals") {
    assertEquals(fmtNum(0.5), "0.5")
    assertEquals(fmtNum(-0.5), "-0.5")
    assertEquals(fmtNum(3.14), "3.14")
    assertEquals(fmtNum(1.25), "1.25")
  }

  test("writeNumber: compressed mode strips leading zero") {
    assertEquals(fmtNum(0.5, compressed = true), ".5")
    // Note: dart-sass's canWriteDirectly path only strips a leading `0`,
    // not `-0`, so `-0.5` compressed is emitted verbatim in the fast path.
    assertEquals(fmtNum(-0.5, compressed = true), "-0.5")
    assertEquals(fmtNum(0.125, compressed = true), ".125")
    assertEquals(fmtNum(1.5, compressed = true), "1.5")
  }

  test("writeNumber: rounds to precision (10 digits)") {
    // 1/3 rounds to 0.3333333333
    assertEquals(fmtNum(1.0 / 3.0), "0.3333333333")
    // 2/3 rounds to 0.6666666667
    assertEquals(fmtNum(2.0 / 3.0), "0.6666666667")
  }

  test("writeNumber: negative zero emits as 0") {
    assertEquals(fmtNum(-0.0), "0")
  }

  test("writeNumber: strips trailing zeros after rounding") {
    assertEquals(fmtNum(1.2000000000001), "1.2")
  }

  test("writeNumber: removeExponent handles large numbers") {
    assertEquals(SerializeVisitor.removeExponent("1e21"), "1" + "0" * 21)
    assertEquals(SerializeVisitor.removeExponent("1.5e5"), "150000")
    assertEquals(SerializeVisitor.removeExponent("-1e3"), "-1000")
  }

  test("writeNumber: removeExponent handles small numbers") {
    assertEquals(SerializeVisitor.removeExponent("1e-7"), "0.0000001")
    assertEquals(SerializeVisitor.removeExponent("1.5e-5"), "0.000015")
    assertEquals(SerializeVisitor.removeExponent("-1e-3"), "-0.001")
  }

  test("writeNumber: no exponent passes through") {
    assertEquals(SerializeVisitor.removeExponent("123.45"), "123.45")
    assertEquals(SerializeVisitor.removeExponent("-7"), "-7")
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

  // dart-sass `_IsInvisibleVisitor.visitCssAtRule` returns false — unknown
  // at-rules are NEVER invisible, even when empty, because we can't guarantee
  // that e.g. `@foo {}` isn't meaningful.
  test("serialize keeps an empty @media at-rule (at-rules are never invisible)") {
    val media = new ModifiableCssAtRule(str("media"), span, value = Nullable(str("print")))
    val s     = stylesheet(List(media))
    assertEquals(SerializeVisitor.serialize(s).css, "@media print {}\n")
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

  // --- Stage A.2: string formatting ----------------------------------------

  private def fmtValue(v: Value, compressed: Boolean = false): String = {
    val vis = new SerializeVisitor(
      style = if (compressed) OutputStyle.Compressed else OutputStyle.Expanded
    )
    val decl = new ModifiableCssDeclaration(
      str("x"),
      new CssValue[Value](v, span),
      span,
      parsedAsSassScript = true
    )
    val rule  = styleRule("a", List(decl))
    val sheet = stylesheet(List(rule))
    val css   = vis.serialize(sheet).css
    val start = css.indexOf("x:") + 2
    val sIdx  = css.indexOf(';', start)
    val bIdx  = css.indexOf('}', start)
    val end   =
      if (sIdx >= 0 && (bIdx < 0 || sIdx < bIdx)) sIdx
      else bIdx
    css.substring(start, end).trim
  }

  test("writeString: unquoted string emits raw text") {
    assertEquals(fmtValue(new SassString("red", hasQuotes = false)), "red")
  }

  test("writeString: quoted string prefers double quotes") {
    assertEquals(fmtValue(new SassString("hi", hasQuotes = true)), "\"hi\"")
  }

  test("writeString: uses single quotes when text has double and no single") {
    assertEquals(fmtValue(new SassString("a\"b", hasQuotes = true)), "'a\"b'")
  }

  test("writeString: escapes double quote when both present") {
    assertEquals(fmtValue(new SassString("a\"'b", hasQuotes = true)), "\"a\\\"'b\"")
  }

  test("writeString: escapes backslash") {
    assertEquals(fmtValue(new SassString("a\\b", hasQuotes = true)), "\"a\\\\b\"")
  }

  test("writeString: hex-escapes control chars with trailing space") {
    assertEquals(fmtValue(new SassString("a\u0001b", hasQuotes = true)), "\"a\\1 b\"")
  }

  // --- Stage A.3: list and map formatting ----------------------------------

  private def u(s: String): Value = new SassString(s, hasQuotes = false)

  test("writeList: comma-separated expanded uses ', '") {
    val l = SassList(List(u("a"), u("b"), u("c")), ListSeparator.Comma)
    assertEquals(fmtValue(l), "a, b, c")
  }

  test("writeList: comma-separated compressed uses ','") {
    val l = SassList(List(u("a"), u("b"), u("c")), ListSeparator.Comma)
    assertEquals(fmtValue(l, compressed = true), "a,b,c")
  }

  test("writeList: space-separated uses single space") {
    val l = SassList(List(u("1px"), u("2px"), u("3px")), ListSeparator.Space)
    assertEquals(fmtValue(l), "1px 2px 3px")
  }

  test("writeList: slash-separated expanded uses ' / '") {
    val l = SassList(List(u("1"), u("2")), ListSeparator.Slash)
    assertEquals(fmtValue(l), "1 / 2")
  }

  test("writeList: slash-separated compressed uses '/'") {
    val l = SassList(List(u("1"), u("2")), ListSeparator.Slash)
    assertEquals(fmtValue(l, compressed = true), "1/2")
  }

  // --- Stage A.5: color formatting -----------------------------------------

  private def rgb(r: Int, g: Int, b: Int, a: Double = 1.0): SassColor =
    SassColor.rgb(Nullable(r.toDouble), Nullable(g.toDouble), Nullable(b.toDouble), Nullable(a))

  // dart-sass serialize.dart:815-826: in expanded mode, named colors take
  // priority over hex, and hex always uses the 6-digit form. Short hex is
  // compressed-only.
  test("writeColor: opaque rgb prefers named color, then 6-digit hex") {
    // white is a named color — preferred over #fff in expanded mode
    assertEquals(fmtValue(rgb(0xff, 0xff, 0xff)), "white")
    // #aabbcc has no named color — 6-digit hex in expanded mode
    assertEquals(fmtValue(rgb(0xaa, 0xbb, 0xcc)), "#aabbcc")
  }

  test("writeColor: opaque rgb falls back to full hex when no shorthand") {
    assertEquals(fmtValue(rgb(0xab, 0xcd, 0xef)), "#abcdef")
  }

  test("writeColor: named color preferred when strictly shorter") {
    // red = #f00 (4 chars) vs "red" (3) -> red
    assertEquals(fmtValue(rgb(0xff, 0x00, 0x00)), "red")
  }

  test("writeColor: non-opaque emits rgba(...)") {
    assertEquals(fmtValue(rgb(10, 20, 30, 0.5)), "rgba(10, 20, 30, 0.5)")
  }

  test("writeColor: compressed rgba drops spaces") {
    // In compressed mode, the leading zero in alpha 0.5 is stripped to .5
    assertEquals(fmtValue(rgb(10, 20, 30, 0.5), compressed = true), "rgba(10,20,30,.5)")
  }

  test("writeMap: throws in non-inspect mode (maps aren't valid CSS values)") {
    val m = new SassMap(ListMap[Value, Value](u("a") -> u("1"), u("b") -> u("2")))
    // dart-sass visitMap: maps throw SassScriptException in non-inspect mode.
    // The serializer wraps this as a SassException when emitting a declaration.
    intercept[SassException] {
      fmtValue(m)
    }
  }

  test("writeMap: renders as (k: v, k2: v2) in inspect mode") {
    val m = new SassMap(ListMap[Value, Value](u("a") -> u("1"), u("b") -> u("2")))
    assertEquals(SerializeVisitor.serializeValue(m, inspect = true), "(a: 1, b: 2)")
  }

  // --- Stage A.4: selector formatting --------------------------------------

  private def cls(name: String): CompoundSelector =
    new CompoundSelector(List(new ClassSelector(name, span)), span)

  private def complex(
    parts:     List[(String, Nullable[Combinator])],
    lineBreak: Boolean = false
  ): ComplexSelector = {
    val comps = parts.map { case (name, combOpt) =>
      val combinators =
        if (combOpt.isDefined) List(new CssValue[Combinator](combOpt.get, span))
        else Nil
      new ComplexSelectorComponent(cls(name), combinators, span)
    }
    new ComplexSelector(Nil, comps, span, lineBreak = lineBreak)
  }

  private def selList(cs: ComplexSelector*): SelectorList =
    new SelectorList(cs.toList, span)

  private def styleRuleSel(selector: SelectorList, children: List[ModifiableCssNode]): ModifiableCssStyleRule = {
    val rule = new ModifiableCssStyleRule(
      new ModifiableBox[SelectorList](selector).seal(),
      span
    )
    for (c <- children) rule.addChild(c)
    rule
  }

  private def fmtSelector(sel: SelectorList, compressed: Boolean = false): String = {
    val vis = new SerializeVisitor(
      style = if (compressed) OutputStyle.Compressed else OutputStyle.Expanded
    )
    val rule  = styleRuleSel(sel, List(declaration("color", "red")))
    val sheet = stylesheet(List(rule))
    val css   = vis.serialize(sheet).css
    // Extract text before the first `{` (trim trailing space/newline).
    val braceIdx = css.indexOf('{')
    css.substring(0, braceIdx).replaceAll("\\s+$", "")
  }

  test("writeSelector: child combinator expanded uses surrounding spaces") {
    val sel = selList(
      complex(List(("a", Nullable(Combinator.Child)), ("b", Nullable.empty)))
    )
    assertEquals(fmtSelector(sel), ".a > .b")
  }

  test("writeSelector: child combinator compressed has no spaces") {
    val sel = selList(
      complex(List(("a", Nullable(Combinator.Child)), ("b", Nullable.empty)))
    )
    assertEquals(fmtSelector(sel, compressed = true), ".a>.b")
  }

  test("writeSelector: next-sibling combinator expanded vs compressed") {
    val sel = selList(
      complex(List(("a", Nullable(Combinator.NextSibling)), ("b", Nullable.empty)))
    )
    assertEquals(fmtSelector(sel), ".a + .b")
    assertEquals(fmtSelector(sel, compressed = true), ".a+.b")
  }

  test("writeSelector: following-sibling combinator expanded vs compressed") {
    val sel = selList(
      complex(List(("a", Nullable(Combinator.FollowingSibling)), ("b", Nullable.empty)))
    )
    assertEquals(fmtSelector(sel), ".a ~ .b")
    assertEquals(fmtSelector(sel, compressed = true), ".a~.b")
  }

  test("writeSelector: descendant combinator uses space in both modes") {
    val sel = selList(
      complex(List(("a", Nullable.empty), ("b", Nullable.empty)))
    )
    assertEquals(fmtSelector(sel), ".a .b")
    assertEquals(fmtSelector(sel, compressed = true), ".a .b")
  }

  test("writeSelector: multiple complex selectors without lineBreak join with `, ` expanded and `,` compressed") {
    // dart-sass `visitSelectorList`: if the complex selector isn't marked
    // with `lineBreak`, the separator is a single optional space — matching
    // `_writeOptionalSpace`. This is the common case for authored one-line
    // selector lists like `.a, .b, .c { ... }`.
    val sel = selList(
      complex(List(("a", Nullable.empty))),
      complex(List(("b", Nullable.empty))),
      complex(List(("c", Nullable.empty)))
    )
    assertEquals(fmtSelector(sel), ".a, .b, .c")
    assertEquals(fmtSelector(sel, compressed = true), ".a,.b,.c")
  }

  test("writeSelector: complex selectors marked lineBreak split with `,\\n` expanded") {
    // When `complex.lineBreak` is true, dart-sass emits `,\n<indent>`
    // instead of `, `. `lineBreak` is propagated through `@extend` and
    // nested-selector resolution when the source selectors were on
    // separate lines.
    val sel = selList(
      complex(List(("a", Nullable.empty))),
      complex(List(("b", Nullable.empty)), lineBreak = true),
      complex(List(("c", Nullable.empty)), lineBreak = true)
    )
    assertEquals(fmtSelector(sel), ".a,\n.b,\n.c")
    assertEquals(fmtSelector(sel, compressed = true), ".a,.b,.c")
  }

  test("compressed: rule with only a non-preserved comment is skipped") {
    val comment = new ModifiableCssComment("/* hi */", span)
    val rule    = styleRule("a", List(comment))
    val s       = stylesheet(List(rule))
    assertEquals(SerializeVisitor.serializeCompressed(s).css, "")
  }
}
