/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/compile.dart
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
 * Covenant-dart-reference: lib/src/compile.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
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
    style:               OutputStyle = OutputStyle.Expanded,
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

    // dart-sass sass.dart:236-239: both `importers` and `loadPaths` are folded
    // into the ImportCache (`ImportCache(importers: importers, loadPaths:
    // loadPaths)`), and dart ALWAYS constructs an ImportCache here -- there is
    // no conditional. That matters because `ImportCache(...)` also folds in the
    // SASS_PATH environment variable (import_cache.dart:124/130-132), so a plain
    // `compileString` honors SASS_PATH on the VM even with no explicit
    // `importers`/`loadPaths`.
    //
    // In dart the entrypoint `importer` (compile.dart:160) is SEPARATE from the
    // ImportCache: the evaluator takes both, with the importer used as the base
    // importer (async_evaluate.dart:369 `_importCache = importCache ??
    // AsyncImportCache.none()`, importer tried via `baseImporter:`). The SSG
    // port flattened that: EvaluateVisitor builds its cache from the singular
    // `importer` when none is supplied (EvaluateVisitor.scala:427-432), so the
    // importer must live in the cache's importer list for scheme-bearing URLs
    // (e.g. `@import "pkg:foo"`, where the relative `baseImporter` path is
    // skipped). To keep ALWAYS constructing the cache without regressing that
    // singular-importer route, we fold `effectiveImporter` into the cache's
    // importers (first, so it is consulted before loadPaths/SASS_PATH), mirroring
    // what the evaluator's own fallback did.
    //
    // When the caller supplies an explicit `importCache`, that takes precedence
    // (matching the port's pre-existing one-importCache contract). dart guards
    // the SASS_PATH/loadPaths block behind `isBrowser` (import_cache.dart:125):
    // the JVM honors SASS_PATH while JS and Native skip it (documented in
    // LoadPathImporterPlatform), so on those platforms the construction folds in
    // only the singular importer when nothing else is passed. import_cache.dart:
    // 128-129 turns each load path into a filesystem importer.
    val effectiveImportCache: Nullable[ImportCache] =
      if (importCache.isDefined) importCache
      else
        Nullable(
          ImportCache.fromOptions(
            effectiveImporter :: importers.fold[List[Importer]](Nil)(_.toList),
            loadPaths.fold[List[String]](Nil)(_.toList),
            Nullable(deprecationLogger)
          )
        )

    val result = _compileStylesheet(
      stylesheet,
      Nullable(deprecationLogger),
      effectiveImportCache,
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
    * Cross-platform file-from-path compilation is not provided in shared code because it requires platform-specific filesystem access. This method throws `UnsupportedOperationException` by design
    * (fail-fast) on all platforms. On the JVM, use `CompileFile.compile` instead -- it reads the file, infers the syntax, and delegates to `compileString`.
    */
  def compile(
    path:                String,
    style:               OutputStyle = OutputStyle.Expanded,
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
      "Compile.compile(path) requires filesystem access -- use CompileFile.compile on JVM."
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
    style:       OutputStyle,
    quietDeps:   Boolean,
    sourceMap:   Boolean,
    charset:     Boolean
  ): CompileResult = {
    // 1. Evaluate Sass AST to CSS AST
    // dart-sass compile.dart:206 forwards `functions` and `quietDeps` into the
    // _EvaluateVisitor constructor (evaluate.dart:375/381).
    val evaluator = new EvaluateVisitor(
      importCache = importCache.fold(Nullable.empty[ImportCache])(ic => Nullable(ic)),
      logger = logger,
      importer = Nullable(importer),
      functions = functions,
      quietDeps = quietDeps
    )
    val evaluateResult = evaluator.run(stylesheet)

    // 2. Serialize CSS AST to text
    // dart-sass compile.dart:220 forwards `charset` into serialize()
    // (serialize.dart:55).
    val serializer      = new SerializeVisitor(style = style, sourceMap = sourceMap)
    val serializeResult = serializer.serialize(evaluateResult.stylesheet, charset = charset)

    CompileResult(
      css = serializeResult.css,
      sourceMap = serializeResult.sourceMap,
      loadedUrls = evaluateResult.loadedUrls,
      warnings = evaluateResult.warnings
    )
  }
}
