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
 *   Convention: ReduceVarsState class instead of patching TreeWalker prototype
 *   Idiom: boundary/break instead of return, mutable.Map for safe_ids
 */
package ssg
package js
package compress

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.Inference.{ isConstantExpression, isModified, lazyOp }
import ssg.js.scope.SymbolDef

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

    /** Check if a definition ID has an entry in the current safe_ids scope. */
    def hasSafe(defId: Int): Boolean =
      safeIds.contains(defId)
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
  // Definition reset (called before each compression pass)
  // -----------------------------------------------------------------------

  /** Clear definition properties before a pass. */
  def resetDef(compressor: CompressorLike, d: SymbolDef): Unit = {
    d.assignments = 0
    d.chained = false
    d.directAccess = false
    d.escaped = 0
    d.recursiveRefs = 0
    d.references = ArrayBuffer.empty
    d.singleUse = null // Use null sentinel (original uses undefined)
    d.shouldReplace = null
    if (
      d.scope.pinned
      || (d.orig.nonEmpty && d.orig(0).isInstanceOf[AstSymbolFunarg] && d.scope.isInstanceOf[AstLambda] && d.scope.asInstanceOf[AstLambda].usesArguments)
    ) {
      d.fixed = false
    } else if (d.orig.nonEmpty && d.orig(0).isInstanceOf[AstSymbolConst] || !compressor.exposed(d)) {
      d.fixed = d.init
    } else {
      d.fixed = false
    }
  }

  /** Reset all variables in a scope. */
  def resetVariables(state: ReduceVarsState, compressor: CompressorLike, scope: AstScope): Unit = {
    scope.variables.foreach { case (_, v) =>
      val d = v.asInstanceOf[SymbolDef]
      resetDef(compressor, d)
      if (d.fixed == null) {
        state.defsToSafeIds(d.id) = state.safeIds
        state.mark(d.id, true)
      } else if (d.fixed != false) {
        state.loopIds(d.id) = state.inLoop
        state.mark(d.id, true)
      }
    }
  }

  /** Reset block-scoped variables. */
  def resetBlockVariables(compressor: CompressorLike, node: AstNode): Unit = {
    node match {
      case b: AstBlock if b.blockScope != null =>
        b.blockScope.nn.variables.foreach { case (_, v) =>
          resetDef(compressor, v.asInstanceOf[SymbolDef])
        }
      case it: AstIterationStatement if it.blockScope != null =>
        it.blockScope.nn.variables.foreach { case (_, v) =>
          resetDef(compressor, v.asInstanceOf[SymbolDef])
        }
      case _ =>
    }
  }

  // -----------------------------------------------------------------------
  // Safe-to-read / safe-to-assign predicates
  // -----------------------------------------------------------------------

  /** Check if it is safe to read a definition's fixed value. */
  private def safeToRead(state: ReduceVarsState, d: SymbolDef): Boolean = {
    if (d.singleUse == "m") return false // @nowarn — modified single-use
    if (state.safeIds.getOrElse(d.id, false)) {
      if (d.fixed == null) {
        // Variable was declared but no initializer — treat as void 0
        val orig = d.orig(0)
        if (orig.isInstanceOf[AstSymbolFunarg] || orig.name == "arguments") return false // @nowarn
        val void0 = new AstUnaryPrefix
        void0.operator = "void"
        void0.expression = {
          val n = new AstNumber
          n.value = 0
          n.start = orig.start
          n.end = orig.end
          n
        }
        void0.start = orig.start
        void0.end = orig.end
        d.fixed = void0
      }
      return true // @nowarn
    }
    d.fixed.isInstanceOf[AstDefun]
  }

  /** Check if it is safe to assign to a definition. */
  private def safeToAssign(state: ReduceVarsState, d: SymbolDef, scope: AstScope | Null, value: Any): Boolean = {
    if (d.fixed == null && state.defsToSafeIds.contains(d.id)) {
      // First assignment to an uninitialized variable
      state.defsToSafeIds.get(d.id).foreach(ids => ids(d.id) = false)
      state.defsToSafeIds.remove(d.id)
      return true // @nowarn
    }
    if (!state.hasSafe(d.id)) return false // @nowarn
    if (!safeToRead(state, d)) return false // @nowarn
    if (d.fixed == false) return false // @nowarn
    if (d.fixed != null && d.fixed != false && (value == null || d.references.size > d.assignments)) return false // @nowarn
    if (d.fixed.isInstanceOf[AstDefun]) {
      return value.isInstanceOf[AstNode] && d.fixed.asInstanceOf[AstDefun].parentScope == scope // @nowarn
    }
    d.orig.forall { sym =>
      !(sym.isInstanceOf[AstSymbolConst] || sym.isInstanceOf[AstSymbolDefun] || sym.isInstanceOf[AstSymbolLambda])
    }
  }

  /** Check if a definition is referenced exactly once. */
  private def refOnce(state: ReduceVarsState, compressor: CompressorLike, d: SymbolDef): Boolean =
    compressor.optionBool("unused") &&
      !d.scope.pinned &&
      d.references.size - d.recursiveRefs == 1 &&
      state.loopIds.get(d.id).contains(state.inLoop)

  /** Check if a value is immutable (constant, lambda, or `this`). */
  def isImmutable(value: AstNode | Null): Boolean =
    if (value == null) false
    else {
      value.nn match {
        case _: AstLambda | _: AstThis => true
        case _                         => Evaluate.isConstant(value.nn)
      }
    }

  // -----------------------------------------------------------------------
  // Escape tracking
  // -----------------------------------------------------------------------

  /** Check if a node's scope is the same as a definition's scope. */
  private def sameScopeAsDef(node: AstNode, d: SymbolDef): Boolean =
    node match {
      case sym: AstSymbol => sym.scope != null && (sym.scope.nn.asInstanceOf[AnyRef] eq d.scope.asInstanceOf[AnyRef])
      case _ => false
    }

  /** Mark a definition as escaped if its value can leave the point of use. */
  private def markEscaped(tw: TreeWalker, d: SymbolDef, scope: AstScope | Null, node: AstNode, value: AstNode | Null, level: Int, depth: Int): Unit = {
    val parent = tw.parent(level)
    if (parent == null) return // @nowarn
    val parentNode = parent.nn

    if (value != null) {
      val v = value.nn
      if (Evaluate.isConstant(v)) return // @nowarn
      if (v.isInstanceOf[AstClassExpression]) return // @nowarn
    }

    // Check if value escapes into an assignment, call, exit, var def, using def, or yield
    parentNode match {
      case assign: AstAssign if (assign.operator == "=" || assign.logical) && assign.right != null && (node eq assign.right.nn) =>
        val actualDepth = if (depth > 1 && !(value != null && isConstantExpression(value.nn, scope) != false)) 1 else depth
        if (d.escaped == 0 || d.escaped > actualDepth) d.escaped = actualDepth
        return // @nowarn
      case call: AstCall if (call.expression == null || !(node eq call.expression.nn)) || call.isInstanceOf[AstNew] =>
        val actualDepth = if (depth > 1) 1 else depth
        if (d.escaped == 0 || d.escaped > actualDepth) d.escaped = actualDepth
        return // @nowarn
      case exit: AstExit if exit.value != null && (node eq exit.value.nn) && (scope == null || !sameScopeAsDef(node, d)) =>
        val actualDepth = if (depth > 1) 1 else depth
        if (d.escaped == 0 || d.escaped > actualDepth) d.escaped = actualDepth
        return // @nowarn
      // AstVarDefLike includes both AstVarDef and AstUsingDef
      case vd: AstVarDef if vd.value != null && (node eq vd.value.nn) =>
        val actualDepth = if (depth > 1) 1 else depth
        if (d.escaped == 0 || d.escaped > actualDepth) d.escaped = actualDepth
        return // @nowarn
      case ud: AstUsingDef if ud.value != null && (node eq ud.value.nn) =>
        val actualDepth = if (depth > 1) 1 else depth
        if (d.escaped == 0 || d.escaped > actualDepth) d.escaped = actualDepth
        return // @nowarn
      // Note: Original Terser uses .value but AST_Yield has .expression — using correct field
      case yld: AstYield if yld.expression != null && (node eq yld.expression.nn) && (scope == null || !sameScopeAsDef(node, d)) =>
        val actualDepth = if (depth > 1) 1 else depth
        if (d.escaped == 0 || d.escaped > actualDepth) d.escaped = actualDepth
        return // @nowarn
      case _ =>
    }

    // Check for propagation through lazy ops, conditionals, sequences, etc.
    parentNode match {
      case _: AstArray | _: AstAwait | _: AstExpansion =>
        markEscaped(tw, d, scope, parentNode, parentNode, level + 1, depth)
        return // @nowarn
      case binary: AstBinary if lazyOp.contains(binary.operator) =>
        markEscaped(tw, d, scope, parentNode, parentNode, level + 1, depth)
        return // @nowarn
      case cond: AstConditional if cond.condition == null || !(node eq cond.condition.nn) =>
        markEscaped(tw, d, scope, parentNode, parentNode, level + 1, depth)
        return // @nowarn
      case seq: AstSequence if seq.expressions.nonEmpty && (node eq seq.expressions.last) =>
        markEscaped(tw, d, scope, parentNode, parentNode, level + 1, depth)
        return // @nowarn
      case kv: AstObjectKeyVal if kv.value != null && (node eq kv.value.nn) =>
        val obj = tw.parent(level + 1)
        if (obj != null) {
          markEscaped(tw, d, scope, obj.nn, obj.nn, level + 2, depth)
        }
        return // @nowarn
      case pa: AstPropAccess if pa.expression != null && (node eq pa.expression.nn) =>
        val propValue = Common.readProperty(
          value match { case null => null; case v => v.nn },
          pa.property match { case n: AstNode => n; case _ => null }
        )
        markEscaped(tw, d, scope, parentNode, propValue, level + 1, depth + 1)
        if (propValue != null) return // @nowarn
      case _ =>
    }

    if (level > 0) return // @nowarn
    parentNode match {
      case seq: AstSequence if seq.expressions.nonEmpty && !(node eq seq.expressions.last) => return // @nowarn
      case _: AstSimpleStatement => return // @nowarn
      case _ =>
    }

    d.directAccess = true
  }

  // -----------------------------------------------------------------------
  // Suppress: mark all symbols in a node as unfixed
  // -----------------------------------------------------------------------

  /** Walk a node and mark all symbol definitions as unfixed (used for destructuring targets). */
  private def suppress(node: AstNode): Unit = {
    val tw = new TreeWalker((child, _) => {
      child match {
        case sym: AstSymbolRef =>
          val d = sym.definition()
          if (d != null) {
            d.nn.references.addOne(sym)
            d.nn.fixed = false
          }
        case sym: AstSymbol =>
          val d = sym.definition()
          if (d != null) {
            d.nn.fixed = false
          }
        case _ =>
      }
      null // continue walking
    })
    node.walk(tw)
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
      case acc: AstAccessor =>
        state.push()
        resetVariables(state, compressor, acc)
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
        markLambda(lambda, descend, compressor, state, tw)

      // Class (must come before AstScope/AstBlock since AstClass extends AstScope extends AstBlock)
      case cls: AstClass =>
        clearFlag(cls, INLINED)
        state.push()
        descend()
        state.pop()
        true

      // ClassStaticBlock
      case _: AstClassStaticBlock =>
        resetBlockVariables(compressor, node)
        null // continue normal walking

      // Toplevel (must come before AstBlock since AstToplevel extends AstScope extends AstBlock)
      case tl: AstToplevel =>
        tl.globals.foreach { case (_, v) =>
          resetDef(compressor, v.asInstanceOf[SymbolDef])
        }
        resetVariables(state, compressor, tl)
        descend()
        handleDefinedAfterHoist(tl)
        true

      // Block (catch-all for remaining AstBlock subtypes)
      case _: AstBlock =>
        resetBlockVariables(compressor, node)
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
        resetBlockVariables(compressor, doLoop)
        val savedLoop = state.inLoop
        state.inLoop = doLoop
        state.push()
        if (doLoop.body != null) doLoop.body.nn.walk(tw)
        if (Common.hasBreakOrContinue(doLoop.asInstanceOf[AstNode & AstIterationStatement], null)) {
          state.pop()
          state.push()
        }
        if (doLoop.condition != null) doLoop.condition.nn.walk(tw)
        state.pop()
        state.inLoop = savedLoop
        true

      // For loop
      case forLoop: AstFor =>
        resetBlockVariables(compressor, forLoop)
        if (forLoop.init != null) forLoop.init.nn.walk(tw)
        val savedLoop = state.inLoop
        state.inLoop = forLoop
        state.push()
        if (forLoop.condition != null) forLoop.condition.nn.walk(tw)
        if (forLoop.body != null) forLoop.body.nn.walk(tw)
        if (forLoop.step != null) {
          if (Common.hasBreakOrContinue(forLoop.asInstanceOf[AstNode & AstIterationStatement], null)) {
            state.pop()
            state.push()
          }
          forLoop.step.nn.walk(tw)
        }
        state.pop()
        state.inLoop = savedLoop
        true

      // ForIn / ForOf loop
      case forIn: AstForIn =>
        resetBlockVariables(compressor, forIn)
        suppress(forIn.init.nn)
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

      // SymbolCatch — mark the catch variable as unfixed
      case sym: AstSymbolCatch =>
        val d = sym.definition()
        if (d != null) d.nn.fixed = false
        null

      // SymbolRef — full reference tracking
      case ref: AstSymbolRef =>
        reduceSymbolRef(ref, compressor, state, tw)

      // Try
      case tr: AstTry =>
        resetBlockVariables(compressor, tr)
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
        reduceUnary(unary, state, tw)

      // UsingDef — suppress (can't track)
      case ud: AstUsingDef =>
        suppress(ud.name.nn)
        null

      // VarDef
      case vd: AstVarDef =>
        reduceVarDef(vd, descend, state, tw)

      // While
      case whileLoop: AstWhile =>
        resetBlockVariables(compressor, whileLoop)
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
  // Lambda handling (mark_lambda)
  // -----------------------------------------------------------------------

  /** Handle lambda/function definitions. */
  private def markLambda(
    lambda:     AstLambda,
    descend:    () => Unit,
    compressor: CompressorLike,
    state:      ReduceVarsState,
    tw:         TreeWalker
  ): Any = {
    clearFlag(lambda, INLINED)
    state.push()
    resetVariables(state, compressor, lambda)

    // Virtually turn IIFE parameters into variable definitions
    val parent = tw.parent()
    parent match {
      case iife: AstCall
          if lambda.name == null
            && !lambda.usesArguments
            && !lambda.pinned
            && iife.expression != null && (iife.expression.nn eq lambda)
            && !iife.args.exists(_.isInstanceOf[AstExpansion])
            && lambda.argnames.forall(_.isInstanceOf[AstSymbol]) =>
        var i = 0
        while (i < lambda.argnames.size) {
          lambda.argnames(i) match {
            case arg: AstSymbol =>
              val d = arg.definition()
              if (d != null) {
                val dd = d.nn
                // Avoid setting fixed when there's more than one origin for a variable value
                if (dd.orig.size <= 1) {
                  val argIdx = i // capture for closure
                  // Avoid setting fixed when there's already one set (d.fixed === undefined check)
                  if (dd.fixed == null && (!lambda.usesArguments || tw.directives.contains("use strict"))) {
                    dd.fixed = new Function0[AstNode] {
                      def apply(): AstNode =
                        if (argIdx < iife.args.size) iife.args(argIdx) else {
                          val void0 = new AstUnaryPrefix
                          void0.operator = "void"
                          void0.expression = {
                            val n = new AstNumber
                            n.value = 0
                            n
                          }
                          void0
                        }
                    }
                    state.loopIds(dd.id) = state.inLoop
                    state.mark(dd.id, true)
                  } else {
                    dd.fixed = false
                  }
                }
              }
            case _ =>
          }
          i += 1
        }
      case _ =>
    }

    descend()
    state.pop()

    handleDefinedAfterHoist(lambda)

    true
  }

  // -----------------------------------------------------------------------
  // handle_defined_after_hoist: detect when a hoisted function uses a variable
  // that is written after the call site
  // -----------------------------------------------------------------------

  /** Info object for walk_parent callback (provides parent access). */
  private class WalkParentInfo(private val stack: ArrayBuffer[AstNode]) {
    def parent(n: Int = 0): AstNode | Null = {
      val idx = stack.size - 1 - n
      if (idx >= 0) stack(idx) else null
    }
  }

  /** Walk a node tree with parent access, similar to original walk_parent.
    *
    * Traverses in depth-first order, calling cb for each node with access to the parent stack. If cb returns WalkAbort, the walk is aborted and returns true. If cb returns any other truthy
    * value, children are skipped. Returns true if aborted, false otherwise.
    */
  private def walkParent(node: AstNode, cb: (AstNode, WalkParentInfo) => Any): Boolean = {
    import scala.util.boundary
    import scala.util.boundary.break

    val toVisit       = ArrayBuffer[AstNode](node)
    val push: AstNode => Unit = n => toVisit.addOne(n)
    val stack         = ArrayBuffer.empty[AstNode]
    val parentPopIndices = ArrayBuffer.empty[Int]

    val info = new WalkParentInfo(stack)

    boundary[Boolean] {
      while (toVisit.nonEmpty) {
        val current = toVisit.remove(toVisit.size - 1)

        // Pop parents that are no longer ancestors
        while (parentPopIndices.nonEmpty && toVisit.size == parentPopIndices.last) {
          stack.remove(stack.size - 1)
          parentPopIndices.remove(parentPopIndices.size - 1)
        }

        val ret = cb(current, info)

        if (ret != null && ret != false && ret != (())) {
          if (ret.asInstanceOf[AnyRef] eq WalkAbort) {
            break(true)
          }
          // truthy but not WalkAbort: skip children
        } else {
          val visitLength = toVisit.size

          current.childrenBackwards(push)

          // Push to stack only if we're going to traverse children
          if (toVisit.size > visitLength) {
            stack.addOne(current)
            parentPopIndices.addOne(visitLength - 1)
          }
        }
      }
      false
    }
  }

  /** It's possible for a hoisted function to use something that's not defined yet.
    *
    * Example:
    * {{{
    * hoisted();
    * var defined_after = true;
    * function hoisted() {
    *   // use defined_after
    * }
    * }}}
    *
    * Or even indirectly:
    * {{{
    * B();
    * var defined_after = true;
    * function A() {
    *   // use defined_after
    * }
    * function B() {
    *   A();
    * }
    * }}}
    *
    * Access a variable before declaration will either throw a ReferenceError (if the variable is declared with `let` or `const`), or get an `undefined` (if the variable is declared with `var`).
    *
    * If the variable is inlined into the function, the behavior will change.
    *
    * This function is called on the parent to disallow inlining of such variables.
    */
  private def handleDefinedAfterHoist(parent: AstScope): Unit = {
    val defuns = ArrayBuffer.empty[AstDefun]

    // First pass: collect hoisted function definitions
    walk(parent, (node, _) => {
      if (node.asInstanceOf[AnyRef] eq parent.asInstanceOf[AnyRef]) {
        null // continue into children
      } else {
        node match {
          case defun: AstDefun =>
            defuns.addOne(defun)
            true // skip children of defun
          case _: AstScope | _: AstSimpleStatement =>
            true // skip children
          case _ =>
            null // continue
        }
      }
    })

    // `defun` id to array of `defun` ids it uses
    val defunDependenciesMap = mutable.Map.empty[Int, ArrayBuffer[Int]]
    // `defun` id to array of enclosing `def` that are used by the function
    val dependenciesMap = mutable.Map.empty[Int, ArrayBuffer[SymbolDef]]
    // all symbol ids that will be tracked for read/write
    val symbolsOfInterest = mutable.Set.empty[Int]
    val defunsOfInterest  = mutable.Set.empty[Int]

    for (defun <- defuns) {
      if (defun.name != null) {
        val fnameDefOpt = defun.name.nn.asInstanceOf[AstSymbol].definition()
        if (fnameDefOpt != null) {
          val fnameDef     = fnameDefOpt.nn
          val enclosingDefs = ArrayBuffer.empty[SymbolDef]

          for (d <- defun.enclosed) {
            val dd = d.asInstanceOf[SymbolDef]
            if (
              dd.fixed != false &&
              !(dd.asInstanceOf[AnyRef] eq fnameDef.asInstanceOf[AnyRef]) &&
              dd.scope.getDefunScope == parent
            ) {
              symbolsOfInterest.add(dd.id)

              // found a reference to another function
              if (
                dd.assignments == 0 &&
                dd.orig.size == 1 &&
                dd.orig(0).isInstanceOf[AstSymbolDefun]
              ) {
                defunsOfInterest.add(dd.id)
                symbolsOfInterest.add(dd.id)

                defunsOfInterest.add(fnameDef.id)
                symbolsOfInterest.add(fnameDef.id)

                if (!defunDependenciesMap.contains(fnameDef.id)) {
                  defunDependenciesMap(fnameDef.id) = ArrayBuffer.empty
                }
                defunDependenciesMap(fnameDef.id).addOne(dd.id)
              } else {
                enclosingDefs.addOne(dd)
              }
            }
          }

          if (enclosingDefs.nonEmpty) {
            dependenciesMap(fnameDef.id) = enclosingDefs
            defunsOfInterest.add(fnameDef.id)
            symbolsOfInterest.add(fnameDef.id)
          }
        }
      }
    }

    // No defuns use outside constants
    if (dependenciesMap.isEmpty) {
      return // @nowarn — early exit
    }

    // Increment to count "symbols of interest" (defuns or defs) that we found.
    // These are tracked in AST order so we can check which is after which.
    var symbolIndex = 1
    // Map a defun ID to its first read (a `symbol_index`)
    val defunFirstReadMap = mutable.Map.empty[Int, Int]
    // Map a symbol ID to its last write (a `symbol_index`)
    val symbolLastWriteMap = mutable.Map.empty[Int, Int]

    walkParent(parent, (node, walkInfo) => {
      node match {
        case sym: AstSymbol if sym.thedef != null =>
          val d  = sym.definition()
          if (d != null) {
            val id = d.nn.id

            symbolIndex += 1

            // Track last-writes to symbols
            if (symbolsOfInterest.contains(id)) {
              if (sym.isInstanceOf[AstSymbolDeclaration] || Inference.isLhs(sym, walkInfo.parent().nn) != null) {
                symbolLastWriteMap(id) = symbolIndex
              }
            }

            // Track first-reads of defuns (refined later)
            if (defunsOfInterest.contains(id)) {
              if (!defunFirstReadMap.contains(id) && !isRecursiveRefByInfo(walkInfo, id)) {
                defunFirstReadMap(id) = symbolIndex
              }
            }
          }
        case _ =>
      }
      null // continue walking
    })

    // Refine `defun_first_read_map` to be as high as possible
    for ((defun, defunFirstRead) <- defunFirstReadMap) {
      // Update all dependencies of `defun`
      val queue = mutable.Set.empty[Int]
      defunDependenciesMap.get(defun).foreach(deps => queue.addAll(deps))

      // Process queue (using iterator + add to simulate JS Set iteration with additions)
      val processed = mutable.Set.empty[Int]
      while (queue.nonEmpty) {
        val enclosedDefun = queue.head
        queue.remove(enclosedDefun)
        if (!processed.contains(enclosedDefun)) {
          processed.add(enclosedDefun)

          val enclosedDefunFirstRead = defunFirstReadMap.get(enclosedDefun)
          if (enclosedDefunFirstRead.isEmpty || enclosedDefunFirstRead.get >= defunFirstRead) {
            defunFirstReadMap(enclosedDefun) = defunFirstRead

            defunDependenciesMap.get(enclosedDefun).foreach { enclosedDeps =>
              for (dep <- enclosedDeps) {
                if (!processed.contains(dep)) {
                  queue.add(dep)
                }
              }
            }
          }
        }
      }
    }

    // Ensure write-then-read order, otherwise clear `fixed`
    // This is safe because last-writes (found_symbol_writes) are assumed to be as late as possible,
    // and first-reads (defun_first_read_map) are assumed to be as early as possible.
    for ((defunId, defs) <- dependenciesMap) {
      defunFirstReadMap.get(defunId) match {
        case None =>
        // defun is never read, skip
        case Some(defunFirstRead) =>
          for (d <- defs) {
            if (d.fixed != false) {
              val defLastWrite = symbolLastWriteMap.getOrElse(d.id, 0)

              if (defunFirstRead < defLastWrite) {
                d.fixed = false
              }
            }
          }
      }
    }
  }

  /** Check if a reference is recursive by walking up the parent stack.
    *
    * Similar to Common.isRecursiveRef but uses WalkParentInfo instead of TreeWalker.
    */
  private def isRecursiveRefByInfo(info: WalkParentInfo, defId: Int): Boolean = {
    import scala.util.boundary
    import scala.util.boundary.break

    boundary[Boolean] {
      var i = 0
      var node = info.parent(i)
      while (node != null) {
        node.nn match {
          case lambda: AstLambda if lambda.name != null =>
            lambda.name.nn match {
              case sym: AstSymbol if sym.thedef != null =>
                val d = sym.definition()
                if (d != null && d.nn.id == defId) break(true)
              case _ =>
            }
          case cls: AstClass if cls.name != null =>
            cls.name.nn match {
              case sym: AstSymbol if sym.thedef != null =>
                val d = sym.definition()
                if (d != null && d.nn.id == defId) break(true)
              case _ =>
            }
          case _ =>
        }
        i += 1
        node = info.parent(i)
      }
      false
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
  ): Any = {
    // Destructuring assignments can't be tracked
    assign.left match {
      case d: AstDestructuring =>
        suppress(d)
        return null // @nowarn — continue normal walking
      case _ =>
    }

    val finishWalk: () => Any = () => {
      if (assign.logical) {
        if (assign.left != null) assign.left.nn.walk(tw)
        state.push()
        if (assign.right != null) assign.right.nn.walk(tw)
        state.pop()
        true
      } else {
        null // continue normal walking
      }
    }

    val sym = assign.left
    sym match {
      case ref: AstSymbolRef =>
        val d = ref.definition()
        if (d == null) return finishWalk() // @nowarn
        val dd = d.nn
        val safe = safeToAssign(state, dd, ref.scope, assign.right)
        dd.assignments += 1
        if (!safe) return finishWalk() // @nowarn

        val fixed = dd.fixed
        if (fixed == null && assign.operator != "=" && !assign.logical) return finishWalk() // @nowarn
        if (fixed == false && assign.operator != "=" && !assign.logical) return finishWalk() // @nowarn

        val eq = assign.operator == "="
        val value: AstNode = if (eq && assign.right != null) assign.right.nn else assign

        if (isModified(compressor, tw, assign, value, 0)) return finishWalk() // @nowarn

        dd.references.addOne(ref)

        if (!assign.logical) {
          if (!eq) dd.chained = true

          if (eq) {
            dd.fixed = new Function0[AstNode] {
              def apply(): AstNode = assign.right.nn
            }
          } else {
            dd.fixed = new Function0[AstNode] {
              def apply(): AstNode = {
                val bin = new AstBinary
                bin.operator = assign.operator.stripSuffix("=")
                bin.left = fixed match {
                  case n: AstNode => n
                  case f: Function0[?] => f().asInstanceOf[AstNode]
                  case _ => ref // fallback
                }
                bin.right = assign.right.nn
                bin.start = assign.start
                bin.end = assign.end
                bin
              }
            }
          }
        }

        if (assign.logical) {
          state.mark(dd.id, false)
          state.push()
          if (assign.right != null) assign.right.nn.walk(tw)
          state.pop()
          return true // @nowarn
        }

        state.mark(dd.id, false)
        if (assign.right != null) assign.right.nn.walk(tw)
        state.mark(dd.id, true)

        markEscaped(tw, dd, ref.scope, assign, value, 0, 1)

        true

      case _ =>
        finishWalk()
    }
  }

  // -----------------------------------------------------------------------
  // SymbolRef tracking
  // -----------------------------------------------------------------------

  /** Handle SymbolRef: track reference, determine fixed value, single_use. */
  private def reduceSymbolRef(
    ref:        AstSymbolRef,
    compressor: CompressorLike,
    state:      ReduceVarsState,
    tw:         TreeWalker
  ): Any = {
    val d = ref.definition()
    if (d == null) return null // @nowarn — continue normal walking
    val dd = d.nn

    dd.references.addOne(ref)

    // Check !d.fixed for falsy (false, null, or undefined/null in original JS)
    // Original JS: !d.fixed — evaluates to true for false, null, undefined
    if (dd.references.size == 1 && (dd.fixed == false || dd.fixed == null) && dd.orig.nonEmpty && dd.orig(0).isInstanceOf[AstSymbolDefun]) {
      state.loopIds(dd.id) = state.inLoop
    }

    var fixedValue: AstNode | Null = null

    if (!safeToRead(state, dd)) {
      dd.fixed = false
    } else if (dd.fixed != false && dd.fixed != null) {
      fixedValue = ref.fixedValue() match {
        case n: AstNode => n
        case _ => null
      }

      if (fixedValue.isInstanceOf[AstLambda] && Common.isRecursiveRef(tw, dd)) {
        dd.recursiveRefs += 1
      } else if (
        fixedValue != null
        && !compressor.exposed(dd)
        && refOnce(state, compressor, dd)
      ) {
        dd.singleUse =
          (fixedValue.isInstanceOf[AstLambda] && !fixedValue.asInstanceOf[AstLambda].pinned) ||
            fixedValue.isInstanceOf[AstClass] ||
            ((dd.scope.asInstanceOf[AnyRef] eq ref.scope.nn.asInstanceOf[AnyRef]) && Evaluate.isConstantExpression(fixedValue.nn))
      } else {
        dd.singleUse = false
      }

      if (isModified(compressor, tw, ref, fixedValue, 0, isImmutable(fixedValue))) {
        if (dd.singleUse != false) {
          dd.singleUse = "m"
        } else {
          dd.fixed = false
        }
      }
    }

    markEscaped(tw, dd, ref.scope, ref, fixedValue, 0, 1)

    null // continue normal walking
  }

  // -----------------------------------------------------------------------
  // Unary ++/-- handling
  // -----------------------------------------------------------------------

  private def reduceUnary(
    unary: AstUnary,
    state: ReduceVarsState,
    tw:    TreeWalker
  ): Any = {
    val exp = unary.expression
    exp match {
      case ref: AstSymbolRef =>
        val d = ref.definition()
        if (d == null) return null // @nowarn
        val dd = d.nn
        val safe = safeToAssign(state, dd, ref.scope, true)
        dd.assignments += 1
        if (!safe) return null // @nowarn — continue normal
        val fixed = dd.fixed
        if (fixed == false || fixed == null) return null // @nowarn
        dd.references.addOne(ref)
        dd.chained = true
        dd.fixed = new Function0[AstNode] {
          def apply(): AstNode = {
            val bin = new AstBinary
            bin.operator = unary.operator.stripSuffix(unary.operator.last.toString)
            val prefix = new AstUnaryPrefix
            prefix.operator = "+"
            prefix.expression = fixed match {
              case n: AstNode => n
              case f: Function0[?] => f().asInstanceOf[AstNode]
              case _ => ref
            }
            prefix.start = unary.start
            prefix.end = unary.end
            bin.left = prefix
            val num = new AstNumber
            num.value = 1
            num.start = unary.start
            num.end = unary.end
            bin.right = num
            bin.start = unary.start
            bin.end = unary.end
            bin
          }
        }
        state.mark(dd.id, true)
        true
      case _ =>
        null // continue normal walking
    }
  }

  // -----------------------------------------------------------------------
  // VarDef handling
  // -----------------------------------------------------------------------

  private def reduceVarDef(
    vd:      AstVarDef,
    descend: () => Unit,
    state:   ReduceVarsState,
    tw:      TreeWalker
  ): Any = {
    if (vd.name.isInstanceOf[AstDestructuring]) {
      suppress(vd.name.nn)
      return null // @nowarn — continue normal walking
    }

    vd.name match {
      case sym: AstSymbol =>
        val d = sym.definition()
        if (d == null) return null // @nowarn
        val dd = d.nn

        if (vd.value != null) {
          if (safeToAssign(state, dd, sym.scope, vd.value.nn)) {
            dd.fixed = new Function0[AstNode] {
              def apply(): AstNode = vd.value.nn
            }
            state.loopIds(dd.id) = state.inLoop
            state.mark(dd.id, false)
            descend()
            state.mark(dd.id, true)
            return true // @nowarn
          } else {
            dd.fixed = false
          }
        }
        null // continue normal walking
      case _ =>
        null
    }
  }
}
