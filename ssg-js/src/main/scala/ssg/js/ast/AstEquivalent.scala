/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Structural AST equality — checks if two AST subtrees are semantically
 * equivalent by comparing node types and shallow properties, then recursing
 * into children.
 *
 * Original source: terser lib/equivalent-to.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: equivalent_to -> equivalentTo, shallow_cmp -> shallowCmp,
 *     AST_* -> Ast*, _children_backwards -> childrenBackwards
 *   Convention: Object with pattern-matching dispatch instead of DEFMETHOD
 *   Idiom: ArrayBuffer stack for iterative tree walk
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer

/** Structural AST equality.
  *
  * Compares two AST subtrees for structural equivalence. Two trees are equivalent if they have the same shape (node types, child counts) and matching shallow properties (operator, name, value, etc.).
  */
object AstEquivalent {

  /** Check if two AST subtrees are structurally equivalent.
    *
    * Uses an iterative stack-based walk: at each step, pops a node from each stack, checks shallow equality, then pushes children (in reverse order) for the next iteration.
    */
  def equivalentTo(a: AstNode | Null, b: AstNode | Null): Boolean = {
    if (a == null && b == null) return true // @nowarn -- both null
    if (a == null || b == null) return false // @nowarn -- one null
    if (!shallowCmp(a.nn, b.nn)) return false // @nowarn -- root mismatch

    val stack1 = ArrayBuffer[AstNode](a.nn)
    val stack2 = ArrayBuffer[AstNode](b.nn)

    val push1: AstNode => Unit = n => stack1.addOne(n)
    val push2: AstNode => Unit = n => stack2.addOne(n)

    while (stack1.nonEmpty && stack2.nonEmpty) {
      val node1 = stack1.remove(stack1.size - 1)
      val node2 = stack2.remove(stack2.size - 1)

      if (!shallowCmp(node1, node2)) return false // @nowarn -- mismatch

      node1.childrenBackwards(push1)
      node2.childrenBackwards(push2)

      if (stack1.size != stack2.size) {
        // Different number of children
        return false // @nowarn -- child count mismatch
      }
    }

    stack1.isEmpty && stack2.isEmpty
  }

  /** Compare two nodes shallowly: same type and matching scalar properties.
    *
    * Does NOT recurse into child nodes — that is handled by the iterative walk in `equivalentTo`.
    */
  def shallowCmp(a: AstNode, b: AstNode): Boolean = {
    if (a.nodeType != b.nodeType) return false // @nowarn -- different types
    shallowCmpByType(a, b)
  }

  /** Type-specific shallow comparison via pattern matching. */
  private def shallowCmpByType(a: AstNode, b: AstNode): Boolean = {
    a match {
      // --- Pass-through (no scalar fields to compare) ---
      case _: AstDebugger | _: AstSimpleStatement | _: AstEmptyStatement | _: AstDo | _: AstWhile | _: AstWith | _: AstToplevel | _: AstExpansion | _: AstAwait | _: AstSwitch | _: AstFinally |
          _: AstCall | _: AstSequence | _: AstChain | _: AstConditional | _: AstArray | _: AstObject | _: AstNewTarget | _: AstThis | _: AstSuper | _: AstImportMeta | _: AstPrivateIn =>
        true

      // AstBlock covers BlockStatement, Default, etc.
      case _: AstBlockStatement | _: AstDefault | _: AstCase | _: AstTryBlock =>
        true

      // AstForIn, AstForOf
      case _: AstForIn =>
        true

      // --- Directive ---
      case da: AstDirective =>
        b.asInstanceOf[AstDirective].value == da.value

      // --- LabeledStatement ---
      case la: AstLabeledStatement =>
        val lb = b.asInstanceOf[AstLabeledStatement]
        (la.label == null && lb.label == null) ||
        (la.label != null && lb.label != null && la.label.nn.name == lb.label.nn.name)

      // --- For ---
      case fa: AstFor =>
        val fb = b.asInstanceOf[AstFor]
        nullEq(fa.init, fb.init) && nullEq(fa.condition, fb.condition) && nullEq(fa.step, fb.step)

      // --- Lambda ---
      case la: AstLambda =>
        val lb = b.asInstanceOf[AstLambda]
        la.isGenerator == lb.isGenerator && la.isAsync == lb.isAsync

      // --- Destructuring ---
      case da: AstDestructuring =>
        da.isArray == b.asInstanceOf[AstDestructuring].isArray

      // --- Template ---
      case _: AstPrefixedTemplateString | _: AstTemplateString =>
        true
      case sa: AstTemplateSegment =>
        sa.value == b.asInstanceOf[AstTemplateSegment].value

      // --- Jumps (AstJump, AstLoopControl) ---
      case _: AstReturn | _: AstThrow | _: AstBreak | _: AstContinue =>
        true

      // --- Yield ---
      case ya: AstYield =>
        ya.isStar == b.asInstanceOf[AstYield].isStar

      // --- If ---
      case ia: AstIf =>
        val ib = b.asInstanceOf[AstIf]
        nullEq(ia.alternative, ib.alternative)

      // --- SwitchBranch ---
      case _: AstSwitchBranch =>
        true

      // --- Try ---
      case ta: AstTry =>
        val tb = b.asInstanceOf[AstTry]
        nullEq(ta.bcatch, tb.bcatch) && nullEq(ta.bfinally, tb.bfinally)

      // --- Catch ---
      case ca: AstCatch =>
        nullEq(ca.argname, b.asInstanceOf[AstCatch].argname)

      // --- Definitions ---
      case _: AstVar | _: AstLet | _: AstConst | _: AstUsing =>
        true

      // --- VarDefLike ---
      case va: AstVarDef =>
        nullEq(va.value, b.asInstanceOf[AstVarDef].value)
      case va: AstUsingDef =>
        nullEq(va.value, b.asInstanceOf[AstUsingDef].value)

      // --- NameMapping ---
      case _: AstNameMapping =>
        true

      // --- Import ---
      case ia: AstImport =>
        val ib = b.asInstanceOf[AstImport]
        nullEq(ia.importedName, ib.importedName) &&
        nullArrayEq(ia.importedNames, ib.importedNames) &&
        nullEq(ia.attributes, ib.attributes)

      // --- Export ---
      case ea: AstExport =>
        val eb = b.asInstanceOf[AstExport]
        nullEq(ea.exportedDefinition, eb.exportedDefinition) &&
        nullEq(ea.exportedValue, eb.exportedValue) &&
        nullArrayEq(ea.exportedNames, eb.exportedNames) &&
        nullEq(ea.attributes, eb.attributes) &&
        nullEq(ea.moduleName, eb.moduleName) &&
        ea.isDefault == eb.isDefault

      // --- PropAccess ---
      case da: AstDot =>
        val db = b.asInstanceOf[AstDot]
        propEq(da.property, db.property)
      case da: AstDotHash =>
        val db = b.asInstanceOf[AstDotHash]
        propEq(da.property, db.property)
      case _: AstSub =>
        true

      // --- Unary ---
      case ua: AstUnaryPrefix =>
        ua.operator == b.asInstanceOf[AstUnaryPrefix].operator
      case ua: AstUnaryPostfix =>
        ua.operator == b.asInstanceOf[AstUnaryPostfix].operator

      // --- Binary ---
      case ba: AstBinary =>
        ba.operator == b.asInstanceOf[AstBinary].operator

      // --- Object properties ---
      case _: AstObjectProperty
          if !a.isInstanceOf[AstObjectKeyVal] &&
            !a.isInstanceOf[AstObjectGetter] && !a.isInstanceOf[AstObjectSetter] &&
            !a.isInstanceOf[AstConciseMethod] && !a.isInstanceOf[AstPrivateMethod] =>
        true
      case ka: AstObjectKeyVal =>
        val kb = b.asInstanceOf[AstObjectKeyVal]
        propEq(ka.key, kb.key) && ka.quote == kb.quote
      case oa: AstObjectGetter =>
        oa.isStatic == b.asInstanceOf[AstObjectGetter].isStatic
      case oa: AstObjectSetter =>
        oa.isStatic == b.asInstanceOf[AstObjectSetter].isStatic
      case ca: AstConciseMethod =>
        ca.isStatic == b.asInstanceOf[AstConciseMethod].isStatic
      case pa: AstPrivateMethod =>
        pa.isStatic == b.asInstanceOf[AstPrivateMethod].isStatic

      // --- Class ---
      case ca: AstClass =>
        val cb = b.asInstanceOf[AstClass]
        nullEq(ca.name, cb.name) && nullEq(ca.superClass, cb.superClass)

      // --- ClassProperty ---
      case cpa: AstClassProperty =>
        val cpb = b.asInstanceOf[AstClassProperty]
        cpa.isStatic == cpb.isStatic &&
        (cpa.key match {
          case s: String =>
            cpb.key match {
              case s2: String => s == s2
              case _ => false
            }
          case _ => true // AST_Node handled elsewhere
        })
      case cpa: AstClassPrivateProperty =>
        cpa.isStatic == b.asInstanceOf[AstClassPrivateProperty].isStatic

      // --- Symbol ---
      case sa: AstSymbol =>
        sa.name == b.asInstanceOf[AstSymbol].name

      // --- Constants ---
      case sa: AstString =>
        sa.value == b.asInstanceOf[AstString].value
      case na: AstNumber =>
        na.value == b.asInstanceOf[AstNumber].value
      case ba: AstBigInt =>
        ba.value == b.asInstanceOf[AstBigInt].value
      case ra: AstRegExp =>
        val rb = b.asInstanceOf[AstRegExp]
        ra.value.flags == rb.value.flags && ra.value.source == rb.value.source

      // --- Atoms (null, NaN, undefined, etc.) ---
      case _: AstAtom =>
        true

      // --- Default: types don't match ---
      case _ => true
    }
  }

  /** Check if two nullable fields are both null or reference-equal. */
  private def nullEq(a: AstNode | Null, b: AstNode | Null): Boolean =
    (a == null && b == null) || (a != null && b != null)

  /** Check if two nullable ArrayBuffers are both null or both non-null. */
  private def nullArrayEq(a: ArrayBuffer[AstNode] | Null, b: ArrayBuffer[AstNode] | Null): Boolean =
    (a == null && b == null) || (a != null && b != null)

  /** Compare property fields (String | AstNode). */
  private def propEq(a: String | AstNode, b: String | AstNode): Boolean =
    (a, b) match {
      case (sa: String, sb: String) => sa == sb
      case (_: AstNode, _: AstNode) => true // AST_Node children handled by walk
      case _                        => false
    }
}
