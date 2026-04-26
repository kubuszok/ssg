/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/recursive_selector.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: recursive_selector.dart -> RecursiveSelectorVisitor.scala
 *   Convention: Dart mixin -> Scala trait extending SelectorVisitor[Unit]
 *   Idiom: Default impls recurse into nested selectors
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/visitor/recursive_selector.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package visitor

import ssg.sass.ast.selector.*

/** A visitor that recursively traverses each selector in a Sass selector AST.
  *
  * The default implementations of compound, complex, list, and pseudo selectors recurse into their components; simple selectors are no-ops.
  */
trait RecursiveSelectorVisitor extends SelectorVisitor[Unit] {

  def visitAttributeSelector(attribute:     AttributeSelector):   Unit = ()
  def visitClassSelector(klass:             ClassSelector):       Unit = ()
  def visitIDSelector(id:                   IDSelector):          Unit = ()
  def visitParentSelector(parent:           ParentSelector):      Unit = ()
  def visitPlaceholderSelector(placeholder: PlaceholderSelector): Unit = ()
  def visitTypeSelector(tpe:                TypeSelector):        Unit = ()
  def visitUniversalSelector(universal:     UniversalSelector):   Unit = ()

  def visitComplexSelector(complex: ComplexSelector): Unit =
    complex.components.foreach(c => visitCompoundSelector(c.selector))

  def visitCompoundSelector(compound: CompoundSelector): Unit =
    compound.components.foreach(_.accept(this))

  def visitPseudoSelector(pseudo: PseudoSelector): Unit =
    pseudo.selector.foreach(_.accept(this))

  def visitSelectorList(list: SelectorList): Unit =
    list.components.foreach(_.accept(this))
}
