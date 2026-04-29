/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AttributesOptions(options: DataHolder) {

  val assignTextAttributes:            Boolean           = AttributesExtension.ASSIGN_TEXT_ATTRIBUTES.get(options)
  val fencedCodeInfoAttributes:        Boolean           = AttributesExtension.FENCED_CODE_INFO_ATTRIBUTES.get(options)
  val fencedCodeAddAttributes:         FencedCodeAddType = AttributesExtension.FENCED_CODE_ADD_ATTRIBUTES.get(options)
  val wrapNonAttributeText:            Boolean           = AttributesExtension.WRAP_NON_ATTRIBUTE_TEXT.get(options)
  val useEmptyImplicitAsSpanDelimiter: Boolean           = AttributesExtension.USE_EMPTY_IMPLICIT_AS_SPAN_DELIMITER.get(options)
}
