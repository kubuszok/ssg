/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: a message pattern is filled by java's own `MessageFormat`, as flexmark's `MessageProvider.DEFAULT`
 * does. Scala.js and Scala Native have no `MessageFormat` and fill `{n}` by hand at the same name. */
package ssg
package md
package util
package misc

object Messages {

  def format(pattern: String, params: Array[Object]): String = java.text.MessageFormat.format(pattern, params*)
}
