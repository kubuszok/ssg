/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/selector/pseudo.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: pseudo.dart -> PseudoSelector.scala
 *   Convention: Dart final class -> Scala final class
 *   Idiom: Nullable selector/argument; _isFakePseudoElement -> companion;
 *          charcode comparisons -> CharCode constants
 */
package ssg
package sass
package ast
package selector

import ssg.sass.{ Nullable, Utils }
import ssg.sass.Nullable.*
import ssg.sass.util.{ CharCode, FileSpan }

import scala.language.implicitConversions

/** A pseudo-class or pseudo-element selector.
  *
  * The semantics of a specific pseudo selector depends on its name. Some selectors take arguments, including other selectors. Sass manually encodes logic for each pseudo selector that takes a
  * selector as an argument, to ensure that extension and other selector operations work properly.
  */
final class PseudoSelector(
  val name:     String,
  span:         FileSpan,
  element:      Boolean = false,
  val argument: Nullable[String] = Nullable.Null,
  val selector: Nullable[SelectorList] = Nullable.Null
) extends SimpleSelector(span) {

  /** Like [[name]], but without any vendor prefixes. */
  val normalizedName: String = Utils.unvendor(name)

  /** Whether this is a pseudo-class selector.
    *
    * This is `true` if and only if [[isElement]] is `false`.
    */
  val isClass: Boolean = !element && !PseudoSelector.isFakePseudoElement(name)

  /** Whether this is syntactically a pseudo-class selector.
    *
    * This is the same as [[isClass]] unless this selector is a pseudo-element that was written syntactically as a pseudo-class (`:before`, `:after`, `:first-line`, or `:first-letter`).
    *
    * This is `true` if and only if [[isSyntacticElement]] is `false`.
    */
  val isSyntacticClass: Boolean = !element

  /** Whether this is a pseudo-element selector.
    *
    * This is `true` if and only if [[isClass]] is `false`.
    */
  def isElement: Boolean = !isClass

  /** Whether this is syntactically a pseudo-element selector.
    *
    * This is `true` if and only if [[isSyntacticClass]] is `false`.
    */
  def isSyntacticElement: Boolean = !isSyntacticClass

  /** Whether this is a valid `:host` selector. */
  def isHost: Boolean = isClass && name == "host"

  /** Whether this is a valid `:host-context` selector. */
  def isHostContext: Boolean =
    isClass && name == "host-context" && selector.isDefined

  override def hasComplicatedSuperselectorSemantics: Boolean =
    isElement || selector.isDefined

  override lazy val specificity: Int =
    if (isElement) 1
    else if (selector.isEmpty) super.specificity
    else {
      val sel = selector.get
      // https://drafts.csswg.org/selectors/#specificity-rules
      normalizedName match {
        case "where"                          => 0
        case "is" | "not" | "has" | "matches" =>
          sel.components.map(_.specificity).max
        case "nth-child" | "nth-last-child" =>
          super.specificity +
            sel.components.map(_.specificity).max
        case _ => super.specificity
      }
    }

  /** Returns a new [[PseudoSelector]] based on this, but with the selector replaced with `newSelector`.
    */
  def withSelector(newSelector: SelectorList): PseudoSelector =
    PseudoSelector(name, span, element = isElement, argument = argument, selector = Nullable(newSelector))

  override def addSuffix(suffix: String): PseudoSelector = {
    if (argument.isDefined || selector.isDefined) super.addSuffix(suffix)
    PseudoSelector(name + suffix, span, element = isElement)
  }

  override def unify(compound: List[SimpleSelector]): Nullable[List[SimpleSelector]] =
    if (name == "host" || name == "host-context") {
      if (
        !compound.forall {
          case p: PseudoSelector => p.isHost || p.selector.isDefined
          case _ => false
        }
      ) {
        Nullable.Null
      } else {
        super.unify(compound)
      }
    } else {
      compound match {
        case List(other: UniversalSelector) =>
          other.unify(List(this))
        case List(other: PseudoSelector) if other.isHost || other.isHostContext =>
          other.unify(List(this))
        case _ =>
          if (compound.contains(this)) Nullable(compound)
          else {
            val result    = scala.collection.mutable.ListBuffer.empty[SimpleSelector]
            var addedThis = false
            for (simple <- compound) {
              simple match {
                case p: PseudoSelector if p.isElement =>
                  // A given compound selector may only contain one pseudo element. If
                  // compound has a different one than this, unification fails.
                  if (isElement) Nullable.Null
                  else {
                    // Otherwise, this is a pseudo selector and should come before pseudo
                    // elements.
                    if (!addedThis) {
                      result += this
                      addedThis = true
                    }
                  }
                case _ => ()
              }
              result += simple
            }
            if (!addedThis) result += this
            Nullable(result.toList)
          }
      }
    }

  override def isSuperselector(other: SimpleSelector): Boolean =
    if (super.isSuperselector(other)) true
    else if (selector.isEmpty) this == other
    else {
      val sel = selector.get
      other match {
        case otherPseudo: PseudoSelector
            if isElement && otherPseudo.isElement &&
              normalizedName == "slotted" && otherPseudo.name == name =>
          otherPseudo.selector.exists(sel.isSuperselector)
        case _ =>
          // Fall back to the logic defined in functions, which knows how to
          // compare selector pseudoclasses against raw selectors.
          CompoundSelector(List(this), span).isSuperselector(CompoundSelector(List(other), span))
      }
    }

  override def accept[T](visitor: SelectorVisitor[T]): T =
    visitor.visitPseudoSelector(this)

  // This intentionally uses identity for the selector list, if one is available.
  override def equals(other: Any): Boolean = other match {
    case that: PseudoSelector =>
      that.name == name &&
      that.isClass == isClass &&
      that.argument == argument &&
      that.selector == selector
    case _ => false
  }

  override def hashCode(): Int =
    name.hashCode() ^
      isElement.hashCode() ^
      argument.hashCode() ^
      selector.hashCode()
}

object PseudoSelector {

  /** Returns whether `name` is the name of a pseudo-element that can be written with pseudo-class syntax (`:before`, `:after`, `:first-line`, or `:first-letter`).
    */
  private[selector] def isFakePseudoElement(name: String): Boolean =
    if (name.isEmpty) false
    else {
      val first = name.charAt(0).toInt
      first match {
        case CharCode.$a | CharCode.$A =>
          Utils.equalsIgnoreCase(name, "after")
        case CharCode.$b | CharCode.$B =>
          Utils.equalsIgnoreCase(name, "before")
        case CharCode.$f | CharCode.$F =>
          Utils.equalsIgnoreCase(name, "first-line") ||
          Utils.equalsIgnoreCase(name, "first-letter")
        case _ => false
      }
    }
}
