/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.language.implicitConversions

class AbbreviationFormatOptions(options: DataHolder) {
  val abbreviationsPlacement: ElementPlacement     = AbbreviationExtension.ABBREVIATIONS_PLACEMENT.get(options)
  val abbreviationsSort:      ElementPlacementSort = AbbreviationExtension.ABBREVIATIONS_SORT.get(options)
}
