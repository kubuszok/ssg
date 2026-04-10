/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Expression AST nodes: calls, property access, operators, arrays, objects,
 * template strings, await/yield, conditional.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*
 *   Convention: Mutable var fields, annotations as Int bitfield
 *   Idiom: ArrayBuffer for args/elements/expressions/segments
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer

// ---------------------------------------------------------------------------
// Call / New
// ---------------------------------------------------------------------------

/** A function call expression. */
class AstCall extends AstNode {
  var expression:  AstNode | Null       = null
  var args:        ArrayBuffer[AstNode] = ArrayBuffer.empty
  var optional:    Boolean              = false
  var annotations: Int                  = 0

  def nodeType: String = "Call"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    // Terser walks args first, then expression
    var i = 0
    while (i < args.size) {
      args(i).walk(visitor)
      i += 1
    }
    if (expression != null) expression.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = args.size
    while ({ i -= 1; i >= 0 }) push(args(i))
    if (expression != null) push(expression.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (expression != null) expression = expression.nn.transform(tw)
    args = transformList(args, tw)
  }
}

/** An object instantiation. Derives from a function call. */
class AstNew extends AstCall {
  override def nodeType: String = "New"
}

// ---------------------------------------------------------------------------
// Sequence
// ---------------------------------------------------------------------------

/** A sequence expression (comma-separated expressions). */
class AstSequence extends AstNode {
  var expressions: ArrayBuffer[AstNode] = ArrayBuffer.empty

  def nodeType: String = "Sequence"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    var i = 0
    while (i < expressions.size) {
      expressions(i).walk(visitor)
      i += 1
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = expressions.size
    while ({ i -= 1; i >= 0 }) push(expressions(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    expressions = transformList(expressions, tw)
    if (expressions.isEmpty) expressions.addOne(new AstNumber())
  }
}

// ---------------------------------------------------------------------------
// Property access
// ---------------------------------------------------------------------------

/** Base class for property access expressions, i.e. `a.foo` or `a["foo"]`. */
trait AstPropAccess extends AstNode {
  var expression: AstNode | Null = null

  /** For AstDot/AstDotHash this is a String stored as AstNode (actually unused -- property name is `property`). For AstSub it's an arbitrary AstNode. We use Any to accommodate both.
    */
  var property: String | AstNode = ""
  var optional: Boolean          = false
}

/** A dotted property access expression. */
class AstDot extends AstNode with AstPropAccess {
  var quote:       String = ""
  var annotations: Int    = 0

  def nodeType: String = "Dot"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}

/** A dotted property access to a private property. */
class AstDotHash extends AstNode with AstPropAccess {
  def nodeType: String = "DotHash"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}

/** Index-style property access, i.e. `a["foo"]`. */
class AstSub extends AstNode with AstPropAccess {
  var annotations: Int = 0

  def nodeType: String = "Sub"

  /** The subscript property as an AstNode (for walking). */
  def propertyNode: AstNode | Null = property match {
    case n: AstNode => n
    case _ => null
  }

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (expression != null) expression.nn.walk(visitor)
    property match {
      case n: AstNode => n.walk(visitor)
      case _ =>
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    property match {
      case n: AstNode => push(n)
      case _ =>
    }
    if (expression != null) push(expression.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (expression != null) expression = expression.nn.transform(tw)
    property match {
      case p: AstNode => property = p.transform(tw)
      case _ =>
    }
  }
}

// ---------------------------------------------------------------------------
// Chain
// ---------------------------------------------------------------------------

/** A chain expression like a?.b?.(c)?.[d]. */
class AstChain extends AstNode {
  var expression: AstNode | Null = null

  def nodeType: String = "Chain"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}

// ---------------------------------------------------------------------------
// Unary
// ---------------------------------------------------------------------------

/** Base class for unary expressions. */
trait AstUnary extends AstNode {
  var operator:   String         = ""
  var expression: AstNode | Null = null
}

/** Unary prefix expression, i.e. `typeof i` or `++i`. */
class AstUnaryPrefix extends AstNode with AstUnary {
  def nodeType: String = "UnaryPrefix"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}

/** Unary postfix expression, i.e. `i++`. */
class AstUnaryPostfix extends AstNode with AstUnary {
  def nodeType: String = "UnaryPostfix"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}

// ---------------------------------------------------------------------------
// Binary
// ---------------------------------------------------------------------------

/** Binary expression, i.e. `a + b`. */
class AstBinary extends AstNode {
  var operator: String         = ""
  var left:     AstNode | Null = null
  var right:    AstNode | Null = null

  def nodeType: String = "Binary"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (left != null) left.nn.walk(visitor)
    if (right != null) right.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (right != null) push(right.nn)
    if (left != null) push(left.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (left != null) left = left.nn.transform(tw)
    if (right != null) right = right.nn.transform(tw)
  }
}

/** An assignment expression -- `a = b + 5`. */
class AstAssign extends AstBinary {
  var logical: Boolean = false

  override def nodeType: String = "Assign"
}

/** A default assignment expression like in `(a = 3) => a`. */
class AstDefaultAssign extends AstBinary {
  override def nodeType: String = "DefaultAssign"
}

// ---------------------------------------------------------------------------
// Conditional
// ---------------------------------------------------------------------------

/** Conditional expression using the ternary operator, i.e. `a ? b : c`. */
class AstConditional extends AstNode {
  var condition:   AstNode | Null = null
  var consequent:  AstNode | Null = null
  var alternative: AstNode | Null = null

  def nodeType: String = "Conditional"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (condition != null) condition.nn.walk(visitor)
    if (consequent != null) consequent.nn.walk(visitor)
    if (alternative != null) alternative.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (alternative != null) push(alternative.nn)
    if (consequent != null) push(consequent.nn)
    if (condition != null) push(condition.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (condition != null) condition = condition.nn.transform(tw)
    if (consequent != null) consequent = consequent.nn.transform(tw)
    if (alternative != null) alternative = alternative.nn.transform(tw)
  }
}

// ---------------------------------------------------------------------------
// Array / Object literals
// ---------------------------------------------------------------------------

/** An array literal. */
class AstArray extends AstNode {
  var elements: ArrayBuffer[AstNode] = ArrayBuffer.empty

  def nodeType: String = "Array"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    var i = 0
    while (i < elements.size) {
      elements(i).walk(visitor)
      i += 1
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = elements.size
    while ({ i -= 1; i >= 0 }) push(elements(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    elements = transformList(elements, tw)
}

/** An object literal. */
class AstObject extends AstNode {
  var properties: ArrayBuffer[AstNode] = ArrayBuffer.empty

  def nodeType: String = "Object"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    var i = 0
    while (i < properties.size) {
      properties(i).walk(visitor)
      i += 1
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = properties.size
    while ({ i -= 1; i >= 0 }) push(properties(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    properties = transformList(properties, tw)
}

// ---------------------------------------------------------------------------
// Await / Yield
// ---------------------------------------------------------------------------

/** An `await` expression. */
class AstAwait extends AstNode {
  var expression: AstNode | Null = null

  def nodeType: String = "Await"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}

/** A `yield` expression. */
class AstYield extends AstNode {
  var expression: AstNode | Null = null
  var isStar:     Boolean        = false

  def nodeType: String = "Yield"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (expression != null) expression.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (expression != null) push(expression.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (expression != null) expression = expression.nn.transform(tw)
}

// ---------------------------------------------------------------------------
// Template strings
// ---------------------------------------------------------------------------

/** A templatestring with a prefix, such as String.raw`foobarbaz`. */
class AstPrefixedTemplateString extends AstNode {
  var templateString: AstTemplateString | Null = null
  var prefix:         AstNode | Null           = null

  def nodeType: String = "PrefixedTemplateString"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (prefix != null) prefix.nn.walk(visitor)
    if (templateString != null) templateString.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (templateString != null) push(templateString.nn)
    if (prefix != null) push(prefix.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (prefix != null) prefix = prefix.nn.transform(tw)
    if (templateString != null) templateString = templateString.nn.transform(tw).asInstanceOf[AstTemplateString]
  }
}

/** A template string literal. */
class AstTemplateString extends AstNode {
  var segments: ArrayBuffer[AstNode] = ArrayBuffer.empty

  def nodeType: String = "TemplateString"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    var i = 0
    while (i < segments.size) {
      segments(i).walk(visitor)
      i += 1
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = segments.size
    while ({ i -= 1; i >= 0 }) push(segments(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    segments = transformList(segments, tw)
}

/** A segment of a template string literal. */
class AstTemplateSegment extends AstNode {
  var value: String = ""
  var raw:   String = ""

  def nodeType: String = "TemplateSegment"
}
