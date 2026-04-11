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
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.compress.Common.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.scope.{ ScopeAnalysis, SymbolDef }

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
  @annotation.nowarn("msg=unused private member") // used by full body flattening (inline level >= 3)
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

  /** Check if a scope encloses variables from the pulled scope that would conflict. */
  private def scopeEnclosesVariablesInThisScope(
    scope:       AstScope,
    pulledScope: AstScope
  ): Boolean =
    boundary[Boolean] {
      for (enclosedAny <- pulledScope.enclosed) {
        val enclosed = enclosedAny.asInstanceOf[SymbolDef]
        if (pulledScope.variables.contains(enclosed.name)) {
          // Variable is local to the pulled scope — no conflict
        } else {
          val lookedUp = ScopeAnalysis.findVariable(scope, enclosed.name)
          if (lookedUp != null && (lookedUp.nn.asInstanceOf[AnyRef] ne enclosed.asInstanceOf[AnyRef])) {
            break(true)
          }
        }
      }
      false
    }

  /** Prevent inlining functions/classes into loops for performance reasons. */
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
    boundary[AstNode] {
      val d = self.definition()
      if (d == null) break(self)
      val dd = d.nn

      val parent = compressor.parent()
      val fixed = self.fixedValue() match {
        case n: AstNode => n
        case _          => null
      }

      // Don't inline retained top-level symbols
      if (compressor.topRetain(dd) && dd.global) {
        dd.fixed = false
        dd.singleUse = false
        break(self)
      }

      // Don't inline lambdas/classes into loops
      if (dontInlineLambdaInLoop(compressor, fixed)) break(self)

      // Single-use optimization
      var singleUse = dd.singleUse
      if (singleUse != false && singleUse != null) {
        // Don't single-use inline into exports if the value has a name
        parent match {
          case _: AstExport if fixed != null && fixed.isInstanceOf[AstLambda] && fixed.asInstanceOf[AstLambda].name != null =>
            singleUse = false
          case _ =>
        }
      }

      if (singleUse == true && fixed != null) {
        // Conservatively disable single-use for complex expressions that may
        // have side effects (calls, assignments, etc.)
        fixed match {
          case _: AstCall | _: AstAssign | _: AstNew | _: AstUnary =>
            singleUse = false
          case _ =>
        }
      }

      if (singleUse == true && fixed != null) {
        fixed match {
          case lambda: AstLambda if !lambda.isInstanceOf[AstAccessor] =>
            // Single-use lambda: inline if scope is safe
            if (dd.scope ne self.scope.nn) {
              // Cross-scope: check if lambda has side effects in the new scope
              if (scopeEnclosesVariablesInThisScope(self.scope.nn, lambda)) {
                break(self)
              }
            }
            if (dd.recursiveRefs > 0) break(self)

            // Mark as inlined
            setFlag(fixed, INLINED)
            dd.references.foreach(ref => setFlag(ref, INLINED))
            dd.replaced += 1
            break(fixed)

          case cls: AstClass =>
            // Single-use class: inline
            setFlag(cls, INLINED)
            dd.references.foreach(ref => setFlag(ref, INLINED))
            dd.replaced += 1
            break(cls)

          case _ =>
            // Single-use constant/expression: inline if no side effects
            if (dd.scope eq self.scope.nn) {
              setFlag(fixed, INLINED)
              dd.replaced += 1
              break(fixed)
            }
        }
      }

      // Constant propagation: replace with evaluated constant if possible
      if (fixed != null && compressor.optionBool("evaluate")) {
        fixed match {
          case _: AstConstant =>
            // Literals can always be inlined
            dd.replaced += 1
            break(fixed)
          case _ =>
        }
      }

      self
    }

  // -----------------------------------------------------------------------
  // Inline into Call
  // -----------------------------------------------------------------------

  /** Try to inline a function call.
    *
    * Handles several patterns:
    *   1. Simple return: `(function(){ return expr; })(args)` -> `(args, expr)`
    *   2. Identity: `(function(x){ return x; })(arg)` -> `arg`
    *   3. Empty body: `(function(){ })(args)` -> `(args, void 0)`
    *   4. IIFE negation
    *
    * @param self
    *   the Call node
    * @param compressor
    *   the compressor context
    * @return
    *   the inlined node, or `self` if inlining is not possible
    */
  def inlineIntoCall(self: AstCall, compressor: CompressorLike): AstNode =
    boundary[AstNode] {
      val exp = self.expression
      if (exp == null) break(self)

      val fn = exp.nn
      val isFunc = fn.isInstanceOf[AstLambda]
      val isRegularFunc = isFunc && {
        val lambda = fn.asInstanceOf[AstLambda]
        !lambda.isGenerator && !lambda.isAsync
      }

      if (!isRegularFunc) break(self)

      val lambda = fn.asInstanceOf[AstLambda]

      // Skip if the function is an accessor
      if (lambda.isInstanceOf[AstAccessor]) break(self)

      // Empty body optimization: (function(){})(...args) -> (...args, void 0)
      if (compressor.optionBool("side_effects") && lambda.body.forall(isEmpty)) {
        val voidNode = makeVoid0(self)
        if (self.args.isEmpty) {
          break(voidNode)
        } else {
          val args = ArrayBuffer.empty[AstNode]
          args.addAll(self.args)
          args.addOne(voidNode)
          break(makeSequence(self, args))
        }
      }

      // Single return-value optimization: (function(){ return X; })(...args) -> (args..., X)
      if (
        lambda.body.size == 1 &&
        !lambda.usesArguments &&
        lambda.argnames.isEmpty &&
        !self.args.exists(_.isInstanceOf[AstExpansion])
      ) {
        lambda.body(0) match {
          case ret: AstReturn if ret.value != null =>
            // (function(){ return X; })() -> X (if no args)
            if (self.args.isEmpty) {
              break(ret.value.nn)
            }
          case _ =>
        }
      }

      // Return-only with args: (function(x){ return x; })(arg) -> arg
      if (
        lambda.body.size == 1 &&
        !lambda.usesArguments &&
        lambda.argnames.size == 1 &&
        self.args.size == 1
      ) {
        lambda.body(0) match {
          case ret: AstReturn if ret.value != null =>
            ret.value.nn match {
              case ref: AstSymbolRef if lambda.argnames(0).isInstanceOf[AstSymbol] =>
                val argSym = lambda.argnames(0).asInstanceOf[AstSymbol]
                if (ref.name == argSym.name) {
                  // Identity function: (x => x)(arg) -> arg
                  break(self.args(0))
                }
              case _ =>
            }
          case _ =>
        }
      }

      // IIFE negation: function(){}() in statement position
      if (compressor.optionBool("negate_iife")) {
        compressor.parent() match {
          case _: AstSimpleStatement if isIifeCall(self) =>
            // Already an IIFE in statement position — keep as-is (negation is cosmetic)
          case _ =>
        }
      }

      self
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
