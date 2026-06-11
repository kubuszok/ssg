/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1137: the Compressor deletes LIVE statements that follow a
 * dead-branch collapse. With explicit default CompressorOptions(),
 * `if (false) { sideEffect(); } done();` compresses to `!1;` — the live `done()`
 * call after the `if` is dropped entirely (a miscompilation).
 *
 * Upstream truth (vendored terser v5.46.1, original-src/terser): the constant-
 * condition `if` is handled by the AST_If optimizer in
 * original-src/terser/lib/compress/index.js, def_optimize(AST_If) (lines
 * 1094-1129). With `dead_code` on (a compress default) and a falsy evaluated
 * condition, lines 1110-1117 replace ONLY the `if` statement itself:
 *
 *     if (!cond) {
 *         var body = [];
 *         extract_from_unreachable_code(compressor, self.body, body);   // :1112
 *         body.push(make_node(AST_SimpleStatement, self.condition, {    // :1113
 *             body: self.condition
 *         }));
 *         if (self.alternative) body.push(self.alternative);            // :1116
 *         return make_node(AST_BlockStatement, self, { body: body })    // :1117
 *             .optimize(compressor);
 *     }
 *
 * i.e. the dead branch yields hoisted declarations plus a `false;` expression
 * statement (later removed by `side_effects`), and every sibling statement in
 * the enclosing block is untouched. The statements AFTER the `if` are live and
 * must survive.
 *
 * Verified against the vendored sources by executing terser v5.46.1
 * (original-src/terser/main.js) with { compress: {}, mangle: false }:
 *
 *     "if (false) { sideEffect(); } done();"        -> "done();"
 *     "if (false) { x(); } var keep = 1; use(keep);" -> "var keep=1;use(keep);"
 *     "if (true) { a(); } b();"                      -> "a(),b();"
 *
 * No vendored compress fixture pins this exact shape (the closest are
 * original-src/terser/test/compress/dead-code.js:342 `if (false) throw x;`
 * inside a try, and original-src/terser/test/compress/issue-1338.js, which
 * runs without the `conditionals` option), so the expectations above were
 * obtained from the live run and are asserted verbatim below.
 */
package ssg
package js

import ssg.js.compress.CompressorOptions

final class LiveCodeDropIss1137Suite extends munit.FunSuite {

  // Explicit CompressorOptions() (== compress defaults per ISS-1033); mangle off so
  // the output is byte-comparable to the upstream run documented in the header.
  private val opts = MinifyOptions(compress = CompressorOptions(), mangle = false)

  // RED 1 (the exact ISS-1137 fixture) — the red criterion: the live trailing call
  // must survive dead-branch elimination (compress/index.js:1110-1117 replaces only
  // the `if` node, never its siblings).
  test("live call after if(false) survives dead-branch collapse (ISS-1137 fixture)") {
    val out = Terser.minifyToString("if (false) { sideEffect(); } done();", opts)
    assert(
      out.contains("done()"),
      s"live `done()` after the collapsed dead branch was deleted; got: $out"
    )
  }

  // RED 1, pinned — terser v5.46.1 (vendored) compresses the fixture to exactly
  // "done();": dead branch fully removed, live call kept.
  test("if(false) fixture compresses to upstream's exact output: done();") {
    val out = Terser.minifyToString("if (false) { sideEffect(); } done();", opts)
    assertEquals(out, "done();", "expected upstream terser v5.46.1 output")
  }

  // RED 2 — variant with a declaration in the live tail: the `var` and its use
  // must both survive. Upstream (same run): "var keep=1;use(keep);".
  test("live var declaration and use after if(false) survive (ISS-1137 variant)") {
    val out = Terser.minifyToString("if (false) { x(); } var keep = 1; use(keep);", opts)
    assert(out.contains("use(keep)"), s"live tail after the dead branch was deleted; got: $out")
    assert(out.contains("keep=1"), s"live `var keep = 1` after the dead branch was deleted; got: $out")
  }

  // CONTROL — true-branch path (compress/index.js:1118-1127: the consequent is kept,
  // only the alternative would be extracted as unreachable). Both calls are live;
  // upstream emits "a(),b();". This pins that the truthy path keeps everything.
  test("control: if(true) keeps both the branch body and the trailing call") {
    val out = Terser.minifyToString("if (true) { a(); } b();", opts)
    assert(out.contains("a()"), s"live `a()` from the taken branch was deleted; got: $out")
    assert(out.contains("b()"), s"live trailing `b()` was deleted; got: $out")
  }
}
