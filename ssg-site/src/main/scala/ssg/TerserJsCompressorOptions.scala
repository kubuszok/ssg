/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Concrete JsCompressorOptions for the Terser-backed adapter.
 *
 * Bridges jekyll-minifier's `terser_args` configuration hash
 * (lib/jekyll-minifier.rb:548 `TERSER_ARGS`, :865-885 `_compute_terser_args`,
 * :793-845 `validate_compressor_args`) into ssg-js Terser's `MinifyOptions`.
 *
 * jekyll-minifier passes `config.terser_args` straight to `::Terser.new(...)`
 * for both standalone JS files (lib/jekyll-minifier.rb:383-388 create_js_compressor)
 * and inline `<script>` blocks inside HTML (lib/jekyll-minifier.rb:433-439
 * create_js_compressor_uncached, wired into the HtmlCompressor via :409/:414).
 * The recognised keys are those accepted by `validate_compressor_args`
 * (lib/jekyll-minifier.rb:799-841): `compress`, `mangle`, `output` (hash-or-bool),
 * `ecma`/`ie8`/`safari10` (numeric-or-bool), and `eval`/`with`/`toplevel` (bool).
 * This bridge carries the subset ssg-js Terser can honor — `with` is dropped
 * because ssg-js Terser exposes no `with`-statement toggle (Terser.scala:46-51,
 * compress/CompressorOptions.scala, scope/Mangler.scala:39-53, output/OutputOptions.scala:73).
 *
 * Covenant: original
 */
package ssg

import ssg.js.MinifyOptions
import ssg.js.compress.CompressorOptions
import ssg.js.scope.ManglerOptions
import ssg.js.output.OutputOptions

/** Terser-backed [[ssg.minify.JsCompressorOptions]].
  *
  * Fields mirror the `terser_args` keys jekyll-minifier supports (lib/jekyll-minifier.rb:799-841 `validate_compressor_args`) that ssg-js's Terser engine can honor. Each maps onto ssg-js
  * `MinifyOptions` via [[toMinifyOptions]]:
  *
  *   - `compress` — enable the compress phase (`true` → default [[ssg.js.compress.CompressorOptions]], `false` → disabled), matching `::Terser.new(compress: …)`.
  *   - `mangle` — enable name mangling (`true` → default [[ssg.js.scope.ManglerOptions]], `false` → disabled).
  *   - `ecma` — ECMAScript output/compress version; flows to both `CompressorOptions.ecma` and `OutputOptions.ecma`.
  *   - `ie8` — IE8 compatibility; flows to both `CompressorOptions.ie8` and `ManglerOptions.ie8`.
  *   - `safari10` — Safari 10 workarounds; flows to `ManglerOptions.safari10`.
  *   - `toplevel` — mangle top-level names; flows to `ManglerOptions.toplevel`.
  *   - `evaluate` — evaluate the `eval` (top-level eval mangling) terser_arg; flows to `ManglerOptions.eval`. Named `evaluate` to avoid shadowing the `eval` reserved word; carries jekyll-minifier's
  *     `eval` terser_arg.
  *
  * Defaults mirror ssg-js Terser's own defaults (compress + mangle on, ECMA 5, ie8/safari10/toplevel/eval off) so `TerserJsCompressorOptions()` behaves like the no-argument `::Terser.new()` path.
  */
final case class TerserJsCompressorOptions(
  compress: Boolean = true,
  mangle:   Boolean = true,
  ecma:     Int = 5,
  ie8:      Boolean = false,
  safari10: Boolean = false,
  toplevel: Boolean = false,
  evaluate: Boolean = false
) extends ssg.minify.JsCompressorOptions {

  /** Map these terser_args onto ssg-js Terser's [[ssg.js.MinifyOptions]].
    *
    * Mirrors `::Terser.new(terser_args)` (lib/jekyll-minifier.rb:384/435): a boolean `compress`/`mangle` toggles the respective phase, while numeric/ boolean `ecma`/`ie8`/`safari10` and boolean
    * `toplevel`/`eval` configure the options objects. ssg-js follows upstream Terser's Boolean semantics (Terser.scala:36-51): `true` selects the default options object, `false` disables the phase.
    */
  def toMinifyOptions: MinifyOptions = {
    val compressOpts: CompressorOptions | Boolean =
      if (compress) CompressorOptions(ecma = ecma, ie8 = ie8) else false
    val mangleOpts: ManglerOptions | Boolean =
      if (mangle) ManglerOptions(eval = evaluate, ie8 = ie8, safari10 = safari10, toplevel = toplevel) else false
    MinifyOptions(
      compress = compressOpts,
      mangle = mangleOpts,
      output = OutputOptions(ecma = ecma)
    )
  }
}
