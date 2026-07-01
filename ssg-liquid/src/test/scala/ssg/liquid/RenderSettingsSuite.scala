/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView
import ssg.liquid.exceptions.VariableNotExistException

import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Tests ported from liqp's RenderSettingsTest.java — 12 tests. */
final class RenderSettingsSuite extends munit.FunSuite {

  private def parserWithStrictVariables(): TemplateParser =
    new TemplateParser.Builder().withStrictVariables(true).build()

  private def parserWithStrictVariablesAndLaxMode(): TemplateParser =
    new TemplateParser.Builder().withStrictVariables(true).withErrorMode(TemplateParser.ErrorMode.LAX).build()

  private def getRootCause(e: Throwable): Throwable = {
    var cause = e
    while (cause.getCause != null && cause.getCause != cause)
      cause = cause.getCause
    cause
  }

  // SSG: strict variables with STRICT ErrorMode behaves differently
  test("strict variables: undefined variable throws with name") { // ISS-1258 (ISS-1024 umbrella)
    try {
      parserWithStrictVariables().parse("{{mu}}").render()
      fail("Expected VariableNotExistException")
    } catch {
      case ex: RuntimeException =>
        val root = getRootCause(ex)
        assert(root.isInstanceOf[VariableNotExistException])
        assertEquals(root.asInstanceOf[VariableNotExistException].variableName, "mu")
    }
  }

  test("strict variables: second undefined variable throws") { // ISS-1258 (ISS-1024 umbrella)
    try {
      parserWithStrictVariables()
        .parse("{{mu}} {{qwe.asd.zxc}}")
        .render(
          TestHelper.mapOf("mu" -> "muValue")
        )
      fail("Expected VariableNotExistException")
    } catch {
      case ex: RuntimeException =>
        val root = getRootCause(ex)
        assert(root.isInstanceOf[VariableNotExistException])
        assertEquals(root.asInstanceOf[VariableNotExistException].variableName, "qwe.asd.zxc")
    }
  }

  test("strict variables: condition false branch not evaluated") {
    // Variable in untaken branch should not cause exception
    parserWithStrictVariables().parse("{% if mu == \"somethingElse\" %}{{ badVariableName }}{% endif %}").render(TestHelper.mapOf("mu" -> "muValue"))
  }

  test("strict variables: condition true branch evaluates") { // ISS-1258 (ISS-1024 umbrella)
    try {
      parserWithStrictVariables().parse("{% if mu == \"muValue\" %}{{ badVariableName }}{% endif %}").render(TestHelper.mapOf("mu" -> "muValue"))
      fail("Expected VariableNotExistException")
    } catch {
      case ex: RuntimeException =>
        val root = getRootCause(ex)
        assert(root.isInstanceOf[VariableNotExistException])
        assertEquals(root.asInstanceOf[VariableNotExistException].variableName, "badVariableName")
    }
  }

  test("strict variables: and operator checks second operand") { // ISS-1258 (ISS-1024 umbrella)
    try {
      parserWithStrictVariables().parse("{% if mu == \"muValue\" and checkThis %}{{ badVariableName }}{% endif %}").render(TestHelper.mapOf("mu" -> "muValue"))
      fail("Expected VariableNotExistException")
    } catch {
      case ex: RuntimeException =>
        val root = getRootCause(ex)
        assert(root.isInstanceOf[VariableNotExistException])
        assertEquals(root.asInstanceOf[VariableNotExistException].variableName, "checkThis")
    }
  }

  test("strict variables: and operator short-circuits false first operand") {
    parserWithStrictVariables().parse("{% if mu == \"somethingElse\" and doNotCheckThis %}{{ badVariableName }}{% endif %}").render(TestHelper.mapOf("mu" -> "muValue"))
  }

  test("strict variables: or operator skips second when first true") { // ISS-1258 (ISS-1024 umbrella)
    try {
      parserWithStrictVariables().parse("{% if mu == \"muValue\" or doNotCheckThis %}{{ badVariableName }}{% endif %}").render(TestHelper.mapOf("mu" -> "muValue"))
      fail("Expected VariableNotExistException")
    } catch {
      case ex: RuntimeException =>
        val root = getRootCause(ex)
        assert(root.isInstanceOf[VariableNotExistException])
        assertEquals(root.asInstanceOf[VariableNotExistException].variableName, "badVariableName")
    }
  }

  test("strict variables: or operator checks second when first false") { // ISS-1258 (ISS-1024 umbrella)
    try {
      parserWithStrictVariables().parse("{% if mu == \"somethingElse\" or checkThis %}{{ badVariableName }}{% endif %}").render(TestHelper.mapOf("mu" -> "muValue"))
      fail("Expected VariableNotExistException")
    } catch {
      case ex: RuntimeException =>
        val root = getRootCause(ex)
        assert(root.isInstanceOf[VariableNotExistException])
        assertEquals(root.asInstanceOf[VariableNotExistException].variableName, "checkThis")
    }
  }

  test("lax mode: records errors without throwing") {
    val parser   = parserWithStrictVariablesAndLaxMode()
    val template = parser.parse("{{a}}{{b}}{{c}}")
    assertEquals(template.errors().size(), 0)

    val rendered = template.render(TestHelper.mapOf("b" -> "FOO"))
    // 2 errors for undefined `a` and `c`
    assertEquals(template.errors().size(), 2)
    // Rendering should not terminate
    assertEquals(rendered, "FOO")
  }

  test("lax mode: records errors for non-existing variables in loops") { // ISS-1258 (ISS-1024 umbrella)
    val parser   = parserWithStrictVariablesAndLaxMode()
    val template = parser.parse(
      "{% for v in a %}{{v.b}}{% endfor %}" +
        "{% for v in badVariableName %}{{v.b}}{% endfor %}" +
        "{% for v in a %}{{v.badVariableName}}{% endfor %}"
    )
    assertEquals(template.errors().size(), 0)

    val vars = TestHelper.mapOf(
      "a" -> TestHelper.listOf(TestHelper.mapOf("b" -> "FOO"))
    )
    val rendered = template.render(vars)
    assertEquals(template.errors().size(), 2)
    assertEquals(
      template.errors().get(0).asInstanceOf[VariableNotExistException].variableName,
      "badVariableName"
    )
    assertEquals(
      template.errors().get(1).asInstanceOf[VariableNotExistException].variableName,
      "v.badVariableName"
    )
    assertEquals(rendered, "FOO")
  }

  test("environment map configurator") {
    val secretKey         = getClass.getName + ".secretKey"
    val gotEnvironmentMap = new AtomicBoolean(false)

    val parser = new TemplateParser.Builder()
      .withFilter(
        new filters.Filter("secret") {
          override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
            DataView.from(super.asString(value, context) + " " + context.getEnvironmentMap.get(secretKey))
        }
      )
      .withEnvironmentMapConfigurator { env =>
        env.put(secretKey, "world")
        gotEnvironmentMap.set(true)
      }
      .build()

    val template = parser.parse("{{ 'Hello' | secret }}")
    assert(!gotEnvironmentMap.get())
    assertEquals(template.render(), "Hello world")
    assert(gotEnvironmentMap.get())
  }

  // SSG: DataView rewrite means renderToObject() always returns DataView, not a custom ObjectAppender.
  // The original test expected the raw MyAppender object to pass through — DataView wrapping prevents this.
  // Rewritten to verify the string output is preserved (the custom MyAppender accumulation
  // still works internally, but the result is wrapped in DataView by BlockNode).
  test("custom render transformer") {
    val parser   = new TemplateParser.Builder().withRenderTransformer(new RenderSettingsSuite.CustomRenderTransformer()).build()
    val template = parser.parse("{{ 'Hello' }} {{ 'world' }}")

    // DataView wraps the result — we can only verify the string representation
    assertEquals(template.render(), "Hello world")
  }
}

object RenderSettingsSuite {

  final class MyAppender extends RenderTransformer.ObjectAppender.Controller {
    private val list = new ArrayList[Any]()

    override def getResult: Any = this

    override def append(obj: Any): Unit =
      list.add(obj)

    def getList: ArrayList[Any] = list

    override def toString: String = {
      val sb = new StringBuilder()
      val it = list.iterator()
      while (it.hasNext)
        sb.append(it.next())
      sb.toString()
    }
  }

  final class CustomRenderTransformer extends RenderTransformer {
    override def transformObject(context: TemplateContext, obj: DataView): DataView = obj

    override def newObjectAppender(context: TemplateContext, estimatedNumberOfAppends: Int): RenderTransformer.ObjectAppender.Controller =
      new MyAppender()
  }
}
