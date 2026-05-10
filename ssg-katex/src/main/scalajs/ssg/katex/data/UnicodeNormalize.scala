/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of Unicode NFC normalization using JS String.normalize().
 */
package ssg
package katex
package data

import scala.scalajs.js

object UnicodeNormalize {
  def nfc(s: String): String = s.asInstanceOf[js.Dynamic].normalize("NFC").asInstanceOf[String]
}
