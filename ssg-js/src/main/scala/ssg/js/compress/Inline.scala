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
 *     can_inject_args -> canInjectArgs, can_inject_vars -> canInjectVars,
 *     append_var -> appendVar, flatten_args -> flattenArgs, flatten_vars -> flattenVars,
 *     flatten_fn -> flattenFn, dont_inline_lambda_in_loop -> dontInlineLambdaInLoop,
 *     is_const_symbol_short_than_init_value -> isConstSymbolShorterThanInitValue
 *   Convention: Object with methods, pattern matching instead of instanceof chains
 *   Idiom: boundary/break instead of return
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/compress/inline.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 6080510127d6c871ad58ce27c5c6b3045d948baa
 */
package ssg
package js
package compress

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.ast.Annotations
import ssg.js.compress.Common.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.Evaluate.evaluate
import ssg.js.compress.Inference.{ containsThis, hasSideEffects, isCalleePure, isConstantExpression, mayThrow }
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
    self:        AstNode,
    maybeLambda: AstNode | Null
  ): Boolean =
    if (maybeLambda == null) false
    else {
      val node = maybeLambda.nn
      (node.isInstanceOf[AstLambda] || node.isInstanceOf[AstClass]) &&
      isWithinLoop(compressor, self)
    }

  /** Faithful port of terser `TreeWalker.is_within_loop()` (ast.js:3250), reading the LIVE ancestry.
    *
    * Terser's Compressor IS the walker, so `is_within_loop` walks `this.stack` from the node being optimized upward; this port drives the transform on a separate `TreeTransformer`, so we walk the
    * live stack via `liveParent` instead (the Compressor's own stack is empty mid-pass and the previous `compressor.parent(level)` walk always yielded `null`). Starting from `self` as the initial
    * `child`, we ascend the parents: an enclosing `AST_Lambda` ends the search (false); an `AST_IterationStatement` returns true unless the child is a for-loop bit that only runs once (`for`'s
    * `init`, or `for-in`/`for-of`'s `object`).
    */
  private def isWithinLoop(compressor: CompressorLike, self: AstNode): Boolean =
    boundary[Boolean] {
      var child: AstNode = self
      var level = 0
      var node: AstNode | Null = liveParent(compressor, self, level)
      while (node != null) {
        val pn = node.nn
        pn match {
          case _: AstLambda => break(false)
          case _: AstIterationStatement
              // exclude for-loop bits that only run once
              if !(pn.isInstanceOf[AstFor] && (pn.asInstanceOf[AstFor].init != null && (pn.asInstanceOf[AstFor].init.nn.asInstanceOf[AnyRef] eq child.asInstanceOf[AnyRef])))
                && !(pn.isInstanceOf[AstForIn] && (pn.asInstanceOf[AstForIn].obj != null && (pn.asInstanceOf[AstForIn].obj.nn.asInstanceOf[AnyRef] eq child.asInstanceOf[AnyRef]))) =>
            break(true)
          case _ =>
        }
        child = pn
        level += 1
        node = liveParent(compressor, self, level)
      }
      false
    }

  /** Check if a const identifier name is shorter than its init value.
    *
    * For top_retain option: only inline if init value is longer than identifier. Example:
    * ```
    * // top_retain: ["example"]
    * const example = 100
    * ```
    * Returns false because "100" is shorter than "example", so we don't inline.
    */
  private def isConstSymbolShorterThanInitValue(dd: SymbolDef, fixedValue: AstNode | Null): Boolean =
    if (dd.orig.size == 1 && fixedValue != null) {
      val initValueLength  = AstSize.size(fixedValue.nn)
      val identifierLength = dd.name.length
      initValueLength > identifierLength
    } else {
      true
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
      // Don't inline in computed key positions
      if (compressor.inComputedKey()) break(self)

      val d = self.definition()
      if (d == null) break(self)
      val dd = d.nn.asInstanceOf[SymbolDef]

      val parent = compressor.parent()
      val nearestScope: AstScope | Null = findScope(compressor)

      // Get fixed value for this symbol
      val fixed: AstNode | Null = self.fixedValue() match {
        case n: AstNode => n
        case _ => null
      }

      // Handle top_retain option — keep the symbol if it's in the retain list
      // but only if identifier name is shorter than init value
      if (
        compressor.topRetain(dd) &&
        dd.global &&
        isConstSymbolShorterThanInitValue(dd, fixed)
      ) {
        dd.fixed = false
        dd.singleUse = false
        break(self)
      }

      // Don't inline lambdas/classes into loops
      if (dontInlineLambdaInLoop(compressor, self, fixed)) break(self)

      // Calculate single_use status
      var singleUse: Any = dd.singleUse

      // Check if callee is pure or has _NOINLINE annotation
      if (singleUse != false && singleUse != null) {
        parent match {
          case call: AstCall if isCalleePure(call, compressor) || hasAnnotation(call, Annotations.NoInline) =>
            singleUse = false
          case _ =>
        }
      }

      // Don't single-use inline into exports if the value has a name
      if (singleUse != false && singleUse != null) {
        parent match {
          case _: AstExport if fixed != null && fixed.isInstanceOf[AstLambda] && fixed.asInstanceOf[AstLambda].name != null =>
            singleUse = false
          case _ =>
        }
      }

      // If the fixed value itself is an AstNode, check for side effects
      if (singleUse == true && fixed != null) {
        if (hasSideEffects(fixed, compressor) || mayThrow(fixed, compressor)) {
          singleUse = false
        }
      }

      // Cross-scope class: don't inline
      if (fixed.isInstanceOf[AstClass] && (dd.scope.asInstanceOf[AnyRef] ne self.scope.nn.asInstanceOf[AnyRef])) {
        break(self)
      }

      // Single-use Lambda or Class handling
      if (singleUse == true && fixed != null && (fixed.isInstanceOf[AstLambda] || fixed.isInstanceOf[AstClass])) {
        // Check retain_top_func
        if (retainTopFunc(fixed, compressor)) {
          singleUse = false
        } else if (
          (dd.scope.asInstanceOf[AnyRef] ne self.scope.nn.asInstanceOf[AnyRef]) &&
          (dd.escaped == 1 ||
            hasFlag(fixed, INLINED) ||
            withinArrayOrObjectLiteral(compressor) ||
            !compressor.optionBool("reduce_funcs"))
        ) {
          singleUse = false
        } else if (isRecursiveRef(compressor, dd)) {
          singleUse = false
        } else if (
          (dd.scope.asInstanceOf[AnyRef] ne self.scope.nn.asInstanceOf[AnyRef]) ||
          (dd.orig.nonEmpty && dd.orig(0).isInstanceOf[AstSymbolFunarg])
        ) {
          // Cross-scope or funarg: check if constant expression
          val constExpr = isConstantExpression(fixed, self.scope)
          constExpr match {
            case "f" =>
              // Function needs to be marked INLINED up the scope chain
              var scopeNode: AstScope | Null = self.scope
              while (scopeNode != null) {
                scopeNode.nn match {
                  case _: AstDefun =>
                    setFlag(scopeNode.nn, INLINED)
                  case fn: AstNode if isFuncExpr(fn) =>
                    setFlag(fn, INLINED)
                  case _ =>
                }
                scopeNode = scopeNode.nn.parentScope
              }
              singleUse = true
            case true =>
              singleUse = true
            case _ =>
              singleUse = false
          }
        }
      }

      // Additional single-use checks for Lambda/Class
      if (singleUse == true && fixed != null && (fixed.isInstanceOf[AstLambda] || fixed.isInstanceOf[AstClass])) {
        val inSameScope = (dd.scope.asInstanceOf[AnyRef] eq self.scope.nn.asInstanceOf[AnyRef]) &&
          nearestScope != null && !scopeEnclosesVariablesInThisScope(nearestScope.nn, fixed.asInstanceOf[AstScope])

        val isDirectCall = parent match {
          case call: AstCall if call.expression != null && (call.expression.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef]) =>
            nearestScope != null && !scopeEnclosesVariablesInThisScope(nearestScope.nn, fixed.asInstanceOf[AstScope]) &&
            !(fixed.asInstanceOf[AstLambda].name match {
              case sym: AstSymbol if sym.thedef != null => sym.thedef.asInstanceOf[SymbolDef].recursiveRefs > 0
              case _ => false
            })
          case _ => false
        }

        if (!inSameScope && !isDirectCall) {
          singleUse = false
        }
      }

      // Perform the single-use replacement
      if (singleUse == true && fixed != null) {
        var result: AstNode = fixed

        // DefClass -> ClassExpression conversion
        result match {
          case defCls: AstDefClass =>
            setFlag(defCls, SQUEEZED)
            val clsExpr = new AstClassExpression
            clsExpr.start = defCls.start
            clsExpr.end = defCls.end
            clsExpr.name = defCls.name
            clsExpr.superClass = defCls.superClass
            clsExpr.properties = defCls.properties
            clsExpr.variables = defCls.variables
            clsExpr.usesWith = defCls.usesWith
            clsExpr.usesEval = defCls.usesEval
            clsExpr.parentScope = defCls.parentScope
            clsExpr.enclosed = defCls.enclosed
            clsExpr.cname = defCls.cname
            clsExpr.blockScope = defCls.blockScope
            result = clsExpr
          case _ =>
        }

        // Defun -> Function conversion
        result match {
          case defun: AstDefun =>
            setFlag(defun, SQUEEZED)
            val fn = new AstFunction
            fn.start = defun.start
            fn.end = defun.end
            fn.name = defun.name
            fn.argnames = defun.argnames
            fn.usesArguments = defun.usesArguments
            fn.isGenerator = defun.isGenerator
            fn.isAsync = defun.isAsync
            fn.body = defun.body
            fn.variables = defun.variables
            fn.usesWith = defun.usesWith
            fn.usesEval = defun.usesEval
            fn.parentScope = defun.parentScope
            fn.enclosed = defun.enclosed
            fn.cname = defun.cname
            fn.blockScope = defun.blockScope
            result = fn
          case _ =>
        }

        // Recursive reference rewriting
        if (dd.recursiveRefs > 0) {
          result match {
            case lambda: AstLambda if lambda.name != null && lambda.name.nn.isInstanceOf[AstSymbolDefun] =>
              val defunName = lambda.name.nn.asInstanceOf[AstSymbolDefun]
              val defunDef  = defunName.thedef.asInstanceOf[SymbolDef]

              // Check if a lambda_def already exists
              val existingLambdaDef = lambda.variables.get(defunName.name)
              val (lambdaDef, _)    = existingLambdaDef match {
                case Some(ld) if ld.asInstanceOf[SymbolDef].orig.nonEmpty && ld.asInstanceOf[SymbolDef].orig(0).isInstanceOf[AstSymbolLambda] =>
                  (ld.asInstanceOf[SymbolDef], ld.asInstanceOf[SymbolDef].orig(0).asInstanceOf[AstSymbolLambda])
                case _ =>
                  // Create new SymbolLambda
                  val newName = new AstSymbolLambda
                  newName.start = defunName.start
                  newName.end = defunName.end
                  newName.name = defunName.name
                  newName.scope = lambda
                  lambda.name = newName
                  val newDef = new SymbolDef(lambda, newName)
                  lambda.variables(newName.name) = newDef
                  (newDef, newName)
              }

              // Rewrite all recursive references
              walk(
                lambda,
                (node, _) => {
                  node match {
                    case ref: AstSymbolRef if ref.thedef != null && (ref.thedef.asInstanceOf[AnyRef] eq defunDef.asInstanceOf[AnyRef]) =>
                      ref.thedef = lambdaDef
                      lambdaDef.references.addOne(ref)
                    case _ =>
                  }
                  null // continue walking
                }
              )
            case _ =>
          }
        }

        // Clone to new scope if needed
        if ((result.isInstanceOf[AstLambda] || result.isInstanceOf[AstClass]) && nearestScope != null) {
          val resultScope = result.asInstanceOf[AstScope]
          if (resultScope.parentScope.asInstanceOf[AnyRef] ne nearestScope.nn.asInstanceOf[AnyRef]) {
            result = cloneNode(result)
            addChildScope(nearestScope.nn, result.asInstanceOf[AstScope])
          }
        }

        // inline.js:277 — return fixed.optimize(compressor)
        break(compressor.optimizeNode(result))
      }

      // Multi-use: attempt constant replacement
      if (fixed != null) {
        var replace: AstNode | Null = null

        fixed match {
          case _: AstThis =>
            // Replace AST_This if all references are in the same scope
            if (
              !dd.orig.headOption.exists(_.isInstanceOf[AstSymbolFunarg]) &&
              dd.references.forall(ref => dd.scope.asInstanceOf[AnyRef] eq ref.scope.nn.asInstanceOf[AnyRef])
            ) {
              replace = fixed
            }

          case _ =>
            // Try to evaluate the fixed value
            val ev = evaluate(fixed, compressor)
            if (
              (ev.asInstanceOf[AnyRef] ne fixed.asInstanceOf[AnyRef]) &&
              (compressor.optionBool("unsafe_regexp") || !ev.isInstanceOf[RegExpValue])
            ) {
              replace = makeNodeFromConstant(ev, fixed)
            }
        }

        if (replace != null) {
          val nameLength  = AstSize.size(self)
          val replaceSize = AstSize.size(replace.nn)

          // Calculate overhead if the variable could be dropped
          var overhead = 0
          if (compressor.optionBool("unused") && !compressor.exposed(dd)) {
            val denominator = dd.references.size - dd.assignments
            if (denominator > 0) {
              overhead = (nameLength + 2 + AstSize.size(fixed)) / denominator
            }
          }

          if (replaceSize <= nameLength + overhead) {
            break(replace.nn)
          }
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
    *   4. Body flattening: complex function bodies inlined when safe
    *   5. IIFE negation
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
      // Don't inline in computed key positions
      if (compressor.inComputedKey()) break(self)

      val exp = self.expression
      if (exp == null) break(self)

      var fn: AstNode = exp.nn

      val simpleArgs = self.args.forall(!_.isInstanceOf[AstExpansion])

      // Handle reduce_vars: look through symbol refs to find the actual function
      if (compressor.optionBool("reduce_vars") && fn.isInstanceOf[AstSymbolRef] && !hasAnnotation(self, Annotations.NoInline)) {
        val fnRef    = fn.asInstanceOf[AstSymbolRef]
        val fixedVal = fnRef.fixedValue()

        fixedVal match {
          case fixedFn: AstNode =>
            // Check retain_top_func
            if (retainTopFunc(fixedFn, compressor)) {
              break(self)
            }

            // Check toplevel.funcs restriction
            val defOpt = fnRef.definition()
            if (defOpt != null && !compressor.toplevel.funcs && defOpt.nn.asInstanceOf[SymbolDef].global) {
              break(self)
            }

            fn = fixedFn
          case _ =>
        }
      }

      // Check dont_inline_lambda_in_loop unless _INLINE annotation present
      if (dontInlineLambdaInLoop(compressor, self, fn) && !hasAnnotation(self, Annotations.Inline)) {
        break(self)
      }

      val isFunc = fn.isInstanceOf[AstLambda]
      val stat: AstNode | Null = if (isFunc) {
        val lambda = fn.asInstanceOf[AstLambda]
        if (lambda.body.nonEmpty) lambda.body(0) else null
      } else null

      val isRegularFunc = isFunc && {
        val lambda = fn.asInstanceOf[AstLambda]
        !lambda.isGenerator && !lambda.isAsync
      }

      val canInline = isRegularFunc &&
        compressor.optionBool("inline") &&
        !isCalleePure(self, compressor)

      // Pattern: (function(){ return X; })(...) -> (args..., X)
      if (canInline && stat.isInstanceOf[AstReturn]) {
        val ret = stat.asInstanceOf[AstReturn]
        var returned: AstNode | Null = ret.value
        if (returned == null || isConstantExpression(returned.nn) == true) {
          if (returned != null) {
            returned = cloneNode(returned.nn)
          } else {
            returned = makeVoid0(self)
          }
          val args = ArrayBuffer.from(self.args)
          args.addOne(returned.nn)
          // inline.js:363 — make_sequence(self, args).optimize(compressor)
          break(compressor.optimizeNode(makeSequence(self, args)))
        }

        // Identity function: (function(x){ return x; })(arg) -> arg
        val lambda = fn.asInstanceOf[AstLambda]
        if (
          lambda.argnames.size == 1 &&
          lambda.argnames(0).isInstanceOf[AstSymbolFunarg] &&
          self.args.size < 2 &&
          !self.args.headOption.exists(_.isInstanceOf[AstExpansion]) &&
          returned.isInstanceOf[AstSymbolRef] &&
          returned.asInstanceOf[AstSymbolRef].name == lambda.argnames(0).asInstanceOf[AstSymbol].name
        ) {
          // inline.js:376 — (self.args[0] || make_void_0()).optimize(compressor)
          val replacement: AstNode =
            compressor.optimizeNode(self.args.headOption.getOrElse(makeVoid0(self)))

          // Check if we need to wrap in (0, replacement) to maintain this binding
          // inline.js:378-383 — `(parent = compressor.parent()) instanceof AST_Call`.
          // The Compressor's own walker stack is empty during a pass, so read the
          // live transformer ancestry (see `liveParent` / Compressor.activeWalker).
          replacement match {
            case _: AstPropAccess =>
              val parentNode = liveParent(compressor, self)
              parentNode match {
                case parentCall: AstCall if parentCall.expression != null && (parentCall.expression.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef]) =>
                  // Identity function was being used to remove `this`:
                  // id(bag.no_this)(...) -> (0, bag.no_this)(...)
                  break(
                    makeSequence(self,
                                 ArrayBuffer(
                                   {
                                     val zero = new AstNumber
                                     zero.start = self.start
                                     zero.end = self.end
                                     zero.value = 0.0
                                     zero
                                   },
                                   replacement
                                 )
                    )
                  )
                case _ =>
              }
            case _ =>
          }
          // Replace call with first argument or void 0
          break(replacement)
        }
      }

      // Body flattening path (inline level >= 3)
      if (canInline) {
        val lambda = fn.asInstanceOf[AstLambda]

        // Variables for can_inject_symbols
        var scopeVar: AstScope | Null               = null
        var inLoop:   ArrayBuffer[SymbolDef] | Null = null
        var level = -1

        // Helper to get the return value from a statement
        def returnValue(statNode: AstNode | Null): AstNode | Null =
          if (statNode == null) makeVoid0(self)
          else
            statNode match {
              case ret: AstReturn =>
                if (ret.value == null) makeVoid0(self)
                else cloneNode(ret.value.nn)
              case simple: AstSimpleStatement =>
                val prefix = new AstUnaryPrefix
                prefix.start = simple.start
                prefix.end = simple.end
                prefix.operator = "void"
                prefix.expression = cloneNode(simple.body.nn)
                prefix
              case _ => null
            }

        // Check if function body can be flattened
        def canFlattenBody(statNode: AstNode | Null): AstNode | Null = {
          val bodyArr     = lambda.body
          val len         = bodyArr.size
          val inlineLevel = compressor.option("inline") match {
            case n: Int => n
            case _ => 1
          }
          if (inlineLevel < 3) {
            if (len == 1) returnValue(statNode) else null
          } else {
            // For inline level >= 3, allow multiple var statements before the return
            var lastNonVar: AstNode | Null = null
            var i = 0
            while (i < len) {
              val line = bodyArr(i)
              line match {
                case varNode: AstVar =>
                  // All var definitions after the statement must have no value
                  if (lastNonVar != null && !varNode.definitions.forall(d => d.asInstanceOf[AstVarDef].value == null)) {
                    return null // @nowarn -- early return in helper function
                  }
                case _: AstEmptyStatement =>
                // Skip empty statements
                case _ if lastNonVar != null =>
                  // More than one non-var statement
                  return null // @nowarn -- early return in helper function
                case _ =>
                  lastNonVar = line
              }
              i += 1
            }
            returnValue(lastNonVar)
          }
        }

        // Check if arguments can be injected
        def canInjectArgs(blockScoped: mutable.Set[String], safeToInject: Boolean): Boolean =
          boundary[Boolean] {
            var i   = 0
            val len = lambda.argnames.size
            while (i < len) {
              val arg = lambda.argnames(i)
              arg match {
                case da: AstDefaultAssign =>
                  if (!hasFlag(da.left.nn, UNUSED)) break(false)
                case _:      AstDestructuring => break(false)
                case expand: AstExpansion     =>
                  if (!hasFlag(expand.expression.nn, UNUSED)) break(false)
                case sym: AstSymbol =>
                  if (!hasFlag(sym, UNUSED)) {
                    if (
                      !safeToInject ||
                      blockScoped.contains(sym.name) ||
                      identifierAtom.contains(sym.name) ||
                      (scopeVar != null && ScopeAnalysis.conflictingDef(scopeVar.nn, sym.name))
                    ) {
                      break(false)
                    }
                    if (inLoop != null) {
                      val d = sym.thedef
                      if (d != null) inLoop.nn.addOne(d.asInstanceOf[SymbolDef])
                    }
                  }
                case _ =>
              }
              i += 1
            }
            true
          }

        // Check if local vars can be injected
        def canInjectVars(blockScoped: mutable.Set[String], safeToInject: Boolean): Boolean =
          boundary[Boolean] {
            val len = lambda.body.size
            var i   = 0
            while (i < len) {
              val statNode = lambda.body(i)
              statNode match {
                case varNode: AstVar =>
                  if (!safeToInject) break(false)
                  var j = varNode.definitions.size
                  while ({ j -= 1; j >= 0 }) {
                    val nameNode = varNode.definitions(j).asInstanceOf[AstVarDef].name
                    nameNode match {
                      case _:   AstDestructuring => break(false)
                      case sym: AstSymbol        =>
                        if (
                          blockScoped.contains(sym.name) ||
                          identifierAtom.contains(sym.name) ||
                          (scopeVar != null && ScopeAnalysis.conflictingDef(scopeVar.nn, sym.name))
                        ) {
                          break(false)
                        }
                        if (inLoop != null) {
                          val d = sym.thedef
                          if (d != null) inLoop.nn.addOne(d.asInstanceOf[SymbolDef])
                        }
                      case _ =>
                    }
                  }
                case _ =>
              }
              i += 1
            }
            true
          }

        // Check if symbols can be injected (combines args + vars checks)
        def canInjectSymbols(): Boolean =
          boundary[Boolean] {
            val blockScoped = mutable.Set.empty[String]
            level = -1
            var currentScope: AstNode | Null = null

            // Walk up the LIVE parent chain collecting block-scoped variables — terser
            // `can_inject_symbols` (inline.js:558-586). A do-while: each iteration processes
            // `scope = compressor.parent(++level)` (including the terminal `AST_Scope`) and then
            // checks `!(scope instanceof AST_Scope)`. The Compressor's own stack is empty mid-pass,
            // so we read the live ancestry of `self` via `liveParent`.
            var continueLoop = true
            while (continueLoop) {
              level += 1
              currentScope = liveParent(compressor, self, level)
              // inline.js:560 — `scope = compressor.parent(++level)`; the do-while dereferences
              // `scope.is_block_scope()` unconditionally, so the live walk must reach a scope.
              if (currentScope == null) break(false)
              val scopeNode = currentScope.nn
              // inline.js:562-568 — `if (scope.is_block_scope() && scope.block_scope) {...}`.
              // Independent of the Catch/Iteration/SymbolRef chain below: an
              // AST_IterationStatement is both a block scope AND triggers `in_loop = []`.
              scopeNode match {
                case b: AstBlock if ScopeAnalysis.isBlockScope(scopeNode) && b.blockScope != null =>
                  b.blockScope.nn.variables.foreach { case (name, _) =>
                    blockScoped.add(name)
                  }
                case it: AstIterationStatement if ScopeAnalysis.isBlockScope(scopeNode) && it.blockScope != null =>
                  it.blockScope.nn.variables.foreach { case (name, _) =>
                    blockScoped.add(name)
                  }
                case _ =>
              }
              // inline.js:569-578 — Catch / IterationStatement / SymbolRef chain.
              scopeNode match {
                case catchNode: AstCatch =>
                  if (catchNode.argname != null) {
                    catchNode.argname.nn match {
                      case sym: AstSymbol => blockScoped.add(sym.name)
                      case _ =>
                    }
                  }
                case _: AstIterationStatement =>
                  inLoop = ArrayBuffer.empty
                case ref: AstSymbolRef =>
                  if (ref.fixedValue().isInstanceOf[AstScope]) break(false)
                case _ =>
              }
              // do-while: terminate once we reach an AST_Scope
              if (scopeNode.isInstanceOf[AstScope]) continueLoop = false
            }

            scopeVar = currentScope match {
              case s: AstScope => s
              case _ => null
            }

            val safeToInject = scopeVar match {
              case _: AstToplevel => compressor.toplevel.vars
              case _ => true
            }

            val inlineLevel = compressor.option("inline") match {
              case n: Int => n
              case _ => 1
            }

            if (!canInjectVars(blockScoped, inlineLevel >= 3 && safeToInject)) break(false)
            if (!canInjectArgs(blockScoped, inlineLevel >= 2 && safeToInject)) break(false)

            // Check if in_loop defs are reachable
            if (inLoop != null && inLoop.nn.nonEmpty && isReachable(lambda, inLoop.nn.asInstanceOf[ArrayBuffer[Any]])) {
              break(false)
            }

            true
          }

        // Append a variable to the target scope
        def appendVar(decls: ArrayBuffer[AstVarDef], expressions: ArrayBuffer[AstNode], name: AstSymbol, value: AstNode | Null): Unit = {
          val d = name.thedef.asInstanceOf[SymbolDef]

          // Check if name already exists in scope
          val alreadyAppended = scopeVar != null && scopeVar.nn.variables.contains(name.name)

          if (!alreadyAppended && scopeVar != null) {
            scopeVar.nn.variables(name.name) = d
            scopeVar.nn.enclosed.addOne(d)
            val varDef = new AstVarDef
            varDef.start = name.start
            varDef.end = name.end
            varDef.name = name
            varDef.value = null
            decls.addOne(varDef)
          }

          val sym = new AstSymbolRef
          sym.start = name.start
          sym.end = name.end
          sym.name = name.name
          sym.thedef = d
          sym.scope = scopeVar
          d.references.addOne(sym)

          if (value != null) {
            val assign = new AstAssign
            assign.start = self.start
            assign.end = self.end
            assign.operator = "="
            assign.logical = false
            assign.left = sym
            assign.right = cloneNode(value.nn)
            expressions.addOne(assign)
          }
        }

        // Flatten function arguments into var declarations and assignments
        def flattenArgs(decls: ArrayBuffer[AstVarDef], expressions: ArrayBuffer[AstNode]): Unit = {
          val len = lambda.argnames.size

          // First, push excess arguments (beyond the parameter count)
          var i = self.args.size
          while ({ i -= 1; i >= len })
            expressions.addOne(self.args(i))

          // Then process parameters in reverse order
          i = len
          while ({ i -= 1; i >= 0 }) {
            val name = lambda.argnames(i)
            val value: AstNode | Null = if (i < self.args.size) self.args(i) else null

            name match {
              case sym: AstSymbol if hasFlag(sym, UNUSED) || sym.name.isEmpty || (scopeVar != null && ScopeAnalysis.conflictingDef(scopeVar.nn, sym.name)) =>
                if (value != null) expressions.addOne(value)
              case sym: AstSymbol =>
                val symbol = new AstSymbolVar
                symbol.start = sym.start
                symbol.end = sym.end
                symbol.name = sym.name
                symbol.thedef = sym.thedef
                symbol.scope = sym.scope

                // Add to orig
                if (sym.thedef != null) {
                  sym.thedef.asInstanceOf[SymbolDef].orig.addOne(symbol)
                }

                val valueToUse = if (value == null && inLoop != null) makeVoid0(self) else value
                appendVar(decls, expressions, symbol, valueToUse)
              case _ =>
                if (value != null) expressions.addOne(value)
            }
          }

          // Reverse to maintain correct order
          val declsReversed = decls.reverse
          decls.clear()
          decls.addAll(declsReversed)
          val exprReversed = expressions.reverse
          expressions.clear()
          expressions.addAll(exprReversed)
        }

        // Flatten function local variables
        def flattenVars(decls: ArrayBuffer[AstVarDef], expressions: ArrayBuffer[AstNode]): Unit = {
          val pos   = expressions.size
          var i     = 0
          val lines = lambda.body.size
          while (i < lines) {
            val statNode = lambda.body(i)
            statNode match {
              case varNode: AstVar =>
                var j    = 0
                val defs = varNode.definitions.size
                while (j < defs) {
                  val varDef      = varNode.definitions(j)
                  val varDefTyped = varDef.asInstanceOf[AstVarDef]
                  varDefTyped.name match {
                    case nameNode: AstSymbol =>
                      appendVar(decls, expressions, nameNode, varDefTyped.value)
                      // If in loop and var is not an argname, add initialization
                      if (
                        inLoop != null && !lambda.argnames.exists {
                          case sym: AstSymbol => sym.name == nameNode.name
                          case _ => false
                        }
                      ) {
                        val d = lambda.variables.get(nameNode.name)
                        d match {
                          case Some(defObj) =>
                            val sym = new AstSymbolRef
                            sym.start = nameNode.start
                            sym.end = nameNode.end
                            sym.name = nameNode.name
                            sym.thedef = defObj
                            sym.scope = scopeVar
                            defObj.asInstanceOf[SymbolDef].references.addOne(sym)
                            val assign = new AstAssign
                            assign.start = varDef.start
                            assign.end = varDef.end
                            assign.operator = "="
                            assign.logical = false
                            assign.left = sym
                            assign.right = makeVoid0(nameNode)
                            // Insert at pos to maintain order
                            expressions.insert(pos, assign)
                          case None =>
                        }
                      }
                    case _ =>
                  }
                  j += 1
                }
              case _ =>
            }
            i += 1
          }
        }

        // Main flatten entry point
        def flattenFn(returnedValue: AstNode): ArrayBuffer[AstNode] = {
          val decls       = ArrayBuffer.empty[AstVarDef]
          val expressions = ArrayBuffer.empty[AstNode]
          flattenArgs(decls, expressions)
          flattenVars(decls, expressions)
          expressions.addOne(returnedValue)

          // Insert var declaration into scope body if needed
          if (decls.nonEmpty && scopeVar != null) {
            // inline.js:667 — `scope.body.indexOf(compressor.parent(level - 1))`.
            // `level` is where the scope was found in `canInjectSymbols`, so
            // `parent(level - 1)` is the scope's child statement; read the live ancestry.
            val parentIdx = liveParent(compressor, self, level - 1)
            // AstScope extends AstBlock, so scopeVar is always an AstBlock
            val scope     = scopeVar.nn.asInstanceOf[AstBlock]
            val insertIdx = scope.body.indexOf(parentIdx) + 1
            val varDecl   = new AstVar
            varDecl.start = lambda.start
            varDecl.end = lambda.end
            varDecl.definitions = decls.asInstanceOf[ArrayBuffer[AstNode]]
            scope.body.insert(insertIdx, varDecl)
          }

          // Clone all expressions
          expressions.map(cloneNode)
        }

        // Check if we should inline in DefaultAssign — terser `in_default_assign` (inline.js:421-432).
        //
        // Due to the fact function parameters have their own scope which can't use `var something`
        // in the function body within, we simply don't inline into DefaultAssign. Walks the LIVE
        // ancestry of `self` via `liveParent` (terser's Compressor IS the walker; this port drives
        // the transform on a separate `TreeTransformer`, so the Compressor's own stack is empty
        // mid-pass and a `compressor.parent(i)` walk always yielded `null`).
        def inDefaultAssign(): Boolean =
          boundary[Boolean] {
            var i = 0
            var p: AstNode | Null = liveParent(compressor, self, i)
            while (p != null) {
              p.nn match {
                case _: AstDefaultAssign => break(true)
                case _: AstBlock         => break(false)
                case _ =>
              }
              i += 1
              p = liveParent(compressor, self, i)
            }
            false
          }

        // Now perform the body flattening check
        val returnedValue = canFlattenBody(stat)
        val nearestScope: AstScope | Null = findScopeLive(compressor, self)

        if (
          simpleArgs &&
          !lambda.usesArguments &&
          // inline.js:407 — `!(compressor.parent() instanceof AST_Class)`; read the live ancestry.
          !liveParent(compressor, self).isInstanceOf[AstClass] &&
          (lambda.name == null || !lambda.isInstanceOf[AstFunction]) &&
          returnedValue != null &&
          ((exp.nn.asInstanceOf[AnyRef] eq fn.asInstanceOf[AnyRef]) ||
            hasAnnotation(self, Annotations.Inline) ||
            (compressor.optionBool("unused") && {
              val defOpt = exp.nn match {
                case ref: AstSymbolRef => ref.definition()
                case _ => null
              }
              defOpt != null && {
                val dd = defOpt.nn.asInstanceOf[SymbolDef]
                dd.references.size == 1 && !isRecursiveRef(compressor, dd) &&
                isConstantExpression(lambda, exp.nn.asInstanceOf[AstSymbolRef].scope) == true
              }
            })) &&
          !hasAnnotation(self, Annotations.Pure | Annotations.NoInline) &&
          !containsThis(lambda) &&
          canInjectSymbols() &&
          nearestScope != null &&
          !scopeEnclosesVariablesInThisScope(nearestScope.nn, lambda) &&
          !inDefaultAssign() &&
          (scopeVar match { case _: AstClass => false; case _ => true })
        ) {
          setFlag(lambda, SQUEEZED)
          addChildScope(nearestScope.nn, lambda)
          // inline.js:437 — make_sequence(self, flatten_fn(returned_value)).optimize(compressor)
          break(compressor.optimizeNode(makeSequence(self, flattenFn(returnedValue.nn))))
        }
      }

      // Force inline with @__INLINE__ annotation
      if (canInline && hasAnnotation(self, Annotations.Inline)) {
        val lambda = fn.asInstanceOf[AstLambda]
        setFlag(lambda, SQUEEZED)

        // Convert Defun to Function if needed
        val fnCopy: AstLambda = lambda match {
          case _: AstDefun =>
            val newFn = new AstFunction
            newFn.start = lambda.start
            newFn.end = lambda.end
            newFn.name = lambda.name
            newFn.argnames = lambda.argnames
            newFn.usesArguments = lambda.usesArguments
            newFn.isGenerator = lambda.isGenerator
            newFn.isAsync = lambda.isAsync
            newFn.body = lambda.body
            newFn.variables = lambda.variables
            newFn.usesWith = lambda.usesWith
            newFn.usesEval = lambda.usesEval
            newFn.parentScope = lambda.parentScope
            newFn.enclosed = lambda.enclosed
            newFn.cname = lambda.cname
            newFn.blockScope = lambda.blockScope
            newFn
          case _ =>
            cloneNode(lambda).asInstanceOf[AstLambda]
        }

        // Figure out scope for the cloned function
        val nearestScope = findScopeLive(compressor, self)
        if (nearestScope != null) {
          val toplevelNode = compressor match {
            case c: Compressor => c.getToplevel
            case _ => null
          }
          ScopeAnalysis.figureOutScope(
            fnCopy,
            ssg.js.scope.ScopeOptions.Defaults,
            nearestScope,
            toplevelNode
          )
        }

        val newCall = new AstCall
        newCall.start = self.start
        newCall.end = self.end
        newCall.expression = fnCopy
        newCall.args = self.args
        // inline.js:450 — make_node(AST_Call, ...).optimize(compressor)
        break(compressor.optimizeNode(newCall))
      }

      // Empty body optimization: (function(){})(...args) -> (...args, void 0)
      val canDropThisCall = isRegularFunc && compressor.optionBool("side_effects") &&
        fn.asInstanceOf[AstLambda].body.forall(isEmpty)
      if (canDropThisCall) {
        val args = ArrayBuffer.from(self.args)
        args.addOne(makeVoid0(self))
        // inline.js:459 — make_sequence(self, args).optimize(compressor)
        break(compressor.optimizeNode(makeSequence(self, args)))
      }

      // IIFE negation: !function(){}() is shorter than (function(){})()
      // inline.js:462-464 — `compressor.parent() instanceof AST_SimpleStatement`.
      // The Compressor's own walker stack is empty during a pass, so read the
      // live transformer ancestry (see `liveParent` / Compressor.activeWalker),
      // mirroring the identity-call this-guard at inline.js:378-383 above.
      if (
        compressor.optionBool("negate_iife") &&
        liveParent(compressor, self).isInstanceOf[AstSimpleStatement] &&
        isIifeCall(self)
      ) {
        // Negate the call to avoid wrapping parens
        break(negate(self, firstInStatement = true))
      }

      // Try to evaluate the entire call
      val ev = evaluate(self, compressor)
      if (ev.asInstanceOf[AnyRef] ne self.asInstanceOf[AnyRef]) {
        // inline.js:470 — ev = make_node_from_constant(ev, self).optimize(compressor)
        val evNode = compressor.optimizeNode(makeNodeFromConstant(ev, self))
        break(bestOfExpression(evNode, self))
      }

      self
    }

  // -----------------------------------------------------------------------
  // Helper functions
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

  /** Check if a node has a given annotation flag. */
  private def hasAnnotation(node: AstNode, flag: Int): Boolean =
    (node.flags & flag) != 0

  /** Check if a function should be retained at top level. */
  private def retainTopFunc(fn: AstNode, compressor: CompressorLike): Boolean =
    fn.isInstanceOf[AstDefun] &&
      hasFlag(fn, TOP) &&
      (fn match {
        case defun: AstDefun =>
          defun.name match {
            case sym: AstSymbol if sym.thedef != null => compressor.topRetain(sym.thedef)
            case _ => false
          }
        case _ => false
      })

  /** Check if a ref refers to the name of a function/class it's defined within. */
  private def isRecursiveRef(compressor: CompressorLike, theDef: SymbolDef): Boolean =
    boundary[Boolean] {
      var i = 0
      var node: AstNode | Null = compressor.parent(i)
      while (node != null) {
        node.nn match {
          case lambda: AstLambda if lambda.name != null =>
            lambda.name.nn match {
              case sym: AstSymbol if sym.thedef != null && (sym.thedef.asInstanceOf[AnyRef] eq theDef.asInstanceOf[AnyRef]) =>
                break(true)
              case _ =>
            }
          case cls: AstClass if cls.name != null =>
            cls.name.nn match {
              case sym: AstSymbol if sym.thedef != null && (sym.thedef.asInstanceOf[AnyRef] eq theDef.asInstanceOf[AnyRef]) =>
                break(true)
              case _ =>
            }
          case _ =>
        }
        i += 1
        node = compressor.parent(i)
      }
      false
    }

  /** Find the nearest enclosing scope — terser `compressor.find_scope()` (inline.js:173, 419).
    *
    * Walks the LIVE ancestry of `self` (the node being optimized) via `liveParent`. Terser's `find_scope()` walks `compressor.parent(i++)` while the Compressor IS the walker; this port drives the
    * transform on a separate `TreeTransformer`, so the Compressor's own stack is empty mid-pass and a `compressor.parent(i)` walk would always yield `null`. Reading the live stack restores the
    * inline-flatten sub-path (inline.js:407-440) whose `nearest_scope` guard otherwise always failed.
    */
  private def findScopeLive(compressor: CompressorLike, self: AstNode): AstScope | Null = {
    var i = 0
    var node: AstNode | Null = liveParent(compressor, self, i)
    while (node != null) {
      node.nn match {
        case t: AstToplevel                      => return t // @nowarn -- interop boundary
        case l: AstLambda                        => return l // @nowarn -- interop boundary
        case b: AstBlock if b.blockScope != null => return b.blockScope // @nowarn -- interop boundary
        case _ =>
      }
      i += 1
      node = liveParent(compressor, self, i)
    }
    null
  }

  /** Find the nearest enclosing scope via the compressor's own ancestry stack (the pre-ISS-1166 behavior). */
  private def findScope(compressor: CompressorLike): AstScope | Null = {
    var i = 0
    var node: AstNode | Null = compressor.parent(i)
    while (node != null) {
      node.nn match {
        case t: AstToplevel                      => return t // @nowarn -- interop boundary
        case l: AstLambda                        => return l // @nowarn -- interop boundary
        case b: AstBlock if b.blockScope != null => return b.blockScope // @nowarn -- interop boundary
        case _ =>
      }
      i += 1
      node = compressor.parent(i)
    }
    null
  }

  /** The live parent of `self` during a compress pass — terser's `compressor.parent()` (inline.js:381).
    *
    * Terser's Compressor *is* the TreeWalker driving the transform, so `compressor.parent()` reads the live ancestry. This port runs the transform on a separate `TreeTransformer`, leaving the
    * Compressor's own stack empty during a pass; the live ancestry is exposed via `Compressor.activeWalker` (Compressor.scala:87, set at the pass loop). We locate `self` (the node currently being
    * optimized) in that live stack and return its immediate parent, exactly as `in_computed_key` walks the active stack (Compressor.scala:95-117). Falls back to the compressor's own `parent()` when
    * no active walker is present (e.g. unit calls outside a pass), preserving the previous behavior.
    */
  private def liveParent(compressor: CompressorLike, self: AstNode): AstNode | Null =
    compressor match {
      case c: Compressor => c.liveParent(self)
      case _ => compressor.parent()
    }

  /** The live ancestor of `self` `n` levels up — terser's `compressor.parent(n)` (inline.js).
    *
    * Same rationale as `liveParent`: terser's Compressor is the walker, so `compressor.parent(n)` reads the live ancestry of the node being optimized. This port drives the transform on a separate
    * `TreeTransformer`, so we walk the live stack via `Compressor.liveParent(self, n)` (Compressor.scala). `n == 0` is the immediate parent. Falls back to the compressor's own `parent(n)` when no
    * active walker is present, preserving the previous behavior. Used by the live-ancestry guards `isWithinLoop` (is_within_loop), `inDefaultAssign` (inline.js:421-432) and `findScope` (find_scope /
    * inline.js:419).
    */
  private def liveParent(compressor: CompressorLike, self: AstNode, n: Int): AstNode | Null =
    compressor match {
      case c: Compressor => c.liveParent(self, n)
      case _ => compressor.parent(n)
    }

  /** Add a child scope to a parent scope. */
  private def addChildScope(parent: AstScope, child: AstScope): Unit = {
    child.parentScope = parent
    // Add enclosed defs to parent
    for (enclosedAny <- child.enclosed) {
      val enclosed = enclosedAny.asInstanceOf[SymbolDef]
      if (!child.variables.contains(enclosed.name)) {
        if (!parent.enclosed.exists(e => e.asInstanceOf[AnyRef] eq enclosed.asInstanceOf[AnyRef])) {
          parent.enclosed.addOne(enclosed)
        }
      }
    }
  }

  /** Clone an AST node (shallow clone with mutable collections copied). */
  private def cloneNode(node: AstNode): AstNode =
    // Simple implementation: create a new node of the same type and copy fields
    // In practice, this should use the actual clone method if available
    node match {
      case num: AstNumber =>
        val copy = new AstNumber
        copy.start = num.start
        copy.end = num.end
        copy.value = num.value
        copy.flags = num.flags
        copy
      case str: AstString =>
        val copy = new AstString
        copy.start = str.start
        copy.end = str.end
        copy.value = str.value
        copy.flags = str.flags
        copy
      case _: AstTrue =>
        val copy = new AstTrue
        copy.start = node.start
        copy.end = node.end
        copy.flags = node.flags
        copy
      case _: AstFalse =>
        val copy = new AstFalse
        copy.start = node.start
        copy.end = node.end
        copy.flags = node.flags
        copy
      case _: AstNull =>
        val copy = new AstNull
        copy.start = node.start
        copy.end = node.end
        copy.flags = node.flags
        copy
      case _: AstUndefined =>
        val copy = new AstUndefined
        copy.start = node.start
        copy.end = node.end
        copy.flags = node.flags
        copy
      case ref: AstSymbolRef =>
        val copy = new AstSymbolRef
        copy.start = ref.start
        copy.end = ref.end
        copy.name = ref.name
        copy.thedef = ref.thedef
        copy.scope = ref.scope
        copy.flags = ref.flags
        copy
      case _ =>
        // For complex nodes, just return as-is since deep cloning is complex
        // In a full implementation, we'd use a visitor pattern or reflection
        node
    }

  /** Negate a node (for IIFE negation). */
  @annotation.nowarn("msg=unused private member") // matches original API signature
  private def negate(node: AstNode, firstInStatement: Boolean = false): AstNode = {
    val neg = new AstUnaryPrefix
    neg.start = node.start
    neg.end = node.end
    neg.operator = "!"
    neg.expression = node
    neg
  }
}
