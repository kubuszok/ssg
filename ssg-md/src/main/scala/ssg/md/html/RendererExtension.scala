/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/RendererExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/RendererExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html

import ssg.md.util.data.MutableDataHolder
import ssg.md.util.misc.Extension

/** Extension point for RenderingExtensions that only provide attributes, link resolvers or html id generators
  */
trait RendererExtension extends Extension {

  /** This method is called first on all extensions so that they can adjust the options that must be common to all extensions.
    *
    * @param options
    *   option set that will be used for the builder
    */
  def rendererOptions(options: MutableDataHolder): Unit

  /** Called to give each extension to register extension points that it contains
    *
    * @param rendererBuilder
    *   builder to call back for extension point registration
    * @param rendererType
    *   type of rendering being performed. For now "HTML", "JIRA" or "YOUTRACK"
    */
  def extend(rendererBuilder: RendererBuilder, rendererType: String): Unit
}
