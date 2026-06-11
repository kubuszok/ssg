/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1035: DropUnused Pass 3 runs via `walk` instead of
 * `transform` (DropUnused.scala:128-131, behind a false "transform is not yet
 * implemented" comment — AstNode.transform exists at AstNode.scala:64 and
 * AstScope.transformDescend at AstScope.scala:82). Because TreeWalker.walk
 * discards visitor return values, every replacement node produced by
 * Pass3Transformer.beforeTransform (DropUnused.scala:~479-515) is thrown away,
 * and the SkipMarker/SpliceMarker sentinels (~745/750) are never consumed.
 * Net effect: unused-assignment elision, unused defun removal and unused
 * class-declaration removal are all no-ops.
 *
 * Upstream truth (vendored terser v5.46.1, original-src/terser): pass 3 is a
 * TreeTransformer applied with `self.transform(tt)` at
 * lib/compress/drop-unused.js:464. The behaviors pinned below map to:
 *   - unused assignment -> `make_node(AST_Number, node, { value: 0 })`
 *     (lib/compress/drop-unused.js:234-244, returns at :238/:243);
 *   - unused AST_DefClass -> dropped via `drop_side_effect_free` /
 *     AST_EmptyStatement (lib/compress/drop-unused.js:290-302, return at :298);
 *   - unused AST_Defun -> AST_EmptyStatement
 *     (lib/compress/drop-unused.js:304-310, return at :309).
 *
 * Fixture provenance (original-src/terser/test/compress/drop-unused.js) — the
 * cases below are MINIMAL variants of the upstream fixtures, deliberately not
 * verbatim copies of the 109 fail-pinned cases in CompressDropUnusedSuite:
 *   - case 1: `drop_assign` f1 (test/compress/drop-unused.js:286-331; input f1
 *     at :291-294, expect `function f1() { 0; }` at :313-315);
 *   - case 2: `drop_toplevel_funcs` (test/compress/drop-unused.js:386-410 —
 *     unused toplevel defun dropped when toplevel:"funcs");
 *   - case 3: `unused_block_decls_in_catch` (test/compress/drop-unused.js:
 *     187-207 — unused `class Zee {};` declaration dropped);
 *   - case 4 (control): used nodes survive, distilled from the kept half of
 *     `drop_toplevel_funcs` (`function g() {}` survives because it is used).
 *
 * Expected outputs verified by executing terser v5.46.1
 * (original-src/terser/main.js) with { compress: { defaults: false, ... },
 * mangle: false }:
 *   "function f1() { var a; a = 1; }"                    -> "function f1(){0}"
 *   "function f() {} function g() {} g();"
 *       (toplevel:"funcs")                               -> "function g(){}g();"
 *   "function foo() { class Zee {} return 1; } foo();"   -> "function foo(){return 1}foo();"
 *   "function f() { var a = 1; return a; }"              -> "function f(){var a=1;return a}"
 */
package ssg
package js

import ssg.js.compress.CompressTestHelper.{ AllOff, assertCompresses }
import ssg.js.compress.ToplevelConfig

final class CompressDropUnusedIss1035Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // RED 1 — unused assignment elided to `0`. Upstream: drop_assign f1
  // (test/compress/drop-unused.js:291-294 -> :313-315); replacement produced at
  // lib/compress/drop-unused.js:238/:243 but discarded by the walk-based Pass 3.
  test("ISS-1035 red 1: unused assignment is elided to 0 (drop_assign f1)") {
    assertCompresses(
      input = "function f1() { var a; a = 1; }",
      expected = "function f1() { 0; }",
      options = AllOff.copy(
        unused = true
      )
    )
  }

  // RED 2 — unused toplevel defun removed with toplevel:"funcs". Upstream:
  // drop_toplevel_funcs (test/compress/drop-unused.js:386-410); replacement
  // (AST_EmptyStatement / MAP.skip) produced at lib/compress/drop-unused.js:309
  // but discarded by the walk-based Pass 3.
  test("ISS-1035 red 2: unused toplevel defun removed with toplevel funcs") {
    assertCompresses(
      input = "function f() {} function g() {} g();",
      expected = "function g() {} g();",
      options = AllOff.copy(
        toplevel = ToplevelConfig(funcs = true, vars = false),
        unused = true
      )
    )
  }

  // RED 3 — unused class declaration removed. Upstream: unused_block_decls_in_catch
  // (test/compress/drop-unused.js:187-207, `class Zee {};` dropped); replacement
  // produced at lib/compress/drop-unused.js:298 but discarded by the walk-based
  // Pass 3.
  test("ISS-1035 red 3: unused class declaration removed") {
    assertCompresses(
      input = "function foo() { class Zee {} return 1; } foo();",
      expected = "function foo() { return 1; } foo();",
      options = AllOff.copy(
        unused = true
      )
    )
  }

  // CONTROL — used nodes survive (must pass today and after the fix): `a` is
  // read by the return, so nothing may be dropped. Distilled from the kept
  // `function g() {}` half of drop_toplevel_funcs (test/compress/drop-unused.js:
  // 403-408) and lib/compress/drop-unused.js:305-307 (`keep` when in_use).
  test("ISS-1035 control: used var declaration and reference survive") {
    assertCompresses(
      input = "function f() { var a = 1; return a; }",
      expected = "function f() { var a = 1; return a; }",
      options = AllOff.copy(
        unused = true
      )
    )
  }
}
