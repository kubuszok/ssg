/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/selector/simple.dart, attribute.dart, class.dart,
 *              id.dart, type.dart, universal.dart, parent.dart, placeholder.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: simple.dart et al -> SimpleSelector.scala (merged small selectors)
 *   Convention: Dart abstract base class -> Scala abstract class
 *   Idiom: _subselectorPseudos -> private val in companion;
 *          null returns -> Nullable; addSuffix throws -> same pattern
 */
package ssg
package sass
package ast
package selector

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.MultiSpanSassException
import ssg.sass.util.{ CharCode, FileSpan }

import scala.language.implicitConversions

/** An abstract superclass for simple selectors. */
abstract class SimpleSelector(span: FileSpan) extends Selector(span) {

  /** This selector's specificity.
    *
    * Specificity is represented in base 1000. The spec says this should be "sufficiently high"; it's extremely unlikely that any single selector sequence will contain 1000 simple selectors.
    */
  def specificity: Int = 1000

  /** Whether this requires complex non-local reasoning to determine whether it's a super- or sub-selector.
    *
    * This includes both pseudo-elements and pseudo-selectors that take selectors as arguments.
    */
  def hasComplicatedSuperselectorSemantics: Boolean = false

  /** Returns a new [[SimpleSelector]] based on `this`, as though it had been written with `suffix` at the end.
    *
    * Assumes `suffix` is a valid identifier suffix. If this wouldn't produce a valid [[SimpleSelector]], throws a [[SassException]].
    */
  def addSuffix(suffix: String): SimpleSelector =
    throw MultiSpanSassException(
      s"""Selector "$this" can't have a suffix""",
      span,
      "outer selector",
      Map.empty
    )

  /** Returns the components of a [[CompoundSelector]] that matches only elements matched by both this and `compound`.
    *
    * By default, this just returns a copy of `compound` with this selector added to the end, or returns the original list if this selector already exists in it.
    *
    * Returns `Nullable.Null` if unification is impossible -- for example, if there are multiple ID selectors.
    */
  def unify(compound: List[SimpleSelector]): Nullable[List[SimpleSelector]] =
    compound match {
      case List(other)
          if other.isInstanceOf[UniversalSelector] ||
            (other.isInstanceOf[PseudoSelector] &&
              (other.asInstanceOf[PseudoSelector].isHost || other.asInstanceOf[PseudoSelector].isHostContext)) =>
        other.unify(List(this))
      case _ =>
        if (compound.contains(this)) Nullable(compound)
        else {
          val result    = scala.collection.mutable.ListBuffer.empty[SimpleSelector]
          var addedThis = false
          for (simple <- compound) {
            // Make sure pseudo selectors always come last.
            if (!addedThis && simple.isInstanceOf[PseudoSelector]) {
              result += this
              addedThis = true
            }
            result += simple
          }
          if (!addedThis) result += this
          Nullable(result.toList)
        }
    }

  /** Whether this is a superselector of `other`.
    *
    * That is, whether this matches every element that `other` matches, as well as possibly additional elements.
    */
  def isSuperselector(other: SimpleSelector): Boolean =
    if (this == other) true
    else
      other match {
        case pseudo: PseudoSelector if pseudo.isClass =>
          val list = pseudo.selector
          if (list.isDefined && SimpleSelector.subselectorPseudos.contains(pseudo.normalizedName)) {
            list.get.components.forall { complex =>
              complex.components.nonEmpty &&
              complex.components.last.selector.components.exists { simple =>
                isSuperselector(simple)
              }
            }
          } else {
            false
          }
        case _ => false
      }
}

object SimpleSelector {

  /** Names of pseudo-classes that take selectors as arguments, and that are subselectors of the union of their arguments.
    *
    * For example, `.foo` is a superselector of `:matches(.foo)`.
    */
  private[selector] val subselectorPseudos: Set[String] =
    Set("is", "matches", "where", "any", "nth-child", "nth-last-child")

  /** Unifies a universal or type selector with another simple selector.
    *
    * Returns `None` if unification is impossible.
    *
    * Port of dart-sass `unifyUniversalAndElement` (lib/src/extend/functions.dart:166-194).
    * Namespace semantics:
    *   - `Nullable.Null` = default (no prefix) — matches only the default namespace
    *   - `Some("*")`     = wildcard `*|` — matches any namespace
    *   - `Some("")`      = empty `||` — matches elements with no namespace
    *   - `Some("ns")`    = explicit — matches only that namespace
    */
  private[selector] def unifyUniversalAndElement(
    selector1: SimpleSelector,
    selector2: SimpleSelector
  ): Option[SimpleSelector] = {
    val (namespace1, name1) = _namespaceAndName(selector1)
    val (namespace2, name2) = _namespaceAndName(selector2)

    // Unify namespaces
    val namespace: Nullable[String] =
      if (namespace1 == namespace2 || namespace2.exists(_ == "*")) namespace1
      else if (namespace1.exists(_ == "*")) namespace2
      else return None // incompatible namespaces

    // Unify names
    val name: Nullable[String] =
      if (name1 == name2 || name2.isEmpty) name1
      else if (name1.isEmpty) name2
      else return None // incompatible names

    val sp = selector1.span
    if (name.isDefined) Some(TypeSelector(QualifiedName(name.get, namespace), sp))
    else Some(new UniversalSelector(sp, namespace))
  }

  /** Extracts (namespace, name) from a UniversalSelector or TypeSelector. */
  private def _namespaceAndName(selector: SimpleSelector): (Nullable[String], Nullable[String]) =
    selector match {
      case u: UniversalSelector => (u.namespace, Nullable.Null)
      case t: TypeSelector      => (t.name.namespace, Nullable(t.name.name))
      case _ => throw new IllegalArgumentException(s"Expected UniversalSelector or TypeSelector, got ${selector.getClass}")
    }
}

// ---------------------------------------------------------------------------
// Concrete simple selectors
// ---------------------------------------------------------------------------

/** An attribute selector.
  *
  * This selects for elements with the given attribute, and optionally with a value matching certain conditions as well.
  */
final class AttributeSelector private (
  val name:     QualifiedName,
  val op:       Nullable[AttributeOperator],
  val value:    Nullable[String],
  val modifier: Nullable[String],
  span:         FileSpan
) extends SimpleSelector(span) {

  override def accept[T](visitor: SelectorVisitor[T]): T =
    visitor.visitAttributeSelector(this)

  override def equals(other: Any): Boolean = other match {
    case that: AttributeSelector =>
      that.name == name && that.op == op && that.value == value && that.modifier == modifier
    case _ => false
  }

  override def hashCode(): Int =
    name.hashCode() ^ op.hashCode() ^ value.hashCode() ^ modifier.hashCode()
}

object AttributeSelector {

  /** Creates an attribute selector that matches any element with a property of the given name.
    */
  def apply(name: QualifiedName, span: FileSpan): AttributeSelector =
    new AttributeSelector(name, Nullable.Null, Nullable.Null, Nullable.Null, span)

  /** Creates an attribute selector that matches an element with a property named `name`, whose value matches `value` based on the semantics of `op`.
    */
  def withOperator(
    name:     QualifiedName,
    op:       AttributeOperator,
    value:    String,
    span:     FileSpan,
    modifier: Nullable[String] = Nullable.Null
  ): AttributeSelector =
    new AttributeSelector(name, Nullable(op), Nullable(value), modifier, span)
}

/** An operator that defines the semantics of an [[AttributeSelector]]. */
enum AttributeOperator(val text: String) extends java.lang.Enum[AttributeOperator] {

  /** The attribute value exactly equals the given value. */
  case Equal extends AttributeOperator("=")

  /** The attribute value is a whitespace-separated list of words, one of which is the given value.
    */
  case Include extends AttributeOperator("~=")

  /** The attribute value is either exactly the given value, or starts with the given value followed by a dash.
    */
  case Dash extends AttributeOperator("|=")

  /** The attribute value begins with the given value. */
  case Prefix extends AttributeOperator("^=")

  /** The attribute value ends with the given value. */
  case Suffix extends AttributeOperator("$=")

  /** The attribute value contains the given value. */
  case Substring extends AttributeOperator("*=")

  override def toString: String = text
}

/** A class selector.
  *
  * This selects elements whose `class` attribute contains an identifier with the given name.
  */
final class ClassSelector(val name: String, span: FileSpan) extends SimpleSelector(span) {

  override def accept[T](visitor: SelectorVisitor[T]): T = visitor.visitClassSelector(this)

  override def addSuffix(suffix: String): ClassSelector = ClassSelector(name + suffix, span)

  override def equals(other: Any): Boolean = other match {
    case that: ClassSelector => that.name == name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode()
}

/** An ID selector.
  *
  * This selects elements whose `id` attribute exactly matches the given name.
  */
final class IDSelector(val name: String, span: FileSpan) extends SimpleSelector(span) {

  override def specificity: Int = {
    val base = super.specificity
    base * base
  }

  override def accept[T](visitor: SelectorVisitor[T]): T = visitor.visitIDSelector(this)

  override def addSuffix(suffix: String): IDSelector = IDSelector(name + suffix, span)

  override def unify(compound: List[SimpleSelector]): Nullable[List[SimpleSelector]] =
    // A given compound selector may only contain one ID.
    if (compound.exists(s => s.isInstanceOf[IDSelector] && s != this)) Nullable.Null
    else super.unify(compound)

  override def equals(other: Any): Boolean = other match {
    case that: IDSelector => that.name == name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode()
}

/** A type selector.
  *
  * This selects elements whose name equals the given name.
  */
final class TypeSelector(val name: QualifiedName, span: FileSpan) extends SimpleSelector(span) {

  override def specificity: Int = 1

  override def accept[T](visitor: SelectorVisitor[T]): T = visitor.visitTypeSelector(this)

  override def addSuffix(suffix: String): TypeSelector =
    TypeSelector(QualifiedName(name.name + suffix, name.namespace), span)

  override def unify(compound: List[SimpleSelector]): Nullable[List[SimpleSelector]] =
    compound match {
      case first :: rest if first.isInstanceOf[UniversalSelector] || first.isInstanceOf[TypeSelector] =>
        val unified = SimpleSelector.unifyUniversalAndElement(this, first)
        if (unified.isEmpty) Nullable.Null
        else Nullable(unified.get :: rest)
      case _ =>
        Nullable(this :: compound)
    }

  override def isSuperselector(other: SimpleSelector): Boolean =
    super.isSuperselector(other) ||
      (other match {
        case t: TypeSelector =>
          name.name == t.name.name &&
          (name.namespace.exists(_ == "*") || name.namespace == t.name.namespace)
        case _ => false
      })

  override def equals(other: Any): Boolean = other match {
    case that: TypeSelector => that.name == name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode()
}

/** Matches any element in the given namespace. */
final class UniversalSelector(span: FileSpan, val namespace: Nullable[String] = Nullable.Null) extends SimpleSelector(span) {

  override def specificity: Int = 0

  override def accept[T](visitor: SelectorVisitor[T]): T = visitor.visitUniversalSelector(this)

  override def unify(compound: List[SimpleSelector]): Nullable[List[SimpleSelector]] =
    compound match {
      case (first @ (_: UniversalSelector | _: TypeSelector)) :: rest =>
        val unified = SimpleSelector.unifyUniversalAndElement(this, first)
        if (unified.isEmpty) Nullable.Null
        else Nullable(unified.get :: rest)
      case List(first: PseudoSelector) if first.isHost || first.isHostContext =>
        Nullable.Null
      case Nil =>
        Nullable(List(this))
      case _ =>
        if (namespace.isEmpty || namespace.exists(_ == "*")) Nullable(compound)
        else Nullable(this :: compound)
    }

  override def isSuperselector(other: SimpleSelector): Boolean =
    if (namespace.exists(_ == "*")) true
    else
      other match {
        case t: TypeSelector      => namespace == t.name.namespace
        case u: UniversalSelector => namespace == u.namespace
        case _ => namespace.isEmpty || super.isSuperselector(other)
      }

  override def equals(other: Any): Boolean = other match {
    case that: UniversalSelector => that.namespace == namespace
    case _ => false
  }

  override def hashCode(): Int = namespace.hashCode()
}

/** A selector that matches the parent in the Sass stylesheet.
  *
  * This is not a plain CSS selector -- it should be removed before emitting a CSS document.
  */
final class ParentSelector(span: FileSpan, val suffix: Nullable[String] = Nullable.Null) extends SimpleSelector(span) {

  override def accept[T](visitor: SelectorVisitor[T]): T = visitor.visitParentSelector(this)

  override def unify(compound: List[SimpleSelector]): Nullable[List[SimpleSelector]] =
    throw new UnsupportedOperationException("& doesn't support unification.")
}

/** A `%name` selector.
  *
  * This doesn't match any elements. It's intended to be extended using `@extend`. It's not a plain CSS selector -- it should be removed before emitting a CSS document.
  */
final class PlaceholderSelector(val name: String, span: FileSpan) extends SimpleSelector(span) {

  /** Returns whether this is a private selector (that is, whether it begins with `-` or `_`).
    */
  def isPrivate: Boolean = CharCode.isPrivate(name)

  override def accept[T](visitor: SelectorVisitor[T]): T =
    visitor.visitPlaceholderSelector(this)

  override def addSuffix(suffix: String): PlaceholderSelector =
    PlaceholderSelector(name + suffix, span)

  override def equals(other: Any): Boolean = other match {
    case that: PlaceholderSelector => that.name == name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode()
}
