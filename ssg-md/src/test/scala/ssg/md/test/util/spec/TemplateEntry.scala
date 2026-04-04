/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/TemplateEntry.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util
package spec

import java.{ util => ju }
import java.util.regex.Pattern

final class TemplateEntry(val entryNumber: Int, val source: String) {

  private val params: ju.HashSet[String] = new ju.HashSet[String]()

  // parse out the parameters
  locally {
    val m = TemplateEntry.PARAMETER_PATTERN.matcher(source)
    while (m.find()) {
      val param = m.group().substring(1, m.group().length - 1)
      params.add(param)
    }
  }

  def getParams: ju.Set[String] = params

  def replaceParams(paramsMap: ju.Map[String, String], sb: StringBuilder): Unit = {
    // create an expanded template result
    val m   = TemplateEntry.PARAMETER_PATTERN.matcher(source)
    var pos = 0
    while (m.find()) {
      val param = m.group().substring(1, m.group().length - 1)

      if (pos < m.start()) {
        sb.append(source.substring(pos, m.start()))
        pos = m.end()
      }

      // append parameter if exists
      if (paramsMap.containsKey(param)) {
        sb.append(paramsMap.get(param))
      }
    }

    if (pos < source.length) {
      sb.append(source.substring(pos))
    }
  }

  override def toString: String = "entry " + entryNumber
}

object TemplateEntry {

  private val PARAMETER_PATTERN: Pattern = Pattern.compile("\\$[a-zA-Z_]+\\$")
}
