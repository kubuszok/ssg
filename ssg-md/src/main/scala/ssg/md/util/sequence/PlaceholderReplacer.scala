/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/PlaceholderReplacer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable

import scala.util.boundary
import scala.util.boundary.break

/** Used to replace placeholder text in the form of open/close char such as &lt;text&gt; in a markdown document.
  *
  * Used by docx conversion application to replace custom fields.
  */
object PlaceholderReplacer {

  def replaceAll[T](
    spanList:         java.util.Collection[T],
    mapper:           String => Nullable[String],
    openPlaceholder:  Char,
    closePlaceholder: Char,
    getter:           T => String,
    setter:           (T, String) => Unit
  ): Unit = {
    if (spanList.isEmpty) return // only valid use: Unit-returning method early exit

    var sb: Nullable[StringBuilder] = Nullable.empty

    // accumulate text from < to >, because placeholder can be broken up across multiple spans
    val iter = spanList.iterator()
    while (iter.hasNext) {
      val span      = iter.next()
      val textValue = getter(span)

      val length  = textValue.length
      var lastPos = 0
      var plainText: Nullable[StringBuilder] = Nullable.empty

      boundary {
        while (lastPos < length)
          if (sb.isEmpty) {
            val pos = textValue.indexOf(openPlaceholder, lastPos)
            if (pos == -1) {
              // nothing in this one
              if (lastPos > 0) {
                // had partial text
                if (plainText.isDefined) plainText.get.append(textValue.substring(lastPos))
                else setter(span, textValue.substring(lastPos))
              }
              break()
            } else {
              sb = Nullable(new StringBuilder())
              if (lastPos < pos) {
                // have plain text
                if (plainText.isEmpty) plainText = Nullable(new StringBuilder())
                plainText.get.append(textValue.substring(lastPos, pos))
              }
              lastPos = pos + 1
              if (lastPos >= length && plainText.isEmpty) setter(span, "")
            }
          } else {
            val pos = textValue.indexOf(closePlaceholder, lastPos)

            if (pos == -1) {
              sb.get.append(textValue.substring(lastPos))
              if (plainText.isEmpty) setter(span, "")
              lastPos = length
            } else {
              // part of it is non-plain text
              sb.get.append(textValue.substring(lastPos, pos))
              lastPos = pos + 1

              val placeholder = sb.get.toString()
              val result: Nullable[String] = mapper(placeholder)
              sb = Nullable.empty

              val resolved =
                if (result.isEmpty) openPlaceholder.toString + placeholder + closePlaceholder.toString
                else result.get

              if (plainText.isEmpty) plainText = Nullable(new StringBuilder())
              plainText.get.append(resolved)
            }
          }
      }

      if (plainText.isDefined) {
        // have replacement text for the span
        setter(span, plainText.get.toString())
      }
    }
  }
}
