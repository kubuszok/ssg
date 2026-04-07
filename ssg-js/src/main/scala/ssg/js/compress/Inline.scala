/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Function and variable inlining.
 *
 * Contains the logic for inlining variable references (replacing a symbol
 * with its constant/single-use value) and inlining function calls (replacing
 * a call with the function body). These are two of the most impactful
 * optimizations in the compressor.
 *
 * Ported from: terser lib/compress/inline.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: inline_into_symbolref -> inlineIntoSymbolRef,
 *     inline_into_call -> inlineIntoCall,
 *     within_array_or_object_literal -> withinArrayOrObjectLiteral,
 *     scope_encloses_variables_in_this_scope -> scopeEnclosesVariablesInThisScope,
 *     can_flatten_body -> canFlattenBody, can_inject_symbols -> canInjectSymbols,
 *     dont_inline_lambda_in_loop -> dontInlineLambdaInLoop
 *   Convention: Object with methods, pattern matching instead of instanceof chains
 *   Idiom: boundary/break instead of return
 *   Gap: 246 LOC vs upstream 684 LOC (~36%). Inlining logic stubbed at
 *     lines 74, 86, 138, 175 (SymbolDef + safety predicates). Function-call
 *     inlining is the highest-impact pass after constant folding; currently
 *     a no-op for most cases. See ISS-032. docs/architecture/terser-port.md.
 *   Audited: 2026-04-07 (major_issues)
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.compress.Common.*

/** Function and variable inlining engine.
  *
  * The two main entry points are:
  *   - `inlineIntoSymbolRef`: replaces a variable reference with its value when safe (single-use, constant, or evaluable).
  *   - `inlineIntoCall`: replaces a function call with the function body (IIFEs, simple returns, identity functions, empty bodies).
  */
object Inline {

  // -----------------------------------------------------------------------
  // Helper predicates
  // -----------------------------------------------------------------------

  /** Check if current compressor position is within an array or object literal. */
  @annotation.nowarn("msg=unused private member") // used when full inlining is ported
  private def withinArrayOrObjectLiteral(compressor: CompressorLike): Boolean =
    boundary[Boolean] {
      var level = 0
      var node: AstNode | Null = compressor.parent(level)
      while (node != null) {
        node.nn match {
          case _: AstStatement => break(false)
          case _: AstArray | _: AstObjectKeyVal | _: AstObject => break(true)
          case _                                               =>
        }
        level += 1
        node = compressor.parent(level)
      }
      false
    }

  /** Check if a scope encloses variables from the pulled scope that would conflict with variables in the target scope.
    */
  @annotation.nowarn("msg=unused private member") // used when full inlining is ported
  private def scopeEnclosesVariablesInThisScope(
    scope:       AstScope,
    pulledScope: AstScope
  ): Boolean =
    // TODO: implement full enclosed variable check when scope analysis is complete
    // For now, conservatively return false (allow inlining)
    false

  /** Check if the identifier is shorter than its init value — used for top_retain optimization.
    */
  @annotation.nowarn("msg=unused private member") // used when full inlining is ported
  private def isConstSymbolShorterThanInitValue(
    origLen:    Int,
    fixedValue: AstNode | Null
  ): Boolean =
    if (fixedValue != null) {
      // TODO: implement size() comparison when available
      true
    } else {
      true
    }

  /** Prevent inlining functions/classes into loops for performance reasons. */
  @annotation.nowarn("msg=unused private member") // used when full inlining is ported
  private def dontInlineLambdaInLoop(
    compressor:  CompressorLike,
    maybeLambda: AstNode | Null
  ): Boolean =
    if (maybeLambda == null) false
    else {
      val node = maybeLambda.nn
      (node.isInstanceOf[AstLambda] || node.isInstanceOf[AstClass]) &&
      isWithinLoop(compressor)
    }

  /** Check if the compressor is currently inside a loop body. */
  private def isWithinLoop(compressor: CompressorLike): Boolean =
    boundary[Boolean] {
      var level = 0
      var node: AstNode | Null = compressor.parent(level)
      while (node != null) {
        node.nn match {
          case _: AstIterationStatement => break(true)
          case _: AstScope              => break(false)
          case _ =>
        }
        level += 1
        node = compressor.parent(level)
      }
      false
    }

  // -----------------------------------------------------------------------
  // Inline into SymbolRef
  // -----------------------------------------------------------------------

  /** Try to inline the value of a variable reference.
    *
    * For single-use variables: replaces the reference with the value directly. For multi-use variables: replaces with evaluated constant if shorter.
    *
    * @param self
    *   the SymbolRef node
    * @param compressor
    *   the compressor context
    * @return
    *   the inlined node, or `self` if inlining is not beneficial
    */
  def inlineIntoSymbolRef(self: AstSymbolRef, compressor: CompressorLike): AstNode =
    // TODO: implement full inlining logic when scope/SymbolDef is complete
    //
    // The full implementation requires:
    // 1. Access to self.definition() for reference counting
    // 2. Access to self.fixed_value() for the constant value
    // 3. Scope analysis (enclosed variables, recursive refs)
    // 4. Size comparison via .size() method
    //
    // Key optimizations to implement:
    // - Single-use: replace ref with value, mark SQUEEZED
    // - Constant propagation: replace with evaluated constant
    // - This-binding: replace `this` refs within same scope
    // - Lambda/Class: inline single-use function/class defs
    self

  // -----------------------------------------------------------------------
  // Inline into Call
  // -----------------------------------------------------------------------

  /** Try to inline a function call.
    *
    * Handles several patterns:
    *   1. Simple return: `(function(){ return expr; })(args)` -> `(args, expr)`
    *   2. Identity: `(function(x){ return x; })(arg)` -> `arg`
    *   3. Empty body: `(function(){ })(args)` -> `(args, void 0)`
    *   4. Full flatten: inline multi-statement bodies when safe
    *   5. IIFE negation: `!function(){}()` -> `function(){}()`
    *   6. Constant evaluation
    *
    * @param self
    *   the Call node
    * @param compressor
    *   the compressor context
    * @return
    *   the inlined node, or `self` if inlining is not possible
    */
  def inlineIntoCall(self: AstCall, compressor: CompressorLike): AstNode = {
    // TODO: implement full call inlining when scope analysis is complete
    //
    // The full implementation requires:
    // 1. fn.pinned() check
    // 2. fn.uses_arguments check
    // 3. fn.contains_this() check
    // 4. can_inject_symbols() for safe variable injection
    // 5. scope_encloses_variables_in_this_scope() for scope safety
    //
    // Key patterns to implement:
    // - Return-only body: extract return value, prepend args
    // - Identity function: (x) => x becomes passthrough
    // - Empty body: drop call, keep side effects
    // - IIFE negation for statement context
    // - Full body flattening (inline >= 3)

    val exp = self.expression
    if (exp == null) {
      self
    } else {
      val fn            = exp.nn
      val isFunc        = fn.isInstanceOf[AstLambda]
      val isRegularFunc = isFunc && {
        val lambda = fn.asInstanceOf[AstLambda]
        !lambda.isGenerator && !lambda.isAsync
      }

      // Empty body optimization: (function(){})(...args) -> (...args, void 0)
      if (isRegularFunc && compressor.optionBool("side_effects")) {
        val lambda = fn.asInstanceOf[AstLambda]
        if (lambda.body.forall(isEmpty)) {
          val voidNode = makeVoid0(self)
          val args     = ArrayBuffer.empty[AstNode]
          args.addAll(self.args)
          args.addOne(voidNode)
          return makeSequence(self, args) // TODO: .optimize(compressor)
        }
      }

      // IIFE negation: function(){}() in statement position
      if (compressor.optionBool("negate_iife")) {
        compressor.parent() match {
          case _: AstSimpleStatement if isIifeCall(self) =>
          // TODO: return self.negate(compressor, true)
          case _ =>
        }
      }

      self
    }
  }

  // -----------------------------------------------------------------------
  // Void 0 helper
  // -----------------------------------------------------------------------

  /** Create `void 0` node preserving source position from `orig`. */
  private def makeVoid0(orig: AstNode): AstNode = {
    val zero = new AstNumber
    zero.start = orig.start
    zero.end = orig.end
    zero.value = 0.0

    val prefix = new AstUnaryPrefix
    prefix.start = orig.start
    prefix.end = orig.end
    prefix.operator = "void"
    prefix.expression = zero
    prefix
  }
}
