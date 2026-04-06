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
 *   Convention: Phase 10/11 skeleton — top-level entry point that wires the
 *               parser, evaluator, and serializer together. The current
 *               implementation returns a stub CompileResult.
 *   Idiom: Dart top-level functions -> Scala 3 object methods.
 */
package ssg
package sass

/** The result of compiling a Sass document to CSS. */
final case class CompileResult(css: String, sourceMap: Option[String], loadedUrls: Set[String])

/** Top-level Sass compilation entry points. */
object Compile {

  /** Compile a Sass/SCSS source string to CSS.
    *
    * Skeleton — currently returns an empty stub result. Will dispatch through
    * StylesheetParser -> EvaluateVisitor -> SerializeVisitor in a future phase.
    */
  def compileString(source: String): CompileResult =
    CompileResult(css = "", sourceMap = None, loadedUrls = Set.empty)

  /** Compile a Sass/SCSS file at the given path. */
  def compile(path: String): CompileResult =
    CompileResult(css = "", sourceMap = None, loadedUrls = Set.empty)
}
