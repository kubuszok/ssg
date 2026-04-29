/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/SpecExampleParse.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.util.data.DataHolder
import ssg.md.util.misc.Utils.suffixWith
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

final class SpecExampleParse(
  val options:          DataHolder,
  val renderer:         SpecExampleRenderer,
  val exampleOptions:   Nullable[DataHolder],
  private var mySource: String
) {

  private var myTimed:      Boolean = false
  private var myIterations: Int     = 1
  private var myStartTime:  Long    = 0L
  private var myParseTime:  Long    = 0L

  // parse on construction
  parse(mySource)

  def source: String = mySource

  def isTimed: Boolean = myTimed

  def iterations: Int = myIterations

  def startTime: Long = myStartTime

  def parseTime: Long = myParseTime

  def parse(source: String): String = {
    if (TestUtils.NO_FILE_EOL.get(options)) {
      mySource = TestUtils.trimTrailingEOL(source)
    }

    val sourcePrefix = TestUtils.SOURCE_PREFIX.get(exampleOptions)
    val sourceSuffix = TestUtils.SOURCE_SUFFIX.get(exampleOptions)
    val sourceIndent = TestUtils.SOURCE_INDENT.get(exampleOptions)

    val input: BasedSequence = if (sourcePrefix.nonEmpty || sourceSuffix.nonEmpty) {
      val combinedSource = sourcePrefix + suffixWith(mySource, "\n") + sourceSuffix
      BasedSequence.of(combinedSource).subSequence(0, combinedSource.length).subSequence(sourcePrefix.length, combinedSource.length - sourceSuffix.length)
    } else {
      BasedSequence.of(mySource)
    }

    val strippedInput = TestUtils.stripIndent(input, sourceIndent)

    val includedText = TestUtils.INCLUDED_DOCUMENT.get(exampleOptions)

    renderer.includeDocument(includedText)

    myTimed = TestUtils.TIMED.get(exampleOptions)
    myIterations = if (myTimed) TestUtils.TIMED_ITERATIONS.get(exampleOptions) else 1

    myStartTime = System.nanoTime()

    renderer.parse(strippedInput.toString)
    var i = 1
    while (i < myIterations) {
      renderer.parse(strippedInput)
      i += 1
    }
    myParseTime = System.nanoTime()

    renderer.finalizeDocument()
    mySource
  }

  def finalizeRender(): Unit =
    renderer.finalizeRender()
}
