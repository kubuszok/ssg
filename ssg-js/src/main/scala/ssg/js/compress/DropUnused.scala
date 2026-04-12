/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Dead code elimination: remove unused variables and functions.
 *
 * Performs a multi-pass analysis to identify and remove unused declarations:
 * 1. Walk the scope to find which symbols are directly referenced
 * 2. Transitively mark symbols referenced by used initializers
 * 3. Transform the AST to remove unused declarations
 *
 * Ported from: terser lib/compress/drop-unused.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*, drop_unused -> dropUnused, scan_ref_scoped ->
 *     scanRefScoped, assign_as_unused -> assignAsUnused, in_use_ids ->
 *     inUseIds, fixed_ids -> fixedIds, var_defs_by_id -> varDefsById
 *   Convention: Object with methods, TreeWalker/TreeTransformer pattern matching
 *   Idiom: boundary/break instead of return, mutable.Map/Set for tracking
 */
package ssg
package js
package compress

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.Common.{isEmpty, makeSequence, maintainThisBinding, canBeEvictedFromBlock, isRefOf}
import ssg.js.compress.Inference.{hasSideEffects, isUsedInExpression}
import ssg.js.compress.DropSideEffectFree.dropSideEffectFree
import ssg.js.scope.SymbolDef

/** Dead code elimination.
  *
  * Removes unused variables, functions, and class definitions from a scope. Uses data from ReduceVars (reference counts, assignment tracking) to determine what is safe to remove.
  */
object DropUnused {

  // Pattern to check if keep_assign option is set
  private val rKeepAssign = "keep_assign".r

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Remove unused declarations from a scope.
    *
    * Performs a three-pass analysis:
    *   1. Find directly-used symbols in this scope
    *   2. Transitively mark symbols used by initializers of used symbols
    *   3. Transform the AST to drop unused declarations
    *
    * @param self
    *   the scope node to analyze (typically AstToplevel or AstLambda)
    * @param compressor
    *   the compressor context
    */
  def dropUnused(self: AstScope, compressor: CompressorLike): Unit = {
    if (!compressor.optionBool("unused")) {
      return // @nowarn — early exit for disabled option
    }
    if (compressor.hasDirective("use asm") != null) {
      return // @nowarn — asm.js must not be modified
    }
    if (self.variables == null) {
      return // @nowarn — not really a scope (eg: AST_Class)
    }

    if (self.pinned) {
      return // @nowarn — pinned scopes can't have things removed
    }

    val isToplevel = self.isInstanceOf[AstToplevel]
    val dropFuncs  = !isToplevel || compressor.toplevel.funcs
    val dropVars   = !isToplevel || compressor.toplevel.vars

    // Check if we should treat assignments as unused
    val keepAssign = rKeepAssign.findFirstIn(compressor.option("unused").toString).isDefined

    // Create the context object that holds all shared state
    val ctx = new DropUnusedContext(self, compressor, dropFuncs, dropVars, keepAssign)

    // If top_retain is configured, mark those defs as in-use.
    if (isToplevel) {
      self.variables.foreach { (_, defAny) =>
        defAny match {
          case sd: SymbolDef if compressor.topRetain(sd) =>
            ctx.inUseIds(sd.id) = sd
          case _ =>
        }
      }
    }

    // -----------------------------------------------------------------------
    // Pass 1: find directly-used symbols
    // -----------------------------------------------------------------------

    val pass1Walker = new Pass1Walker(ctx)
    self.walk(pass1Walker)

    // -----------------------------------------------------------------------
    // Pass 2: transitively mark initializers of used symbols
    // -----------------------------------------------------------------------

    val pass2Walker = new Pass2Walker(ctx)
    ctx.inUseIds.foreach { (defId, _) =>
      ctx.initializations.get(defId) match {
        case Some(inits) =>
          inits.foreach(_.walk(pass2Walker))
        case None =>
      }
    }

    // -----------------------------------------------------------------------
    // Pass 3: transform to drop unused declarations
    // -----------------------------------------------------------------------

    val transformer = new Pass3Transformer(ctx)
    // Use walk since transform is not yet implemented on AstScope
    // The transformer modifies the tree in-place via its callbacks
    self.walk(transformer)
  }

  // -----------------------------------------------------------------------
  // Context class holding all shared state
  // -----------------------------------------------------------------------

  /** Holds shared state for the three passes of drop_unused. */
  private class DropUnusedContext(
    val self:       AstScope,
    val compressor: CompressorLike,
    val dropFuncs:  Boolean,
    val dropVars:   Boolean,
    val keepAssign: Boolean
  ) {
    // Track which symbols are in use
    val inUseIds:        mutable.Map[Int, SymbolDef]            = mutable.Map.empty
    val fixedIds:        mutable.Map[Int, AstNode]              = mutable.Map.empty
    val varDefsById:     mutable.Map[Int, ArrayBuffer[AstNode]] = mutable.Map.empty
    val initializations: mutable.Map[Int, ArrayBuffer[AstNode]] = mutable.Map.empty

    // Current scope during walking
    var currentScope: AstScope = self

    // Current scope during transformation
    var transformScope: AstScope = self

    // Helper: add value to a map of id -> ArrayBuffer
    def mapAdd[T](map: mutable.Map[Int, ArrayBuffer[T]], id: Int, value: T): Unit =
      map.get(id) match {
        case Some(arr) => arr.addOne(value)
        case None      => map(id) = ArrayBuffer(value)
      }

    // Helper: get definition from a node (AstSymbol.thedef)
    def getDefinition(node: AstNode): SymbolDef | Null =
      node match {
        case sym: AstSymbol =>
          sym.thedef match {
            case sd: SymbolDef => sd
            case _             => null
          }
        case _ => null
      }

    // Helper: assign_as_unused — checks if an assignment can be treated as unused
    def assignAsUnused(node: AstNode): AstNode | Null =
      if (keepAssign) null
      else
        node match {
          case assign: AstAssign if !assign.logical && (hasFlag(assign, WRITE_ONLY) || assign.operator == "=") =>
            assign.left
          case unary: AstUnary if hasFlag(unary, WRITE_ONLY) =>
            unary.expression
          case _ => null
        }

    // Helper to get fixed_value from a symbol
    def getFixedValue(sym: AstSymbol): AstNode | Null =
      getDefinition(sym) match {
        case sd: SymbolDef =>
          sd.fixedValue match {
            case n: AstNode => n
            case _          => null
          }
        case null => null
      }

    // scan_ref_scoped: Helper for marking references as in-use
    def scanRefScoped(tw: TreeWalker, node: AstNode, descend: () => Unit): Boolean = {
      var nodeDef: SymbolDef | Null = null

      val sym = assignAsUnused(node)
      sym match {
        case sr: AstSymbolRef =>
          val leftOk = node match {
            case assign: AstAssign =>
              !isRefOf[AstSymbolBlockDeclaration](assign.left)
            case _ => true
          }
          if (leftOk) {
            val srDef = getDefinition(sr)
            if (srDef != null && self.variables.get(sr.name).contains(srDef)) {
              nodeDef = srDef
              node match {
                case assign: AstAssign =>
                  assign.right.walk(tw)
                  if (!srDef.nn.chained && getFixedValue(sr) == assign.right) {
                    fixedIds(srDef.nn.id) = node
                  }
                case _ =>
              }
              return true
            }
          }
        case _ =>
      }

      node match {
        case sr: AstSymbolRef =>
          nodeDef = getDefinition(sr)
          if (nodeDef != null) {
            val nd = nodeDef.nn
            if (!inUseIds.contains(nd.id)) {
              inUseIds(nd.id) = nd
              // Handle catch variable redefinition
              nd.orig.headOption match {
                case Some(_: AstSymbolCatch) =>
                  if (nd.scope.isBlockScope) {
                    val defunScope = nd.scope.getDefunScope
                    defunScope.variables.get(nd.name) match {
                      case Some(redef: SymbolDef) =>
                        inUseIds(redef.id) = redef
                      case _ =>
                    }
                  }
                case _ =>
              }
            }
          }
          return true

        case _: AstClass =>
          descend()
          return true

        case scope2: AstScope if !scope2.isInstanceOf[AstClassStaticBlock] =>
          val savedScope = currentScope
          currentScope = scope2
          descend()
          currentScope = savedScope
          return true

        case _ =>
      }

      false
    }
  }

  // -----------------------------------------------------------------------
  // Pass 1 Walker
  // -----------------------------------------------------------------------

  /** Pass 1: Find directly-used symbols in this scope. */
  private class Pass1Walker(ctx: DropUnusedContext) extends TreeWalker() {

    override def _visit(node: AstNode, descend: () => Unit): Unit = {
      push(node)
      val ret = visitNode(node, descend)
      if (ret == null || ret == false || ret == (())) {
        descend()
      }
      pop()
    }

    def visitNode(node: AstNode, descend: () => Unit): Any = {
      // Handle uses_arguments
      node match {
        case lambda: AstLambda if lambda.usesArguments =>
          // Check if not in strict mode
          if (this.hasDirective("use strict") == null) {
            lambda.argnames.foreach {
              case sd: AstSymbolDeclaration =>
                val d = ctx.getDefinition(sd)
                if (d != null) {
                  ctx.inUseIds(d.nn.id) = d.nn
                }
              case _ =>
            }
          }
        case _ =>
      }

      if (node eq ctx.self) {
        return null // Continue descending into self
      }

      // Handle classes with side effects
      node match {
        case cls: AstClass if hasSideEffects(cls, ctx.compressor) =>
          // Simplified: just descend (is_self_referential and visit_nondeferred_class_parts not ported)
          descend()
          return true
        case _ =>
      }

      // Handle function/class declarations
      node match {
        case defun: AstDefun =>
          defun.name match {
            case sym: AstSymbol =>
              val nodeDef = ctx.getDefinition(sym)
              if (nodeDef != null) {
                val nd       = nodeDef.nn
                val parentNode = this.parent()
                val inExport = parentNode.isInstanceOf[AstExport]
                if (inExport || (!ctx.dropFuncs && (ctx.currentScope eq ctx.self))) {
                  if (nd.global) {
                    ctx.inUseIds(nd.id) = nd
                  }
                }
                ctx.mapAdd(ctx.initializations, nd.id, node)
              }
            case _ =>
          }
          return true // don't go in nested scopes

        case defcls: AstDefClass =>
          defcls.name match {
            case sym: AstSymbol =>
              val nodeDef = ctx.getDefinition(sym)
              if (nodeDef != null) {
                val nd       = nodeDef.nn
                val parentNode = this.parent()
                val inExport = parentNode.isInstanceOf[AstExport]
                if (inExport || (!ctx.dropFuncs && (ctx.currentScope eq ctx.self))) {
                  if (nd.global) {
                    ctx.inUseIds(nd.id) = nd
                  }
                }
                ctx.mapAdd(ctx.initializations, nd.id, node)
              }
            case _ =>
          }
          return true // don't go in nested scopes

        case _ =>
      }

      // In the root scope, we drop things. In inner scopes, we just check for uses.
      val inRootScope = ctx.currentScope eq ctx.self

      // Track function arguments in root scope
      node match {
        case funarg: AstSymbolFunarg if inRootScope =>
          val d = ctx.getDefinition(funarg)
          if (d != null) {
            ctx.mapAdd(ctx.varDefsById, d.nn.id, funarg)
          }
        case _ =>
      }

      // Handle variable definitions in root scope
      node match {
        case defs: AstDefinitions if inRootScope =>
          val parentNode = this.parent()
          val inExport   = parentNode.isInstanceOf[AstExport]

          defs.definitions.foreach {
            case vdef: AstVarDef =>
              // Track var declarations
              vdef.name match {
                case sv: AstSymbolVar =>
                  val d = ctx.getDefinition(sv)
                  if (d != null) {
                    ctx.mapAdd(ctx.varDefsById, d.nn.id, vdef)
                  }
                case _ =>
              }

              // Mark exported or non-droppable vars as in-use
              if (inExport || !ctx.dropVars) {
                walkSymbolDeclarations(vdef.name, (sym: AstSymbolDeclaration) => {
                  val d = ctx.getDefinition(sym)
                  if (d != null && d.nn.global) {
                    ctx.inUseIds(d.nn.id) = d.nn
                  }
                })
              }

              // Walk destructuring patterns
              vdef.name match {
                case _: AstDestructuring =>
                  vdef.walk(this)
                case _ =>
              }

              // Track initializers with values
              vdef.name match {
                case sym: AstSymbolDeclaration if vdef.value != null =>
                  val nodeDef = ctx.getDefinition(sym)
                  if (nodeDef != null) {
                    val nd = nodeDef.nn
                    ctx.mapAdd(ctx.initializations, nd.id, vdef.value.nn)
                    if (!nd.chained && ctx.getFixedValue(sym) == vdef.value) {
                      ctx.fixedIds(nd.id) = vdef
                    }
                    if (hasSideEffects(vdef.value.nn, ctx.compressor)) {
                      vdef.value.nn.walk(this)
                    }
                  }
                case _ =>
              }
            case other =>
              other.walk(this)
          }
          return true

        case _ =>
      }

      // Use scan_ref_scoped for everything else
      if (ctx.scanRefScoped(this, node, descend)) true else null
    }
  }

  // -----------------------------------------------------------------------
  // Pass 2 Walker
  // -----------------------------------------------------------------------

  /** Pass 2: Transitively mark initializers of used symbols. */
  private class Pass2Walker(ctx: DropUnusedContext) extends TreeWalker() {

    override def _visit(node: AstNode, descend: () => Unit): Unit = {
      push(node)
      val ret = visitNode(node, descend)
      if (ret == null || ret == false || ret == (())) {
        descend()
      }
      pop()
    }

    def visitNode(node: AstNode, descend: () => Unit): Any =
      if (ctx.scanRefScoped(this, node, descend)) true else null
  }

  // -----------------------------------------------------------------------
  // Pass 3 Transformer
  // -----------------------------------------------------------------------

  /** Pass 3: Transform to drop unused declarations. */
  private class Pass3Transformer(ctx: DropUnusedContext) extends TreeTransformer() {

    override def _visit(node: AstNode, descend: () => Unit): Unit = {
      push(node)
      val ret = beforeTransform(node, descend)
      if (ret == null || ret == false || ret == (())) {
        descend()
      }
      // Handle the after callback
      afterTransform(node)
      pop()
    }

    def beforeTransform(node: AstNode, descend: () => Unit): Any = {
      val parentNode = this.parent()
      val inList     = isInList(this)

      // Handle unused assignments
      if (ctx.dropVars) {
        val sym = ctx.assignAsUnused(node)
        sym match {
          case sr: AstSymbolRef =>
            val d = ctx.getDefinition(sr)
            if (d != null) {
              val inUse = ctx.inUseIds.contains(d.nn.id)
              node match {
                case assign: AstAssign =>
                  if (!inUse || (ctx.fixedIds.contains(d.nn.id) && ctx.fixedIds.get(d.nn.id) != Some(node))) {
                    val assignee = transformNode(assign.right, this)
                    if (!inUse && !hasSideEffects(assignee, ctx.compressor) && !isUsedInExpression(this)) {
                      return if (inList) SkipMarker else makeNumber(node, 0)
                    }
                    return maintainThisBinding(parentNode, node, assignee)
                  }
                case _ =>
                  if (!inUse) {
                    return if (inList) SkipMarker else makeNumber(node, 0)
                  }
              }
            }
          case _ =>
        }
      }

      // Only process in root scope
      if (ctx.transformScope ne ctx.self) {
        return null
      }

      // Handle anonymous function/class names
      node match {
        case cls: AstClassExpression if cls.name != null =>
          cls.name.nn match {
            case sym: AstSymbol =>
              val d = ctx.getDefinition(sym)
              if (d != null && (!ctx.inUseIds.contains(d.nn.id) || d.nn.orig.size > 1)) {
                // Don't keep unused class expression name
                if (!keepName(ctx.compressor, "keep_classnames", d.nn.name)) {
                  cls.name = null
                }
              }
            case _ =>
          }
        case fn: AstFunction if fn.name != null =>
          fn.name.nn match {
            case sym: AstSymbol =>
              val d = ctx.getDefinition(sym)
              if (d != null && (!ctx.inUseIds.contains(d.nn.id) || d.nn.orig.size > 1)) {
                if (!keepName(ctx.compressor, "keep_fnames", d.nn.name)) {
                  fn.name = null
                }
              }
            case _ =>
          }
        case _ =>
      }

      // Trim unused lambda arguments
      node match {
        case lambda: AstLambda if !lambda.isInstanceOf[AstAccessor] =>
          val trim = !ctx.compressor.optionBool("keep_fargs") ||
            (parentNode match {
              case call: AstCall if (call.expression eq lambda) && !lambda.pinned =>
                lambda.name match {
                  case null => true
                  case sym: AstSymbol =>
                    val d = ctx.getDefinition(sym)
                    d != null && d.nn.references.isEmpty
                  case _ => true
                }
              case _ => false
            })

          var i          = lambda.argnames.size
          var shouldTrim = trim
          while ({ i -= 1; i >= 0 }) {
            var sym = lambda.argnames(i)
            sym match {
              case exp: AstExpansion => sym = exp.expression
              case _                 =>
            }
            sym match {
              case da: AstDefaultAssign => sym = da.left
              case _                    =>
            }
            // Do not drop destructuring arguments — they constitute a type assertion
            sym match {
              case _: AstDestructuring =>
                shouldTrim = false
              case argSym: AstSymbol =>
                val d = ctx.getDefinition(argSym)
                if (d != null && !ctx.inUseIds.contains(d.nn.id)) {
                  setFlag(argSym, UNUSED)
                  if (shouldTrim) {
                    lambda.argnames.remove(i)
                  }
                } else {
                  shouldTrim = false
                }
              case _ =>
                shouldTrim = false
            }
          }
        case _ =>
      }

      // Handle unused class declarations
      node match {
        case defcls: AstDefClass if !(node eq ctx.self) =>
          defcls.name match {
            case sym: AstSymbol =>
              val d = ctx.getDefinition(sym)
              if (d != null) {
                descend()
                val keepClass = (d.nn.global && !ctx.dropFuncs) || ctx.inUseIds.contains(d.nn.id)
                if (!keepClass) {
                  val kept = dropSideEffectFree(node, ctx.compressor)
                  d.nn.eliminated += 1
                  if (kept == null) {
                    return if (inList) SkipMarker else makeEmpty(node)
                  }
                  return kept
                }
                return node
              }
            case _ =>
          }
        case _ =>
      }

      // Handle unused function declarations
      node match {
        case defun: AstDefun if !(node eq ctx.self) =>
          defun.name match {
            case sym: AstSymbol =>
              val d = ctx.getDefinition(sym)
              if (d != null) {
                val keep = (d.nn.global && !ctx.dropFuncs) || ctx.inUseIds.contains(d.nn.id)
                if (!keep) {
                  d.nn.eliminated += 1
                  return if (inList) SkipMarker else makeEmpty(node)
                }
              }
            case _ =>
          }
        case _ =>
      }

      // Handle variable definitions
      node match {
        case defs: AstDefinitions =>
          // Don't process for-in init
          val isForInInit = parentNode match {
            case forIn: AstForIn => forIn.init eq node
            case _               => false
          }
          if (!isForInInit) {
            return processDefinitions(defs, parentNode, inList, ctx, this)
          }
        case _ =>
      }

      // Handle for loops
      node match {
        case forNode: AstFor =>
          descend()
          var block: AstBlockStatement | Null = null
          forNode.init match {
            case bs: AstBlockStatement =>
              block = bs
              forNode.init = bs.body.remove(bs.body.size - 1)
              bs.body.addOne(forNode)
            case _ =>
          }
          forNode.init match {
            case ss: AstSimpleStatement                => forNode.init = ss.body
            case init if init != null && isEmpty(init) => forNode.init = null
            case _                                     =>
          }
          if (block != null) {
            return if (inList) SpliceMarker(block.nn.body) else block
          }
          return node

        case _ =>
      }

      // Handle labeled for loops
      node match {
        case ls: AstLabeledStatement =>
          ls.body match {
            case _: AstFor =>
              descend()
              ls.body match {
                case block: AstBlockStatement =>
                  ls.body = block.body.remove(block.body.size - 1)
                  block.body.addOne(ls)
                  return if (inList) SpliceMarker(block.body) else block
                case _ =>
              }
              return node
            case _ =>
          }
        case _ =>
      }

      // Handle block statements
      node match {
        case block: AstBlockStatement =>
          descend()
          if (inList && block.body.forall(canBeEvictedFromBlock)) {
            return SpliceMarker(block.body)
          }
          return node
        case _ =>
      }

      // Handle nested scopes
      node match {
        case scope2: AstScope if !scope2.isInstanceOf[AstClassStaticBlock] =>
          val savedScope = ctx.transformScope
          ctx.transformScope = scope2
          descend()
          ctx.transformScope = savedScope
          return node
        case _ =>
      }

      null
    }

    def afterTransform(node: AstNode): Any =
      node match {
        case seq: AstSequence =>
          seq.expressions.size match {
            case 0 => makeNumber(node, 0)
            case 1 => seq.expressions(0)
            case _ => null
          }
        case _ => null
      }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Marker object for nodes that should be skipped in a list. */
  private object SkipMarker extends AstNode {
    def nodeType: String = "_Skip"
  }

  /** Marker class for nodes that should be spliced into a list. */
  private final case class SpliceMarker(nodes: ArrayBuffer[AstNode]) extends AstNode {
    def nodeType: String = "_Splice"
  }

  /** Create an AstNumber node with the given value. */
  private def makeNumber(orig: AstNode, value: Double): AstNumber = {
    val num = new AstNumber
    num.start = orig.start
    num.end = orig.end
    num.value = value
    num
  }

  /** Create an AstEmptyStatement. */
  private def makeEmpty(orig: AstNode): AstEmptyStatement = {
    val empty = new AstEmptyStatement
    empty.start = orig.start
    empty.end = orig.end
    empty
  }

  /** Check if the current node is in a list context. */
  private def isInList(tt: TreeTransformer): Boolean = {
    val parentNode = tt.parent()
    parentNode match {
      case _: AstBlock       => true
      case _: AstDefinitions => true
      case _                 => false
    }
  }

  /** Transform a node using the given transformer. */
  private def transformNode(node: AstNode, tt: TreeTransformer): AstNode = {
    // Walk through the transformer
    val result = tt.before(node, () => {})
    if (result != null && result != false && result != (())) {
      result.asInstanceOf[AstNode]
    } else {
      node
    }
  }

  /** Walk all symbol declarations in a pattern, calling fn for each. */
  private def walkSymbolDeclarations(node: AstNode | Null, fn: AstSymbolDeclaration => Unit): Unit =
    if (node != null) {
      node.nn match {
        case sd: AstSymbolDeclaration => fn(sd)
        case dest: AstDestructuring =>
          dest.names.foreach(n => walkSymbolDeclarations(n, fn))
        case da: AstDefaultAssign =>
          walkSymbolDeclarations(da.left, fn)
        case exp: AstExpansion =>
          walkSymbolDeclarations(exp.expression, fn)
        case _ =>
      }
    }

  /** Check keep_name option. */
  private def keepName(compressor: CompressorLike, option: String, name: String): Boolean = {
    val opt = compressor.option(option)
    opt match {
      case true                         => true
      case false                        => false
      case r: scala.util.matching.Regex => r.findFirstIn(name).isDefined
      case s: String                    => s.r.findFirstIn(name).isDefined
      case _                            => false
    }
  }

  /** Process variable definitions, potentially dropping unused ones. */
  private def processDefinitions(
    defs:       AstDefinitions,
    parentNode: AstNode | Null,
    inList:     Boolean,
    ctx:        DropUnusedContext,
    tt:         TreeTransformer
  ): AstNode | Null = {
    val dropBlock = (parentNode match {
      case _: AstToplevel => false
      case _              => true
    }) && !defs.isInstanceOf[AstVar]

    val body        = ArrayBuffer.empty[AstNode]
    val head        = ArrayBuffer.empty[AstNode]
    val tail        = ArrayBuffer.empty[AstNode]
    var sideEffects = ArrayBuffer.empty[AstNode]

    defs.definitions.foreach {
      case vdef: AstVarDef =>
        // Transform the value if present
        if (vdef.value != null) {
          vdef.value = transformNode(vdef.value.nn, tt)
        }

        val isDestructure = vdef.name.isInstanceOf[AstDestructuring]
        val sym: SymbolDef | Null =
          if (isDestructure) {
            // Create fake SymbolDef for destructuring
            new SymbolDef(null.asInstanceOf[AstScope], new AstSymbolVar { name = "<destructure>" })
          } else {
            vdef.name match {
              case s: AstSymbol =>
                s.thedef match {
                  case sd: SymbolDef => sd
                  case _             => null
                }
              case _ => null
            }
          }

        if (sym == null) {
          tail.addOne(vdef)
        } else if (dropBlock && sym.nn.global) {
          tail.addOne(vdef)
        } else if (
          !(ctx.dropVars || dropBlock) ||
          (isDestructure && (
            vdef.name.asInstanceOf[AstDestructuring].names.nonEmpty ||
            vdef.name.asInstanceOf[AstDestructuring].isArray ||
            ctx.compressor.option("pure_getters") != true
          )) ||
          ctx.inUseIds.contains(sym.nn.id)
        ) {
          // Keep this definition
          if (vdef.value != null && ctx.fixedIds.contains(sym.nn.id) && ctx.fixedIds.get(sym.nn.id) != Some(vdef)) {
            vdef.value = dropSideEffectFree(vdef.value.nn, ctx.compressor)
          }

          // Track whether we eliminated this VarDef and should skip adding to head/tail
          var eliminated = false

          vdef.name match {
            case sv: AstSymbolVar =>
              ctx.varDefsById.get(sym.nn.id) match {
                case Some(varDefs) if varDefs.size > 1 =>
                  val origIdx = sym.nn.orig.indexOf(sv)
                  if (vdef.value == null || origIdx > sym.nn.eliminated) {
                    if (vdef.value != null) {
                      // Convert to assignment
                      val ref = new AstSymbolRef
                      ref.start = sv.start
                      ref.end = sv.end
                      ref.name = sv.name
                      ref.thedef = sv.thedef
                      ref.scope = sv.scope
                      sym.nn.references.addOne(ref)

                      val assign = new AstAssign
                      assign.start = vdef.start
                      assign.end = vdef.end
                      assign.operator = "="
                      assign.logical = false
                      assign.left = ref
                      assign.right = vdef.value.nn

                      if (ctx.fixedIds.get(sym.nn.id).contains(vdef)) {
                        ctx.fixedIds(sym.nn.id) = assign
                      }
                      sideEffects.addOne(transformNode(assign, tt))
                    }
                    varDefs -= vdef
                    sym.nn.eliminated += 1
                    eliminated = true
                  }
                case _ =>
              }
            case _ =>
          }

          if (!eliminated) {
            if (vdef.value != null) {
              if (sideEffects.nonEmpty) {
                if (tail.nonEmpty) {
                  sideEffects.addOne(vdef.value.nn)
                  vdef.value = makeSequence(vdef.value.nn, sideEffects)
                } else {
                  val ss = new AstSimpleStatement
                  ss.start = defs.start
                  ss.end = defs.end
                  ss.body = makeSequence(defs, sideEffects)
                  body.addOne(ss)
                }
                sideEffects = ArrayBuffer.empty
              }
              tail.addOne(vdef)
            } else {
              head.addOne(vdef)
            }
          }
        } else {
          // Check for catch variable
          sym.nn.orig.headOption match {
            case Some(_: AstSymbolCatch) =>
              val value =
                if (vdef.value != null) dropSideEffectFree(vdef.value.nn, ctx.compressor)
                else null
              if (value != null) {
                sideEffects.addOne(value.nn)
              }
              vdef.value = null
              head.addOne(vdef)

            case _ =>
              // Drop this definition, but keep side effects
              val value =
                if (vdef.value != null) dropSideEffectFree(vdef.value.nn, ctx.compressor)
                else null
              if (value != null) {
                sideEffects.addOne(value.nn)
              }
              sym.nn.eliminated += 1
          }
        }
      case other =>
        // Non-VarDef in definitions — keep it
        tail.addOne(other)
    }

    if (head.nonEmpty || tail.nonEmpty) {
      defs.definitions = ArrayBuffer.empty
      defs.definitions.addAll(head)
      defs.definitions.addAll(tail)
      body.addOne(defs)
    }

    if (sideEffects.nonEmpty) {
      val ss = new AstSimpleStatement
      ss.start = defs.start
      ss.end = defs.end
      ss.body = makeSequence(defs, sideEffects)
      body.addOne(ss)
    }

    body.size match {
      case 0 =>
        if (inList) SkipMarker else makeEmpty(defs)
      case 1 =>
        body(0)
      case _ =>
        if (inList) {
          SpliceMarker(body)
        } else {
          val block = new AstBlockStatement
          block.start = defs.start
          block.end = defs.end
          block.body = body
          block
        }
    }
  }

  /** Public API for assignAsUnused (for external callers). */
  def assignAsUnused(node: AstNode, keepAssign: Boolean): AstNode | Null =
    if (keepAssign) null
    else {
      node match {
        case assign: AstAssign if !assign.logical && (hasFlag(assign, WRITE_ONLY) || assign.operator == "=") =>
          assign.left
        case unary: AstUnary if hasFlag(unary, WRITE_ONLY) =>
          unary.expression
        case _ => null
      }
    }
}
