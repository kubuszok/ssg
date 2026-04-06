/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import java.net.URI

import ssg.sass.parse.ScssParser
import ssg.sass.value.{ SassNumber, SassString, Value }
import ssg.sass.visitor.findDependencies

/** Tests for the Module / EvaluationContext / Callable / FindDependenciesVisitor polish pass:
  *   - BuiltInModule.css returns an empty stylesheet
  *   - ForwardedView show/hide/prefix filtering
  *   - ShadowedView name filtering
  *   - EvaluationContext.withContext / current stack
  *   - Callable.overloadedFunction arity dispatch
  *   - FindDependenciesVisitor records meta.load-css("...") URLs
  */
final class ModuleInfraSuite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // BuiltInModule
  // ---------------------------------------------------------------------------

  test("BuiltInModule.css returns an empty stylesheet, not a throw") {
    val mod: Module[BuiltInCallable] = new BuiltInModule[BuiltInCallable]("math")
    val sheet = mod.css
    assert(sheet ne null)
    assertEquals(sheet.children.size, 0)
  }

  // ---------------------------------------------------------------------------
  // ForwardedView
  // ---------------------------------------------------------------------------

  private def makeInner(): BuiltInModule[BuiltInCallable] = {
    val fns = List(
      BuiltInCallable.function("foo", "$x", args => args.head),
      BuiltInCallable.function("bar", "$x", args => args.head)
    )
    val mxs = List(
      BuiltInCallable.mixin("mx", "$x", _ => SassString.empty())
    )
    new BuiltInModule[BuiltInCallable](
      name = "test",
      functionList = fns,
      mixinList = mxs,
      variableMap = Map[String, Value](
        "color" -> SassString("red", hasQuotes = false),
        "size"  -> SassNumber(1.0)
      )
    )
  }

  test("ForwardedView show filter only exposes listed members") {
    val inner = makeInner()
    val view = new ForwardedView[BuiltInCallable](
      inner = inner,
      shownMixinsAndFunctions = Nullable(Set("foo")),
      shownVariables = Nullable(Set("color"))
    )
    assertEquals(view.functions.keySet, Set("foo"))
    assert(view.functions.contains("foo"))
    assert(!view.functions.contains("bar"))
    assertEquals(view.variables.keySet, Set("color"))
  }

  test("ForwardedView hide filter drops listed members") {
    val inner = makeInner()
    val view = new ForwardedView[BuiltInCallable](
      inner = inner,
      hiddenMixinsAndFunctions = Nullable(Set("bar")),
      hiddenVariables = Nullable(Set("size"))
    )
    assertEquals(view.functions.keySet, Set("foo"))
    assertEquals(view.variables.keySet, Set("color"))
  }

  test("ForwardedView prefix prepends to all visible member names") {
    val inner = makeInner()
    val view  = new ForwardedView[BuiltInCallable](inner, prefix = Nullable("ns-"))
    assertEquals(view.functions.keySet, Set("ns-foo", "ns-bar"))
    assertEquals(view.mixins.keySet, Set("ns-mx"))
    assertEquals(view.variables.keySet, Set("ns-color", "ns-size"))
    // setVariable strips the prefix and forwards to the inner module
    view.setVariable("ns-color", SassString("blue", hasQuotes = false))
    assertEquals(
      inner.variables("color").asInstanceOf[SassString].text,
      "blue"
    )
  }

  // ---------------------------------------------------------------------------
  // ShadowedView
  // ---------------------------------------------------------------------------

  test("ShadowedView hides the shadowed names from variables/functions/mixins") {
    val inner = makeInner()
    val view = new ShadowedView[BuiltInCallable](
      inner = inner,
      shadowedVars = Set("color"),
      shadowedFunctions = Set("foo")
    )
    assertEquals(view.variables.keySet, Set("size"))
    assertEquals(view.functions.keySet, Set("bar"))
    assertEquals(view.mixins.keySet, Set("mx"))
  }

  // ---------------------------------------------------------------------------
  // EvaluationContext
  // ---------------------------------------------------------------------------

  test("EvaluationContext.withContext pushes/pops a current context") {
    final class StubCtx(label: String) extends EvaluationContext {
      def currentCallableNode: ssg.sass.ast.AstNode                  = null.asInstanceOf[ssg.sass.ast.AstNode]
      def warn(message: String, deprecation: Boolean = false): Unit  = ()
      override def toString:                                  String = s"Stub($label)"
    }
    assert(EvaluationContext.current.isEmpty)
    val outer = new StubCtx("outer")
    val inner = new StubCtx("inner")
    val seen = EvaluationContext.withContext(outer) {
      val a = EvaluationContext.current
      val b = EvaluationContext.withContext(inner) {
        EvaluationContext.current
      }
      val c = EvaluationContext.current
      (a, b, c)
    }
    assert(seen._1.isDefined && (seen._1.get eq outer))
    assert(seen._2.isDefined && (seen._2.get eq inner))
    assert(seen._3.isDefined && (seen._3.get eq outer))
    // Stack must be empty after the outermost frame returns.
    assert(EvaluationContext.current.isEmpty)
  }

  test("EvaluationContext.withContext restores the previous context on exception") {
    final class StubCtx extends EvaluationContext {
      def currentCallableNode: ssg.sass.ast.AstNode                 = null.asInstanceOf[ssg.sass.ast.AstNode]
      def warn(message: String, deprecation: Boolean = false): Unit = ()
    }
    val ctx = new StubCtx
    intercept[RuntimeException] {
      EvaluationContext.withContext(ctx) {
        throw new RuntimeException("boom")
      }
    }
    assert(EvaluationContext.current.isEmpty)
  }

  // ---------------------------------------------------------------------------
  // Callable.overloadedFunction
  // ---------------------------------------------------------------------------

  test("Callable.overloadedFunction dispatches by argument count") {
    val one    = SassNumber(1.0)
    val two    = SassNumber(2.0)
    val tag1   = SassString("one", hasQuotes = false)
    val tag2   = SassString("two", hasQuotes = false)
    val tagAny = SassString("rest", hasQuotes = false)
    val fn = BuiltInCallable.overloadedFunction(
      "pick",
      Map(
        "$a"             -> ((_: List[Value]) => tag1),
        "$a, $b"         -> ((_: List[Value]) => tag2),
        "$args..."       -> ((_: List[Value]) => tagAny)
      )
    )
    assertEquals(fn.callback(List(one)).asInstanceOf[SassString].text, "one")
    assertEquals(fn.callback(List(one, two)).asInstanceOf[SassString].text, "two")
    // 3 args -> rest overload
    assertEquals(
      fn.callback(List(one, two, one)).asInstanceOf[SassString].text,
      "rest"
    )
  }

  // ---------------------------------------------------------------------------
  // FindDependenciesVisitor
  // ---------------------------------------------------------------------------

  test("FindDependenciesVisitor records meta.load-css with a literal URL") {
    val source =
      "@use \"sass:meta\" as *;\n@include load-css(\"foo/bar\");\n"
    val sheet = new ScssParser(source).parse()
    val deps  = findDependencies(sheet)
    assertEquals(deps.metaLoadCss, Set(new URI("foo/bar")))
  }

  test("FindDependenciesVisitor ignores meta.load-css with a non-literal URL") {
    val source =
      "@use \"sass:meta\" as *;\n$u: \"x\";\n@include load-css($u);\n"
    val sheet = new ScssParser(source).parse()
    val deps  = findDependencies(sheet)
    assertEquals(deps.metaLoadCss, Set.empty[URI])
  }
}
