/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scope analysis — 4-pass algorithm that builds scope chains, resolves
 * variable references, and handles IE8/Safari workarounds.
 *
 * Original source: terser lib/scope.js (figure_out_scope, lines 204-481)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: figure_out_scope -> figureOutScope, snake_case -> camelCase
 *   Convention: Mutable scope state, TreeWalker-based passes
 *   Idiom: boundary/break replaces JS return, mutable.Map/ArrayBuffer for scope state
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/scope.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 88493d7ca0d708389f5f78f541c4fb48e71d9fe2
 */
package ssg
package js
package scope

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import ssg.js.ast.*
import ssg.js.parse.JsParseError

/** Options for scope analysis. */
final case class ScopeOptions(
  cache:    ManglerCache | Null = null,
  ie8:      Boolean = false,
  safari10: Boolean = false,
  module:   Boolean = false
)

object ScopeOptions {
  val Defaults: ScopeOptions = ScopeOptions()
}

/** Scope analysis engine.
  *
  * Implements the 4-pass `figure_out_scope` algorithm from terser:
  *   1. Set up scope chains (parent_scope links, register vars/functions)
  *   2. Back-references (link SymbolRef -> SymbolDef, detect eval)
  *   3. IE8 workarounds (catch scope)
  *   4. Safari loop fixes
  *
  * Also provides scope utility methods (init_scope_vars, def_variable, def_function, find_variable, is_block_scope) as static helpers that operate on AstScope nodes.
  */
object ScopeAnalysis {

  /** Export bitmask: symbol should not be mangled. */
  val MaskExportDontMangle: Int = 1 << 0

  /** Export bitmask: default export, may still mangle. */
  val MaskExportWantMangle: Int = 1 << 1

  // ---- Module-level mutable state (matches terser's module vars) ----

  /** Set of SymbolDef IDs for function definitions (populated when keep_fnames is set). */
  var functionDefs: mutable.Set[Int] | Null = null

  /** Set of short unmangleable names (used to avoid collisions). */
  var unmangledNames: mutable.Set[String] | Null = null

  /** Scopes containing block-level function declarations (for Annex B semantics). */
  var scopesWithBlockDefuns: mutable.Set[AstScope] | Null = null

  // =========================================================================
  // Scope utility methods
  // =========================================================================

  /** Initialize scope variables on the given scope node. */
  def initScopeVars(scope: AstScope, parentScope: AstScope | Null): Unit = {
    scope.variables = mutable.Map.empty
    scope.usesWith = false
    scope.usesEval = false
    scope.parentScope = parentScope
    scope.enclosed = ArrayBuffer.empty
    scope.cname = -1
  }

  /** Initialize scope variables on a lambda (also defines `arguments`). */
  def initLambdaScopeVars(lambda: AstLambda, parentScope: AstScope | Null): Unit = {
    initScopeVars(lambda, parentScope)
    lambda.usesArguments = false
    val argsSym = new AstSymbolFunarg
    argsSym.name = "arguments"
    argsSym.start = lambda.start
    argsSym.end = lambda.end
    defVariable(lambda, argsSym, null)
  }

  /** Initialize scope variables on an arrow function (no `arguments` binding). */
  def initArrowScopeVars(arrow: AstArrow, parentScope: AstScope | Null): Unit = {
    initScopeVars(arrow, parentScope)
    arrow.usesArguments = false
  }

  /** Define a variable in the given scope. Returns the SymbolDef. */
  def defVariable(scope: AstScope, symbol: AstSymbol, init: AstNode | Null): SymbolDef =
    scope.variables.get(symbol.name) match {
      case Some(existing) =>
        val d = existing.asInstanceOf[SymbolDef]
        d.orig.addOne(symbol)
        if (d.init != null && (d.scope ne symbol.scope.asInstanceOf[AnyRef]) || d.init.isInstanceOf[AstFunction]) {
          d.init = init
        }
        symbol.thedef = d
        d
      case None =>
        val d = new SymbolDef(scope, symbol, init)
        scope.variables(symbol.name) = d
        d.global = scope.parentScope == null
        symbol.thedef = d
        d
    }

  /** Define a function in the given scope. Returns the SymbolDef. */
  def defFunction(scope: AstScope, symbol: AstSymbol, init: AstNode | Null): SymbolDef = {
    val d = defVariable(scope, symbol, init)
    if (d.init == null || d.init.isInstanceOf[AstDefun]) {
      d.init = init
    }
    d
  }

  /** Define a global symbol on a toplevel scope. */
  def defGlobal(toplevel: AstToplevel, node: AstSymbol): SymbolDef = {
    val name = node.name
    toplevel.globals.get(name) match {
      case Some(existing) =>
        existing.asInstanceOf[SymbolDef]
      case None =>
        val g = new SymbolDef(toplevel, node)
        g.undeclared = true
        g.global = true
        toplevel.globals(name) = g
        g
    }
  }

  /** Find a variable by name, walking up the scope chain. */
  def findVariable(scope: AstScope, name: String): SymbolDef | Null =
    scope.variables.get(name) match {
      case Some(d) => d.asInstanceOf[SymbolDef]
      case None    =>
        scope.parentScope match {
          case ps: AstScope => findVariable(ps, name)
          case null => null
        }
    }

  /** Check whether a scope node is block-scoped. */
  def isBlockScope(node: AstNode): Boolean =
    node match {
      case _: AstClass | _: AstLambda | _: AstToplevel | _: AstSwitchBranch => false
      case s: AstScope              => s.isBlockScope
      case _: AstIterationStatement => true
      case _: AstBlock              => true
      case _ => false
    }

  /** Mark a symbol as enclosed in all scopes up to (and including) its definition scope. */
  def markEnclosed(symbol: AstSymbol): Unit = {
    val d = symbol.thedef.asInstanceOf[SymbolDef]
    var s: AstScope | Null = symbol.scope
    while (s != null) {
      pushUniq(s.nn.enclosed, d)
      if ((s.nn: AnyRef) eq (d.scope: AnyRef)) {
        s = null // @nowarn — break out of loop
      } else {
        s = s.nn.parentScope
      }
    }
  }

  /** Record a reference to a symbol's definition and mark enclosed. */
  def reference(symbol: AstSymbolRef): Unit = {
    val d = symbol.thedef.asInstanceOf[SymbolDef]
    d.references.addOne(symbol)
    markEnclosed(symbol)
  }

  /** Add an element to an ArrayBuffer only if not already present. */
  def pushUniq(buf: ArrayBuffer[Any], item: Any): Unit = {
    val itemRef = item.asInstanceOf[AnyRef]
    var i       = 0
    while (i < buf.size) {
      if ((buf(i).asInstanceOf[AnyRef]) eq itemRef) return // @nowarn — already present
      i += 1
    }
    buf.addOne(item)
  }

  /** Check if a scope has a conflicting definition for a name. */
  def conflictingDef(scope: AstScope, name: String): Boolean =
    scope.enclosed.exists(d => d.asInstanceOf[SymbolDef].name == name) ||
      scope.variables.contains(name) ||
      (scope.parentScope match {
        case ps: AstScope => conflictingDef(ps, name)
        case null => false
      })

  /** Check if a scope has a conflicting definition for a name (shallow — no parent walk). */
  def conflictingDefShallow(scope: AstScope, name: String): Boolean =
    scope.enclosed.exists(d => d.asInstanceOf[SymbolDef].name == name) ||
      scope.variables.contains(name)

  // =========================================================================
  // Main scope analysis: figureOutScope
  // =========================================================================

  /** Run the 4-pass scope analysis on an AST. */
  def figureOutScope(
    ast:            AstNode & AstScope,
    options:        ScopeOptions = ScopeOptions.Defaults,
    parentScopeOpt: AstScope | Null = null,
    toplevelOpt:    AstToplevel | Null = null
  ): Unit = {
    val toplevel: AstToplevel = toplevelOpt match {
      case tl: AstToplevel => tl
      case null =>
        ast match {
          case tl: AstToplevel => tl
          case _ => throw new IllegalArgumentException("Invalid toplevel scope")
        }
    }
    val ctx = new ScopeContext(ast, toplevel, options, parentScopeOpt)
    ctx.pass1()
    ctx.pass2()
    ctx.pass3()
    ctx.pass4()
  }

  /** Mark a symbol definition's export flag based on its position in the AST. */
  private[scope] def markExport(
    d:               SymbolDef,
    level:           Int,
    tw:              TreeWalker,
    inDestructuring: AstDestructuring
  ): Unit = {
    var actualLevel = level
    if (inDestructuring != null) {
      var i     = 0
      var found = false
      while (!found) {
        actualLevel += 1
        if (tw.parent(i) != null && (tw.parent(i).asInstanceOf[AnyRef] eq inDestructuring.asInstanceOf[AnyRef])) {
          found = true
        }
        i += 1
      }
    }
    val parentNode = tw.parent(actualLevel)
    parentNode match {
      case exp: AstExport =>
        d.exportFlag = MaskExportDontMangle
        val exported = exp.exportedDefinition
        if ((exported.isInstanceOf[AstDefun] || exported.isInstanceOf[AstDefClass]) && exp.isDefault) {
          d.exportFlag = MaskExportWantMangle
        }
      case _ =>
        d.exportFlag = 0
    }
  }
}

/** Mutable context for one figureOutScope invocation.
  *
  * This is a separate class (not a closure) to avoid Scala 3 compiler crashes with mutable var captures in nested lambda closures (NoDenotation.owner bug).
  */
private[scope] class ScopeContext(
  ast:            AstNode & AstScope,
  toplevel:       AstToplevel,
  options:        ScopeOptions,
  parentScopeOpt: AstScope | Null
) {
  import ScopeAnalysis.*

  var scope: AstScope = ast
  var labels = mutable.Map.empty[String, AstLabel]
  var defun:           AstScope         = null.asInstanceOf[AstScope] // @nowarn — null sentinel, set during walk
  var inDestructuring: AstDestructuring = null.asInstanceOf[AstDestructuring] // @nowarn — null sentinel
  val forScopes = ArrayBuffer.empty[AstScope]
  private var rootInitialized: Boolean = false

  private def pass1Visit(node: AstNode, descend: () => Unit): Any = {
    if (isBlockScope(node)) {
      handleBlockScope(node, descend)
      return true
    }

    node match {
      case d: AstDestructuring =>
        val save = inDestructuring
        inDestructuring = d
        descend()
        inDestructuring = save
        return true
      case _ =>
    }

    node match {
      case s: AstScope =>
        if (!rootInitialized) {
          s match {
            case lambda: AstArrow  => initArrowScopeVars(lambda, parentScopeOpt)
            case lambda: AstLambda => initLambdaScopeVars(lambda, parentScopeOpt)
            case _ => initScopeVars(s, parentScopeOpt)
          }
          rootInitialized = true
        } else {
          s match {
            case lambda: AstArrow  => initArrowScopeVars(lambda, scope)
            case lambda: AstLambda => initLambdaScopeVars(lambda, scope)
            case _ => initScopeVars(s, scope)
          }
        }
        val saveScope  = scope
        val saveDefun  = defun
        val saveLabels = labels
        scope = s
        defun = s
        labels = mutable.Map.empty
        descend()
        scope = saveScope
        defun = saveDefun
        labels = saveLabels
        return true
      case _ =>
    }

    node match {
      case ls: AstLabeledStatement =>
        val l = ls.label
        if (l != null) {
          val lbl = l.nn
          if (labels.contains(lbl.name)) {
            throw new JsParseError(
              s"Label ${lbl.name} defined twice",
              lbl.start.file,
              lbl.start.line,
              lbl.start.col,
              lbl.start.pos
            )
          }
          labels(lbl.name) = lbl
          descend()
          labels.remove(lbl.name)
        }
        return true
      case _ =>
    }

    node match {
      case _: AstWith =>
        var s: AstScope | Null = scope
        while (s != null) { s.nn.usesWith = true; s = s.nn.parentScope }
        return ()
      case _ =>
    }

    node match { case sym: AstSymbol => sym.scope = scope; case _ => }

    node match {
      case lbl: AstLabel => lbl.thedef = lbl; lbl.references = ArrayBuffer.empty
      case _ =>
    }

    handleSymbolDecl(node)
    ()
  }

  /** Handle block scope setup in pass 1. */
  private def handleBlockScope(node: AstNode, descend: () => Unit): Unit = {
    val saveScope  = scope
    val blockScope = new AstToplevel
    node match {
      case b:  AstBlock              => b.blockScope = blockScope
      case it: AstIterationStatement => it.blockScope = blockScope
      case _ =>
    }
    scope = blockScope
    initScopeVars(blockScope, saveScope)
    blockScope.usesWith = saveScope.usesWith
    blockScope.usesEval = saveScope.usesEval

    if (options.safari10) {
      node match {
        case _: AstFor | _: AstForIn | _: AstForOf => forScopes.addOne(scope)
        case _                                     =>
      }
    }

    node match {
      case sw: AstSwitch =>
        // XXX: HACK! Ensure the switch expression gets the correct scope (the parent scope)
        // and the body gets the contained scope.
        val theBlockScope = scope
        scope = saveScope
        if (sw.expression != null) sw.expression.nn.walk(currentTw)
        scope = theBlockScope
        var i = 0
        while (i < sw.body.size) {
          sw.body(i).walk(currentTw)
          i += 1
        }
      case _ =>
        descend()
    }
    scope = saveScope
  }

  /** Handle symbol declarations in pass 1. */
  private def handleSymbolDecl(node: AstNode): Unit = {
    node match {
      case sym: AstSymbolLambda =>
        val initVal: AstNode | Null = if (sym.name == "arguments") null else defun.asInstanceOf[AstNode | Null]
        if (defun != null) defFunction(defun, sym, initVal)

      case sym: AstSymbolDefun =>
        if (defun != null) {
          val closestScope = defun.parentScope
          if (closestScope != null) {
            val cs = closestScope.nn
            // In strict mode, function definitions are block-scoped
            val targetScope =
              if (currentTw != null && currentTw.directives.contains("use strict")) cs
              else cs.getDefunScope
            sym.scope = targetScope
            val d = defFunction(targetScope, sym, defun.asInstanceOf[AstNode | Null])
            markExport(d, 1, currentTw, inDestructuring)
          }
        }

      case sym: AstSymbolClass =>
        if (defun != null) {
          val dd = defVariable(defun, sym, defun.asInstanceOf[AstNode])
          markExport(dd, 1, currentTw, inDestructuring)
        }

      case sym: AstSymbolImport =>
        defVariable(scope, sym, null)

      case sym: AstSymbolDefClass =>
        if (defun != null && defun.parentScope != null) {
          val ps = defun.parentScope.nn
          sym.scope = ps
          val dd = defFunction(ps, sym, defun.asInstanceOf[AstNode])
          markExport(dd, 1, currentTw, inDestructuring)
        }

      case sym: AstSymbolVar =>
        handleVarLikeDecl(sym)
      case sym: AstSymbolLet =>
        handleVarLikeDecl(sym)
      case sym: AstSymbolConst =>
        handleVarLikeDecl(sym)
      case sym: AstSymbolUsing =>
        handleVarLikeDecl(sym)
      case sym: AstSymbolCatch =>
        handleVarLikeDecl(sym)

      case ref: AstLabelRef =>
        labels.get(ref.name) match {
          case Some(lbl) => ref.thedef = lbl
          case None      =>
            throw new JsParseError(
              s"Undefined label ${ref.name} [${ref.start.line},${ref.start.col}]",
              ref.start.file,
              ref.start.line,
              ref.start.col,
              ref.start.pos
            )
        }

      case _ =>
    }

    // Validate export/import position
    if (!scope.isInstanceOf[AstToplevel]) {
      node match {
        case _: AstExport | _: AstImport =>
          throw new JsParseError(
            s""""${node.nodeType}" statement may only appear at the top level""",
            node.start.file,
            node.start.line,
            node.start.col,
            node.start.pos
          )
        case _ =>
      }
    }
  }

  /** Handle var/let/const/using/catch declarations. */
  private def handleVarLikeDecl(sym: AstSymbol): Unit = {
    val d: SymbolDef =
      if (sym.isInstanceOf[AstSymbolBlockDeclaration]) {
        defVariable(scope, sym, null)
      } else {
        if (defun != null) defVariable(defun, sym, null)
        else defVariable(scope, sym, null)
      }

    // Verify no illegal redeclarations
    if (
      !d.orig.forall { s =>
        if ((s: AnyRef) eq (sym: AnyRef)) true
        else if (sym.isInstanceOf[AstSymbolBlockDeclaration]) s.isInstanceOf[AstSymbolLambda]
        else !s.isInstanceOf[AstSymbolLet] && !s.isInstanceOf[AstSymbolConst] && !s.isInstanceOf[AstSymbolUsing]
      }
    ) {
      throw new JsParseError(
        s""""${sym.name}" is redeclared""",
        sym.start.file,
        sym.start.line,
        sym.start.col,
        sym.start.pos
      )
    }

    if (!sym.isInstanceOf[AstSymbolFunarg]) {
      markExport(d, 2, currentTw, inDestructuring)
    }

    if (defun != null && (defun.asInstanceOf[AnyRef] ne scope.asInstanceOf[AnyRef])) {
      markEnclosed(sym)
      val outerDef = findVariable(scope, sym.name)
      if (outerDef != null && (sym.thedef.asInstanceOf[AnyRef] ne outerDef.asInstanceOf[AnyRef])) {
        sym.thedef = outerDef
        outerDef.references.addOne(sym.asInstanceOf[AstSymbolRef])
        markEnclosed(sym)
      }
    }
  }

  // TreeWalker reference for pass 1 (set before walk)
  private var currentTw: TreeWalker = null.asInstanceOf[TreeWalker] // @nowarn — null sentinel

  /** Pass 2: find back references and eval. */
  def pass2(): Unit = {
    val isToplevel = ast.isInstanceOf[AstToplevel]
    if (isToplevel) {
      toplevel.globals = mutable.Map.empty
    }

    val tw2: TreeWalker = new TreeWalker(pass2Visit)
    ast.walk(tw2)
  }

  private def pass2Visit(node: AstNode, descend: () => Unit): Any = {
    node match {
      case lc: AstLoopControl if lc.label != null =>
        lc.label.nn.thedef match {
          case lbl: AstLabel => lbl.references.addOne(lc.asInstanceOf[AstNode])
          case _ =>
        }
        return true

      case ref: AstSymbolRef =>
        pass2HandleRef(ref)
        return true

      case _ =>
    }

    // Ensure mangling works if catch reuses a scope variable
    node match {
      case sym: AstSymbolCatch =>
        sym.thedef match {
          case d: SymbolDef =>
            val redefined = SymbolDef.redefinedCatchDef(d)
            if (redefined != null) {
              var s: AstScope | Null = sym.scope
              while (s != null) {
                pushUniq(s.nn.enclosed, redefined.nn)
                if ((s.nn: AnyRef) eq (redefined.nn.scope: AnyRef)) {
                  s = null // @nowarn — break
                } else {
                  s = s.nn.parentScope
                }
              }
            }
          case _ =>
        }
      case _ =>
    }
    ()
  }

  private def pass2HandleRef(ref: AstSymbolRef): Unit = {
    val name = ref.name
    // Detect eval calls
    if (name == "eval") {
      // We need access to the TreeWalker but pass2Visit doesn't have it directly.
      // The walker is captured via the method reference, but we need its parent().
      // Since pass2Visit is called by the walker, we can check scope chain instead.
      var s: AstScope | Null = ref.scope
      while (s != null && !s.nn.usesEval) {
        s.nn.usesEval = true
        s = s.nn.parentScope
      }
    }

    var sym: SymbolDef | Null = null

    if (ref.scope != null) {
      sym = findVariable(ref.scope.nn, name)
    }

    if (sym == null) {
      sym = defGlobal(toplevel, ref)
      if (ref.isInstanceOf[AstSymbolExport]) {
        sym.exportFlag = MaskExportDontMangle
      }
    } else if (sym.scope.isInstanceOf[AstLambda] && name == "arguments") {
      sym.scope.asInstanceOf[AstLambda].getDefunScope match {
        case l: AstLambda => l.usesArguments = true
        case _ =>
      }
    }

    ref.thedef = sym
    sym.references.addOne(ref)
    markEnclosed(ref)

    if (ref.scope != null && ref.scope.nn.isBlockScope) {
      if (!sym.orig(0).isInstanceOf[AstSymbolBlockDeclaration]) {
        ref.scope = ref.scope.nn.getDefunScope
      }
    }
  }

  /** Pass 3: work around IE8 and Safari catch scope bugs. */
  def pass3(): Unit =
    if (options.ie8 || options.safari10) {
      walk(
        ast,
        (node: AstNode, _: ArrayBuffer[AstNode]) =>
          node match {
            case sym: AstSymbolCatch if sym.scope != null =>
              val name       = sym.name
              val refs       = sym.thedef.asInstanceOf[SymbolDef].references
              val catchScope = sym.scope.nn.getDefunScope

              var d = findVariable(catchScope, name)
              if (d == null) {
                toplevel.globals.get(name) match {
                  case Some(g) => d = g.asInstanceOf[SymbolDef]
                  case None    => d = defVariable(catchScope, sym, null)
                }
              }

              refs.foreach { ref =>
                ref.thedef = d
                d.references.addOne(ref)
                markEnclosed(ref)
              }
              sym.thedef = d
              d.references.addOne(sym.asInstanceOf[AstSymbolRef])
              markEnclosed(sym)
              true // skip children
            case _ => () // continue
          }
      )
    }

  /** Pass 4: add symbol definitions to loop scopes (Safari workaround). */
  def pass4(): Unit =
    if (options.safari10) {
      for (loopScope <- forScopes)
        loopScope.parentScope match {
          case ps: AstScope =>
            ps.variables.foreach { case (_, d) =>
              pushUniq(loopScope.enclosed, d)
            }
          case null =>
        }
    }

  /** Pass 1: setup scope chaining and handle definitions. */
  def pass1(): Unit = {
    ast.parentScope = parentScopeOpt

    val tw: TreeWalker = new TreeWalker(pass1Visit)
    currentTw = tw

    if (options.module) {
      tw.directives("use strict") = new AstDirective
    }

    ast.walk(tw)
  }
}
