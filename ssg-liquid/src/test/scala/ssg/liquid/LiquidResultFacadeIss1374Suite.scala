/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.commons.Severity
import ssg.commons.io.FilePath
import ssg.data.DataView
import ssg.liquid.exceptions.{ ExceededMaxIterationsException, LiquidException }
import ssg.liquid.parser.Flavor

import java.util.{ HashMap, Map => JMap }

/** Differential tests for the ISS-1374 error-contract facades on ssg-liquid (docs/architecture/error-contracts.md §2.2).
  *
  * The additive `DiagResult` envelope wraps the throwing entry points WITHOUT changing them:
  *
  *   - `TemplateParser.parseResult(input[, location])` catches ONLY the module-native `LiquidException` (§1.2 rule 3) thrown unconditionally by the parser error listener (LiquidParser.scala:80-94)
  *     and maps it to a `Severity.Error` failure diagnostic — component `"ssg-liquid"`, code `"parse-error"`, native exception preserved as cause — whose position follows the §1.3 ssg-liquid row:
  *     `line = e.line` (1-based passthrough), `column = e.charPositionInLine + 1` (native `charPositionInLine` is 0-based, ANTLR convention — the off-by-one trap).
  *   - `Template.renderResult(variables)` catches `LiquidException` (code `"render-error"`, same position mapping) and `ExceededMaxIterationsException` (code `"iteration-limit"`, no position). After
  *     a successful render it drains `errors()` (the WARN/LAX-mode collected exceptions): a non-empty list becomes a DEGRADED result (the same output bytes as `render`, plus one `"render-error"`
  *     diagnostic per collected exception); an empty list is a clean success. The bare `RuntimeException` size/time-limit guards (Template.scala:88-89,106-112) are deliberately NOT caught (that would
  *     be a blanket catch — §1.2 rule 3, C12) and keep propagating.
  *
  * Ground truth for every invalid input is taken from the unchanged native parser/renderer (intercepting the thrown `LiquidException`/`ExceededMaxIterationsException`) and pinned with LITERAL
  * expected values per §3:
  *   - `"hello\n  {% endif %}"` — a stray end tag → `LiquidException(line = 2, charPositionInLine = 2)`, so `column == 3` (pins BOTH the 1-based line and the `+1` column).
  *   - `"{% if true %}yes{% endfor %}"` — a mismatched end → `LiquidException(line = 1, charPositionInLine = 19)`, so `column == 20` (pins `charPositionInLine + 1`).
  *   - `"{{ 98 > 97 }}"` under STRICT → render throws `LiquidException(line = 1, charPositionInLine = 6)`, so `column == 7`; under WARN → renders `"98"` and collects one `LiquidException` (degraded).
  */
final class LiquidResultFacadeIss1374Suite extends munit.FunSuite {

  private def parser(mode: TemplateParser.ErrorMode): TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withErrorMode(mode).build()

  private def noVars: JMap[String, DataView] = new HashMap[String, DataView]()

  // A stray {% endif %} on line 2 with two leading spaces: pins line == 2 and charPositionInLine == 2.
  private val strayEnd = "hello\n  {% endif %}"
  // A mismatched end (endfor closing an if): charPositionInLine == 19 — the off-by-one column pin.
  private val mismatchedEnd = "{% if true %}yes{% endfor %}"
  // Trailing output-tag junk: STRICT throws at render, WARN renders "98" and collects the error.
  private val outputJunk = "{{ 98 > 97 }}"

  test("ISS-1374: parseResult maps a parse LiquidException to an Error failure with the §1.3 mapped position") {
    // Ground truth from the unchanged native parser.
    val native = intercept[LiquidException](TemplateParser.DEFAULT.parse(strayEnd))
    assertEquals(native.line, 2)
    assertEquals(native.charPositionInLine, 2)

    val result = TemplateParser.DEFAULT.parseResult(strayEnd)
    assert(result.isFailure, s"a parse error must produce a failure, got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1, s"diagnostics: ${result.diagnostics}")
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-liquid")
    assertEquals(d.code, Some("parse-error"))
    assert(d.cause.exists(_.isInstanceOf[LiquidException]), s"cause must be the native LiquidException, got ${d.cause}")

    val pos = d.position.getOrElse(fail("expected a position"))
    // §1.3 ssg-liquid row: line = e.line (1-based), column = e.charPositionInLine + 1.
    assertEquals(pos.line, Some(2))
    assertEquals(pos.column, Some(3)) // native charPositionInLine 2 + 1
    // And the mapping is exactly the native fields with the documented +1.
    assertEquals(pos.line, Some(native.line))
    assertEquals(pos.column, Some(native.charPositionInLine + 1))
  }

  test("ISS-1374: parseResult pins the charPositionInLine + 1 off-by-one on a mismatched end") {
    val native = intercept[LiquidException](TemplateParser.DEFAULT.parse(mismatchedEnd))
    assertEquals(native.line, 1)
    assertEquals(native.charPositionInLine, 19)

    val result = TemplateParser.DEFAULT.parseResult(mismatchedEnd)
    assert(result.isFailure, s"expected a failure, got $result")
    val pos = result.diagnostics.head.position.getOrElse(fail("expected a position"))
    assertEquals(pos.line, Some(1))
    assertEquals(pos.column, Some(20)) // native charPositionInLine 19 + 1 — the off-by-one trap
  }

  test("ISS-1374: parseResult(input, location) overload maps the same failure for an invalid template") {
    val result = TemplateParser.DEFAULT.parseResult(mismatchedEnd, FilePath.of("page.html"))
    assert(result.isFailure, s"expected a failure, got $result")
    val d = result.diagnostics.head
    assertEquals(d.component, "ssg-liquid")
    assertEquals(d.code, Some("parse-error"))
    assertEquals(d.position.flatMap(_.column), Some(20))
  }

  test("ISS-1374: parseResult is a clean success carrying a Template that renders like the legacy parse") {
    val src    = "Hello {{ 'world' }}"
    val legacy = TemplateParser.DEFAULT.parse(src).render()
    val result = TemplateParser.DEFAULT.parseResult(src)

    assert(result.isSuccess, s"a clean parse must be a success, got $result")
    assert(!result.isDegraded)
    assertEquals(result.diagnostics, Vector.empty)
    val template = result.value.getOrElse(fail("expected a Template"))
    assertEquals(template.render(), legacy)
  }

  test("ISS-1374: renderResult maps a STRICT render LiquidException to an Error 'render-error' failure with position") {
    val native = intercept[LiquidException](parser(TemplateParser.ErrorMode.STRICT).parse(outputJunk).render())
    assertEquals(native.line, 1)
    assertEquals(native.charPositionInLine, 6)

    val result = parser(TemplateParser.ErrorMode.STRICT).parse(outputJunk).renderResult(noVars)
    assert(result.isFailure, s"a STRICT render error must be a failure, got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1, s"diagnostics: ${result.diagnostics}")
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-liquid")
    assertEquals(d.code, Some("render-error"))
    assert(d.cause.exists(_.isInstanceOf[LiquidException]), s"cause must be the native LiquidException, got ${d.cause}")
    val pos = d.position.getOrElse(fail("expected a position"))
    assertEquals(pos.line, Some(1))
    assertEquals(pos.column, Some(7)) // native charPositionInLine 6 + 1
  }

  test("ISS-1374: renderResult drains WARN-mode collected errors into a degraded result matching the legacy render bytes") {
    val legacy = parser(TemplateParser.ErrorMode.WARN).parse(outputJunk).render()
    assertEquals(legacy, "98")

    val result = parser(TemplateParser.ErrorMode.WARN).parse(outputJunk).renderResult(noVars)
    assert(result.isDegraded, s"WARN-mode suppressed errors must be degraded (value + Error), got $result")
    assert(result.hasErrors)
    assertEquals(result.value, Some(legacy)) // byte-parity with the legacy render
    assertEquals(result.diagnostics.size, 1, s"diagnostics: ${result.diagnostics}")
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-liquid")
    assertEquals(d.code, Some("render-error"))
    assert(d.cause.exists(_.isInstanceOf[LiquidException]), s"cause must be the collected native exception, got ${d.cause}")
  }

  test("ISS-1374: renderResult maps ExceededMaxIterationsException to an 'iteration-limit' failure with no position") {
    val p   = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withMaxIterations(5).build()
    val src = "{% for i in (1..100) %}{{ i }}{% endfor %}"
    intercept[ExceededMaxIterationsException](p.parse(src).render())

    val result = p.parse(src).renderResult(noVars)
    assert(result.isFailure, s"an iteration-limit breach must be a failure, got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1, s"diagnostics: ${result.diagnostics}")
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-liquid")
    assertEquals(d.code, Some("iteration-limit"))
    assertEquals(d.position, None)
    assert(
      d.cause.exists(_.isInstanceOf[ExceededMaxIterationsException]),
      s"cause must be the native ExceededMaxIterationsException, got ${d.cause}"
    )
  }

  test("ISS-1374: renderResult is a clean success carrying the same bytes as the legacy render") {
    val src    = "Hello {{ 'world' }}"
    val legacy = TemplateParser.DEFAULT.parse(src).render()
    val result = TemplateParser.DEFAULT.parse(src).renderResult(noVars)

    assert(result.isSuccess, s"a clean render must be a success, got $result")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value, Some(legacy))
  }
}
