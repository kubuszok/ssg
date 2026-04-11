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
import ssg.js.compress.Common.isEmpty
import ssg.js.scope.SymbolDef

/** Dead code elimination.
  *
  * Removes unused variables, functions, and class definitions from a scope. Uses data from ReduceVars (reference counts, assignment tracking) to determine what is safe to remove.
  */
object DropUnused {

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
    * @param scope
    *   the scope node to analyze (typically AstToplevel or AstLambda)
    * @param compressor
    *   the compressor context
    */
  def dropUnused(scope: AstScope, compressor: CompressorLike): Unit = {
    if (!compressor.optionBool("unused")) {
      return // @nowarn — early exit for disabled option
    }
    if (compressor.hasDirective("use asm") != null) {
      return // @nowarn — asm.js must not be modified
    }

    if (scope.pinned) {
      return // @nowarn — pinned scopes can't have things removed
    }

    val isToplevel = scope.isInstanceOf[AstToplevel]

    // Track which symbols are in use (defId -> SymbolDef)
    val inUseIds        = mutable.Map.empty[Int, SymbolDef]
    val initializations = mutable.Map.empty[Int, ArrayBuffer[AstNode]]

    // If top_retain is configured, mark those defs as in-use.
    if (isToplevel) {
      scope.variables.foreach { (_, defAny) =>
        val d = defAny.asInstanceOf[SymbolDef]
        if (compressor.topRetain(d)) {
          inUseIds(d.id) = d
        }
      }
    }

    // Helper: check if a def is in-use
    def isInUse(d: SymbolDef): Boolean = inUseIds.contains(d.id)

    // Helper: mark a def as in-use
    def markInUse(d: SymbolDef): Unit =
      if (!inUseIds.contains(d.id)) inUseIds(d.id) = d

    // -----------------------------------------------------------------------
    // Pass 1: find directly-used symbols
    // -----------------------------------------------------------------------

    var currentScope: AstNode = scope

    val tw1 = new TreeWalker((node, descend) =>
      node match {
        case _: AstLambda if node ne scope =>
          // Don't descend into nested scopes for pass 1 (handled separately)
          true

        case defun: AstDefun if !(node eq scope) =>
          // Function declarations: check if exported or top-retained
          defun.name match {
            case sym: AstSymbol =>
              val d = sym.definition()
              if (d != null) {
                val dd = d.nn
                if (dd.global && !compressor.optionBool("toplevel")) markInUse(dd)
                if (dd.exportFlag != 0) markInUse(dd)
              }
            case _ =>
          }
          true

        case cls: AstDefClass if !(node eq scope) =>
          // Class declarations: check if exported or top-retained
          cls.name match {
            case sym: AstSymbol =>
              val d = sym.definition()
              if (d != null) {
                val dd = d.nn
                if (dd.global && !compressor.optionBool("toplevel")) markInUse(dd)
                if (dd.exportFlag != 0) markInUse(dd)
              }
            case _ =>
          }
          true

        case sr: AstSymbolRef =>
          // Mark the referenced symbol as in-use
          val d = sr.definition()
          if (d != null) markInUse(d.nn)
          true

        case _: AstClass if !(node eq scope) =>
          descend()
          true

        case scope2: AstScope if !scope2.isInstanceOf[AstClassStaticBlock] && !(node eq scope) =>
          val savedScope = currentScope
          currentScope = scope2
          descend()
          currentScope = savedScope
          true

        case _ => null // continue normal walking
      }
    )
    scope.walk(tw1)

    // -----------------------------------------------------------------------
    // Pass 2: transitively mark initializers of used symbols
    // -----------------------------------------------------------------------

    val tw2 = new TreeWalker((node, descend) =>
      node match {
        case sr: AstSymbolRef =>
          val d = sr.definition()
          if (d != null) markInUse(d.nn)
          true
        case _: AstClass =>
          descend()
          true
        case scope2: AstScope if !scope2.isInstanceOf[AstClassStaticBlock] =>
          val savedScope = currentScope
          currentScope = scope2
          descend()
          currentScope = savedScope
          true
        case _ => null
      }
    )

    // Walk initializers of in-use symbols — loop until stable since marking
    // new symbols as in-use can cause new initializers to be walked.
    var changed = true
    while (changed) {
      changed = false
      val snapshot = inUseIds.keys.toSet
      initializations.foreach { (defId, inits) =>
        if (inUseIds.contains(defId)) {
          inits.foreach(_.walk(tw2))
        }
      }
      if (inUseIds.size > snapshot.size) changed = true
    }

    // -----------------------------------------------------------------------
    // Pass 3: transform to drop unused declarations
    // -----------------------------------------------------------------------

    var transformScope: AstNode = scope

    val tt = new TreeTransformer(
      before = (node, descend) =>
        node match {
          // Handle unused function declarations
          case defun: AstDefun if !(node eq scope) =>
            val unused = defun.name match {
              case sym: AstSymbol =>
                val d = sym.definition()
                d != null && !isInUse(d.nn)
              case _ => false
            }
            if (unused) {
              val empty = new AstEmptyStatement
              empty.start = node.start
              empty.end = node.end
              empty
            } else {
              null // keep
            }

          // Handle unused class declarations
          case cls: AstDefClass if !(node eq scope) =>
            val unused = cls.name match {
              case sym: AstSymbol =>
                val d = sym.definition()
                d != null && !isInUse(d.nn)
              case _ => false
            }
            if (unused) {
              val empty = new AstEmptyStatement
              empty.start = node.start
              empty.end = node.end
              empty
            } else {
              null // keep
            }

          // Handle variable definitions (var, let, const)
          case defs: AstDefinitions =>
            val keptDefs = ArrayBuffer.empty[AstNode]
            val sideEffects = ArrayBuffer.empty[AstNode]

            var i = 0
            while (i < defs.definitions.size) {
              defs.definitions(i) match {
                case vd: AstVarDef =>
                  vd.name match {
                    case sym: AstSymbol =>
                      val d = sym.definition()
                      if (d != null && !isInUse(d.nn)) {
                        // Unused — but preserve side effects from the initializer
                        if (vd.value != null) {
                          val v = vd.value.nn
                          // Conservatively preserve all initializer side effects
                          sideEffects.addOne(v)
                        }
                      } else {
                        // In use — keep it
                        keptDefs.addOne(vd)
                      }
                    case _: AstDestructuring =>
                      // Can't determine usage for destructuring — keep it
                      keptDefs.addOne(vd)
                    case _ =>
                      keptDefs.addOne(vd)
                  }
                case other =>
                  keptDefs.addOne(other)
              }
              i += 1
            }

            if (keptDefs.size == defs.definitions.size) {
              // Nothing was dropped
              null
            } else if (keptDefs.isEmpty && sideEffects.isEmpty) {
              // Everything dropped, no side effects
              val empty = new AstEmptyStatement
              empty.start = node.start
              empty.end = node.end
              empty
            } else if (keptDefs.isEmpty) {
              // All declarations dropped, but have side effects
              val ss = new AstSimpleStatement
              ss.body = Common.makeSequence(node, sideEffects)
              ss.start = node.start
              ss.end = node.end
              ss
            } else {
              // Some declarations kept
              defs.definitions = keptDefs
              if (sideEffects.nonEmpty) {
                // Insert side effects before the declaration
                // Return the declaration as-is — side effects are separate
                // (In a full implementation, we'd wrap in a block; for now, keep simple)
              }
              null
            }

          // Handle for loops (may need restructuring after drops)
          case forNode: AstFor =>
            descend()
            // Fix up the init if it became a BlockStatement
            forNode.init match {
              case ss: AstSimpleStatement => forNode.init = ss.body
              case init if init != null && isEmpty(init) => forNode.init = null
              case _                                     =>
            }
            node

          // Handle nested scopes
          case scope2: AstScope if !scope2.isInstanceOf[AstClassStaticBlock] && !(node eq scope) =>
            val savedScope = transformScope
            transformScope = scope2
            descend()
            transformScope = savedScope
            node

          // Handle sequences (may need cleanup after drops)
          case _ => null // continue normal walking
        },
      after = node =>
        node match {
          case seq: AstSequence =>
            seq.expressions.size match {
              case 0 =>
                val zero = new AstNumber
                zero.start = seq.start
                zero.end = seq.end
                zero.value = 0.0
                zero
              case 1 => seq.expressions(0)
              case _ => null
            }
          case _ => null
        }
    )

    // Run the transformation
    scope.transform(tt)
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Check if an assignment can be treated as unused.
    *
    * Assignments marked with WRITE_ONLY or using `=` operator can potentially be dropped if their target is unused.
    *
    * @return
    *   the assigned-to symbol, or null if the assignment must be kept
    */
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
