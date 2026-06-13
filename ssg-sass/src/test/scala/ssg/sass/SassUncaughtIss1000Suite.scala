/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

/** Regression guard for ISS-1000 — adjudicated STALE.
  *
  * ISS-1000 described an *uncaught* crash on two deeply-nested-paren inputs
  * from the sass-spec `libsass-todo-issues` set. As of this branch those two
  * inputs no longer crash uncaught: each one now produces a proper
  * `ssg.sass.SassFormatException` (a subtype of `ssg.sass.SassException`) with
  * an "Expected ')'" parse error — i.e. a clean compile error, exactly what a
  * malformed stylesheet should yield.
  *
  * This file pins that GREEN behavior so the crash cannot silently return.
  * Both assertions pass TODAY. No source change accompanies this guard.
  *
  * Inputs (read verbatim from the vendored sass-spec .hrx fixtures):
  *
  *   - issue_221262
  *     (original-src/sass-spec/spec/libsass-todo-issues/issue_221262.hrx,
  *     `<===> input.scss`): the 665-char line `0{g:00;m:` followed by 655
  *     `(` characters followed by a final `0`. The first unbalanced `(`
  *     leaves the value expression expecting a closing paren, so the parser
  *     reports `Expected ')'.` at column 666 (the position just past the
  *     last `(`).
  *
  *   - issue_221292
  *     (original-src/sass-spec/spec/libsass-todo-issues/issue_221292.hrx,
  *     `<===> input.scss`): the 664-char line `/**/0{i:` followed by 656
  *     `(` characters (no trailing token). Same shape, reported at
  *     column 665.
  *
  * Both fixtures are tagged `:todo: - dart-sass` upstream. In the SSG
  * sass-spec baseline they are recorded with an `Error` outcome
  * (.rescale/data/sass-spec-baseline.tsv rows 11470 / 11474) — i.e. the
  * expected result is a compile error, which is what the two assertions
  * below verify the port now produces.
  */
final class SassUncaughtIss1000Suite extends munit.FunSuite {

  /** issue_221262 input.scss: `0{g:00;m:` + 655 * `(` + `0` (665 chars). */
  private val input221262: String =
    "0{g:00;m:" + ("(" * 655) + "0"

  /** issue_221292 input.scss: `/**/0{i:` + 656 * `(` (664 chars). */
  private val input221292: String =
    "/**/0{i:" + ("(" * 656)

  test("ISS-1000 (stale): issue_221262 throws SassFormatException, not an uncaught crash") {
    val ex = intercept[ssg.sass.SassException] {
      Compile.compileString(input221262, OutputStyle.Expanded)
    }
    assert(
      ex.isInstanceOf[ssg.sass.SassFormatException],
      s"expected a SassFormatException, got ${ex.getClass.getName}: ${ex.getMessage}"
    )
    assert(
      ex.getMessage.contains("Expected ')'"),
      s"expected an \"Expected ')'\" parse error, got: ${ex.getMessage}"
    )
    // The unbalanced run starts at the value expression for `m:`; the parser
    // reports the missing close paren at column 666 (1-based, just past the
    // 655th `(` which is the 665th input char).
    assert(
      ex.getMessage.contains(":1:666:"),
      s"expected the error at 1:666, got: ${ex.getMessage}"
    )
  }

  test("ISS-1000 (stale): issue_221292 throws SassFormatException, not an uncaught crash") {
    val ex = intercept[ssg.sass.SassException] {
      Compile.compileString(input221292, OutputStyle.Expanded)
    }
    assert(
      ex.isInstanceOf[ssg.sass.SassFormatException],
      s"expected a SassFormatException, got ${ex.getClass.getName}: ${ex.getMessage}"
    )
    assert(
      ex.getMessage.contains("Expected ')'"),
      s"expected an \"Expected ')'\" parse error, got: ${ex.getMessage}"
    )
    // 656 `(` after the 8-char prefix `/**/0{i:`; missing close paren is
    // reported at column 665 (just past the last `(`).
    assert(
      ex.getMessage.contains(":1:665:"),
      s"expected the error at 1:665, got: ${ex.getMessage}"
    )
  }
}
