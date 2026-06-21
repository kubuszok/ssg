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
 *   Idiom: source-map orchestration (minify.js:191-200, 313-352): the
 *     options.sourceMap struct is modelled as MinifySourceMapOptions and wired
 *     through the ported ssg.js.sourcemap.SourceMap wrapper (lib/sourcemap.js).
 *     Inline content decoding (content == "inline", minify.js:226-230 +
 *     read_source_map minify.js:33-40 → ssg.js.sourcemap.InlineSourceMap),
 *     includeSources (minify.js:321), the multi-file + inline guard
 *     (minify.js:227-228), url == "inline" data-URI appending (minify.js:346-348)
 *     and the plain url append (minify.js:349-350), asObject (minify.js:336) and
 *     getDecoded (minify.js:345) are all wired below. The includeSources guard
 *     "original source content unavailable" (minify.js:314-316) is unreachable —
 *     ssg-js's public API always takes source strings, never a pre-parsed AST.
 *     The in-minify.js orchestration (ecma normalization, nameCache, format/output
 *     resolution + set_shorthand, wrap/enclose, multi-file input, structured error
 *     fields) is ported below.
 *   Audited: 2026-06-20
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

/** Source-map orchestration options, mirroring terser's `options.sourceMap` object (minify.js:191-200 defaults; minify.js:313-352 usage).
  *
  * Upstream `options.sourceMap` is resolved by `defaults(options.sourceMap, { asObject: false, content: null, filename: null, includeSources: false, root: null, url: null })` (minify.js:192-199).
  * Each field is faithfully reproduced:
  *
  * @param filename
  *   the generated file name — becomes `file` of the [[SourceMap]] (minify.js:318 `file: options.sourceMap.filename`). Upstream calls the field `filename`; there is no separate `file` slot.
  * @param root
  *   the `sourceRoot` of the output map (minify.js:320 `root: options.sourceMap.root`).
  * @param content
  *   the input ("original") source map to chain through (minify.js:319 `orig: options.sourceMap.content`). May be a parsed [[ssg.js.sourcemap.SourceMapData]], a JSON source-map string
  *   (test/mocha/minify.js:299 reads the `.map` file as a string), or the literal `"inline"` (minify.js:226-230) to read the inline `//# sourceMappingURL=data:...;base64,...` comment from the input
  *   JS.
  * @param includeSources
  *   when true, pass the input source strings as the map's `sourcesContent` (minify.js:321 `files: options.sourceMap.includeSources ? files : null`). minify.js:314-316 throws "original source content
  *   unavailable" if the input is a pre-parsed AST — N/A here since ssg-js's public API always takes source strings, never a pre-parsed toplevel.
  * @param url
  *   when `"inline"`, append the output map to the result code as a `//# sourceMappingURL=data:application/json;charset=utf-8;base64,<...>` comment (minify.js:346-348); when any other non-null
  *   string, append it as a plain `//# sourceMappingURL=<url>` comment (minify.js:349-350).
  * @param asObject
  *   when true, [[MinifyResult.sourceMap]] is the structured map; when false (the upstream default), [[MinifyResult.sourceMapString]] additionally carries the `JSON.stringify(map)` form
  *   (minify.js:336).
  */
final case class MinifySourceMapOptions(
  filename:       String | Null = null,
  root:           String | Null = null,
  content:        ssg.js.sourcemap.SourceMapData | String | Null = null,
  includeSources: Boolean = false,
  url:            String | Null = null,
  asObject:       Boolean = false
)

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
  enclose:   Boolean | String = false,
  sourceMap: MinifySourceMapOptions | Null = null
)

object MinifyOptions {
  val Defaults: MinifyOptions = MinifyOptions()

  /** Minify with no compression or mangling — just parse and output. */
  val NoOptimize: MinifyOptions = MinifyOptions(compress = false, mangle = false)
}

/** Result of a Terser minification.
  *
  * @param code
  *   the minified output code (with any `//# sourceMappingURL=` comment appended when `sourceMap.url` is set — minify.js:346-350).
  * @param ast
  *   the (transformed) top-level AST.
  * @param sourceMap
  *   the structured output source map (`getEncoded()`), or `null` when no source map was requested.
  * @param sourceMapString
  *   the `JSON.stringify(map)` form of [[sourceMap]] (minify.js:336), populated when a source map was requested with `asObject == false` (the upstream default — `result.map` is the JSON string in
  *   that case). `null` when `asObject == true` or no map was requested.
  * @param decodedMap
  *   the decoded source map (`getDecoded()`, minify.js:345), or `null`.
  */
final case class MinifyResult(
  code:            String,
  ast:             AstToplevel,
  sourceMap:       ssg.js.sourcemap.SourceMapData | Null = null,
  sourceMapString: String | Null = null,
  decodedMap:      ssg.js.sourcemap.SourceMapData | Null = null
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
    // minify.js:208 — `if (typeof files == "string") files = [ files ]`: a single
    // source string becomes a one-element array, so the `for (var name in files)`
    // loop (minify.js:223-225) keys it by the array index "0" and sets
    // `options.parse.filename = "0"`. The index always wins over any caller-set
    // `options.parse.filename`, so the source-map source name is "0" — matching
    // terser's `"sources":["0"]` (test/mocha/minify.js:358). An explicitly set
    // `options.parse.filename` is honored only as a fallback for callers that rely
    // on it (e.g. SourceMapSuite passes "0" explicitly).
    val filename = if (options.parse.filename.isEmpty) "0" else options.parse.filename
    minifyFiles(scala.collection.immutable.ListMap(filename -> code), options)
  }

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

    // -- Source-map content resolution (minify.js:226-230) --
    // minify.js:226-230 — inside the parse loop, when `options.sourceMap.content
    // == "inline"`, terser reads the inline `//# sourceMappingURL=data:...;base64`
    // comment from the (single) input file and replaces `content` with the decoded
    // JSON. The multi-file guard (minify.js:227-228) rejects an inline map with more
    // than one input.
    val resolvedOrig: ssg.js.sourcemap.SourceMapData | Null =
      options.sourceMap match {
        case null => null
        case sm: MinifySourceMapOptions =>
          sm.content match {
            case null => null
            case data: ssg.js.sourcemap.SourceMapData => data
            case "inline" =>
              // minify.js:227-228 — `if (Object.keys(files).length > 1) throw …`.
              if (files.size > 1)
                throw new IllegalArgumentException("inline source map only works with singular input")
              // minify.js:229 — `read_source_map(files[name])` returns the decoded
              // JSON string (or null when no inline comment is present).
              ssg.js.sourcemap.InlineSourceMap.readSourceMap(firstSrc) match {
                case null => null
                case json: String => ssg.js.sourcemap.SourceMapJson.parse(json)
              }
            case json: String =>
              // test/mocha/minify.js:299 — `content` may be a raw JSON source-map
              // string (the `.map` file read verbatim); @jridgewell's
              // SourceMapConsumer JSON.parses it (sourcemap.js:73).
              ssg.js.sourcemap.SourceMapJson.parse(json)
          }
      }

    // minify.js:313-322 — build the SourceMap wrapper when `options.sourceMap` is
    // set. includeSources (minify.js:321) passes the input source strings as the
    // map's `sourcesContent`. The minify.js:314-316 "original source content
    // unavailable" guard fires only when the input is a pre-parsed AST_Toplevel;
    // ssg-js's public API always takes source strings, so it is unreachable here.
    val minifySourceMap: ssg.js.sourcemap.SourceMap | Null =
      options.sourceMap match {
        case null => null
        case sm: MinifySourceMapOptions =>
          new ssg.js.sourcemap.SourceMap(
            ssg.js.sourcemap.SourceMapOptions(
              file = sm.filename,
              root = sm.root,
              orig = resolvedOrig,
              files = if (sm.includeSources) files.toMap else Map.empty
            )
          )
      }

    // minify.js:317 — `format_options.source_map = SourceMap({...})`. When the
    // high-level sourceMap option is set, it drives the OutputStream's source map
    // (overriding any low-level OutputOptions.sourceMap escape hatch); otherwise
    // the existing OutputOptions.sourceMap path is used unchanged.
    val effectiveOutputSourceMap: ssg.js.sourcemap.SourceMap | Null =
      if (minifySourceMap != null) minifySourceMap else outputOptions.sourceMap

    // The resolved mangler options for the active mangle phase, or `null` when
    // mangling is disabled. minify.js:161-174 normalizes Boolean `true` to the
    // default mangler options. nameCache (minify.js:162-163,184-186) is woven in
    // here: the cache fields are pointed at the NameCache's live ManglerCache
    // objects BEFORE figure_out_scope/mangle_names so they accumulate. Resolved
    // before the compress phase because the reserve_quoted_keys step
    // (minify.js:239-241) runs ahead of compress.
    val resolvedMangle: ManglerOptions | Null =
      options.mangle match {
        case mangleOpts: ManglerOptions => applyMangleShorthand(mangleOpts, topEcma, options.toplevel, options.nameCache)
        case true  => applyMangleShorthand(ManglerOptions(), topEcma, options.toplevel, options.nameCache)
        case false => null
      }

    // The resolved property-mangler options, woven with the nameCache property
    // cache (minify.js:184-186) and normalized per minify.js:175-178. Resolved
    // once here so the `reserved` set populated by reserve_quoted_keys
    // (minify.js:239-241) is the same set later read by mangle_properties.
    val resolvedPropOptions: PropManglerOptions | Null =
      resolvedMangle match {
        case m: ManglerOptions =>
          ManglerOptions.resolveProperties(m.properties) match {
            case po: PropManglerOptions => applyPropertyCache(po, options.nameCache)
            case null => null
          }
        case null => null
      }

    // minify.js:175-183,239-241 — quoted-key reservation. When `keep_quoted` is
    // set (and not "strict"), minify.js points `quoted_props` at
    // `options.mangle.properties.reserved` (the same array later read by
    // mangle_properties) and calls `reserve_quoted_keys(toplevel, quoted_props)`
    // BEFORE the compress phase. Running ahead of compress is essential: quoted
    // keys that appear only inside dead code (e.g. `({ "keep": 1 })`) must be
    // reserved before dead-code elimination removes them, so an unquoted key is
    // never mangled into the reserved name. `resolvedPropOptions.reserved` is the
    // shared mutable set threaded into mangleProperties below.
    if (resolvedPropOptions != null && resolvedPropOptions.nn.keepQuoted) {
      PropMangler.reserveQuotedKeys(ast, resolvedPropOptions.nn.reserved)
    }

    // -- wrap / enclose (minify.js:246-251; ast.js:648-675) --
    // minify.js:247 — `if (options.wrap) toplevel = toplevel.wrap_commonjs(options.wrap)`.
    // minify.js:250 — `if (options.enclose) toplevel = toplevel.wrap_enclose(options.enclose)`.
    // Upstream applies wrap/enclose at minify.js:246-251 — AFTER reserve_quoted_keys
    // (minify.js:239-241) and find_annotated_props (minify.js:242-245) but BEFORE the
    // compress phase (minify.js:260-266) and mangle phase (minify.js:268-280). The
    // injected wrapper IIFE therefore PARTICIPATES in compress + mangle: the wrapped
    // toplevel flows through figure_out_scope → compress (minify.js:263) →
    // figure_out_scope → mangle_names (minify.js:270,274), exactly like
    // minify.js:247→263→270. We reassign `ast` to the wrapped toplevel here so the
    // wrapper body is reachable by the Compressor below (e.g. constant-fold inside the
    // wrapped function body). Note: terser does NOT mangle the toplevel by default
    // (mangle.toplevel defaults to false), so the wrapper-introduced names (`exports`,
    // enclose args) stay un-mangled even though wrap now precedes mangle — this falls
    // out of the default options and is not special-cased.
    if (options.wrap != null) {
      ast = wrapCommonjs(ast, options.wrap.nn)
    }
    options.enclose match {
      case false => // no enclose
      case true  => ast = wrapEnclose(ast, "")
      case s: String => ast = wrapEnclose(ast, s)
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
    if (resolvedPropOptions != null) {
      // `resolvedPropOptions` already carries the nameCache property cache and the
      // reserved set populated by reserveQuotedKeys above (minify.js:239-241), so
      // quoted keys reserved ahead of compress remain reserved here.
      ast = PropMangler.mangleProperties(ast, resolvedPropOptions.nn)
    }

    // -- Format phase (minify.js:282-353) --
    // minify.js:285-292 — `format.ast` / `format.spidermonkey` / `format.code`
    // control whether the AST and/or code string are returned. The typed
    // MinifyResult always carries both `code` and `ast`, so these JS-object
    // result-shaping flags are N/A.
    // minify.js:317 — `format_options.source_map = SourceMap({...})`: the resolved
    // source map drives the OutputStream. `effectiveOutputSourceMap` is the
    // high-level minify-option map when present, else the low-level
    // OutputOptions.sourceMap escape hatch.
    val resolvedOutputOptions = applyOutputShorthand(outputOptions, topEcma)
    val out                   = new OutputStream(
      if (minifySourceMap != null) resolvedOutputOptions.copy(sourceMap = effectiveOutputSourceMap)
      else resolvedOutputOptions
    )
    out.printNode(ast)
    var code = out.get()

    // -- nameCache write-back (minify.js:354-359) --
    // `if (options.nameCache && options.mangle) { nameCache.vars = cache_to_json(mangle.cache);
    //  if (mangle.properties && mangle.properties.cache) nameCache.props = cache_to_json(mangle.properties.cache); }`.
    // The ManglerCache objects are mutated in place by mangleNames/mangleProperties,
    // so they are already the "written back" caches; no JSON round-trip is needed
    // because the NameCache holds the live ManglerCache instances.

    // 5. Retrieve and finalize the source map (minify.js:330-352).
    // `getEncoded()` is the `format_options.source_map.getEncoded()` of minify.js:335.
    val mapData: ssg.js.sourcemap.SourceMapData | Null = effectiveOutputSourceMap match {
      case sm: ssg.js.sourcemap.SourceMap => sm.getEncoded()
      case null => null
    }

    // Source-map result shaping (minify.js:330-351) only runs when the high-level
    // sourceMap option is set (the low-level escape hatch keeps the historical
    // shape: structured `sourceMap`, no url-append/asObject handling).
    var mapString: String | Null                         = null
    var decoded:   ssg.js.sourcemap.SourceMapData | Null = null
    options.sourceMap match {
      case sm: MinifySourceMapOptions if mapData != null =>
        val map = mapData.nn
        // minify.js:336 — `result.map = asObject ? map : JSON.stringify(map)`.
        // ssg-js always keeps the structured map in `sourceMap`; `asObject == false`
        // additionally surfaces the JSON string form in `sourceMapString`.
        if (!sm.asObject) mapString = ssg.js.sourcemap.SourceMapJson.stringify(map)
        // minify.js:345 — `result.decoded_map = format_options.source_map.getDecoded()`.
        decoded = effectiveOutputSourceMap match {
          case s: ssg.js.sourcemap.SourceMap => s.getDecoded()
          case null => null
        }
        // minify.js:346-350 — append the sourceMappingURL comment.
        sm.url match {
          case "inline" =>
            // minify.js:347-348 — `var sourceMap = typeof result.map === "object"
            // ? JSON.stringify(result.map) : result.map;` then append the data-URI.
            // The stringified map is the asObject==false form; recompute when asObject.
            val sourceMapStr = if (sm.asObject) ssg.js.sourcemap.SourceMapJson.stringify(map) else mapString.nn
            code = code + "\n//# sourceMappingURL=data:application/json;charset=utf-8;base64," +
              ssg.js.sourcemap.Base64.encode(sourceMapStr)
          case null => // no url
          case url: String =>
            // minify.js:349-350 — `result.code += "\n//# sourceMappingURL=" + url`.
            code = code + "\n//# sourceMappingURL=" + url
        }
      case _ => // no high-level source-map option
    }

    // minify.js:360-362 — `if (format_options.source_map) format_options.source_map.destroy()`.
    if (minifySourceMap != null) minifySourceMap.destroy()

    MinifyResult(code, ast, mapData, mapString, decoded)
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
