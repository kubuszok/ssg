/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Regression test for ISS-1035 (bounce 1): the `in_list` flag that Pass 3 of
 * drop_unused passes to its `before`/`after` callbacks must be threaded
 * explicitly through the transform machinery, exactly as upstream does.
 *
 * Upstream truth (vendored terser v5.46.1, original-src/terser):
 *   - `transform(tw, in_list)` forwards `in_list` to `before(this, descend,
 *     in_list)` and `after(transformed, in_list)`
 *     (lib/transform.js:97,100,105);
 *   - `MAP`/`do_list(a, tw, allow_splicing = true)` calls
 *     `item.transform(tw, allow_splicing)` (lib/utils/index.js:101,106), so
 *     `in_list` is precisely the parent `do_list`'s `allow_splicing`;
 *   - AST_Sequence descends its expressions with the default
 *     `do_list(self.expressions, tw)` => `allow_splicing = true`
 *     (lib/transform.js:220-221), so a sequence element is `in_list`;
 *   - drop_unused's `before`/`after` use that flag to choose `MAP.skip`
 *     (drop the element) over `make_node(AST_Number, node, { value: 0 })`
 *     (lib/compress/drop-unused.js:238,243,457).
 *
 * The previous port inferred `in_list` from the parent node type
 * (`parent ∈ {AstBlock, AstDefinitions}`), which is FALSE for an
 * AST_Sequence parent. As a result an unused assignment that is a sequence
 * element was rewritten to the literal `0` instead of being dropped, leaving
 * a dead `0,` / `,0` in the output.
 *
 * Expected outputs verified by executing terser v5.46.1
 * (original-src/terser/main.js) with
 * { compress: { defaults: false, unused: true }, mangle: false }:
 *   "function g(){} function f() { var a; a = 1, g(); } f();"
 *       -> "function g(){}function f(){g()}f();"
 *   "function g(){} function f() { var a; g(), a = 1; } f();"
 *       -> "function g(){}function f(){g()}f();"
 *
 * (The minimal forms "function f() { var a; a = 1, g(); }" likewise yield
 * "function f(){g()}" under terser v5.46.1, but reference the undeclared
 * global `g`, which trips the ScopeAnalysis hang documented in
 * CompressTestHelper / ISS-031/032; `g` is declared here to keep the test
 * deterministic while still exercising the unused sequence-element drop.)
 */
package ssg
package js

import ssg.js.compress.CompressTestHelper.{ AllOff, assertCompresses }

final class DropUnusedInListIss1035Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // ISS-1035 — unused assignment as the FIRST element of a sequence is
  // dropped (in_list => MAP.skip), not rewritten to `0`. terser v5.46.1
  // executed: "function g(){}function f(){g()}f();".
  test("ISS-1035: unused assignment first in a sequence is dropped, not left as 0") {
    assertCompresses(
      input = "function g(){} function f() { var a; a = 1, g(); } f();",
      expected = "function g(){} function f() { g(); } f();",
      options = AllOff.copy(
        unused = true
      )
    )
  }

  // ISS-1035 — unused assignment as the LAST element of a sequence is
  // dropped (in_list => MAP.skip), not rewritten to `0`. terser v5.46.1
  // executed: "function g(){}function f(){g()}f();".
  test("ISS-1035: unused assignment last in a sequence is dropped, not left as 0") {
    assertCompresses(
      input = "function g(){} function f() { var a; g(), a = 1; } f();",
      expected = "function g(){} function f() { g(); } f();",
      options = AllOff.copy(
        unused = true
      )
    )
  }
}
