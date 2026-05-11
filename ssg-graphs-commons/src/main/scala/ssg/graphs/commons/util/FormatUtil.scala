/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package graphs
package commons
package util

object FormatUtil {

  def roundNumber(num: Double, precision: Int = 2): Double = {
    val factor = math.pow(10, precision)
    math.round(num * factor).toDouble / factor
  }

  def formatNumber(value: Double): String =
    if (value == value.toLong.toDouble) {
      value.toLong.toString
    } else {
      val rounded = roundNumber(value, 4)
      val s       = rounded.toString
      if (s.contains("E") || s.contains("e")) {
        f"$rounded%.4f".replaceAll("0+$", "").replaceAll("\\.$", "")
      } else {
        s
      }
    }
}
