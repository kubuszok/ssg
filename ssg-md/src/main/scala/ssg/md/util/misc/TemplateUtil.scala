/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/TemplateUtil.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package misc

import ssg.md.Nullable

import java.util.regex.Pattern
import scala.collection.mutable

object TemplateUtil {

  val NULL_RESOLVER: Resolver = (_: Array[String]) => Nullable.empty

  trait Resolver {
    def resolve(groups: Array[String]): Nullable[String]
  }

  final class MappedResolver(protected val resolved: mutable.Map[String, String]) extends Resolver {

    def this() =
      this(mutable.HashMap.empty[String, String])

    def set(name: String, value: String): MappedResolver = {
      resolved.put(name, value)
      this
    }

    def mMap: mutable.Map[String, String] = resolved

    override def resolve(groups: Array[String]): Nullable[String] =
      if (groups.length > 2) Nullable.empty
      else Nullable.fromOption(resolved.get(groups(1)))
  }

  def resolveRefs(text: Nullable[CharSequence], pattern: Pattern, resolver: Resolver): String =
    if (text.isEmpty) ""
    else {
      val matcher = pattern.matcher(text.get)
      if (matcher.find()) {
        val sb = new StringBuffer()

        var found = true
        while (found) {
          val groups = new Array[String](matcher.groupCount() + 1)
          var i      = 0
          while (i < groups.length) {
            groups(i) = matcher.group(i)
            i += 1
          }

          val resolved = resolver.resolve(groups)

          matcher.appendReplacement(
            sb,
            if (resolved.isEmpty) ""
            else resolved.get.replace("\\", "\\\\").replace("$", "\\$")
          )
          found = matcher.find()
        }

        matcher.appendTail(sb)
        sb.toString
      } else {
        text.get.toString
      }
    }
}
