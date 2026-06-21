/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Test for ISS-1042: ParserOptions gains a `toplevel` option (terser parse.js:1151
 * `toplevel : null`). When supplied, parseToplevel (terser parse.js:3601-3611) appends
 * the newly parsed statements to the existing toplevel's body and advances its end —
 * the basis of multi-file input — returning that SAME instance. `start` is preserved.
 *
 * Oracle (parse.js:3602-3608):
 *   var toplevel = options.toplevel;
 *   if (toplevel) {
 *       toplevel.body = toplevel.body.concat(body);
 *       toplevel.end = end;
 *   } else {
 *       toplevel = new AST_Toplevel({ start: start, body: body, end: end });
 *   }
 *
 * Without the new `toplevel` field, `ParserOptions(toplevel = tl1)` does not compile —
 * that compile failure is the red state proving this test gates the change.
 */
package ssg
package js
package parse

import ssg.js.ast.AstToplevel

final class ParserToplevelOptionIss1042Suite extends munit.FunSuite {

  test("ISS-1042: parsing into an existing toplevel appends statements (same instance)") {
    // Step 1: parse source A with default options.
    val tl1: AstToplevel = new Parser().parse("var a = 1; var b = 2;")
    assertEquals(tl1.body.size, 2, "source A has two top-level statements")
    val startAfterA = tl1.start
    val endAfterA   = tl1.end
    val bodyA       = tl1.body.toVector // A's statements, captured before the append

    // Step 2: parse source B continuing into tl1.
    val result = new Parser(ParserOptions(toplevel = tl1)).parse("var c = 3;")

    // The returned node IS the passed-in toplevel (identity preserved).
    assert(result eq tl1, "result must be the same instance as the supplied toplevel")
    // Body now holds A's and B's statements (2 + 1).
    assertEquals(tl1.body.size, 3, "body holds A's then B's statements")
    // Existing-first order (oracle: toplevel.body.concat(body)) — A's nodes stay first, then B's.
    assert(tl1.body(0) eq bodyA(0), "A's first statement stays at index 0")
    assert(tl1.body(1) eq bodyA(1), "A's second statement stays at index 1")
    assert(!bodyA.contains(tl1.body(2)), "B's statement is appended after A's")
    // start is unchanged (oracle: only body and end are updated).
    assert(tl1.start eq startAfterA, "start is preserved from after parsing A")
    // end advanced to reflect B (no longer the end from after A).
    assert(tl1.end ne endAfterA, "end advanced to reflect source B")
  }

  test("ISS-1042: default options (no toplevel) yield a fresh independent toplevel") {
    val tlA = new Parser().parse("var a = 1; var b = 2;")
    val tlB = new Parser().parse("var c = 3;")
    assert(tlA ne tlB, "default parses produce distinct toplevels")
    assertEquals(tlA.body.size, 2)
    assertEquals(tlB.body.size, 1)
  }
}
