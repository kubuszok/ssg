/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/MessageProvider.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/MessageProvider.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package options

trait MessageProvider {
  def message(key: String, defaultText: String, params: AnyRef*): String
}

object MessageProvider {
  val DEFAULT: MessageProvider = new MessageProvider {
    override def message(key: String, defaultText: String, params: AnyRef*): String =
      if (params.nonEmpty && defaultText.indexOf('{') >= 0) simpleFormat(defaultText, params*)
      else defaultText
  }

  /** Simple cross-platform replacement for java.text.MessageFormat.format. Replaces `{0}`, `{1}`, etc. with the corresponding parameter values.
    */
  private def simpleFormat(pattern: String, params: AnyRef*): String = {
    var result = pattern
    var i      = 0
    while (i < params.length) {
      result = result.replace(s"{$i}", String.valueOf(params(i)))
      i += 1
    }
    result
  }
}
