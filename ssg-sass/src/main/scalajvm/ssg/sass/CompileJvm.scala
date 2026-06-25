/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only filesystem-aware compile entry point. */
package ssg
package sass

import ssg.commons.io.{ FileOps, FilePathPlatform }
import ssg.sass.importer.{ FilesystemImporter, Importer, ImporterFileUtils }
import ssg.sass.visitor.OutputStyle

import scala.language.implicitConversions

/** Filesystem-backed Sass compilation. JVM-only. */
object CompileFile {

  /** Compile a Sass/SCSS file at the given path. The file's parent directory becomes the load path for `@import`/`@use` resolution.
    */
  def compile(path: String, style: OutputStyle = OutputStyle.Expanded): CompileResult = {
    val file   = FilePathPlatform.fromNioPath(java.nio.file.Paths.get(path)).toAbsolute
    val source = FileOps.readString(file)
    // file.parent.pathString is a POSIX-model string ("/C:/..." on Windows);
    // the host String ctor calls Paths.get(...) which throws on "/C:/...", so
    // convert back to a host path via toNioPath (ISS-1339 sub-cause A).
    // On POSIX this is a no-op (model == host for POSIX paths).
    val loadPath = file.parent.map(p => FilePathPlatform.toNioPath(p).toString).getOrElse(".")
    val importer: Importer = new FilesystemImporter(loadPath)
    // dart-sass compile.dart:72/82: syntax ?? Syntax.forPath(path)
    // dart-sass syntax.dart:21-24 / port Syntax.scala:40-43
    val syntax = Syntax.forPath(path)
    // dart-sass compile.dart:83: url: p.toUri(path) — thread the file URL so
    // error spans and loadedUrls carry the entry-file path instead of <unknown>.
    val fileUrl = ImporterFileUtils.toFileUri(file.pathString)
    Compile.compileString(
      source,
      style,
      importer = Nullable(importer),
      syntax = syntax,
      url = Nullable(fileUrl)
    )
  }
}
