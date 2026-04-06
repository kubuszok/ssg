/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/selector/list.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: list.dart -> SelectorList.scala
 *   Convention: Dart final class -> Scala final class
 *   Idiom: Nullable parent in nestWithin; _containsParentSelector/_ParentSelectorVisitor
 *          -> companion object; flattenVertically -> Utils.flattenVertically;
 *          nestWithin complex logic preserved; asSassList stubbed (needs Value types)
 */
package ssg
package sass
package ast
package selector

import ssg.sass.{ MultiSpanSassException, Nullable, SassException, Utils }
import ssg.sass.Nullable.*
import ssg.sass.ast.css.CssValue
import ssg.sass.util.FileSpan
import ssg.sass.value.{ ListSeparator, SassList => SassListValue, SassString, Value }

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** A selector list.
  *
  * A selector list is composed of [[ComplexSelector]]s. It matches any element that matches any of the component selectors.
  */
final class SelectorList(
  val components: List[ComplexSelector],
  span:           FileSpan
) extends Selector(span) {

  require(components.nonEmpty, "components may not be empty.")

  /** Returns a SassScript list that represents this selector.
    *
    * This has the same format as a list returned by `selector-parse()`.
    */
  def asSassList: SassListValue =
    SassListValue(
      components.map { complex =>
        val parts = scala.collection.mutable.ListBuffer.empty[Value]
        for (combinator <- complex.leadingCombinators)
          parts += SassString(combinator.toString, hasQuotes = false)
        for (component <- complex.components) {
          parts += SassString(component.selector.toString, hasQuotes = false)
          for (combinator <- component.combinators)
            parts += SassString(combinator.toString, hasQuotes = false)
        }
        SassListValue(parts.toList, ListSeparator.Space)
      },
      ListSeparator.Comma
    )

  override def accept[T](visitor: SelectorVisitor[T]): T =
    visitor.visitSelectorList(this)

  /** Returns a [[SelectorList]] that matches only elements that are matched by both this and `other`.
    *
    * Returns `Nullable.Null` if no such list can be produced.
    *
    * Note: The full implementation requires `unifyComplex` from the extend functions module, which will be ported separately. This implementation handles the common case where every complex selector
    * is a single compound selector — pairs of compounds are unified via [[SimpleSelector.unify]] and the cross-product results are returned.
    */
  def unify(other: SelectorList): Nullable[SelectorList] = {
    val unified = scala.collection.mutable.ListBuffer.empty[ComplexSelector]
    for {
      a <- components
      b <- other.components
    } {
      val aSingle = a.singleCompound
      val bSingle = b.singleCompound
      if (aSingle.isDefined && bSingle.isDefined) {
        val merged = SelectorList.unifyCompounds(aSingle.get, bSingle.get)
        if (merged.isDefined) {
          unified += new ComplexSelector(
            Nil,
            List(new ComplexSelectorComponent(merged.get, Nil, span)),
            span
          )
        }
      }
    }
    if (unified.isEmpty) Nullable.Null
    else Nullable(SelectorList(unified.toList, span))
  }

  /** Returns a new selector list that represents `this` nested within `parent`.
    *
    * By default, this replaces [[ParentSelector]]s in `this` with `parent`. If `preserveParentSelectors` is true, this instead preserves those selectors as parent selectors.
    *
    * If `implicitParent` is true, this prepends `parent` to any [[ComplexSelector]]s in this that don't contain explicit [[ParentSelector]]s, or to _all_ [[ComplexSelector]]s if
    * `preserveParentSelectors` is true.
    *
    * The given `parent` may be `Nullable.Null`, indicating that this has no parents. If so, this list is returned as-is if it doesn't contain any explicit [[ParentSelector]]s or if
    * `preserveParentSelectors` is true. Otherwise, this throws a [[SassException]].
    */
  def nestWithin(
    parent:                  Nullable[SelectorList],
    implicitParent:          Boolean = true,
    preserveParentSelectors: Boolean = false
  ): SelectorList =
    if (parent.isEmpty) {
      if (preserveParentSelectors) this
      else {
        val parentSelector = SelectorList.findParentSelector(this)
        if (parentSelector.isEmpty) this
        else {
          throw SassException(
            "Top-level selectors may not contain the parent selector \"&\".",
            parentSelector.get.span
          )
        }
      }
    } else {
      val parentList = parent.get
      SelectorList(
        Utils.flattenVertically(
          components.map { complex =>
            if (preserveParentSelectors || !SelectorList.containsParentSelector(complex)) {
              if (!implicitParent) List(complex)
              else {
                parentList.components.map { parentComplex =>
                  parentComplex.concatenate(complex, complex.span)
                }
              }
            } else {
              nestWithinComplex(complex, parentList)
            }
          }
        ),
        span
      )
    }

  /** Returns a new selector list based on `component` with all [[ParentSelector]]s replaced with `parent`.
    *
    * Returns the result for each complex component.
    */
  private def nestWithinComplex(
    complex: ComplexSelector,
    parent:  SelectorList
  ): List[ComplexSelector] = {
    var newComplexes = ArrayBuffer.empty[ComplexSelector]

    for (component <- complex.components) {
      val resolved = nestWithinCompound(component, parent)
      if (resolved.isEmpty) {
        if (newComplexes.isEmpty) {
          newComplexes += ComplexSelector(
            complex.leadingCombinators,
            List(component),
            complex.span,
            lineBreak = false
          )
        } else {
          var i = 0
          while (i < newComplexes.length) {
            newComplexes(i) = newComplexes(i).withAdditionalComponent(
              component,
              complex.span
            )
            i += 1
          }
        }
      } else {
        val resolvedList = resolved.get.toList
        if (newComplexes.isEmpty) {
          if (complex.leadingCombinators.isEmpty) {
            newComplexes ++= resolvedList
          } else {
            newComplexes ++= resolvedList.map { resolvedComplex =>
              ComplexSelector(
                if (resolvedComplex.leadingCombinators.isEmpty) complex.leadingCombinators
                else complex.leadingCombinators ++ resolvedComplex.leadingCombinators,
                resolvedComplex.components,
                complex.span,
                lineBreak = resolvedComplex.lineBreak
              )
            }
          }
        } else {
          val previousComplexes = newComplexes.toList
          newComplexes = ArrayBuffer.empty[ComplexSelector]
          for {
            newComplex <- previousComplexes
            resolvedComplex <- resolvedList
          }
            newComplexes += newComplex.concatenate(resolvedComplex, newComplex.span)
        }
      }
    }

    newComplexes.toList
  }

  /** Returns a new selector list based on `component` with all [[ParentSelector]]s replaced with `parent`.
    *
    * Returns `Nullable.Null` if `component` doesn't contain any [[ParentSelector]]s.
    */
  private def nestWithinCompound(
    component: ComplexSelectorComponent,
    parent:    SelectorList
  ): Nullable[Iterable[ComplexSelector]] = {
    val simples                = component.selector.components
    val containsSelectorPseudo = simples.exists {
      case p: PseudoSelector =>
        p.selector.isDefined && SelectorList.containsParentSelector(p.selector.get)
      case _ => false
    }
    if (!containsSelectorPseudo && !simples.head.isInstanceOf[ParentSelector]) {
      Nullable.Null
    } else {
      val resolvedSimples: List[SimpleSelector] =
        if (containsSelectorPseudo) {
          simples.map {
            case p: PseudoSelector
                if p.selector.isDefined &&
                  SelectorList.containsParentSelector(p.selector.get) =>
              p.withSelector(p.selector.get.nestWithin(Nullable(parent), implicitParent = false))
            case other => other
          }
        } else {
          simples
        }

      val parentSelector = simples.head
      try
        parentSelector match {
          case ps: ParentSelector =>
            if (simples.length == 1 && ps.suffix.isEmpty) {
              Nullable(
                withAdditionalCombinators(component.combinators).components
              )
            } else {
              Nullable(
                parent.components.map { complex =>
                  try {
                    val lastComponent = complex.components.last
                    if (lastComponent.combinators.nonEmpty) {
                      throw MultiSpanSassException(
                        s"""Selector "$complex" can't be used as a parent in a compound selector.""",
                        lastComponent.span,
                        "outer selector",
                        Map(parentSelector.span -> "parent selector")
                      )
                    }

                    val suffix      = ps.suffix
                    val lastSimples = lastComponent.selector.components
                    val last        = CompoundSelector(
                      if (suffix.isEmpty) {
                        lastSimples ++ resolvedSimples.tail
                      } else {
                        val s = suffix.get
                        lastSimples.init :+ lastSimples.last.addSuffix(s) match {
                          case adjusted => adjusted ++ resolvedSimples.tail
                        }
                      },
                      component.selector.span
                    )

                    ComplexSelector(
                      complex.leadingCombinators,
                      complex.components.init :+ ComplexSelectorComponent(
                        last,
                        component.combinators,
                        component.span
                      ),
                      component.span,
                      lineBreak = complex.lineBreak
                    )
                  } catch {
                    case e: SassException =>
                      throw e.withAdditionalSpan(parentSelector.span, "parent selector")
                  }
                }
              )
            }
          case _ =>
            Nullable(
              List(
                ComplexSelector(
                  Nil,
                  List(
                    ComplexSelectorComponent(
                      CompoundSelector(resolvedSimples, component.selector.span),
                      component.combinators,
                      component.span
                    )
                  ),
                  component.span
                )
              )
            )
        }
      catch {
        case e: SassException =>
          throw e.withAdditionalSpan(parentSelector.span, "parent selector")
      }
    }
  }

  /** Whether this is a superselector of `other`.
    *
    * That is, whether this matches every element that `other` matches, as well as possibly additional elements.
    *
    * Note: The full implementation delegates to `listIsSuperselector` in the extend functions module, which will be ported separately.
    */
  def isSuperselector(other: SelectorList): Boolean =
    // Basic implementation: every component of other must be superselectored
    // by at least one component of this
    other.components.forall { otherComplex =>
      components.exists(_.isSuperselector(otherComplex))
    }

  /** Returns a copy of `this` with `combinators` added to the end of each complex selector in [[components]].
    */
  def withAdditionalCombinators(
    combinators: List[CssValue[Combinator]]
  ): SelectorList =
    if (combinators.isEmpty) this
    else
      SelectorList(
        components.map(_.withAdditionalCombinators(combinators)),
        span
      )

  override def hashCode(): Int = Utils.iterableHash(components)

  override def equals(other: Any): Boolean = other match {
    case that: SelectorList => Utils.iterableEquals(components, that.components)
    case _ => false
  }
}

object SelectorList {

  /** Unifies two compound selectors into a single compound selector that matches every element matched by both. Returns `Nullable.Null` if unification is impossible.
    *
    * The simple selectors of `right` are folded into `left` one at a time using each simple's [[SimpleSelector.unify]] hook.
    */
  private[selector] def unifyCompounds(
    left:  CompoundSelector,
    right: CompoundSelector
  ): Nullable[CompoundSelector] =
    boundary[Nullable[CompoundSelector]] {
      var current: List[SimpleSelector] = left.components
      val it = right.components.iterator
      while (it.hasNext) {
        val next   = it.next()
        val merged = next.unify(current)
        if (merged.isEmpty) break(Nullable.Null)
        current = merged.get
      }
      if (current.isEmpty) Nullable.Null
      else Nullable(new CompoundSelector(current, left.span))
    }

  /** Returns whether `selector` recursively contains a parent selector. */
  private[selector] def containsParentSelector(selector: Selector): Boolean =
    findParentSelector(selector).isDefined

  /** A visitor for finding the first [[ParentSelector]] in a given selector.
    *
    * Returns `Nullable.Null` if no parent selector is found.
    */
  private[selector] def findParentSelector(selector: Selector): Nullable[ParentSelector] =
    selector.accept(FindParentSelectorVisitor)

  /** Visitor that searches for a [[ParentSelector]]. */
  private object FindParentSelectorVisitor extends SelectorVisitor[Nullable[ParentSelector]] {

    def visitAttributeSelector(attribute:     AttributeSelector):   Nullable[ParentSelector] = Nullable.Null
    def visitClassSelector(klass:             ClassSelector):       Nullable[ParentSelector] = Nullable.Null
    def visitIDSelector(id:                   IDSelector):          Nullable[ParentSelector] = Nullable.Null
    def visitTypeSelector(tpe:                TypeSelector):        Nullable[ParentSelector] = Nullable.Null
    def visitUniversalSelector(universal:     UniversalSelector):   Nullable[ParentSelector] = Nullable.Null
    def visitPlaceholderSelector(placeholder: PlaceholderSelector): Nullable[ParentSelector] = Nullable.Null

    def visitParentSelector(parent: ParentSelector): Nullable[ParentSelector] =
      Nullable(parent)

    def visitPseudoSelector(pseudo: PseudoSelector): Nullable[ParentSelector] =
      pseudo.selector.flatMap(_.accept(this))

    def visitCompoundSelector(compound: CompoundSelector): Nullable[ParentSelector] =
      boundary[Nullable[ParentSelector]] {
        for (component <- compound.components) {
          val result = component.accept(this)
          if (result.isDefined) break(result)
        }
        Nullable.Null
      }

    def visitComplexSelector(complex: ComplexSelector): Nullable[ParentSelector] =
      boundary[Nullable[ParentSelector]] {
        for (component <- complex.components) {
          val result = component.selector.accept(this)
          if (result.isDefined) break(result)
        }
        Nullable.Null
      }

    def visitSelectorList(list: SelectorList): Nullable[ParentSelector] =
      boundary[Nullable[ParentSelector]] {
        for (component <- list.components) {
          val result = component.accept(this)
          if (result.isDefined) break(result)
        }
        Nullable.Null
      }
  }
}
