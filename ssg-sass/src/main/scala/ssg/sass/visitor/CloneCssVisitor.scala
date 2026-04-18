/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/clone_css.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: clone_css.dart -> CloneCssVisitor.scala
 *   Convention: Dart top-level function cloneCssStylesheet -> companion object method
 *   Idiom: _CloneCssVisitor is private to this file; public API is CloneCssVisitor.cloneCssStylesheet
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.*
import ssg.sass.ast.selector.SelectorList
import ssg.sass.extend.ExtensionStore
import ssg.sass.util.Box

/** Returns deep copies of both [stylesheet] and [extensionStore].
  *
  * The [extensionStore] must be associated with [stylesheet].
  */
object CloneCssVisitor {

  def cloneCssStylesheet(
    stylesheet:     CssStylesheet,
    extensionStore: ExtensionStore
  ): (ModifiableCssStylesheet, ExtensionStore) = {
    val (newExtensionStore, oldToNewSelectors) = extensionStore.cloneStore()
    val visitor = new CloneCssVisitor(oldToNewSelectors)
    (visitor.visitCssStylesheet(stylesheet), newExtensionStore)
  }
}

/** A visitor that creates a deep (and mutable) copy of a [CssStylesheet]. */
final class CloneCssVisitor private[visitor] (
  /** A map from selectors in the original stylesheet to selectors generated for
    * the new stylesheet using [ExtensionStore.cloneStore].
    */
  private val oldToNewSelectors: Map[SelectorList, Box[SelectorList]]
) extends CssVisitor[ModifiableCssNode] {

  def visitCssAtRule(node: CssAtRule): ModifiableCssAtRule = {
    val rule = new ModifiableCssAtRule(
      node.name,
      node.span,
      childless = node.isChildless,
      value = node.value
    )
    if (node.isChildless) rule else _visitChildren(rule, node)
  }

  def visitCssComment(node: CssComment): ModifiableCssComment =
    new ModifiableCssComment(node.text, node.span)

  def visitCssDeclaration(node: CssDeclaration): ModifiableCssDeclaration =
    new ModifiableCssDeclaration(
      node.name,
      node.value,
      node.span,
      parsedAsSassScript = node.parsedAsSassScript,
      valueSpanForMapOpt = Some(node.valueSpanForMap)
    )

  def visitCssImport(node: CssImport): ModifiableCssImport =
    new ModifiableCssImport(node.url, node.span, modifiers = node.modifiers)

  def visitCssKeyframeBlock(node: CssKeyframeBlock): ModifiableCssKeyframeBlock =
    _visitChildren(
      new ModifiableCssKeyframeBlock(node.selector, node.span),
      node
    )

  def visitCssMediaRule(node: CssMediaRule): ModifiableCssMediaRule =
    _visitChildren(new ModifiableCssMediaRule(node.queries, node.span), node)

  def visitCssStyleRule(node: CssStyleRule): ModifiableCssStyleRule = {
    oldToNewSelectors.get(node.selector) match {
      case Some(newSelector) =>
        _visitChildren(
          new ModifiableCssStyleRule(
            newSelector,
            node.span,
            originalSel = ssg.sass.Nullable(node.originalSelector)
          ),
          node
        )
      case None =>
        throw new IllegalStateException(
          "The ExtensionStore and CssStylesheet passed to cloneCssStylesheet() " +
            "must come from the same compilation."
        )
    }
  }

  def visitCssStylesheet(node: CssStylesheet): ModifiableCssStylesheet =
    _visitChildren(new ModifiableCssStylesheet(node.span), node)

  def visitCssSupportsRule(node: CssSupportsRule): ModifiableCssSupportsRule =
    _visitChildren(
      new ModifiableCssSupportsRule(node.condition, node.span),
      node
    )

  /** Visits [oldParent]'s children and adds their cloned values as children of
    * [newParent], then returns [newParent].
    */
  private def _visitChildren[T <: ModifiableCssParentNode](
    newParent: T,
    oldParent: CssParentNode
  ): T = {
    for (child <- oldParent.children) {
      val newChild = child.accept(this)
      newChild.isGroupEnd = child.isGroupEnd
      newParent.addChild(newChild)
    }
    newParent
  }
}
