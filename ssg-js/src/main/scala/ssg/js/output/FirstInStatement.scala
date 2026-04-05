/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Utility functions for determining statement position of AST nodes.
 *
 * Original source: terser lib/utils/first_in_statement.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: first_in_statement -> firstInStatement, left_is_object -> leftIsObject
 *   Convention: Pure functions, no mutation
 *   Idiom: Pattern matching instead of instanceof chains
 */
package ssg
package js
package output

import scala.util.boundary
import scala.util.boundary.break
import ssg.js.ast.*

/** Utilities to determine if a node is the lexically first token in a statement, and whether the leftmost sub-expression of a node is an object literal.
  *
  * These are critical for correct parenthesization: a function expression or object literal at statement position must be wrapped in parentheses to avoid being parsed as a declaration or block.
  */
object FirstInStatement {

  /** Returns true if the innermost node on the output stack is lexically the first token in a statement.
    *
    * Walks up the parent chain: if at each level the node is the "leftmost" child of the parent (e.g., left operand of binary, first expression of sequence, condition of conditional, etc.), keep
    * going. If the parent is a statement-with-body and the node is its body, return true. Otherwise return false.
    */
  def firstInStatement(output: OutputStream): Boolean =
    boundary[Boolean] {
      var node: AstNode | Null = output.parent(-1)
      if (node == null) break(false)
      var i = 0
      var p: AstNode | Null = output.parent(i)
      while (p != null) {
        val parent = p.nn
        parent match {
          case stmt: AstStatementWithBody if stmt.body != null && (stmt.body.nn eq node.nn) =>
            break(true)
          case _ =>
            val isLeftmost = parent match {
              case seq: AstSequence =>
                seq.expressions.nonEmpty && (seq.expressions.head eq node.nn)
              case call: AstCall =>
                call.expression != null && (call.expression.nn eq node.nn)
              case pts: AstPrefixedTemplateString =>
                pts.prefix != null && (pts.prefix.nn eq node.nn)
              case dot: AstDot =>
                dot.expression != null && (dot.expression.nn eq node.nn)
              case sub: AstSub =>
                sub.expression != null && (sub.expression.nn eq node.nn)
              case chain: AstChain =>
                chain.expression != null && (chain.expression.nn eq node.nn)
              case cond: AstConditional =>
                cond.condition != null && (cond.condition.nn eq node.nn)
              case bin: AstBinary =>
                bin.left != null && (bin.left.nn eq node.nn)
              case post: AstUnaryPostfix =>
                post.expression != null && (post.expression.nn eq node.nn)
              case _ => false
            }
            if (isLeftmost) {
              node = parent
            } else {
              break(false)
            }
        }
        i += 1
        p = output.parent(i)
      }
      false
    }

  /** Returns whether the leftmost item in the expression tree is an object literal.
    *
    * This is used to determine if an expression statement starting with `{` would be ambiguous with a block statement.
    */
  def leftIsObject(node: AstNode): Boolean =
    node match {
      case _:   AstObject   => true
      case seq: AstSequence =>
        seq.expressions.nonEmpty && leftIsObject(seq.expressions.head)
      case call: AstCall if call.expression != null =>
        leftIsObject(call.expression.nn)
      case pts: AstPrefixedTemplateString if pts.prefix != null =>
        leftIsObject(pts.prefix.nn)
      case dot: AstDot if dot.expression != null =>
        leftIsObject(dot.expression.nn)
      case sub: AstSub if sub.expression != null =>
        leftIsObject(sub.expression.nn)
      case chain: AstChain if chain.expression != null =>
        leftIsObject(chain.expression.nn)
      case cond: AstConditional if cond.condition != null =>
        leftIsObject(cond.condition.nn)
      case bin: AstBinary if bin.left != null =>
        leftIsObject(bin.left.nn)
      case post: AstUnaryPostfix if post.expression != null =>
        leftIsObject(post.expression.nn)
      case _ => false
    }
}
