/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/LinkResolverContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/LinkResolverContext.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.data.DataHolder
import ssg.md.util.html.Attributes

trait LinkResolverContext extends LinkResolverBasicContext {

  /** Get the current rendering context DataHolder.
    */
  override def getOptions: DataHolder

  /** @return
    *   the Document node of the current context
    */
  override def getDocument: Document

  /** @param url
    *   to be encoded
    * @return
    *   an encoded URL (depending on the configuration)
    */
  def encodeUrl(url: CharSequence): String

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
    *   the current node being rendered
    */
  def getCurrentNode: Node

  /** Resolve link for rendering.
    */
  def resolveLink(linkType: LinkType, url: CharSequence, urlEncode: Nullable[Boolean]): ResolvedLink =
    resolveLink(linkType, url, Nullable.empty[Attributes], urlEncode)

  /** Resolve link for rendering.
    */
  def resolveLink(linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes], urlEncode: Nullable[Boolean]): ResolvedLink
}
