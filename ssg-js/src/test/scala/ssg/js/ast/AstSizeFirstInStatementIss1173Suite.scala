/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1173 reproducer: AstSize.isFirstInStatement is a direct-parent-only check that
 * only inspects the DIRECT parent for AstSimpleStatement. Terser's
 * first_in_statement (original-src/terser/lib/utils/first_in_statement.js:17-39)
 * is a LEFT-SPINE WALK up the parent stack: it ascends while each parent has
 * the walked-up node as its left-spine element (Sequence.expressions[0],
 * Dot.expression, ...), returning true when an AST_Statement has that node as
 * its body. An AST_Object that is the left-spine start of a statement is
 * therefore first-in-statement in terser and gets +2 (parens) added to its
 * size (original-src/terser/lib/size.js:366-367), but SSG's direct-parent-only check
 * sees the object's direct parent (AstDot, not AstSimpleStatement) and omits
 * the +2 — under-sizing the node by 2.
 */
package ssg
package js
package ast

import ssg.js.parse.Parser

final class AstSizeFirstInStatementIss1173Suite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // Differential: the SAME object-under-Dot subtree `({a:1}).b` appears in both
  // snippets, paired with `0` in a Sequence, so the two snippets contain an
  // identical multiset of nodes (and identical per-node sizes) EXCEPT for the
  // object's first-in-statement parens.
  //
  //   firstStart = "({a:1}).b,0" -> object is Sequence.expressions[0], i.e. the
  //     LEFT SPINE of the statement -> first_in_statement(object) == true in
  //     terser -> object size gains +2 (parens).
  //   notFirst   = "0,({a:1}).b" -> object is Sequence.expressions[1], NOT on
  //     the left spine -> first_in_statement(object) == false -> no +2.
  //
  // So size(firstStart) - size(notFirst) must be exactly 2.
  // SSG's direct-parent-only isFirstInStatement returns false in BOTH cases (the
  // object's direct parent is AstDot, never AstSimpleStatement), so the
  // difference collapses to 0 -> RED until the left-spine walk is ported.
  test("ISS-1173: left-spine object at statement start is sized with parens (+2)") {
    val firstStart = AstSize.size(parse("({a:1}).b,0"))
    val notFirst   = AstSize.size(parse("0,({a:1}).b"))
    assertEquals(
      firstStart - notFirst,
      2,
      s"expected left-spine first-in-statement object to add +2 parens " +
        s"(firstStart=$firstStart, notFirst=$notFirst)"
    )
  }
}
