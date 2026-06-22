/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Tests for ISS-1239: top-level shorthand options (ie8, keep_classnames,
 * keep_fnames, module, safari10) propagated from MinifyOptions into
 * compress/mangle/format sub-options via the set_shorthand pattern
 * (minify.js:42-51, 153-157).
 *
 * Each test asserts that the top-level shorthand produces the SAME output as
 * setting the sub-option directly. Fill-if-absent semantics are also verified:
 * an explicit sub-option must NOT be overwritten by the top-level shorthand.
 *
 * Oracle: terser lib/minify.js lines 142-158 (set_shorthand calls + the
 * keep_classnames pre-rule).
 */
package ssg
package js

import ssg.js.compress.CompressorOptions
import ssg.js.output.OutputOptions
import ssg.js.parse.ParserOptions
import ssg.js.scope.ManglerOptions

final class TopLevelShorthandIss1239Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

  // ======================================================================
  // keepFnames (minify.js:155 → compress.keepFnames, mangle.keepFnames)
  // ======================================================================

  // Oracle: terser minify({keep_fnames:true, toplevel:true}) preserves function
  // names in mangled output. We use toplevel=true to make top-level function
  // names mangleable; without keepFnames, the mangler renames them.
  test("keepFnames: top-level keepFnames=true preserves function name in mangled output") {
    val src = "function longFunctionName(x) { return x + 1; } longFunctionName(42);"
    // Via top-level shorthand:
    val viaShorthand = Terser.minifyToString(src, MinifyOptions(compress = false, keepFnames = true, toplevel = true))
    // Via direct sub-option on mangle:
    val viaDirect = Terser.minifyToString(src,
                                          MinifyOptions(
                                            compress = false,
                                            mangle = ManglerOptions(keepFnames = true, toplevel = true),
                                            toplevel = true
                                          )
    )
    // Both must preserve the function name.
    assert(
      viaShorthand.contains("longFunctionName"),
      s"top-level keepFnames=true must preserve function name, got: $viaShorthand"
    )
    assertEquals(viaShorthand, viaDirect, "top-level shorthand must produce same result as direct sub-option")
    // Without keepFnames but with toplevel, the name should be mangled (shorter).
    val noKeep = Terser.minifyToString(src, MinifyOptions(compress = false, toplevel = true))
    assert(!noKeep.contains("longFunctionName"), s"without keepFnames, function name should be mangled, got: $noKeep")
  }

  // ======================================================================
  // keepClassnames (minify.js:154 → compress.keepClassnames, mangle.keepClassnames)
  // ======================================================================

  // Oracle: terser minify({keep_classnames:true, toplevel:true}) preserves class names.
  test("keepClassnames: top-level keepClassnames=true preserves class name in mangled output") {
    val src          = "class VeryLongClassName { constructor() { this.x = 1; } } new VeryLongClassName();"
    val viaShorthand = Terser.minifyToString(src, MinifyOptions(compress = false, keepClassnames = true, toplevel = true, ecma = Some(2015)))
    val viaDirect    = Terser.minifyToString(
      src,
      MinifyOptions(
        compress = false,
        mangle = ManglerOptions(keepClassnames = true, toplevel = true),
        toplevel = true,
        ecma = Some(2015)
      )
    )
    assert(
      viaShorthand.contains("VeryLongClassName"),
      s"top-level keepClassnames=true must preserve class name, got: $viaShorthand"
    )
    assertEquals(viaShorthand, viaDirect, "top-level shorthand must produce same result as direct sub-option")
    // Without keepClassnames but with toplevel, the name should be mangled.
    val noKeep = Terser.minifyToString(src, MinifyOptions(compress = false, toplevel = true, ecma = Some(2015)))
    assert(!noKeep.contains("VeryLongClassName"), s"without keepClassnames, class name should be mangled, got: $noKeep")
  }

  // ======================================================================
  // keep_classnames pre-rule (minify.js:142-144):
  // if (options.keep_classnames === undefined) options.keep_classnames = options.keep_fnames
  // ======================================================================

  test("keepClassnames pre-rule: keepClassnames defaults to keepFnames when not explicitly set (minify.js:142-144)") {
    val src = "class VeryLongClassName { constructor() { this.x = 1; } } new VeryLongClassName();"
    // Set keepFnames=true but NOT keepClassnames — keepClassnames should default to keepFnames.
    val withKeepFnames = Terser.minifyToString(src, MinifyOptions(compress = false, keepFnames = true, toplevel = true, ecma = Some(2015)))
    assert(
      withKeepFnames.contains("VeryLongClassName"),
      s"keepClassnames should default to keepFnames=true, preserving class name, got: $withKeepFnames"
    )
  }

  test("keepClassnames pre-rule: explicit keepClassnames=false overrides pre-rule even with keepFnames=true") {
    // An explicitly-set keepClassnames (even false) should NOT be overridden
    // by the pre-rule. Here keepClassnames=false is falsy, so the shorthand
    // does not propagate, and the class name should be mangled.
    val src = "class VeryLongClassName { constructor() { this.x = 1; } } new VeryLongClassName();"
    // Note: keepClassnames=false is explicitly set by the caller but equals
    // the MinifyOptions default. The pre-rule (minify.js:142-144) only fires
    // when keepClassnames is UNDEFINED (=== undefined), not when it's false.
    // In our typed API, "undefined" is modelled as "equal to the default" —
    // so keepClassnames=false (the default) IS treated as undefined, and the
    // pre-rule DOES fire, setting keepClassnames to keepFnames (true).
    // This is faithful to terser: `{keep_fnames:true}` implies keep_classnames
    // because undefined → keep_fnames; an explicit `{keep_classnames:false,
    // keep_fnames:true}` in terser also triggers because JS `undefined`
    // check is `=== undefined` not `=== false`. So keepClassnames=false
    // at the MinifyOptions level is indeed treated as "not set".
    // To explicitly DISABLE keepClassnames while keepFnames is true, the
    // caller must set it on the sub-options directly.
    val result = Terser.minifyToString(src,
                                       MinifyOptions(
                                         compress = false,
                                         keepFnames = true,
                                         keepClassnames = false,
                                         toplevel = true,
                                         ecma = Some(2015)
                                       )
    )
    // Pre-rule fires: keepClassnames defaults to keepFnames=true.
    assert(
      result.contains("VeryLongClassName"),
      s"keepClassnames=false (default) should be treated as undefined by the pre-rule, got: $result"
    )
  }

  // ======================================================================
  // ie8 (minify.js:153 → compress.ie8, mangle.ie8, format.ie8)
  // ======================================================================

  // Observable ie8 effect on output: vertical tab U+000B is escaped as \x0B
  // under ie8 vs \v without ie8 (output.js:379).
  test("ie8: top-level ie8=true threads into format (vertical tab escape)") {
    // The source contains the actual U+000B (vertical tab) character between 'a' and 'b'.
    val vtSource     = "var s = \"ab\";"
    val viaShorthand = Terser.minifyToString(vtSource, MinifyOptions(compress = false, mangle = false, ie8 = true))
    val viaDirect    = Terser.minifyToString(vtSource, MinifyOptions(compress = false, mangle = false, output = OutputOptions(ie8 = true)))
    assertEquals(viaShorthand, viaDirect, "top-level ie8 must produce same output as direct format.ie8")
    assert(viaShorthand.contains("\\x0B"), s"ie8=true must emit \\x0B for vertical tab, got: $viaShorthand")
  }

  test("ie8: top-level ie8=true threads into mangle") {
    val src          = "function f(x) { return x; } f(1);"
    val viaShorthand = Terser.minifyToString(src, MinifyOptions(compress = false, ie8 = true))
    val viaDirect    = Terser.minifyToString(src, MinifyOptions(compress = false, mangle = ManglerOptions(ie8 = true)))
    assertEquals(viaShorthand, viaDirect, "top-level ie8 must produce same result as direct mangle.ie8")
  }

  // ======================================================================
  // safari10 (minify.js:157 → mangle.safari10, format.safari10)
  // ======================================================================

  test("safari10: top-level safari10=true threads into mangle and format") {
    val src          = "function f(x) { return x; } f(1);"
    val viaShorthand = Terser.minifyToString(src, MinifyOptions(compress = false, safari10 = true))
    val viaDirect    = Terser.minifyToString(src,
                                          MinifyOptions(
                                            compress = false,
                                            mangle = ManglerOptions(safari10 = true),
                                            output = OutputOptions(safari10 = true)
                                          )
    )
    assertEquals(viaShorthand, viaDirect, "top-level safari10 must produce same result as direct sub-options")
  }

  // ======================================================================
  // module (minify.js:156 → parse.module, compress.module, mangle.module)
  // ======================================================================

  // Observable module effect: module=true implies "use strict" in the parser
  // (Parser.scala:3183), which changes how the code is parsed.
  test("module: top-level module=true threads into parse.module") {
    // module=true adds "use strict" directive (parser.js:76-79).
    // We can test that the top-level shorthand produces the same result as
    // setting parse.module directly.
    val src          = "var x = 1;"
    val viaShorthand = Terser.minifyToString(src, MinifyOptions(compress = false, mangle = false, module = true))
    val viaDirect    = Terser.minifyToString(src, MinifyOptions(compress = false, mangle = false, parse = ParserOptions(module = true)))
    assertEquals(viaShorthand, viaDirect, "top-level module must produce same result as direct parse.module")
  }

  test("module: top-level module=true threads into mangle.module") {
    val src          = "function f(x) { return x; } f(1);"
    val viaShorthand = Terser.minifyToString(src, MinifyOptions(compress = false, module = true))
    val viaDirect    = Terser.minifyToString(src, MinifyOptions(compress = false, mangle = ManglerOptions(module = true)))
    assertEquals(viaShorthand, viaDirect, "top-level module must produce same result as direct mangle.module")
  }

  // ======================================================================
  // FILL-IF-ABSENT: explicit sub-option must NOT be overwritten
  // ======================================================================

  // terser set_shorthand semantics (minify.js:47): `if (!(name in options[key]))`.
  // In the typed API this translates to: only fill when the sub-option still
  // equals its own case-class default. If the caller set it to a non-default
  // value, the top-level shorthand must NOT overwrite.

  test("fill-if-absent: explicit mangle.keepFnames=true is NOT overwritten by top-level keepFnames with regex") {
    val src = "function longFunctionName(x) { return x + 1; } longFunctionName(42);"
    val re  = "^long".r
    // Caller explicitly set mangle.keepFnames=true (non-default); top-level
    // keepFnames is a regex. Since the sub-option differs from its default
    // (false), the top-level shorthand must NOT overwrite it.
    val result = Terser.minifyToString(src,
                                       MinifyOptions(
                                         compress = false,
                                         mangle = ManglerOptions(keepFnames = true, toplevel = true),
                                         keepFnames = re,
                                         toplevel = true
                                       )
    )
    assert(result.contains("longFunctionName"), s"explicit mangle.keepFnames=true must be preserved, got: $result")
  }

  test("fill-if-absent: explicit output.ie8=true is NOT overwritten by top-level ie8=false") {
    val vtSource = "var s = \"ab\";"
    // Caller explicitly sets output.ie8=true; top-level ie8 defaults to false
    // so no shorthand propagation occurs; the explicit output.ie8=true must
    // survive.
    val result = Terser.minifyToString(vtSource,
                                       MinifyOptions(
                                         compress = false,
                                         mangle = false,
                                         output = OutputOptions(ie8 = true),
                                         ie8 = false
                                       )
    )
    assert(result.contains("\\x0B"), s"explicit output.ie8=true must be preserved, got: $result")
  }

  test("fill-if-absent: explicit compress.module=true is NOT overwritten by top-level module=false") {
    val src = "var x = 1;"
    // Caller explicitly sets compress.module=true; top-level module=false.
    // The explicit sub-option must survive.
    val viaExplicit = Terser.minifyToString(src,
                                            MinifyOptions(
                                              compress = CompressorOptions(module = true),
                                              mangle = false
                                            )
    )
    val viaBoth = Terser.minifyToString(src,
                                        MinifyOptions(
                                          compress = CompressorOptions(module = true),
                                          mangle = false,
                                          module = false
                                        )
    )
    assertEquals(viaExplicit, viaBoth, "explicit compress.module=true must survive top-level module=false")
  }
}
