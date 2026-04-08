/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package util

import ssg.sass.util.StringUtil.*

final class StringUtilSuite extends munit.FunSuite {

  test("toCssIdentifier passes through simple identifiers") {
    assertEquals("hello".toCssIdentifier, "hello")
    assertEquals("foo-bar".toCssIdentifier, "foo-bar")
    assertEquals("_private".toCssIdentifier, "_private")
  }

  test("toCssIdentifier escapes leading digits") {
    assertEquals("123".toCssIdentifier, "\\31 23")
  }

  test("toCssIdentifier handles single dash") {
    assertEquals("-".toCssIdentifier, "\\2d")
  }

  test("toCssIdentifier handles double dash") {
    assertEquals("--custom".toCssIdentifier, "--custom")
  }

  test("toCssIdentifier escapes special characters") {
    val result = "a b".toCssIdentifier
    assert(result.contains("\\"))
  }

  test("toCssIdentifier rejects empty string") {
    intercept[StringScannerException] {
      "".toCssIdentifier
    }
  }

  test("codeUnitAtOrNull returns -1 for out-of-bounds") {
    assertEquals(StringUtil.codeUnitAtOrNull("abc", 0), 'a'.toInt)
    assertEquals(StringUtil.codeUnitAtOrNull("abc", 5), -1)
  }
}
