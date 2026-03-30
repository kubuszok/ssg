/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/MessageProvider.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package options

import java.text.MessageFormat

trait MessageProvider {
  def message(key: String, defaultText: String, params: AnyRef*): String
}

object MessageProvider {
  val DEFAULT: MessageProvider = new MessageProvider {
    override def message(key: String, defaultText: String, params: AnyRef*): String =
      if (params.nonEmpty && defaultText.indexOf('{') >= 0) MessageFormat.format(defaultText, params*)
      else defaultText
  }
}
