/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.language.implicitConversions

class EnumeratedReferenceFormatOptions(options: DataHolder) {
  val enumeratedReferencePlacement: ElementPlacement     = EnumeratedReferenceExtension.ENUMERATED_REFERENCE_PLACEMENT.get(options)
  val enumeratedReferenceSort:      ElementPlacementSort = EnumeratedReferenceExtension.ENUMERATED_REFERENCE_SORT.get(options)
}
