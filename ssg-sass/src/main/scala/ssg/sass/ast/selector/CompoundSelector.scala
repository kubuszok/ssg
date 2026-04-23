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
 *          isSuperselector delegated to ExtendFunctions
 */
package ssg
package sass
package ast
package selector

import ssg.sass.Nullable
import ssg.sass.Utils
import ssg.sass.extend.ExtendFunctions
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
    * Port of dart-sass `compoundIsSuperselector` in lib/src/extend/functions.dart. Dispatches three ways:
    *
    *   1. Fast path — if neither side has "complicated" pseudo semantics (i.e. no pseudo-element and no pseudo-class with a selector argument), fall back to the simple "every component of this is a
    *      superselector of some component of other" check.
    *   2. Pseudo-element check — if either side has a pseudo-element, both must have the SAME pseudo-element at matching positions, and the pre- and post-pseudo-element slices must each be
    *      superselectors.
    *   3. Mixed path — for each simple in this, if it's a PseudoSelector with a non-empty inner selector, dispatch to [[CompoundSelector.selectorPseudoIsSuperselector]] (the port of dart-sass
    *      `_selectorPseudoIsSuperselector`). Otherwise fall back to the per-simple isSuperselector check.
    *
    * The `parents` parameter represents the parents of `other` in the containing complex selector, used by pseudo selectors with selector arguments that need to know the surrounding context (e.g.
    * `:is(a .b)` needs to see the parent chain to know if it matches a descendant shape).
    */
  def isSuperselector(
    other:   CompoundSelector,
    parents: Nullable[List[ComplexSelectorComponent]] = Nullable.empty
  ): Boolean = {
    if (!hasComplicatedSuperselectorSemantics && !other.hasComplicatedSuperselectorSemantics) {
      if (components.length > other.components.length) return false
      return components.forall { simple1 =>
        other.components.exists(simple1.isSuperselector)
      }
    }
    // Pseudo-element check. dart-sass `_findPseudoElementIndexed`.
    val pe1 = CompoundSelector.findPseudoElementIndexed(this)
    val pe2 = CompoundSelector.findPseudoElementIndexed(other)
    (pe1.toOption, pe2.toOption) match {
      case (Some((pseudo1, index1)), Some((pseudo2, index2))) =>
        return pseudo1.isSuperselector(pseudo2) &&
          CompoundSelector.compoundComponentsIsSuperselector(
            components.take(index1),
            other.components.take(index2),
            parents = parents
          ) &&
          CompoundSelector.compoundComponentsIsSuperselector(
            components.drop(index1 + 1),
            other.components.drop(index2 + 1),
            parents = parents
          )
      case (Some(_), None) | (None, Some(_)) =>
        return false
      case (None, None) => ()
    }
    // Mixed path. Every simple in this must match.
    components.forall { simple1 =>
      simple1 match {
        case p: PseudoSelector if p.selector.isDefined =>
          CompoundSelector.selectorPseudoIsSuperselector(p, other, parents)
        case _ =>
          other.components.exists(simple1.isSuperselector)
      }
    }
  }

  override def hashCode(): Int = Utils.iterableHash(components)

  override def equals(other: Any): Boolean = other match {
    case that: CompoundSelector => Utils.iterableEquals(components, that.components)
    case _ => false
  }
}

object CompoundSelector {

  /** If [compound] contains a pseudo-element, returns it and its index within `compound.components`. Port of dart-sass `_findPseudoElementIndexed`.
    */
  private[selector] def findPseudoElementIndexed(
    compound: CompoundSelector
  ): Nullable[(PseudoSelector, Int)] = {
    var i = 0
    while (i < compound.components.length) {
      compound.components(i) match {
        case p: PseudoSelector if p.isElement =>
          return Nullable((p, i))
        case _ => ()
      }
      i += 1
    }
    Nullable.empty
  }

  /** Like `compoundIsSuperselector` but operates on the raw simple- selector lists produced by slicing around a pseudo-element. Port of dart-sass `_compoundComponentsIsSuperselector`.
    *
    * If both slices are empty, the comparison is trivially true. If only the right side is empty, we substitute a universal selector so the recursive call has a valid compound to compare against.
    */
  private[selector] def compoundComponentsIsSuperselector(
    compound1: List[SimpleSelector],
    compound2: List[SimpleSelector],
    parents:   Nullable[List[ComplexSelectorComponent]]
  ): Boolean = {
    if (compound1.isEmpty) return true
    val rhs =
      if (compound2.nonEmpty) compound2
      else List(UniversalSelector(compound1.head.span, namespace = Nullable("*")))
    val c1 = new CompoundSelector(compound1, compound1.head.span)
    val c2 = new CompoundSelector(rhs, rhs.head.span)
    c1.isSuperselector(c2, parents)
  }

  /** Port of dart-sass `_selectorPseudoIsSuperselector` in lib/src/extend/functions.dart.
    *
    * Given a pseudo selector `pseudo1` with a non-empty inner selector and a compound selector `compound2`, return whether `pseudo1` matches every element that `compound2` matches. The dispatch is
    * keyed by `pseudo1.normalizedName`:
    *
    *   - `is` / `matches` / `any` / `where`: `pseudo1` matches if any same-named pseudo in `compound2` has an inner selector that's a subset, OR if any complex in `pseudo1`'s inner selector is a
    *     complex-superselector of `parents + compound2`.
    *   - `has` / `host` / `host-context`: `pseudo1` matches if any same-named pseudo in `compound2` has an inner selector that's a subset of `pseudo1`'s inner.
    *   - `slotted` (pseudo-element): same, keyed on `isClass == false`.
    *   - `not`: inverts — every complex in `pseudo1`'s inner selector must collide with SOMETHING in `compound2` (either a TypeSelector / IDSelector of the same kind but different value, or a
    *     same-named `:not(...)` pseudo whose inner list is a superselector of the complex under consideration).
    *   - `current`: any same-named pseudo in `compound2` with an equal inner selector.
    *   - `nth-child` / `nth-last-child`: any same-named pseudo in `compound2` with the same argument and a subset inner selector.
    *
    * Non-matching pseudo names return false (the dart code throws "unreachable" because the caller guarantees the dispatch covers every name that reaches here; ssg-sass returns false defensively so a
    * new pseudo name doesn't crash the compare).
    */
  private[selector] def selectorPseudoIsSuperselector(
    pseudo1:   PseudoSelector,
    compound2: CompoundSelector,
    parents:   Nullable[List[ComplexSelectorComponent]]
  ): Boolean = {
    val selector1 = pseudo1.selector.get // caller guarantees non-empty

    pseudo1.normalizedName match {
      case "is" | "matches" | "any" | "where" =>
        val args = selectorPseudoArgs(compound2, pseudo1.name, isClass = true)
        args.exists(selector2 => selector1.isSuperselector(selector2)) ||
        selector1.components.exists { complex1 =>
          complex1.leadingCombinators.isEmpty && {
            val parentsList = parents.toOption.getOrElse(Nil)
            val suffix      = new ComplexSelectorComponent(compound2, Nil, compound2.span)
            val target      = parentsList :+ suffix
            ExtendFunctions.complexIsSuperselector(complex1.components, target)
          }
        }

      case "has" | "host" | "host-context" =>
        val args = selectorPseudoArgs(compound2, pseudo1.name, isClass = true)
        args.exists(selector2 => selector1.isSuperselector(selector2))

      case "slotted" =>
        val args = selectorPseudoArgs(compound2, pseudo1.name, isClass = false)
        args.exists(selector2 => selector1.isSuperselector(selector2))

      case "not" =>
        selector1.components.forall { complex =>
          if (complex.isBogus) false
          else {
            complex.components.lastOption match {
              case None                => false
              case Some(lastComponent) =>
                compound2.components.exists { simple2 =>
                  simple2 match {
                    case t2: TypeSelector =>
                      lastComponent.selector.components.exists {
                        case t1: TypeSelector => t1 != t2
                        case _ => false
                      }
                    case id2: IDSelector =>
                      lastComponent.selector.components.exists {
                        case id1: IDSelector => id1 != id2
                        case _ => false
                      }
                    case p2: PseudoSelector if p2.name == pseudo1.name && p2.selector.isDefined =>
                      p2.selector.get.isSuperselector(
                        new SelectorList(List(complex), complex.span)
                      )
                    case _ => false
                  }
                }
            }
          }
        }

      case "current" =>
        val args = selectorPseudoArgs(compound2, pseudo1.name, isClass = true)
        args.exists(_ == selector1)

      case "nth-child" | "nth-last-child" =>
        compound2.components.exists {
          case p2: PseudoSelector
              if p2.name == pseudo1.name
                && p2.argument == pseudo1.argument
                && p2.selector.isDefined =>
            selector1.isSuperselector(p2.selector.get)
          case _ => false
        }

      case _ =>
        // Unknown pseudo name — conservative false. dart-sass throws
        // "unreachable" here because the caller filters to
        // selectorPseudoClasses, but returning false keeps ssg-sass
        // from crashing on new pseudos that haven't been added to
        // the dispatch table.
        false
    }
  }

  /** Returns the selector arguments of every pseudo-class selector in [compound] whose name equals [name]. Port of dart-sass `_selectorPseudoArgs`.
    */
  private def selectorPseudoArgs(
    compound: CompoundSelector,
    name:     String,
    isClass:  Boolean
  ): List[SelectorList] =
    compound.components.collect {
      case p: PseudoSelector if p.isClass == isClass && p.name == name && p.selector.isDefined =>
        p.selector.get
    }

}
