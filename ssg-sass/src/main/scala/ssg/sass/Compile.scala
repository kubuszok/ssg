/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/compile.dart, lib/src/compile_result.dart
 * Original: Copyright (c) 2021 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: compile.dart + compile_result.dart -> Compile.scala
 *   Convention: Wires StylesheetParser -> EvaluateVisitor -> SerializeVisitor.
 *   Idiom: Dart top-level functions -> Scala 3 object methods.
 */
package ssg
package sass

import ssg.sass.importer.Importer
import ssg.sass.parse.{ SassParser, ScssParser, StylesheetParser }
import ssg.sass.visitor.{ EvaluateVisitor, OutputStyle, SerializeVisitor }

import scala.language.implicitConversions

/** The result of compiling a Sass document to CSS. */
final case class CompileResult(
  css:        String,
  sourceMap:  Nullable[String] = Nullable.empty[String],
  loadedUrls: Set[String] = Set.empty,
  warnings:   List[String] = Nil
)

/** Top-level Sass compilation entry points. */
object Compile {

  /** Compile a Sass/SCSS source string to CSS.
    *
    * Wires the full pipeline: StylesheetParser → EvaluateVisitor → SerializeVisitor.
    *
    * @param source
    *   the Sass/SCSS source text
    * @param style
    *   the output style ("expanded" or "compressed")
    * @param importer
    *   optional importer for resolving `@import`/`@use` (JVM/Native only)
    */
  def compileString(
    source:    String,
    style:     String = OutputStyle.Expanded,
    importer:  Nullable[Importer] = Nullable.empty,
    sourceMap: Boolean = false,
    syntax:    Syntax = Syntax.Scss
  ): CompileResult = {
    // 1. Parse source to Sass AST. Pick the parser by syntax: indented
    //    Sass uses SassParser; SCSS (and CSS, for now) use ScssParser.
    val parser: StylesheetParser = syntax match {
      case Syntax.Sass => new SassParser(source)
      case _           => new ScssParser(source)
    }
    val sassAst = parser.parse()

    // 2. Evaluate Sass AST to CSS AST
    val evaluator = new EvaluateVisitor(importer = importer)
    val result    = evaluator.run(sassAst)

    // 3. Serialize CSS AST to text
    val serializer = new SerializeVisitor(style = style, sourceMap = sourceMap)
    val serialized = serializer.serialize(result.stylesheet)

    CompileResult(
      css = serialized.css,
      sourceMap = serialized.sourceMap,
      loadedUrls = result.loadedUrls,
      warnings = result.warnings
    )
  }

  /** Compile a Sass/SCSS file at the given path. JVM-only — overridden in `src/main/scala-jvm`. The JS/Native default throws.
    */
  def compile(path: String, style: String = OutputStyle.Expanded): CompileResult =
    throw new UnsupportedOperationException(
      "Compile.compile(path) requires filesystem access — JVM only."
    )
}
