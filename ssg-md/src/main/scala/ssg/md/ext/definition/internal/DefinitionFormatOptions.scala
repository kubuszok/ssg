/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionFormatOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package definition
package internal

import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.DefinitionMarker

import scala.language.implicitConversions

class DefinitionFormatOptions(options: DataHolder) {

  val markerSpaces: Int              = DefinitionExtension.FORMAT_MARKER_SPACES.get(options)
  val markerType:   DefinitionMarker = DefinitionExtension.FORMAT_MARKER_TYPE.get(options)
}
