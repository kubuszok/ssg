/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/CellAlignment.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package html

import scala.util.boundary
import scala.util.boundary.break

enum CellAlignment extends java.lang.Enum[CellAlignment] {
  case NONE, LEFT, CENTER, RIGHT
}

object CellAlignment {
  def getAlignment(alignment: String): CellAlignment = {
    val values = CellAlignment.values
    boundary {
      var i = 0
      while (i < values.length) {
        if (values(i).name().equalsIgnoreCase(alignment)) {
          break(values(i))
        }
        i += 1
      }
      CellAlignment.NONE
    }
  }
}
