/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/DumpSpecReader.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.{ ResourceLocation, SpecExample, SpecReader }
import ssg.md.util.data.DataHolder

import java.io.InputStream
import scala.language.implicitConversions

class DumpSpecReader(
  stream:                 InputStream,
  protected val testCase: SpecExampleProcessor,
  location:               ResourceLocation,
  compoundSections:       Boolean
) extends SpecReader(stream, location, compoundSections) {

  protected val sb:             StringBuilder           = new StringBuilder()
  protected val sbExp:          StringBuilder           = new StringBuilder()
  protected var exampleComment: Nullable[StringBuilder] = Nullable.empty

  def fullSpec: String = sb.toString()

  def expectedFullSpec: String = sbExp.toString()

  override def readExamples(): Unit =
    super.readExamples()

  override def addSpecLine(line: String, isSpecExampleOpen: Boolean): Unit = {
    if (!isSpecExampleOpen) sb.append(line).append("\n")
    sbExp.append(line).append("\n")
  }

  override protected def addSpecExample(specExample: SpecExample): Unit = {
    // not needed but to keep it consistent with SpecReader
    super.addSpecExample(specExample)

    val example = testCase.checkExample(specExample)
    var exampleOptions: Nullable[DataHolder] = Nullable.empty
    var ignoredTestCase = false

    try
      exampleOptions = TestUtils.getOptions(example, example.optionsSet, testCase.options)
    catch {
      // JUnit 4: AssumptionViolatedException — stubbed as generic exception check
      case _: Exception =>
        ignoredTestCase = true
        exampleOptions = Nullable.empty
    }

    if (exampleOptions.exists(TestUtils.FAIL.get(_))) {
      ignoredTestCase = true
    }

    val exampleRenderer = testCase.getSpecExampleRenderer(example, exampleOptions)

    val exampleParse = new SpecExampleParse(exampleRenderer.options.get, exampleRenderer, exampleOptions, example.source)
    val source       = exampleParse.source
    val timed        = exampleParse.isTimed
    val iterations   = exampleParse.iterations
    val start        = exampleParse.startTime
    val parse        = exampleParse.parseTime

    val html: String = if (!ignoredTestCase) {
      val h = exampleRenderer.getHtml
      var i = 1
      while (i < iterations) {
        exampleRenderer.getHtml
        i += 1
      }
      h
    } else {
      example.html
    }
    val render = System.nanoTime()

    val embedTimed = TestUtils.EMBED_TIMED.get(exampleRenderer.options.get)

    val timingInfo = TestUtils.getFormattedTimingInfo(
      example.section.getOrElse(""),
      example.exampleNumber,
      iterations,
      start,
      parse,
      render
    )

    if (timed || embedTimed) {
      System.out.println(timingInfo)
    }

    val ast: Nullable[String] = if (example.ast.isEmpty) {
      Nullable.empty
    } else if (!ignoredTestCase) {
      exampleRenderer.getAst
    } else {
      example.ast
    }

    // allow other formats to accumulate
    testCase.addFullSpecExample(exampleRenderer, exampleParse, exampleOptions, ignoredTestCase, html, ast)
    exampleRenderer.finalizeRender()

    if (embedTimed) {
      sb.append(timingInfo)
    }

    // include source so that diff can be used to update spec
    TestUtils.addSpecExample(true, sb, source, html, ast, example.optionsSet, exampleRenderer.includeExampleInfo, example.section, example.exampleNumber)
    TestUtils.addSpecExample(
      false,
      sbExp,
      source,
      example.html,
      example.ast,
      example.optionsSet,
      exampleRenderer.includeExampleInfo,
      example.section,
      example.exampleNumber
    )
  }
}
