/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }
import scala.jdk.CollectionConverters.*

final class RenderToObjectTailIss1148Suite extends munit.FunSuite {

  private def warnParser: TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withErrorMode(TemplateParser.ErrorMode.WARN).build()

  private def noVars: scala.collection.mutable.Map[String, Object] =
    new JHashMap[String, Object]().asScala

  test("ISS-1148 control: guarded render assigns templateContext so errors() reports the collected error") {
    val template = warnParser.parse("{{ 98 > 97 }}")
    val res      = template.render(noVars)
    assertEquals(res, "98")
    val errors = template.errors()
    assertEquals(errors.size, 1, s"expected one collected error after guarded render, got: $errors")
    assert(
      errors(0).getMessage.contains("unexpected output"),
      s"expected 'unexpected output' message, got: ${errors(0).getMessage}"
    )
  }

  test("ISS-1148 unguarded render assigns templateContext so errors() reports the collected error") {
    val template = warnParser.parse("{{ 98 > 97 }}")
    val res      = template.renderToObjectUnguarded(noVars)
    assertEquals(res.toString, "98")
    val errors = template.errors()
    assertEquals(errors.size, 1, s"expected one collected error after unguarded render, got: $errors")
    assert(
      errors(0).getMessage.contains("unexpected output"),
      s"expected 'unexpected output' message, got: ${errors(0).getMessage}"
    )
  }
}
