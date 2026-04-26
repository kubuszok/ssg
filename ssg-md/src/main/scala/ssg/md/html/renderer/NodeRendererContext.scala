/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/NodeRendererContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/NodeRendererContext.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html
package renderer

import ssg.md.html.{ HtmlRendererOptions, HtmlWriter }
import ssg.md.util.ast.Node
import ssg.md.util.html.{ Attributes, MutableAttributes }

/** The context for node rendering, including configuration and functionality for the node renderer to use.
  */
trait NodeRendererContext extends LinkResolverContext {

  /** Extend the attributes by extensions for the node being currently rendered.
    *
    * @param part
    *   the tag of the node being rendered, some nodes render multiple tags with attributes
    * @param attributes
    *   the attributes that were calculated by the renderer or null, these may be modified.
    * @return
    *   the extended attributes with added/updated/removed entries
    */
  def extendRenderingNodeAttributes(part: AttributablePart, attributes: Nullable[Attributes]): MutableAttributes

  /** Extend the attributes by extensions for the node being currently rendered.
    *
    * @param node
    *   node for which to get attributes
    * @param part
    *   the tag of the node being rendered, some nodes render multiple tags with attributes
    * @param attributes
    *   the attributes that were calculated by the renderer or null, these may be modified.
    * @return
    *   the extended attributes with added/updated/removed entries
    */
  def extendRenderingNodeAttributes(node: Node, part: AttributablePart, attributes: Nullable[Attributes]): MutableAttributes

  /** @return
    *   the HTML writer to use
    */
  def getHtmlWriter: HtmlWriter

  /** Creates a child rendering context that can be used to collect rendered html text.
    *
    * @param inheritIndent
    *   whether the html writer of the sub-context should inherit the current context's indentation level
    * @return
    *   a new rendering context with a given appendable for its output
    */
  def getSubContext(inheritIndent: Boolean): NodeRendererContext

  /** Creates a child rendering context that can be used to collect rendered html text of the previously registered node renderer.
    *
    * @param inheritIndent
    *   whether the html writer of the sub-context should inherit the current context's indentation level
    * @return
    *   a new rendering context with a given appendable for its output
    */
  def getDelegatedSubContext(inheritIndent: Boolean): NodeRendererContext

  /** pass node rendering to previously registered handler
    */
  def delegateRender(): Unit

  /** Get the id attribute for the given node.
    *
    * @param node
    *   node for which to get an id
    * @return
    *   id string or null
    */
  def getNodeId(node: Node): Nullable[String]

  /** Whether the current rendering context has link rendering disabled.
    */
  def isDoNotRenderLinks: Boolean

  /** Increment/Decrement the do not render links in this context.
    */
  def doNotRenderLinks(doNotRenderLinks: Boolean): Unit

  /** Increment the do not render links in this context.
    */
  def doNotRenderLinks(): Unit

  /** Decrement the do not render links in this context.
    */
  def doRenderLinks(): Unit

  /** @return
    *   current rendering phase
    */
  def getRenderingPhase: RenderingPhase

  /** @return
    *   the HtmlRendererOptions for the context.
    */
  def getHtmlOptions: HtmlRendererOptions
}
