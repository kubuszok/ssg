/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM implementation of Unicode NFC normalization.
 */
package ssg
package katex
package data

import java.text.Normalizer

object UnicodeNormalize {
  def nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
}
