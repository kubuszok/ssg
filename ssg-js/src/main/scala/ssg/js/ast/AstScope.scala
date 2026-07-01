/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scope and function AST nodes.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*
 *   Convention: Mutable scope analysis fields (variables, enclosed, etc.)
 *   Idiom: mutable.Map for variables/globals, ArrayBuffer for enclosed
 */
package ssg
package js
package ast

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// ---------------------------------------------------------------------------
// Scope
// ---------------------------------------------------------------------------

/** Base class for all statements introducing a lexical scope. */
trait AstScope extends AstBlock {

  /** Map of name -> SymbolDef for all variables/functions defined in this scope. */
  var variables:   mutable.Map[String, Any] = mutable.Map.empty
  var usesWith:    Boolean                  = false
  var usesEval:    Boolean                  = false
  var parentScope: AstScope | Null          = null

  /** List of all symbol definitions accessed from this scope or subscopes. */
  var enclosed: ArrayBuffer[Any] = ArrayBuffer.empty

  /** Current index for mangling variables (used internally by the mangler). */
  var cname: Int = -1

  def pinned: Boolean = usesEval || usesWith

  def isBlockScope: Boolean = false

  def getDefunScope: AstScope = {
    var self: AstScope = this
    while (self.isBlockScope)
      self.parentScope match {
        case p: AstScope => self = p
        case null => return self // @nowarn — parentScope can be null
      }
    self
  }
}

/** The toplevel scope. */
class AstToplevel extends AstNode with AstScope {

  /** Map of name -> SymbolDef for all undeclared names. */
  var globals: mutable.Map[String, Any] = mutable.Map.empty

  /** Set of mangled names already assigned (used to avoid duplicates at top level). */
  var mangledNames: mutable.Set[String] = mutable.Set.empty

  def nodeType: String = "Toplevel"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    walkBody(body, visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    body = transformList(body, tw)
}

// ---------------------------------------------------------------------------
// Lambda (functions)
// ---------------------------------------------------------------------------

/** Base class for functions. */
trait AstLambda extends AstScope {
  var name:          AstNode | Null       = null
  var argnames:      ArrayBuffer[AstNode] = ArrayBuffer.empty
  var usesArguments: Boolean              = false
  var isGenerator:   Boolean              = false
  var isAsync:       Boolean              = false

  /** Check if this is a braceless arrow function. */
  def isBraceless: Boolean =
    body.nonEmpty && body(0).isInstanceOf[AstReturn] &&
      body(0).asInstanceOf[AstReturn].value != null

  /** Count of named parameters (excluding defaults and expansion). */
  def lengthProperty: Int = {
    var length = 0
    var i      = 0
    while (i < argnames.size) {
      argnames(i) match {
        case _: AstSymbolFunarg | _: AstDestructuring => length += 1
        case _                                        =>
      }
      i += 1
    }
    length
  }
}

/** Helper mixin for lambda walk/childrenBackwards. */
private trait AstLambdaWalk extends AstLambda {
  override protected def walkChildren(visitor: TreeWalker): Unit = {
    val n = name
    if (n != null) n.nn.walk(visitor)
    var i = 0
    while (i < argnames.size) {
      argnames(i).walk(visitor)
      i += 1
    }
    walkBody(body, visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
    i = argnames.size
    while ({ i -= 1; i >= 0 }) push(argnames(i))
    val n = name
    if (n != null) push(n.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (name != null) name = name.nn.transform(tw)
    argnames = transformList(argnames, tw)
    body = transformList(body, tw)
  }
}

/** A setter/getter function. The `name` property is always null. */
class AstAccessor extends AstNode with AstLambda with AstLambdaWalk {
  def nodeType: String = "Accessor"
}

/** A function expression. */
class AstFunction extends AstNode with AstLambda with AstLambdaWalk {
  def nodeType: String = "Function"
}

/** An ES6 Arrow function ((a) => b). */
class AstArrow extends AstNode with AstLambda with AstLambdaWalk {
  def nodeType: String = "Arrow"
}

/** A function definition. */
class AstDefun extends AstNode with AstLambda with AstLambdaWalk {
  def nodeType: String = "Defun"
}

// ---------------------------------------------------------------------------
// Destructuring
// ---------------------------------------------------------------------------

/** A destructuring of several names. Used in destructuring assignment and function args. */
class AstDestructuring extends AstNode {
  var names:   ArrayBuffer[AstNode] = ArrayBuffer.empty
  var isArray: Boolean              = false

  def nodeType: String = "Destructuring"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    var i = 0
    while (i < names.size) {
      names(i).walk(visitor)
      i += 1
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = names.size
    while ({ i -= 1; i >= 0 }) push(names(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    names = transformList(names, tw)
}

// ---------------------------------------------------------------------------
// Expansion (spread)
// ---------------------------------------------------------------------------

/** An expandable argument, such as ...rest, a splat, or expansion in a variable declaration. */
class AstExpansion extends AstNode {
  var expression: AstNode | Null = null

  def nodeType: String = "Expansion"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}
