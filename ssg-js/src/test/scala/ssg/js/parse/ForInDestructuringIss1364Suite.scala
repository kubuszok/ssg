/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Reproducer / diagnosis for ISS-1364.
 *
 * FILED symptom: `for ([x,y] in pairs)` and `for ({a,b} in obj)` (a for-in/for-of with a
 * BARE destructuring assignment-target LHS — array/object literal, no var/let/const) throw
 * `JsParseError: SyntaxError: Unexpected token: eof`. Filed as a Parser bug: "add the
 * toDestructuring conversion".
 *
 * ACTUAL diagnosis (this suite): the Parser already handles bare-destructuring for-in AND
 * for-of correctly. The for-loop init dispatcher (Parser.scala:619-626) DOES reach
 * `toDestructuring(other)` for a bare array/object literal LHS, and `toDestructuring`
 * (Parser.scala:3034-3081) correctly converts AstArray -> AstDestructuring(isArray=true) and
 * AstObject -> AstDestructuring(isArray=false). Every VALID form parses (tests below are GREEN).
 *
 * The "Unexpected token: eof" is thrown only for a TRUNCATED input whose final for-statement
 * has NO body — e.g. the string `... ; for ([x,y] in pairs)` that ends right after `pairs)`.
 * A `for` requires a Statement body; with nothing following, `forIn` (Parser.scala:679) calls
 * `statement(isForBody = true)` which hits EOF and `unexpected()`s at Parser.scala:486. This is
 * FAITHFUL behavior — terser rejects the same truncated source.
 *
 * ROOT CAUSE of the blocked test: `CompressDestructuringSuite`'s `reduce_vars` case
 * (ssg-js/src/test/scalajvm/ssg/js/compress/CompressDestructuringSuite.scala:219 and :235)
 * MIS-TRANSCRIBED terser test/compress/destructuring.js:284 — it DROPPED the trailing `;` from
 * the final `for ([x,y] in pairs);`, leaving an invalid, body-less for at EOF. Terser's source
 * has the `;` (destructuring.js:284) and parses fine. The fix is to restore the `;` in the
 * suite input+expected (a test-transcription fix, ISS-1366 territory) — NOT a Parser.scala change.
 *
 * Terser reference — for_() at parse.js:1547-1608, LHS dispatch at :1580:
 *   else if (is_assignable(init) || (init = to_destructuring(init)) instanceof AST_Destructuring)
 */
package ssg
package js
package parse

import ssg.js.ast.{AstForIn, AstForOf, AstDestructuring, AstNode}

final class ForInDestructuringIss1364Suite extends munit.FunSuite {

  private def parseStmt(src: String): AstNode =
    new Parser().parse(src).body.head

  // ==========================================================================
  // The parser ALREADY handles every valid bare-destructuring form — GREEN.
  // These prove ISS-1364's filed "Parser can't parse bare destructuring LHS" is wrong.
  // ==========================================================================

  test("ISS-1364: for-in, bare array-destructuring LHS, empty-stmt body -> ForIn+Destructuring(array)") {
    val forIn = parseStmt("for ([x,y] in pairs);").asInstanceOf[AstForIn]
    val init  = forIn.init.asInstanceOf[AstDestructuring]
    assert(init.isArray, "array-literal LHS must become an array destructuring")
    assertEquals(init.names.size, 2)
  }

  test("ISS-1364: for-in, bare object-destructuring LHS, block body -> ForIn+Destructuring(object)") {
    val forIn = parseStmt("for ({a, b} in obj) {}").asInstanceOf[AstForIn]
    val init  = forIn.init.asInstanceOf[AstDestructuring]
    assert(!init.isArray, "object-literal LHS must become an object destructuring")
    assertEquals(init.names.size, 2)
  }

  test("ISS-1364: for-of, bare array-destructuring LHS -> ForOf+Destructuring(array)") {
    val forOf = parseStmt("for ([x, y] of pairs) {}").asInstanceOf[AstForOf]
    val init  = forOf.init.asInstanceOf[AstDestructuring]
    assert(init.isArray, "array-literal LHS must become an array destructuring")
    assertEquals(init.names.size, 2)
  }

  test("ISS-1364: all four for-in LHS variants parse WHEN each has a body (';')") {
    val src =
      """for (const [x,y] in pairs);
        |for (let [x,y] in pairs);
        |for (var [x,y] in pairs);
        |for ([x,y] in pairs);""".stripMargin
    val tl = new Parser().parse(src)
    assertEquals(tl.body.size, 4)
  }

  // ==========================================================================
  // The real trigger: the EXACT (truncated) suite input throws — faithful behavior.
  // Reproduces the filed exception + localizes it to the missing for-body, not the LHS.
  // ==========================================================================

  test("ISS-1364 DIAGNOSTIC: exact reduce_vars suite input (drops trailing ';') throws eof at the for-BODY") {
    // This is CompressDestructuringSuite.scala:219 verbatim — note it ends at `pairs)` (no ';').
    val truncated =
      """for (const [x,y] in pairs);
        |for (let [x,y] in pairs);
        |for (var [x,y] in pairs);
        |for ([x,y] in pairs)""".stripMargin
    val ex = intercept[JsParseError](new Parser().parse(truncated))
    assert(
      ex.getMessage.contains("Unexpected token: eof"),
      s"expected eof error from the missing for-body, got: ${ex.getMessage}"
    )
    // Restoring the trailing ';' (as terser destructuring.js:284 has it) makes it parse.
    val fixed = truncated + ";"
    assertEquals(new Parser().parse(fixed).body.size, 4)
  }

  // ==========================================================================
  // Controls — isolate: declared destructuring and simple LHS also parse.
  // ==========================================================================

  test("ISS-1364 control: for-of with declared destructuring (const) parses") {
    assert(parseStmt("for (const [x, y] of pairs) {}").isInstanceOf[AstForOf])
  }

  test("ISS-1364 control: for-in with a simple (symbol-ref) LHS parses") {
    assert(parseStmt("for (x in obj) {}").isInstanceOf[AstForIn])
  }
}
