/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

/** Missing-method port for ISS-1149: `Template.renderUnguarded(TemplateContext)` and its private companion `renderToObjectUnguarded(TemplateContext)` were absent from the SSG port even though liqp
  * `Template.java:396-409` defines both parent-context overloads:
  *
  * {{{
  * public String renderUnguarded(TemplateContext parent) {
  *     return renderToObjectUnguarded(parent).toString();
  * }
  * private Object renderToObjectUnguarded(TemplateContext parent) {
  *     return renderToObjectUnguarded(new HashMap<String, Object>(), parent, true);
  * }
  * }}}
  *
  * These overloads render the template against a supplied parent context, delegating to the 3-arg `renderToObjectUnguarded` which — for a non-null parent — builds `parent.newChildContext(variables)`
  * (Template.scala:181-182, liqp Template.java:358-362). The child then resolves variables local-first and falls through to the enclosing parent scope (TemplateContext.get,
  * TemplateContext.scala:73-82).
  *
  * Proof-of-red is stash-based: without the two new overloads in Template.scala the call to `template.renderUnguarded(parent)` below does not compile (no such single-`TemplateContext` overload
  * exists).
  */
final class RenderUnguardedContextIss1149Suite extends munit.FunSuite {

  test("ISS-1149 renderUnguarded(parent) wires the parent context through so the child inherits parent variables") {
    // Build a parent context carrying a variable `x` that the template does NOT itself define.
    val parser = TemplateParser.DEFAULT
    val parent = new TemplateContext(parser)
    parent.put("x", DataView("from-parent"))

    // The template references only `x` — it can resolve only if the parent context is wired through.
    val template = parser.parse("{{ x }}")
    val rendered = template.renderUnguarded(parent)

    assertEquals(rendered, "from-parent")
  }

  test("ISS-1149 renderUnguarded(parent) child scope shadows parent, but falls through for unset keys") {
    val parser = TemplateParser.DEFAULT
    val parent = new TemplateContext(parser)
    parent.put("greeting", DataView("hello"))
    parent.put("name", DataView("world"))

    // `assign` mutates the child scope; the untouched `name` must still resolve from the parent.
    val template = parser.parse("{% assign greeting = 'hi' %}{{ greeting }} {{ name }}")
    val rendered = template.renderUnguarded(parent)

    assertEquals(rendered, "hi world")
  }
}
