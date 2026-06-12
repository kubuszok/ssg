/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1170: a destructuring assignment statement prints INVALID JS —
 * `({a:x}=b);` is printed without the mandatory parens as `{a:x}=b;`, which node
 * rejects (`{a:x}` parses as a block containing the labeled statement `a: x`, then
 * the dangling `=` is a SyntaxError).
 *
 * AST-representation findings (refines the ISS-1163 audit hypothesis):
 *   - terser 5.46.1 parses the LHS of `({a:x}=b);` as AST_Destructuring with
 *     `is_array === false` (NOT AST_Object) under AST_SimpleStatement > AST_Assign,
 *     verified by dumping node constructor names via lib/parse.js. `[a]=b;` yields
 *     AST_Destructuring with `is_array === true`. So output.js:1018
 *     (PARENS(AST_Object) first-in-statement) is NOT the rule that fires here;
 *     the operative rule is output.js:1247 inside PARENS(AST_Assign|AST_Conditional):
 *     `this instanceof AST_Assign && this.left instanceof AST_Destructuring &&
 *     this.left.is_array === false` — parens around the WHOLE assignment, in ANY
 *     position, not only first-in-statement (hence `y=({a:x}=b);` too).
 *   - The port parses the LHS the SAME way: Parser.scala `maybeAssign` calls
 *     `toDestructuring` (Parser.scala:3099) and the LHS becomes AstDestructuring
 *     with isArray=false (pinned green by the white-box control below). The issue's
 *     "LHS is not AstObject so the AST_Object PARENS rule cannot fire" hypothesis
 *     is therefore moot on both sides — neither implementation relies on AST_Object
 *     for this statement.
 *   - The actual port defect is in the print path: OutputStream.scala:592 dispatches
 *     `case _: AstAssign | _: AstConditional => needsParensAssignConditional(node)`
 *     which contains a faithful port of the output.js:1247 destructuring clause
 *     (OutputStream.scala:871-875) — but `AstAssign extends AstBinary`
 *     (AstExpressions.scala:268), so the EARLIER arm `case bin: AstBinary =>
 *     needsParensBinary(bin)` (OutputStream.scala:583) captures every AstAssign
 *     first and the destructuring-parens clause is unreachable dead code.
 *
 * Oracle: the vendored original terser at original-src/terser, version 5.46.1
 * (package.json), executed with node v24.12.0 on 2026-06-12:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, {compress: false, mangle: false});
 *   console.log(r.code);"
 *
 * Expected outputs recorded from that run (2026-06-12):
 *   a) ({a:x}=b);          → ({a:x}=b);
 *   b) ({a:[x]}=b);        → ({a:[x]}=b);
 *   c) ({a:{b:y}}=c);      → ({a:{b:y}}=c);
 *   d) ({a:x=1}=b);        → ({a:x=1}=b);
 *   e) if(t)({a:x}=b);     → if(t)({a:x}=b);
 *   f) for(;;)({a:x}=b);   → for(;;)({a:x}=b);
 *   g) ({a:x}=b),c;        → ({a:x}=b),c;
 *   h) y=({a:x}=b);        → y=({a:x}=b);    (control: port already correct here, see below)
 *   i) [a]=b;              → [a]=b;          (control: array pattern needs no parens)
 *   j) [{a:x}]=b;          → [{a:x}]=b;      (control: object pattern nested in array)
 *
 * Invalid-JS proofs (node v24.12.0, `new Function(code)`, 2026-06-12) for the
 * paren-less misprints of (a)-(g):
 *   {a:x}=b;  {a:[x]}=b;  {a:{b:y}}=c;  {a:x=1}=b;  if(t){a:x}=b;
 *   for(;;){a:x}=b;  while(t){a:x}=b;  {a:x}=b,c;
 *     → all throw SyntaxError: Unexpected token '='
 *
 * Port outputs observed on the first red run (2026-06-12, more-improvements
 * 9a45c8c9 + this suite): (a) {a:x}=b;  (b) {a:[x]}=b;  (c) {a:{b:y}}=c;
 * (d) {a:x=1}=b;  (e) if(t){a:x}=b;  (f) for(;;){a:x}=b;  (g) {a:x}=b,c;
 * — every one of which is one of the SyntaxError forms proven above. Cases
 * (h)-(j) matched the oracle byte-for-byte and are pinned as controls: in the
 * nested-assign position (h) the port's needsParensBinary path already emits the
 * parens, so the defect is confined to positions where no other parens rule fires.
 *
 * Shorthand scoping note: terser 5.46.1 with {compress:false, mangle:false} expands
 * shorthand properties at the default ecma 5 output level — `({a}=b);` prints as
 * `({a:a}=b);` and plain `var x={a};` prints as `var x={a:a};` (oracle run
 * 2026-06-12). Because the divergence reproduces in the non-destructuring context
 * `var x={a};`, it is ISS-1168's general ecma-gate defect, NOT part of this
 * statement-parens issue — it is deliberately NOT pinned here (all fixtures below
 * use explicit `key:value` properties); see ISS-1168.
 */
package ssg
package js

import ssg.js.ast.{ AstAssign, AstDestructuring, AstSimpleStatement }

final class DestructuringStmtIss1170Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  /** Equivalent of terser's `{compress: false, mangle: false}`. */
  private val noOpt = MinifyOptions(compress = false, mangle = false)

  // -- Red: expected output taken verbatim from terser 5.46.1 (header oracle run) --

  test("red ISS-1170 (a): object destructuring assignment statement keeps parens") {
    val out = Terser.minifyToString("({a:x}=b);", noOpt)
    assertEquals(out, "({a:x}=b);", "output must match terser 5.46.1 and be valid JS")
  }

  test("red ISS-1170 (b): array pattern nested under object pattern keeps parens") {
    val out = Terser.minifyToString("({a:[x]}=b);", noOpt)
    assertEquals(out, "({a:[x]}=b);", "output must match terser 5.46.1 and be valid JS")
  }

  test("red ISS-1170 (c): nested object pattern keeps parens") {
    val out = Terser.minifyToString("({a:{b:y}}=c);", noOpt)
    assertEquals(out, "({a:{b:y}}=c);", "output must match terser 5.46.1 and be valid JS")
  }

  test("red ISS-1170 (d): default value inside object pattern keeps parens") {
    val out = Terser.minifyToString("({a:x=1}=b);", noOpt)
    assertEquals(out, "({a:x=1}=b);", "output must match terser 5.46.1 and be valid JS")
  }

  test("red ISS-1170 (e): destructuring assignment as if-branch body keeps parens") {
    val out = Terser.minifyToString("if(t)({a:x}=b);", noOpt)
    assertEquals(out, "if(t)({a:x}=b);", "output must match terser 5.46.1 and be valid JS")
  }

  test("red ISS-1170 (f): destructuring assignment as for body keeps parens") {
    val out = Terser.minifyToString("for(;;)({a:x}=b);", noOpt)
    assertEquals(out, "for(;;)({a:x}=b);", "output must match terser 5.46.1 and be valid JS")
  }

  test("red ISS-1170 (g): destructuring assignment heading a sequence keeps parens") {
    val out = Terser.minifyToString("({a:x}=b),c;", noOpt)
    assertEquals(out, "({a:x}=b),c;", "output must match terser 5.46.1 and be valid JS")
  }

  // -- Control: nested-assign position is already correct (must PASS today) --
  // Probe result (2026-06-12, this suite's first red run): this test PASSES today.
  // output.js:1247 is position-independent (parens whenever an AST_Assign's left is an
  // object AST_Destructuring), and here the port's needsParensBinary path happens to
  // emit the parens already; `y={a:x}=b;` would even be valid JS. Keep this green so a
  // fix to the statement positions does not regress (or double-wrap) this one.

  test("control ISS-1170 (h): destructuring assignment nested in an assignment keeps parens") {
    val out = Terser.minifyToString("y=({a:x}=b);", noOpt)
    assertEquals(out, "y=({a:x}=b);", "output must match terser 5.46.1")
  }

  // -- Control, white-box: the port's LHS representation matches terser's --
  // Probe result (2026-06-12, this suite's first red run): this test PASSES today.
  // It refutes the ISS-1163 audit hypothesis quoted in ISS-1170 ("the LHS is not
  // represented as AstObject"): neither terser nor the port uses AST_Object/AstObject
  // here — both parse the LHS as an object-mode Destructuring node. The defect is the
  // dead `case _: AstAssign` arm in OutputStream.needsParens (see file header).

  test("control ISS-1170 (white-box): parsed LHS is AstDestructuring(isArray=false)") {
    val ast = Terser.minify("({a:x}=b);", noOpt).ast
    ast.body.head match {
      case stmt: AstSimpleStatement =>
        stmt.body.nn match {
          case assign: AstAssign =>
            assign.left.nn match {
              case d: AstDestructuring =>
                assert(!d.isArray, "object pattern must have isArray=false (terser: is_array === false)")
              case other => fail(s"LHS must be AstDestructuring (terser: AST_Destructuring), got: $other")
            }
          case other => fail(s"Expected AstAssign under the statement, got: $other")
        }
      case other => fail(s"Expected AstSimpleStatement, got: $other")
    }
  }

  // -- Controls: array destructuring statements are valid WITHOUT parens (must PASS today) --

  test("control ISS-1170 (i): array destructuring statement needs no parens") {
    val out = Terser.minifyToString("[a]=b;", noOpt)
    assertEquals(out, "[a]=b;", "output must match terser 5.46.1")
  }

  test("control ISS-1170 (j): object pattern nested inside array pattern needs no parens") {
    val out = Terser.minifyToString("[{a:x}]=b;", noOpt)
    assertEquals(out, "[{a:x}]=b;", "output must match terser 5.46.1")
  }
}
