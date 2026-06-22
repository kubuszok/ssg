/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** Red reproducer for ISS-1148: `Template.renderToObjectUnguarded` omits the tail of liqp `Template.renderToObjectUnguarded` (original-src/liqp/src/main/java/liqp/Template.java:355-380).
  *
  * The guarded `render`/`renderToObject` path (Template.scala:87-116) assigns `this.templateContext = context` (mirroring liqp Template.java:358-362) before rendering, so `Template.errors()`
  * (Template.scala:67-69, reading `this.templateContext.errors()`) reflects the errors collected during THAT render. The UNGUARDED path (Template.scala:125-140, used by include tags and `where_exp`)
  * builds a local `context`, renders against it, and returns — but never assigns `this.templateContext`. Per liqp Template.java:358 (`this.templateContext = ...`) the field MUST be assigned on the
  * unguarded path too, otherwise `Template.errors()` reads a stale/uninitialized context and reports no errors even though the render collected some.
  *
  * Reproduction (gap a — errors() after render): Under the JEKYLL flavor, `evaluateInOutputTag` is false (Flavor.scala:54), so `{{ 98 > 97 }}` parses the leading `term` (`98`) and leaves `> 97` as
  * trailing `unparsed`. In WARN error mode `OutputNode.render` (OutputNode.scala:65-66) does NOT throw — it calls `context.addError(...)` with an "unexpected output" `LiquidException` and still
  * renders "98". This deterministically collects exactly one error into the render context, with no exception, on both the guarded and unguarded paths.
  *
  *   - Control: the guarded `render()` assigns `this.templateContext`, so `template.errors()` reports the one collected error. (Passes today; must keep passing after the fix.)
  *   - Red: `renderToObjectUnguarded(variables)` (Template.scala:147 → (variables, null, true)) renders the same template but never assigns `this.templateContext`, so `template.errors()` reports zero
  *     errors even though the render collected one. This assertion fails until the unguarded path assigns `this.templateContext` per liqp Template.java:358.
  */
final class RenderToObjectTailIss1148Suite extends munit.FunSuite {

  private def warnParser: TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withErrorMode(TemplateParser.ErrorMode.WARN).build()

  private def noVars: JHashMap[String, DataView] = new JHashMap[String, DataView]()

  // Control: the guarded render path assigns this.templateContext, so errors() reflects the render.
  test("ISS-1148 control: guarded render assigns templateContext so errors() reports the collected error") {
    val template = warnParser.parse("{{ 98 > 97 }}")
    val res      = template.render(noVars)
    assertEquals(res, "98")
    val errors = template.errors()
    assertEquals(errors.size(), 1, s"expected one collected error after guarded render, got: $errors")
    assert(
      errors.get(0).getMessage.contains("unexpected output"),
      s"expected 'unexpected output' message, got: ${errors.get(0).getMessage}"
    )
  }

  // Red: the unguarded path must also assign this.templateContext (liqp Template.java:358) so
  // that Template.errors() reflects the errors collected during the unguarded render.
  test("ISS-1148 unguarded render assigns templateContext so errors() reports the collected error") {
    val template = warnParser.parse("{{ 98 > 97 }}")
    val res      = template.renderToObjectUnguarded(noVars)
    assertEquals(res.toString, "98")
    val errors = template.errors()
    assertEquals(errors.size(), 1, s"expected one collected error after unguarded render, got: $errors")
    assert(
      errors.get(0).getMessage.contains("unexpected output"),
      s"expected 'unexpected output' message, got: ${errors.get(0).getMessage}"
    )
  }
}
