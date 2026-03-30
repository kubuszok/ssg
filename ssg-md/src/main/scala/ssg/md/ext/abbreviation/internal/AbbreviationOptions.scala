/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AbbreviationOptions(options: DataHolder) {
  val useLinks: Boolean = AbbreviationExtension.USE_LINKS.get(options)
}
