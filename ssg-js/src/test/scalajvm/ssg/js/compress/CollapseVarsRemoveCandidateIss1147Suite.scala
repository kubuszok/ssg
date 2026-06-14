/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Regression coverage for ISS-1147 (ssg-js, R0610-P1).
 *
 * collapse_vars's `remove_candidate` remover must drop a matched candidate
 * statement that sits in a statement list once the candidate has been
 * collapsed into a later use. Upstream returns `in_list ? MAP.skip : null`
 * at terser/lib/compress/tighten-body.js:882 — the `MAP.skip` branch is what
 * removes the candidate from its enclosing statement list. In SSG's transform
 * semantics a plain `null` from a `before` callback means DESCEND-AND-KEEP
 * (AstNode.scala:85-90), so returning `null` for the in-list candidate leaves
 * the collapsed statement behind instead of removing it.
 *
 * Oracle: terser v5, `minify(code, {compress:{defaults:false,
 * collapse_vars:true}, mangle:false})`. See also the candidate-removal shape
 * exercised by collapse_vars.js fixture `collapse_vars_seq`
 * (CompressCollapseVarsSuite.scala:618), where the `a = z, b = 7;` simple
 * statement is collapsed and removed from the function body. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CollapseVarsRemoveCandidateIss1147Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // =========================================================================
  // Control (passes today): a constant collapse into a closure.
  //
  // `var outer = 3` is a constant that is replaced inside the returned
  // closure, and `unused` removes the now-dead declaration. This anchors the
  // harness for a successful collapse_vars run; it mirrors the
  // `collapse_vars_closures` fixture (CompressCollapseVarsSuite.scala:1083),
  // which passes on the current port.
  // =========================================================================
  test("collapse_vars_remove_candidate_control_Iss1147") {
    assertCompresses(
      input = """function g() {
            var outer = 3;
            return function() { return outer; };
        }""".stripMargin.trim,
      // terser oracle: function g(){return function(){return 3}}
      expected = """function g() {
            return function() { return 3; };
        }""".stripMargin.trim,
      options = AllOff.copy(
        booleans = true,
        collapseVars = true,
        comparisons = true,
        conditionals = true,
        deadCode = true,
        evaluate = true,
        hoistFuns = true,
        ifReturn = true,
        joinVars = true,
        keepFargs = true,
        loops = true,
        properties = true,
        reduceFuncs = true,
        reduceVars = true,
        sequencesLimit = 200,
        sideEffects = true,
        unused = true
      )
    )
  }

  // =========================================================================
  // RED (fails today): candidate in statement-LIST position is collapsed into
  // a later use and the original statement must be REMOVED.
  //
  // `a = x;` is a simple statement in the function body's statement list. It
  // is the candidate; after `collapse_vars` folds it into `return a + 1`, the
  // candidate statement is removed via `in_list ? MAP.skip` (upstream
  // tighten-body.js:882). The terser oracle drops the `a = x;` statement:
  //   function f(x){var a;return(a=x)+1}
  //
  // On current SSG code TightenBody.removeCandidate returns plain `null` for
  // the in-list candidate, which KEEPS the node, so the port leaves the stray
  // `a = x;` statement behind:
  //   function f(x){var a;a=x;return a+1}
  //
  // This assertion therefore FAILS today: terser-expected (statement removed)
  // vs actual (statement leftover).
  // =========================================================================
  test("collapse_vars_remove_candidate_in_list_Iss1147") {
    assertCompresses(
      input = """function f(x) {
            var a;
            a = x;
            return a + 1;
        }""".stripMargin.trim,
      // terser oracle: function f(x){var a;return(a=x)+1}
      expected = """function f(x) {
            var a;
            return (a = x) + 1;
        }""".stripMargin.trim,
      options = AllOff.copy(
        collapseVars = true
      )
    )
  }
}
