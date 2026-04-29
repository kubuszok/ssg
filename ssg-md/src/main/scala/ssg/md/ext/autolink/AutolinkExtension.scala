/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-autolink/src/main/java/com/vladsch/flexmark/ext/autolink/AutolinkExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-autolink/src/main/java/com/vladsch/flexmark/ext/autolink/AutolinkExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package autolink

import ssg.md.ext.autolink.internal.AutolinkNodePostProcessor
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataKey, MutableDataHolder }
import scala.language.implicitConversions

/** Extension for automatically turning plain URLs and email addresses into links.
  *
  * Create it with [[AutolinkExtension.create]] and then configure it on the builders.
  *
  * The parsed links are turned into normal [[Link]] nodes.
  */
class AutolinkExtension private () extends Parser.ParserExtension {

  //  regex to match all link texts which should be ignored for auto-linking
  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.postProcessorFactory(new AutolinkNodePostProcessor.Factory())
}

object AutolinkExtension {

  val IGNORE_LINKS: DataKey[String] = new DataKey[String]("IGNORE_LINKS", "")

  def create(): AutolinkExtension = new AutolinkExtension()
}
