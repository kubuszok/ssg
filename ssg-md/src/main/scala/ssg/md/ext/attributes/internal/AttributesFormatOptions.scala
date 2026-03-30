/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.DiscretionaryText

import scala.language.implicitConversions

class AttributesFormatOptions(options: DataHolder) {

  val combineConsecutive: Boolean = AttributesExtension.FORMAT_ATTRIBUTES_COMBINE_CONSECUTIVE.get(options)
  val sort: Boolean = AttributesExtension.FORMAT_ATTRIBUTES_SORT.get(options)
  val attributesSpaces: DiscretionaryText = AttributesExtension.FORMAT_ATTRIBUTES_SPACES.get(options)
  val attributeEqualSpace: DiscretionaryText = AttributesExtension.FORMAT_ATTRIBUTE_EQUAL_SPACE.get(options)
  val attributeValueQuotes: AttributeValueQuotes = AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES.get(options)
  val attributeId: AttributeImplicitName = AttributesExtension.FORMAT_ATTRIBUTE_ID.get(options)
  val attributeClass: AttributeImplicitName = AttributesExtension.FORMAT_ATTRIBUTE_CLASS.get(options)
}
