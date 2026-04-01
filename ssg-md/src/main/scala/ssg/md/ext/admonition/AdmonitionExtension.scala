/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/AdmonitionExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package admonition

import ssg.md.ext.admonition.internal.{AdmonitionBlockParser, AdmonitionNodeFormatter, AdmonitionNodeRenderer}
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder, NotNullValueSupplier}

import java.io.{InputStreamReader, StringWriter}
import java.{util => ju}
import scala.language.implicitConversions

/**
 * Extension for admonitions
 *
 * Create it with [[AdmonitionExtension.create]] and then configure it on the builders
 *
 * The parsed admonition text is turned into [[AdmonitionBlock]] nodes.
 */
class AdmonitionExtension private ()
    extends Parser.ParserExtension,
    HtmlRenderer.HtmlRendererExtension,
    Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new AdmonitionNodeFormatter.Factory())
  }

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customBlockParserFactory(new AdmonitionBlockParser.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new AdmonitionNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object AdmonitionExtension {

  val CONTENT_INDENT: DataKey[Integer] = new DataKey[Integer]("ADMONITION.CONTENT_INDENT", 4)
  val ALLOW_LEADING_SPACE: DataKey[Boolean] = new DataKey[Boolean]("ADMONITION.ALLOW_LEADING_SPACE", true)
  val INTERRUPTS_PARAGRAPH: DataKey[Boolean] = new DataKey[Boolean]("ADMONITION.INTERRUPTS_PARAGRAPH", true)
  val INTERRUPTS_ITEM_PARAGRAPH: DataKey[Boolean] = new DataKey[Boolean]("ADMONITION.INTERRUPTS_ITEM_PARAGRAPH", true)
  val WITH_SPACES_INTERRUPTS_ITEM_PARAGRAPH: DataKey[Boolean] = new DataKey[Boolean]("ADMONITION.WITH_SPACES_INTERRUPTS_ITEM_PARAGRAPH", true)
  val ALLOW_LAZY_CONTINUATION: DataKey[Boolean] = new DataKey[Boolean]("ADMONITION.ALLOW_LAZY_CONTINUATION", true)
  val UNRESOLVED_QUALIFIER: DataKey[String] = new DataKey[String]("ADMONITION.UNRESOLVED_QUALIFIER", "note")
  val QUALIFIER_TYPE_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]]("ADMONITION.QUALIFIER_TYPE_MAP", new NotNullValueSupplier[ju.Map[String, String]] { def get: ju.Map[String, String] = getQualifierTypeMap() })
  val QUALIFIER_TITLE_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]]("ADMONITION.QUALIFIER_TITLE_MAP", new NotNullValueSupplier[ju.Map[String, String]] { def get: ju.Map[String, String] = getQualifierTitleMap() })
  val TYPE_SVG_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]]("ADMONITION.TYPE_SVG_MAP", new NotNullValueSupplier[ju.Map[String, String]] { def get: ju.Map[String, String] = getQualifierSvgValueMap() })

  def getQualifierTypeMap(): ju.Map[String, String] = {
    val map = new ju.HashMap[String, String]()
    map.put("abstract", "abstract"); map.put("summary", "abstract"); map.put("tldr", "abstract")
    map.put("bug", "bug")
    map.put("danger", "danger"); map.put("error", "danger")
    map.put("example", "example"); map.put("snippet", "example")
    map.put("fail", "fail"); map.put("failure", "fail"); map.put("missing", "fail")
    map.put("faq", "faq"); map.put("question", "faq"); map.put("help", "faq")
    map.put("info", "info"); map.put("todo", "info")
    map.put("note", "note"); map.put("seealso", "note")
    map.put("quote", "quote"); map.put("cite", "quote")
    map.put("success", "success"); map.put("check", "success"); map.put("done", "success")
    map.put("tip", "tip"); map.put("hint", "tip"); map.put("important", "tip")
    map.put("warning", "warning"); map.put("caution", "warning"); map.put("attention", "warning")
    map
  }

  def getQualifierTitleMap(): ju.Map[String, String] = {
    val map = new ju.HashMap[String, String]()
    map.put("abstract", "Abstract"); map.put("summary", "Summary"); map.put("tldr", "TLDR")
    map.put("bug", "Bug")
    map.put("danger", "Danger"); map.put("error", "Error")
    map.put("example", "Example"); map.put("snippet", "Snippet")
    map.put("fail", "Fail"); map.put("failure", "Failure"); map.put("missing", "Missing")
    map.put("faq", "Faq"); map.put("question", "Question"); map.put("help", "Help")
    map.put("info", "Info"); map.put("todo", "To Do")
    map.put("note", "Note"); map.put("seealso", "See Also")
    map.put("quote", "Quote"); map.put("cite", "Cite")
    map.put("success", "Success"); map.put("check", "Check"); map.put("done", "Done")
    map.put("tip", "Tip"); map.put("hint", "Hint"); map.put("important", "Important")
    map.put("warning", "Warning"); map.put("caution", "Caution"); map.put("attention", "Attention")
    map
  }

  def getQualifierSvgValueMap(): ju.Map[String, String] = {
    val map = new ju.HashMap[String, String]()
    for (name <- Array("abstract", "bug", "danger", "example", "fail", "faq", "info", "note", "quote", "success", "tip", "warning")) {
      map.put(name, getInputStreamContent(classOf[AdmonitionExtension].getResourceAsStream("/images/adm-" + name + ".svg")))
    }
    map
  }

  def getInputStreamContent(inputStream: java.io.InputStream): String = {
    try {
      val streamReader = new InputStreamReader(inputStream)
      val stringWriter = new StringWriter()
      copy(streamReader, stringWriter)
      stringWriter.close()
      stringWriter.toString
    } catch {
      case e: Exception =>
        e.printStackTrace()
        ""
    }
  }

  def getDefaultCSS: String = getInputStreamContent(classOf[AdmonitionExtension].getResourceAsStream("/admonition.css"))

  def getDefaultScript: String = getInputStreamContent(classOf[AdmonitionExtension].getResourceAsStream("/admonition.js"))

  def copy(reader: java.io.Reader, writer: java.io.Writer): Unit = {
    val buffer = new Array[Char](4096)
    var n = reader.read(buffer)
    while (n != -1) {
      writer.write(buffer, 0, n)
      n = reader.read(buffer)
    }
    writer.flush()
    reader.close()
  }

  def create(): AdmonitionExtension = new AdmonitionExtension()
}
