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
 *   Audited: 2026-04-11 (pass)
 */
package ssg
package js
package compress

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.ast.AstEquivalent
import ssg.js.compress.Common.*
import ssg.js.compress.CompressorFlags.{ WRITE_ONLY, clearFlag }
import ssg.js.compress.Inference.{ aborts, hasSideEffects, isLhs, isModified, lazyOp, mayThrow, unarySideEffects, isRefImmutable }
import ssg.js.compress.NativeObjects.purePropAccessGlobals
import ssg.js.scope.{ ScopeAnalysis, SymbolDef }

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
    // Build a walker to get findScope
    val tw = buildParentWalker(compressor)
    val nearestScope = tw.findScope()
    val defunScope   = if (nearestScope != null) nearestScope.nn.getDefunScope else null

    // Find whether we're in a loop or try block
    val (inLoop, inTry) = findLoopScopeTry(compressor)

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

      val seqLimit = compressor.option("sequences") match {
        case false    => 0
        case true     => 200
        case n: Int if n == 1 => 800
        case n: Int   => n
        case _        => 0
      }
      if (seqLimit > 0) {
        sequenceize(statements, compressor, seqLimit, markChanged)
        sequenceize2(statements, compressor, markChanged)
      }

      if (compressor.optionBool("join_vars")) {
        joinConsecutiveVars(statements, compressor, nearestScope, markChanged)
      }

      if (compressor.optionBool("collapse_vars") && nearestScope != null && defunScope != null) {
        collapseVars(statements, compressor, nearestScope.nn, defunScope.nn, inLoop, inTry, markChanged)
      }

      iterations -= 1
    }
  }

  /** Build a TreeWalker with the parent stack from the compressor. */
  private def buildParentWalker(compressor: CompressorLike): TreeWalker = {
    val tw = new TreeWalker()
    var level = 0
    var p     = compressor.parent(level)
    val parentStack = ArrayBuffer.empty[AstNode]
    while (p != null) {
      parentStack.insert(0, p.nn)
      level += 1
      p = compressor.parent(level)
    }
    parentStack.foreach(tw.push)
    tw
  }

  /** Find whether we're currently in a loop or try block by walking up the parent chain. */
  private def findLoopScopeTry(compressor: CompressorLike): (Boolean, Boolean) = {
    var inLoop = false
    var inTry  = false
    var level  = 0
    var node   = compressor.parent(level)
    while (node != null) {
      node.nn match {
        case _: AstIterationStatement => inLoop = true
        case _: AstScope              => return (inLoop, inTry) // @nowarn
        case _: AstTryBlock           => inTry = true
        case _ =>
      }
      level += 1
      node = compressor.parent(level)
    }
    (inLoop, inTry)
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

  /** Get the body of a loop (unwrap BlockStatement if present). */
  private def loopBody(x: AstNode | Null): AstNode | Null =
    if (x == null) null
    else x.nn match {
      case it: AstIterationStatement =>
        it.body match {
          case bs: AstBlockStatement => bs
          case _ => it
        }
      case _ => x
    }

  /** Remove statements that appear after return/throw/break/continue.
    *
    * Preserves var declarations and function declarations from the dead code since they are hoisted.
    */
  private def eliminateDeadCode(
    statements:  ArrayBuffer[AstNode],
    compressor:  CompressorLike,
    markChanged: () => Unit
  ): Unit = {
    // Build a walker to get loopcontrol_target
    val tw = buildParentWalker(compressor)
    val self = compressor.parent() // self() in Terser — immediate parent

    var n = 0
    var hasQuit: ArrayBuffer[AstNode] | Null = null
    var i   = 0
    val len = statements.size

    while (i < len) {
      val stat = statements(i)

      stat match {
        case lc: AstLoopControl =>
          val lct = tw.loopcontrolTarget(lc)
          val dropIt = lc match {
            case _: AstBreak =>
              // Drop break if it doesn't target an iteration statement AND targets self
              lct != null && !lct.nn.isInstanceOf[AstIterationStatement] &&
                (loopBody(lct) != null && self != null && (loopBody(lct).nn eq self.nn))
            case _: AstContinue =>
              lct != null && self != null && (loopBody(lct) != null) && (loopBody(lct).nn eq self.nn)
            case _ => false
          }

          if (dropIt) {
            // Remove label reference if dropping the statement
            if (lc.label != null) {
              lc.label.nn match {
                case lr: AstLabelRef if lr.thedef != null =>
                  val lbl = lr.thedef.asInstanceOf[AstLabel]
                  lbl.references = lbl.references.filterNot(r => r.asInstanceOf[AnyRef] eq lc.asInstanceOf[AnyRef])
                case _ =>
              }
            }
          } else {
            statements(n) = stat
            n += 1
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

  /** Check if all definitions in a var statement have no initializers. */
  private def declarationsOnly(node: AstDefinitions): Boolean =
    node.definitions.forall {
      case vd: AstVarDef => vd.value == null
      case _ => true
    }

  /** Check if there are multiple if-return statements in the body. */
  private def hasMultipleIfReturns(statements: ArrayBuffer[AstNode]): Boolean = {
    var n = 0
    var i = statements.size
    while ({ i -= 1; i >= 0 }) {
      val stat = statements(i)
      if (stat.isInstanceOf[AstIf] && stat.asInstanceOf[AstIf].body.isInstanceOf[AstReturn]) {
        n += 1
        if (n > 1) return true // @nowarn
      }
    }
    false
  }

  /** Check if return value is void (null or void expression). */
  private def isReturnVoid(value: AstNode | Null): Boolean =
    value == null || (value.nn.isInstanceOf[AstUnaryPrefix] &&
      value.nn.asInstanceOf[AstUnaryPrefix].operator == "void")

  /** Find next statement index, skipping var declarations with no initializers. */
  private def nextIndex(statements: ArrayBuffer[AstNode], i: Int): Int = {
    var j = i + 1
    while (j < statements.size) {
      val stat = statements(j)
      stat match {
        case v: AstVar if declarationsOnly(v) => j += 1
        case _ => return j // @nowarn
      }
    }
    j
  }

  /** Find previous statement index, skipping var declarations with no initializers. */
  private def prevIndex(statements: ArrayBuffer[AstNode], i: Int): Int = {
    var j = i - 1
    while (j >= 0) {
      val stat = statements(j)
      stat match {
        case v: AstVar if declarationsOnly(v) => j -= 1
        case _ => return j // @nowarn
      }
    }
    j
  }

  /** Simple negation: create `!expr`. */
  private def negate(node: AstNode): AstNode = {
    val neg = new AstUnaryPrefix
    neg.start = node.start
    neg.end = node.end
    neg.operator = "!"
    neg.expression = node
    neg
  }

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
    // Build walker for loopcontrol_target
    val tw = buildParentWalker(compressor)

    // Check if we're in a lambda body
    val self               = compressor.parent() // self() in Terser
    val inLambda           = self != null && self.nn.isInstanceOf[AstLambda]
    val multipleIfReturns  = hasMultipleIfReturns(statements)

    // Helper: check if an abrupt can be merged (returns are void, continue/break targets self)
    def canMergeFlow(ab: AstNode | Null): Boolean = {
      if (ab == null) return false // @nowarn
      // Check for let/const between here and end
      var found = false
      var j     = 0
      while (j < statements.size && !found) {
        if (statements(j) eq ab.nn) found = true
        j += 1
      }
      if (!found) return false // @nowarn

      while (j < statements.size) {
        val stat = statements(j)
        stat match {
          case d: AstDefinitionsLike if !d.isInstanceOf[AstVar] => return false // @nowarn
          case _ =>
        }
        j += 1
      }
      ab.nn match {
        case ret: AstReturn if inLambda && isReturnVoid(ret.value) => true
        case cont: AstContinue =>
          val lct = tw.loopcontrolTarget(cont)
          lct != null && self != null && (loopBody(lct) != null) && (self.nn eq loopBody(lct).nn)
        case brk: AstBreak =>
          val lct = tw.loopcontrolTarget(brk)
          lct != null && lct.nn.isInstanceOf[AstBlockStatement] && self != null && (self.nn eq lct.nn)
        case _ => false
      }
    }

    // Helper: extract function definitions to move out of the if body
    def extractDefuns(i: Int): ArrayBuffer[AstNode] = {
      val tail   = statements.slice(i + 1, statements.size)
      while (statements.size > i + 1) statements.remove(statements.size - 1)
      val result = ArrayBuffer.empty[AstNode]
      tail.foreach { stat =>
        stat match {
          case _: AstDefun =>
            statements.addOne(stat)
          case _ =>
            result.addOne(stat)
        }
      }
      result
    }

    // Helper: convert block body to statement array, handling abrupt ending
    def asStatementArrayWithReturn(node: AstNode | Null, ab: AstNode): ArrayBuffer[AstNode] | Null = {
      val body = asStatementArray(node)
      if (body.isEmpty || !(body.last eq ab)) return null // @nowarn
      val result = body.slice(0, body.size - 1)
      if (!result.forall(canBeEvictedFromBlock)) return null // @nowarn
      ab match {
        case ret: AstReturn if ret.value != null =>
          ret.value.nn match {
            case prefix: AstUnaryPrefix if prefix.operator == "void" && prefix.expression != null =>
              val ss = new AstSimpleStatement
              ss.start = ret.value.nn.start
              ss.end = ret.value.nn.end
              ss.body = prefix.expression.nn
              result.addOne(ss)
            case _ =>
          }
        case _ =>
      }
      result
    }

    // Limit iteration depth to prevent stack overflow
    val iterStart = Math.min(statements.size, 500)
    var i         = iterStart - 1

    while (i >= 0) {
      val stat = statements(i)
      val j    = nextIndex(statements, i)
      val next = if (j < statements.size) statements(j) else null

      // Pattern 1: Remove trailing void return in lambda
      if (inLambda && next == null) {
        stat match {
          case ret: AstReturn if ret.value == null =>
            markChanged()
            statements.remove(i)
            i -= 1
            // continue loop (skip rest of this iteration)
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

      // Pattern 2 & 3: if (cond) body; [more statements] where body aborts
      stat match {
        case ifStat: AstIf =>
          boundary {
            // Try to merge flow from if body
            val ab1 = aborts(ifStat.body)
            if (canMergeFlow(ab1)) {
              val newElse = asStatementArrayWithReturn(ifStat.body, ab1.nn)
              if (newElse != null) {
                ab1.nn match {
                  case lc: AstLoopControl if lc.label != null =>
                    val lbl = lc.label.nn.asInstanceOf[AstLabelRef]
                    if (lbl.thedef != null) {
                      val label = lbl.thedef.asInstanceOf[AstLabel]
                      label.references = label.references.filterNot(r => r.asInstanceOf[AnyRef] eq ab1.nn.asInstanceOf[AnyRef])
                    }
                  case _ =>
                }
                markChanged()
                val negated = negate(ifStat.condition.nn)
                val bodyBlock = new AstBlockStatement
                bodyBlock.start = ifStat.start
                bodyBlock.end = ifStat.end
                bodyBlock.body = asStatementArray(ifStat.alternative) ++ extractDefuns(i)

                val altBlock = new AstBlockStatement
                altBlock.start = ifStat.start
                altBlock.end = ifStat.end
                altBlock.body = newElse.nn

                val newIf = new AstIf
                newIf.start = ifStat.start
                newIf.end = ifStat.end
                newIf.condition = negated
                newIf.body = bodyBlock
                newIf.alternative = altBlock
                statements(i) = newIf
                break(())
              }
            }

            // Try to merge flow from alternative
            val ab2 = aborts(ifStat.alternative)
            if (canMergeFlow(ab2)) {
              val newElse = asStatementArrayWithReturn(ifStat.alternative, ab2.nn)
              if (newElse != null) {
                ab2.nn match {
                  case lc: AstLoopControl if lc.label != null =>
                    val lbl = lc.label.nn.asInstanceOf[AstLabelRef]
                    if (lbl.thedef != null) {
                      val label = lbl.thedef.asInstanceOf[AstLabel]
                      label.references = label.references.filterNot(r => r.asInstanceOf[AnyRef] eq ab2.nn.asInstanceOf[AnyRef])
                    }
                  case _ =>
                }
                markChanged()
                val bodyBlock = new AstBlockStatement
                bodyBlock.start = ifStat.body.start
                bodyBlock.end = ifStat.body.end
                bodyBlock.body = asStatementArray(ifStat.body) ++ extractDefuns(i)

                val altBlock = new AstBlockStatement
                altBlock.start = ifStat.start
                altBlock.end = ifStat.end
                altBlock.body = newElse.nn

                val newIf = new AstIf
                newIf.start = ifStat.start
                newIf.end = ifStat.end
                newIf.condition = ifStat.condition
                newIf.body = bodyBlock
                newIf.alternative = altBlock
                statements(i) = newIf
                break(())
              }
            }
          }

          // Pattern 4-7: if (cond) return value patterns
          if (ifStat.body.isInstanceOf[AstReturn]) {
            val value = ifStat.body.asInstanceOf[AstReturn].value

            // Pattern 4: if (foo()) return; return; -> foo(); return;
            if (value == null && ifStat.alternative == null &&
                (inLambda && next == null || (next != null && next.nn.isInstanceOf[AstReturn] &&
                  next.nn.asInstanceOf[AstReturn].value == null))) {
              markChanged()
              val ss = new AstSimpleStatement
              ss.start = ifStat.condition.nn.start
              ss.end = ifStat.condition.nn.end
              ss.body = ifStat.condition.nn
              statements(i) = ss
              i -= 1
            }
            // Pattern 5: if (foo()) return x; return y; -> return foo() ? x : y;
            else if (value != null && ifStat.alternative == null &&
                     next != null && next.nn.isInstanceOf[AstReturn] &&
                     next.nn.asInstanceOf[AstReturn].value != null) {
              markChanged()
              val nextRet = next.nn.asInstanceOf[AstReturn]
              val cond = new AstConditional
              cond.start = ifStat.start
              cond.end = nextRet.end
              cond.condition = ifStat.condition.nn
              cond.consequent = value.nn
              cond.alternative = nextRet.value.nn

              val ret = new AstReturn
              ret.start = ifStat.start
              ret.end = nextRet.end
              ret.value = cond

              statements(i) = ret
              statements.remove(j)
              i -= 1
            }
            // Pattern 6: if (foo()) return x; [return;] -> return foo() ? x : undefined;
            else if (value != null && ifStat.alternative == null &&
                     ((next == null && inLambda && multipleIfReturns) ||
                       (next != null && next.nn.isInstanceOf[AstReturn]))) {
              markChanged()
              val alt: AstNode =
                if (next != null && next.nn.asInstanceOf[AstReturn].value != null) {
                  next.nn.asInstanceOf[AstReturn].value.nn
                } else {
                  val voidExpr = new AstUndefined
                  voidExpr.start = ifStat.start
                  voidExpr.end = ifStat.end
                  voidExpr
                }

              val cond = new AstConditional
              cond.start = ifStat.start
              cond.end = ifStat.end
              cond.condition = ifStat.condition.nn
              cond.consequent = value.nn
              cond.alternative = alt

              val ret = new AstReturn
              ret.start = ifStat.start
              ret.end = ifStat.end
              ret.value = cond

              statements(i) = ret
              if (next != null) statements.remove(j)
              i -= 1
            }
            // Pattern 7: if (a) return b; if (c) return d; e; -> return a ? b : c ? d : void e;
            else if (compressor.optionBool("sequences") && inLambda && ifStat.alternative == null && value != null) {
              val prevIdx = prevIndex(statements, i)
              val prev    = if (prevIdx >= 0) statements(prevIdx) else null
              if (prev != null && prev.nn.isInstanceOf[AstIf] &&
                  prev.nn.asInstanceOf[AstIf].body.isInstanceOf[AstReturn] &&
                  nextIndex(statements, j) == statements.size &&
                  next != null && next.nn.isInstanceOf[AstSimpleStatement]) {
                markChanged()
                val nextSS = next.nn.asInstanceOf[AstSimpleStatement]
                val voidRet = new AstReturn
                voidRet.start = nextSS.start
                voidRet.end = nextSS.end
                voidRet.value = null

                val altBlock = new AstBlockStatement
                altBlock.start = nextSS.start
                altBlock.end = nextSS.end
                altBlock.body = ArrayBuffer(nextSS, voidRet)

                val newIf = new AstIf
                newIf.start = ifStat.start
                newIf.end = ifStat.end
                newIf.condition = ifStat.condition
                newIf.body = ifStat.body
                newIf.alternative = altBlock
                statements(i) = newIf
                statements.remove(j)
                i -= 1
              } else {
                i -= 1
              }
            } else {
              i -= 1
            }
          } else {
            i -= 1
          }

        case _ =>
          i -= 1
      }
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
    seqLimit:    Int,
    markChanged: () => Unit
  ): Unit = {
    if (statements.size < 2) {
      return // @nowarn — nothing to merge
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
  // Sub-pass: sequenceize_2
  // -----------------------------------------------------------------------

  /** Convert statement into simple statement if possible. */
  private def toSimpleStatement(block: AstNode | Null, decls: ArrayBuffer[AstNode]): AstNode | Null | Boolean = {
    block match {
      case null              => null
      case bs: AstBlockStatement =>
        var stat: AstNode | Null = null
        var i = 0
        while (i < bs.body.size) {
          val line = bs.body(i)
          line match {
            case v: AstVar if declarationsOnly(v) =>
              decls.addOne(v)
            case d: AstDefinitionsLike if !d.isInstanceOf[AstVar] =>
              return false // @nowarn -- let/const, cannot merge
            case _ =>
              if (stat != null) return false // @nowarn -- more than one statement
              stat = line
          }
          i += 1
        }
        stat
      case other => other
    }
  }

  /** Sequenceize pass 2: merge preceding simple statement into control structures.
    *
    * `a; return b;` -> `return (a, b);`
    * `a; for (init; ...) {}` -> `for (a, init; ...) {}`
    */
  private def sequenceize2(
    statements:  ArrayBuffer[AstNode],
    compressor:  CompressorLike,
    markChanged: () => Unit
  ): Unit = {
    var n    = 0
    var prev: AstSimpleStatement | Null = null

    def consSeq(right: AstNode): AstNode = {
      n -= 1
      markChanged()
      val left = prev.nn.body.nn
      makeSequence(left, ArrayBuffer(left, right))
    }

    var i = 0
    while (i < statements.size) {
      val stat = statements(i)
      if (prev != null) {
        stat match {
          case exit: AstExit =>
            val v = if (exit.value != null) exit.value.nn else {
              val undef = new AstUndefined
              undef.start = exit.start
              undef.end = exit.end
              undef
            }
            exit.value = consSeq(v)

          case forStat: AstFor if !forStat.init.isInstanceOf[AstDefinitionsLike] =>
            // Check that prev.body doesn't contain `x in y` binary
            val hasInBinary = walk(prev.nn.body.nn, (node, _) => node match {
              case _: AstScope => true // skip scopes
              case bin: AstBinary if bin.operator == "in" => WalkAbort
              case _ => null
            })
            if (!hasInBinary) {
              if (forStat.init != null)
                forStat.init = consSeq(forStat.init.nn)
              else {
                forStat.init = prev.nn.body
                n -= 1
                markChanged()
              }
            }

          case forIn: AstForIn if !forIn.init.isInstanceOf[AstDefinitionsLike] || forIn.init.isInstanceOf[AstVar] =>
            forIn.obj = consSeq(forIn.obj.nn)

          case ifStat: AstIf =>
            ifStat.condition = consSeq(ifStat.condition.nn)

          case sw: AstSwitch =>
            sw.expression = consSeq(sw.expression.nn)

          case withStat: AstWith =>
            withStat.expression = consSeq(withStat.expression.nn)

          case _ =>
        }
      }

      // Handle conditionals with var declarations
      if (compressor.optionBool("conditionals")) {
        stat match {
          case ifStat: AstIf =>
            val decls = ArrayBuffer.empty[AstNode]
            val body  = toSimpleStatement(ifStat.body, decls)
            val alt   = toSimpleStatement(ifStat.alternative, decls)
            if (body != false && alt != false && decls.nonEmpty) {
              val bodyStmt = body match {
                case null =>
                  val empty = new AstEmptyStatement
                  empty.start = ifStat.body.start
                  empty.end = ifStat.body.end
                  empty
                case n: AstNode => n
                case _ => ifStat.body
              }
              val altStmt = alt match {
                case null     => null
                case n: AstNode => n
                case _ => ifStat.alternative
              }
              val newIf = new AstIf
              newIf.start = ifStat.start
              newIf.end = ifStat.end
              newIf.condition = ifStat.condition
              newIf.body = bodyStmt
              newIf.alternative = altStmt
              decls.addOne(newIf)
              // Splice decls in place of this statement
              val len = decls.size
              statements.remove(n)
              var di = 0
              while (di < len) {
                statements.insert(n + di, decls(di))
                di += 1
              }
              i += len - 1
              n += len
              prev = null
              markChanged()
              i += 1
              // skip to next iteration
            } else {
              statements(n) = stat
              n += 1
              prev = if (stat.isInstanceOf[AstSimpleStatement]) stat.asInstanceOf[AstSimpleStatement] else null
              i += 1
            }
          case _ =>
            statements(n) = stat
            n += 1
            prev = if (stat.isInstanceOf[AstSimpleStatement]) stat.asInstanceOf[AstSimpleStatement] else null
            i += 1
        }
      } else {
        statements(n) = stat
        n += 1
        prev = if (stat.isInstanceOf[AstSimpleStatement]) stat.asInstanceOf[AstSimpleStatement] else null
        i += 1
      }
    }
    while (statements.size > n) statements.remove(statements.size - 1)
  }

  // -----------------------------------------------------------------------
  // Sub-pass: join consecutive var declarations
  // -----------------------------------------------------------------------

  /** Try to merge `obj.a = 1; obj.b = 2;` into the object literal.
    *
    * @param defn
    *   The previous definition statement (must have an object literal as value)
    * @param body
    *   The expression to try to merge (assignment or sequence of assignments)
    * @param nearestScope
    *   The nearest enclosing scope
    * @param compressor
    *   The compressor context
    * @return
    *   Remaining expressions after trimming, or null if no merge happened
    */
  private def joinObjectAssignments(
    defn:         AstNode | Null,
    body:         AstNode | Null,
    nearestScope: AstScope | Null,
    compressor:   CompressorLike
  ): ArrayBuffer[AstNode] | Null = {
    if (defn == null || !defn.nn.isInstanceOf[AstDefinitions]) return null // @nowarn
    val defs = defn.nn.asInstanceOf[AstDefinitions]
    if (defs.definitions.isEmpty) return null // @nowarn
    val d = defs.definitions.last
    if (!d.isInstanceOf[AstVarDef]) return null // @nowarn
    val varDef = d.asInstanceOf[AstVarDef]
    if (varDef.value == null || !varDef.value.nn.isInstanceOf[AstObject]) return null // @nowarn
    val obj = varDef.value.nn.asInstanceOf[AstObject]

    val exprs: ArrayBuffer[AstNode] = body match {
      case a: AstAssign if !a.logical => ArrayBuffer(a)
      case s: AstSequence             => ArrayBuffer.from(s.expressions)
      case _                          => return null // @nowarn
    }

    var trimmed = false
    while (exprs.nonEmpty) {
      val node = exprs(0)
      node match {
        case assign: AstAssign if assign.operator == "=" && !assign.logical =>
          assign.left match {
            case pa: AstPropAccess =>
              pa.expression match {
                case ref: AstSymbolRef if varDef.name.isInstanceOf[AstSymbol] &&
                                          ref.name == varDef.name.asInstanceOf[AstSymbol].name =>
                  // Check if right side is constant expression
                  if (Inference.isConstantExpression(assign.right.nn, nearestScope) == false) return if (trimmed) exprs else null // @nowarn

                  // Evaluate the property key
                  val propKey: Any = pa match {
                    case dot: AstDot => dot.property
                    case sub: AstSub =>
                      sub.property match {
                        case str: AstString => str.value
                        case num: AstNumber => num.value.toString
                        case _              => return if (trimmed) exprs else null // @nowarn
                      }
                    case _ => return if (trimmed) exprs else null // @nowarn
                  }
                  val propStr = propKey.toString

                  // Check if property already exists with different key
                  val ecmaVersion = compressor.option("ecma") match {
                    case n: Int => n
                    case _      => 2020
                  }
                  val hasStrict = compressor.hasDirective("use strict") != null

                  val diff: AstNode => Boolean =
                    if (ecmaVersion < 2015 && hasStrict)
                      (node: AstNode) => node match {
                        case p: AstObjectProperty =>
                          val pKey: String | Null = p.key match {
                            case s: String  => s
                            case _: AstNode => null
                          }
                          pKey != propStr && (p.key match {
                            case sym: AstSymbol => sym.name != propStr
                            case _              => true
                          })
                        case _ => true
                      }
                    else
                      (node: AstNode) => node match {
                        case p: AstObjectProperty =>
                          p.key match {
                            case sym: AstSymbol => sym.name != propStr
                            case _              => true
                          }
                        case _ => true
                      }

                  if (!obj.properties.forall(diff)) return if (trimmed) exprs else null // @nowarn

                  // Find existing property with same key
                  val existing = obj.properties.collectFirst {
                    case p: AstObjectProperty if (p.key match {
                      case s: String      => s == propStr
                      case sym: AstSymbol => sym.name == propStr
                      case _              => false
                    }) => p
                  }

                  existing match {
                    case Some(p: AstObjectKeyVal) =>
                      // Merge into existing property with sequence
                      val seq = new AstSequence
                      seq.start = p.start
                      seq.end = p.end
                      val clonedPrev = p.value.nn // Don't need to clone for now
                      val clonedNew  = assign.right.nn
                      seq.expressions = ArrayBuffer(clonedPrev, clonedNew)
                      p.value = seq

                    case _ =>
                      // Add new property
                      val kv = new AstObjectKeyVal
                      kv.start = node.start
                      kv.end = node.end
                      kv.key = propStr
                      kv.value = assign.right.nn
                      obj.properties.addOne(kv)
                  }

                  exprs.remove(0)
                  trimmed = true

                case _ => return if (trimmed) exprs else null // @nowarn
              }
            case _ => return if (trimmed) exprs else null // @nowarn
          }
        case _ => return if (trimmed) exprs else null // @nowarn
      }
    }
    if (trimmed) exprs else null
  }

  /** Join consecutive `var` declarations into one, and merge object assignments.
    *
    * `var a = 1; var b = 2;` -> `var a = 1, b = 2;`
    */
  private def joinConsecutiveVars(
    statements:   ArrayBuffer[AstNode],
    compressor:   CompressorLike,
    nearestScope: AstScope | Null,
    markChanged:  () => Unit
  ): Unit = {
    var defs: AstDefinitions | Null = null
    var j    = -1
    var i    = 0

    /** Extract object assignments from a value, updating j and statements. */
    def extractObjectAssignments(value: AstNode | Null, stat: AstNode, prev: AstNode | Null): AstNode | Null = {
      j += 1
      statements(j) = stat
      val exprs = joinObjectAssignments(prev, value, nearestScope, compressor)
      if (exprs != null) {
        markChanged()
        if (exprs.nn.nonEmpty) {
          makeSequence(value.nn, exprs.nn)
        } else {
          value match {
            case seq: AstSequence => seq.expressions.last.asInstanceOf[AstAssign].left
            case a: AstAssign     => a.left
            case _ => value
          }
        }
      } else {
        value
      }
    }

    while (i < statements.size) {
      val stat = statements(i)
      val prev = if (j >= 0) statements(j) else null

      stat match {
        case d: AstDefinitions =>
          if (prev != null && prev.nn.getClass == stat.getClass) {
            prev.nn.asInstanceOf[AstDefinitions].definitions.addAll(d.definitions)
            markChanged()
          } else if (defs != null && defs.nn.getClass == stat.getClass && declarationsOnly(d)) {
            defs.nn.definitions.addAll(d.definitions)
            markChanged()
          } else {
            j += 1
            statements(j) = stat
            defs = d
          }

        case u: AstUsing if prev != null && prev.nn.isInstanceOf[AstUsing] &&
                            prev.nn.asInstanceOf[AstUsing].isAwait == u.isAwait =>
          prev.nn.asInstanceOf[AstUsing].definitions.addAll(u.definitions)
          markChanged()

        case exit: AstExit =>
          exit.value = extractObjectAssignments(exit.value, stat, prev)

        case forStat: AstFor =>
          val exprs = joinObjectAssignments(prev, forStat.init, nearestScope, compressor)
          if (exprs != null) {
            markChanged()
            forStat.init = if (exprs.nn.nonEmpty) makeSequence(forStat.init.nn, exprs.nn) else null
            j += 1
            statements(j) = stat
          } else if (prev != null && prev.nn.isInstanceOf[AstVar] &&
              (forStat.init == null || forStat.init.nn.getClass == prev.nn.getClass)) {
            if (forStat.init != null) {
              prev.nn.asInstanceOf[AstVar].definitions.addAll(forStat.init.nn.asInstanceOf[AstDefinitions].definitions)
            }
            forStat.init = prev.nn.asInstanceOf[AstVar]
            statements(j) = stat
            markChanged()
          } else if (defs != null && defs.nn.isInstanceOf[AstVar] && forStat.init != null &&
                     forStat.init.nn.isInstanceOf[AstVar] &&
                     declarationsOnly(forStat.init.nn.asInstanceOf[AstVar])) {
            defs.nn.definitions.addAll(forStat.init.nn.asInstanceOf[AstDefinitions].definitions)
            forStat.init = null
            j += 1
            statements(j) = stat
            markChanged()
          } else {
            j += 1
            statements(j) = stat
          }

        case forIn: AstForIn =>
          forIn.obj = extractObjectAssignments(forIn.obj, stat, prev)

        case ifStat: AstIf =>
          ifStat.condition = extractObjectAssignments(ifStat.condition, stat, prev)

        case ss: AstSimpleStatement =>
          val exprs = joinObjectAssignments(prev, ss.body, nearestScope, compressor)
          if (exprs != null) {
            markChanged()
            if (exprs.nn.isEmpty) {
              // All consumed - skip statement
              i += 1
              // Don't increment j
            } else {
              ss.body = makeSequence(ss.body.nn, exprs.nn)
              j += 1
              statements(j) = stat
              i += 1
            }
          } else {
            j += 1
            statements(j) = stat
            i += 1
          }
          i -= 1 // Cancel the increment at end of loop since we handle i manually here

        case sw: AstSwitch =>
          sw.expression = extractObjectAssignments(sw.expression, stat, prev)

        case w: AstWith =>
          w.expression = extractObjectAssignments(w.expression, stat, prev)

        case _ =>
          j += 1
          statements(j) = stat
      }
      i += 1
    }
    while (statements.size > j + 1) statements.remove(statements.size - 1)
  }

  // -----------------------------------------------------------------------
  // Sub-pass: collapse_vars
  // -----------------------------------------------------------------------

  /** Check if LHS is read-only (cannot be assigned to). */
  private def isLhsReadOnly(lhs: AstNode): Boolean =
    lhs match {
      case _: AstThis => true
      case ref: AstSymbolRef =>
        ref.definition() match {
          case null => false
          case d    => d.nn.orig.nonEmpty && d.nn.orig(0).isInstanceOf[AstSymbolLambda]
        }
      case pa: AstPropAccess =>
        var expr: AstNode = pa.expression.nn
        expr match {
          case ref: AstSymbolRef =>
            if (isRefImmutable(ref)) return false // @nowarn
            ref.fixedValue() match {
              case n: AstNode => expr = n
              case _ => return true // @nowarn -- no fixed value
            }
          case _ =>
        }
        expr match {
          case _: AstRegExp   => false
          case _: AstConstant => true
          case _              => isLhsReadOnly(expr)
        }
      case _ => false
    }

  /** Main collapse_vars pass.
    *
    * Search from right to left for assignment-like expressions:
    * - `var a = x;`
    * - `a = x;`
    * - `++a`
    *
    * For each candidate, scan from left to right for first usage, then try to fold assignment into the site for compression.
    */
  private def collapseVars(
    statements:   ArrayBuffer[AstNode],
    compressor:   CompressorLike,
    nearestScope: AstScope,
    defunScope:   AstScope,
    inLoop:       Boolean,
    inTry:        Boolean,
    markChanged:  () => Unit
  ): Unit = {
    // Check if scope is pinned (has eval/with)
    if (nearestScope.pinned || defunScope.pinned) return // @nowarn

    val candidates = ArrayBuffer.empty[ArrayBuffer[AstNode]]
    var statIndex  = statements.size

    // Variables used during scanning (some only used in full implementation)
    // @nowarn annotations suppress warnings for vars used in simplified implementation
    @annotation.nowarn("msg=local variable") var abort       = false
    @annotation.nowarn("msg=local variable") var hit         = false
    @annotation.nowarn("msg=local variable") var hitIndex    = 0
    var hitStack    = ArrayBuffer.empty[AstNode]
    var candidate:  AstNode = null.asInstanceOf[AstNode]
    var valueDef:   SymbolDef | Null = null
    @annotation.nowarn("msg=local variable") var stopAfter:  AstNode | Null = null
    @annotation.nowarn("msg=local variable") var stopIfHit:  AstNode | Null = null
    var lhs:        AstNode | Null = null
    var lvalues:    mutable.Map[String, LValueInfo] = mutable.Map.empty
    @annotation.nowarn("msg=local variable") var lhsLocal    = false
    var sideEffects = false
    @annotation.nowarn("msg=local variable") var replaceAll  = false
    @annotation.nowarn("msg=local variable") var mayThrowFlag = false
    var funarg      = false
    var replaced    = 0
    @annotation.nowarn("msg=local variable") var canReplace  = false

    /** Info about an lvalue reference. */
    final case class LValueInfo(d: SymbolDef, modified: Boolean)

    /** Check if any lvalue shadows a variable in the given scope. */
    @annotation.nowarn("msg=unused local definition")
    def shadows(myScope: AstScope | Null): Boolean =
      boundary[Boolean] {
        if (myScope == null) break(false)
        for ((name, info) <- lvalues) {
          val lookedUp = ScopeAnalysis.findVariable(myScope.nn, name)
          if (lookedUp != null && !(lookedUp.nn.asInstanceOf[AnyRef] eq info.d.asInstanceOf[AnyRef])) {
            break(true)
          }
        }
        false
      }

    /** Get the value to be assigned from a candidate. */
    def getRValue(expr: AstNode): AstNode | Null =
      expr match {
        case a: AstAssign => a.right
        case v: AstVarDef => v.value
        case _ => null
      }

    /** Get the left-hand side of a candidate. */
    def getLhs(expr: AstNode): AstNode | Null = {
      expr match {
        case a: AstAssign if a.logical => null
        case v: AstVarDef =>
          v.name match {
            case sym: AstSymbolDeclaration =>
              val d = sym.definition()
              if (d == null || !d.nn.orig.contains(sym)) return null // @nowarn
              val referenced = d.nn.references.size - d.nn.replaced
              if (referenced == 0) return null // @nowarn
              val declared = d.nn.orig.size - d.nn.eliminated
              if (declared > 1 && !v.name.isInstanceOf[AstSymbolFunarg]) {
                val ref = new AstSymbolRef
                ref.start = v.name.start
                ref.end = v.name.end
                ref.name = sym.name
                ref.thedef = sym.thedef
                ref
              } else if (referenced > 1) {
                mangleableVar(v) match {
                  case Some(_) =>
                    val ref = new AstSymbolRef
                    ref.start = v.name.start
                    ref.end = v.name.end
                    ref.name = sym.name
                    ref.thedef = sym.thedef
                    ref
                  case None => null
                }
              } else if (!compressor.exposed(d.nn)) {
                val ref = new AstSymbolRef
                ref.start = v.name.start
                ref.end = v.name.end
                ref.name = sym.name
                ref.thedef = sym.thedef
                ref
              } else {
                null
              }
            case _ => null
          }
        case a: AstAssign =>
          val l = a.left
          l match {
            case ref: AstSymbolRef =>
              ref.definition() match {
                case null => l
                case d =>
                  if (d.nn.orig.nonEmpty &&
                      (d.nn.orig(0).isInstanceOf[AstSymbolConst] ||
                       d.nn.orig(0).isInstanceOf[AstSymbolLet] ||
                       d.nn.orig(0).isInstanceOf[AstSymbolUsing])) {
                    null
                  } else {
                    l
                  }
              }
            case _ => l
          }
        case u: AstUnary => u.expression
        case _ => null
      }
    }

    /** Check if VarDef's value is a simple SymbolRef that can be mangled. */
    def mangleableVar(varDef: AstVarDef): Option[SymbolDef] =
      varDef.value match {
        case ref: AstSymbolRef if ref.name != "arguments" =>
          ref.definition() match {
            case null => None
            case d if d.nn.undeclared => None
            case d =>
              valueDef = d.nn
              Some(d.nn)
          }
        case _ => None
      }

    /** Get all lvalues from an expression's RHS. */
    def getLvalues(expr: AstNode): mutable.Map[String, LValueInfo] = {
      val result = mutable.Map.empty[String, LValueInfo]
      if (expr.isInstanceOf[AstUnary]) return result // @nowarn

      var tw: TreeWalker = null.asInstanceOf[TreeWalker] // @nowarn -- initialized before use
      tw = new TreeWalker((node, _) => {
        var sym = node
        while (sym.isInstanceOf[AstPropAccess])
          sym = sym.asInstanceOf[AstPropAccess].expression.nn
        sym match {
          case ref: AstSymbolRef =>
            ref.definition() match {
              case null =>
              case d =>
                val prev = result.get(ref.name)
                if (prev.isEmpty || !prev.get.modified) {
                  result(ref.name) = LValueInfo(d.nn, isModified(compressor, tw, node, node, 0))
                }
            }
          case _ =>
        }
        null
      })
      getRValue(expr) match {
        case null =>
        case rv   => rv.nn.walk(tw)
      }
      result
    }

    /** Check if the LHS is local to the function scope. */
    def computeLhsLocal(): Boolean = {
      if (lhs == null) return false // @nowarn
      var l = lhs.nn
      while (l.isInstanceOf[AstPropAccess])
        l = l.asInstanceOf[AstPropAccess].expression.nn
      l match {
        case ref: AstSymbolRef =>
          val d = ref.definition()
          d != null && d.nn.scope.getDefunScope == defunScope &&
            !(inLoop &&
              (lvalues.contains(ref.name) ||
               candidate.isInstanceOf[AstUnary] ||
               (candidate.isInstanceOf[AstAssign] &&
                !candidate.asInstanceOf[AstAssign].logical &&
                candidate.asInstanceOf[AstAssign].operator != "=")))
        case _ => false
      }
    }

    /** Check if value has side effects. */
    def valueHasSideEffects(expr: AstNode): Boolean =
      expr match {
        case u: AstUnary => unarySideEffects.contains(u.operator)
        case _ =>
          getRValue(expr) match {
            case null => false
            case rv   => hasSideEffects(rv.nn, compressor)
          }
      }

    /** Check if we should replace all occurrences. */
    def replaceAllSymbols: Boolean = {
      if (sideEffects) return false // @nowarn
      if (valueDef != null) return true // @nowarn
      lhs match {
        case ref: AstSymbolRef =>
          ref.definition() match {
            case null => false
            case d =>
              val refCount = d.nn.references.size - d.nn.replaced
              refCount == (if (candidate.isInstanceOf[AstVarDef]) 1 else 2)
          }
        case _ => false
      }
    }

    /** Check if symbol may be modified from outside the current scope. */
    @annotation.nowarn("msg=unused local definition")
    def mayModify(sym: AstNode): Boolean = {
      sym match {
        case _: AstDestructuring => true
        case ref: AstSymbolRef =>
          ref.definition() match {
            case null => true
            case d =>
              if (d.nn.orig.size == 1 && d.nn.orig(0).isInstanceOf[AstSymbolDefun]) {
                false
              } else if (d.nn.scope.getDefunScope != defunScope) {
                true
              } else {
                d.nn.references.exists(r => r.scope.getDefunScope != defunScope)
              }
          }
        case _ => true
      }
    }

    /** Check for side effects external to the current scope. */
    @annotation.nowarn("msg=unused")
    def sideEffectsExternal(node: AstNode, isLhsArg: Boolean = false): Boolean = {
      node match {
        case a: AstAssign  => sideEffectsExternal(a.left.nn, isLhsArg = true)
        case u: AstUnary   => sideEffectsExternal(u.expression.nn, isLhsArg = true)
        case v: AstVarDef  => v.value != null && sideEffectsExternal(v.value.nn, isLhsArg = false)
        case _ =>
          if (isLhsArg) {
            node match {
              case dot: AstDot => sideEffectsExternal(dot.expression.nn, isLhsArg = true)
              case sub: AstSub => sideEffectsExternal(sub.expression.nn, isLhsArg = true)
              case ref: AstSymbolRef =>
                ref.definition() match {
                  case null => true
                  case d    => d.nn.scope.getDefunScope != defunScope
                }
              case _ => false
            }
          } else {
            false
          }
      }
    }

    // -----------------------------------------------------------------------
    // handle_custom_scan_order: custom traversal order for certain AST nodes
    // -----------------------------------------------------------------------
    def handleCustomScanOrder(node: AstNode, scanner: TreeTransformer): AstNode = {
      // Skip (non-executed) functions
      if (node.isInstanceOf[AstScope]) return node // @nowarn

      // Scan case expressions first in a switch statement
      node match {
        case sw: AstSwitch =>
          sw.expression = sw.expression.nn.transform(scanner)
          var i = 0
          while (!abort && i < sw.body.size) {
            sw.body(i) match {
              case branch: AstCase =>
                if (!hit) {
                  if (!(branch.asInstanceOf[AnyRef] eq hitStack(hitIndex).asInstanceOf[AnyRef])) {
                    i += 1
                  } else {
                    hitIndex += 1
                    branch.expression = branch.expression.nn.transform(scanner)
                    if (!replaceAll) {
                      i = sw.body.size // break out
                    } else {
                      i += 1
                    }
                  }
                } else {
                  branch.expression = branch.expression.nn.transform(scanner)
                  if (!replaceAll) {
                    i = sw.body.size // break out
                  } else {
                    i += 1
                  }
                }
              case _ => i += 1
            }
          }
          abort = true
          return node // @nowarn
        case _ =>
      }
      node
    }

    // -----------------------------------------------------------------------
    // find_stop: determine where collapsing must stop
    // -----------------------------------------------------------------------
    def findStop(node: AstNode, level: Int, writeOnly: Boolean, scanner: TreeTransformer): AstNode | Null = {
      val parent = scanner.parent(level)
      if (parent == null) return null // @nowarn

      parent.nn match {
        case assign: AstAssign =>
          if (writeOnly && !assign.logical &&
              !(assign.left.nn.isInstanceOf[AstPropAccess] ||
                (assign.left.nn.isInstanceOf[AstSymbolRef] &&
                  lvalues.contains(assign.left.nn.asInstanceOf[AstSymbolRef].name)))) {
            findStop(parent.nn, level + 1, writeOnly, scanner)
          } else {
            node
          }

        case binary: AstBinary =>
          if (writeOnly && (!lazyOp.contains(binary.operator) || (binary.left.nn eq node))) {
            findStop(parent.nn, level + 1, writeOnly, scanner)
          } else {
            node
          }

        case _: AstCall => node

        case _: AstCase => node

        case cond: AstConditional =>
          if (writeOnly && (cond.condition.nn eq node)) {
            findStop(parent.nn, level + 1, writeOnly, scanner)
          } else {
            node
          }

        case _: AstDefinitions =>
          findStop(parent.nn, level + 1, writeOnly = true, scanner)

        case _: AstExit =>
          if (writeOnly) findStop(parent.nn, level + 1, writeOnly, scanner)
          else node

        case ifStat: AstIf =>
          if (writeOnly && (ifStat.condition.nn eq node)) {
            findStop(parent.nn, level + 1, writeOnly, scanner)
          } else {
            node
          }

        case _: AstIterationStatement => node

        case seq: AstSequence =>
          val tailNode = seq.expressions.lastOption.orNull
          findStop(parent.nn, level + 1, !(node.asInstanceOf[AnyRef] eq tailNode.asInstanceOf[AnyRef]), scanner)

        case _: AstSimpleStatement =>
          findStop(parent.nn, level + 1, writeOnly = true, scanner)

        case _: AstSwitch => node

        case _: AstVarDef => node

        case _ => null
      }
    }

    // -----------------------------------------------------------------------
    // redefined_within_scope: check if variable is redefined in an inner scope
    // -----------------------------------------------------------------------
    def redefinedWithinScope(d: SymbolDef, scope: AstScope): Boolean = {
      if (d.global) return false // @nowarn
      var curScope = d.scope
      while (curScope != null && !(curScope.asInstanceOf[AnyRef] eq scope.asInstanceOf[AnyRef])) {
        if (curScope.nn.variables.contains(d.name)) {
          return true // @nowarn
        }
        curScope = curScope.nn.parentScope
      }
      false
    }

    // -----------------------------------------------------------------------
    // has_overlapping_symbol: check if arg references vars defined in fn
    // -----------------------------------------------------------------------
    def hasOverlappingSymbol(fn: AstLambda, arg: AstNode, fnStrict: Boolean): Boolean = {
      var found     = false
      var scanThisVar = !fn.isInstanceOf[AstArrow]

      val tw = new TreeWalker((node, _) => {
        if (found) {
          true // skip
        } else {
          node match {
            case ref: AstSymbolRef if fn.variables.contains(ref.name) ||
                                      (ref.definition() != null && redefinedWithinScope(ref.definition().nn, fn)) =>
              val s = ref.definition().nn.scope
              if (!(s.asInstanceOf[AnyRef] eq defunScope.asInstanceOf[AnyRef])) {
                var ps = s.parentScope
                var foundDefun = false
                while (ps != null && !foundDefun) {
                  if (ps.nn.asInstanceOf[AnyRef] eq defunScope.asInstanceOf[AnyRef]) {
                    foundDefun = true
                  }
                  ps = ps.nn.parentScope
                }
                if (foundDefun) {
                  found = true
                  true // skip
                } else {
                  found = true
                  true // skip
                }
              } else {
                found = true
                true // skip
              }

            case _: AstThis if fnStrict || scanThisVar =>
              found = true
              true // skip

            case _: AstScope if !node.isInstanceOf[AstArrow] =>
              // Non-arrow scope has its own `this` binding, don't scan for `this` inside
              scanThisVar = false
              null // let normal descent happen, we track via scanThisVar

            case _ =>
              null // continue descent
          }
        }
      })
      arg.walk(tw)
      found
    }

    // -----------------------------------------------------------------------
    // arg_is_injectable: check if IIFE arg can be injected as candidate
    // -----------------------------------------------------------------------
    def argIsInjectable(arg: AstNode): Boolean = {
      if (arg.isInstanceOf[AstExpansion]) return false // @nowarn
      var containsAwait = false
      walk(arg, (node, _) => node match {
        case _: AstAwait =>
          containsAwait = true
          WalkAbort
        case _ => null
      })
      !containsAwait
    }

    // -----------------------------------------------------------------------
    // extract_args: extract IIFE arguments into candidates
    // -----------------------------------------------------------------------
    var args: ArrayBuffer[AstNode] | Null = null

    def extractArgs(): Unit = {
      val fn   = compressor.parent()
      val iife = compressor.parent(1)
      if (fn == null || iife == null) return // @nowarn
      if (!isFuncExpr(fn.nn)) return // @nowarn
      fn.nn match {
        case lambda: AstLambda =>
          if (lambda.name != null || lambda.usesArguments || lambda.pinned) return // @nowarn
          iife.nn match {
            case call: AstCall if call.expression.nn.asInstanceOf[AnyRef] eq lambda.asInstanceOf[AnyRef] =>
              if (!call.args.forall(argIsInjectable)) return // @nowarn

              val fnStrictDir = compressor.hasDirective("use strict")
              val fnStrict = fnStrictDir != null && !lambda.body.contains(fnStrictDir.nn)

              val len   = lambda.argnames.size
              args = ArrayBuffer.from(call.args.slice(len, call.args.size))
              val names = mutable.Set.empty[String]

              var i = len - 1
              while (i >= 0) {
                val sym = lambda.argnames(i)
                val arg = if (i < call.args.size) call.args(i) else null

                // Check if reassigned
                val d = sym match {
                  case s: AstSymbol => s.definition()
                  case _            => null
                }
                val isReassigned = d != null && d.nn.orig.size > 1
                if (!isReassigned) {
                  val varDef = new AstVarDef
                  varDef.start = sym.start
                  varDef.end = sym.end
                  varDef.name = sym
                  varDef.value = arg
                  args.nn.insert(0, varDef)

                  val symName = sym match {
                    case s: AstSymbol           => s.name
                    case exp: AstExpansion      => exp.expression.nn.asInstanceOf[AstSymbol].name
                    case _ => ""
                  }
                  if (!names.contains(symName)) {
                    names.add(symName)

                    sym match {
                      case exp: AstExpansion =>
                        val elements = ArrayBuffer.from(call.args.slice(i, call.args.size))
                        if (elements.forall(e => !hasOverlappingSymbol(lambda, e, fnStrict))) {
                          val arr = new AstArray
                          arr.start = call.start
                          arr.end = call.end
                          arr.elements = elements

                          val candVarDef = new AstVarDef
                          candVarDef.start = sym.start
                          candVarDef.end = sym.end
                          candVarDef.name = exp.expression.nn.asInstanceOf[AstSymbol]
                          candVarDef.value = arr
                          candidates.insert(0, ArrayBuffer(candVarDef))
                        }

                      case _ =>
                        var candArg: AstNode | Null = arg
                        if (candArg == null) {
                          val zero = new AstNumber
                          zero.start = sym.start
                          zero.end = sym.end
                          zero.value = 0.0
                          val voidExpr = new AstUnaryPrefix
                          voidExpr.start = sym.start
                          voidExpr.end = sym.end
                          voidExpr.operator = "void"
                          voidExpr.expression = zero
                          candArg = voidExpr
                        } else if (candArg.nn.isInstanceOf[AstLambda] && candArg.nn.asInstanceOf[AstLambda].pinned ||
                                   hasOverlappingSymbol(lambda, candArg.nn, fnStrict)) {
                          candArg = null
                        }
                        if (candArg != null) {
                          val candVarDef = new AstVarDef
                          candVarDef.start = sym.start
                          candVarDef.end = sym.end
                          candVarDef.name = sym
                          candVarDef.value = candArg.nn
                          candidates.insert(0, ArrayBuffer(candVarDef))
                        }
                    }
                  }
                }
                i -= 1
              }
            case _ =>
          }
        case _ =>
      }
    }

    // -----------------------------------------------------------------------
    // remove_candidate: remove candidate from statement after successful collapse
    // -----------------------------------------------------------------------
    def removeCandidate(expr: AstNode): Boolean = {
      expr match {
        case v: AstVarDef if v.name.isInstanceOf[AstSymbolFunarg] =>
          val iife = compressor.parent()
          val argnames = compressor.parent().nn match {
            case l: AstLambda => l.argnames
            case _ => return true // @nowarn
          }
          val index = argnames.indexOf(v.name)
          iife.nn match {
            case call: AstCall =>
              if (index < 0) {
                // Truncate args if not found
                if (argnames.nonEmpty && call.args.size > argnames.size - 1) {
                  while (call.args.size > argnames.size - 1) call.args.remove(call.args.size - 1)
                }
              } else if (index < call.args.size && call.args(index) != null) {
                // Replace arg with 0
                val zero = new AstNumber
                zero.start = call.args(index).start
                zero.end = call.args(index).end
                zero.value = 0.0
                call.args(index) = zero
              }
            case _ =>
          }
          return true // @nowarn
        case _ =>
      }

      var found = false

      val remover = new TreeTransformer(
        before = (node, _) => {
          if (found) node
          else if ((node.asInstanceOf[AnyRef] eq expr.asInstanceOf[AnyRef]) ||
                   (node.isInstanceOf[AstSimpleStatement] &&
                     (node.asInstanceOf[AstSimpleStatement].body.nn.asInstanceOf[AnyRef] eq expr.asInstanceOf[AnyRef]))) {
            found = true
            node match {
              case vd: AstVarDef =>
                if (vd.name.isInstanceOf[AstSymbolConst]) {
                  // const always needs a value - use void 0
                  val zero = new AstNumber
                  zero.start = vd.start
                  zero.end = vd.end
                  zero.value = 0.0
                  val voidExpr = new AstUnaryPrefix
                  voidExpr.start = vd.start
                  voidExpr.end = vd.end
                  voidExpr.operator = "void"
                  voidExpr.expression = zero
                  vd.value = voidExpr
                } else {
                  vd.value = null
                }
                node
              case _ =>
                // Return null to remove the node (in_list case handled below)
                null
            }
          } else {
            null // continue descent
          }
        },
        after = node => {
          node match {
            case seq: AstSequence =>
              seq.expressions.size match {
                case 0 => null
                case 1 => seq.expressions(0)
                case _ => node
              }
            case _ => node
          }
        }
      )

      statements(statIndex) = statements(statIndex).transform(remover)
      found
    }

    /** Extract candidates from a statement. */
    def extractCandidates(expr: AstNode): Unit = {
      hitStack.addOne(expr)
      expr match {
        case a: AstAssign if !hasSideEffects(a.left.nn, compressor) && !a.right.nn.isInstanceOf[AstChain] =>
          candidates.addOne(ArrayBuffer.from(hitStack))
          extractCandidates(a.right.nn)

        case b: AstBinary =>
          extractCandidates(b.left.nn)
          extractCandidates(b.right.nn)

        case call: AstCall if (call.flags & Annotations.NoInline) == 0 =>
          extractCandidates(call.expression.nn)
          call.args.foreach(extractCandidates)

        case cas: AstCase =>
          extractCandidates(cas.expression.nn)

        case cond: AstConditional =>
          extractCandidates(cond.condition.nn)
          extractCandidates(cond.consequent.nn)
          extractCandidates(cond.alternative.nn)

        case defs: AstDefinitions =>
          val len = defs.definitions.size
          var i   = Math.max(0, len - 200)
          while (i < len) {
            extractCandidates(defs.definitions(i))
            i += 1
          }

        case dw: AstDWLoop =>
          extractCandidates(dw.condition.nn)
          dw.body match {
            case _: AstBlock =>
            case b           => extractCandidates(b)
          }

        case exit: AstExit if exit.value != null =>
          extractCandidates(exit.value.nn)

        case forStat: AstFor =>
          if (forStat.init != null) extractCandidates(forStat.init.nn)
          if (forStat.condition != null) extractCandidates(forStat.condition.nn)
          if (forStat.step != null) extractCandidates(forStat.step.nn)
          forStat.body match {
            case _: AstBlock =>
            case b           => extractCandidates(b)
          }

        case forIn: AstForIn =>
          extractCandidates(forIn.obj.nn)
          forIn.body match {
            case _: AstBlock =>
            case b           => extractCandidates(b)
          }

        case ifStat: AstIf =>
          extractCandidates(ifStat.condition.nn)
          ifStat.body match {
            case _: AstBlock =>
            case b           => extractCandidates(b)
          }
          if (ifStat.alternative != null) {
            ifStat.alternative.nn match {
              case _: AstBlock =>
              case b           => extractCandidates(b)
            }
          }

        case seq: AstSequence =>
          seq.expressions.foreach(extractCandidates)

        case ss: AstSimpleStatement =>
          extractCandidates(ss.body.nn)

        case sw: AstSwitch =>
          extractCandidates(sw.expression.nn)
          sw.body.foreach(extractCandidates)

        case u: AstUnary if u.operator == "++" || u.operator == "--" =>
          candidates.addOne(ArrayBuffer.from(hitStack))

        case v: AstVarDef if v.value != null && !v.value.nn.isInstanceOf[AstChain] =>
          candidates.addOne(ArrayBuffer.from(hitStack))
          extractCandidates(v.value.nn)

        case _ =>
      }
      hitStack.remove(hitStack.size - 1)
    }

    // -----------------------------------------------------------------------
    // scanner: TreeTransformer that tracks state and replaces variables
    // -----------------------------------------------------------------------
    lazy val scanner: TreeTransformer = new TreeTransformer(
      before = (node, descend) => {
        if (abort) node
        else {
          // Skip nodes before `candidate` as quickly as possible
          if (!hit) {
            if (!(node.asInstanceOf[AnyRef] eq hitStack(hitIndex).asInstanceOf[AnyRef])) {
              node
            } else {
              hitIndex += 1
              if (hitIndex < hitStack.size) {
                handleCustomScanOrder(node, scanner)
              } else {
                hit = true
                stopAfter = findStop(node, 0, writeOnly = false, scanner)
                if (stopAfter != null && (stopAfter.nn.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef])) {
                  abort = true
                }
                node
              }
            }
          } else {
            // Stop immediately if these node types are encountered
            val parent = scanner.parent()

            // Check for abort conditions
            val shouldAbort = {
              (node.isInstanceOf[AstAssign] &&
                (node.asInstanceOf[AstAssign].logical ||
                  (node.asInstanceOf[AstAssign].operator != "=" &&
                    lhs != null && AstEquivalent.equivalentTo(lhs.nn, node.asInstanceOf[AstAssign].left.nn)))) ||
              node.isInstanceOf[AstAwait] ||
              node.isInstanceOf[AstUsing] ||
              (node.isInstanceOf[AstCall] && lhs != null && lhs.nn.isInstanceOf[AstPropAccess] &&
                AstEquivalent.equivalentTo(lhs.nn, node.asInstanceOf[AstCall].expression.nn)) ||
              ((node.isInstanceOf[AstCall] || node.isInstanceOf[AstPropAccess]) &&
                (node match {
                  case c: AstCall       => c.optional
                  case p: AstPropAccess => p.optional
                  case _                => false
                })) ||
              node.isInstanceOf[AstDebugger] ||
              node.isInstanceOf[AstDestructuring] ||
              (node.isInstanceOf[AstExpansion] &&
                node.asInstanceOf[AstExpansion].expression.nn.isInstanceOf[AstSymbol] &&
                (node.asInstanceOf[AstExpansion].expression.nn.isInstanceOf[AstThis] ||
                  {
                    val expRef = node.asInstanceOf[AstExpansion].expression.nn.asInstanceOf[AstSymbol]
                    val expDef = expRef.definition()
                    expDef != null && expDef.nn.references.size > 1
                  })) ||
              (node.isInstanceOf[AstIterationStatement] && !node.isInstanceOf[AstFor]) ||
              node.isInstanceOf[AstLoopControl] ||
              node.isInstanceOf[AstTry] ||
              node.isInstanceOf[AstWith] ||
              node.isInstanceOf[AstYield] ||
              node.isInstanceOf[AstExport] ||
              node.isInstanceOf[AstClass] ||
              (parent != null && parent.nn.isInstanceOf[AstFor] &&
                !(node.asInstanceOf[AnyRef] eq parent.nn.asInstanceOf[AstFor].init.asInstanceOf[AnyRef])) ||
              (!replaceAll &&
                node.isInstanceOf[AstSymbolRef] &&
                !Inference.isRefDeclared(node.asInstanceOf[AstSymbolRef], compressor) &&
                !purePropAccessGlobals.contains(node.asInstanceOf[AstSymbolRef].name)) ||
              (node.isInstanceOf[AstSymbolRef] &&
                parent != null && parent.nn.isInstanceOf[AstCall] &&
                (parent.nn.asInstanceOf[AstCall].flags & Annotations.NoInline) != 0) ||
              (node.isInstanceOf[AstObjectProperty] &&
                node.asInstanceOf[AstObjectProperty].key.isInstanceOf[AstNode])
            }

            if (shouldAbort) {
              abort = true
              node
            } else {
              // Stop only if candidate is found within conditional branches
              if (stopIfHit == null && (!lhsLocal || !replaceAll)) {
                val inConditional = parent != null && (
                  (parent.nn.isInstanceOf[AstBinary] &&
                    lazyOp.contains(parent.nn.asInstanceOf[AstBinary].operator) &&
                    !(parent.nn.asInstanceOf[AstBinary].left.nn.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef])) ||
                  (parent.nn.isInstanceOf[AstConditional] &&
                    !(parent.nn.asInstanceOf[AstConditional].condition.nn.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef])) ||
                  (parent.nn.isInstanceOf[AstIf] &&
                    !(parent.nn.asInstanceOf[AstIf].condition.nn.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef]))
                )
                if (inConditional) {
                  stopIfHit = parent.nn
                }
              }

              // Replace variable with assignment when found
              if (canReplace &&
                  !node.isInstanceOf[AstSymbolDeclaration] &&
                  lhs != null && AstEquivalent.equivalentTo(lhs.nn, node) &&
                  !shadows(scanner.findScope())) {
                if (stopIfHit != null) {
                  abort = true
                  node
                } else {
                  val nodeLhs = isLhs(node, parent)
                  if (nodeLhs != null) {
                    if (valueDef != null) replaced += 1
                    node
                  } else {
                    replaced += 1
                    if (valueDef != null && candidate.isInstanceOf[AstVarDef]) {
                      node
                    } else {
                      markChanged()
                      abort = true
                      candidate match {
                        case up: AstUnaryPostfix =>
                          // Convert postfix to prefix
                          val prefix = new AstUnaryPrefix
                          prefix.start = up.start
                          prefix.end = up.end
                          prefix.operator = up.operator
                          prefix.expression = up.expression
                          prefix
                        case vd: AstVarDef =>
                          val d = vd.name.asInstanceOf[AstSymbolDeclaration].definition()
                          val value = vd.value.nn
                          if (d != null && d.nn.references.size - d.nn.replaced == 1 && !compressor.exposed(d.nn)) {
                            d.nn.replaced += 1
                            if (funarg && isIdentifierAtom(value)) {
                              value
                            } else if (parent != null) {
                              maintainThisBinding(parent.nn, node, value)
                            } else {
                              value
                            }
                          } else {
                            val assign = new AstAssign
                            assign.start = vd.start
                            assign.end = vd.end
                            assign.operator = "="
                            assign.logical = false
                            val ref = new AstSymbolRef
                            ref.start = vd.name.start
                            ref.end = vd.name.end
                            ref.name = vd.name.asInstanceOf[AstSymbol].name
                            ref.thedef = vd.name.asInstanceOf[AstSymbol].thedef
                            assign.left = ref
                            assign.right = value
                            assign
                          }
                        case _ =>
                          clearFlag(candidate, WRITE_ONLY)
                          candidate
                      }
                    }
                  }
                }
              } else {
                // Check for stop conditions that don't abort immediately
                val sym = isLhs(node.asInstanceOf[AstNode], node)
                val shouldStop = {
                  (node.isInstanceOf[AstCall]) ||
                  (node.isInstanceOf[AstExit] &&
                    (sideEffects || (lhs != null && lhs.nn.isInstanceOf[AstPropAccess]) || mayModify(lhs.nn))) ||
                  (node.isInstanceOf[AstPropAccess] &&
                    (sideEffects || Inference.mayThrowOnAccess(node.asInstanceOf[AstPropAccess].expression.nn, compressor))) ||
                  (node.isInstanceOf[AstSymbolRef] &&
                    (lvalues.get(node.asInstanceOf[AstSymbolRef].name).exists(_.modified) ||
                      (sideEffects && mayModify(node)))) ||
                  (node.isInstanceOf[AstVarDef] && node.asInstanceOf[AstVarDef].value != null &&
                    (lvalues.contains(node.asInstanceOf[AstVarDef].name.asInstanceOf[AstSymbol].name) ||
                      (sideEffects && mayModify(node.asInstanceOf[AstVarDef].name)))) ||
                  node.isInstanceOf[AstUsing] ||
                  (sym != null &&
                    (sym.isInstanceOf[AstPropAccess] ||
                      (sym.isInstanceOf[AstSymbolRef] && lvalues.contains(sym.asInstanceOf[AstSymbolRef].name)))) ||
                  (mayThrowFlag &&
                    (if (inTry) hasSideEffects(node, compressor) else sideEffectsExternal(node)))
                }

                if (shouldStop) {
                  stopAfter = node
                  if (node.isInstanceOf[AstScope]) abort = true
                }
                handleCustomScanOrder(node, scanner)
              }
            }
          }
        }
      },
      after = node => {
        if (!abort) {
          if (stopAfter != null && (stopAfter.nn.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef])) {
            abort = true
          }
          if (stopIfHit != null && (stopIfHit.nn.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef])) {
            stopIfHit = null
          }
        }
      }
    )

    // -----------------------------------------------------------------------
    // multi_replacer: TreeTransformer for multi-use variable replacement
    // -----------------------------------------------------------------------
    lazy val multiReplacer: TreeTransformer = new TreeTransformer(
      before = (node, _) => {
        if (abort) node
        else {
          // Skip nodes before `candidate` as quickly as possible
          if (!hit) {
            if (!(node.asInstanceOf[AnyRef] eq hitStack(hitIndex).asInstanceOf[AnyRef])) {
              node
            } else {
              hitIndex += 1
              if (hitIndex < hitStack.size) {
                null // continue descent
              } else {
                hit = true
                node
              }
            }
          } else {
            // Replace variable when found
            node match {
              case ref: AstSymbolRef =>
                val d = candidate.asInstanceOf[AstVarDef].name.asInstanceOf[AstSymbol].definition()
                if (d != null && ref.name == d.nn.name) {
                  replaced -= 1
                  if (replaced == 0) abort = true
                  val nodeLhs = isLhs(node, multiReplacer.parent())
                  if (nodeLhs != null) {
                    node
                  } else {
                    d.nn.replaced += 1
                    valueDef.nn.replaced -= 1
                    candidate.asInstanceOf[AstVarDef].value.nn
                  }
                } else {
                  node
                }
              // Skip (non-executed) functions and (leading) default case in switch
              case _: AstDefault | _: AstScope => node
              case _ => null // continue descent
            }
          }
        }
      }
    )

    // Main loop: iterate through statements from right to left
    while ({ statIndex -= 1; statIndex >= 0 }) {
      // Treat parameters as collapsible in IIFE
      if (statIndex == 0 && compressor.optionBool("unused")) {
        extractArgs()
      }

      hitStack.clear()
      extractCandidates(statements(statIndex))

      while (candidates.nonEmpty) {
        hitStack = candidates.remove(candidates.size - 1)
        hitIndex = 0
        candidate = hitStack.last
        valueDef = null
        stopAfter = null
        stopIfHit = null
        lhs = getLhs(candidate)
        if (lhs == null || isLhsReadOnly(lhs.nn) || hasSideEffects(lhs.nn, compressor)) {
          // Skip this candidate
        } else {
          lvalues = getLvalues(candidate)
          lhsLocal = computeLhsLocal()
          lhs match {
            case ref: AstSymbolRef =>
              val d = ref.definition()
              if (d != null) lvalues(ref.name) = LValueInfo(d.nn, modified = false)
            case _ =>
          }
          sideEffects = valueHasSideEffects(candidate)
          replaceAll = replaceAllSymbols
          mayThrowFlag = mayThrow(candidate, compressor)
          funarg = candidate match {
            case v: AstVarDef => v.name.isInstanceOf[AstSymbolFunarg]
            case _            => false
          }
          hit = funarg
          abort = false
          replaced = 0
          canReplace = args == null || !hit

          // If not can_replace yet (args exist and hit), scan trailing args first
          if (!canReplace) {
            candidate match {
              case vd: AstVarDef =>
                val argnames = compressor.parent().nn.asInstanceOf[AstLambda].argnames
                val candIndex = argnames.lastIndexOf(vd.name)
                var j = candIndex + 1
                while (!abort && args != null && j < args.nn.size) {
                  args.nn(j) = args.nn(j).transform(scanner)
                  j += 1
                }
              case _ =>
            }
            canReplace = true
          }

          // Scan statements from statIndex forward
          var i = statIndex
          while (!abort && i < statements.size) {
            statements(i) = statements(i).transform(scanner)
            i += 1
          }

          // Handle multi-use variable replacement (valueDef case)
          if (valueDef != null) {
            val d = candidate.asInstanceOf[AstVarDef].name.asInstanceOf[AstSymbol].definition()
            if (abort && d != null && d.nn.references.size - d.nn.replaced > replaced) {
              // Too many references, can't replace
              // replaced stays as-is (effectively false)
            } else {
              abort = false
              hitIndex = 0
              hit = funarg
              i = statIndex
              while (!abort && i < statements.size) {
                statements(i) = statements(i).transform(multiReplacer)
                i += 1
              }
              valueDef.nn.singleUse = false
            }
          }

          // If successful replacement, remove the candidate
          if (replaced > 0 && !removeCandidate(candidate)) {
            statements.remove(statIndex)
          }
        }
      }
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
