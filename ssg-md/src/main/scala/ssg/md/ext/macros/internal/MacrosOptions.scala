/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacrosOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacrosOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package macros
package internal

import ssg.md.util.data.{ DataHolder, MutableDataHolder, MutableDataSetter }

import scala.language.implicitConversions

class MacrosOptions(options: DataHolder) extends MutableDataSetter {
  val sourceWrapMacroReferences: Boolean = MacrosExtension.SOURCE_WRAP_MACRO_REFERENCES.get(options)

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.set(MacrosExtension.SOURCE_WRAP_MACRO_REFERENCES, sourceWrapMacroReferences)
    dataHolder
  }
}
