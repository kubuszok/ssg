/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1033: `MinifyOptions(compress = true)` silently disables compression,
 * and `mangle = true` likewise disables mangling — Terser.scala:76-92 matches any Boolean
 * as "disabled" (`case _: Boolean =>`), the exact opposite of upstream Terser.
 *
 * Upstream semantics (original-src/terser, vendored):
 *   - lib/utils/index.js:66-71 — `defaults(args, defs)`: `if (args === true) { args = {}; }`,
 *     i.e. Boolean `true` means "use the default options object".
 *   - lib/minify.js:116-117,123 — top-level option normalization defaults `compress: {}` and
 *     `mangle: {}`, so an omitted option also means defaults-on.
 *   - lib/minify.js:161-174 — truthy `options.mangle` (including `true`) is merged with the
 *     default mangler options via `defaults(options.mangle, {...}, true)`.
 *   - lib/minify.js:262-266 — `if (options.compress)` runs the Compressor (whose constructor
 *     normalizes `true` to defaults via the same `defaults()` helper).
 *   - lib/minify.js:270-276 — `if (options.mangle)` runs `figure_out_scope`,
 *     `compute_char_frequency`, and `mangle_names`.
 *   So the trichotomy is: `true` → default options, `false` → disabled, explicit options
 *   object → those options.
 *
 * Notes for the implementer (observed while building fixtures, separate from ISS-1033):
 *   - SSG's default-when-omitted (MinifyOptions fields default to `CompressorOptions()` /
 *     `ManglerOptions()`, Terser.scala:37-42) already matches upstream's defaults-on
 *     behavior (minify.js:117,123); only the Boolean `true` arms diverge.
 *   - The mangle assertions below are AST-level (scope analysis ran; `mangledName` assigned)
 *     rather than output-level, because even with explicit `ManglerOptions()` the assigned
 *     `mangledName` never reaches the printed output (`function f(longName){return longName}`
 *     is emitted although the param's SymbolDef carries `mangledName = "a"`) — the symbol
 *     nodes printed by OutputStream.getSymbolName do not see the mangled definition. That is
 *     a pre-existing defect in the mangle output path, not in the option wiring under test.
 *   - The dead-branch fixture `if (false) { sideEffect(); } done();` was rejected: explicit
 *     `CompressorOptions()` collapses it to `!1;`, dropping the live `done()` call (broken
 *     compression passes, see ISS-1035/ISS-1036). Constant folding is used instead because
 *     it works correctly with explicit options, so the only variable here is the Boolean arm.
 */
package ssg
package js

import ssg.js.ast.AstDefun
import ssg.js.compress.CompressorOptions
import ssg.js.scope.{ ManglerOptions, SymbolDef }

final class TerserBooleanOptionsIss1033Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // Compression fixture exercising a pass known to work today (constant folding via
  // `evaluate`, on by default), so the only variable under test is the Boolean option arm.
  private val foldFixture = "var x = 1 + 2;"

  // Mangling fixture: a local parameter that the default mangler must process.
  private val mangleFixture = "function f(longName) { return longName; }"

  /** The `mangledName` assigned to `longName` inside `function f`, or null if none. */
  private def paramMangledName(result: MinifyResult): String | Null =
    result.ast.body.head match {
      case defun: AstDefun =>
        defun.variables.get("longName") match {
          case Some(d) => d.asInstanceOf[SymbolDef].mangledName
          case None    => null
        }
      case other => fail(s"Expected AstDefun, got: $other")
    }

  // -- Controls: explicit options objects (pass today; pin the baseline) --

  test("control A: explicit CompressorOptions() folds constants") {
    val out = Terser.minifyToString(foldFixture, MinifyOptions(compress = CompressorOptions(), mangle = false))
    assertEquals(out, "var x=3;", "explicit CompressorOptions() must fold 1+2 to 3")
  }

  test("control B: explicit ManglerOptions() runs the mangle phase") {
    val result = Terser.minify(mangleFixture, MinifyOptions(compress = false, mangle = ManglerOptions()))
    assert(result.ast.variables.contains("f"), "scope analysis must have run (toplevel variables populated)")
    assert(paramMangledName(result) != null, "the mangler must have assigned a mangled name to longName")
  }

  // -- Red: Boolean `true` must mean default options, exactly like upstream --
  // utils/index.js:66-68 (`args === true` → `{}`) + minify.js:262-266 / 270-276.

  test("red ISS-1033: compress = true behaves like explicit CompressorOptions()") {
    val explicit = Terser.minifyToString(foldFixture, MinifyOptions(compress = CompressorOptions(), mangle = false))
    val viaTrue  = Terser.minifyToString(foldFixture, MinifyOptions(compress = true, mangle = false))
    assertEquals(viaTrue, explicit, "compress = true must produce the same output as explicit default CompressorOptions()")
    assert(viaTrue.contains("3"), s"compress = true must actually compress (fold 1+2 to 3), got: $viaTrue")
  }

  test("red ISS-1033: mangle = true behaves like explicit ManglerOptions()") {
    val explicit = Terser.minify(mangleFixture, MinifyOptions(compress = false, mangle = ManglerOptions()))
    val viaTrue  = Terser.minify(mangleFixture, MinifyOptions(compress = false, mangle = true))
    assertEquals(viaTrue.code, explicit.code, "mangle = true must produce the same output as explicit default ManglerOptions()")
    assert(viaTrue.ast.variables.contains("f"), "mangle = true must run scope analysis like explicit ManglerOptions()")
    assertEquals(
      Option(paramMangledName(viaTrue)),
      Option(paramMangledName(explicit)),
      "mangle = true must assign the same mangled name as explicit default ManglerOptions()"
    )
    assert(paramMangledName(viaTrue) != null, "mangle = true must actually mangle (assign a mangled name to longName)")
  }

  // -- Controls: Boolean `false` must keep the phase disabled (minify.js:262,270) --

  test("control: compress = false leaves code uncompressed") {
    val out = Terser.minifyToString(foldFixture, MinifyOptions(compress = false, mangle = false))
    assert(out.contains("1+2"), s"compress = false must not fold constants, got: $out")
  }

  test("control: mangle = false keeps names and skips the mangle phase") {
    val result = Terser.minify(mangleFixture, MinifyOptions(compress = false, mangle = false))
    assert(result.code.contains("longName"), s"mangle = false must keep longName, got: ${result.code}")
    assert(result.ast.variables.isEmpty, "mangle = false must not run the mangle phase's scope analysis")
  }
}
