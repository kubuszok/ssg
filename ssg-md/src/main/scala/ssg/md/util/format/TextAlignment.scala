/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TextAlignment.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TextAlignment.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

enum TextAlignment extends java.lang.Enum[TextAlignment] {
  case LEFT
  case CENTER
  case RIGHT
  case JUSTIFIED
}

object TextAlignment {

  def getAlignment(alignment: String): TextAlignment = {
    import scala.util.boundary
    import scala.util.boundary.break
    boundary {
      val values = TextAlignment.values
      var i      = 0
      while (i < values.length) {
        if (values(i).name().equalsIgnoreCase(alignment)) {
          break(values(i))
        }
        i += 1
      }
      LEFT
    }
  }
}
