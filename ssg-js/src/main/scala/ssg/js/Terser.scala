/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Public API for the Terser JavaScript minifier.
 *
 * Original source: terser lib/minify.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: minify() → Terser.minify()
 *   Convention: Immutable options, pure function API
 *   Idiom: Implements ssg.minify.JsCompressor for integration with ssg-minify
 *   Gap: 93 LOC vs upstream 413 LOC (~23%). Happy-path orchestration only.
 *     Missing: ecma version normalization, mangle cache, format/output option
 *     resolution, structured error shape, source-map integration (sourcemap.js
 *     not ported at all). See ISS-033, ISS-034. docs/architecture/terser-port.md.
 *   Audited: 2026-04-07 (major_issues)
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/minify.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 6e7323fd4b0e255a06f6d3a2dcd111b8640a9031
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.{ Parser, ParserOptions }
import ssg.js.output.{ OutputOptions, OutputStream }
import ssg.js.scope.{ Mangler, ManglerOptions, ScopeAnalysis }
import ssg.js.compress.{ Compressor, CompressorOptions }

/** Options for the Terser minifier. */
final case class MinifyOptions(
  parse:    ParserOptions = ParserOptions(),
  compress: CompressorOptions | Boolean = CompressorOptions(),
  mangle:   ManglerOptions | Boolean = ManglerOptions(),
  output:   OutputOptions = OutputOptions()
)

object MinifyOptions {
  val Defaults: MinifyOptions = MinifyOptions()

  /** Minify with no compression or mangling — just parse and output. */
  val NoOptimize: MinifyOptions = MinifyOptions(compress = false, mangle = false)
}

/** Result of a Terser minification. */
final case class MinifyResult(
  code:      String,
  ast:       AstToplevel,
  sourceMap: ssg.js.sourcemap.SourceMapData | Null = null
)

/** Terser JavaScript minifier — public API. */
object Terser {

  /** Minify JavaScript source code.
    *
    * @param code
    *   JavaScript source code
    * @param options
    *   minification options
    * @return
    *   MinifyResult with minified code and AST
    */
  def minify(code: String, options: MinifyOptions = MinifyOptions.Defaults): MinifyResult = {
    // 1. Parse
    val parser = new Parser(options.parse)
    var ast    = parser.parse(code)

    // 2. Compress (if enabled)
    options.compress match {
      case compressOpts: CompressorOptions =>
        ScopeAnalysis.figureOutScope(ast)
        val compressor = new Compressor(compressOpts)
        ast = compressor.compress(ast)
      case _: Boolean =>
      // compression disabled
    }

    // 3. Mangle (if enabled)
    options.mangle match {
      case mangleOpts: ManglerOptions =>
        ScopeAnalysis.figureOutScope(ast)
        Mangler.mangleNames(ast, mangleOpts)
      case _: Boolean =>
      // mangling disabled
    }

    // 4. Output
    val out = new OutputStream(options.output)
    out.printNode(ast)

    // 5. Retrieve source map if configured
    val mapData = options.output.sourceMap match {
      case sm: ssg.js.sourcemap.SourceMap => sm.getEncoded()
      case null => null
    }

    MinifyResult(out.get(), ast, mapData)
  }

  /** Minify JavaScript source code, returning just the code string. */
  def minifyToString(code: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    minify(code, options).code
}
