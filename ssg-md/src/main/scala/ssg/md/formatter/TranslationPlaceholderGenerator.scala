/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslationPlaceholderGenerator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslationPlaceholderGenerator.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package formatter

trait TranslationPlaceholderGenerator {

  /** Return a placeholder for given translation span index
    *
    * @param index
    *   1..N, make sure it is unique within this customizer, default format and within other customizers
    * @return
    *   string for the placeholder
    */
  def getPlaceholder(index: Int): String
}
