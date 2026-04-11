/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Statement-level optimizations: tighten a block of statements.
 *
 * Applies iterative transformations to a sequence of statements:
 * - Remove spurious empty statements and flatten unnecessary blocks
 * - Eliminate dead code after return/throw/break/continue
 * - Merge `if (x) return a; return b;` into `return x ? a : b;`
 * - Combine consecutive simple statements into sequence expressions
 * - Join consecutive `var` declarations
 * - Collapse single-use variable assignments into their use sites
 * - Extract declarations from unreachable code (var hoisting)
 *
 * Ported from: terser lib/compress/tighten-body.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: tighten_body -> tightenBody, eliminate_dead_code -> eliminateDeadCode,
 *     eliminate_spurious_blocks -> eliminateSpuriousBlocks,
 *     handle_if_return -> handleIfReturn, sequencesize -> sequenceize,
 *     join_consecutive_vars -> joinConsecutiveVars,
 *     extract_from_unreachable_code -> extractFromUnreachableCode,
 *     collapse -> collapseVars
 *   Convention: Object with methods, mutable ArrayBuffer manipulation
 *   Idiom: boundary/break instead of return, var CHANGED for iteration
 *   Gap: 540 LOC vs upstream 1532 LOC (~35%). Scope-dependent optimizations
 *     stubbed at lines 96, 248, 257, 492. collapse-vars not implemented.
 *     See ISS-032, ISS-035. docs/architecture/terser-port.md.
 *   Audited: 2026-04-07 (major_issues)
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer

import ssg.js.ast.*
import ssg.js.compress.Common.*
import ssg.js.compress.Inference.aborts

/** Statement-level optimization engine.
  *
  * The main entry point is `tightenBody`, which applies multiple optimization passes over a mutable statement list until no more changes are made (or a maximum iteration count is reached).
  */
object TightenBody {

  /** Maximum number of tighten iterations to prevent infinite loops. */
  private val MaxIterations = 10

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Tighten a sequence of statements, applying all statement-level optimizations.
    *
    * Modifies `statements` in place. Runs multiple sub-passes in a loop until no changes are detected or `MaxIterations` is reached.
    *
    * @param statements
    *   mutable buffer of statements to optimize
    * @param compressor
    *   the compressor context
    */
  def tightenBody(statements: ArrayBuffer[AstNode], compressor: CompressorLike): Unit = {
    var changed    = false
    var iterations = MaxIterations

    def markChanged(): Unit = changed = true

    changed = true // enter loop at least once
    while (changed && iterations > 0) {
      changed = false

      eliminateSpuriousBlocks(statements, markChanged)

      if (compressor.optionBool("dead_code")) {
        eliminateDeadCode(statements, compressor, markChanged)
      }

      if (compressor.optionBool("if_return")) {
        handleIfReturn(statements, compressor, markChanged)
      }

      val hasSequences = compressor.option("sequences") match {
        case b: Boolean => b
        case n: Int     => n > 0
        case _ => false
      }
      if (hasSequences) {
        sequenceize(statements, compressor, markChanged)
      }

      if (compressor.optionBool("join_vars")) {
        joinConsecutiveVars(statements, markChanged)
      }

      // collapse_vars is the most complex sub-pass
      // TODO: implement collapse() when full scope analysis is available
      // if (compressor.optionBool("collapse_vars")) {
      //   collapseVars(statements, compressor, markChanged)
      // }

      iterations -= 1
    }
  }

  // -----------------------------------------------------------------------
  // Extract declarations from unreachable code
  // -----------------------------------------------------------------------

  /** Extract var/function declarations from unreachable code.
    *
    * Even though the code is dead, `var` declarations are hoisted and `function` declarations (in sloppy mode) are visible. We need to preserve these as declaration-only statements.
    *
    * @param compressor
    *   the compressor context
    * @param stat
    *   the unreachable statement to extract from
    * @param target
    *   buffer to append extracted declarations to
    */
  def extractFromUnreachableCode(
    compressor: CompressorLike,
    stat:       AstNode,
    target:     ArrayBuffer[AstNode]
  ): Unit =
    walk(
      stat,
      (node, _) =>
        node match {
          case varNode: AstVar =>
            val stripped = removeInitializers(varNode)
            if (stripped != null) target.addOne(stripped.nn)
            true // don't descend into children

          case defun: AstDefun if defun.eq(stat) || compressor.hasDirective("use strict") == null =>
            if (defun.eq(stat)) {
              target.addOne(defun)
            } else {
              // In sloppy mode, turn nested defun into var declaration
              val symVar = new AstSymbolVar
              symVar.start = defun.name.nn.start
              symVar.end = defun.name.nn.end
              symVar.name = defun.name.nn.asInstanceOf[AstSymbol].name
              symVar.thedef = defun.name.nn.asInstanceOf[AstSymbol].thedef

              val varDef = new AstVarDef
              varDef.start = defun.start
              varDef.end = defun.end
              varDef.name = symVar
              varDef.value = null

              val varStmt = new AstVar
              varStmt.start = defun.start
              varStmt.end = defun.end
              varStmt.definitions = ArrayBuffer(varDef)

              target.addOne(varStmt)
            }
            true

          case exp: AstExport =>
            target.addOne(exp)
            true

          case imp: AstImport =>
            target.addOne(imp)
            true

          case _: AstScope | _: AstClass =>
            true // don't descend into nested scopes

          case _ =>
            null // continue walking
        }
    )

  // -----------------------------------------------------------------------
  // Sub-pass: eliminate spurious blocks and empty statements
  // -----------------------------------------------------------------------

  /** Remove empty statements, flatten unnecessary block statements, and deduplicate directives.
    */
  private def eliminateSpuriousBlocks(
    statements:  ArrayBuffer[AstNode],
    markChanged: () => Unit
  ): Unit = {
    val seenDirs = ArrayBuffer.empty[String]
    var i        = 0
    while (i < statements.size) {
      val stat = statements(i)
      stat match {
        case block: AstBlockStatement if block.body.forall(canBeEvictedFromBlock) =>
          markChanged()
          eliminateSpuriousBlocks(block.body, markChanged)
          statements.remove(i)
          var j = 0
          while (j < block.body.size) {
            statements.insert(i + j, block.body(j))
            j += 1
          }
          i += block.body.size

        case _: AstEmptyStatement =>
          markChanged()
          statements.remove(i)

        case dir: AstDirective =>
          if (seenDirs.indexOf(dir.value) < 0) {
            i += 1
            seenDirs.addOne(dir.value)
          } else {
            markChanged()
            statements.remove(i)
          }

        case _ =>
          i += 1
      }
    }
  }

  // -----------------------------------------------------------------------
  // Sub-pass: eliminate dead code after abrupt completion
  // -----------------------------------------------------------------------

  /** Remove statements that appear after return/throw/break/continue.
    *
    * Preserves var declarations and function declarations from the dead code since they are hoisted.
    */
  private def eliminateDeadCode(
    statements:  ArrayBuffer[AstNode],
    compressor:  CompressorLike,
    markChanged: () => Unit
  ): Unit = {
    var n = 0
    var hasQuit: ArrayBuffer[AstNode] | Null = null
    var i   = 0
    val len = statements.size

    while (i < len) {
      val stat = statements(i)

      stat match {
        case lc: AstLoopControl =>
          // Remove break/continue that targets the current scope
          // (they are no-ops)
          val isNoop = lc match {
            case _: AstBreak | _: AstContinue =>
              // TODO: check loopcontrol_target when available
              false
            case _ => false
          }
          if (!isNoop) {
            statements(n) = stat
            n += 1
          } else {
            // Remove label reference if dropping the statement
            val lblRef: AstNode | Null = lc match {
              case brk: AstBreak    => brk.label
              case cont: AstContinue => cont.label
              case _                 => null
            }
            if (lblRef != null) {
              lblRef.nn match {
                case lr: AstLabelRef if lr.thedef != null =>
                  lr.thedef.asInstanceOf[AstLabel].references =
                    lr.thedef.asInstanceOf[AstLabel].references.filterNot(r => (r.asInstanceOf[AnyRef] eq lc.asInstanceOf[AnyRef]))
                case _ =>
              }
            }
          }
        case _ =>
          statements(n) = stat
          n += 1
      }

      if (aborts(stat) != null) {
        hasQuit = ArrayBuffer.empty[AstNode]
        var j = i + 1
        while (j < len) {
          hasQuit.nn.addOne(statements(j))
          j += 1
        }
        i = len // break
      } else {
        i += 1
      }
    }

    if (n != len) {
      markChanged()
    }
    while (statements.size > n) statements.remove(statements.size - 1)

    if (hasQuit != null) {
      hasQuit.nn.foreach { stat =>
        extractFromUnreachableCode(compressor, stat, statements)
      }
    }
  }

  // -----------------------------------------------------------------------
  // Sub-pass: if/return optimization
  // -----------------------------------------------------------------------

  /** Optimize `if/return` patterns in function bodies.
    *
    * Key transformations:
    *   - `if (x) return; return;` -> `x; return;`
    *   - `if (x) return a; return b;` -> `return x ? a : b;`
    *   - Remove trailing `return;` in lambdas (void return)
    *   - `return void expr;` -> `expr;` at end of lambda
    */
  private def handleIfReturn(
    statements:  ArrayBuffer[AstNode],
    compressor:  CompressorLike,
    markChanged: () => Unit
  ): Unit = {
    // Check if we're in a lambda body
    val self     = compressor.parent() // self() in Terser
    val inLambda = self != null && self.nn.isInstanceOf[AstLambda]

    // Limit iteration depth to prevent stack overflow
    val iterStart = Math.min(statements.size, 500)
    var i         = iterStart - 1

    while (i >= 0) {
      val stat = statements(i)

      // Remove trailing void return in lambda
      if (inLambda && i == statements.size - 1) {
        stat match {
          case ret: AstReturn if ret.value == null =>
            markChanged()
            statements.remove(i)
            i -= 1
          // continue with loop (skip the rest of this iteration)
          case ret: AstReturn
              if ret.value != null
                && ret.value.nn.isInstanceOf[AstUnaryPrefix]
                && ret.value.nn.asInstanceOf[AstUnaryPrefix].operator == "void" =>
            markChanged()
            val simple = new AstSimpleStatement
            simple.start = stat.start
            simple.end = stat.end
            simple.body = ret.value.nn.asInstanceOf[AstUnaryPrefix].expression.nn
            statements(i) = simple
          case _ =>
        }
      }

      // if (x) return a; return b; -> return x ? a : b;
      stat match {
        case ifStat: AstIf
            if ifStat.body.isInstanceOf[AstReturn]
              && ifStat.alternative == null
              && i + 1 < statements.size =>
          val ifReturn = ifStat.body.asInstanceOf[AstReturn]
          statements(i + 1) match {
            case nextReturn: AstReturn if ifReturn.value != null && nextReturn.value != null =>
              // if (x) return a; return b; -> return x ? a : b;
              markChanged()
              val cond = new AstConditional
              cond.start = ifStat.start
              cond.end = nextReturn.end
              cond.condition = ifStat.condition.nn
              cond.consequent = ifReturn.value.nn
              cond.alternative = nextReturn.value.nn

              val ret = new AstReturn
              ret.start = ifStat.start
              ret.end = nextReturn.end
              ret.value = cond

              statements(i) = ret
              statements.remove(i + 1)
            case _ =>
          }
        case _ =>
      }

      i -= 1
    }
  }

  // -----------------------------------------------------------------------
  // Sub-pass: sequenceize
  // -----------------------------------------------------------------------

  /** Merge consecutive simple statements into sequence expressions.
    *
    * `a(); b(); c();` -> `a(), b(), c();`
    */
  private def sequenceize(
    statements:  ArrayBuffer[AstNode],
    compressor:  CompressorLike,
    markChanged: () => Unit
  ): Unit = {
    if (statements.size < 2) {
      return // @nowarn — nothing to merge
    }

    val seqLimit = compressor.option("sequences") match {
      case n: Int if n == 1 => 800
      case n: Int           => n
      case true => 200
      case _    => 200
    }

    val seq = ArrayBuffer.empty[AstNode]
    var n   = 0

    def pushSeq(): Unit =
      if (seq.nonEmpty) {
        val body   = makeSequence(seq(0), ArrayBuffer.from(seq))
        val simple = new AstSimpleStatement
        simple.start = body.start
        simple.end = body.end
        simple.body = body
        statements(n) = simple
        n += 1
        seq.clear()
      }

    var i   = 0
    val len = statements.size
    while (i < len) {
      val stat = statements(i)
      stat match {
        case ss: AstSimpleStatement =>
          if (seq.size >= seqLimit) {
            pushSeq()
          }
          if (ss.body != null) {
            mergeSequence(seq, ss.body.nn)
          }

        case d: AstDefinitions if declarationsOnly(d) =>
          statements(n) = stat
          n += 1

        case _: AstDefun =>
          statements(n) = stat
          n += 1

        case _ =>
          pushSeq()
          statements(n) = stat
          n += 1
      }
      i += 1
    }
    pushSeq()
    if (n != len) {
      markChanged()
    }
    while (statements.size > n) statements.remove(statements.size - 1)
  }

  // -----------------------------------------------------------------------
  // Sub-pass: join consecutive var declarations
  // -----------------------------------------------------------------------

  /** Join consecutive `var` declarations into one.
    *
    * `var a = 1; var b = 2;` -> `var a = 1, b = 2;`
    */
  private def joinConsecutiveVars(
    statements:  ArrayBuffer[AstNode],
    markChanged: () => Unit
  ): Unit = {
    var i = 0
    while (i < statements.size - 1)
      (statements(i), statements(i + 1)) match {
        case (prev: AstVar, next: AstVar) =>
          markChanged()
          prev.definitions.addAll(next.definitions)
          statements.remove(i + 1)
        // don't increment i — check if next statement is also var
        case _ =>
          i += 1
      }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Remove initializer values from a var statement, keeping only declarations. */
  private def removeInitializers(varStatement: AstVar): AstNode | Null = {
    val decls = ArrayBuffer.empty[AstNode]
    varStatement.definitions.foreach { defn =>
      defn match {
        case vd: AstVarDef =>
          vd.name match {
            case _: AstSymbolDeclaration =>
              val stripped = new AstVarDef
              stripped.start = vd.start
              stripped.end = vd.end
              stripped.name = vd.name
              stripped.value = null
              decls.addOne(stripped)
            case _ =>
            // Destructuring — expand to individual name declarations
            // TODO: implement declarations_as_names when destructuring is ported
          }
        case _ =>
      }
    }
    if (decls.nonEmpty) {
      val result = new AstVar
      result.start = varStatement.start
      result.end = varStatement.end
      result.definitions = decls
      result
    } else {
      null
    }
  }

  /** Check if all definitions in a var statement have no initializers. */
  private def declarationsOnly(node: AstDefinitions): Boolean =
    node.definitions.forall {
      case vd: AstVarDef => vd.value == null
      case _ => true
    }

  /** Walk an AST node, calling `visitor` for each child. Returns true if walk_abort was signaled.
    */
  private def walk(
    node:    AstNode,
    visitor: (AstNode, ArrayBuffer[AstNode]) => Any
  ): Boolean = {
    val parents = ArrayBuffer.empty[AstNode]
    var aborted = false
    val tw      = new TreeWalker((n, _) =>
      if (aborted) {
        true // skip
      } else {
        visitor(n, parents) match {
          case WalkAbort =>
            aborted = true
            true
          case true => true // skip children
          case _    => null // continue
        }
      }
    )
    node.walk(tw)
    aborted
  }
}
