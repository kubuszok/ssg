/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1184: sequences compress option default + true-mapping must match terser.
 * Reference: terser/lib/compress/index.js:326-327
 *
 *   var sequences = this.options["sequences"];
 *   this.sequences_limit = sequences == 1 ? 800 : sequences | 0;
 *
 * In JS `true == 1`, so the DEFAULT (`sequences: true`) AND `sequences: 1`
 * both resolve to 800. SSG's CompressorOptions default is therefore expected
 * to be 800, not 200. A numeric `sequences` value (ISS-1038) is honored
 * unchanged. */
package ssg
package js
package compress

final class SequencesDefaultLimitIss1184Suite extends munit.FunSuite {

  test("ISS-1184: default sequencesLimit is 800 (terser sequences:true -> 800, index.js:327)") {
    assertEquals(CompressorOptions().sequencesLimit, 800)
  }

  test("ISS-1184: numeric sequences value is honored unchanged (ISS-1038)") {
    assertEquals(CompressorOptions(sequencesLimit = 50).sequencesLimit, 50)
  }
}
