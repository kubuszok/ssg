/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/node.dart, lib/src/ast/css/value.dart,
 *              lib/src/ast/css/modifiable/node.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: css/node.dart + css/value.dart + css/modifiable/node.dart -> CssNode.scala
 *   Convention: Dart abstract class -> Scala abstract class; mutable parent via var
 *   Idiom: _children internal list exposed as unmodifiable view via children
 *   Idiom: _IsInvisibleVisitor deferred to Phase 5 when CssVisitor is available
 */
package ssg
package sass
package ast
package css

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.visitor.CssVisitor

import scala.collection.mutable.ArrayBuffer

// ---------------------------------------------------------------------------
// CssNode hierarchy (immutable interfaces)
// ---------------------------------------------------------------------------

/** A statement in a plain CSS syntax tree. */
abstract class CssNode extends AstNode {

  /** The node that contains this, or null for the root CssStylesheet node. */
  def parent: Nullable[CssParentNode]

  /** Whether this was generated from the last node in a nested Sass tree that got flattened during evaluation.
    */
  def isGroupEnd: Boolean

  /** Calls the appropriate visit method on [visitor]. */
  def accept[T](visitor: CssVisitor[T]): T

  /** Whether this is invisible and won't be emitted to the compiled stylesheet.
    *
    * Note that this doesn't consider nodes that contain loud comments to be invisible even though they're omitted in compressed mode.
    *
    * Default implementation returns false; overridden via visitor.
    */
  def isInvisible: Boolean = false

  /** Whether this node would be invisible even if style rule selectors within it didn't have bogus combinators.
    */
  def isInvisibleOtherThanBogusCombinators: Boolean = false

  /** Whether this node will be invisible when loud comments are stripped. */
  def isInvisibleHidingComments: Boolean = false
}

/** A CssNode that can have child statements. */
trait CssParentNode extends CssNode {

  /** The child statements of this node. */
  def children: List[CssNode]

  /** Whether the rule has no children and should be emitted without curly braces.
    *
    * This implies `children.isEmpty`, but the reverse is not true -- for a rule like `@foo {}`, children is empty but isChildless is false.
    */
  def isChildless: Boolean
}

// ---------------------------------------------------------------------------
// ModifiableCssNode hierarchy (mutable, used during evaluation)
// ---------------------------------------------------------------------------

/** A modifiable version of CssNode.
  *
  * Almost all CSS nodes are the modifiable classes under the covers. However, modification should only be done within the evaluation step, so the unmodifiable types are used elsewhere to enforce that
  * constraint.
  */
abstract class ModifiableCssNode extends CssNode {

  /** The parent of this node, set when added to a parent's children. */
  private var _parent: Nullable[ModifiableCssParentNode] = Nullable.empty

  /** The index of this node in parent.children. This makes remove() more efficient.
    */
  private var _indexInParent: Nullable[Int] = Nullable.empty

  /** Whether this was generated from the last node in a nested Sass tree that got flattened during evaluation.
    */
  var isGroupEnd: Boolean = false

  def parent: Nullable[CssParentNode] =
    _parent.fold(Nullable.empty[CssParentNode])(p => Nullable(p: CssParentNode))

  /** The parent as ModifiableCssParentNode, for use during evaluation. */
  def modifiableParent: Nullable[ModifiableCssParentNode] = _parent

  /** Whether this node has a visible sibling after it. */
  def hasFollowingSibling: Boolean =
    _parent
      .flatMap { p =>
        _indexInParent.map { idx =>
          p.children.drop(idx + 1).exists(sibling => !sibling.isInvisible)
        }
      }
      .getOrElse(false)

  /** Removes this node from parent's child list.
    *
    * Throws a StateError if parent is null.
    */
  def remove(): Unit = {
    val p = _parent.getOrElse {
      throw new IllegalStateException("Can't remove a node without a parent.")
    }
    val idx = _indexInParent.getOrElse {
      throw new IllegalStateException("Node has parent but no index.")
    }
    p._removeChildAt(idx)
    _parent = Nullable.empty
  }

  // Package-private setters used by ModifiableCssParentNode
  private[css] def _setParent(p: ModifiableCssParentNode): Unit =
    _parent = Nullable(p)

  private[css] def _clearParent(): Unit = {
    _parent = Nullable.empty
    _indexInParent = Nullable.empty
  }

  private[css] def _setIndexInParent(i: Int): Unit =
    _indexInParent = Nullable(i)

  private[css] def _getIndexInParent: Nullable[Int] = _indexInParent
}

/** A modifiable version of CssParentNode for use in the evaluation step. */
abstract class ModifiableCssParentNode extends ModifiableCssNode with CssParentNode {

  /** The internal mutable children list. */
  private val _children: ArrayBuffer[ModifiableCssNode] = ArrayBuffer.empty

  /** Unmodifiable view of children. */
  def children: List[CssNode] = _children.toList

  /** The internal modifiable children, for subclass use. */
  def modifiableChildren: List[ModifiableCssNode] = _children.toList

  def isChildless: Boolean = false

  /** Returns whether this node is equal to [other], ignoring their child nodes. */
  def equalsIgnoringChildren(other: ModifiableCssNode): Boolean

  /** Returns a copy of this node with an empty children list.
    *
    * This is not a deep copy. If other parts of this node are modifiable, they are shared between the new and old nodes.
    */
  def copyWithoutChildren(): ModifiableCssParentNode

  /** Adds [child] as a child of this statement. */
  def addChild(child: ModifiableCssNode): Unit = {
    child._setParent(this)
    child._setIndexInParent(_children.length)
    _children += child
  }

  /** Destructively removes all elements from children. */
  def clearChildren(): Unit = {
    for (child <- _children)
      child._clearParent()
    _children.clear()
  }

  /** Package-private: removes the child at the given index and updates subsequent indices.
    */
  private[css] def _removeChildAt(index: Int): Unit = {
    _children.remove(index)
    var i = index
    while (i < _children.length) {
      _children(i)._setIndexInParent(i)
      i += 1
    }
  }
}
