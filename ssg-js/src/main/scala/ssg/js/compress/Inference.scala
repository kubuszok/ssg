/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Type inference predicates and side-effect analysis.
 *
 * Determines whether expressions always produce certain types (number, string,
 * boolean), whether they have side effects, may throw, are constants, or are
 * nullish. These predicates drive optimization decisions throughout the
 * compressor.
 *
 * Ported from: terser lib/compress/inference.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*, DEFMETHOD pattern -> pattern matching functions,
 *     is_undeclared_ref -> isUndeclaredRef, is_lhs -> isLhs, lazy_op -> lazyOp,
 *     unary_side_effects -> unarySideEffects, has_side_effects -> hasSideEffects,
 *     may_throw_on_access -> mayThrowOnAccess, is_modified -> isModified,
 *     is_used_in_expression -> isUsedInExpression, is_nullish -> isNullish,
 *     is_undefined -> isUndefined, is_nullish_shortcircuited -> isNullishShortcircuited
 *   Convention: Object with methods, pattern matching instead of DEFMETHOD
 *   Idiom: boundary/break instead of return, Set instead of makePredicate
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.NativeObjects.*

/** Type inference and side-effect analysis predicates.
  *
  * Provides functions to determine static properties of AST nodes:
  *   - Type predicates: isNumber, isString, isBoolean, isBigInt
  *   - Side effect analysis: hasSideEffects, mayThrow
  *   - Nullish analysis: isNullish, isNullishShortcircuited
  *   - Property access safety: mayThrowOnAccess
  *   - Constant analysis: isConstantExpression
  *   - Flow analysis helpers: isModified, isLhs, isUsedInExpression
  */
object Inference {

  /** Operators that short-circuit (lazy evaluation). */
  val lazyOp: Set[String] = Set("&&", "||", "??")

  /** Unary operators with side effects. */
  val unarySideEffects: Set[String] = Set("delete", "++", "--")

  /** Bitwise binary operators. */
  val bitwiseBinop: Set[String] = Set("<<<", ">>", "<<", "&", "|", "^", "~")

  // -----------------------------------------------------------------------
  // Undeclared reference check
  // -----------------------------------------------------------------------

  /** Check if a node is a SymbolRef whose definition is undeclared. */
  def isUndeclaredRef(node: AstNode): Boolean =
    node match {
      case ref: AstSymbolRef =>
        ref.thedef match {
          case null => true // no definition means undeclared
          case _    => false // TODO: check def.undeclared when SymbolDef is ported
        }
      case _ => false
    }

  // -----------------------------------------------------------------------
  // Boolean type predicate
  // -----------------------------------------------------------------------

  private val unaryBoolOps:  Set[String] = Set("!", "delete")
  private val binaryBoolOps: Set[String] = Set("in", "instanceof", "==", "!=", "===", "!==", "<", "<=", ">=", ">")

  /** Does this expression always produce a boolean value? */
  def isBoolean(node: AstNode): Boolean =
    node match {
      case prefix: AstUnaryPrefix => unaryBoolOps.contains(prefix.operator)
      case assign: AstAssign      => // must come before AstBinary
        assign.operator == "=" && assign.right != null && isBoolean(assign.right.nn)
      case binary: AstBinary =>
        binaryBoolOps.contains(binary.operator) ||
        (lazyOp.contains(binary.operator) &&
          binary.left != null && binary.right != null &&
          isBoolean(binary.left.nn) && isBoolean(binary.right.nn))
      case cond: AstConditional =>
        cond.consequent != null && cond.alternative != null &&
        isBoolean(cond.consequent.nn) && isBoolean(cond.alternative.nn)
      case seq: AstSequence =>
        seq.expressions.nonEmpty && isBoolean(seq.expressions.last)
      case _: AstTrue | _: AstFalse => true
      case _                        => false
    }

  // -----------------------------------------------------------------------
  // Number type predicate
  // -----------------------------------------------------------------------

  private val unaryNumOps: Set[String] = Set("+", "-", "~", "++", "--")
  private val numericOps:  Set[String] = Set("-", "*", "/", "%", "&", "|", "^", "<<", ">>", ">>>")

  /** Does this expression always produce a number? */
  def isNumber(node: AstNode, compressor: CompressorLike): Boolean =
    node match {
      case _:     AstNumber => true
      case unary: AstUnary  =>
        unaryNumOps.contains(unary.operator) &&
        unary.expression != null && isNumber(unary.expression.nn, compressor)
      case assign: AstAssign => // must come before AstBinary
        (assign.operator == "=" || numericOps.contains(assign.operator.stripSuffix("="))) &&
        assign.right != null && isNumber(assign.right.nn, compressor)
      case binary: AstBinary =>
        if (binary.operator == "+") {
          binary.left != null && binary.right != null &&
          isNumber(binary.left.nn, compressor) && isNumberOrBigInt(binary.right.nn, compressor) ||
          binary.right != null && binary.left != null &&
          isNumber(binary.right.nn, compressor) && isNumberOrBigInt(binary.left.nn, compressor)
        } else if (numericOps.contains(binary.operator)) {
          (binary.left != null && isNumber(binary.left.nn, compressor)) ||
          (binary.right != null && isNumber(binary.right.nn, compressor))
        } else {
          false
        }
      case seq: AstSequence =>
        seq.expressions.nonEmpty && isNumber(seq.expressions.last, compressor)
      case cond: AstConditional =>
        cond.consequent != null && cond.alternative != null &&
        isNumber(cond.consequent.nn, compressor) && isNumber(cond.alternative.nn, compressor)
      case _ => false
    }

  // -----------------------------------------------------------------------
  // BigInt type predicate
  // -----------------------------------------------------------------------

  private val bigintNumericOps: Set[String] = Set("-", "*", "/", "%", "&", "|", "^", "<<", ">>")

  /** Does this expression always produce a BigInt? */
  def isBigInt(node: AstNode, compressor: CompressorLike): Boolean =
    node match {
      case _:     AstBigInt => true
      case unary: AstUnary  =>
        unaryNumOps.contains(unary.operator) &&
        unary.expression != null && isBigInt(unary.expression.nn, compressor)
      case assign: AstAssign => // must come before AstBinary
        (bigintNumericOps.contains(assign.operator.stripSuffix("=")) || assign.operator == "=") &&
        assign.right != null && isBigInt(assign.right.nn, compressor)
      case binary: AstBinary =>
        if (binary.operator == "+") {
          binary.left != null && binary.right != null &&
          isBigInt(binary.left.nn, compressor) && isNumberOrBigInt(binary.right.nn, compressor) ||
          binary.right != null && binary.left != null &&
          isBigInt(binary.right.nn, compressor) && isNumberOrBigInt(binary.left.nn, compressor)
        } else if (bigintNumericOps.contains(binary.operator)) {
          (binary.left != null && isBigInt(binary.left.nn, compressor)) ||
          (binary.right != null && isBigInt(binary.right.nn, compressor))
        } else {
          false
        }
      case seq: AstSequence =>
        seq.expressions.nonEmpty && isBigInt(seq.expressions.last, compressor)
      case cond: AstConditional =>
        cond.consequent != null && cond.alternative != null &&
        isBigInt(cond.consequent.nn, compressor) && isBigInt(cond.alternative.nn, compressor)
      case _ => false
    }

  // -----------------------------------------------------------------------
  // Number or BigInt predicate
  // -----------------------------------------------------------------------

  private val numericUnaryOps:         Set[String] = Set("+", "-", "~", "++", "--")
  private val numberOrBigintBinaryOps: Set[String] = Set("-", "*", "/", "%", "&", "|", "^", "<<", ">>")

  /** Does this expression always produce a number or a BigInt? */
  def isNumberOrBigInt(node: AstNode, compressor: CompressorLike): Boolean =
    node match {
      case _: AstNumber | _: AstBigInt => true
      case unary:  AstUnary  => numericUnaryOps.contains(unary.operator)
      case assign: AstAssign => // must come before AstBinary
        numberOrBigintBinaryOps.contains(assign.operator.stripSuffix("=")) ||
        (assign.operator == "=" && assign.right != null && isNumberOrBigInt(assign.right.nn, compressor))
      case binary: AstBinary =>
        if (binary.operator == "+") {
          binary.left != null && binary.right != null &&
          isNumberOrBigInt(binary.left.nn, compressor) && isNumberOrBigInt(binary.right.nn, compressor)
        } else {
          numberOrBigintBinaryOps.contains(binary.operator)
        }
      case seq: AstSequence =>
        seq.expressions.nonEmpty && isNumberOrBigInt(seq.expressions.last, compressor)
      case cond: AstConditional =>
        cond.consequent != null && cond.alternative != null &&
        isNumberOrBigInt(cond.consequent.nn, compressor) && isNumberOrBigInt(cond.alternative.nn, compressor)
      case _ => false
    }

  // -----------------------------------------------------------------------
  // 32-bit integer predicate
  // -----------------------------------------------------------------------

  /** Does this expression always produce a 32-bit integer? */
  def is32BitInteger(node: AstNode, compressor: CompressorLike): Boolean =
    node match {
      case n:      AstNumber      => n.value == (n.value.toInt: Double)
      case prefix: AstUnaryPrefix =>
        if (prefix.operator == "~") prefix.expression != null && isNumber(prefix.expression.nn, compressor)
        else if (prefix.operator == "+") prefix.expression != null && is32BitInteger(prefix.expression.nn, compressor)
        else false
      case binary: AstBinary =>
        bitwiseBinop.contains(binary.operator) &&
        ((binary.left != null && isNumber(binary.left.nn, compressor)) ||
          (binary.right != null && isNumber(binary.right.nn, compressor)))
      case _ => false
    }

  // -----------------------------------------------------------------------
  // String type predicate
  // -----------------------------------------------------------------------

  /** Does this expression always produce a string? */
  def isString(node: AstNode, compressor: CompressorLike): Boolean =
    node match {
      case _: AstString | _: AstTemplateString => true
      case prefix: AstUnaryPrefix => prefix.operator == "typeof"
      case assign: AstAssign      => // must come before AstBinary
        (assign.operator == "=" || assign.operator == "+=") &&
        assign.right != null && isString(assign.right.nn, compressor)
      case binary: AstBinary =>
        binary.operator == "+" &&
        binary.left != null && binary.right != null &&
        (isString(binary.left.nn, compressor) || isString(binary.right.nn, compressor))
      case seq: AstSequence =>
        seq.expressions.nonEmpty && isString(seq.expressions.last, compressor)
      case cond: AstConditional =>
        cond.consequent != null && cond.alternative != null &&
        isString(cond.consequent.nn, compressor) && isString(cond.alternative.nn, compressor)
      case _ => false
    }

  // -----------------------------------------------------------------------
  // Undefined / Nullish predicates
  // -----------------------------------------------------------------------

  /** Is this node explicitly undefined? */
  def isUndefined(node: AstNode, compressor: CompressorLike): Boolean =
    hasFlag(node, UNDEFINED) ||
      node.isInstanceOf[AstUndefined] ||
      (node.isInstanceOf[AstUnaryPrefix] &&
        node.asInstanceOf[AstUnaryPrefix].operator == "void" &&
        node.asInstanceOf[AstUnaryPrefix].expression != null &&
        !hasSideEffects(node.asInstanceOf[AstUnaryPrefix].expression.nn, compressor))

  /** Is this node explicitly null or undefined? */
  private def isNullOrUndefined(node: AstNode, compressor: CompressorLike): Boolean = {
    node.isInstanceOf[AstNull] || isUndefined(node, compressor)
    // TODO: check SymbolRef fixed value when scope analysis is ported
  }

  /** Is this node nullish (null, undefined, or optionally chained from null/undefined)? */
  def isNullish(node: AstNode, compressor: CompressorLike): Boolean =
    isNullOrUndefined(node, compressor) || isNullishShortcircuited(node, compressor)

  /** Is this node part of an optional chain whose base is null/undefined? */
  def isNullishShortcircuited(node: AstNode, compressor: CompressorLike): Boolean =
    node match {
      case pa: AstPropAccess =>
        pa.expression match {
          case null => false
          case expr =>
            (pa.optional && isNullOrUndefined(expr.nn, compressor)) ||
            isNullishShortcircuited(expr.nn, compressor)
        }
      case call: AstCall =>
        call.expression match {
          case null => false
          case expr =>
            (call.optional && isNullOrUndefined(expr.nn, compressor)) ||
            isNullishShortcircuited(expr.nn, compressor)
        }
      case chain: AstChain =>
        chain.expression match {
          case null => false
          case expr => isNullishShortcircuited(expr.nn, compressor)
        }
      case _ => false
    }

  // -----------------------------------------------------------------------
  // Side effects
  // -----------------------------------------------------------------------

  /** Does this expression have side effects? */
  def hasSideEffects(node: AstNode, compressor: CompressorLike): Boolean = {
    node match {
      case _: AstEmptyStatement | _: AstConstant | _: AstThis  => false
      case _: AstSymbolClassProperty | _: AstSymbolDeclaration => false
      case _: AstImportMeta      => false
      case _: AstTemplateSegment => false

      // Lambda must come before AstScope/AstBlock
      case _: AstLambda => false

      // Class must come before AstScope/AstBlock (AstClass extends AstScope extends AstBlock)
      case cls: AstClass =>
        (cls.superClass != null && hasSideEffects(cls.superClass.nn, compressor)) ||
        anyHasSideEffects(cls.properties, compressor)

      // ClassStaticBlock must come before AstScope/AstBlock
      case csb: AstClassStaticBlock =>
        anyHasSideEffects(csb.body, compressor)

      // Switch must come before AstBlock
      case sw: AstSwitch =>
        (sw.expression != null && hasSideEffects(sw.expression.nn, compressor)) ||
        anyHasSideEffects(sw.body, compressor)

      // Case must come before AstBlock (extends AstSwitchBranch extends AstBlock)
      case cas: AstCase =>
        (cas.expression != null && hasSideEffects(cas.expression.nn, compressor)) ||
        anyHasSideEffects(cas.body, compressor)

      // Call (not a subtype issue but comes before generic nodes)
      case _: AstCall =>
        // TODO: is_callee_pure / is_call_pure checks
        true

      // Try (not AstBlock but has block-like children)
      case tr: AstTry =>
        (tr.body != null && hasSideEffects(tr.body.nn, compressor)) ||
        (tr.bcatch != null && hasSideEffects(tr.bcatch.nn, compressor)) ||
        (tr.bfinally != null && hasSideEffects(tr.bfinally.nn, compressor))

      // If
      case ifn: AstIf =>
        (ifn.condition != null && hasSideEffects(ifn.condition.nn, compressor)) ||
        (ifn.body != null && hasSideEffects(ifn.body.nn, compressor)) ||
        (ifn.alternative != null && hasSideEffects(ifn.alternative.nn, compressor))

      case ls: AstLabeledStatement =>
        ls.body != null && hasSideEffects(ls.body.nn, compressor)

      case ss: AstSimpleStatement =>
        ss.body != null && hasSideEffects(ss.body.nn, compressor)

      // AstBlock catch-all (BlockStatement, Default, TryBlock, Catch, Finally, etc.)
      case block: AstBlock => anyHasSideEffects(block.body, compressor)

      // Assign must come before AstBinary
      case _: AstAssign => true

      case binary: AstBinary =>
        (binary.left != null && hasSideEffects(binary.left.nn, compressor)) ||
        (binary.right != null && hasSideEffects(binary.right.nn, compressor))

      case cond: AstConditional =>
        (cond.condition != null && hasSideEffects(cond.condition.nn, compressor)) ||
        (cond.consequent != null && hasSideEffects(cond.consequent.nn, compressor)) ||
        (cond.alternative != null && hasSideEffects(cond.alternative.nn, compressor))

      case unary: AstUnary =>
        unarySideEffects.contains(unary.operator) ||
        (unary.expression != null && hasSideEffects(unary.expression.nn, compressor))

      case ref: AstSymbolRef =>
        // Undeclared refs may throw ReferenceError
        // TODO: is_declared check when scope analysis is ported
        !purePropAccessGlobals.contains(ref.name)

      case obj: AstObject => anyHasSideEffects(obj.properties, compressor)

      case kv: AstObjectKeyVal =>
        (kv.computedKey() && (kv.key match {
          case n: AstNode => hasSideEffects(n, compressor)
          case _ => false
        })) || (kv.value != null && hasSideEffects(kv.value.nn, compressor))

      case cp: AstClassProperty =>
        (cp.computedKey() && (cp.key match {
          case n: AstNode => hasSideEffects(n, compressor)
          case _ => false
        })) || (cp.isStatic && cp.value != null && hasSideEffects(cp.value.nn, compressor))

      case cpp: AstClassPrivateProperty =>
        cpp.isStatic && cpp.value != null && hasSideEffects(cpp.value.nn, compressor)

      case cm: AstConciseMethod =>
        cm.computedKey() && (cm.key match {
          case n: AstNode => hasSideEffects(n, compressor)
          case _ => false
        })
      case og: AstObjectGetter =>
        og.computedKey() && (og.key match {
          case n: AstNode => hasSideEffects(n, compressor)
          case _ => false
        })
      case os: AstObjectSetter =>
        os.computedKey() && (os.key match {
          case n: AstNode => hasSideEffects(n, compressor)
          case _ => false
        })

      case _: AstPrivateMethod | _: AstPrivateGetter | _: AstPrivateSetter => false

      case arr: AstArray => anyHasSideEffects(arr.elements, compressor)

      case dot: AstDot =>
        if (isNullish(dot, compressor)) {
          dot.expression != null && hasSideEffects(dot.expression.nn, compressor)
        } else {
          (!dot.optional && dot.expression != null && mayThrowOnAccess(dot, compressor)) ||
          (dot.expression != null && hasSideEffects(dot.expression.nn, compressor))
        }

      case sub: AstSub =>
        if (isNullish(sub, compressor)) {
          sub.expression != null && hasSideEffects(sub.expression.nn, compressor)
        } else {
          (!sub.optional && sub.expression != null && mayThrowOnAccess(sub, compressor)) ||
          (sub.property match {
            case n: AstNode => hasSideEffects(n, compressor)
            case _ => false
          }) ||
          (sub.expression != null && hasSideEffects(sub.expression.nn, compressor))
        }

      case chain: AstChain =>
        chain.expression != null && hasSideEffects(chain.expression.nn, compressor)

      case seq: AstSequence => anyHasSideEffects(seq.expressions, compressor)

      case defs: AstDefinitions => anyHasSideEffects(defs.definitions, compressor)

      case vd: AstVarDef => vd.value != null

      case ts: AstTemplateString => anyHasSideEffects(ts.segments, compressor)

      // Default: assume side effects
      case _ => true
    }
  }

  private def anyHasSideEffects(list: ArrayBuffer[AstNode], compressor: CompressorLike): Boolean = {
    var i = list.size
    while ({ i -= 1; i >= 0 })
      if (hasSideEffects(list(i), compressor)) {
        return true // @nowarn — performance hot path
      }
    false
  }

  // -----------------------------------------------------------------------
  // May throw
  // -----------------------------------------------------------------------

  /** Might evaluating this expression throw an exception? */
  def mayThrow(node: AstNode, compressor: CompressorLike): Boolean = {
    node match {
      case _: AstConstant | _: AstEmptyStatement | _: AstSymbolDeclaration | _: AstThis | _: AstImportMeta | _: AstSymbolClassProperty => false

      case _: AstPrivateMethod | _: AstPrivateGetter | _: AstPrivateSetter => false

      // Lambda before AstScope/AstBlock
      case _: AstLambda => false

      // Class before AstScope/AstBlock
      case cls: AstClass =>
        (cls.superClass != null && mayThrow(cls.superClass.nn, compressor)) ||
        anyMayThrow(cls.properties, compressor)

      // ClassStaticBlock before AstScope/AstBlock
      case csb: AstClassStaticBlock => anyMayThrow(csb.body, compressor)

      // Switch before AstBlock
      case sw: AstSwitch =>
        (sw.expression != null && mayThrow(sw.expression.nn, compressor)) ||
        anyMayThrow(sw.body, compressor)

      // Case before AstBlock
      case cas: AstCase =>
        (cas.expression != null && mayThrow(cas.expression.nn, compressor)) ||
        anyMayThrow(cas.body, compressor)

      // Definitions before AstBlock (AstDefinitions extends AstStatement, not AstBlock,
      // but AstVar/AstLet/AstConst extend AstDefinitions which is a AstStatement)
      case defs: AstDefinitions => anyMayThrow(defs.definitions, compressor)

      // Call
      case call: AstCall =>
        if (isNullish(call, compressor)) false
        else if (anyMayThrow(call.args, compressor)) true
        else true // TODO: callee purity checks

      // Try
      case tr: AstTry =>
        if (tr.bcatch != null) mayThrow(tr.bcatch.nn, compressor)
        else
          (tr.body != null && mayThrow(tr.body.nn, compressor)) ||
          (tr.bfinally != null && mayThrow(tr.bfinally.nn, compressor))

      // If
      case ifn: AstIf =>
        (ifn.condition != null && mayThrow(ifn.condition.nn, compressor)) ||
        (ifn.body != null && mayThrow(ifn.body.nn, compressor)) ||
        (ifn.alternative != null && mayThrow(ifn.alternative.nn, compressor))

      case ls: AstLabeledStatement =>
        ls.body != null && mayThrow(ls.body.nn, compressor)

      case ss: AstSimpleStatement =>
        ss.body != null && mayThrow(ss.body.nn, compressor)

      case ret: AstReturn =>
        ret.value != null && mayThrow(ret.value.nn, compressor)

      // AstBlock catch-all (after all specific subtypes)
      case block: AstBlock => anyMayThrow(block.body, compressor)

      case arr: AstArray => anyMayThrow(arr.elements, compressor)

      // Assign before Binary
      case assign: AstAssign =>
        (assign.right != null && mayThrow(assign.right.nn, compressor)) ||
        (assign.left != null && mayThrow(assign.left.nn, compressor))

      case binary: AstBinary =>
        (binary.left != null && mayThrow(binary.left.nn, compressor)) ||
        (binary.right != null && mayThrow(binary.right.nn, compressor))

      case cond: AstConditional =>
        (cond.condition != null && mayThrow(cond.condition.nn, compressor)) ||
        (cond.consequent != null && mayThrow(cond.consequent.nn, compressor)) ||
        (cond.alternative != null && mayThrow(cond.alternative.nn, compressor))

      case obj: AstObject => anyMayThrow(obj.properties, compressor)

      case kv: AstObjectKeyVal =>
        (kv.computedKey() && (kv.key match {
          case n: AstNode => mayThrow(n, compressor)
          case _ => false
        })) || (kv.value != null && mayThrow(kv.value.nn, compressor))

      case cp: AstClassProperty =>
        (cp.computedKey() && (cp.key match {
          case n: AstNode => mayThrow(n, compressor)
          case _ => false
        })) || (cp.isStatic && cp.value != null && mayThrow(cp.value.nn, compressor))

      case cm: AstConciseMethod =>
        cm.computedKey() && (cm.key match {
          case n: AstNode => mayThrow(n, compressor)
          case _ => false
        })
      case og: AstObjectGetter =>
        og.computedKey() && (og.key match {
          case n: AstNode => mayThrow(n, compressor)
          case _ => false
        })
      case os: AstObjectSetter =>
        os.computedKey() && (os.key match {
          case n: AstNode => mayThrow(n, compressor)
          case _ => false
        })

      case seq: AstSequence => anyMayThrow(seq.expressions, compressor)

      case dot: AstDot =>
        if (isNullish(dot, compressor)) false
        else {
          (!dot.optional && dot.expression != null && mayThrowOnAccess(dot, compressor)) ||
          (dot.expression != null && mayThrow(dot.expression.nn, compressor))
        }

      case sub: AstSub =>
        if (isNullish(sub, compressor)) false
        else {
          (!sub.optional && sub.expression != null && mayThrowOnAccess(sub, compressor)) ||
          (sub.expression != null && mayThrow(sub.expression.nn, compressor)) ||
          (sub.property match {
            case n: AstNode => mayThrow(n, compressor)
            case _ => false
          })
        }

      case chain: AstChain =>
        chain.expression != null && mayThrow(chain.expression.nn, compressor)

      case ref: AstSymbolRef =>
        // TODO: is_declared check when scope analysis is ported
        !purePropAccessGlobals.contains(ref.name)

      case unary: AstUnary =>
        if (unary.operator == "typeof" && unary.expression.isInstanceOf[AstSymbolRef]) false
        else unary.expression != null && mayThrow(unary.expression.nn, compressor)

      case vd: AstVarDef =>
        vd.value != null && mayThrow(vd.value.nn, compressor)

      case _ => true
    }
  }

  private def anyMayThrow(list: ArrayBuffer[AstNode], compressor: CompressorLike): Boolean = {
    var i = list.size
    while ({ i -= 1; i >= 0 })
      if (mayThrow(list(i), compressor)) {
        return true // @nowarn — performance hot path
      }
    false
  }

  // -----------------------------------------------------------------------
  // May throw on access
  // -----------------------------------------------------------------------

  /** May accessing a property on this expression throw?
    *
    * Returns true if the expression might be null, undefined, or contain an accessor (getter/setter).
    */
  def mayThrowOnAccess(node: AstNode, compressor: CompressorLike): Boolean =
    node match {
      case _: AstNull | _: AstUndefined => true
      case _: AstConstant => false
      case _: AstArray    => false
      case _: AstClass    => false
      case _: AstFunction | _: AstArrow => false
      case _:      AstUnaryPostfix   => false
      case _:      AstObjectGetter   => true // must come before AstObjectProperty
      case _:      AstObjectProperty => false
      case prefix: AstUnaryPrefix    => prefix.operator == "void"
      case assign: AstAssign         => // must come before AstBinary
        if (assign.logical) true
        else assign.operator == "=" && assign.right != null && mayThrowOnAccess(assign.right.nn, compressor)
      case binary: AstBinary =>
        (binary.operator == "&&" || binary.operator == "||" || binary.operator == "??") &&
        ((binary.left != null && mayThrowOnAccess(binary.left.nn, compressor)) ||
          (binary.right != null && mayThrowOnAccess(binary.right.nn, compressor)))
      case cond: AstConditional =>
        (cond.consequent != null && mayThrowOnAccess(cond.consequent.nn, compressor)) ||
        (cond.alternative != null && mayThrowOnAccess(cond.alternative.nn, compressor))
      case seq: AstSequence =>
        seq.expressions.nonEmpty && mayThrowOnAccess(seq.expressions.last, compressor)
      case chain: AstChain =>
        chain.expression != null && mayThrowOnAccess(chain.expression.nn, compressor)
      case ref: AstSymbolRef =>
        if (ref.name == "arguments") false // TODO: check scope is lambda
        else if (hasFlag(ref, UNDEFINED)) true
        else false // simplified — full check needs scope analysis
      case _ =>
        // Default: check pure_getters option
        !compressor.optionBool("pure_getters")
    }

  // -----------------------------------------------------------------------
  // LHS detection
  // -----------------------------------------------------------------------

  /** Is this node the left-hand side of an assignment or mutation?
    *
    * Returns the expression being assigned to, or null if not an LHS.
    */
  def isLhs(node: AstNode, parent: AstNode): AstNode | Null =
    parent match {
      case unary: AstUnary if unarySideEffects.contains(unary.operator) =>
        unary.expression match {
          case null => null
          case expr => expr.nn
        }
      case assign: AstAssign if assign.left != null && (assign.left.nn eq node) => node
      case fi:     AstForIn if fi.init != null && (fi.init.nn eq node)          => node
      case _ => null
    }

  // -----------------------------------------------------------------------
  // Modified detection (for reduce_vars)
  // -----------------------------------------------------------------------

  /** Is this variable reference modified (reassigned or mutated) in context?
    *
    * Walks up the parent chain to determine if the reference's value is being changed.
    */
  def isModified(
    compressor: CompressorLike,
    tw:         TreeWalker,
    node:       AstNode,
    value:      AstNode | Null,
    level:      Int,
    immutable:  Boolean = false
  ): Boolean =
    boundary[Boolean] {
      val parent = tw.parent(level)
      if (parent == null) break(false)

      val lhs = isLhs(node, parent.nn)
      if (lhs != null) break(true)

      if (!immutable) {
        parent.nn match {
          case call: AstCall
              if call.expression != null && (call.expression.nn eq node)
                && !value.isInstanceOf[AstArrow]
                && !value.isInstanceOf[AstClass] =>
            // TODO: full callee purity check
            break(true)
          case _ =>
        }
      }

      parent.nn match {
        case _: AstArray =>
          break(isModified(compressor, tw, parent.nn, parent.nn, level + 1, immutable = false))
        case kv: AstObjectKeyVal if kv.value != null && (node eq kv.value.nn) =>
          val obj = tw.parent(level + 1)
          if (obj != null) break(isModified(compressor, tw, obj.nn, obj.nn, level + 2, immutable = false))
        case pa: AstPropAccess if pa.expression != null && (pa.expression.nn eq node) =>
          val prop = Common.readProperty(
            value match { case null => null; case v => v.nn },
            pa.property match {
              case s: String =>
                val sn = new AstString
                sn.value = s
                sn
              case n: AstNode => n
            }
          )
          if (!immutable) break(isModified(compressor, tw, parent.nn, prop, level + 1, immutable = false))
        case _ =>
      }

      false
    }

  // -----------------------------------------------------------------------
  // Used in expression
  // -----------------------------------------------------------------------

  /** Check if a node's value is actually used by the enclosing expression.
    *
    * For example, `void (0, 1, node, 2)` does not use `node`'s value, but `console.log(0, node)` does.
    */
  def isUsedInExpression(tw: TreeWalker): Boolean =
    boundary[Boolean] {
      var p    = -1
      var cont = true
      while (cont) {
        val node   = tw.parent(p)
        val parent = tw.parent(p + 1)
        if (node == null || parent == null) {
          cont = false
        } else {
          parent.nn match {
            case seq: AstSequence =>
              val idx = seq.expressions.indexOf(node.nn)
              if (idx != seq.expressions.size - 1) {
                // Not the tail — value is unused (unless it's a this-binding sequence)
                break(false)
              }
              // Is the tail, continue checking parent
              p += 1

            case unary: AstUnary =>
              val op = unary.operator
              if (op == "void") break(false)
              else if (op == "typeof" || op == "+" || op == "-" || op == "!" || op == "~") {
                p += 1
              } else {
                cont = false
              }

            case _: AstSimpleStatement | _: AstLabeledStatement =>
              break(false)

            case _: AstScope =>
              break(false)

            case _ =>
              cont = false
          }
        }
      }
      true
    }

  // -----------------------------------------------------------------------
  // Aborts (statement aborts flow)
  // -----------------------------------------------------------------------

  /** Does this statement abort (return/throw/break/continue)? */
  def aborts(thing: AstNode | Null): AstNode | Null =
    if (thing == null) null
    else
      thing.nn match {
        case _:     AstJump           => thing
        case block: AstBlockStatement =>
          var i = 0
          while (i < block.body.size) {
            val a = aborts(block.body(i))
            if (a != null) {
              return a // @nowarn — early exit in loop
            }
            i += 1
          }
          null
        case _ => null
      }
}
