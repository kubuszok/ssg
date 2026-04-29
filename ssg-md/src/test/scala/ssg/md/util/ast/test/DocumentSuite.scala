/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package ast
package test

import ssg.md.util.data.MutableDataSet
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

final class DocumentSuite extends munit.FunSuite {

  test("testLineNumberWithUnixEol") {
    val document = new Document(new MutableDataSet(), BasedSequence.of("Hello\nWorld").subSequence(0, "Hello\nWorld".length))

    assertEquals(document.getLineNumber(0), 0)
    assertEquals(document.getLineNumber(5), 0)
    assertEquals(document.getLineNumber(6), 1)
    assertEquals(document.getLineNumber(10), 1)
  }

  test("testLineNumberWithUnixEol2") {
    val document = new Document(new MutableDataSet(), BasedSequence.of("Hello\n\nWorld").subSequence(0, "Hello\n\nWorld".length))

    assertEquals(document.getLineNumber(0), 0)
    assertEquals(document.getLineNumber(5), 0)
    assertEquals(document.getLineNumber(6), 1)
    assertEquals(document.getLineNumber(7), 2)
    assertEquals(document.getLineNumber(8), 2)
    assertEquals(document.getLineNumber(10), 2)
  }

  test("testLineNumberWithWindowsEol") {
    val document = new Document(new MutableDataSet(), BasedSequence.of("Hello\r\nWorld").subSequence(0, "Hello\r\nWorld".length))

    assertEquals(document.getLineNumber(0), 0)
    assertEquals(document.getLineNumber(5), 0)
    assertEquals(document.getLineNumber(6), 0)
    assertEquals(document.getLineNumber(7), 1)
    assertEquals(document.getLineNumber(11), 1)
  }

  test("testLineNumberWithWindowsEol2") {
    val document = new Document(new MutableDataSet(), BasedSequence.of("Hello\r\n\r\nWorld").subSequence(0, "Hello\r\n\r\nWorld".length))

    assertEquals(document.getLineNumber(0), 0)
    assertEquals(document.getLineNumber(5), 0)
    assertEquals(document.getLineNumber(6), 0)
    assertEquals(document.getLineNumber(7), 1)
    assertEquals(document.getLineNumber(8), 1)
    assertEquals(document.getLineNumber(9), 2)
    assertEquals(document.getLineNumber(10), 2)
    assertEquals(document.getLineNumber(11), 2)
  }

  test("testLineNumberWithOldMacEol") {
    val document = new Document(new MutableDataSet(), BasedSequence.of("Hello\rWorld").subSequence(0, "Hello\rWorld".length))

    assertEquals(document.getLineNumber(0), 0)
    assertEquals(document.getLineNumber(5), 0)
    assertEquals(document.getLineNumber(6), 1)
    assertEquals(document.getLineNumber(10), 1)
  }

  test("testLineNumberWithOldMacEol2") {
    val document = new Document(new MutableDataSet(), BasedSequence.of("Hello\r\rWorld").subSequence(0, "Hello\r\rWorld".length))

    assertEquals(document.getLineNumber(0), 0)
    assertEquals(document.getLineNumber(5), 0)
    assertEquals(document.getLineNumber(6), 1)
    assertEquals(document.getLineNumber(7), 2)
    assertEquals(document.getLineNumber(8), 2)
    assertEquals(document.getLineNumber(10), 2)
  }
}
