/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/selector/compound.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: compound.dart -> CompoundSelector.scala
 *   Convention: Dart final class -> Scala final class
 *   Idiom: Nullable singleSimple; lazy specificity/hasComplicatedSuperselectorSemantics;
 *          isSuperselector delegated to extend functions (stub for now)
 */
package ssg
package sass
package ast
package selector

import ssg.sass.Nullable
import ssg.sass.Utils
import ssg.sass.util.FileSpan

import scala.language.implicitConversions

/** A compound selector.
  *
  * A compound selector is composed of [[SimpleSelector]]s. It matches an element that matches all of the component simple selectors.
  */
final class CompoundSelector(
  val components: List[SimpleSelector],
  span:           FileSpan
) extends Selector(span) {

  require(components.nonEmpty, "components may not be empty.")

  /** This selector's specificity.
    *
    * Specificity is represented in base 1000. The spec says this should be "sufficiently high"; it's extremely unlikely that any single selector sequence will contain 1000 simple selectors.
    */
  lazy val specificity: Int = components.foldLeft(0)((sum, c) => sum + c.specificity)

  /** If this compound selector is composed of a single simple selector, returns it. Otherwise, returns `Nullable.Null`.
    */
  def singleSimple: Nullable[SimpleSelector] =
    if (components.length == 1) Nullable(components.head)
    else Nullable.Null

  /** Whether any simple selector in this contains a selector that requires complex non-local reasoning to determine whether it's a super- or sub-selector.
    *
    * This includes both pseudo-elements and pseudo-selectors that take selectors as arguments.
    */
  lazy val hasComplicatedSuperselectorSemantics: Boolean =
    components.exists(_.hasComplicatedSuperselectorSemantics)

  override def accept[T](visitor: SelectorVisitor[T]): T =
    visitor.visitCompoundSelector(this)

  /** Whether this is a superselector of `other`.
    *
    * That is, whether this matches every element that `other` matches, as well as possibly additional elements.
    *
    * Note: The full implementation delegates to `compoundIsSuperselector` in the extend functions module, which will be ported separately.
    */
  def isSuperselector(other: CompoundSelector): Boolean =
    // Basic implementation: every component of this must be a superselector of
    // at least one component of other
    components.forall { thisComponent =>
      other.components.exists(thisComponent.isSuperselector)
    }

  override def hashCode(): Int = Utils.iterableHash(components)

  override def equals(other: Any): Boolean = other match {
    case that: CompoundSelector => Utils.iterableEquals(components, that.components)
    case _ => false
  }
}
