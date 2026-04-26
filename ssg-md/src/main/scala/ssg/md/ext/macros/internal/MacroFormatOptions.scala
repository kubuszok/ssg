/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacroFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacroFormatOptions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package macros
package internal

import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.language.implicitConversions

class MacroFormatOptions(options: DataHolder) {
  val macrosPlacement: ElementPlacement     = MacrosExtension.MACRO_DEFINITIONS_PLACEMENT.get(options)
  val macrosSort:      ElementPlacementSort = MacrosExtension.MACRO_DEFINITIONS_SORT.get(options)
}
