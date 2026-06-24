/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Regression tests for ISS-1312: isCallPure called the type-dispatch
 * (mayThrowOnAccess) instead of the wrapper (dotThrow), so with
 * pure_getters=false SSG unsoundly dropped may-throwing calls like
 * ({p:1}).valueOf() that terser keeps.
 *
 * Oracle: terser v5.x via `node original-src/terser/dist/bundle.min.js`
 * (API: terser.minify with defaults=false). */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressIsCallPureMayThrowIss1312Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // =========================================================================
  // Case A: pure_getters=false — terser KEEPS the valueOf() call because
  // may_throw_on_access (wrapper) returns true when pure_getters is off.
  // Oracle: "function f(){({p:1}).valueOf();return 1}"
  // =========================================================================
  test("isCallPure_keeps_valueOf_when_pureGetters_false") {
    assertCompresses(
      input = "function f(){ ({p:1}).valueOf(); return 1; }",
      expected = "function f(){({p:1}).valueOf();return 1}",
      options = AllOff.copy(
        sideEffects = true,
        unsafe = true,
        pureGetters = false
      )
    )
  }

  // =========================================================================
  // Case B: pure_getters=true — terser DROPS the valueOf() call because
  // may_throw_on_access (wrapper) delegates to _dot_throw, and AstObject
  // in non-strict mode returns false => isCallPure sees "Object" => pure.
  // Oracle: "function f(){return 1}"
  // =========================================================================
  test("isCallPure_drops_valueOf_when_pureGetters_true") {
    assertCompresses(
      input = "function f(){ ({p:1}).valueOf(); return 1; }",
      expected = "function f(){return 1}",
      options = AllOff.copy(
        sideEffects = true,
        unsafe = true,
        pureGetters = true
      )
    )
  }
}
