/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/TemplateTestCase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.{TemplateEntry, TemplateReader, TemplateReaderFactory}

import java.io.InputStream
import scala.language.implicitConversions

// JUnit 4: @Test annotation — will need adaptation to munit later
abstract class TemplateTestCase extends TemplateReaderFactory {

  private var dumpTemplateReader: Nullable[DumpTemplateReader] = Nullable.empty

  override def create(inputStream: InputStream): TemplateReader = {
    val reader = new DumpTemplateReader(inputStream, this)
    dumpTemplateReader = Nullable(reader)
    reader
  }

  def getExpandedEntry(entry: TemplateEntry, sb: StringBuilder): Unit

  protected def processTemplate(template: String, expandedTemplate: String): Unit = {
    if (outputTemplate()) {
      System.out.println(expandedTemplate)
    }
  }

  /**
   * @return return resource name for the spec to use for the examples of the test
   */
  protected def templateResourceName: String

  /**
   * @return return true if template to be dumped to stdout
   */
  protected def outputTemplate(): Boolean = true

  // JUnit 4: @Test — will need adaptation to munit later
  def testDumpSpec(): Unit = {
    val specResourcePath = templateResourceName
    TemplateReader.readEntries(Nullable(specResourcePath), Nullable(this))
    val fullSpec = TemplateReader.readSpec(Nullable(specResourcePath))
    val actual = dumpTemplateReader.get.template
    processTemplate(fullSpec, actual)
  }
}
