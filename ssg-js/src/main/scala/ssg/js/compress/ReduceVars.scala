/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data-flow analysis: track variable assignments, uses, and escapes.
 *
 * Called before optimization passes to walk the AST in execution order and
 * annotate each variable definition (SymbolDef) with information about how
 * it is used: fixed value, assignment count, single_use, escaped, etc.
 * Other passes (constant folding, inlining, dead code elimination) consume
 * this information.
 *
 * Ported from: terser lib/compress/reduce-vars.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*, def_reduce_vars -> reduceNode,
 *     reset_def -> resetDef, reset_variables -> resetVariables,
 *     safe_to_read -> safeToRead, safe_to_assign -> safeToAssign,
 *     mark_escaped -> markEscaped, mark_lambda -> markLambda,
 *     handle_defined_after_hoist -> handleDefinedAfterHoist,
 *     ref_once -> refOnce, is_immutable -> isImmutable
 *   Convention: ReduceVarsWalker class instead of patching TreeWalker prototype
 *   Idiom: boundary/break instead of return, mutable.Map for safe_ids
 *   Gap: 388 LOC vs upstream 865 LOC (~45%). SymbolDef tracking incomplete
 *     (TODOs at lines 288-369). Without proper fixed-value/escape annotations,
 *     downstream passes (Evaluate, Inline, DropUnused) cannot fire safely.
 *     See ISS-032. docs/architecture/terser-port.md.
 *   Audited: 2026-04-07 (major_issues)
 */
package ssg
package js
package compress

import scala.collection.mutable

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.Inference.lazyOp

/** Data-flow analysis for variable tracking.
  *
  * Walks the AST in execution order before optimization passes begin. For each variable definition, tracks:
  *   - `fixed`: the value assigned at the definition site (if deterministic)
  *   - `assignments`: number of assignments to this variable
  *   - `single_use`: whether the variable is referenced exactly once
  *   - `escaped`: whether the variable's value can escape the use site
  *   - `direct_access`: whether the variable is accessed dynamically
  *   - `recursive_refs`: count of recursive references (function calling itself)
  */
object ReduceVars {

  // -----------------------------------------------------------------------
  // Walker state
  // -----------------------------------------------------------------------

  /** State maintained during the reduce_vars tree walk. */
  class ReduceVarsState {

    /** Chain of safe-id maps (linked by prototype-like nesting). Each entry maps a definition ID to whether it is safe to read.
      */
    var safeIds: mutable.Map[Int, Boolean] = mutable.Map.empty

    /** Stack of safe-id maps for push/pop. */
    private var _safeIdsStack: List[mutable.Map[Int, Boolean]] = Nil

    /** Map from definition ID to safe-ids at point of first encounter. */
    val defsToSafeIds: mutable.Map[Int, mutable.Map[Int, Boolean]] = mutable.Map.empty

    /** Map from definition ID to the loop node containing it. */
    val loopIds: mutable.Map[Int, AstNode | Null] = mutable.Map.empty

    /** The current innermost loop, or null. */
    var inLoop: AstNode | Null = null

    /** Push a new scope for safe IDs (forking from parent). */
    def push(): Unit = {
      _safeIdsStack = safeIds :: _safeIdsStack
      safeIds = safeIds.clone()
    }

    /** Pop back to the parent safe-ID scope. */
    def pop(): Unit = {
      safeIds = _safeIdsStack.head
      _safeIdsStack = _safeIdsStack.tail
    }

    /** Mark a definition as safe or unsafe. */
    def mark(defId: Int, safe: Boolean): Unit =
      safeIds(defId) = safe
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Run data-flow analysis on a scope.
    *
    * Walks the AST in execution order, tracking variable definitions, assignments, and uses. Sets flags on SymbolDef that other passes use.
    *
    * @param scope
    *   the scope node (Toplevel or Lambda) to analyze
    * @param compressor
    *   the compressor context
    */
  def reduceVars(scope: AstNode, compressor: CompressorLike): Unit = {
    val state = new ReduceVarsState
    var tw: TreeWalker = null // @nowarn — initialized before use
    tw = new TreeWalker((node, descend) => reduceNode(node, descend, compressor, state, tw))
    scope.walk(tw)
  }

  // -----------------------------------------------------------------------
  // Node-specific reduce_vars logic
  // -----------------------------------------------------------------------

  /** Dispatch reduce_vars logic for each node type. */
  private def reduceNode(
    node:       AstNode,
    descend:    () => Unit,
    compressor: CompressorLike,
    state:      ReduceVarsState,
    tw:         TreeWalker
  ): Any = {
    node match {
      // Accessor (getter/setter function)
      case _: AstAccessor =>
        state.push()
        // Reset variables in the accessor's scope
        descend()
        state.pop()
        true // handled

      // Assignment
      case assign: AstAssign =>
        reduceAssign(assign, descend, compressor, state, tw)

      // Binary with lazy operator
      case binary: AstBinary if lazyOp.contains(binary.operator) =>
        if (binary.left != null) binary.left.nn.walk(tw)
        state.push()
        if (binary.right != null) binary.right.nn.walk(tw)
        state.pop()
        true

      // Case (must come before AstBlock since AstCase mixes in AstSwitchBranch extends AstBlock)
      case cas: AstCase =>
        state.push()
        if (cas.expression != null) cas.expression.nn.walk(tw)
        state.pop()
        state.push()
        walkBody(cas.body, tw)
        state.pop()
        true

      // Default (must come before AstBlock)
      case _: AstDefault =>
        state.push()
        descend()
        state.pop()
        true

      // Lambda (must come before AstClass/AstScope/AstBlock since AstLambda extends AstScope extends AstBlock)
      case lambda: AstLambda =>
        clearFlag(lambda, INLINED)
        state.push()
        descend()
        state.pop()
        true

      // Class (must come before AstScope/AstBlock since AstClass extends AstScope extends AstBlock)
      case cls: AstClass =>
        clearFlag(cls, INLINED)
        state.push()
        descend()
        state.pop()
        true

      // Toplevel (must come before AstBlock since AstToplevel extends AstScope extends AstBlock)
      case _: AstToplevel =>
        // Reset all variables
        descend()
        true

      // Block (catch-all for remaining AstBlock subtypes like AstBlockStatement, AstSwitch)
      case _: AstBlock =>
        // Block-scoped variables are reset via resetBlockVariables in Terser.
        // For now, proceed with normal walking.
        null // continue normal walking

      // Conditional
      case cond: AstConditional =>
        if (cond.condition != null) cond.condition.nn.walk(tw)
        state.push()
        if (cond.consequent != null) cond.consequent.nn.walk(tw)
        state.pop()
        state.push()
        if (cond.alternative != null) cond.alternative.nn.walk(tw)
        state.pop()
        true

      // Chain (optional chaining)
      case _: AstChain =>
        val savedSafeIds = state.safeIds
        descend()
        state.safeIds = savedSafeIds
        true

      // Call
      case call: AstCall =>
        if (call.expression != null) call.expression.nn.walk(tw)
        if (call.optional) {
          state.push()
          // Note: we don't pop here — the Chain above handles it
        }
        var i = 0
        while (i < call.args.size) {
          call.args(i).walk(tw)
          i += 1
        }
        true

      // PropAccess (optional)
      case pa: AstPropAccess if pa.optional =>
        if (pa.expression != null) pa.expression.nn.walk(tw)
        state.push()
        pa.property match {
          case n: AstNode => n.walk(tw)
          case _ =>
        }
        true

      // Do loop
      case doLoop: AstDo =>
        val savedLoop = state.inLoop
        state.inLoop = doLoop
        state.push()
        if (doLoop.body != null) doLoop.body.nn.walk(tw)
        if (doLoop.condition != null) doLoop.condition.nn.walk(tw)
        state.pop()
        state.inLoop = savedLoop
        true

      // For loop
      case forLoop: AstFor =>
        if (forLoop.init != null) forLoop.init.nn.walk(tw)
        val savedLoop = state.inLoop
        state.inLoop = forLoop
        state.push()
        if (forLoop.condition != null) forLoop.condition.nn.walk(tw)
        if (forLoop.body != null) forLoop.body.nn.walk(tw)
        if (forLoop.step != null) forLoop.step.nn.walk(tw)
        state.pop()
        state.inLoop = savedLoop
        true

      // ForIn / ForOf loop
      case forIn: AstForIn =>
        // suppress init (destructuring targets can't be tracked)
        if (forIn.obj != null) forIn.obj.nn.walk(tw)
        val savedLoop = state.inLoop
        state.inLoop = forIn
        state.push()
        if (forIn.body != null) forIn.body.nn.walk(tw)
        state.pop()
        state.inLoop = savedLoop
        true

      // If
      case ifStmt: AstIf =>
        if (ifStmt.condition != null) ifStmt.condition.nn.walk(tw)
        state.push()
        if (ifStmt.body != null) ifStmt.body.nn.walk(tw)
        state.pop()
        if (ifStmt.alternative != null) {
          state.push()
          ifStmt.alternative.nn.walk(tw)
          state.pop()
        }
        true

      // Labeled statement
      case _: AstLabeledStatement =>
        state.push()
        descend()
        state.pop()
        true

      // SymbolCatch
      case _: AstSymbolCatch =>
        // Mark the catch variable as unfixed
        // TODO: set d.fixed = false when SymbolDef is ported
        null

      // SymbolRef
      case _: AstSymbolRef =>
        // Track reference
        // TODO: full reference tracking when SymbolDef is ported
        null

      // Try
      case tr: AstTry =>
        state.push()
        if (tr.body != null) tr.body.nn.walk(tw)
        state.pop()
        if (tr.bcatch != null) {
          state.push()
          tr.bcatch.nn.walk(tw)
          state.pop()
        }
        if (tr.bfinally != null) tr.bfinally.nn.walk(tw)
        true

      // Unary (++/--)
      case unary: AstUnary if unary.operator == "++" || unary.operator == "--" =>
        // Track mutation of the operand
        // TODO: full tracking when SymbolDef is ported
        null

      // VarDef
      case vd: AstVarDef =>
        // Track variable definition
        if (vd.name.isInstanceOf[AstDestructuring]) {
          // Can't track destructured names
          null
        } else {
          // TODO: full tracking when SymbolDef is ported
          null
        }

      // While
      case whileLoop: AstWhile =>
        val savedLoop = state.inLoop
        state.inLoop = whileLoop
        state.push()
        descend()
        state.pop()
        state.inLoop = savedLoop
        true

      // Everything else: continue normal walking
      case _ => null
    }
  }

  // -----------------------------------------------------------------------
  // Assignment handling
  // -----------------------------------------------------------------------

  private def reduceAssign(
    assign:     AstAssign,
    descend:    () => Unit,
    compressor: CompressorLike,
    state:      ReduceVarsState,
    tw:         TreeWalker
  ): Any =
    // Destructuring assignments can't be tracked
    assign.left match {
      case _: AstDestructuring =>
        // Walk normally — we can't track individual destructured names
        null
      case _ =>
        if (assign.logical) {
          // Logical assignment (&&=, ||=, ??=)
          if (assign.left != null) assign.left.nn.walk(tw)
          state.push()
          if (assign.right != null) assign.right.nn.walk(tw)
          state.pop()
          true
        } else {
          // Regular assignment — walk right side first (to resolve value),
          // then mark the left side
          // TODO: full tracking when SymbolDef is ported
          null // continue normal walking
        }
    }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Check if a value is immutable (constant, lambda, or `this`). */
  def isImmutable(value: AstNode | Null): Boolean =
    if (value == null) false
    else {
      value.nn match {
        case _: AstLambda | _: AstThis => true
        case _                         => Evaluate.isConstant(value.nn)
      }
    }
}
