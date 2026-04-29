/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package misc
package test

import java.util.regex.Pattern

import scala.language.implicitConversions

final class TemplateUtilSuite extends munit.FunSuite {

  test("test_resolveRefs") {
    val pattern  = Pattern.compile("\\$\\{([a-zA-Z_$][a-zA-Z_0-9$]+)}")
    val resolver = new TemplateUtil.MappedResolver()

    resolver.set("FILE1", "/Users/name/home/file.ext")
    resolver.set("FILE2", "C:\\Users\\name\\home\\file.ext")
    resolver.set("FILE3", "C:\\Users\\name\\home\\$file.ext")

    assertEquals(
      TemplateUtil.resolveRefs("${FILE1}\n${FILE2}\n${FILE3}", pattern, resolver),
      "/Users/name/home/file.ext\nC:\\Users\\name\\home\\file.ext\nC:\\Users\\name\\home\\$file.ext"
    )
  }
}
