/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * AST node size estimation — returns approximate output character count
 * for each node type. Used by the compressor's bestOf helpers to compare
 * two AST representations and pick the shorter output.
 *
 * Original source: terser lib/size.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*, _size -> nodeSize, list_overhead -> listOverhead,
 *     key_size -> keySize, static_size -> staticSize, lambda_modifiers -> lambdaModifiers
 *   Convention: Object with pattern-matching dispatch instead of prototype methods
 *   Idiom: TreeWalker for tree walk with parent tracking
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/size.js
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer

/** AST node size estimation.
  *
  * Estimates the approximate output character count for AST nodes. Used by the compressor to compare alternative representations and pick the shorter one.
  */
object AstSize {

  /** Estimate the output size of a node and all its children.
    *
    * Walks the tree summing `nodeSize` per node. For braceless arrow functions, the fake "return" statement's value is also measured.
    */
  def size(node: AstNode, mangleOptions: Any | Null = null): Int = {
    var total = 0
    var tw: TreeWalker = null // @nowarn -- initialized before use
    tw = new TreeWalker((current, _) => {
      total = total + nodeSize(current, tw, mangleOptions)

      // Braceless arrow functions have fake "return" statements
      current match {
        case arrow: AstArrow if arrow.isBraceless =>
          arrow.body(0) match {
            case ret: AstReturn if ret.value != null =>
              total = total + nodeSize(ret.value.nn, tw, mangleOptions)
            case _ =>
          }
          true // skip children already handled
        case _ =>
          null // continue walking
      }
    })
    node.walk(tw)
    total
  }

  /** Count commas/semicolons necessary to show a list of expressions/statements. */
  private def listOverhead(array: ArrayBuffer[?]): Int =
    if (array.nonEmpty) array.size - 1 else 0

  /** Size contribution of `*` and `async` modifiers on a lambda. */
  private def lambdaModifiers(isGenerator: Boolean, isAsync: Boolean): Int =
    (if (isGenerator) 1 else 0) + (if (isAsync) 6 else 0)

  /** Size contribution of the `static` keyword on a property. */
  private def staticSize(isStatic: Boolean): Int =
    if (isStatic) 7 else 0

  /** Size of a property key when it is a string. */
  private def keySize(key: String | AstNode): Int =
    key match {
      case s: String => s.length
      case _ => 0
    }

  /** Check if a node is first in statement position (simplified version for size estimation). */
  private def isFirstInStatement(tw: TreeWalker): Boolean = {
    val p = tw.parent()
    p != null && p.nn.isInstanceOf[AstSimpleStatement]
  }

  /** Estimate the size of a single node (not including children).
    *
    * Returns the approximate number of characters this node contributes to the output, excluding its child subtrees (which are measured separately by walk).
    */
  private def nodeSize(node: AstNode, tw: TreeWalker, mangleOptions: Any | Null): Int = {
    node match {
      // --- Statements ---
      case _: AstDebugger         => 8
      case d: AstDirective        => 2 + d.value.length
      case _: AstEmptyStatement   => 1
      case _: AstLabeledStatement => 2 // "x:"
      case _: AstDo               => 9
      case _: AstWhile            => 7
      case _: AstFor              => 8
      case _: AstForIn            => 8 // AstForOf also inherits this size
      case _: AstWith             => 6
      case _: AstIf               => 4
      case _: AstTry              => 3

      // --- Blocks ---
      case tl:    AstToplevel       => listOverhead(tl.body)
      case block: AstBlockStatement => 2 + listOverhead(block.body)

      // --- Switch ---
      case sw: AstSwitch  => 8 + listOverhead(sw.body)
      case c:  AstCase    => 5 + listOverhead(c.body)
      case d:  AstDefault => 8 + listOverhead(d.body)

      // --- Catch/Finally ---
      case c: AstCatch =>
        var s = 7 + listOverhead(c.body)
        if (c.argname != null) s += 2
        s
      case f: AstFinally => 7 + listOverhead(f.body)

      // --- Jumps ---
      case r: AstReturn   => if (r.value != null) 7 else 6
      case _: AstThrow    => 6
      case b: AstBreak    => if (b.label != null) 6 else 5
      case c: AstContinue => if (c.label != null) 9 else 8

      // --- Definitions ---
      case v: AstVar   => 4 + listOverhead(v.definitions)
      case l: AstLet   => 4 + listOverhead(l.definitions)
      case c: AstConst => 6 + listOverhead(c.definitions)
      case u: AstUsing =>
        val awaitSize = if (u.isAwait) 6 else 0
        awaitSize + 6 + listOverhead(u.definitions)
      case vd: AstVarDef   => if (vd.value != null) 1 else 0
      case ud: AstUsingDef => if (ud.value != null) 1 else 0

      // --- Import/Export ---
      case nm: AstNameMapping => if (nm.name != null) 4 else 0
      case im: AstImport      =>
        var s = 6
        if (im.importedName != null) s += 1
        if (im.importedName != null || im.importedNames != null) s += 5
        if (im.importedNames != null) s += 2 + listOverhead(im.importedNames.nn)
        s
      case _:  AstImportMeta => 11
      case ex: AstExport     =>
        var s = 7 + (if (ex.isDefault) 8 else 0)
        if (ex.exportedValue != null) s += nodeSize(ex.exportedValue.nn, tw, mangleOptions)
        if (ex.exportedNames != null) s += 2 + listOverhead(ex.exportedNames.nn)
        if (ex.moduleName != null) s += 5
        s

      // --- Functions ---
      case acc: AstAccessor =>
        lambdaModifiers(acc.isGenerator, acc.isAsync) + 4 +
          listOverhead(acc.argnames) + listOverhead(acc.body)
      case fn: AstFunction =>
        val first = if (isFirstInStatement(tw)) 2 else 0
        first + lambdaModifiers(fn.isGenerator, fn.isAsync) + 12 +
          listOverhead(fn.argnames) + listOverhead(fn.body)
      case d: AstDefun =>
        lambdaModifiers(d.isGenerator, d.isAsync) + 13 +
          listOverhead(d.argnames) + listOverhead(d.body)
      case arrow: AstArrow =>
        var argsAndArrow = 2 + listOverhead(arrow.argnames)
        // Single symbol arg doesn't need parens
        val singleSymbolArg = arrow.argnames.size == 1 && arrow.argnames(0).isInstanceOf[AstSymbol]
        if (!singleSymbolArg) argsAndArrow += 2
        val bodyOverhead = if (arrow.isBraceless) 0 else listOverhead(arrow.body) + 2
        lambdaModifiers(arrow.isGenerator, arrow.isAsync) + argsAndArrow + bodyOverhead

      // --- Expressions ---
      case call: AstCall if !call.isInstanceOf[AstNew] =>
        (if (call.optional) 4 else 2) + listOverhead(call.args)
      case nw: AstNew =>
        6 + listOverhead(nw.args)
      case seq: AstSequence =>
        listOverhead(seq.expressions)
      case dot: AstDot =>
        val prop = dot.property match {
          case s: String => s
          case _ => ""
        }
        if (dot.optional) prop.length + 2 else prop.length + 1
      case dh: AstDotHash =>
        val prop = dh.property match {
          case s: String => s
          case _ => ""
        }
        if (dh.optional) prop.length + 3 else prop.length + 2
      case sub: AstSub =>
        if (sub.optional) 4 else 2
      case u: AstUnaryPrefix =>
        if (u.operator == "typeof") 7
        else if (u.operator == "void") 5
        else u.operator.length
      case _:   AstUnaryPostfix => 2 // ++ or --
      case bin: AstBinary       =>
        if (bin.operator == "in") 4
        else {
          var s = bin.operator.length
          // 1+ +a needs space between the operators
          if (
            (bin.operator == "+" || bin.operator == "-") &&
            bin.right != null && bin.right.nn.isInstanceOf[AstUnary]
          ) {
            val rightUnary = bin.right.nn.asInstanceOf[AstUnary]
            if (rightUnary.operator == bin.operator) s += 1
          }
          s
        }
      case _: AstConditional => 3

      // --- Spread ---
      case _: AstExpansion => 3

      // --- Destructuring ---
      case _: AstDestructuring => 2

      // --- Template strings ---
      case ts: AstTemplateString =>
        2 + (Math.floor(ts.segments.size / 2.0) * 3).toInt
      case seg: AstTemplateSegment =>
        seg.value.length

      // --- Array/Object literals ---
      case arr: AstArray =>
        2 + listOverhead(arr.elements)
      case obj: AstObject =>
        var base = 2
        if (isFirstInStatement(tw)) base += 2
        base + listOverhead(obj.properties)

      // --- Object properties ---
      case kv: AstObjectKeyVal =>
        keySize(kv.key) + 1
      case og: AstObjectGetter =>
        5 + staticSize(og.isStatic) + keySize(og.key)
      case os: AstObjectSetter =>
        5 + staticSize(os.isStatic) + keySize(os.key)
      case cm: AstConciseMethod =>
        staticSize(cm.isStatic) + keySize(cm.key)
      case pm: AstPrivateMethod =>
        staticSize(pm.isStatic) + keySize(pm.key) + 1
      case pg: AstPrivateGetter =>
        staticSize(pg.isStatic) + keySize(pg.key) + 4
      case ps: AstPrivateSetter =>
        staticSize(ps.isStatic) + keySize(ps.key) + 4
      case _: AstPrivateIn => 5 // "#" and " in "

      // --- Class ---
      case cls: AstClass =>
        (if (cls.name != null) 8 else 7) +
          (if (cls.superClass != null) 8 else 0)
      case csb: AstClassStaticBlock =>
        8 + listOverhead(csb.body)
      case cp: AstClassProperty =>
        staticSize(cp.isStatic) +
          (cp.key match {
            case s: String => s.length + 2
            case _ => 0
          }) +
          (if (cp.value != null) 1 else 0)
      case cpp: AstClassPrivateProperty =>
        staticSize(cpp.isStatic) +
          (cpp.key match {
            case s: String  => s.length + 2
            case _: AstNode => 0
          }) +
          (if (cpp.value != null) 1 else 0) + 1

      // --- Special symbols (must come before AstSymbol catch-all) ---
      case _: AstNewTarget => 10
      case _: AstSuper     => 5
      case _: AstThis      => 4

      // --- Symbols ---
      case sr: AstSymbolRef =>
        if (sr.name == "arguments") 9
        else symbolSize(sr, mangleOptions)
      case sd: AstSymbolDeclaration =>
        if (sd.name == "arguments") 9
        else symbolSize(sd, mangleOptions)
      case scp: AstSymbolClassProperty =>
        // TODO: take propmangle into account
        scp.name.length
      case sef: AstSymbolExportForeign =>
        sef.name.length
      case sif: AstSymbolImportForeign =>
        sif.name.length
      case sym: AstSymbol =>
        symbolSize(sym, mangleOptions)

      // --- Constants ---
      case s:  AstString    => s.value.length + 2
      case n:  AstNumber    => numberSize(n.value)
      case bi: AstBigInt    => bi.value.length
      case re: AstRegExp    => re.value.source.length + re.value.flags.length + 2
      case _:  AstNull      => 4
      case _:  AstNaN       => 3
      case _:  AstUndefined => 6 // "void 0"
      case _:  AstHole      => 0 // comma is taken into account by listOverhead
      case _:  AstInfinity  => 8
      case _:  AstTrue      => 4
      case _:  AstFalse     => 5
      case _:  AstAwait     => 6
      case _:  AstYield     => 6

      // Default: no size contribution
      case _ => 0
    }
  }

  /** Estimate symbol name size (1 if mangleable, name.length otherwise). */
  private def symbolSize(sym: AstSymbol, mangleOptions: Any | Null): Int =
    // When mangle options are set and the symbol has a mangleable definition,
    // assume the mangled name will be 1 character
    if (mangleOptions != null && sym.thedef != null) {
      // Simplified: assume mangling shortens to 1 char
      1
    } else {
      sym.name.length
    }

  /** Estimate the output size of a number literal. */
  private def numberSize(value: Double): Int =
    if (value == 0.0) 1
    else if (value > 0 && Math.floor(value) == value)
      Math.floor(Math.log10(value) + 1).toInt
    else value.toString.length
}
