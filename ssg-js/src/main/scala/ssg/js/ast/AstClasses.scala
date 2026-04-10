/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Class and object property AST nodes.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*, static -> isStatic (reserved keyword)
 *   Convention: `extends` -> `superClass` (reserved keyword)
 *   Idiom: computedKey() method on property nodes
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer

// ---------------------------------------------------------------------------
// Object properties
// ---------------------------------------------------------------------------

/** Base class for literal object/class properties. */
trait AstObjectProperty extends AstNode {

  /** Property name. For ObjectKeyVal this is a string key. For getters, setters, computed: an AstNode. */
  var key:         String | AstNode = ""
  var value:       AstNode | Null   = null
  var annotations: Int              = 0

  def computedKey(): Boolean = key.isInstanceOf[AstNode]
}

/** Helper: walk key (if AstNode) then value. */
private trait AstObjectPropertyWalk extends AstObjectProperty {
  override protected def walkChildren(visitor: TreeWalker): Unit = {
    key match {
      case n: AstNode => n.walk(visitor)
      case _ =>
    }
    if (value != null) value.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (value != null) push(value.nn)
    key match {
      case n: AstNode => push(n)
      case _ =>
    }
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    key match {
      case k: AstNode => key = k.transform(tw)
      case _          =>
    }
    if (value != null) value = value.nn.transform(tw)
  }
}

/** A key: value object property. */
class AstObjectKeyVal extends AstNode with AstObjectProperty with AstObjectPropertyWalk {
  var quote: String = ""

  def nodeType: String = "ObjectKeyVal"
}

/** An object getter property. */
class AstObjectGetter extends AstNode with AstObjectProperty with AstObjectPropertyWalk {
  var quote:    String  = ""
  var isStatic: Boolean = false

  def nodeType: String = "ObjectGetter"

  override def computedKey(): Boolean = key match {
    case _: AstSymbolMethod => false
    case _: AstNode         => true
    case _ => false
  }
}

/** An object setter property. */
class AstObjectSetter extends AstNode with AstObjectProperty with AstObjectPropertyWalk {
  var quote:    String  = ""
  var isStatic: Boolean = false

  def nodeType: String = "ObjectSetter"

  override def computedKey(): Boolean = key match {
    case _: AstSymbolMethod => false
    case _: AstNode         => true
    case _ => false
  }
}

/** An ES6 concise method inside an object or class. */
class AstConciseMethod extends AstNode with AstObjectProperty with AstObjectPropertyWalk {
  var quote:    String  = ""
  var isStatic: Boolean = false

  def nodeType: String = "ConciseMethod"

  override def computedKey(): Boolean = key match {
    case _: AstSymbolMethod => false
    case _: AstNode         => true
    case _ => false
  }
}

/** A private class method inside a class. */
class AstPrivateMethod extends AstNode with AstObjectProperty with AstObjectPropertyWalk {
  var isStatic: Boolean = false

  def nodeType: String = "PrivateMethod"

  override def computedKey(): Boolean = false
}

/** A private getter property. */
class AstPrivateGetter extends AstNode with AstObjectProperty with AstObjectPropertyWalk {
  var isStatic: Boolean = false

  def nodeType: String = "PrivateGetter"

  override def computedKey(): Boolean = false
}

/** A private setter property. */
class AstPrivateSetter extends AstNode with AstObjectProperty with AstObjectPropertyWalk {
  var isStatic: Boolean = false

  def nodeType: String = "PrivateSetter"

  override def computedKey(): Boolean = false
}

// ---------------------------------------------------------------------------
// Class properties
// ---------------------------------------------------------------------------

/** A class property. */
class AstClassProperty extends AstNode with AstObjectProperty {
  var isStatic: Boolean = false
  var quote:    String  = ""

  def nodeType: String = "ClassProperty"

  override def computedKey(): Boolean = key match {
    case _: AstSymbolClassProperty => false
    case _: AstNode                => true
    case _ => false
  }

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    key match {
      case n: AstNode => n.walk(visitor)
      case _: String  =>
    }
    if (value != null) value.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (value != null) push(value.nn)
    key match {
      case n: AstNode => push(n)
      case _: String  =>
    }
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    key match {
      case k: AstNode => key = k.transform(tw)
      case _          =>
    }
    if (value != null) value = value.nn.transform(tw)
  }
}

/** A class property for a private property. */
class AstClassPrivateProperty extends AstNode with AstObjectProperty {
  var isStatic: Boolean = false

  def nodeType: String = "ClassPrivateProperty"

  override def computedKey(): Boolean = false

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (value != null) value.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (value != null) push(value.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (value != null) value = value.nn.transform(tw)
}

/** An `in` binop when the key is private, eg `#x in this`. */
class AstPrivateIn extends AstNode {
  var key:   AstNode | Null = null
  var value: AstNode | Null = null

  def nodeType: String = "PrivateIn"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (key != null) key.nn.walk(visitor)
    if (value != null) value.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (value != null) push(value.nn)
    if (key != null) push(key.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (key != null) key = key.nn.transform(tw)
    if (value != null) value = value.nn.transform(tw)
  }
}

// ---------------------------------------------------------------------------
// Class static block
// ---------------------------------------------------------------------------

/** A block containing statements to be executed in the context of the class. */
class AstClassStaticBlock extends AstNode with AstScope {
  def nodeType: String = "ClassStaticBlock"

  def computedKey(): Boolean = false

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
// Class
// ---------------------------------------------------------------------------

/** An ES6 class. */
class AstClass extends AstNode with AstScope {
  var name:       AstNode | Null       = null
  var superClass: AstNode | Null       = null
  var properties: ArrayBuffer[AstNode] = ArrayBuffer.empty

  def nodeType: String = "Class"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (name != null) name.nn.walk(visitor)
    if (superClass != null) superClass.nn.walk(visitor)
    var i = 0
    while (i < properties.size) {
      properties(i).walk(visitor)
      i += 1
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = properties.size
    while ({ i -= 1; i >= 0 }) push(properties(i))
    if (superClass != null) push(superClass.nn)
    if (name != null) push(name.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (name != null) name = name.nn.transform(tw)
    if (superClass != null) superClass = superClass.nn.transform(tw)
    properties = transformList(properties, tw)
  }
}

/** A class definition. */
class AstDefClass extends AstClass {
  override def nodeType: String = "DefClass"
}

/** A class expression. */
class AstClassExpression extends AstClass {
  override def nodeType: String = "ClassExpression"
}
