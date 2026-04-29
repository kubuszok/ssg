/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/RenderingTestCase.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.data._
import ssg.md.util.misc.Extension

import java.{ util => ju }
import scala.language.implicitConversions

// JUnit 4: abstract test case with @Rule ExpectedException — will need adaptation to munit later
abstract class RenderingTestCase extends SpecExampleProcessor {

  val IGNORE:            DataKey[Boolean] = TestUtils.IGNORE
  val FAIL:              DataKey[Boolean] = TestUtils.FAIL
  val NO_FILE_EOL:       DataKey[Boolean] = TestUtils.NO_FILE_EOL
  val TIMED_ITERATIONS:  DataKey[Int]     = TestUtils.TIMED_ITERATIONS
  val EMBED_TIMED:       DataKey[Boolean] = TestUtils.EMBED_TIMED
  val TIMED:             DataKey[Boolean] = TestUtils.TIMED
  val INCLUDED_DOCUMENT: DataKey[String]  = TestUtils.INCLUDED_DOCUMENT
  val SOURCE_PREFIX:     DataKey[String]  = TestUtils.SOURCE_PREFIX
  val SOURCE_SUFFIX:     DataKey[String]  = TestUtils.SOURCE_SUFFIX
  val SOURCE_INDENT:     DataKey[String]  = TestUtils.SOURCE_INDENT

  val NO_FILE_EOL_FALSE: DataHolder                                    = TestUtils.NO_FILE_EOL_FALSE
  val UNLOAD_EXTENSIONS: DataKey[ju.Collection[Class[? <: Extension]]] = TestUtils.UNLOAD_EXTENSIONS
  val LOAD_EXTENSIONS:   DataKey[ju.Collection[Extension]]             = TestUtils.LOAD_EXTENSIONS
  val EXTENSIONS:        DataKey[ju.Collection[Extension]]             = SharedDataKeys.EXTENSIONS

  // JUnit 4: @Rule ExpectedException — stubbed, will need adaptation to munit later
  // public ExpectedException thrown = ExpectedException.none()

  /** Called after processing individual test case
    *
    * @param exampleRenderer
    *   renderer used
    * @param exampleParse
    *   parse information
    * @param exampleOptions
    *   example options
    */
  def addSpecExample(exampleRenderer: SpecExampleRenderer, exampleParse: SpecExampleParse, exampleOptions: Nullable[DataHolder]): Unit = {
    // default no-op
  }

  /** Called when processing full spec test case by DumpSpecReader
    *
    * @param exampleRenderer
    *   example renderer
    * @param exampleParse
    *   example parse state
    * @param exampleOptions
    *   example options
    * @param ignoredTestCase
    *   true if ignored example
    * @param html
    *   html used for comparison to expected html
    * @param ast
    *   ast used for comparison to expected ast
    */
  override def addFullSpecExample(
    exampleRenderer: SpecExampleRenderer,
    exampleParse:    SpecExampleParse,
    exampleOptions:  Nullable[DataHolder],
    ignoredTestCase: Boolean,
    html:            String,
    ast:             Nullable[String]
  ): Unit = {
    // default no-op
  }

  /* Convenience functions for those tests that do not have an example */
  final protected def assertRendering(source: String, html: String): Unit =
    assertRendering(SpecExample.ofCaller(1, this.getClass, source, html, Nullable.empty))

  final protected def assertRendering(source: String, html: String, ast: Nullable[String]): Unit =
    assertRendering(SpecExample.ofCaller(1, this.getClass, source, html, ast))

  final protected def assertRendering(specExample: SpecExample): Unit = {
    val example        = checkExample(specExample)
    val message        = example.fileUrlWithLineNumber
    val source         = example.source
    val optionsSet     = example.optionsSet
    val expectedHtml   = example.html
    val expectedAst    = example.ast
    val exampleOptions = TestUtils.getOptions(example, optionsSet, this.options)

    val exampleRenderer = getSpecExampleRenderer(example, exampleOptions)

    val specExampleParse = new SpecExampleParse(exampleRenderer.options.get, exampleRenderer, exampleOptions, source)
    val timed            = specExampleParse.isTimed
    val iterations       = specExampleParse.iterations

    val html = exampleRenderer.getHtml
    var i    = 1
    while (i < iterations) {
      exampleRenderer.getHtml
      i += 1
    }
    val render = System.nanoTime()

    val ast: Nullable[String] = if (expectedAst.isEmpty) Nullable.empty else Nullable(exampleRenderer.getAst.get)
    val embedTimed = TestUtils.EMBED_TIMED.get(exampleRenderer.options.get)

    val formattedTimingInfo = TestUtils.getFormattedTimingInfo(iterations, specExampleParse.startTime, specExampleParse.parseTime, render)
    if (timed || embedTimed) {
      System.out.print(formattedTimingInfo)
    }

    addSpecExample(exampleRenderer, specExampleParse, exampleOptions)
    exampleRenderer.finalizeRender()

    val expected: String = if (example.section.isDefined) {
      val outExpected = new StringBuilder()
      if (embedTimed) {
        outExpected.append(formattedTimingInfo)
      }
      TestUtils.addSpecExample(true, outExpected, source, expectedHtml, expectedAst, optionsSet, true, example.section, example.exampleNumber)
      outExpected.toString()
    } else {
      if (embedTimed) {
        formattedTimingInfo + TestUtils.addSpecExample(true, source, expectedHtml, expectedAst, optionsSet)
      } else {
        TestUtils.addSpecExample(true, source, expectedHtml, ast, optionsSet)
      }
    }

    val actual: String = if (example.section.isDefined) {
      val outActual = new StringBuilder()
      TestUtils.addSpecExample(true, outActual, source, html, ast, optionsSet, true, example.section, example.exampleNumber)
      outActual.toString()
    } else {
      TestUtils.addSpecExample(true, source, html, ast, optionsSet)
    }

    // JUnit 4: assertEquals — will need adaptation to munit later
    if (exampleOptions.exists(TestUtils.FAIL.get(_))) {
      // JUnit 4: thrown.expect(ComparisonFailure.class) — stubbed
    }

    if (message.nonEmpty) {
      assert(expected == actual, s"$message: expected != actual")
    } else {
      assert(expected == actual, "expected != actual")
    }
  }
}
