/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/.../MergeJekyllTagTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package jekyll
package tag
package test

import ssg.md.ext.jekyll.tag.JekyllTagExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class MergeJekyllTagSuite extends munit.FunSuite {

  private val content: HashMap[String, String] = {
    val m = new HashMap[String, String]()
    m.put("test.md", "" +
      "## Embedded Content\n" +
      "\n" +
      "Content\n" +
      "\n" +
      "")
    m.put("test2.md", "" +
      "Content\n" +
      "")
    m
  }

  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(JekyllTagExtension.create()))
    .set(Parser.BLANK_LINES_IN_AST, true)
    .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
    .set(Parser.HEADING_NO_ATX_SPACE, true)
    .set(Formatter.MAX_TRAILING_BLANK_LINES, 0)
    .set(JekyllTagExtension.INCLUDED_HTML, content)
    .set(JekyllTagExtension.EMBED_INCLUDED_CONTENT, true)
    .toImmutable

  private val NON_EMBEDDING_OPTIONS: DataHolder = new MutableDataSet(Nullable(OPTIONS))
    .set(JekyllTagExtension.EMBED_INCLUDED_CONTENT, false)
    .toImmutable

  private val EMBEDDING_FORMATTER:     Formatter = Formatter.builder(Nullable(OPTIONS)).build()
  private val NON_EMBEDDING_FORMATTER: Formatter = Formatter.builder(Nullable(NON_EMBEDDING_OPTIONS)).build()
  private val EMBEDDING_PARSER:        Parser    = Parser.builder(OPTIONS).build()
  private val NON_EMBEDDING_PARSER:    Parser    = Parser.builder(NON_EMBEDDING_OPTIONS).build()

  private def assertMerged(embedContent: Boolean, expected: String, markdownSources: String*): Unit = {
    val parser    = if (embedContent) EMBEDDING_PARSER else NON_EMBEDDING_PARSER
    val formatter = if (embedContent) EMBEDDING_FORMATTER else NON_EMBEDDING_FORMATTER
    val documents = markdownSources.map(src => parser.parse(src)).toArray
    val mergedOutput = formatter.mergeRender(documents, 1)
    assertEquals(mergedOutput, expected, "Merged results differ")
  }

  test("IdAttributeConflict") {
    assertMerged(false,
      "" +
        "{% include test.md %}\n" +
        "\n" +
        "{% include test.md %}\n" +
        "\n" +
        "",
      "" +
        "{% include test.md %}\n" +
        "\n" +
        "",
      "" +
        "{% include test.md %}\n" +
        "\n" +
        ""
    )
  }

  test("IdAttributeConflictEmbed") {
    assertMerged(true,
      "" +
        "## Embedded Content\n" +
        "\n" +
        "Content\n" +
        "\n" +
        "## Embedded Content\n" +
        "\n" +
        "Content\n" +
        "\n" +
        "",
      "" +
        "{% include test.md %}\n" +
        "\n" +
        "",
      "" +
        "{% include test.md %}\n" +
        "\n" +
        ""
    )
  }

  test("IdAttributeConflictEmbedInline") {
    assertMerged(true,
      "" +
        "text Content\n" +
        "\n" +
        "text2 Content\n" +
        "\n" +
        "",
      "" +
        "text {% include test2.md %}\n" +
        "\n" +
        "",
      "" +
        "text2 {% include test2.md %}\n" +
        "\n" +
        ""
    )
  }
}
