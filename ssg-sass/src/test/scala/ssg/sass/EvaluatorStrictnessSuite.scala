/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Spec-compliance gaps: ISS-026 mixed-decls, ISS-027 @at-root queries,
 * ISS-028 slash-div deprecation, ISS-033 private-var module config.
 */
package ssg
package sass

import scala.language.implicitConversions

final class EvaluatorStrictnessSuite extends munit.FunSuite {

  // ISS-026 — mixed-decls ----------------------------------------------------

  test("ISS-026: declaration after nested rule emits mixed-decls warning") {
    val src =
      """|.a {
         |  .b { color: red; }
         |  color: blue;
         |}""".stripMargin
    val result = Compile.compileString(src)
    assert(
      result.warnings.exists(_.contains("mixed-decls")) ||
        result.warnings.exists(_.contains("nested rules")),
      s"Expected mixed-decls warning, got: ${result.warnings}"
    )
  }

  test("ISS-026: declaration before nested rule does NOT warn") {
    val src =
      """|.a {
         |  color: blue;
         |  .b { color: red; }
         |}""".stripMargin
    val result = Compile.compileString(src)
    assert(
      !result.warnings.exists(_.contains("mixed-decls")),
      s"Did not expect mixed-decls, got: ${result.warnings}"
    )
  }

  // ISS-028 — slash-div deprecation ------------------------------------------

  test("ISS-028: numeric / numeric emits slash-div") {
    val result = Compile.compileString("$x: 10px; a { width: $x / 2; }")
    assert(
      result.warnings.exists(_.contains("slash-div")) ||
        result.warnings.exists(_.contains("/ for division")),
      s"Expected slash-div warning, got: ${result.warnings}"
    )
  }

  test("ISS-028: math.div does not emit slash-div") {
    val result = Compile.compileString("@use 'sass:math'; a { width: math.div(10px, 2); }")
    assert(
      !result.warnings.exists(_.contains("slash-div")),
      s"Did not expect slash-div warning, got: ${result.warnings}"
    )
  }

  // ISS-033 — private-var module config --------------------------------------

  test("ISS-033: @use with ($_private: ...) emits deprecation warning") {
    // dart-sass: configuring private variables is deprecated (not yet an error).
    // The configuration succeeds but emits a with-private deprecation warning.
    // MapImporter keys use .scss extension to match Sass import resolution rules.
    val importer = new ssg.sass.importer.MapImporter(
      Map("lib.scss" -> "$_priv: 1 !default; a { x: $_priv; }")
    )
    val result = Compile.compileString(
      """@use "lib" with ($_priv: 2);""",
      importer = importer
    )
    assert(
      result.warnings.exists(w => w.contains("with-private") || w.contains("private variables")),
      s"Expected 'with-private' deprecation warning, got: ${result.warnings}"
    )
    assert(result.css.contains("x: 2"), s"expected x: 2 in ${result.css}")
  }

  test("ISS-033: @use with ($-dash: ...) emits deprecation warning") {
    // dart-sass: configuring private variables is deprecated (not yet an error).
    // MapImporter keys use .scss extension to match Sass import resolution rules.
    val importer = new ssg.sass.importer.MapImporter(
      Map("lib.scss" -> "$-dash: 1 !default; a { x: $-dash; }")
    )
    val result = Compile.compileString(
      """@use "lib" with ($-dash: 2);""",
      importer = importer
    )
    assert(
      result.warnings.exists(w => w.contains("with-private") || w.contains("private variables")),
      s"Expected 'with-private' deprecation warning, got: ${result.warnings}"
    )
    assert(result.css.contains("x: 2"), s"expected x: 2 in ${result.css}")
  }

  test("ISS-033: public var in with(...) is accepted") {
    // MapImporter keys use .scss extension to match Sass import resolution rules.
    val importer = new ssg.sass.importer.MapImporter(
      Map("lib.scss" -> "$pub: 1 !default; a { x: $pub; }")
    )
    val result = Compile.compileString(
      """@use "lib" with ($pub: 2);""",
      importer = importer
    )
    assert(result.css.contains("x: 2"), s"expected x: 2 in ${result.css}")
  }

  // ISS-027 — @at-root queries -----------------------------------------------

  test("ISS-027: @at-root (without: media) lifts out of @media") {
    val src =
      """|@media (max-width: 600px) {
         |  .a { @at-root (without: media) { color: red; } }
         |}""".stripMargin
    val css = Compile.compileString(src, ssg.sass.visitor.OutputStyle.Compressed).css
    // No @media wrapper expected around the inner .a block.
    assert(!css.startsWith("@media"), s"expected no leading @media, got: $css")
    assert(css.contains("color:red") || css.contains("color: red"))
  }

  test("ISS-027: @at-root (with: media) with bare declaration errors") {
    // dart-sass: @at-root (with: media) inside a style rule excludes the style
    // rule, so bare declarations are invalid. Verified against dart-sass 1.x.
    val src =
      """|@media (max-width: 600px) {
         |  .a { @at-root (with: media) { color: red; } }
         |}""".stripMargin
    val ex = intercept[SassException] {
      Compile.compileString(src, ssg.sass.visitor.OutputStyle.Compressed)
    }
    assert(ex.getMessage.contains("Declarations may only be used within style rules"))
  }
}
