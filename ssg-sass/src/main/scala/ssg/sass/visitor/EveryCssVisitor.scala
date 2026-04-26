/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/every_css.dart
 * Original: Copyright (c) 2022 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: every_css.dart -> EveryCssVisitor.scala
 *   Convention: Dart mixin -> Scala trait
 *   Idiom: Parent nodes recurse via node.children.forall(_.accept(this));
 *          leaf nodes default to false.
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/visitor/every_css.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.*

/** A visitor that visits each statement in a CSS AST and returns `true` if all of the individual methods return `true`.
  *
  * Each method returns `false` by default.
  */
trait EveryCssVisitor extends CssVisitor[Boolean] {
  def visitCssAtRule(node: CssAtRule): Boolean =
    node.children.forall(_.accept(this))
  def visitCssComment(node:     CssComment):         Boolean = false
  def visitCssDeclaration(node: CssDeclaration):     Boolean = false
  def visitCssImport(node:      CssImport):          Boolean = false
  def visitCssKeyframeBlock(node: CssKeyframeBlock): Boolean =
    node.children.forall(_.accept(this))
  def visitCssMediaRule(node: CssMediaRule): Boolean =
    node.children.forall(_.accept(this))
  def visitCssStyleRule(node: CssStyleRule): Boolean =
    node.children.forall(_.accept(this))
  def visitCssStylesheet(node: CssStylesheet): Boolean =
    node.children.forall(_.accept(this))
  def visitCssSupportsRule(node: CssSupportsRule): Boolean =
    node.children.forall(_.accept(this))
}
