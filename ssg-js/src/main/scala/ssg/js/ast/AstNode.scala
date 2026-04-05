/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Base AST node trait, tree walker, and tree transformer.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast* (e.g., AST_Node -> AstNode)
 *   Convention: Mutable flags/scope fields, trait hierarchy
 *   Idiom: TreeWalker uses ArrayBuffer stack instead of JS array
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer

/** Sentinel object to abort a tree walk. */
object WalkAbort

/** Annotation bit flags for calls, dots, etc. */
object Annotations {
  val Pure:       Int = 0x01
  val Inline:     Int = 0x02
  val NoInline:   Int = 0x04
  val Key:        Int = 0x08
  val MangleProp: Int = 0x10
}

/** Base trait for all AST nodes. */
trait AstNode {
  var start: AstToken = AstToken.Empty
  var end:   AstToken = AstToken.Empty
  var flags: Int      = 0

  /** Node type name (e.g., "Binary", "Call"). */
  def nodeType: String

  /** Walk this node and its children with a visitor. */
  def walk(visitor: TreeWalker): Unit =
    visitor._visit(this, () => walkChildren(visitor))

  /** Walk children (override in nodes with children). */
  protected def walkChildren(visitor: TreeWalker): Unit = {}

  /** Push children in reverse order for backwards iteration. */
  def childrenBackwards(push: AstNode => Unit): Unit = {}
}

/** Walks nodes in depth-first search fashion using childrenBackwards. Callback can return WalkAbort to stop iteration, or true to skip children.
  */
def walk(node: AstNode, cb: (AstNode, ArrayBuffer[AstNode]) => Any): Boolean = {
  val toVisit = ArrayBuffer[AstNode](node)
  val push: AstNode => Unit = n => toVisit.addOne(n)
  while (toVisit.nonEmpty) {
    val current = toVisit.remove(toVisit.size - 1)
    val ret     = cb(current, toVisit)
    if (ret != null && ret != false && ret != (())) {
      if (ret.asInstanceOf[AnyRef] eq WalkAbort) {
        return true
      }
      // ret is truthy but not WalkAbort: skip children
    } else {
      current.childrenBackwards(push)
    }
  }
  false
}

/** Helper to walk the body array of a block-like node. */
def walkBody(body: ArrayBuffer[AstNode], visitor: TreeWalker): Unit = {
  var i = 0
  while (i < body.size) {
    body(i).walk(visitor)
    i += 1
  }
}

/** Tree walker with ancestry stack and directive tracking. */
class TreeWalker(val visit: (AstNode, () => Unit) => Any = (_, _) => {}) {
  val stack:      ArrayBuffer[AstNode]                          = ArrayBuffer.empty
  var directives: scala.collection.mutable.Map[String, AstNode] =
    scala.collection.mutable.Map.empty

  private var _directivesStack: List[scala.collection.mutable.Map[String, AstNode]] = Nil

  def _visit(node: AstNode, descend: () => Unit): Unit = {
    push(node)
    val ret = visit(node, descend)
    if (ret == null || ret == false || ret == (())) {
      descend()
    }
    pop()
  }

  def parent(n: Int = 0): AstNode | Null = {
    val idx = stack.size - 2 - n
    if (idx >= 0) stack(idx) else null
  }

  def push(node: AstNode): Unit = {
    node match {
      case _: AstLambda =>
        _directivesStack = directives :: _directivesStack
        directives = directives.clone()
      case d: AstDirective if !directives.contains(d.value) =>
        directives(d.value) = d
      case _: AstClass =>
        _directivesStack = directives :: _directivesStack
        directives = directives.clone()
        if (!directives.contains("use strict")) {
          directives("use strict") = node
        }
      case _ =>
    }
    stack.addOne(node)
  }

  def pop(): Unit = {
    val node = stack.remove(stack.size - 1)
    node match {
      case _: AstLambda | _: AstClass =>
        directives = _directivesStack.head
        _directivesStack = _directivesStack.tail
      case _ =>
    }
  }

  def self(): AstNode = stack(stack.size - 1)

  /** Find an ancestor of the given type. */
  def findParent[T <: AstNode](using ct: scala.reflect.ClassTag[T]): T | Null = {
    var i = stack.size
    while ({ i -= 1; i >= 0 })
      stack(i) match {
        case t: T => return t // scalastyle:ignore -- interop boundary @nowarn
        case _ =>
      }
    null
  }

  def findScope(): AstScope | Null = {
    var i = stack.size
    while ({ i -= 1; i >= 0 })
      stack(i) match {
        case t: AstToplevel                      => return t // scalastyle:ignore -- interop boundary @nowarn
        case l: AstLambda                        => return l // scalastyle:ignore -- interop boundary @nowarn
        case b: AstBlock if b.blockScope != null => return b.blockScope // scalastyle:ignore -- interop boundary @nowarn
        case _ =>
      }
    null
  }

  def hasDirective(dtype: String): AstNode | Null = {
    import scala.util.boundary
    import scala.util.boundary.break
    directives.getOrElse(
      dtype,
      stack.lastOption match {
        case Some(scope: AstScope) =>
          boundary[AstNode | Null] {
            var i = 0
            while (i < scope.body.size)
              scope.body(i) match {
                case d: AstDirective if d.value == dtype => break(d)
                case _: AstDirective                     => i += 1
                case _ => break(null)
              }
            null
          }
        case _ => null
      }
    )
  }

  def loopcontrolTarget(node: AstLoopControl): AstNode | Null = {
    if (node.label != null) {
      var i = stack.size
      while ({ i -= 1; i >= 0 })
        stack(i) match {
          case ls: AstLabeledStatement if ls.label.name == node.label.nn.name =>
            return ls.body // scalastyle:ignore -- interop boundary @nowarn
          case _ =>
        }
    } else {
      var i = stack.size
      while ({ i -= 1; i >= 0 })
        stack(i) match {
          case it: AstIterationStatement                    => return it // scalastyle:ignore -- interop boundary @nowarn
          case sw: AstSwitch if node.isInstanceOf[AstBreak] => return sw // scalastyle:ignore -- interop boundary @nowarn
          case _ =>
        }
    }
    null
  }
}

/** Tree transformer that extends TreeWalker with before/after callbacks. */
class TreeTransformer(
  val before: (AstNode, () => Unit) => Any = (_, _) => {},
  val after:  (AstNode) => Any = _ => {}
) extends TreeWalker(before)
