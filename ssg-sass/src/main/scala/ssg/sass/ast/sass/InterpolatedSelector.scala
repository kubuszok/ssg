/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/interpolated_selector.dart,
 *              lib/src/ast/sass/interpolated_selector/list.dart,
 *              lib/src/ast/sass/interpolated_selector/complex.dart,
 *              lib/src/ast/sass/interpolated_selector/complex_component.dart,
 *              lib/src/ast/sass/interpolated_selector/compound.dart,
 *              lib/src/ast/sass/interpolated_selector/simple.dart,
 *              lib/src/ast/sass/interpolated_selector/attribute.dart,
 *              lib/src/ast/sass/interpolated_selector/class.dart,
 *              lib/src/ast/sass/interpolated_selector/id.dart,
 *              lib/src/ast/sass/interpolated_selector/parent.dart,
 *              lib/src/ast/sass/interpolated_selector/placeholder.dart,
 *              lib/src/ast/sass/interpolated_selector/pseudo.dart,
 *              lib/src/ast/sass/interpolated_selector/qualified_name.dart,
 *              lib/src/ast/sass/interpolated_selector/type.dart,
 *              lib/src/ast/sass/interpolated_selector/universal.dart
 * Original: Copyright (c) 2025 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: interpolated_selector.dart + 14 subtype files -> InterpolatedSelector.scala
 *   Convention: Dart abstract base class -> Scala abstract class;
 *               Dart final class -> Scala final class
 *   Idiom: InterpolatedSelectorVisitor as forward-reference trait;
 *          Nullable for optional fields
 */
package ssg
package sass
package ast
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.ast.css.CssValue
import ssg.sass.ast.selector.{ AttributeOperator, Combinator }
import ssg.sass.util.FileSpan

// ===========================================================================
// InterpolatedSelectorVisitor — forward reference trait
// ===========================================================================

/** Visitor interface for [InterpolatedSelector] nodes. */
trait InterpolatedSelectorVisitor[T] {
  def visitSelectorList(node:        InterpolatedSelectorList):        T
  def visitComplexSelector(node:     InterpolatedComplexSelector):     T
  def visitCompoundSelector(node:    InterpolatedCompoundSelector):    T
  def visitAttributeSelector(node:   InterpolatedAttributeSelector):   T
  def visitClassSelector(node:       InterpolatedClassSelector):       T
  def visitIDSelector(node:          InterpolatedIDSelector):          T
  def visitParentSelector(node:      InterpolatedParentSelector):      T
  def visitPlaceholderSelector(node: InterpolatedPlaceholderSelector): T
  def visitPseudoSelector(node:      InterpolatedPseudoSelector):      T
  def visitTypeSelector(node:        InterpolatedTypeSelector):        T
  def visitUniversalSelector(node:   InterpolatedUniversalSelector):   T
}

// ===========================================================================
// InterpolatedSelector — base class
// ===========================================================================

/** A selector before interpolation is resolved.
  *
  * Unlike [Selector], this is parsed during the initial stylesheet parse when `parseSelectors: true` is passed to `Stylesheet.parse`.
  */
abstract class InterpolatedSelector extends SassNode {

  /** Calls the appropriate visit method on [visitor]. */
  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T
}

// ===========================================================================
// InterpolatedSimpleSelector — abstract base for simple selectors
// ===========================================================================

/** A simple selector before interpolation is resolved. */
abstract class InterpolatedSimpleSelector extends InterpolatedSelector

// ===========================================================================
// InterpolatedQualifiedName
// ===========================================================================

/** A qualified name in an interpolated selector context.
  *
  * @param name
  *   the identifier name
  * @param span
  *   the source span
  * @param namespace
  *   the namespace name, or empty
  */
final class InterpolatedQualifiedName(
  val name:      Interpolation,
  val span:      FileSpan,
  val namespace: Nullable[Interpolation] = Nullable.empty
) extends SassNode {

  override def toString: String =
    namespace.fold(name.toString)(ns => s"$ns|$name")
}

// ===========================================================================
// InterpolatedComplexSelectorComponent
// ===========================================================================

/** A component of an [InterpolatedComplexSelector].
  *
  * @param selector
  *   this component's compound selector
  * @param span
  *   the source span
  * @param combinator
  *   this selector's combinator, or empty for implicit descendant
  */
final class InterpolatedComplexSelectorComponent(
  val selector:   InterpolatedCompoundSelector,
  val span:       FileSpan,
  val combinator: Nullable[CssValue[Combinator]] = Nullable.empty
) extends SassNode {

  override def toString: String =
    combinator.fold(selector.toString)(c => s"$selector $c")
}

// ===========================================================================
// InterpolatedSelectorList
// ===========================================================================

/** A selector list before interpolation is resolved.
  *
  * @param components
  *   the components of this selector (never empty)
  */
final class InterpolatedSelectorList(
  val components: List[InterpolatedComplexSelector]
) extends InterpolatedSelector {
  require(components.nonEmpty, "components may not be empty.")

  def span: FileSpan =
    if (components.length == 1) components.head.span
    else components.head.span.expand(components.last.span)

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitSelectorList(this)

  override def toString: String = components.mkString(", ")
}

// ===========================================================================
// InterpolatedComplexSelector
// ===========================================================================

/** A complex selector before interpolation is resolved.
  *
  * @param components
  *   the components of this selector
  * @param span
  *   the source span
  * @param leadingCombinator
  *   the leading combinator, or empty
  */
final class InterpolatedComplexSelector(
  val components:        List[InterpolatedComplexSelectorComponent],
  val span:              FileSpan,
  val leadingCombinator: Nullable[CssValue[Combinator]] = Nullable.empty
) extends InterpolatedSelector {
  require(
    leadingCombinator.isDefined || components.nonEmpty,
    "components may not be empty if leadingCombinator is empty."
  )

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitComplexSelector(this)

  override def toString: String = components.mkString(" ")
}

// ===========================================================================
// InterpolatedCompoundSelector
// ===========================================================================

/** A compound selector before interpolation is resolved.
  *
  * @param components
  *   the components of this selector (never empty)
  */
final class InterpolatedCompoundSelector(
  val components: List[InterpolatedSimpleSelector]
) extends InterpolatedSelector {
  require(components.nonEmpty, "components may not be empty.")

  def span: FileSpan =
    if (components.length == 1) components.head.span
    else components.head.span.expand(components.last.span)

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitCompoundSelector(this)

  override def toString: String = components.mkString
}

// ===========================================================================
// InterpolatedAttributeSelector
// ===========================================================================

/** An attribute selector.
  *
  * @param name
  *   the name of the attribute being selected for
  * @param span
  *   the source span
  * @param op
  *   the operator, or empty
  * @param value
  *   an assertion about the value of [name], or empty
  * @param modifier
  *   the modifier, or empty
  */
final class InterpolatedAttributeSelector(
  val name:     InterpolatedQualifiedName,
  val span:     FileSpan,
  val op:       Nullable[CssValue[AttributeOperator]] = Nullable.empty,
  val value:    Nullable[Interpolation] = Nullable.empty,
  val modifier: Nullable[Interpolation] = Nullable.empty
) extends InterpolatedSimpleSelector {

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitAttributeSelector(this)

  override def toString: String = {
    val result = new StringBuilder(s"[$name")
    op.foreach { o =>
      result.append(s"$o${value.get}")
      modifier.foreach(m => result.append(s" $m"))
    }
    result.append(']')
    result.toString()
  }
}

// ===========================================================================
// InterpolatedClassSelector
// ===========================================================================

/** A class selector.
  *
  * @param name
  *   the class name this selects for
  */
final class InterpolatedClassSelector(
  val name: Interpolation
) extends InterpolatedSimpleSelector {

  def span: FileSpan =
    name.span.file.span(name.span.start.offset - 1, name.span.end.offset)

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitClassSelector(this)

  override def toString: String = s".$name"
}

// ===========================================================================
// InterpolatedIDSelector
// ===========================================================================

/** An ID selector.
  *
  * @param name
  *   the id name this selects for
  */
final class InterpolatedIDSelector(
  val name: Interpolation
) extends InterpolatedSimpleSelector {

  def span: FileSpan =
    name.span.file.span(name.span.start.offset - 1, name.span.end.offset)

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitIDSelector(this)

  override def toString: String = s"#$name"
}

// ===========================================================================
// InterpolatedParentSelector
// ===========================================================================

/** A parent selector.
  *
  * @param span
  *   the source span
  * @param suffix
  *   the suffix that will be added to the parent selector after resolution
  */
final class InterpolatedParentSelector(
  val span:   FileSpan,
  val suffix: Nullable[Interpolation] = Nullable.empty
) extends InterpolatedSimpleSelector {

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitParentSelector(this)

  override def toString: String =
    suffix.fold("&")(s => s"&$s")
}

// ===========================================================================
// InterpolatedPlaceholderSelector
// ===========================================================================

/** A placeholder selector.
  *
  * @param name
  *   the name of the placeholder
  */
final class InterpolatedPlaceholderSelector(
  val name: Interpolation
) extends InterpolatedSimpleSelector {

  def span: FileSpan =
    name.span.file.span(name.span.start.offset - 1, name.span.end.offset)

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitPlaceholderSelector(this)

  override def toString: String = s"%$name"
}

// ===========================================================================
// InterpolatedPseudoSelector
// ===========================================================================

/** A pseudo-class or pseudo-element selector.
  *
  * @param name
  *   the name of this selector (including any vendor prefixes)
  * @param span
  *   the source span
  * @param isSyntacticClass
  *   whether this is syntactically a pseudo-class selector
  * @param argument
  *   the non-selector argument, or empty
  * @param selector
  *   the selector argument, or empty
  */
final class InterpolatedPseudoSelector(
  val name:             Interpolation,
  val span:             FileSpan,
  val isSyntacticClass: Boolean = true,
  val argument:         Nullable[Interpolation] = Nullable.empty,
  val selector:         Nullable[InterpolatedSelectorList] = Nullable.empty
) extends InterpolatedSimpleSelector {

  /** Whether this is syntactically a pseudo-element selector. */
  def isSyntacticElement: Boolean = !isSyntacticClass

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitPseudoSelector(this)

  override def toString: String = {
    val prefix = if (isSyntacticClass) ":" else "::"
    val result = new StringBuilder(s"$prefix$name")
    if (argument.isDefined || selector.isDefined) {
      result.append('(')
      argument.foreach { arg =>
        result.append(arg)
        if (selector.isDefined) result.append(' ')
      }
      selector.foreach(sel => result.append(sel))
      result.append(')')
    }
    result.toString()
  }
}

// ===========================================================================
// InterpolatedTypeSelector
// ===========================================================================

/** A type selector.
  *
  * @param name
  *   the element name being selected for
  */
final class InterpolatedTypeSelector(
  val name: InterpolatedQualifiedName
) extends InterpolatedSimpleSelector {

  def span: FileSpan = name.span

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitTypeSelector(this)

  override def toString: String = name.toString
}

// ===========================================================================
// InterpolatedUniversalSelector
// ===========================================================================

/** A universal selector.
  *
  * @param span
  *   the source span
  * @param namespace
  *   the selector namespace, or empty
  */
final class InterpolatedUniversalSelector(
  val span:      FileSpan,
  val namespace: Nullable[Interpolation] = Nullable.empty
) extends InterpolatedSimpleSelector {

  def accept[T](visitor: InterpolatedSelectorVisitor[T]): T =
    visitor.visitUniversalSelector(this)

  override def toString: String =
    namespace.fold("*")(ns => s"$ns|*")
}
