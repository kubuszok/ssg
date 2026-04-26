/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/RendererBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/RendererBuilder.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html

import ssg.md.html.renderer.HeaderIdGeneratorFactory
import ssg.md.util.data.DataHolder

/** Extension point for RenderingExtensions that only provide attributes, link resolvers or html id generators
  */
trait RendererBuilder extends DataHolder {

  /** Add an attribute provider for adding/changing HTML attributes to the rendered tags.
    *
    * @param attributeProviderFactory
    *   the attribute provider factory to add
    * @return
    *   `this`
    */
  def attributeProviderFactory(attributeProviderFactory: AttributeProviderFactory): RendererBuilder

  /** Add a factory for resolving links in markdown to URI used in rendering
    *
    * @param linkResolverFactory
    *   the factory for creating a node renderer
    * @return
    *   `this`
    */
  def linkResolverFactory(linkResolverFactory: LinkResolverFactory): RendererBuilder

  /** Add a factory for resolving URI to content
    *
    * @param contentResolverFactory
    *   the factory for creating a node renderer
    * @return
    *   `this`
    */
  def contentResolverFactory(contentResolverFactory: UriContentResolverFactory): RendererBuilder

  /** Add a factory for generating the header id attribute from the header's text
    *
    * @param htmlIdGeneratorFactory
    *   the factory for generating header tag id attributes
    * @return
    *   `this`
    */
  def htmlIdGeneratorFactory(htmlIdGeneratorFactory: HeaderIdGeneratorFactory): RendererBuilder
}
