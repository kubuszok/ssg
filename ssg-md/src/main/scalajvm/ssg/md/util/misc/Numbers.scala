/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: `NumberFormat.parse(String, ParsePosition)` is java's own call. scala-java-locales does not
 * implement it (Scala Native answers `???`, Scala.js does not link), so the other rows restate it at
 * the same name. The generated code reaches this object through the port's call-site substitutions. */
package ssg
package md
package util
package misc

import java.text.{ NumberFormat, ParsePosition }

object Numbers {

  def parse(format: NumberFormat, text: String, pos: ParsePosition): Number = format.parse(text, pos)
}
