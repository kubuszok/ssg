/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.value.{ SassNumber, Value }
import ssg.sass.visitor.OutputStyle

/** Exercises the scope chain, closures, `!global`, and semi-global tracking ported into [[Environment]] from dart-sass.
  */
final class EnvironmentScopeSuite extends munit.FunSuite {

  private def num(n: Double): Value = SassNumber(n)

  // --- Direct Environment API ------------------------------------------------

  test("inner scope shadows outer") {
    val env = new Environment()
    env.setVariable("x", num(1))
    env.withinScope(semiGlobal = false) {
      env.setVariable("x", num(2))
      assertEquals(env.getVariable("x").get, num(2))
    }
    assertEquals(env.getVariable("x").get, num(1))
  }

  test("semi-global scope propagates assignment to outer") {
    val env = new Environment()
    env.setVariable("x", num(1))
    env.withinSemiGlobalScope {
      env.setVariable("x", num(2))
    }
    assertEquals(env.getVariable("x").get, num(2))
  }

  test("non-semi-global scope does not leak new global binding") {
    val env = new Environment()
    env.setVariable("x", num(1))
    env.withinScope(semiGlobal = false) {
      env.setVariable("x", num(2))
    }
    assertEquals(env.getVariable("x").get, num(1))
  }

  test("!global writes global from nested scope") {
    val env = new Environment()
    env.withinScope(semiGlobal = false) {
      env.withinScope(semiGlobal = false) {
        env.setGlobalVariable("y", num(42))
      }
    }
    assertEquals(env.getVariable("y").get, num(42))
    assert(env.globalVariableExists("y"))
  }

  test("closure captures current scope chain") {
    val env = new Environment()
    env.setVariable("a", num(1))
    env.withinScope(semiGlobal = false) {
      env.setVariable("b", num(2))
      val snap = env.closure()
      // Inside the closure we can see both a and b.
      assertEquals(snap.getVariable("a").get, num(1))
      assertEquals(snap.getVariable("b").get, num(2))
    }
    // After leaving the scope, a fresh lookup in env no longer sees b,
    // but the closure does.
    assertEquals(env.getVariable("b"), Nullable.empty)
  }

  test("setLocalVariable shadows outer binding") {
    val env = new Environment()
    env.setVariable("x", num(1))
    env.withinScope(semiGlobal = true) {
      env.setLocalVariable("x", num(9))
      assertEquals(env.getVariable("x").get, num(9))
    }
    assertEquals(env.getVariable("x").get, num(1))
  }

  // --- Through the evaluator -------------------------------------------------

  test("@if body updates outer variable (semi-global)") {
    val css = Compile
      .compileString(
        """|$x: 1;
           |@if true { $x: 2; }
           |a { b: $x; }
           |""".stripMargin,
        OutputStyle.Compressed
      )
      .css
    assertEquals(css, "a{b:2}")
  }

  test("@each body updates outer variable (semi-global)") {
    // Keep this simple: just verify that writes inside @each propagate
    // out. We don't depend on $i arithmetic because iterator binding
    // has a separate pre-existing issue in EachRule that's out of
    // scope for this port.
    val css = Compile
      .compileString(
        """|$last: 0;
           |@each $i in 1, 2, 3 { $last: 7; }
           |a { b: $last; }
           |""".stripMargin,
        OutputStyle.Compressed
      )
      .css
    assertEquals(css, "a{b:7}")
  }

  test("@for body updates outer variable (semi-global)") {
    val css = Compile
      .compileString(
        """|$n: 0;
           |@for $i from 1 through 3 { $n: $n + 1; }
           |a { b: $n; }
           |""".stripMargin,
        OutputStyle.Compressed
      )
      .css
    assertEquals(css, "a{b:3}")
  }

  test("!global from nested scope writes outer") {
    val css = Compile
      .compileString(
        """|$x: 1;
           |@if true {
           |  @if true { $x: 9 !global; }
           |}
           |a { b: $x; }
           |""".stripMargin,
        OutputStyle.Compressed
      )
      .css
    assertEquals(css, "a{b:9}")
  }
}
