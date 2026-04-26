/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/HtmlIdGeneratorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/HtmlIdGeneratorFactory.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html
package renderer

/** Factory for instantiating new node renderers when rendering is done.
  */
trait HtmlIdGeneratorFactory {

  /** Create an id generator
    *
    * @return
    *   an html id generator
    */
  def create(): HtmlIdGenerator
}
