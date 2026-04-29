/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/TemplateReader.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
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

class TemplateReader protected (protected val inputStream: InputStream) {

  protected var state:       TemplateReader.State = TemplateReader.State.BEFORE
  protected var source:      StringBuilder        = new StringBuilder()
  protected var entryNumber: Int                  = 0

  protected var examples: ju.List[TemplateEntry] = new ju.ArrayList[TemplateEntry]()

  protected def read(): ju.List[TemplateEntry] = {
    resetContents()

    val reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
    var line   = reader.readLine()
    while (line != null) {
      processLine(line)
      line = reader.readLine()
    }

    examples
  }

  // can use these to generate spec from source
  protected def addSpecLine(line: String): Unit = {
    // default no-op
  }

  protected def addTemplateEntry(example: TemplateEntry): Unit =
    examples.add(example)

  protected def processLine(line: String): Unit = {
    var lineAbsorbed = false

    state match {
      case TemplateReader.State.BEFORE =>
        if (line.endsWith(TemplateReader.ENTRY_START)) {
          state = TemplateReader.State.SOURCE
          entryNumber += 1
          lineAbsorbed = true
        }
      case TemplateReader.State.SOURCE =>
        if (line.endsWith(TemplateReader.ENTRY_BREAK)) {
          state = TemplateReader.State.BEFORE
          addTemplateEntry(new TemplateEntry(entryNumber, source.toString()))
          resetContents()
          lineAbsorbed = true
        } else {
          // examples use "rightwards arrow" to show tab
          source.append(line).append('\n')
          lineAbsorbed = true
        }
    }

    if (!lineAbsorbed) {
      addSpecLine(line)
    }
  }

  protected def resetContents(): Unit =
    source = new StringBuilder()
}

object TemplateReader {

  val ENTRY_START: String = "```````````````````````````````` template"
  val ENTRY_BREAK: String = "````````````````````````````````"

  def readEntries(): ju.List[TemplateEntry] = readEntries(Nullable.empty, Nullable.empty)

  def readEntries(resource: Nullable[String]): ju.List[TemplateEntry] = readEntries(resource, Nullable.empty)

  def readEntries(resource: Nullable[String], readerFactory: Nullable[TemplateReaderFactory]): ju.List[TemplateEntry] =
    try {
      val stream = getSpecInputStream(resource)
      val reader = readerFactory.fold(new TemplateReader(stream))(f => f.create(stream))
      reader.read()
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }

  def readExamplesAsString(): ju.List[String] = readExamplesAsString(Nullable.empty, Nullable.empty)

  def readExamplesAsString(resource: Nullable[String]): ju.List[String] = readExamplesAsString(resource, Nullable.empty)

  def readExamplesAsString(resource: Nullable[String], readerFactory: Nullable[TemplateReaderFactory]): ju.List[String] = {
    val entries = readEntries(resource, readerFactory)
    val result  = new ju.ArrayList[String]()
    val iter    = entries.iterator()
    while (iter.hasNext)
      result.add(iter.next().source)
    result
  }

  def readSpec(): String = readSpec(Nullable.empty)

  def readSpec(resource: Nullable[String]): String = {
    val sb = new StringBuilder()
    try {
      val reader = new BufferedReader(new InputStreamReader(getSpecInputStream(resource), StandardCharsets.UTF_8))
      var line   = reader.readLine()
      while (line != null) {
        sb.append(line)
        sb.append("\n")
        line = reader.readLine()
      }
      sb.toString()
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
  }

  def getSpecInputStream(): InputStream = getSpecInputStream(Nullable.empty)

  def getSpecInputStream(resource: Nullable[String]): InputStream = {
    val specPath = resource.getOrElse("/template.txt")
    val stream   = classOf[TemplateReader].getResourceAsStream(specPath)
    if (stream == null) {
      throw new IllegalStateException("Could not load " + resource + " classpath resource")
    }
    stream
  }

  enum State extends java.lang.Enum[State] {
    case BEFORE, SOURCE
  }
}
