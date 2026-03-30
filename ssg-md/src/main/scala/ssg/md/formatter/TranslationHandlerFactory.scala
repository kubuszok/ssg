/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslationHandlerFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.html.renderer.HtmlIdGeneratorFactory
import ssg.md.util.data.DataHolder

trait TranslationHandlerFactory extends TranslationContext {
  def create(options: DataHolder, idGeneratorFactory: HtmlIdGeneratorFactory): TranslationHandler
}
