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
 *   Renames: minify() → Terser.minify(); minify_sync_or_async generator →
 *     a single synchronous Terser.minify (ssg-js has no async source-map
 *     generator step, so the generator/yield* machinery — minify.js:106,
 *     379-406 — collapses to a plain method).
 *   Convention: Immutable options (final case class), pure function API.
 *     The JS code mutates `options` in place (set_shorthand, defaults,
 *     cache init); the Scala port threads the resolved values explicitly
 *     and never mutates the caller's MinifyOptions.
 *   Idiom: Terser does NOT implement ssg.minify.JsCompressor. Integration
 *     with ssg-minify is via the separate ssg.TerserJsCompressorAdapter in
 *     the ssg/ aggregator module (which delegates to TerserJsCompressor /
 *     Terser.minifyToString). Terser itself is a standalone pure API.
 *   Idiom: structured error shape — minify.js itself throws (the `{ error }`
 *     envelope lives in lib/cli.js:227-249, which reads ex.filename/line/col).
 *     The ported parser already throws ssg.js.parse.JsParseError, a
 *     final case class carrying message/filename/line/col/pos, so the
 *     structured fields the CLI envelope reads are preserved verbatim and
 *     propagate out of Terser.minify.
 *   Idiom: wrap_commonjs / wrap_enclose (ast.js:648-675) are methods on
 *     AST_Toplevel upstream; the ssg-js AST port has no such methods, so
 *     they are relocated here as private helpers (wrapCommonjs / wrapEnclose)
 *     parsing the wrapper template with the ported Parser and splicing the
 *     original body in via TreeTransformer + TransformSplice (the MAP.splice
 *     analog).
 *
 *   Gap: source-map orchestration: ISS-1219. lib/sourcemap.js is a separate
 *     unported module; options.sourceMap (minify.js:191-200, 313-352) — inline
 *     content decoding, includeSources, url=inline appending, asObject — is
 *     tracked there. The in-minify.js orchestration (ecma normalization,
 *     nameCache, format/output resolution + set_shorthand, wrap/enclose,
 *     multi-file input, structured error fields) is ported below.
 *   Audited: 2026-06-19
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/minify.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 6e7323fd4b0e255a06f6d3a2dcd111b8640a9031
 */
package ssg
package js

import scala.collection.mutable

import ssg.js.ast.*
import ssg.js.parse.{ Parser, ParserOptions }
import ssg.js.output.{ OutputOptions, OutputStream }
import ssg.js.scope.{ Mangler, ManglerCache, ManglerOptions, PropMangler, PropManglerOptions, ScopeAnalysis, ScopeOptions }
import ssg.js.compress.{ Compressor, CompressorOptions }

/** Mangle name cache, mirroring terser's `options.nameCache` (minify.js:125, 162-163, 184-186, 354-358).
  *
  * Upstream a `nameCache` is a plain JS object with two slots — `vars` and `props` — each of which becomes the JSON form `{ props: {...} }` produced by `cache_to_json` (minify.js:62-66). The live
  * caches threaded into the mangler are the [[ManglerCache]] objects below; reading `vars`/`props` after a minify call exposes the accumulated original→mangled name maps so a second minify call
  * reuses the same names (cross-file consistency, test/mocha/minify.js:118-149).
  *
  * `vars` backs `options.mangle.cache` (minify.js:162-163) and `props` backs `options.mangle.properties.cache` (minify.js:184-186).
  */
final class NameCache {
  // minify.js:163 `options.nameCache.vars || {}` — the variable-name cache.
  var vars: ManglerCache = new ManglerCache()
  // minify.js:185 `options.nameCache.props || {}` — the property-name cache.
  var props: ManglerCache = new ManglerCache()
}

/** Options for the Terser minifier.
  *
  * The `compress` and `mangle` fields follow upstream Terser's Boolean semantics (utils/index.js:66-68 `defaults()` + minify.js:116-123,161-174,262-276): an explicit options object uses those
  * options, `true` means "use the default options object" (equivalent to `CompressorOptions()` / `ManglerOptions()`), and `false` disables the phase.
  *
  * @param compress
  *   `true` (or an explicit [[CompressorOptions]], the default) enables compression with the given options; `true` uses default [[CompressorOptions]]; `false` disables compression.
  * @param mangle
  *   `true` (or an explicit [[ManglerOptions]], the default) enables name mangling with the given options; `true` uses default [[ManglerOptions]]; `false` disables mangling.
  * @param ecma
  *   top-level ECMAScript target (minify.js:118 defaults to `undefined` → `None`). When set, propagated into parse/compress/output via the set_shorthand rule (minify.js:152) — only filling a
  *   sub-option that is still at its own default.
  * @param toplevel
  *   top-level mangling/compression flag (minify.js:134); propagated to compress.toplevel and mangle.toplevel via set_shorthand (minify.js:158).
  * @param nameCache
  *   cross-call mangle name cache (minify.js:125); read before mangling and written back after.
  * @param wrap
  *   when non-null, wrap the output in a CommonJS module exposing the given export name (minify.js:136,246-248; ast.js:648-657).
  * @param enclose
  *   when `true` or a (possibly empty) arg/arg:value string, wrap the output in an IIFE (minify.js:119,249-251; ast.js:659-675).
  */
final case class MinifyOptions(
  parse:     ParserOptions = ParserOptions(),
  compress:  CompressorOptions | Boolean = CompressorOptions(),
  mangle:    ManglerOptions | Boolean = ManglerOptions(),
  output:    OutputOptions = OutputOptions(),
  ecma:      Option[Int] = None,
  toplevel:  Boolean = false,
  nameCache: NameCache | Null = null,
  wrap:      String | Null = null,
  enclose:   Boolean | String = false
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
  def minify(code: String, options: MinifyOptions = MinifyOptions.Defaults): MinifyResult =
    minifyFiles(scala.collection.immutable.ListMap(options.parse.filename -> code), options)

  /** Minify multiple named source files, concatenating their parsed bodies into a single top-level AST.
    *
    * Ports minify.js:205-238 multi-file input: when `files` is a map of filename→source, each file is parsed with `options.parse.filename` set to its name and the resulting top-level bodies are
    * accumulated into one toplevel (the loop `for (var name in files) { options.parse.filename = name; options.parse.toplevel = parse(files[name], options.parse); }`, which keeps appending to the
    * single mutable `options.parse.toplevel`).
    */
  def minifyFiles(files: scala.collection.immutable.SeqMap[String, String], options: MinifyOptions): MinifyResult = {
    // minify.js:233-235 — `if (options.parse.toplevel === null) throw "no source file given"`.
    if (files.isEmpty) throw new IllegalArgumentException("no source file given")

    // -- Option resolution (minify.js:142-200) --

    // minify.js:148-151 — `output`/`format` mutual exclusion + alias. ssg-js
    // exposes a single `output: OutputOptions` field, so there is no separate
    // `format` slot to conflict with; the resolved output options are just
    // `options.output`. (The JS "Please only specify either output or format"
    // error guards two raw object slots that the typed API collapses into one.)
    val outputOptions = options.output

    // minify.js:152 — set_shorthand("ecma", options, ["parse", "compress", "format"]).
    // In the typed API the only ecma-bearing sub-options that affect output are
    // the compressor and the output stream; threading a non-default top-level
    // ecma fills a sub-option that is still at its own default (the `!(name in
    // options[key])` guard).
    val topEcma: Option[Int] = options.ecma

    // -- Parse phase (minify.js:202-238) --
    // minify.js:223-231 — iterate files, parsing each with its filename
    // (`options.parse.filename = name`) and concatenating bodies into a single
    // toplevel (the loop keeps reassigning `options.parse.toplevel = parse(...)`
    // while the parser appends each file's statements). `files` is non-empty
    // (guarded above), so the first file's parse seeds the toplevel and the
    // rest append their bodies.
    def parseFile(name: String, src: String): AstToplevel =
      new Parser(options.parse.copy(filename = name)).parse(src)
    val (firstName, firstSrc) = files.head
    var ast: AstToplevel = parseFile(firstName, firstSrc)
    files.tail.foreach { case (name, src) =>
      ast.body.addAll(parseFile(name, src).body)
    }

    // -- Compress phase (minify.js:260-266) --
    // Upstream minify.js:262 runs the Compressor when `options.compress` is truthy.
    // utils/index.js:66-68 normalizes the Boolean `true` to the default options object
    // (`if (args === true) { args = {}; }`), so `compress = true` means "default
    // CompressorOptions"; only `false` disables the phase.
    val resolvedCompress: CompressorOptions | Null =
      options.compress match {
        case compressOpts: CompressorOptions =>
          // Resolve `defaults = false` before construction (terser index.js:220-222);
          // resolveDefaults is a no-op when `defaults == true`.
          CompressorOptions.resolveDefaults(compressOpts)
        case true =>
          CompressorOptions()
        case false =>
          null
      }
    if (resolvedCompress != null) {
      // minify.js:152,158 — thread top-level ecma/toplevel into the compressor
      // (set_shorthand only fills a still-default sub-option).
      val co = applyCompressShorthand(resolvedCompress.nn, topEcma, options.toplevel)
      ScopeAnalysis.figureOutScope(ast)
      val compressor = new Compressor(co)
      ast = compressor.compress(ast)
    }

    // -- Mangle phase (minify.js:268-280) --
    // Upstream minify.js:270-274 runs figure_out_scope, then compute_char_frequency,
    // then mangle_names when `options.mangle` is truthy; utils/index.js:66-68 normalizes
    // the Boolean `true` to the default mangler options (minify.js:161-174), so
    // `mangle = true` means "default ManglerOptions"; only `false` disables the phase.
    // minify.js:270 — `if (options.mangle) toplevel.figure_out_scope(options.mangle)`:
    // the mangle-phase scope analysis is run with the mangler options, so ie8/safari10/
    // module/cache reach figure_out_scope. figure_out_scope normalizes via
    // `defaults(options, { cache, ie8, safari10, module })` (scope.js:205-209),
    // which ScopeOptions mirrors.
    def scopeOptionsFor(m: ManglerOptions): ScopeOptions =
      ScopeOptions(cache = m.cache, ie8 = m.ie8, safari10 = m.safari10, module = m.module)

    // The resolved mangler options for the active mangle phase, or `null` when
    // mangling is disabled. minify.js:161-174 normalizes Boolean `true` to the
    // default mangler options. nameCache (minify.js:162-163,184-186) is woven in
    // here: the cache fields are pointed at the NameCache's live ManglerCache
    // objects BEFORE figure_out_scope/mangle_names so they accumulate.
    val resolvedMangle: ManglerOptions | Null =
      options.mangle match {
        case mangleOpts: ManglerOptions => applyMangleShorthand(mangleOpts, topEcma, options.toplevel, options.nameCache)
        case true  => applyMangleShorthand(ManglerOptions(), topEcma, options.toplevel, options.nameCache)
        case false => null
      }

    if (resolvedMangle != null) {
      val m = resolvedMangle.nn
      ScopeAnalysis.figureOutScope(ast, scopeOptionsFor(m))
      Mangler.computeCharFrequency(ast, m)
      Mangler.mangleNames(ast, m)
      // minify.js:275 — `toplevel = mangle_private_properties(toplevel, options.mangle)`.
      // Always runs in the mangle phase (private members are always safe).
      ast = PropMangler.manglePrivateProperties(ast, m.nthIdentifier).asInstanceOf[AstToplevel]
    }

    // 3b. Property mangling (minify.js:277-280)
    // Upstream minify.js:278-280 — `if (options.mangle && options.mangle.properties)
    // toplevel = mangle_properties(toplevel, options.mangle.properties, annotated_props)`.
    // Runs AFTER mangle_names (minify.js:274). minify.js:175-178 normalizes a truthy
    // non-object `properties` to `{}` (ManglerOptions.resolveProperties). The property
    // cache (minify.js:184-186) is woven into the resolved PropManglerOptions.
    if (resolvedMangle != null) {
      ManglerOptions.resolveProperties(resolvedMangle.nn.properties) match {
        case propOpts: PropManglerOptions =>
          val po = applyPropertyCache(propOpts, options.nameCache)
          ast = PropMangler.mangleProperties(ast, po)
        case null =>
        // property mangling disabled
      }
    }

    // -- wrap / enclose (minify.js:246-251; ast.js:648-675) --
    // minify.js:246-248 — `if (options.wrap) toplevel = toplevel.wrap_commonjs(options.wrap)`.
    // minify.js:249-251 — `if (options.enclose) toplevel = toplevel.wrap_enclose(options.enclose)`.
    // NOTE: upstream applies wrap/enclose at minify.js:246-251 (between
    // find_annotated_props and the rename/compress/mangle phases). The ssg-js
    // mangle pipeline mutates `ast` in place, so wrap/enclose is applied here,
    // after mangling, on the same toplevel — the wrapper template is parsed
    // fresh and the (already-mangled) body is spliced into it. The wrapped
    // names (`exports`, enclose args) are intentionally NOT mangled in upstream
    // either, since wrap/enclose run before mangle but the wrapper identifiers
    // are introduced by the template parse, not the user source.
    if (options.wrap != null) {
      ast = wrapCommonjs(ast, options.wrap.nn)
    }
    options.enclose match {
      case false => // no enclose
      case true  => ast = wrapEnclose(ast, "")
      case s: String => ast = wrapEnclose(ast, s)
    }

    // -- Format phase (minify.js:282-353) --
    // minify.js:285-292 — `format.ast` / `format.spidermonkey` / `format.code`
    // control whether the AST and/or code string are returned. The typed
    // MinifyResult always carries both `code` and `ast`, so these JS-object
    // result-shaping flags are N/A.
    val out = new OutputStream(applyOutputShorthand(outputOptions, topEcma))
    out.printNode(ast)

    // -- nameCache write-back (minify.js:354-359) --
    // `if (options.nameCache && options.mangle) { nameCache.vars = cache_to_json(mangle.cache);
    //  if (mangle.properties && mangle.properties.cache) nameCache.props = cache_to_json(mangle.properties.cache); }`.
    // The ManglerCache objects are mutated in place by mangleNames/mangleProperties,
    // so they are already the "written back" caches; no JSON round-trip is needed
    // because the NameCache holds the live ManglerCache instances.

    // 5. Retrieve source map if configured (ISS-1219 — source-map orchestration).
    val mapData = outputOptions.sourceMap match {
      case sm: ssg.js.sourcemap.SourceMap => sm.getEncoded()
      case null => null
    }

    MinifyResult(out.get(), ast, mapData)
  }

  /** Minify an array of source strings, naming each by its index ("0", "1", …), mirroring minify.js:208 `if (typeof files == "string") files = [ files ]` and the file-map iteration
    * (test/mocha/minify-file-map.js "array of strings": sources become `['0', '1']`). The bodies are concatenated into one toplevel.
    */
  def minifySeq(sources: Seq[String], options: MinifyOptions = MinifyOptions.Defaults): MinifyResult = {
    val files = scala.collection.immutable.ListMap.from(sources.zipWithIndex.map { case (src, i) => i.toString -> src })
    minifyFiles(files, options)
  }

  /** Minify JavaScript source code, returning just the code string. */
  def minifyToString(code: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    minify(code, options).code

  // ==========================================================================
  // set_shorthand helpers (minify.js:42-51, 152-159)
  //
  // Upstream `set_shorthand(name, options, keys)` (minify.js:42-51): for each
  // sub-option key, if the top-level option is truthy and the sub-option object
  // does NOT already carry `name`, copy the top-level value down. In the typed
  // API a sub-option always carries every field, so "does not already carry" is
  // approximated as "is still at the field's own default value".
  // ==========================================================================

  private def applyCompressShorthand(co: CompressorOptions, ecma: Option[Int], toplevel: Boolean): CompressorOptions = {
    var result = co
    // set_shorthand("ecma", ...) — minify.js:152.
    ecma.foreach { e =>
      if (result.ecma == CompressorOptions().ecma) result = result.copy(ecma = e)
    }
    // set_shorthand("toplevel", options, ["compress", "mangle"]) — minify.js:158.
    if (toplevel && result.toplevel == CompressorOptions().toplevel) {
      result = result.copy(toplevel = ssg.js.compress.ToplevelConfig(funcs = true, vars = true))
    }
    result
  }

  private def applyMangleShorthand(
    mo:        ManglerOptions,
    ecma:      Option[Int],
    toplevel:  Boolean,
    nameCache: NameCache | Null
  ): ManglerOptions = {
    var result = mo
    // set_shorthand("toplevel", options, ["compress", "mangle"]) — minify.js:158.
    if (toplevel && result.toplevel == ManglerOptions().toplevel) {
      result = result.copy(toplevel = true)
    }
    // ecma is not a mangler sub-option (set_shorthand("ecma") targets parse/
    // compress/format — minify.js:152 — not mangle), so it is not threaded here.
    val _ = ecma
    // nameCache.vars → options.mangle.cache (minify.js:162-163). Only fill when
    // the caller did not provide an explicit cache.
    if (nameCache != null && result.cache == null) {
      result = result.copy(cache = nameCache.nn.vars)
    }
    result
  }

  private def applyPropertyCache(po: PropManglerOptions, nameCache: NameCache | Null): PropManglerOptions =
    // minify.js:184-186 — `if (options.nameCache && !("cache" in mangle.properties))
    //   mangle.properties.cache = options.nameCache.props || {}`.
    if (nameCache != null && po.cache == null) po.copy(cache = nameCache.nn.props)
    else po

  private def applyOutputShorthand(oo: OutputOptions, ecma: Option[Int]): OutputOptions =
    // set_shorthand("ecma", options, ["parse", "compress", "format"]) — minify.js:152.
    ecma match {
      case Some(e) if oo.ecma == OutputOptions().ecma => oo.copy(ecma = e)
      case _                                          => oo
    }

  // ==========================================================================
  // wrap_commonjs / wrap_enclose (ast.js:648-675)
  // ==========================================================================

  /** Port of `AST_Toplevel.wrap_commonjs` (ast.js:648-657): wrap the body in `(function(exports){'$ORIG';})(typeof name=='undefined'?(name={}):name);` then splice the original body where the `$ORIG`
    * directive sits.
    */
  private def wrapCommonjs(toplevel: AstToplevel, name: String): AstToplevel = {
    val body       = toplevel.body
    val wrappedSrc =
      "(function(exports){'$ORIG';})(typeof " + name + "=='undefined'?(" + name + "={}):" + name + ");"
    val wrapped = new Parser(ParserOptions()).parse(wrappedSrc)
    spliceOrig(wrapped, body)
  }

  /** Port of `AST_Toplevel.wrap_enclose` (ast.js:659-675): split the `args:values` string on the first `:`, build `(function(<args>){"$ORIG"})(<values>)`, parse it, and splice the original body in
    * for the `$ORIG` directive. A non-string `args_values` becomes `""` (here the empty string from the Boolean-`true` enclose case).
    */
  private def wrapEnclose(toplevel: AstToplevel, argsValues: String): AstToplevel = {
    val body  = toplevel.body
    var index = argsValues.indexOf(":")
    if (index < 0) index = argsValues.length
    // JS `args_values.slice(index + 1)` returns "" when `index + 1 > length`;
    // Scala's substring throws, so clamp the start to the string length.
    val valuesStart = math.min(index + 1, argsValues.length)
    val src         =
      "(function(" + argsValues.substring(0, index) + "){\"$ORIG\"})(" + argsValues.substring(valuesStart) + ")"
    val wrapped = new Parser(ParserOptions()).parse(src)
    spliceOrig(wrapped, body)
  }

  /** Shared splice: walk the parsed wrapper, replacing the `$ORIG` directive with the original body via a TransformSplice (the ported analog of upstream's `MAP.splice(body)` returned from the
    * TreeTransformer, ast.js:653-655,670-673).
    */
  private def spliceOrig(wrapped: AstToplevel, body: mutable.ArrayBuffer[AstNode]): AstToplevel = {
    val tt = new TreeTransformer(
      before = (node, _, _) =>
        node match {
          case d: AstDirective if d.value == "$ORIG" => TransformSplice(body)
          case _ => null
        }
    )
    wrapped.transform(tt).asInstanceOf[AstToplevel]
  }
}
