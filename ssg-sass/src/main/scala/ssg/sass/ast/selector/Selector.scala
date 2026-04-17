/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/selector.dart, lib/src/ast/selector/combinator.dart
 * Original: Copyright (c) 2016, 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: selector.dart -> Selector.scala; combinator.dart merged here
 *   Convention: Dart abstract base class -> Scala abstract class
 *   Idiom: Private visitor classes -> nested objects with visitor methods;
 *          Dart enum Combinator -> Scala 3 enum
 */
package ssg
package sass
package ast
package selector

import ssg.sass.util.FileSpan

/** A node in the abstract syntax tree for a selector.
  *
  * This selector tree is mostly plain CSS, but also may contain a [[ParentSelector]] or a [[PlaceholderSelector]].
  *
  * Selectors have structural equality semantics.
  */
abstract class Selector(val span: FileSpan) extends AstNode {

  /** Whether this selector, and complex selectors containing it, should not be emitted.
    */
  def isInvisible: Boolean =
    accept(Selector.IsInvisibleVisitor(includeBogus = true))

  /** Whether this selector contains a [[ParentSelector]]. */
  def containsParentSelector: Boolean =
    accept(Selector.ContainsParentSelectorVisitor)

  /** Whether this selector would be invisible even if it didn't have bogus combinators.
    */
  def isInvisibleOtherThanBogusCombinators: Boolean =
    accept(Selector.IsInvisibleVisitor(includeBogus = false))

  /** Whether this selector is not valid CSS.
    *
    * This includes both selectors that are useful exclusively for build-time nesting (`> .foo`) and selectors with invalid combinators that are still supported for backwards-compatibility reasons
    * (`.foo + ~ .bar`).
    */
  def isBogus: Boolean =
    accept(Selector.IsBogusVisitor(includeLeadingCombinator = true))

  /** Whether this selector is bogus other than having a leading combinator. */
  def isBogusOtherThanLeadingCombinator: Boolean =
    accept(Selector.IsBogusVisitor(includeLeadingCombinator = false))

  /** Whether this is a useless selector (that is, it's bogus _and_ it can't be transformed into valid CSS by `@extend` or nesting).
    */
  def isUseless: Boolean =
    accept(Selector.IsUselessVisitor)

  /** Calls the appropriate visit method on `visitor`. */
  def accept[T](visitor: SelectorVisitor[T]): T

  override def toString: String = accept(Selector.ToStringVisitor)
}

object Selector {

  /** Visitor that checks whether a selector is invisible. */
  final private class IsInvisibleVisitor(val includeBogus: Boolean) extends AnySelectorVisitor {

    override def visitSelectorList(list: SelectorList): Boolean =
      list.components.forall(visitComplexSelector)

    override def visitComplexSelector(complex: ComplexSelector): Boolean =
      super.visitComplexSelector(complex) ||
        (includeBogus && complex.isBogusOtherThanLeadingCombinator)

    override def visitPlaceholderSelector(placeholder: PlaceholderSelector): Boolean = true

    override def visitPseudoSelector(pseudo: PseudoSelector): Boolean = {
      val sel = pseudo.selector
      if (sel.isDefined) {
        val s = sel.get
        // We don't consider `:not(%foo)` to be invisible because, semantically,
        // it means "doesn't match this selector that matches nothing", so it's
        // equivalent to *. If the entire compound selector is composed of `:not`s
        // with invisible lists, the serializer emits it as `*`.
        if (pseudo.name == "not") includeBogus && s.isBogus
        else s.accept(this)
      } else {
        false
      }
    }
  }

  /** Visitor that checks whether a selector is bogus. */
  final private class IsBogusVisitor(val includeLeadingCombinator: Boolean) extends AnySelectorVisitor {

    override def visitComplexSelector(complex: ComplexSelector): Boolean =
      if (complex.components.isEmpty) {
        complex.leadingCombinators.nonEmpty
      } else {
        complex.leadingCombinators.length >
          (if (includeLeadingCombinator) 0 else 1) ||
          complex.components.last.combinators.nonEmpty ||
          complex.components.exists { component =>
            component.combinators.length > 1 ||
            component.selector.accept(this)
          }
      }

    override def visitPseudoSelector(pseudo: PseudoSelector): Boolean =
      if (pseudo.selector.isEmpty) false
      else {
        val sel = pseudo.selector.get
        // The CSS spec specifically allows leading combinators in `:has()`.
        if (pseudo.name == "has") sel.isBogusOtherThanLeadingCombinator
        else sel.isBogus
      }
  }

  /** Visitor that checks whether a selector is useless. */
  private object IsUselessVisitor extends AnySelectorVisitor {

    override def visitComplexSelector(complex: ComplexSelector): Boolean =
      complex.leadingCombinators.length > 1 ||
        complex.components.exists { component =>
          component.combinators.length > 1 || component.selector.accept(this)
        }

    override def visitPseudoSelector(pseudo: PseudoSelector): Boolean = pseudo.isBogus
  }

  /** Visitor that checks whether a selector contains a parent selector. */
  private object ContainsParentSelectorVisitor extends AnySelectorVisitor {
    override def visitParentSelector(parent: ParentSelector): Boolean = true
  }

  /** Simple toString visitor that produces debug output. This is a temporary debug serializer; the real serializer lives in the visitor package.
    */
  private object ToStringVisitor extends SelectorVisitor[String] {
    def visitAttributeSelector(attribute: AttributeSelector): String = {
      val sb = new StringBuilder("[")
      sb.append(attribute.name.toString)
      if (attribute.op.isDefined) {
        sb.append(attribute.op.get.text)
        val v = attribute.value.getOrElse("")
        // Quote the value if it's not a valid CSS identifier or starts with
        // "--" (IE11 compat). Matches dart-sass visitAttributeSelector.
        if (v.nonEmpty && ssg.sass.parse.Parser.isIdentifier(v) && !v.startsWith("--")) {
          sb.append(v)
        } else {
          sb.append('"')
          sb.append(v.replace("\\", "\\\\").replace("\"", "\\\""))
          sb.append('"')
        }
        attribute.modifier.foreach { m =>
          sb.append(" ")
          sb.append(m)
        }
      }
      sb.append("]")
      sb.toString()
    }

    def visitClassSelector(klass: ClassSelector): String = s".${klass.name}"

    def visitComplexSelector(complex: ComplexSelector): String = {
      val sb = new StringBuilder()
      for (c <- complex.leadingCombinators) {
        sb.append(c.value.text)
        sb.append(" ")
      }
      val parts = complex.components.map { component =>
        val sel   = component.selector.accept(this)
        val combs = component.combinators.map(c => s" ${c.value.text}").mkString
        s"$sel$combs"
      }
      sb.append(parts.mkString(" "))
      sb.toString()
    }

    def visitCompoundSelector(compound: CompoundSelector): String =
      compound.components.map(_.accept(this)).mkString

    def visitIDSelector(id: IDSelector): String = s"#${id.name}"

    def visitParentSelector(parent: ParentSelector): String =
      "&" + parent.suffix.getOrElse("")

    def visitPlaceholderSelector(placeholder: PlaceholderSelector): String =
      s"%${placeholder.name}"

    def visitPseudoSelector(pseudo: PseudoSelector): String = {
      val sb = new StringBuilder()
      sb.append(if (pseudo.isSyntacticClass) ":" else "::")
      sb.append(pseudo.name)
      if (pseudo.argument.isDefined || pseudo.selector.isDefined) {
        sb.append("(")
        pseudo.argument.foreach { arg =>
          // Normalize An+B microsyntax for :nth-child/:nth-last-child:
          // remove spaces around + and - (e.g. "2n + 1" → "2n+1")
          val nn = pseudo.normalizedName
          if ((nn == "nth-child" || nn == "nth-last-child" || nn == "nth-of-type" || nn == "nth-last-of-type") && arg.contains('n')) {
            val ofIdx = arg.indexOf(" of")
            val (anb, rest) = if (ofIdx >= 0) (arg.substring(0, ofIdx), arg.substring(ofIdx)) else (arg, "")
            sb.append(anb.replaceAll("\\s*\\+\\s*", "+").replaceAll("\\s*-\\s*", "-"))
            sb.append(rest)
          } else {
            sb.append(arg)
          }
        }
        if (pseudo.argument.isDefined && pseudo.selector.isDefined) sb.append(" ")
        pseudo.selector.foreach(s => sb.append(s.accept(this)))
        sb.append(")")
      }
      sb.toString()
    }

    def visitSelectorList(list: SelectorList): String =
      list.components.map(_.accept(this)).mkString(", ")

    def visitTypeSelector(tpe: TypeSelector): String = tpe.name.toString

    def visitUniversalSelector(universal: UniversalSelector): String =
      if (universal.namespace.isDefined) s"${universal.namespace.get}|*"
      else "*"
  }
}

/** A combinator that defines the relationship between selectors in a [[ComplexSelector]].
  */
enum Combinator(val text: String) extends java.lang.Enum[Combinator] {

  /** Matches the right-hand selector if it's immediately adjacent to the left-hand selector in the DOM tree.
    */
  case NextSibling extends Combinator("+")

  /** Matches the right-hand selector if it's a direct child of the left-hand selector in the DOM tree.
    */
  case Child extends Combinator(">")

  /** Matches the right-hand selector if it comes after the left-hand selector in the DOM tree.
    */
  case FollowingSibling extends Combinator("~")

  override def toString: String = text
}

/** Forward-declaration trait for selector visitors.
  *
  * The full visitor hierarchy (AnySelectorVisitor, SelectorSearchVisitor, etc.) will be ported separately; this trait defines the interface needed by the AST nodes themselves.
  */
trait SelectorVisitor[T] {
  def visitAttributeSelector(attribute:     AttributeSelector):   T
  def visitClassSelector(klass:             ClassSelector):       T
  def visitComplexSelector(complex:         ComplexSelector):     T
  def visitCompoundSelector(compound:       CompoundSelector):    T
  def visitIDSelector(id:                   IDSelector):          T
  def visitParentSelector(parent:           ParentSelector):      T
  def visitPlaceholderSelector(placeholder: PlaceholderSelector): T
  def visitPseudoSelector(pseudo:           PseudoSelector):      T
  def visitSelectorList(list:               SelectorList):        T
  def visitTypeSelector(tpe:                TypeSelector):        T
  def visitUniversalSelector(universal:     UniversalSelector):   T
}

/** A mixin-style base for selector visitors that returns false by default.
  *
  * Concrete visitors override specific methods to return true for matching selectors. The default implementations traverse into compound/complex selectors so that any nested match is found.
  */
trait AnySelectorVisitor extends SelectorVisitor[Boolean] {
  def visitAttributeSelector(attribute: AttributeSelector): Boolean = false
  def visitClassSelector(klass:         ClassSelector):     Boolean = false

  def visitComplexSelector(complex: ComplexSelector): Boolean =
    complex.components.exists(_.selector.accept(this))

  def visitCompoundSelector(compound: CompoundSelector): Boolean =
    compound.components.exists(_.accept(this))

  def visitIDSelector(id:                   IDSelector):          Boolean = false
  def visitParentSelector(parent:           ParentSelector):      Boolean = false
  def visitPlaceholderSelector(placeholder: PlaceholderSelector): Boolean = false

  def visitPseudoSelector(pseudo: PseudoSelector): Boolean =
    pseudo.selector.exists(_.accept(this))

  def visitSelectorList(list: SelectorList): Boolean =
    list.components.exists(_.accept(this))

  def visitTypeSelector(tpe:            TypeSelector):      Boolean = false
  def visitUniversalSelector(universal: UniversalSelector): Boolean = false
}
