/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only filesystem-aware compile entry point.
 */
package ssg
package sass

import ssg.sass.importer.{ FilesystemImporter, Importer }
import ssg.sass.visitor.OutputStyle

import scala.language.implicitConversions

/** Filesystem-backed Sass compilation. JVM-only. */
object CompileFile {

  /** Compile a Sass/SCSS file at the given path. The file's parent directory becomes the load path for `@import`/`@use` resolution.
    */
  def compile(path: String, style: String = OutputStyle.Expanded): CompileResult = {
    val file   = java.nio.file.Paths.get(path).toAbsolutePath
    val source = new String(
      java.nio.file.Files.readAllBytes(file),
      java.nio.charset.StandardCharsets.UTF_8
    )
    val loadPath = Option(file.getParent).map(_.toString).getOrElse(".")
    val importer: Importer = new FilesystemImporter(loadPath)
    Compile.compileString(source, style, Nullable(importer))
  }
}
