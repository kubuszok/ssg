/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1305: Compressor.safeToFlatten is STRICTER than terser for a bare class
 * literal.
 *
 * Terser's safe_to_flatten (lib/compress/index.js:3516-3524) returns `true` for
 * ANY AST_Class. Walk the guard for a Class value:
 *   3521: `!(value instanceof AST_Lambda || value instanceof AST_Class)` is
 *         FALSE for a Class (it IS an AST_Class), so this early-return is
 *         skipped.
 *   3522: `!(value instanceof AST_Lambda && value.contains_this())` — a Class is
 *         NOT an AST_Lambda, so the `&&` is false and the outer `!` is TRUE, so
 *         terser returns `true` here regardless of the parent node.
 * SSG's safeToFlatten (Compressor.scala:5473-5478) instead has
 * `case _: AstClass => par instanceof AstNew`, requiring the parent to be `new`,
 * so SSG will NOT flatten a class literal where terser WOULD.
 *
 * Observable gate: array-index flattening (Compressor.scala:3993-4001, mirroring
 * terser index.js) rewrites `[a,b,c][i]` to the indexed element when
 * `safeToFlatten(retValue)` holds. With `properties` + `side_effects` enabled,
 * terser turns `x = [class{}][0]` into `x = class{}` (the class is this-free, so
 * safe_to_flatten returns true); SSG keeps the un-flattened `x = [class{}][0]`
 * because its parent is not `new`.
 *
 * Expected values derived from the terser oracle:
 *   node -e 'require("original-src/terser/main.js").minify(<input>,
 *            {compress:{defaults:false,properties:true,side_effects:true},
 *             mangle:false})'
 *   var x; x=[class{}][0];        -> var x;x=class{};
 *   var x; x=[class C{m(){}}][0]; -> var x;x=class C{m(){}};
 *
 * Runs on JVM, JS, and Native (no java.nio dependency). */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressSafeToFlattenClassIss1305Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val flattenOn = AllOff.copy(properties = true, sideEffects = true)

  // =========================================================================
  // array_index_flatten_bare_class
  // =========================================================================
  // `[class{}][0]` -> `class{}`. The class is this-free, so terser's
  // safe_to_flatten (index.js:3521-3522, Class -> true) permits the array-index
  // flatten. SSG without the fix keeps `[class{}][0]`.
  test("array_index_flatten_bare_class") {
    assertCompresses(
      input = "var x; x=[class{}][0];",
      expected = "var x;x=class{};",
      options = flattenOn
    )
  }

  // =========================================================================
  // array_index_flatten_named_class_with_body
  // =========================================================================
  // Same divergence with a named, non-empty class body.
  test("array_index_flatten_named_class_with_body") {
    assertCompresses(
      input = "var x; x=[class C{m(){}}][0];",
      expected = "var x;x=class C{m(){}};",
      options = flattenOn
    )
  }
}
