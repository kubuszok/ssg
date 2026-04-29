/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.parser.Parser
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class EnumeratedReferenceOptions(options: DataHolder) {
  val contentIndent: Int = Parser.LISTS_ITEM_INDENT.get(options)
}
