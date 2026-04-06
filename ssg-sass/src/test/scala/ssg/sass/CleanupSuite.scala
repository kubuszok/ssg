/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
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

  test("Environment.closure snapshots variables — later mutations don't leak") {
    val env = Environment()
    env.setVariable("a", SassNumber(1.0))
    val closure = env.closure()
    // Mutate the original after capturing the closure.
    env.setVariable("a", SassNumber(99.0))
    env.setVariable("b", SassNumber(2.0))
    // Closure should still see the original `a` and not see `b` at all.
    val a = closure.getVariable("a")
    assert(a.isDefined)
    assertEquals(a.get.toCssString(), "1")
    assert(closure.getVariable("b").isEmpty)
  }

  test("Environment.closure also isolates functions") {
    val env = Environment()
    env.setFunction(Callable.function("orig", "$x", args => args.head))
    val closure = env.closure()
    env.setFunction(Callable.function("added-later", "$x", args => args.head))
    assert(closure.getFunction("orig").isDefined)
    assert(closure.getFunction("added-later").isEmpty)
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
    val cfg = Configuration(
      Map(
        "primary" -> ConfiguredValue.explicit(SassNumber(1.0)),
        "accent"  -> ConfiguredValue.explicit(SassNumber(2.0))
      )
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
