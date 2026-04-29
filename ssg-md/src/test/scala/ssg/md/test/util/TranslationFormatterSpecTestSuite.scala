/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * munit adapter for flexmark translation formatter spec tests.
 * Replaces TranslationFormatterSpecTest (JUnit4 ComboSpecTestCase subclass). */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.formatter.{ Formatter, RenderPurpose }
import ssg.md.parser.Parser
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.ast.Document
import ssg.md.util.data.{ DataHolder, DataKey, DataSet, MutableDataSet }

import java.{ util => ju }
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

/** munit suite for Translation Formatter spec tests.
  *
  * Parses markdown, runs through 3-phase translation (TRANSLATION_SPANS -> TRANSLATED_SPANS -> TRANSLATED), compares against expected output.
  *
  * Mirrors the original TranslationFormatterSpecTest.
  */
abstract class TranslationFormatterSpecTestSuite extends FormatterSpecTestSuite {

  private val SHOW_INTERMEDIATE:     Boolean = false
  private val SHOW_INTERMEDIATE_AST: Boolean = false

  val DETAILS:     DataKey[Boolean] = new DataKey[Boolean]("DETAILS", SHOW_INTERMEDIATE)
  val AST_DETAILS: DataKey[Boolean] = new DataKey[Boolean]("AST_DETAILS", SHOW_INTERMEDIATE_AST)

  private val TRANSLATION_BASE_OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.HTML_FOR_TRANSLATOR, true)
    .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
    .set(Formatter.MAX_TRAILING_BLANK_LINES, 0)
    .toImmutable

  private val translationOptionsMap: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    map.put("details", new MutableDataSet().set(DETAILS, true).toImmutable)
    map.put("ast-details", new MutableDataSet().set(AST_DETAILS, true).toImmutable)
    map
  }

  override protected def optionsFor(example: SpecExample): DataHolder = {
    val subclassBase = defaultOptions.getOrElse(new MutableDataSet())
    // Merge translation base options with formatter base options and subclass defaults
    val base = DataSet.aggregate(
      Nullable(FormatterSpecTestSuite.BASE_OPTIONS),
      DataSet.aggregate(Nullable(TRANSLATION_BASE_OPTIONS), Nullable(subclassBase))
    ).toImmutable
    val optionSet = example.optionsSet
    if (optionSet.isDefined && optionSet.get.nonEmpty) {
      val mergedMap = new ju.HashMap[String, DataHolder](FormatterSpecTestSuite.BASE_OPTIONS_MAP)
      mergedMap.putAll(translationOptionsMap)
      mergedMap.putAll(optionsMap)
      val optionsProvider: String => Nullable[DataHolder] = { name =>
        TestUtils.processOption(mergedMap.asInstanceOf[ju.Map[String, DataHolder]], name)
      }
      val opts = TestUtils.getOptions(example, optionSet, optionsProvider)
      if (opts.isDefined) {
        DataSet.aggregate(Nullable(base), opts).toImmutable
      } else {
        base
      }
    } else {
      base
    }
  }

  /** Translate function: swap case, keep certain consonants, keep vowels */
  private def translate(text: CharSequence): CharSequence = {
    val sb = new StringBuilder()
    val iMax = text.length
    var i = 0
    while (i < iMax) {
      val c = text.charAt(i)

      if ("htpcom".indexOf(c) != -1) {
        sb.append(c)
        i += 1
      } else {
        if ("aeiouy".indexOf(c) != -1) {
          sb.append(c)
        }

        if (Character.isUpperCase(c)) {
          sb.append(Character.toLowerCase(c))
        } else if (Character.isLowerCase(c)) {
          sb.append(Character.toUpperCase(c))
        } else {
          sb.append(c)
        }
        i += 1
      }
    }
    sb.toString
  }

  override protected def renderHtml(example: SpecExample, options: DataHolder): String = {
    val parser    = Parser.builder(options).build()
    val formatter = Formatter.builder(Nullable(options)).build()

    val noFileEol     = TestUtils.NO_FILE_EOL.get(options)
    val trimmedSource = if (noFileEol) TestUtils.trimTrailingEOL(example.source) else example.source
    val document = parser.parse(trimmedSource).asInstanceOf[Document]

    val showIntermediate    = DETAILS.get(options)
    val showIntermediateAst = AST_DETAILS.get(options)

    val handler = formatter.getTranslationHandler
    val formattedOutput = formatter.translationRender(document, handler, RenderPurpose.TRANSLATION_SPANS)

    // now need to output translation strings, delimited
    val translatingTexts = handler.getTranslatingTexts

    val outputAst: Nullable[StringBuilder] = if (showIntermediateAst) Nullable(new StringBuilder()) else Nullable.empty

    val out = new StringBuilder()

    if (showIntermediate) {
      out.append("- Translating Spans ------\n")
      out.append(formattedOutput)
      out.append("- Translated Spans --------\n")
    }

    if (showIntermediateAst) {
      outputAst.get.append("- Original ----------------\n")
      outputAst.get.append(TestUtils.ast(document))
    }

    val translatedTexts = new java.util.ArrayList[CharSequence](translatingTexts.size)
    for (text <- translatingTexts) {
      val translated = translate(text)
      translatedTexts.add(translated)
      if (showIntermediate) {
        out.append("<<<").append(text).append('\n')
        out.append(">>>").append(translated).append('\n')
      }
    }

    // use the translations
    if (showIntermediate) {
      out.append("- Partial ----------------\n")
    }

    handler.setTranslatedTexts(translatedTexts.asScala.toList)
    val partial = formatter.translationRender(document, handler, RenderPurpose.TRANSLATED_SPANS)

    if (showIntermediate) {
      out.append(partial)
      out.append("- Translated -------------\n")
    }
    val partialDoc = Parser.builder(options).build().parse(partial)

    if (showIntermediateAst) {
      outputAst.get.append("- Partial ----------------\n")
      outputAst.get.append(TestUtils.ast(partialDoc))
    }

    val translated = formatter.translationRender(partialDoc, handler, RenderPurpose.TRANSLATED)
    out.append(translated)

    out.toString
  }

  override protected def renderAst(example: SpecExample, options: DataHolder): Nullable[String] = {
    // Translation tests override AST only when showIntermediateAst is set; for now, no AST
    Nullable.empty
  }
}
