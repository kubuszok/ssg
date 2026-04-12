/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Remove expressions with no side effects.
 *
 * When we don't care about the value of an expression, only about its side
 * effects, this module determines what can be safely dropped. For example,
 * `(a, 5)` can be reduced to just `a` because `5` has no side effects.
 *
 * Ported from: terser lib/compress/drop-side-effect-free.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*, def_drop_side_effect_free -> pattern matching,
 *     drop_side_effect_free -> dropSideEffectFree
 *   Convention: Object with methods, pattern matching instead of DEFMETHOD
 *   Idiom: boundary/break instead of return, ArrayBuffer trimming
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.Common.makeSequence
import ssg.js.compress.Inference.{ hasSideEffects, isCalleePure, isNullishShortcircuited, lazyOp, mayThrowOnAccess, unarySideEffects }
import ssg.js.compress.NativeObjects.purePropAccessGlobals

/** Side-effect-free expression removal.
  *
  * Provides `dropSideEffectFree(node, compressor)` which returns null if the expression can be safely dropped (no side effects), or a reduced version of the expression containing only the
  * side-effectful parts.
  */
object DropSideEffectFree {

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Try to drop an expression that is only evaluated for its side effects.
    *
    * @param node
    *   the expression node
    * @param compressor
    *   the compressor context
    * @param firstInStatement
    *   true if this is the first expression in a statement position
    * @return
    *   null if the expression can be fully dropped, or the reduced expression
    */
  def dropSideEffectFree(
    node:             AstNode,
    compressor:       CompressorLike,
    firstInStatement: Boolean = false
  ): AstNode | Null = {
    node match {
      // Constants and `this` are always droppable
      case _: AstConstant | _: AstThis | _: AstTemplateSegment => null

      // Accessor/Function/Arrow are droppable (function values with no invocation)
      case _: AstAccessor | _: AstFunction | _: AstArrow => null

      // Call
      case call: AstCall =>
        dropCall(call, compressor, firstInStatement)

      // Class
      case cls: AstClass =>
        dropClass(cls, compressor)

      // Class property
      case cp: AstClassProperty =>
        val key = if (cp.computedKey()) {
          cp.key match {
            case n: AstNode => dropSideEffectFree(n, compressor, firstInStatement = false)
            case _ => null
          }
        } else { null }
        val value =
          if (cp.isStatic && cp.value != null)
            dropSideEffectFree(cp.value.nn, compressor, firstInStatement = false)
          else null
        if (key != null && value != null)
          makeSequence(cp, ArrayBuffer(key.nn, value.nn))
        else if (key != null) key
        else value

      case cpp: AstClassPrivateProperty =>
        if (cpp.isStatic && cpp.value != null)
          dropSideEffectFree(cpp.value.nn, compressor, firstInStatement = false)
        else null

      // Assign must come before Binary (AstAssign extends AstBinary)
      case assign: AstAssign =>
        dropAssign(assign, compressor)

      // Binary
      case binary: AstBinary =>
        dropBinary(binary, compressor, firstInStatement)

      // Conditional (ternary)
      case cond: AstConditional =>
        dropConditional(cond, compressor)

      // Unary
      case unary: AstUnary =>
        dropUnary(unary, compressor, firstInStatement)

      // SymbolRef
      case ref: AstSymbolRef =>
        // Declared symbols are safe to drop; undeclared may throw ReferenceError
        val d = ref.definition()
        val isDeclared = d != null && !d.nn.undeclared
        if (isDeclared || purePropAccessGlobals.contains(ref.name)) null else ref

      // Object literal
      case obj: AstObject =>
        val values = trim(obj.properties, compressor, firstInStatement)
        if (values == null) null
        else makeSequence(obj, values)

      // ObjectKeyVal
      case kv: AstObjectKeyVal =>
        val computedKey = kv.key.isInstanceOf[AstNode]
        val key         = if (computedKey) {
          kv.key match {
            case n: AstNode => dropSideEffectFree(n, compressor, firstInStatement)
            case _ => null
          }
        } else { null }
        val value = if (kv.value != null) dropSideEffectFree(kv.value.nn, compressor, firstInStatement) else null
        if (key != null && value != null) makeSequence(kv, ArrayBuffer(key.nn, value.nn))
        else if (key != null) key
        else value

      // Concise method / getter / setter — only computed keys have effects
      case cm: AstConciseMethod =>
        if (cm.computedKey()) cm.key match {
          case n: AstNode => n
          case _ => null
        }
        else null
      case og: AstObjectGetter =>
        if (og.computedKey()) og.key match {
          case n: AstNode => n
          case _ => null
        }
        else null
      case os: AstObjectSetter =>
        if (os.computedKey()) os.key match {
          case n: AstNode => n
          case _ => null
        }
        else null

      // Private method/getter/setter are always droppable
      case _: AstPrivateMethod | _: AstPrivateGetter | _: AstPrivateSetter => null

      // Array literal
      case arr: AstArray =>
        val values = trim(arr.elements, compressor, firstInStatement)
        if (values == null) null
        else makeSequence(arr, values)

      // Dot access
      case dot: AstDot =>
        if (isNullishShortcircuited(dot, compressor)) {
          if (dot.expression != null) dropSideEffectFree(dot.expression.nn, compressor, firstInStatement)
          else null
        } else if (!dot.optional && dot.expression != null && mayThrowOnAccess(dot, compressor)) {
          dot
        } else {
          if (dot.expression != null) dropSideEffectFree(dot.expression.nn, compressor, firstInStatement)
          else null
        }

      // Sub (bracket) access
      case sub: AstSub =>
        if (isNullishShortcircuited(sub, compressor)) {
          if (sub.expression != null) dropSideEffectFree(sub.expression.nn, compressor, firstInStatement)
          else null
        } else if (!sub.optional && sub.expression != null && mayThrowOnAccess(sub, compressor)) {
          sub
        } else {
          val property = sub.property match {
            case n: AstNode => dropSideEffectFree(n, compressor, firstInStatement = false)
            case _ => null
          }
          if (property != null && sub.optional) sub
          else {
            val expression =
              if (sub.expression != null)
                dropSideEffectFree(sub.expression.nn, compressor, firstInStatement)
              else null
            if (expression != null && property != null) makeSequence(sub, ArrayBuffer(expression.nn, property.nn))
            else if (expression != null) expression
            else property
          }
        }

      // Chain
      case chain: AstChain =>
        if (chain.expression != null) dropSideEffectFree(chain.expression.nn, compressor, firstInStatement)
        else null

      // Sequence
      case seq: AstSequence =>
        if (seq.expressions.isEmpty) {
          val zero = new AstNumber
          zero.start = seq.start
          zero.end = seq.end
          zero.value = 0.0
          zero
        } else {
          val last = seq.expressions.last
          val expr = dropSideEffectFree(last, compressor, firstInStatement = false)
          if (expr != null && (expr.nn eq last)) seq
          else {
            val expressions = seq.expressions.slice(0, seq.expressions.size - 1)
            if (expr != null) expressions.addOne(expr.nn)
            if (expressions.isEmpty) {
              val zero = new AstNumber
              zero.start = seq.start
              zero.end = seq.end
              zero.value = 0.0
              zero
            } else {
              makeSequence(seq, expressions)
            }
          }
        }

      // Expansion (spread)
      case exp: AstExpansion =>
        if (exp.expression != null) dropSideEffectFree(exp.expression.nn, compressor, firstInStatement)
        else null

      // TemplateString
      case ts: AstTemplateString =>
        val values = trim(ts.segments, compressor, firstInStatement)
        if (values == null) null
        else makeSequence(ts, values)

      // Default: keep the expression (it may have side effects)
      case _ => node
    }
  }

  // -----------------------------------------------------------------------
  // Call
  // -----------------------------------------------------------------------

  private def dropCall(
    call:             AstCall,
    compressor:       CompressorLike,
    firstInStatement: Boolean
  ): AstNode | Null =
    if (isNullishShortcircuited(call, compressor)) {
      if (call.expression != null) dropSideEffectFree(call.expression.nn, compressor, firstInStatement)
      else null
    } else if (isCalleePure(call, compressor)) {
      // Pure call: drop if args have no side effects
      val args = call.args
      val keptArgs = ArrayBuffer.empty[AstNode]
      var i = 0
      while (i < args.size) {
        val trimmed = dropSideEffectFree(args(i), compressor, false)
        if (trimmed != null) keptArgs.addOne(trimmed.nn)
        i += 1
      }
      if (keptArgs.isEmpty) null
      else if (keptArgs.size == 1) keptArgs(0)
      else {
        val seq = new AstSequence
        seq.expressions = keptArgs
        seq.start = call.start
        seq.end = call.end
        seq
      }
    } else {
      // Not pure, keep the call
      call
    }

  // -----------------------------------------------------------------------
  // Class
  // -----------------------------------------------------------------------

  private def dropClass(
    cls:        AstClass,
    compressor: CompressorLike
  ): AstNode | Null =
    boundary[AstNode | Null] {
      val withEffects = ArrayBuffer.empty[AstNode]

      // Check if class references itself (can't safely decompose)
      if (hasSideEffects(cls, compressor)) {
        // TODO: is_self_referential check
        // For safety, keep the class if it has any side effects
        break(cls)
      }

      // Extends clause
      if (cls.superClass != null) {
        val trimmedExtends = dropSideEffectFree(cls.superClass.nn, compressor)
        if (trimmedExtends != null) withEffects.addOne(trimmedExtends.nn)
      }

      // Properties
      var i = 0
      while (i < cls.properties.size) {
        cls.properties(i) match {
          case csb: AstClassStaticBlock =>
            if (hasSideEffects(csb, compressor)) break(cls) // be cautious
          case prop =>
            val trimmed = dropSideEffectFree(prop, compressor)
            if (trimmed != null) withEffects.addOne(trimmed.nn)
        }
        i += 1
      }

      if (withEffects.isEmpty) null
      else {
        val exprs = makeSequence(cls, withEffects)
        cls match {
          case _: AstDefClass =>
            // We want a statement
            val ss = new AstSimpleStatement
            ss.start = cls.start
            ss.end = cls.end
            ss.body = exprs
            ss
          case _ => exprs
        }
      }
    }

  // -----------------------------------------------------------------------
  // Binary
  // -----------------------------------------------------------------------

  private def dropBinary(
    binary:           AstBinary,
    compressor:       CompressorLike,
    firstInStatement: Boolean
  ): AstNode | Null =
    if (binary.right == null) {
      if (binary.left != null) dropSideEffectFree(binary.left.nn, compressor, firstInStatement) else null
    } else {
      val right = dropSideEffectFree(binary.right.nn, compressor)
      if (right == null) {
        if (binary.left != null) dropSideEffectFree(binary.left.nn, compressor, firstInStatement) else null
      } else if (lazyOp.contains(binary.operator)) {
        if (right.nn eq binary.right.nn) binary
        else {
          // Clone with reduced right side
          val clone = new AstBinary
          clone.start = binary.start
          clone.end = binary.end
          clone.operator = binary.operator
          clone.left = binary.left
          clone.right = right
          clone
        }
      } else {
        val left =
          if (binary.left != null)
            dropSideEffectFree(binary.left.nn, compressor, firstInStatement)
          else null
        if (left == null) {
          if (binary.right != null) dropSideEffectFree(binary.right.nn, compressor, firstInStatement) else null
        } else {
          makeSequence(binary, ArrayBuffer(left.nn, right.nn))
        }
      }
    }

  // -----------------------------------------------------------------------
  // Assign
  // -----------------------------------------------------------------------

  private def dropAssign(
    assign:     AstAssign,
    compressor: CompressorLike
  ): AstNode | Null =
    if (assign.logical) assign
    else {
      if (assign.left == null) assign
      else {
        val left = assign.left.nn
        if (hasSideEffects(left, compressor)) assign
        else {
          setFlag(assign, WRITE_ONLY)
          // Walk down property access chain
          var current = left
          while (current.isInstanceOf[AstPropAccess])
            current = current.asInstanceOf[AstPropAccess].expression match {
              case null => current // break the loop
              case expr => expr.nn
            }
          // If the target is a constant expression (pure access chain), we can drop
          if (Evaluate.isConstantExpression(current)) null
          else assign
        }
      }
    }

  // -----------------------------------------------------------------------
  // Conditional
  // -----------------------------------------------------------------------

  private def dropConditional(
    cond:       AstConditional,
    compressor: CompressorLike
  ): AstNode | Null =
    if (cond.consequent == null || cond.alternative == null || cond.condition == null) cond
    else {
      val consequent  = dropSideEffectFree(cond.consequent.nn, compressor)
      val alternative = dropSideEffectFree(cond.alternative.nn, compressor)

      if (
        (consequent != null && (consequent.nn eq cond.consequent.nn)) &&
        (alternative != null && (alternative.nn eq cond.alternative.nn))
      ) {
        cond
      } else if (consequent == null) {
        if (alternative != null) {
          // condition || alternative
          val binary = new AstBinary
          binary.start = cond.start
          binary.end = cond.end
          binary.operator = "||"
          binary.left = cond.condition
          binary.right = alternative
          binary
        } else {
          dropSideEffectFree(cond.condition.nn, compressor)
        }
      } else if (alternative == null) {
        // condition && consequent
        val binary = new AstBinary
        binary.start = cond.start
        binary.end = cond.end
        binary.operator = "&&"
        binary.left = cond.condition
        binary.right = consequent
        binary
      } else {
        val clone = new AstConditional
        clone.start = cond.start
        clone.end = cond.end
        clone.condition = cond.condition
        clone.consequent = consequent
        clone.alternative = alternative
        clone
      }
    }

  // -----------------------------------------------------------------------
  // Unary
  // -----------------------------------------------------------------------

  private def dropUnary(
    unary:            AstUnary,
    compressor:       CompressorLike,
    firstInStatement: Boolean
  ): AstNode | Null =
    if (unarySideEffects.contains(unary.operator)) {
      if (unary.expression != null && !hasSideEffects(unary.expression.nn, compressor)) {
        setFlag(unary, WRITE_ONLY)
      } else {
        clearFlag(unary, WRITE_ONLY)
      }
      unary
    } else if (unary.operator == "typeof" && unary.expression.isInstanceOf[AstSymbolRef]) {
      null
    } else {
      if (unary.expression != null)
        dropSideEffectFree(unary.expression.nn, compressor, firstInStatement)
      else null
    }

  // -----------------------------------------------------------------------
  // Trim helper
  // -----------------------------------------------------------------------

  /** Drop side-effect-free elements from a list of expressions.
    *
    * @return
    *   a new ArrayBuffer with only side-effectful nodes, or null if all were dropped
    */
  private def trim(
    nodes:            ArrayBuffer[AstNode],
    compressor:       CompressorLike,
    firstInStatement: Boolean
  ): ArrayBuffer[AstNode] | Null =
    if (nodes.isEmpty) null
    else {
      var changed = false
      val result  = ArrayBuffer.empty[AstNode]
      var fis     = firstInStatement
      var i       = 0
      while (i < nodes.size) {
        val dropped = dropSideEffectFree(nodes(i), compressor, fis)
        if (dropped == null || !(dropped.nn eq nodes(i))) changed = true
        if (dropped != null) {
          result.addOne(dropped.nn)
          fis = false
        }
        i += 1
      }
      if (!changed) nodes
      else if (result.isEmpty) null
      else result
    }
}
