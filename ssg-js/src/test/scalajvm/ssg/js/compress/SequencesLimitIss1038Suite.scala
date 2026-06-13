/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1038: the numeric `sequences` limit is dead in
 * ssg/js/compress. CompressorOptions.get("sequences") returns
 * `sequencesLimit > 0` (compress/CompressorOptions.scala:278), collapsing the
 * Int to a Boolean, so the join-limit match in compress/TightenBody.scala:104-110
 * never sees the number and the documented per-sequence max-count
 * (CompressorOptions.scala:190-191, sequencesLimit default 200) is ignored.
 *
 * terser oracle (lib/compress/index.js:326-327):
 *   var sequences = this.options["sequences"];
 *   this.sequences_limit = sequences == 1 ? 800 : sequences | 0;
 * A numeric limit N caps each joined comma-sequence at N expressions, so a
 * SMALL N yields MULTIPLE shorter sequences instead of one big one.
 *
 * Executed oracle (terser 5.46.1 per original-src/terser/package.json,
 * node v24.12.0, run on 2026-06-14), false_by_default to match AllOff:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *     import { minify } from './main.js';
 *     for (const n of [2, 200]) {
 *       const r = await minify('function f(){a();b();c();d();e();f();}',
 *         { compress: { defaults: false, sequences: n }, mangle: false });
 *       console.log('seq' + n + ':', JSON.stringify(r.code)); }"
 *
 *   seq2:   "function f(){a(),b();c(),d();e(),f()}"   (three 2-expr sequences)
 *   seq200: "function f(){a(),b(),c(),d(),e(),f()}"   (one 6-expr sequence)
 *
 * The control (limit 200 -> one big sequence) passes today, proving sequencing
 * happens. The RED assertion (limit 2 -> three capped sequences) fails today:
 * the port collapses the limit to a Boolean, always behaving as 200, so the
 * limit-2 output is the single unlimited sequence. */
package ssg
package js
package compress

import CompressTestHelper.compressAndNormalize
import CompressTestHelper.AllOff

final class SequencesLimitIss1038Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // Six consecutive simple expression statements terser joins with the comma
  // operator. With a numeric limit N each sequence is capped at N expressions.
  private val FixtureInput = "function f(){a();b();c();d();e();f();}"

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
  // CONTROL (passes today): the default-style limit 200 joins all six
  // statements into ONE comma-sequence. Executed oracle 2026-06-14:
  // "function f(){a(),b(),c(),d(),e(),f()}"
  // =========================================================================
  test("Iss1038 control: limit 200 joins all six statements into one sequence") {
    assertCompressesTo(
      input = FixtureInput,
      expected = "function f(){a(),b(),c(),d(),e(),f()}",
      options = AllOff.copy(
        sequencesLimit = 200
      ),
      message = "control: terser with sequences:200 joins all six statements " +
        "into one comma-sequence (executed oracle 2026-06-14)"
    )
  }

  // =========================================================================
  // RED (must FAIL today): a SMALL limit of 2 caps each comma-sequence at 2
  // expressions, producing THREE shorter sequences. Executed oracle
  // 2026-06-14: "function f(){a(),b();c(),d();e(),f()}". The port collapses the
  // numeric limit to a Boolean (CompressorOptions.scala:278), so it always
  // behaves as 200 and emits the single unlimited sequence
  // "function f(){a(),b(),c(),d(),e(),f()}" instead.
  // =========================================================================
  test("Iss1038 limit 2 caps each sequence at two expressions (three sequences)") {
    assertCompressesTo(
      input = FixtureInput,
      expected = "function f(){a(),b();c(),d();e(),f()}",
      options = AllOff.copy(
        sequencesLimit = 2
      ),
      message = "ISS-1038: terser with sequences:2 caps each comma-sequence at " +
        "2 expressions -> \"function f(){a(),b();c(),d();e(),f()}\" (three " +
        "sequences, executed oracle 2026-06-14); the port collapses the numeric " +
        "limit to a Boolean so it always behaves as 200 and emits one unlimited " +
        "sequence \"function f(){a(),b(),c(),d(),e(),f()}\""
    )
  }
}
