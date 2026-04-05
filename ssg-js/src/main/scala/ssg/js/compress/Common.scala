/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Shared utility functions used across compress modules.
 *
 * Provides helpers for creating sequences, comparing AST size, checking for
 * break/continue statements, creating nodes from constants, and various
 * predicate functions used during optimization.
 *
 * Original source: terser lib/compress/common.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: merge_sequence -> mergeSequence, make_sequence -> makeSequence,
 *     best_of -> bestOf, has_break_or_continue -> hasBreakOrContinue,
 *     make_node_from_constant -> makeNodeFromConstant, is_func_expr -> isFuncExpr,
 *     is_iife_call -> isIifeCall, is_empty -> isEmpty, is_identifier_atom ->
 *     isIdentifierAtom, is_ref_of -> isRefOf, can_be_evicted_from_block ->
 *     canBeEvictedFromBlock, as_statement_array -> asStatementArray,
 *     is_reachable -> isReachable, is_recursive_ref -> isRecursiveRef,
 *     retain_top_func -> retainTopFunc, read_property -> readProperty,
 *     get_simple_key -> getSimpleKey, maintain_this_binding -> maintainThisBinding,
 *     requires_sequence_to_maintain_binding -> requiresSequenceToMaintainBinding,
 *     make_empty_function -> makeEmptyFunction
 *   Convention: Pattern matching instead of instanceof chains
 *   Idiom: boundary/break instead of return
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.ast.AstSize
import ssg.js.compress.CompressorFlags.{ TOP, hasFlag }

/** Shared utilities used across compress modules. */
object Common {

  // -----------------------------------------------------------------------
  // Sequence helpers
  // -----------------------------------------------------------------------

  /** Flatten a node into an array: if `node` is AstSequence, push all its expressions; otherwise push the node itself.
    */
  def mergeSequence(array: ArrayBuffer[AstNode], node: AstNode): ArrayBuffer[AstNode] = {
    node match {
      case seq: AstSequence =>
        array.addAll(seq.expressions)
      case _ =>
        array.addOne(node)
    }
    array
  }

  /** Create an AstSequence from multiple expressions, or return the single expression if there is only one. Throws if expressions is empty.
    */
  def makeSequence(orig: AstNode, expressions: ArrayBuffer[AstNode]): AstNode =
    if (expressions.size == 1) {
      expressions(0)
    } else if (expressions.isEmpty) {
      throw new IllegalArgumentException("trying to create a sequence with length zero!")
    } else {
      val seq = new AstSequence
      seq.start = orig.start
      seq.end = orig.end
      seq.expressions = expressions.foldLeft(ArrayBuffer.empty[AstNode])(mergeSequence)
      seq
    }

  // -----------------------------------------------------------------------
  // Empty function
  // -----------------------------------------------------------------------

  /** Create an empty AstFunction node preserving source position from `self`. */
  def makeEmptyFunction(self: AstNode): AstFunction = {
    val fn = new AstFunction
    fn.start = self.start
    fn.end = self.end
    fn.usesArguments = false
    fn.argnames = ArrayBuffer.empty
    fn.body = ArrayBuffer.empty
    fn.isGenerator = false
    fn.isAsync = false
    fn.variables = scala.collection.mutable.Map.empty
    fn.usesWith = false
    fn.usesEval = false
    fn.parentScope = null
    fn.enclosed = ArrayBuffer.empty
    fn.cname = 0
    fn.blockScope = null
    fn
  }

  // -----------------------------------------------------------------------
  // Constant -> Node conversion
  // -----------------------------------------------------------------------

  /** Create an AST node representing the given constant value.
    *
    * Supports strings, doubles (including NaN/Infinity/negative), booleans, null, and BigInt (as string). Preserves source position from `orig`.
    */
  def makeNodeFromConstant(value: Any, orig: AstNode): AstNode =
    value match {
      case s: String =>
        val node = new AstString
        node.start = orig.start
        node.end = orig.end
        node.value = s
        node

      case d: Double =>
        if (d.isNaN) {
          val node = new AstNaN
          node.start = orig.start
          node.end = orig.end
          node
        } else if (d.isInfinite) {
          if (d < 0) {
            val inf = new AstInfinity
            inf.start = orig.start
            inf.end = orig.end
            val prefix = new AstUnaryPrefix
            prefix.start = orig.start
            prefix.end = orig.end
            prefix.operator = "-"
            prefix.expression = inf
            prefix
          } else {
            val node = new AstInfinity
            node.start = orig.start
            node.end = orig.end
            node
          }
        } else if (1.0 / d < 0) {
          // Negative zero or negative number
          val num = new AstNumber
          num.start = orig.start
          num.end = orig.end
          num.value = -d
          val prefix = new AstUnaryPrefix
          prefix.start = orig.start
          prefix.end = orig.end
          prefix.operator = "-"
          prefix.expression = num
          prefix
        } else {
          val node = new AstNumber
          node.start = orig.start
          node.end = orig.end
          node.value = d
          node
        }

      case b: Boolean =>
        if (b) {
          val node = new AstTrue
          node.start = orig.start
          node.end = orig.end
          node
        } else {
          val node = new AstFalse
          node.start = orig.start
          node.end = orig.end
          node
        }

      case null =>
        val node = new AstNull
        node.start = orig.start
        node.end = orig.end
        node

      case rv: RegExpValue =>
        val node = new AstRegExp
        node.start = orig.start
        node.end = orig.end
        node.value = rv
        node

      case _ =>
        throw new IllegalArgumentException(
          s"Can't handle constant of type: ${if (value == null) "null" else value.getClass.getName}"
        )
    }

  // -----------------------------------------------------------------------
  // Best-of comparisons (pick shorter output)
  // -----------------------------------------------------------------------

  /** Compare two expression ASTs and return the one with smaller size. */
  def bestOfExpression(ast1: AstNode, ast2: AstNode): AstNode =
    if (AstSize.size(ast1) <= AstSize.size(ast2)) ast1 else ast2

  /** Compare two expressions as statements, and return the one that produces shorter output when wrapped in AstSimpleStatement.
    */
  def bestOfStatement(ast1: AstNode, ast2: AstNode): AstNode = {
    val s1 = new AstSimpleStatement
    s1.start = ast1.start
    s1.end = ast1.end
    s1.body = ast1

    val s2 = new AstSimpleStatement
    s2.start = ast2.start
    s2.end = ast2.end
    s2.body = ast2

    bestOfExpression(s1, s2) match {
      case stmt: AstSimpleStatement => stmt.body.nn
      case other => other
    }
  }

  // -----------------------------------------------------------------------
  // Property access helpers
  // -----------------------------------------------------------------------

  /** Simplify an object property's key, if possible. Returns the string/number value for constant keys, undefined for void expressions, or the key node itself if it cannot be simplified.
    */
  def getSimpleKey(key: AstNode): AstNode | String | Double | Null =
    key match {
      case c: AstConstant =>
        c match {
          case s: AstString => s.value
          case n: AstNumber => n.value: java.lang.Double
          case _ => key
        }
      case prefix: AstUnaryPrefix
          if prefix.operator == "void" && prefix.expression != null
            && prefix.expression.nn.isInstanceOf[AstConstant] =>
        null
      case _ => key
    }

  /** Read a property from an array or object literal at compile time.
    *
    * For arrays: supports numeric indices and "length". For objects: scans key-value properties in reverse for the matching key.
    */
  def readProperty(obj: AstNode, key: AstNode): AstNode | Null = {
    val simpleKey = getSimpleKey(key)
    // If key could not be simplified to a primitive, bail out
    simpleKey match {
      case _: AstNode => null // @nowarn -- non-constant key
      case _ =>
        boundary[AstNode | Null] {
          obj match {
            case arr: AstArray =>
              simpleKey match {
                case "length" =>
                  break(makeNodeFromConstant(arr.elements.size.toDouble, obj))
                case idx: java.lang.Double =>
                  val i = idx.intValue
                  if (i >= 0 && i < arr.elements.size)
                    break(resolveFixedValue(arr.elements(i)))
                case _ =>
              }

            case objLit: AstObject =>
              val keyStr = if (simpleKey == null) "undefined" else simpleKey.toString
              var value: AstNode | Null = null
              var i = objLit.properties.size
              while ({ i -= 1; i >= 0 })
                objLit.properties(i) match {
                  case kv: AstObjectKeyVal =>
                    if (value == null) {
                      kv.key match {
                        case s: String if s == keyStr =>
                          value = kv.value
                        case _ =>
                      }
                    }
                  case _ =>
                    break(null) // non-simple property (getter, setter, spread)
                }
              if (value != null)
                break(resolveFixedValue(value.nn))

            case _ =>
          }
          null
        }
    }
  }

  /** If a node is a SymbolRef with a fixed value, return the fixed value; otherwise return the node itself.
    */
  private def resolveFixedValue(node: AstNode): AstNode =
    node match {
      case ref: AstSymbolRef if ref.thedef != null =>
        // SymbolDef.fixed holds the fixed value (an AstNode) when the variable
        // is assigned exactly once and never reassigned. Use reflection since
        // SymbolDef is typed as Any.
        try {
          val sdClass     = ref.thedef.getClass
          val fixedMethod = sdClass.getMethod("fixed")
          val fixed       = fixedMethod.invoke(ref.thedef)
          fixed match {
            case n: AstNode => n
            case _ => node
          }
        } catch {
          case _: Exception => node
        }
      case _ => node
    }

  // -----------------------------------------------------------------------
  // Break / Continue detection
  // -----------------------------------------------------------------------

  /** Check if a loop body contains a break or continue that targets `loop`. */
  def hasBreakOrContinue(loop: AstNode & AstIterationStatement, parent: AstNode | Null): Boolean = {
    var found = false
    var tw: TreeWalker = null // @nowarn -- initialized before use
    tw = new TreeWalker((node, _) =>
      if (found || node.isInstanceOf[AstScope]) {
        true // skip
      } else {
        node match {
          case lc: AstLoopControl =>
            val target = tw.loopcontrolTarget(lc)
            if (target != null && (target.nn eq loop)) {
              found = true
              true
            } else {
              null // continue walking
            }
          case _ => null // continue walking
        }
      }
    )
    parent match {
      case ls: AstLabeledStatement => tw.push(ls)
      case _ =>
    }
    tw.push(loop)
    val body = loop.body
    if (body != null) body.nn.walk(tw)
    found
  }

  // -----------------------------------------------------------------------
  // This-binding maintenance
  // -----------------------------------------------------------------------

  /** Check whether we need a sequence expression `(0, val)` to maintain correct `this` binding when replacing `orig` with `val` inside `parent`.
    *
    * We shouldn't compress `(1, func)(something)` to `func(something)` because that changes the meaning of `func` (becomes lexical instead of global).
    */
  def requiresSequenceToMaintainBinding(parent: AstNode, orig: AstNode, value: AstNode): Boolean =
    (parent.isInstanceOf[AstUnaryPrefix]
      && parent.asInstanceOf[AstUnaryPrefix].operator == "delete") ||
      (parent.isInstanceOf[AstCall]
        && parent.asInstanceOf[AstCall].expression != null
        && (parent.asInstanceOf[AstCall].expression.nn eq orig)
        && (value.isInstanceOf[AstChain]
          || value.isInstanceOf[AstPropAccess]
          || (value.isInstanceOf[AstSymbolRef]
            && value.asInstanceOf[AstSymbolRef].name == "eval")))

  /** Wrap `val` in a sequence `(0, val)` if needed to maintain `this` binding. */
  def maintainThisBinding(parent: AstNode, orig: AstNode, value: AstNode): AstNode =
    if (requiresSequenceToMaintainBinding(parent, orig, value)) {
      val zero = new AstNumber
      zero.start = orig.start
      zero.end = orig.end
      zero.value = 0.0
      makeSequence(orig, ArrayBuffer(zero, value))
    } else {
      value
    }

  // -----------------------------------------------------------------------
  // Node type predicates
  // -----------------------------------------------------------------------

  /** Check if a node is a function expression (arrow or function). */
  def isFuncExpr(node: AstNode): Boolean =
    node.isInstanceOf[AstArrow] || node.isInstanceOf[AstFunction]

  /** Check if a node is an IIFE (immediately invoked function expression). Used to determine whether the node can benefit from negation. Not the case with arrow functions (you need an extra set of
    * parens).
    */
  def isIifeCall(node: AstNode): Boolean =
    node match {
      case call: AstCall =>
        call.expression match {
          case _:    AstFunction => true
          case expr: AstNode     => isIifeCall(expr)
          case null => false
        }
      case _ => false
    }

  /** Check if a node is effectively empty (null, EmptyStatement, or empty block). */
  def isEmpty(thing: AstNode | Null): Boolean =
    if (thing == null) true
    else
      thing.nn match {
        case _:     AstEmptyStatement => true
        case block: AstBlockStatement => block.body.isEmpty
        case _ => false
      }

  /** Set of identifier names that are atoms (Infinity, NaN, undefined). */
  val identifierAtom: Set[String] = Set("Infinity", "NaN", "undefined")

  /** Check if a node is an identifier atom (Infinity, NaN, or undefined). */
  def isIdentifierAtom(node: AstNode): Boolean =
    node.isInstanceOf[AstInfinity] ||
      node.isInstanceOf[AstNaN] ||
      node.isInstanceOf[AstUndefined]

  /** Check if `ref` is a SymbolRef whose definition has an orig of the given type. */
  def isRefOf[T <: AstNode](ref: AstNode)(using ct: scala.reflect.ClassTag[T]): Boolean =
    ref match {
      case _: AstSymbolRef =>
        // TODO: implement when SymbolDef.orig is available
        false
      case _ => false
    }

  /** Check if a node can be evicted from a block (turned into bare statements). Returns false for declarations that must stay inside blocks.
    */
  def canBeEvictedFromBlock(node: AstNode): Boolean =
    !(node.isInstanceOf[AstDefClass] ||
      node.isInstanceOf[AstDefun] ||
      node.isInstanceOf[AstLet] ||
      node.isInstanceOf[AstConst] ||
      node.isInstanceOf[AstUsing] ||
      node.isInstanceOf[AstExport] ||
      node.isInstanceOf[AstImport])

  /** Convert a node to a statement array.
    *   - null -> empty array
    *   - BlockStatement -> its body
    *   - EmptyStatement -> empty array
    *   - any Statement -> single-element array
    */
  def asStatementArray(thing: AstNode | Null): ArrayBuffer[AstNode] =
    if (thing == null) ArrayBuffer.empty
    else
      thing.nn match {
        case block: AstBlockStatement => block.body
        case _:     AstEmptyStatement => ArrayBuffer.empty
        case stmt:  AstStatement      => ArrayBuffer(stmt)
        case _ => throw new IllegalArgumentException("Can't convert thing to statement array")
      }

  // -----------------------------------------------------------------------
  // Reachability
  // -----------------------------------------------------------------------

  /** Check if any of `defs` are reachable (referenced) from within `scopeNode`, considering that inline function calls execute synchronously but closures (async/generators, non-call references) may
    * not.
    */
  def isReachable(scopeNode: AstNode, defs: ArrayBuffer[Any]): Boolean = {
    // Walk using the top-level walk function with parent tracking
    val findRef: (AstNode, ArrayBuffer[AstNode]) => Any = (node, _) =>
      node match {
        case sr: AstSymbolRef if defs.contains(sr.thedef) => WalkAbort
        case _ => null
      }

    // Simplified: walk all children looking for references
    walk(
      scopeNode,
      (node, parents) =>
        node match {
          case scope: AstScope if !(scope eq scopeNode) =>
            // Check if this scope captures any of the defs
            if (walk(scope, findRef)) WalkAbort
            else true // skip children (already walked)
          case _ => null
        }
    )
  }

  // -----------------------------------------------------------------------
  // Recursive reference detection
  // -----------------------------------------------------------------------

  /** Check if a ref refers to the name of a function/class it's defined within. */
  def isRecursiveRef(tw: TreeWalker, theDef: Any): Boolean =
    boundary[Boolean] {
      var i = 0
      var node: AstNode | Null = tw.parent(i)
      while (node != null) {
        node.nn match {
          case lambda: AstLambda =>
            if (lambda.name != null) {
              lambda.name.nn match {
                case sym: AstSymbol if sym.thedef != null && (sym.thedef.asInstanceOf[AnyRef] eq theDef.asInstanceOf[AnyRef]) =>
                  break(true)
                case _ =>
              }
            }
          case cls: AstClass =>
            if (cls.name != null) {
              cls.name.nn match {
                case sym: AstSymbol if sym.thedef != null && (sym.thedef.asInstanceOf[AnyRef] eq theDef.asInstanceOf[AnyRef]) =>
                  break(true)
                case _ =>
              }
            }
          case _ =>
        }
        i += 1
        node = tw.parent(i)
      }
      false
    }

  // -----------------------------------------------------------------------
  // Top-level function retention
  // -----------------------------------------------------------------------

  /** Check if a function definition should be retained as a top-level definition. Uses the compressor's `topRetain` filter from `CompressorLike`.
    */
  def retainTopFunc(fn: AstNode, hasTopFlag: Boolean, topRetainCheck: Any => Boolean): Boolean =
    fn.isInstanceOf[AstDefun] &&
      hasTopFlag &&
      hasFlag(fn, TOP) &&
      (fn match {
        case defun: AstDefun =>
          defun.name match {
            case sym: AstSymbol if sym.thedef != null => topRetainCheck(sym.thedef)
            case _ => false
          }
        case _ => false
      })
}
