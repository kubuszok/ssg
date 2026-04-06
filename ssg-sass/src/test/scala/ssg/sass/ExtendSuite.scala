/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

final class ExtendSuite extends munit.FunSuite {

  test("@extend of missing target without !optional raises SassException") {
    val src =
      """.a { @extend .missing; color: red; }""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src)
    }
    assert(
      e.sassMessage.contains("The target selector was not found"),
      s"unexpected message: ${e.sassMessage}"
    )
    assert(
      e.sassMessage.contains("!optional"),
      s"expected !optional hint in: ${e.sassMessage}"
    )
  }

  test("@extend of missing target with !optional is silent") {
    val src = ".a { @extend .missing !optional; color: red; }"
    val result = Compile.compileString(src, OutputStyle.Compressed)
    assertEquals(result.css, ".a{color:red;}")
    assertEquals(result.warnings, Nil)
  }

  test("@extend with compound target raises 'compound selectors may no longer be extended'") {
    val src =
      """.base { color: red; }
        |.x { @extend .a.b; }""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src)
    }
    assert(
      e.sassMessage.contains("compound selectors may no longer be extended"),
      s"unexpected message: ${e.sassMessage}"
    )
  }

  test("@extend with complex (descendant) target raises the compound-selector error") {
    val src =
      """.a .b { color: red; }
        |.x { @extend .a .b; }""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src)
    }
    assert(
      e.sassMessage.contains("compound selectors may no longer be extended"),
      s"unexpected message: ${e.sassMessage}"
    )
  }

  test("@extend inside @media only applies to rules in the same @media block") {
    val src =
      """.foo { color: red; }
        |@media screen {
        |  .foo { color: blue; }
        |  .bar { @extend .foo; }
        |}""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    // Inside @media, `.bar` should extend `.foo`.
    assert(css.contains("@media screen"), s"missing @media in: $css")
    val mediaIdx = css.indexOf("@media")
    val afterMedia = css.substring(mediaIdx)
    assert(
      afterMedia.contains(".foo,.bar") || afterMedia.contains(".foo, .bar"),
      s"expected .foo,.bar inside @media in: $css"
    )
    // The top-level `.foo` rule must NOT have `.bar` appended — extend is
    // scoped to its media context.
    val beforeMedia = css.substring(0, mediaIdx)
    assert(
      !beforeMedia.contains(".bar"),
      s"top-level .foo should not be extended by .bar; got: $beforeMedia"
    )
  }

  test("CompileResult exposes an empty warnings list by default") {
    val result = Compile.compileString(".a { color: red; }")
    assertEquals(result.warnings, Nil)
  }
}
