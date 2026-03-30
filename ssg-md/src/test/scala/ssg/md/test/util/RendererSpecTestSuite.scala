/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * munit adapter for flexmark renderer spec tests.
 * Replaces RendererSpecTest (JUnit4 ComboSpecTestCase subclass).
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.data.{DataHolder, DataSet, MutableDataSet}
import ssg.md.util.sequence.BasedSequence

import java.{util => ju}
import scala.language.implicitConversions

/**
 * munit suite for HTML rendering spec tests.
 *
 * Parses markdown with Parser, renders with HtmlRenderer,
 * compares against expected HTML from the spec file.
 *
 * Mirrors the original RendererSpecTest which sets INDENT_SIZE=2
 * and provides a "src-pos" option.
 */
abstract class RendererSpecTestSuite extends SpecTestSuite {

  /** Aggregate INDENT_SIZE=2 (from original RendererSpecTest) with subclass defaults. */
  override protected def optionsFor(example: SpecExample): DataHolder = {
    val subclassBase = defaultOptions.getOrElse(new MutableDataSet())
    // Merge renderer base options (INDENT_SIZE=2) with subclass defaults
    val base = DataSet.aggregate(Nullable(RendererSpecTestSuite.RENDERER_OPTIONS), Nullable(subclassBase)).toImmutable
    val optionSet = example.optionsSet
    if (optionSet.isDefined && optionSet.get.nonEmpty) {
      val mergedMap = new ju.HashMap[String, DataHolder](RendererSpecTestSuite.BASE_OPTIONS_MAP)
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

  /** Prepare the source for parsing, trimming trailing EOL and applying SOURCE_PREFIX/SUFFIX/INDENT. */
  protected def prepareSource(example: SpecExample, options: DataHolder): String = {
    val noFileEol = TestUtils.NO_FILE_EOL.get(options)
    val trimmedSource = if (noFileEol) TestUtils.trimTrailingEOL(example.source) else example.source

    val sourcePrefix = TestUtils.SOURCE_PREFIX.get(options)
    val sourceSuffix = TestUtils.SOURCE_SUFFIX.get(options)
    val sourceIndent = TestUtils.SOURCE_INDENT.get(options)

    val baseInput: BasedSequence = if (sourcePrefix.nonEmpty || sourceSuffix.nonEmpty) {
      import ssg.md.util.misc.Utils.suffixWith
      val combinedSource = sourcePrefix + suffixWith(trimmedSource, "\n") + sourceSuffix
      BasedSequence.of(combinedSource).subSequence(sourcePrefix.length, combinedSource.length - sourceSuffix.length)
    } else {
      BasedSequence.of(trimmedSource)
    }

    val input = TestUtils.stripIndent(baseInput, sourceIndent)
    input.toString
  }

  override protected def renderHtml(example: SpecExample, options: DataHolder): String = {
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()
    val source = prepareSource(example, options)
    val document = parser.parse(source)
    renderer.render(document)
  }

  override protected def renderAst(example: SpecExample, options: DataHolder): Nullable[String] = {
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()
    val source = prepareSource(example, options)
    val document = parser.parse(source)
    // Render first so side effects (e.g. footnote ordinal resolution) are applied before AST collection
    renderer.render(document)
    val visitor = new AstCollectingVisitor()
    Nullable(visitor.collectAndGetAstText(document))
  }
}

object RendererSpecTestSuite {

  /** Base renderer options: INDENT_SIZE=2 (matches original RendererSpecTest). */
  val RENDERER_OPTIONS: DataHolder = new MutableDataSet()
    .set(HtmlRenderer.INDENT_SIZE, 2)
    .toImmutable

  /** Base options map with "src-pos" (matches original RendererSpecTest). */
  val BASE_OPTIONS_MAP: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    map.put("src-pos", new MutableDataSet().set(HtmlRenderer.SOURCE_POSITION_ATTRIBUTE, "md-pos").toImmutable)
    map
  }
}
