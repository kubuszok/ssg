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
 *   Gap: Now includes isCalleePure, isCallPure, bitwiseNegate, isConstantExpression,
 *     allRefsLocal, containsThis, isRefDeclared, isRefImmutable. Fixed hasSideEffects
 *     to check isCallPure for Dot expressions, mayThrow to recurse into Lambda bodies,
 *     mayThrowOnAccess with is_strict mode and additional cases (AstObject, AstExpansion,
 *     AstDot, enhanced AstSymbolRef), aborts with SwitchBranch/DefClass/ClassStaticBlock/If/Import.
 *   Audited: 2026-04-12 (pass)
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
import ssg.js.scope.ScopeAnalysis

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

  /** Pure global functions (no side effects, no exceptions when called with valid args). */
  val globalPureFns: Set[String] = Set(
    "Boolean",
    "decodeURI",
    "decodeURIComponent",
    "Date",
    "encodeURI",
    "encodeURIComponent",
    "Error",
    "escape",
    "EvalError",
    "isFinite",
    "isNaN",
    "Number",
    "Object",
    "parseFloat",
    "parseInt",
    "RangeError",
    "ReferenceError",
    "String",
    "SyntaxError",
    "TypeError",
    "unescape",
    "URIError"
  )

  // -----------------------------------------------------------------------
  // Undeclared reference check
  // -----------------------------------------------------------------------

  /** Check if a node is a SymbolRef whose definition is undeclared. */
  def isUndeclaredRef(node: AstNode): Boolean =
    node match {
      case ref: AstSymbolRef =>
        val d = ref.definition()
        if (d == null) true // no definition means undeclared
        else d.nn.undeclared
      case _ => false
    }

  /** Global names that are always considered "declared" in unsafe mode. */
  private val globalNames: Set[String] = Set(
    "Array", "Boolean", "clearInterval", "clearTimeout", "console", "Date",
    "decodeURI", "decodeURIComponent", "encodeURI", "encodeURIComponent",
    "Error", "escape", "eval", "EvalError", "Function", "isFinite", "isNaN",
    "JSON", "Math", "Number", "parseFloat", "parseInt", "RangeError",
    "ReferenceError", "RegExp", "Object", "setInterval", "setTimeout",
    "String", "SyntaxError", "TypeError", "unescape", "URIError"
  )

  /** Check if a SymbolRef is considered declared. */
  def isRefDeclared(ref: AstSymbolRef, compressor: CompressorLike): Boolean = {
    val d = ref.definition()
    if (d != null && !d.nn.undeclared) true
    else compressor.optionBool("unsafe") && globalNames.contains(ref.name)
  }

  /** Check if a SymbolRef refers to an immutable binding (lambda name). */
  def isRefImmutable(ref: AstSymbolRef): Boolean = {
    val d = ref.definition()
    if (d == null) false
    else {
      val orig = d.nn.orig
      orig.size == 1 && orig(0).isInstanceOf[AstSymbolLambda]
    }
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
    if (node.isInstanceOf[AstNull] || isUndefined(node, compressor)) return true // @nowarn
    // Check SymbolRef fixed value
    node match {
      case ref: AstSymbolRef =>
        ref.fixedValue() match {
          case n: AstNode => isNullOrUndefined(n, compressor)
          case _          => false
        }
      case _ => false
    }
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
      case call: AstCall =>
        if (!isCalleePure(call, compressor)) {
          // Check if the call expression is a pure method call
          call.expression match {
            case dot: AstDot =>
              if (!isCallPure(dot, compressor) || hasSideEffects(dot, compressor)) true
              else anyHasSideEffects(call.args, compressor)
            case _ => true
          }
        } else {
          anyHasSideEffects(call.args, compressor)
        }

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
        // Declared refs don't have side effects; undeclared may throw ReferenceError
        val d = ref.definition()
        if (d != null && !d.nn.undeclared) false
        else !purePropAccessGlobals.contains(ref.name)

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
        else if (isCalleePure(call, compressor)) false
        else if (call.expression != null && mayThrow(call.expression.nn, compressor)) true
        else {
          // If callee is a Lambda, check if the body may throw
          call.expression match {
            case lambda: AstLambda => anyMayThrow(lambda.body, compressor)
            case _                 => true
          }
        }

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
        // Declared refs don't throw; undeclared may throw ReferenceError
        val d = ref.definition()
        if (d != null && !d.nn.undeclared) false
        else !purePropAccessGlobals.contains(ref.name)

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

  /** Check if pure_getters option contains "strict". */
  private def isStrict(compressor: CompressorLike): Boolean = {
    val pg = compressor.option("pure_getters")
    pg match {
      case s: String => s.contains("strict")
      case _         => false
    }
  }

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
      case obj: AstObject =>
        if (!isStrict(compressor)) false
        else {
          // In strict mode, check if any property may throw (has getter)
          var i = obj.properties.size
          while ({ i -= 1; i >= 0 }) {
            if (dotThrow(obj.properties(i), compressor)) {
              return true // @nowarn — performance hot path
            }
          }
          false
        }
      case exp: AstExpansion =>
        exp.expression != null && dotThrow(exp.expression.nn, compressor)
      case prefix: AstUnaryPrefix => prefix.operator == "void"
      case assign: AstAssign      => // must come before AstBinary
        if (assign.logical) true
        else assign.operator == "=" && assign.right != null && dotThrow(assign.right.nn, compressor)
      case binary: AstBinary =>
        (binary.operator == "&&" || binary.operator == "||" || binary.operator == "??") &&
        ((binary.left != null && dotThrow(binary.left.nn, compressor)) ||
          (binary.right != null && dotThrow(binary.right.nn, compressor)))
      case cond: AstConditional =>
        (cond.consequent != null && dotThrow(cond.consequent.nn, compressor)) ||
        (cond.alternative != null && dotThrow(cond.alternative.nn, compressor))
      case dot: AstDot =>
        if (!isStrict(compressor)) false
        else if (dot.property == "prototype") {
          // .prototype on Function or Class doesn't throw
          dot.expression match {
            case _: AstFunction | _: AstClass => false
            case _                            => true
          }
        } else true
      case seq: AstSequence =>
        seq.expressions.nonEmpty && dotThrow(seq.expressions.last, compressor)
      case chain: AstChain =>
        chain.expression != null && dotThrow(chain.expression.nn, compressor)
      case ref: AstSymbolRef =>
        if (ref.name == "arguments" && ref.scope != null && ref.scope.nn.isInstanceOf[AstLambda]) false
        else if (hasFlag(ref, UNDEFINED)) true
        else if (!isStrict(compressor)) false
        else if (isUndeclaredRef(ref) && isRefDeclared(ref, compressor)) false
        else if (isRefImmutable(ref)) false
        else {
          // Check if the fixed value may throw on access
          ref.fixedValue() match {
            case n: AstNode => dotThrow(n, compressor)
            case _          => true // No fixed value in strict mode -> may throw
          }
        }
      case _ =>
        // Default: in strict mode, assume may throw
        isStrict(compressor)
    }

  /** Internal helper for recursive _dot_throw checks. */
  private def dotThrow(node: AstNode, compressor: CompressorLike): Boolean =
    !compressor.optionBool("pure_getters") || mayThrowOnAccess(node, compressor)

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
                && !value.isInstanceOf[AstClass]
                && !isCalleePure(call, compressor)
                && (value match {
                  case fn: AstFunction => !call.isInstanceOf[AstNew] && containsThis(fn)
                  case _               => true
                }) =>
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
                // Detect (0, x.noThis)() constructs - value IS used in 2-element this-binding sequences
                val grandparent = tw.parent(p + 2)
                if (
                  seq.expressions.size > 2
                    || seq.expressions.size == 1
                    || grandparent == null
                    || !Common.requiresSequenceToMaintainBinding(grandparent.nn, seq, seq.expressions(1))
                ) {
                  break(false)
                }
                break(true)
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
        case _: AstJump   => thing
        case _: AstImport => null
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
        case branch: AstSwitchBranch =>
          var i = 0
          while (i < branch.body.size) {
            val a = aborts(branch.body(i))
            if (a != null) {
              return a // @nowarn — early exit in loop
            }
            i += 1
          }
          null
        case defCls: AstDefClass =>
          var i = 0
          while (i < defCls.properties.size) {
            defCls.properties(i) match {
              case csb: AstClassStaticBlock =>
                val a = aborts(csb)
                if (a != null) {
                  return a // @nowarn — early exit in loop
                }
              case _ =>
            }
            i += 1
          }
          null
        case csb: AstClassStaticBlock =>
          var i = 0
          while (i < csb.body.size) {
            val a = aborts(csb.body(i))
            if (a != null) {
              return a // @nowarn — early exit in loop
            }
            i += 1
          }
          null
        case ifn: AstIf =>
          // If aborts only if BOTH body and alternative abort
          if (ifn.alternative != null && aborts(ifn.body) != null && aborts(ifn.alternative) != null) thing
          else null
        case _ => null
      }

  // -----------------------------------------------------------------------
  // Callee purity
  // -----------------------------------------------------------------------

  /** Is this call expression's callee known to be pure?
    *
    * Checks:
    * - unsafe mode: global pure functions, pure native static methods
    * - AST_New with pure_new option
    * - @__PURE__ annotation
    * - pure_funcs compressor option
    */
  def isCalleePure(call: AstCall, compressor: CompressorLike): Boolean =
    boundary[Boolean] {
      if (compressor.optionBool("unsafe")) {
        val expr = call.expression
        // Check for hasOwnProperty call with null/undefined first arg
        expr match {
          case dot: AstDot if dot.expression != null =>
            val dotExpr = dot.expression.nn
            dotExpr match {
              case ref: AstSymbolRef if ref.name == "hasOwnProperty" =>
                if (call.args.nonEmpty) {
                  val firstArg = call.args(0)
                  val evaluated = Evaluate.evaluate(firstArg, compressor)
                  evaluated match {
                    case null => break(false)
                    case ref2: AstSymbolRef =>
                      val d = ref2.definition()
                      if (d != null && d.nn.undeclared) break(false)
                    case _ =>
                  }
                } else {
                  break(false)
                }
              case _ =>
            }
          case _ =>
        }
        // Check for global pure function
        if (isUndeclaredRef(expr.nn) && globalPureFns.contains(expr.nn.asInstanceOf[AstSymbolRef].name)) {
          break(true)
        }
        // Check for pure native static method (e.g., Math.abs)
        expr match {
          case dot: AstDot if dot.expression != null && isUndeclaredRef(dot.expression.nn) =>
            val exprName = dot.expression.nn.asInstanceOf[AstSymbolRef].name
            dot.property match {
              case propName: String =>
                if (isPureNativeFn(exprName, propName)) break(true)
              case _ =>
            }
          case _ =>
        }
      }
      // AST_New with pure_new option
      if (call.isInstanceOf[AstNew] && compressor.optionBool("pure_new")) {
        break(true)
      }
      // Check @__PURE__ annotation
      if (compressor.optionBool("side_effects") && (call.flags & Annotations.Pure) != 0) {
        break(true)
      }
      // Check pure_funcs option
      !compressor.pureFuncs(call)
    }

  /** Is this dot access calling a pure native method?
    *
    * Used for method calls on native objects like Array.prototype.slice, String.prototype.charAt, etc.
    */
  def isCallPure(dot: AstDot, compressor: CompressorLike): Boolean =
    boundary[Boolean] {
      if (!compressor.optionBool("unsafe")) break(false)
      val expr = dot.expression
      if (expr == null) break(false)

      val nativeObj: String | Null = expr.nn match {
        case _: AstArray  => "Array"
        case _ if isBoolean(expr.nn) => "Boolean"
        case _ if isNumber(expr.nn, compressor) => "Number"
        case _: AstRegExp => "RegExp"
        case _ if isString(expr.nn, compressor) => "String"
        case _ if !mayThrowOnAccess(dot, compressor) => "Object"
        case _ => null
      }

      if (nativeObj == null) break(false)

      dot.property match {
        case propName: String => isPureNativeMethod(nativeObj.nn, propName)
        case _ => false
      }
    }

  // -----------------------------------------------------------------------
  // Bitwise negate
  // -----------------------------------------------------------------------

  /** Bitwise negation: ~x.
    *
    * Folds ~~x -> x when in 32-bit context, constant folds ~N.
    */
  def bitwiseNegate(node: AstNode, compressor: CompressorLike, in32BitContext: Boolean = false): AstNode = {
    def basicBitwiseNegation(exp: AstNode): AstNode = {
      val neg = new AstUnaryPrefix
      neg.operator = "~"
      neg.expression = exp
      neg.start = exp.start
      neg.end = exp.end
      neg
    }

    node match {
      case n: AstNumber =>
        val neg = ~n.value.toLong
        if (neg.toString.length > n.value.toString.length) {
          basicBitwiseNegation(n)
        } else {
          val result = new AstNumber
          result.value = neg.toDouble
          result.start = n.start
          result.end = n.end
          result
        }
      case up: AstUnaryPrefix if up.operator == "~" =>
        val useContext = if (in32BitContext) in32BitContext else compressor.in32BitContext()
        if (up.expression != null && (is32BitInteger(up.expression.nn, compressor) || useContext)) {
          up.expression.nn
        } else {
          basicBitwiseNegation(node)
        }
      case _ =>
        basicBitwiseNegation(node)
    }
  }

  // -----------------------------------------------------------------------
  // Constant expression
  // -----------------------------------------------------------------------

  /** Is this expression a constant (can be inlined without side effects)?
    *
    * Checks that all referenced variables are local to the expression's scope.
    * Returns true if safe to inline, "f" if safe only within this function, false otherwise.
    */
  def isConstantExpression(node: AstNode, scope: AstScope | Null = null): Any = {
    node match {
      case _: AstConstant => true
      case cls: AstClass =>
        // Class extends must be constant
        if (cls.superClass != null && isConstantExpression(cls.superClass.nn, scope) == false) {
          false
        } else {
          // Check all properties
          var allConst = true
          var i = 0
          while (allConst && i < cls.properties.size) {
            val prop = cls.properties(i)
            // Computed keys must be constant
            prop match {
              case op: AstObjectProperty if op.computedKey() =>
                op.key match {
                  case k: AstNode if isConstantExpression(k, scope) == false =>
                    allConst = false
                  case _ =>
                }
              case _ =>
            }
            // Static values must be constant
            prop match {
              case cp: AstClassProperty if cp.isStatic && cp.value != null =>
                if (isConstantExpression(cp.value.nn, scope) == false) {
                  allConst = false
                }
              case _: AstClassStaticBlock =>
                allConst = false
              case _ =>
            }
            i += 1
          }
          if (!allConst) false
          else allRefsLocal(cls, scope)
        }
      case lambda: AstLambda =>
        allRefsLocal(lambda, scope)
      case unary: AstUnary =>
        if (unary.expression == null) false
        else isConstantExpression(unary.expression.nn, scope)
      case binary: AstBinary =>
        if (binary.left == null || binary.right == null) false
        else {
          val leftConst = isConstantExpression(binary.left.nn, scope)
          if (leftConst == false) false
          else {
            val rightConst = isConstantExpression(binary.right.nn, scope)
            if (rightConst == false) false
            else if (leftConst == "f" || rightConst == "f") "f"
            else true
          }
        }
      case arr: AstArray =>
        var allConst: Any = true
        var i = 0
        while (allConst != false && i < arr.elements.size) {
          val elemConst = isConstantExpression(arr.elements(i), scope)
          if (elemConst == false) allConst = false
          else if (elemConst == "f") allConst = "f"
          i += 1
        }
        allConst
      case obj: AstObject =>
        var allConst: Any = true
        var i = 0
        while (allConst != false && i < obj.properties.size) {
          val propConst = isConstantExpression(obj.properties(i), scope)
          if (propConst == false) allConst = false
          else if (propConst == "f") allConst = "f"
          i += 1
        }
        allConst
      case op: AstObjectProperty =>
        // Key must not be AstNode (computed), and value must be constant
        op.key match {
          case _: AstNode => false
          case _ =>
            if (op.value == null) true
            else isConstantExpression(op.value.nn, scope)
        }
      case _ => false
    }
  }

  /** Check if all symbol references in node are local to its scope.
    *
    * Returns:
    * - true: all refs are local, safe to inline anywhere
    * - "f": refs are local to this function scope, safe to inline within same function
    * - false: has external refs, not safe to inline
    */
  private def allRefsLocal(node: AstScope, scope: AstScope | Null): Any =
    boundary[Any] {
      // If the node has been inlined, it's not safe
      if (hasFlag(node, INLINED)) break(false)

      val enclosed = node.enclosed
      val variables = node.variables

      var result: Any = true
      var aborted = false

      // Walk the node looking for SymbolRef and This
      walk(node, (current, _) => {
        if (aborted) {
          WalkAbort
        } else {
          current match {
            case ref: AstSymbolRef =>
              val d = ref.definition()
              if (d != null) {
                val sd = d.nn
                // Check if enclosed in node but not defined in node's variables
                var isEnclosed = false
                var ei = 0
                while (!isEnclosed && ei < enclosed.size) {
                  if (enclosed(ei).asInstanceOf[AnyRef] eq sd.asInstanceOf[AnyRef]) {
                    isEnclosed = true
                  }
                  ei += 1
                }
                if (isEnclosed && !variables.contains(sd.name)) {
                  if (scope != null) {
                    val scopeDef = ScopeAnalysis.findVariable(scope.nn, ref.name)
                    // If undeclared: scope_def must also be null
                    // Otherwise: scope_def must be the same definition
                    if (sd.undeclared) {
                      if (scopeDef == null) {
                        result = "f"
                        true // skip children
                      } else {
                        result = false
                        aborted = true
                        WalkAbort
                      }
                    } else if (scopeDef != null && (scopeDef.nn.asInstanceOf[AnyRef] eq sd.asInstanceOf[AnyRef])) {
                      result = "f"
                      true // skip children
                    } else {
                      result = false
                      aborted = true
                      WalkAbort
                    }
                  } else {
                    result = false
                    aborted = true
                    WalkAbort
                  }
                } else {
                  true // skip children
                }
              } else {
                true // skip children
              }
            case _: AstThis if node.isInstanceOf[AstArrow] =>
              // Arrow function captures outer `this`
              result = false
              aborted = true
              WalkAbort
            case _ => null // continue walking
          }
        }
      })
      result
    }

  // -----------------------------------------------------------------------
  // Contains this
  // -----------------------------------------------------------------------

  /** Does this node contain an AST_This reference?
    *
    * Stops at non-arrow function scopes since they have their own `this`.
    */
  def containsThis(node: AstNode): Boolean =
    walk(node, (current, _) => {
      current match {
        case _: AstThis => WalkAbort
        case scope: AstScope if (scope.asInstanceOf[AnyRef] ne node.asInstanceOf[AnyRef]) && !scope.isInstanceOf[AstArrow] =>
          true // skip children (non-arrow scope has its own `this`)
        case _ => null // continue walking
      }
    })

  // -----------------------------------------------------------------------
  // Self-referential class
  // -----------------------------------------------------------------------

  /** Does the class reference itself by name or `this` in non-deferred parts?
    *
    * Non-deferred parts are computed property keys and static initializers.
    * If true, we cannot safely decompose the class.
    */
  def isSelfReferential(cls: AstClass): Boolean = {
    val thisId = cls.name match {
      case sym: AstSymbol =>
        val d = sym.definition()
        if (d != null) d.nn.id else -1
      case _ => -1
    }
    var found = false
    var classThis = true // track whether `this` refers to the class

    // Visit only non-deferred class parts (computed keys and static initializers)
    visitNondeferredClassParts(cls, (node, descend) => {
      if (found) true // abort
      else node match {
        case _: AstThis =>
          found = classThis
          classThis
        case ref: AstSymbolRef =>
          val d = ref.definition()
          found = d != null && d.nn.id == thisId
          found
        case lambda: AstLambda if !lambda.isInstanceOf[AstArrow] =>
          // Non-arrow function: `this` inside refers to call-time value
          val saveClassThis = classThis
          classThis = false
          descend()
          classThis = saveClassThis
          true // already descended
        case _ =>
          false // continue walking
      }
    })
    found
  }

  /** Walk non-deferred class parts (computed keys and static initializers).
    *
    * Non-deferred parts are evaluated at class definition time:
    * - Computed property keys
    * - Static property initializers
    * - Static blocks
    * - Extends clause
    */
  def visitNondeferredClassParts(cls: AstClass, visitor: (AstNode, () => Unit) => Boolean): Unit = {
    var i = 0
    while (i < cls.properties.size) {
      val prop = cls.properties(i)
      prop match {
        case op: AstObjectProperty if op.computedKey() =>
          // Computed key is non-deferred
          op.key match {
            case k: AstNode =>
              walkWithVisitor(k, visitor)
            case _ =>
          }
        case _ =>
      }
      prop match {
        case csb: AstClassStaticBlock =>
          // Static block body is non-deferred
          var j = 0
          while (j < csb.body.size) {
            walkWithVisitor(csb.body(j), visitor)
            j += 1
          }
        case cp: AstClassProperty if cp.isStatic && cp.value != null =>
          // Static property initializer is non-deferred
          walkWithVisitor(cp.value.nn, visitor)
        case cpp: AstClassPrivateProperty if cpp.isStatic && cpp.value != null =>
          // Static private property initializer is non-deferred
          walkWithVisitor(cpp.value.nn, visitor)
        case _ =>
      }
      i += 1
    }
    // Also check extends clause
    if (cls.superClass != null) {
      walkWithVisitor(cls.superClass.nn, visitor)
    }
  }

  /** Walk node tree, calling visitor for each node. */
  private def walkWithVisitor(node: AstNode, visitor: (AstNode, () => Unit) => Boolean): Unit = {
    val tw = new TreeWalker((current, descend) => {
      if (visitor(current, descend)) true
      else {
        descend()
        true
      }
    })
    node.walk(tw)
  }

  // -----------------------------------------------------------------------
  // Negate
  // -----------------------------------------------------------------------

  /** Negate an expression for drop_side_effect_free optimization.
    *
    * Used to convert IIFE in statement position to negated form
    * to avoid needing parentheses.
    */
  def negate(node: AstNode, compressor: CompressorLike, firstInStatement: Boolean = false): AstNode = {
    val neg = new AstUnaryPrefix
    neg.start = node.start
    neg.end = node.end
    neg.operator = "!"
    neg.expression = node
    neg
  }
}
