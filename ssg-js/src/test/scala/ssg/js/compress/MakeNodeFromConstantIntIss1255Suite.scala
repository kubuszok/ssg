/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1255: Common.makeNodeFromConstant must handle Int/Long numeric constants,
 * not just Double.
 *
 * Oracle: terser lib/compress/common.js make_node_from_constant (line 128). Its
 * `switch (typeof val)` has a single `case "number":` that handles ALL JS
 * numbers uniformly -- in JS every number is an IEEE-754 double, so there is no
 * separate integer type. A constant of 42 and 42.0 take the identical path and
 * both yield an AST_Number with that value.
 *
 * The SSG port (ssg-js/src/main/scala/ssg/js/compress/Common.scala:115) instead
 * pattern-matches `case d: Double` and has NO case for Scala's `Int`/`Long`.
 * A boxed `java.lang.Integer`/`java.lang.Long` therefore falls through to the
 * final `case _ => throw new IllegalArgumentException(...)`.
 *
 * This surfaces through the public Terser.minify API: a globalDefs map whose
 * value is a Scala Int (the natural literal `42`) flows -- via GlobalDefs.toNode
 * (GlobalDefs.scala:110) -- straight into makeNodeFromConstant, which throws.
 * The same map with `42.0` (a Double) works. JS makes no such distinction, so
 * the Int path must succeed identically to the Double path.
 *
 * Runs on JVM, JS, and Native.
 */
package ssg
package js
package compress

import ssg.js.ast.AstNumber

final class MakeNodeFromConstantIntIss1255Suite extends munit.FunSuite {

  // -----------------------------------------------------------------------
  // End-to-end via the public Terser.minify API (faithful to how ISS-1255
  // surfaced in the ISS-1179 audit).
  // -----------------------------------------------------------------------

  // RED: an Int globalDefs value (the natural Scala literal `42`) must be
  // substituted for X, matching terser `make_node_from_constant`'s uniform
  // numeric handling. Currently throws IllegalArgumentException because
  // makeNodeFromConstant has no Int case.
  test("Terser.minify substitutes an Int globalDefs value (ISS-1255)") {
    val out = Terser.minifyToString(
      "alert(X);",
      MinifyOptions(
        compress = CompressorOptions(globalDefs = Map("X" -> 42)),
        mangle = false
      )
    )
    assertEquals(out, "alert(42);")
  }

  // CONTROL: the equivalent Double globalDefs value already works -- this
  // confirms the harness (globalDefs substitution path) is wired correctly,
  // so the Int failure above is specifically the missing numeric case.
  test("Terser.minify substitutes a Double globalDefs value (control)") {
    val out = Terser.minifyToString(
      "alert(X);",
      MinifyOptions(
        compress = CompressorOptions(globalDefs = Map("X" -> 42.0)),
        mangle = false
      )
    )
    assertEquals(out, "alert(42);")
  }

  // -----------------------------------------------------------------------
  // Direct unit assertions on Common.makeNodeFromConstant -- pin the fix
  // tightly. `orig` only needs to supply start/end positions; any AstNode
  // works, so a fresh AstNumber suffices as the carrier.
  // -----------------------------------------------------------------------

  // RED: an Int 42 must produce an AstNumber whose value is 42.0, exactly as
  // the Double path does (terser common.js:133 `case "number"`). Currently
  // throws IllegalArgumentException("Can't handle constant of type:
  // java.lang.Integer").
  test("makeNodeFromConstant handles Int 42 as AstNumber 42.0 (ISS-1255)") {
    val orig = new AstNumber
    val node = Common.makeNodeFromConstant(42, orig)
    node match {
      case n: AstNumber => assertEquals(n.value, 42.0)
      case other => fail(s"expected AstNumber for Int 42, got ${other.nodeType}")
    }
  }

  // RED: a Long 42 must likewise produce an AstNumber 42.0. JS has no separate
  // integer/long type; terser routes every number through `case "number"`.
  test("makeNodeFromConstant handles Long 42 as AstNumber 42.0 (ISS-1255)") {
    val orig = new AstNumber
    val node = Common.makeNodeFromConstant(42L, orig)
    node match {
      case n: AstNumber => assertEquals(n.value, 42.0)
      case other => fail(s"expected AstNumber for Long 42, got ${other.nodeType}")
    }
  }
}
