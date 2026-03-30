/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/HeaderIdGeneratorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html
package renderer

/** Factory for instantiating an HTML id generator
  */
trait HeaderIdGeneratorFactory extends HtmlIdGeneratorFactory {

  /** Create a new HeaderIdGenerator for the specified resolver context.
    *
    * @param context
    *   the context for link resolution
    * @return
    *   an HTML id generator.
    */
  def create(context: LinkResolverContext): HtmlIdGenerator
}
