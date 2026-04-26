/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only filesystem-aware compile entry point.
 */
package ssg
package sass

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.importer.{ FilesystemImporter, Importer }
import ssg.sass.visitor.OutputStyle

import scala.language.implicitConversions

/** Filesystem-backed Sass compilation. JVM-only. */
object CompileFile {

  /** Compile a Sass/SCSS file at the given path. The file's parent directory becomes the load path for `@import`/`@use` resolution.
    */
  def compile(path: String, style: String = OutputStyle.Expanded): CompileResult = {
    val file     = FilePath.of(path).toAbsolute
    val source   = FileOps.readString(file)
    val loadPath = file.parent.map(_.pathString).getOrElse(".")
    val importer: Importer = new FilesystemImporter(loadPath)
    Compile.compileString(source, style, Nullable(importer))
  }
}
