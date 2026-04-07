/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.value.{ SassNumber, SassString, Value }

/** Exercises the [[EnvironmentModule]] adapter wrapping an [[Environment]]
  * as a [[Module]] `[Callable]`.
  */
final class EnvironmentModuleSuite extends munit.FunSuite {

  private def num(n: Double): Value = SassNumber(n)

  test("adapter exposes wrapped environment variables") {
    val env = new Environment()
    env.setVariable("a", num(1))
    env.setVariable("b", new SassString("hello", hasQuotes = true))
    val mod = EnvironmentModule(env, url = Nullable("custom:test"))
    assertEquals(mod.url.get, "custom:test")
    assertEquals(mod.variables("a"), num(1))
    assertEquals(mod.variables("b").asInstanceOf[SassString].text, "hello")
    assertEquals(mod.variableIdentities, Set("a", "b"))
  }

  test("adapter exposes functions and mixins") {
    val env = new Environment()
    val fn  = BuiltInCallable.function("f", "$x", args => args.head)
    val mx  = BuiltInCallable.mixin("m", "", _ => num(0))
    env.setFunction(fn)
    env.setMixin(mx)
    val mod = EnvironmentModule(env)
    assertEquals(mod.functions("f"), fn: Callable)
    assertEquals(mod.mixins("m"), mx: Callable)
    assert(mod.functionIdentities.contains("f"))
    assert(mod.mixinIdentities.contains("m"))
  }

  test("setVariable through adapter updates the underlying environment") {
    val env = new Environment()
    env.setVariable("x", num(1))
    val mod = EnvironmentModule(env)
    mod.setVariable("x", num(42))
    assertEquals(env.getVariable("x").get, num(42))
    assertEquals(mod.variables("x"), num(42))
  }

  test("setVariable throws for undefined name") {
    val env = new Environment()
    val mod = EnvironmentModule(env)
    intercept[IllegalArgumentException] {
      mod.setVariable("missing", num(1))
    }
  }

  test("empty module has a fresh built-in environment") {
    val mod = EnvironmentModule.empty(url = Nullable("sass:custom"))
    assertEquals(mod.url.get, "sass:custom")
    // Built-ins are seeded, so some functions should exist.
    assert(mod.functions.nonEmpty)
    assertEquals(mod.variables, Map.empty[String, Value])
    assertEquals(mod.transitivelyContainsCss, false)
    assertEquals(mod.transitivelyContainsExtensions, false)
  }
}
