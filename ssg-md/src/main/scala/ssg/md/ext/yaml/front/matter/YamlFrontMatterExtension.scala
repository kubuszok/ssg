/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package yaml
package front
package matter

import ssg.md.ext.yaml.front.matter.internal.{YamlFrontMatterBlockParser, YamlFrontMatterNodeFormatter}
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.util.data.MutableDataHolder

/**
 * Extension for YAML-like metadata.
 *
 * Create it with [[YamlFrontMatterExtension.create]] and then configure it on the builders.
 *
 * The parsed metadata is turned into [[YamlFrontMatterNode]]. You can access the metadata using [[AbstractYamlFrontMatterVisitor]].
 */
class YamlFrontMatterExtension private () extends Parser.ParserExtension with Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new YamlFrontMatterNodeFormatter.Factory())
  }

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new YamlFrontMatterBlockParser.Factory())
  }

  override def parserOptions(options: MutableDataHolder): Unit = {}
}

object YamlFrontMatterExtension {

  def create(): YamlFrontMatterExtension = new YamlFrontMatterExtension()
}
