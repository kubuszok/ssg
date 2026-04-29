/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocFormatOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class TocFormatOptions(options: DataHolder) {

  val updateOnFormat:   SimTocGenerateOnFormat = TocExtension.FORMAT_UPDATE_ON_FORMAT.get(options)
  val formatTocOptions: TocOptions             = TocExtension.FORMAT_OPTIONS.get(options)
}
