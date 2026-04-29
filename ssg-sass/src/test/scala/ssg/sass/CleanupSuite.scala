/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

import ssg.sass.value.SassNumber

/** Tests for the cleanup pass:
  *   - Environment.closure() snapshot isolation
  *   - Environment.global() returning a fresh global-only env
  *   - Configuration.throwErrorForUnknownVariables / implicitConfig
  *   - UserDefinedCallable.name reading from the declaration
  *   - Second law of extend (specificity filtering) round-trip
  */
final class CleanupSuite extends munit.FunSuite {

  test("Environment.closure sees later assignments in captured scopes (dart-sass semantics)") {
    // dart-sass closure() shares the existing scope chain (shallow-copied
    // list of maps), so mutations to scopes visible when the closure was
    // created are reflected. Only new scopes pushed after capture are
    // invisible.
    val env = Environment()
    env.setVariable("a", SassNumber(1.0))
    val closure = env.closure()
    env.setVariable("a", SassNumber(99.0))
    env.setVariable("b", SassNumber(2.0))
    assertEquals(closure.getVariable("a").get.toCssString(), "99")
    assertEquals(closure.getVariable("b").get.toCssString(), "2")
  }

  test("Environment.closure does not see scopes pushed after capture") {
    val env = Environment()
    env.setVariable("a", SassNumber(1.0))
    val closure = env.closure()
    env.withinScope(semiGlobal = false) {
      env.setLocalVariable("inner", SassNumber(7.0))
    }
    assert(closure.getVariable("inner").isEmpty)
  }

  test("Environment.global returns a fresh environment with builtins and !global vars") {
    val env = Environment.withBuiltins()
    env.setVariable("local", SassNumber(1.0))
    env.setGlobalVariable("shared", SassNumber(7.0))
    val g = env.global()
    // Local (non-!global) variables are dropped.
    assert(g.getVariable("local").isEmpty)
    // !global variables survive.
    val shared = g.getVariable("shared")
    assert(shared.isDefined)
    assertEquals(shared.get.toCssString(), "7")
    // Builtins are present.
    assert(g.getFunction("rgb").isDefined || g.getFunction("if").isDefined)
  }

  test("Configuration.throwErrorForUnknownVariables throws for explicit unused values") {
    val fakeNode = ssg.sass.ast.AstNode.fake { () =>
      val file = new ssg.sass.util.SourceFile(Nullable.empty, "")
      val loc  = ssg.sass.util.FileLocation(file, 0, 0, 0)
      ssg.sass.util.FileSpan(file, loc, loc)
    }
    val cfg = ExplicitConfiguration(
      Map(
        "primary" -> ConfiguredValue.explicit(SassNumber(1.0)),
        "accent" -> ConfiguredValue.explicit(SassNumber(2.0))
      ),
      fakeNode
    )
    intercept[SassException](cfg.throwErrorForUnknownVariables())
  }

  test("Configuration.implicitConfig swallows unused values silently") {
    val cfg = Configuration.implicitConfig(
      Map("primary" -> ConfiguredValue.explicit(SassNumber(1.0)))
    )
    // Should NOT throw — implicit configs come from forwarded `with` clauses.
    cfg.throwErrorForUnknownVariables()
    assert(cfg.isImplicit)
  }

  test("Configuration.empty.throwErrorForUnknownVariables is a no-op") {
    Configuration.empty.throwErrorForUnknownVariables()
    assert(Configuration.empty.isEmpty)
  }

  test("UserDefinedCallable.name reads the declaration name through @function compile") {
    // End-to-end: a user-defined function should be invocable by its
    // declared name (which exercises UserDefinedCallable.name -> cd.name).
    val source = """
      @function double($x) { @return $x * 2; }
      .a { width: double(8px); }
    """
    val result = Compile.compileString(source)
    assert(result.css.contains("16px"), result.css)
  }

  test("Second law of extend: extension with sufficient specificity is emitted") {
    val source = """
      .foo.bar { color: red; }
      .baz { @extend .foo; }
    """
    val result = Compile.compileString(source)
    // The extender should appear — `.bar.baz` (or `.baz.bar`) has the same
    // specificity as `.foo.bar`, so it passes the second law filter.
    assert(result.css.contains(".baz"), result.css)
    assert(result.css.contains(".foo.bar") || result.css.contains(".bar.foo"), result.css)
  }
}
