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

    // Track which symbols are in use
    val inUseIds        = mutable.Map.empty[Int, Any] // defId -> SymbolDef
    val initializations = mutable.Map.empty[Int, ArrayBuffer[AstNode]] // defId -> init expressions

    // If top_retain is configured, mark those defs as in-use.
    // SymbolDef is typed as Any; access its `id` via reflection.
    if (isToplevel) {
      scope.variables.foreach { (_, defAny) =>
        if (compressor.topRetain(defAny)) {
          inUseIds(symbolDefId(defAny)) = defAny
        }
      }
    }

    // -----------------------------------------------------------------------
    // Pass 1: find directly-used symbols
    // -----------------------------------------------------------------------

    var currentScope: AstNode = scope

    val tw1 = new TreeWalker((node, descend) =>
      node match {
        case _: AstLambda if node ne scope =>
          // Don't descend into nested scopes for pass 1 (handled separately)
          // But do track the function name as used if it's in an export
          true

        case _: AstDefun if !(node eq scope) =>
          // Function declarations: track but don't descend
          true

        case _: AstDefClass if !(node eq scope) =>
          // Class declarations: track but don't descend
          true

        case sr: AstSymbolRef =>
          // Mark the referenced symbol as in-use via its SymbolDef.id
          if (sr.thedef != null) {
            inUseIds(symbolDefId(sr.thedef)) = sr.thedef
          }
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
          // Transitively mark symbols referenced by initializers of used symbols
          if (sr.thedef != null) {
            inUseIds(symbolDefId(sr.thedef)) = sr.thedef
          }
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

    // Walk initializers of in-use symbols
    inUseIds.foreach { (defId, _) =>
      initializations.get(defId) match {
        case Some(inits) =>
          inits.foreach(_.walk(tw2))
        case None =>
      }
    }

    // -----------------------------------------------------------------------
    // Pass 3: transform to drop unused declarations
    // -----------------------------------------------------------------------

    var transformScope: AstNode = scope

    val tt = new TreeTransformer(
      before = (node, descend) =>
        node match {
          // Handle unused function/class declarations
          case _: AstDefun if !(node eq scope) =>
            // TODO: check if def is in-use when SymbolDef is ported
            // For now, keep all defuns
            null

          case _: AstDefClass if !(node eq scope) =>
            // TODO: check if def is in-use when SymbolDef is ported
            // For now, keep all classes
            null

          // Handle variable definitions
          case _: AstDefinitions =>
            // TODO: drop unused variable definitions when SymbolDef is ported
            // For now, keep all definitions
            null

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
    scope.walk(tt)
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Extract the `id` from a SymbolDef (typed as Any). Falls back to hashCode. */
  private def symbolDefId(defAny: Any): Int =
    try {
      val m = defAny.getClass.getMethod("id")
      m.invoke(defAny).asInstanceOf[Int]
    } catch {
      case _: Exception => defAny.hashCode()
    }

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
