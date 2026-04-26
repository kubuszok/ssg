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
 *          NodeImporter / isBrowser branches omitted (no JS-specific API).
 *          Async variants omitted (synchronous only).
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/compile.dart, lib/src/compile_result.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass

import ssg.sass.importer.{ Importer, NoOpImporter }
import ssg.sass.parse.{ CssParser, SassParser, ScssParser, StylesheetParser }
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
    * Wires the full pipeline: StylesheetParser -> EvaluateVisitor -> SerializeVisitor.
    *
    * The first two positional parameters preserve the original calling convention (source, style) used throughout the test suite.
    *
    * At most one of `importCache` and `nodeImporter` may be provided at once (nodeImporter is omitted in the SSG port because we have no JS-specific API).
    */
  def compileString(
    source:              String,
    style:               String = OutputStyle.Expanded,
    importer:            Nullable[Importer] = Nullable.empty,
    sourceMap:           Boolean = false,
    syntax:              Syntax = Syntax.Scss,
    logger:              Nullable[Logger] = Nullable.empty,
    importCache:         Nullable[ImportCache] = Nullable.empty,
    importers:           Nullable[Iterable[Importer]] = Nullable.empty,
    loadPaths:           Nullable[Iterable[String]] = Nullable.empty,
    functions:           Nullable[Iterable[Callable]] = Nullable.empty,
    url:                 Nullable[String] = Nullable.empty,
    quietDeps:           Boolean = false,
    verbose:             Boolean = false,
    charset:             Boolean = true,
    silenceDeprecations: Nullable[Iterable[Deprecation]] = Nullable.empty,
    fatalDeprecations:   Nullable[Iterable[Deprecation]] = Nullable.empty,
    futureDeprecations:  Nullable[Iterable[Deprecation]] = Nullable.empty
  ): CompileResult = {
    // Wrap the user's logger with deprecation processing
    val deprecationLogger = new DeprecationProcessingLogger(
      logger.getOrElse(Logger.default),
      silenceDeprecations = silenceDeprecations.fold(Set.empty[Deprecation])(_.toSet),
      fatalDeprecations = fatalDeprecations.fold(Set.empty[Deprecation])(_.toSet),
      futureDeprecations = futureDeprecations.fold(Set.empty[Deprecation])(_.toSet),
      limitRepetition = !verbose
    )
    deprecationLogger.validate()

    // Parse the source string
    val parser: StylesheetParser = syntax match {
      case Syntax.Sass => new SassParser(source, url)
      case Syntax.Css  => new CssParser(source, url)
      case Syntax.Scss => new ScssParser(source, url)
    }
    val stylesheet = parser.parse()

    val effectiveImporter = importer.getOrElse(new NoOpImporter())

    val result = _compileStylesheet(
      stylesheet,
      Nullable(deprecationLogger),
      importCache,
      effectiveImporter,
      functions,
      style,
      quietDeps,
      sourceMap,
      charset
    )

    deprecationLogger.summarize()
    result
  }

  /** Compile a Sass/SCSS file at the given path.
    *
    * This cross-platform default throws -- the JVM override in `CompileFile` provides the real implementation using FilesystemImporter.
    */
  def compile(
    path:                String,
    style:               String = OutputStyle.Expanded,
    syntax:              Nullable[Syntax] = Nullable.empty,
    logger:              Nullable[Logger] = Nullable.empty,
    importCache:         Nullable[ImportCache] = Nullable.empty,
    functions:           Nullable[Iterable[Callable]] = Nullable.empty,
    quietDeps:           Boolean = false,
    verbose:             Boolean = false,
    sourceMap:           Boolean = false,
    charset:             Boolean = true,
    silenceDeprecations: Nullable[Iterable[Deprecation]] = Nullable.empty,
    fatalDeprecations:   Nullable[Iterable[Deprecation]] = Nullable.empty,
    futureDeprecations:  Nullable[Iterable[Deprecation]] = Nullable.empty
  ): CompileResult =
    throw new UnsupportedOperationException(
      "Compile.compile(path) requires filesystem access -- use CompileFile on JVM."
    )

  /** Compiles [stylesheet] and returns its result.
    *
    * Arguments are handled as for [compileString].
    */
  private def _compileStylesheet(
    stylesheet:  ssg.sass.ast.sass.Stylesheet,
    logger:      Nullable[Logger],
    importCache: Nullable[ImportCache],
    importer:    Importer,
    functions:   Nullable[Iterable[Callable]],
    style:       String,
    quietDeps:   Boolean,
    sourceMap:   Boolean,
    charset:     Boolean
  ): CompileResult = {
    // 1. Evaluate Sass AST to CSS AST
    val evaluator = new EvaluateVisitor(
      importCache = importCache.fold(Nullable.empty[ImportCache])(ic => Nullable(ic)),
      logger = logger,
      importer = Nullable(importer)
    )
    val evaluateResult = evaluator.run(stylesheet)

    // 2. Serialize CSS AST to text
    val serializer      = new SerializeVisitor(style = style, sourceMap = sourceMap)
    val serializeResult = serializer.serialize(evaluateResult.stylesheet)

    CompileResult(
      css = serializeResult.css,
      sourceMap = serializeResult.sourceMap,
      loadedUrls = evaluateResult.loadedUrls,
      warnings = evaluateResult.warnings
    )
  }
}
