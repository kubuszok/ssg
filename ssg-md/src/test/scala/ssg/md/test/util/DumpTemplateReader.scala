/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/DumpTemplateReader.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.test.util.spec.{TemplateEntry, TemplateReader}

import java.io.InputStream

class DumpTemplateReader(stream: InputStream, private val testCase: TemplateTestCase) extends TemplateReader(stream) {

  private val sb: StringBuilder = new StringBuilder()

  def template: String = sb.toString()

  override protected def addSpecLine(line: String): Unit = {
    sb.append(line).append("\n")
  }

  override protected def addTemplateEntry(entry: TemplateEntry): Unit = {
    testCase.getExpandedEntry(entry, sb)
  }
}
