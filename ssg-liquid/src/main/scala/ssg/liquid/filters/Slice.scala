/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Slice.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Slice.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

/** Liquid "slice" filter — returns a substring or sub-array. */
class Slice extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    checkParams(params, 1, 2)

    if (!canBeInteger(params(0))) {
      throw new RuntimeException("Liquid error: invalid integer")
    }

    var array:  Vector[DataView] = null
    var string: String           = null
    var offset = asNumber(params(0)).intValue()
    var length = 1

    if (isArray(value)) {
      array = asArray(value, context)
      val totalLength = array.size

      if (params.length > 1) {
        if (!canBeInteger(params(1))) {
          throw new RuntimeException("Liquid error: invalid integer")
        }
        length = asNumber(params(1)).intValue()
      }

      if (offset < 0) {
        offset = totalLength + offset
      }
      if (offset + length > totalLength) {
        length = totalLength - offset
      }
      if (offset > totalLength || offset < 0) {
        DataView.from("")
      } else {
        DataView.from(array.slice(offset, offset + length))
      }
    } else {
      string = asString(value, context)
      val totalLength = string.length()

      if (params.length > 1) {
        if (!canBeInteger(params(1))) {
          throw new RuntimeException("Liquid error: invalid integer")
        }
        length = asNumber(params(1)).intValue()
      }

      if (offset < 0) {
        offset = totalLength + offset
      }
      if (offset + length > totalLength) {
        length = totalLength - offset
      }
      if (offset > totalLength || offset < 0) {
        DataView.from("")
      } else {
        DataView.from(string.substring(offset, offset + length))
      }
    }
  }
}
