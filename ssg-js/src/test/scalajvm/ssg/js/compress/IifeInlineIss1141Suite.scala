/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red tests for ISS-1141: IIFE-inline gap in ssg/js/compress/Inline.scala.
 *
 * Upstream terser inlines `(function(a) { if (true) return; var b = 42; })(this);`
 * to empty output — the IIFE body reduces to nothing, so the whole call is
 * inlined away (terser lib/compress/inline.js, IIFE inline path). The port
 * never inlines the IIFE, leaving the call in the output.
 *
 * Fixtures (vendored, original-src/terser v5.46.1):
 * - original-src/terser/test/compress/functions.js:84 `issue_485_crashing_1530`
 *   options: conditionals, dead_code, evaluate, inline, side_effects
 * - original-src/terser/test/compress/arrow.js:471 `issue_485_crashing_1530`
 *   options: arrows, conditionals, dead_code, ecma: 2015, evaluate, inline,
 *   side_effects
 * Both: input `(function(a) { if (true) return; var b = 42; })(this);`,
 * expect `{}` (i.e. empty output).
 *
 * Executed oracle (terser 5.46.1 per original-src/terser/package.json:7,
 * node v24.12.0, run on 2026-06-12):
 *
 *   cd original-src/terser && node --input-type=module -e "
 *     import { minify } from './main.js';
 *     const code = '(function(a) { if (true) return; var b = 42; })(this);';
 *     const r = await minify(code, { compress: { defaults: false,
 *       conditionals: true, dead_code: true, evaluate: true, inline: true,
 *       side_effects: true }, mangle: false });
 *     console.log(JSON.stringify(r.code));"
 *
 *   functions.js option set                    -> ""
 *   arrow.js option set (+arrows, ecma: 2015)  -> ""
 *   control, { defaults: false } (all off)     -> "(function(a){if(true)return;var b=42})(this);"
 *
 * Related pins (do not touch; removed once the fix lands):
 * - ssg-js/src/test/scalajvm/ssg/js/compress/CompressFunctionsSuite.scala:95
 * - ssg-js/src/test/scalajvm/ssg/js/compress/CompressArrowSuite.scala:477
 */
package ssg
package js
package compress

import CompressTestHelper.compressAndNormalize
import CompressTestHelper.AllOff

final class IifeInlineIss1141Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val FixtureInput = """(function(a) {
            if (true) return;
            var b = 42;
        })(this)""".stripMargin.trim

  private def assertCompressesTo(
    input:    String,
    expected: String,
    options:  CompressorOptions,
    message:  String
  )(using loc: munit.Location): Unit =
    compressAndNormalize(input, expected, options) match {
      case Some((actual, exp)) => assertEquals(actual, exp, message)
      case None                => fail(s"compression timed out — $message")
    }

  // =========================================================================
  // RED 1: issue_485_crashing_1530, function form
  // (original-src/terser/test/compress/functions.js:84)
  // =========================================================================
  test("issue_485_crashing_1530 function form inlines IIFE to empty output (functions.js:84)") {
    assertCompressesTo(
      input = FixtureInput,
      expected = "",
      options = AllOff.copy(
        conditionals = true,
        deadCode = true,
        evaluate = true,
        inline = InlineLevel.InlineFull,
        sideEffects = true
      ),
      message = "ISS-1141: terser inlines the IIFE to empty output " +
        "(fixture original-src/terser/test/compress/functions.js:84, executed oracle 2026-06-12: \"\"); " +
        "the port leaves the IIFE call un-inlined"
    )
  }

  // =========================================================================
  // RED 2: issue_485_crashing_1530, arrow option set
  // (original-src/terser/test/compress/arrow.js:471)
  // =========================================================================
  test("issue_485_crashing_1530 arrow option set inlines IIFE to empty output (arrow.js:471)") {
    assertCompressesTo(
      input = FixtureInput,
      expected = "",
      options = AllOff.copy(
        arrows = true,
        conditionals = true,
        deadCode = true,
        ecma = 2015,
        evaluate = true,
        inline = InlineLevel.InlineFull,
        sideEffects = true
      ),
      message = "ISS-1141: terser inlines the IIFE to empty output " +
        "(fixture original-src/terser/test/compress/arrow.js:471, executed oracle 2026-06-12: \"\"); " +
        "the port leaves the IIFE call un-inlined"
    )
  }

  // =========================================================================
  // CONTROL: with inlining disabled (all compress flags off) the IIFE must
  // survive verbatim. Executed oracle 2026-06-12 with { defaults: false }:
  // "(function(a){if(true)return;var b=42})(this);"
  // =========================================================================
  test("control: with all compress flags off the IIFE survives verbatim") {
    assertCompressesTo(
      input = FixtureInput,
      expected = "(function(a){if(true)return;var b=42})(this);",
      options = AllOff,
      message = "control: terser with all compress flags off keeps the IIFE verbatim " +
        "(executed oracle 2026-06-12)"
    )
  }
}
