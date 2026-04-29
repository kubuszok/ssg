/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/SpecReader.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package spec

import ssg.md.Nullable

import java.io.{ BufferedReader, IOException, InputStream, InputStreamReader }
import java.nio.charset.StandardCharsets
import java.{ util => ju }
import scala.language.implicitConversions
import java.util.regex.Pattern

class SpecReader(
  protected val inputStream:      InputStream,
  protected val resourceLocation: ResourceLocation,
  protected val compoundSections: Boolean
) {

  protected val examples: ju.List[SpecExample] = new ju.ArrayList[SpecExample]()

  protected val sections:         Array[Nullable[String]] = new Array[Nullable[String]](7) // 0 is not used and signals no section when indexed by lastSectionLevel
  protected var lastSectionLevel: Int                     = 1

  protected var state:             SpecReader.State        = SpecReader.State.BEFORE
  protected var section:           Nullable[String]        = Nullable.empty
  protected var optionsSet:        String                  = ""
  protected var source:            StringBuilder           = new StringBuilder()
  protected var html:              StringBuilder           = new StringBuilder()
  protected var ast:               StringBuilder           = new StringBuilder()
  protected var comment:           Nullable[StringBuilder] = Nullable.empty
  protected var exampleNumber:     Int                     = 0
  protected var lineNumber:        Int                     = 0
  protected var contentLineNumber: Int                     = 0
  protected var commentLineNumber: Int                     = 0

  def fileUrl: String = resourceLocation.fileUrl

  def getExamples: ju.List[SpecExample] = examples

  def getExamplesSourceAsString: ju.List[String] = {
    val result = new ju.ArrayList[String]()
    val iter   = examples.iterator()
    while (iter.hasNext)
      result.add(iter.next().source)
    result
  }

  def readExamples(): Unit =
    try {
      resetContents()

      var line: String = null // Java interop: readLine returns null at EOF
      lineNumber = 0
      val reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
      line = reader.readLine()
      while (line != null) {
        lineNumber += 1
        processLine(line)
        line = reader.readLine()
      }

      if (state == SpecReader.State.COMMENT) {
        // unterminated comment
        throw new IllegalStateException("Unterminated comment\n" + resourceLocation.getFileUrl(commentLineNumber))
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }

  // can use these to generate spec from source
  protected def addSpecLine(line: String, isSpecExampleOpen: Boolean): Unit = {
    // default no-op
  }

  protected def addSpecExample(example: SpecExample): Unit =
    examples.add(example)

  protected def processLine(line: String): Unit = {
    var lineAbsorbed  = false
    var lineProcessed = false

    state match {
      case SpecReader.State.COMMENT =>
        // look for comment end
        val trimmed = line.trim

        if (trimmed.startsWith("-->")) {
          if (trimmed.endsWith("<!--")) {
            // reset line number
            commentLineNumber = lineNumber - 1
          } else {
            state = SpecReader.State.BEFORE
          }
          lineProcessed = true
        }

      case SpecReader.State.BEFORE =>
        val trimmed = line.trim
        if (trimmed.startsWith("<!--")) {
          if (!trimmed.endsWith("-->")) {
            state = SpecReader.State.COMMENT
            commentLineNumber = lineNumber - 1
          }
          lineProcessed = true
        } else {
          val matcher = SpecReader.SECTION_PATTERN.matcher(line)
          if (matcher.matches()) {
            if (compoundSections) {
              val pair = TestUtils.addSpecSection(matcher.group(), matcher.group(1), sections)
              lastSectionLevel = pair.second.get
              section = pair.first
            } else {
              section = Nullable(matcher.group(1))
            }

            lineProcessed = true
            exampleNumber = 0
          } else if (line.startsWith(SpecReader.EXAMPLE_START) || line.startsWith(SpecReader.EXAMPLE_START_NBSP)) {
            val optionMatcher = SpecReader.OPTIONS_PATTERN.matcher(line.subSequence(SpecReader.EXAMPLE_START.length, line.length))
            if (optionMatcher.matches()) {
              optionsSet = optionMatcher.group(1)
            }

            state = SpecReader.State.SOURCE
            exampleNumber += 1
            contentLineNumber = lineNumber
            // NOTE: let dump spec reader get the actual definition line for comparison
            // lineAbsorbed = true
          }
        }

      case SpecReader.State.SOURCE =>
        if (line == SpecReader.SECTION_BREAK) {
          state = SpecReader.State.HTML
        } else {
          // examples use "rightwards arrow" to show tab
          val processedLine = TestUtils.fromVisibleSpecText(line)
          source.append(processedLine).append('\n')
        }
        lineAbsorbed = true

      case SpecReader.State.HTML =>
        if (line == SpecReader.EXAMPLE_BREAK) {
          state = SpecReader.State.BEFORE
          addSpecExample(
            new SpecExample(
              resourceLocation,
              contentLineNumber,
              Nullable(optionsSet),
              section,
              exampleNumber,
              source.toString(),
              html.toString(),
              Nullable.empty,
              comment.map(_.toString)
            )
          )
          resetContents()
          lineAbsorbed = true
        } else if (line == SpecReader.SECTION_BREAK) {
          state = SpecReader.State.AST
          lineAbsorbed = true
        } else {
          val processedLine = TestUtils.fromVisibleSpecText(line)
          html.append(processedLine).append('\n')
          lineAbsorbed = true
        }

      case SpecReader.State.AST =>
        if (line == SpecReader.EXAMPLE_BREAK) {
          state = SpecReader.State.BEFORE
          addSpecExample(
            new SpecExample(
              resourceLocation,
              contentLineNumber,
              Nullable(optionsSet),
              section,
              exampleNumber,
              source.toString(),
              html.toString(),
              Nullable(ast.toString()),
              comment.map(_.toString)
            )
          )
          resetContents()
        } else {
          ast.append(line).append('\n')
        }
        lineAbsorbed = true
    }

    if (!lineAbsorbed) {
      if (lineProcessed) {
        comment = Nullable.empty
      } else if (section.isDefined && state == SpecReader.State.BEFORE) {
        if (comment.isEmpty) {
          comment = Nullable(new StringBuilder())
        }
        comment.foreach(_.append(line).append('\n'))
      }
      addSpecLine(line, state != SpecReader.State.BEFORE && state != SpecReader.State.COMMENT)
    }
  }

  protected def resetContents(): Unit = {
    optionsSet = ""
    source = new StringBuilder()
    html = new StringBuilder()
    ast = new StringBuilder()
    comment = Nullable.empty
    contentLineNumber = 0
  }
}

object SpecReader {

  val EXAMPLE_KEYWORD:           String  = "example"
  val EXAMPLE_BREAK:             String  = "````````````````````````````````"
  val EXAMPLE_START:             String  = EXAMPLE_BREAK + " " + EXAMPLE_KEYWORD
  val EXAMPLE_START_NBSP:        String  = EXAMPLE_BREAK + "\u00A0" + EXAMPLE_KEYWORD
  val EXAMPLE_TEST_BREAK:        String  = "````````````````"
  val EXAMPLE_TEST_START:        String  = EXAMPLE_TEST_BREAK + " " + EXAMPLE_KEYWORD
  val OPTIONS_KEYWORD:           String  = "options"
  val OPTIONS_STRING:            String  = " " + OPTIONS_KEYWORD
  val OPTIONS_PATTERN:           Pattern = Pattern.compile(".*(?:\\s|\u00A0)\\Q" + OPTIONS_KEYWORD + "\\E(?:\\s|\u00A0)*\\((?:\\s|\u00A0)*(.*)(?:\\s|\u00A0)*\\)(?:\\s|\u00A0)*")
  val SECTION_BREAK:             String  = "."
  val SECTION_TEST_BREAK:        String  = "\u2026"
  protected val SECTION_PATTERN: Pattern = Pattern.compile("#{1,6} +(.*)")

  def create(location: ResourceLocation, compoundSections: Boolean): SpecReader =
    create(location, (stream, loc) => new SpecReader(stream, loc, compoundSections))

  def create[S <: SpecReader](location: ResourceLocation, readerFactory: SpecReaderFactory[S]): S = {
    val stream = location.resourceInputStream
    readerFactory.create(stream, location)
  }

  def createAndReadExamples(location: ResourceLocation, compoundSections: Boolean): SpecReader =
    createAndReadExamples(location, (stream, loc) => new SpecReader(stream, loc, compoundSections))

  def createAndReadExamples[S <: SpecReader](location: ResourceLocation, readerFactory: SpecReaderFactory[S]): S = {
    val reader = create(location, readerFactory)
    reader.readExamples()
    reader
  }

  enum State extends java.lang.Enum[State] {
    case BEFORE, SOURCE, HTML, AST, COMMENT
  }
}
