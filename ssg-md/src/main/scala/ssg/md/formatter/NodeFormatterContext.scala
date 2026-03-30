/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/NodeFormatterContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.html.renderer.LinkResolverContext
import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.data.DataHolder
import ssg.md.util.format.{ NodeContext, TrackedOffsetList }
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence

import java.util.Collection

/** The context for node rendering, including configuration and functionality for the node renderer to use.
  */
trait NodeFormatterContext extends NodeContext[Node, NodeFormatterContext] with TranslationContext with LinkResolverContext with ExplicitAttributeIdProvider {

  /** @return
    *   the HTML writer to use
    */
  def getMarkdown: MarkdownWriter

  /** Render the specified node and its children using the configured renderers.
    *
    * @param node
    *   the node to render
    */
  def render(node: Node): Unit

  /** Render the children of the node, used by custom renderers
    *
    * @param parent
    *   node the children of which are to be rendered
    */
  def renderChildren(parent: Node): Unit

  /** @return
    *   current rendering phase
    */
  def getFormattingPhase: FormattingPhase

  /** pass node rendering to previously registered handler
    */
  def delegateRender(): Unit

  /** Get the current rendering context DataHolder.
    */
  override def getOptions: DataHolder

  /** @return
    *   the FormatterOptions for the context.
    */
  def getFormatterOptions: FormatterOptions

  /** @return
    *   the Document node of the current context
    */
  override def getDocument: Document

  /** @return
    *   predicate for prefix chars which compact like block quote prefix char
    */
  def getBlockQuoteLikePrefixPredicate: CharPredicate

  /** @return
    *   char sequence of all prefix chars which compact like block quote prefix char
    */
  def getBlockQuoteLikePrefixChars: BasedSequence

  /** @return
    *   tracked offset list
    */
  def getTrackedOffsets: TrackedOffsetList

  def isRestoreTrackedSpaces: Boolean

  /** @return
    *   original sequence used for tracked offsets.
    */
  def getTrackedSequence: BasedSequence

  /** Get iterable of nodes of given types, in order of their appearance in the document tree.
    */
  def nodesOfType(classes: Array[Class[?]]): Iterable[? <: Node]

  def nodesOfType(classes: Collection[Class[?]]): Iterable[? <: Node]

  /** Get iterable of nodes of given types, in reverse order of their appearance in the document tree.
    */
  def reversedNodesOfType(classes: Array[Class[?]]): Iterable[? <: Node]

  def reversedNodesOfType(classes: Collection[Class[?]]): Iterable[? <: Node]
}
