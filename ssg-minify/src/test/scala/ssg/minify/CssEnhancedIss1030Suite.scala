/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

import ssg.minify.css.{ CssEnhancedCompressor, CssEnhancedConfig, CssMinifier }

/** ISS-1030 [R0610-P1] — differential tests for the enhanced-CSS options ported from cssminify2 lib/cssminify2/enhanced.rb (CSSminify2Enhanced). Each enhanced flag has a case asserting the enhanced
  * output differs from the standard (base-minify) output in the way the Ruby source documents. Expected values are cited from enhanced.rb line numbers.
  */
final class CssEnhancedIss1030Suite extends munit.FunSuite {

  // Standard (base) minification — the gem's CssCompressor.compress equivalent.
  private def standard(css: String): String = CssMinifier.minify(css)

  // Enhanced compression with a single flag enabled.
  private def enhanced(css: String)(configure: CssEnhancedConfig => Unit): String = {
    val config = new CssEnhancedConfig()
    configure(config)
    CssMinifier.compressEnhanced(css, config)
  }

  // -- Defaults / opt-in safety (enhanced.rb:24-33) --

  test("Iss1030: all enhancement flags are opt-in (off) by default") {
    val c = new CssEnhancedConfig()
    assertEquals(c.mergeDuplicateSelectors, false)
    assertEquals(c.optimizeShorthandProperties, false)
    assertEquals(c.advancedColorOptimization, false)
    assertEquals(c.preserveIeHacks, true) // default true (enhanced.rb:28)
    assertEquals(c.compressCssVariables, false)
    assertEquals(c.strictErrorHandling, false)
    assertEquals(c.generateSourceMap, false)
    assertEquals(c.statisticsEnabled, false)
  }

  test("Iss1030: with no enhancements enabled, enhanced output equals standard output") {
    val css = ".a{color:red}.a{margin:0}"
    assertEquals(enhanced(css)(_ => ()), standard(css))
  }

  // -- Presets (enhanced.rb:36-54) --

  test("Iss1030: presets — conservative disables all, aggressive enables four, modern adds stats/sourcemap") {
    val cons = CssEnhancedConfig.conservative
    assertEquals(cons.mergeDuplicateSelectors, false)
    assertEquals(cons.optimizeShorthandProperties, false)

    val agg = CssEnhancedConfig.aggressive
    assertEquals(agg.mergeDuplicateSelectors, true)
    assertEquals(agg.optimizeShorthandProperties, true)
    assertEquals(agg.advancedColorOptimization, true)
    assertEquals(agg.compressCssVariables, true)
    assertEquals(agg.generateSourceMap, false)

    val mod = CssEnhancedConfig.modern
    assertEquals(mod.advancedColorOptimization, true)
    assertEquals(mod.generateSourceMap, true)
    assertEquals(mod.statisticsEnabled, true)
  }

  // -- merge_duplicate_selectors (enhanced.rb:216) --

  test("Iss1030: merge_duplicate_selectors merges duplicate .button rules") {
    val css = ".button{color:red}.button{padding:0}"
    val std = standard(css)
    val enh = enhanced(css)(_.mergeDuplicateSelectors = true)
    // Standard keeps both rules; enhanced merges into one (.button{color:red;padding:0}).
    assertEquals(std, ".button{color:red}.button{padding:0}")
    assertEquals(enh, ".button{color:red;padding:0}")
    assertNotEquals(enh, std)
  }

  test("Iss1030: merge_duplicate_selectors later declaration overrides earlier (enhanced.rb:320-330)") {
    val css = ".x{color:red}.x{color:blue}"
    val enh = enhanced(css)(_.mergeDuplicateSelectors = true)
    // merge_declarations: new_props take precedence — color:blue wins.
    assertEquals(enh, ".x{color:blue}")
  }

  // -- optimize_shorthand_properties (enhanced.rb:355) --

  test("Iss1030: optimize_shorthand_properties collapses four identical margins (enhanced.rb:385)") {
    val css = ".a{margin:10px 10px 10px 10px}"
    val std = standard(css)
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    assertEquals(std, ".a{margin:10px 10px 10px 10px}")
    assertEquals(enh, ".a{margin:10px}")
    assertNotEquals(enh, std)
  }

  test("Iss1030: optimize_shorthand_properties collapses vertical/horizontal pairs (enhanced.rb:388)") {
    val css = ".a{padding:10px 20px 10px 20px}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    assertEquals(enh, ".a{padding:10px 20px}")
  }

  test("Iss1030: optimize_shorthand_properties collapses three values w/ equal 1st & 3rd (enhanced.rb:391)") {
    val css = ".a{margin:10px 20px 10px}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    assertEquals(enh, ".a{margin:10px 20px}")
  }

  test("Iss1030: optimize_shorthand_properties font-weight normal→400, bold→700 (enhanced.rb:424-425)") {
    val css = ".a{font-weight:normal}.b{font-weight:bold}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    assertEquals(enh, ".a{font-weight:400}.b{font-weight:700}")
  }

  test("Iss1030: optimize_shorthand_properties background none repeat scroll 0 0 (enhanced.rb:398)") {
    val css = ".a{background:none repeat scroll 0 0 red}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // background: none repeat scroll 0 0 color → background: color
    assertEquals(enh, ".a{background:red}")
  }

  test("Iss1030: optimize_shorthand_properties list-style none inside (enhanced.rb:435)") {
    val css = ".a{list-style:none inside}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    assertEquals(enh, ".a{list-style:none}")
  }

  test("Iss1030: optimize_shorthand_properties enhance_zero_value flex 1 1 auto→1 (enhanced.rb:550)") {
    // optimize_shorthand_properties also triggers optimize_modern_layout_properties
    // (enhanced.rb:120/132), which runs the flex optimizations.
    val css = ".a{flex:1 1 auto}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    assertEquals(enh, ".a{flex:1}")
  }

  test("Iss1030: optimize_shorthand_properties grid-gap→gap and dup gap collapse (enhanced.rb:611,615)") {
    val css = ".a{gap:10px 10px}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // gap: 10px 10px → gap: 10px (enhanced.rb:611)
    assertEquals(enh, ".a{gap:10px}")
  }

  // -- optimize_alignment_properties: place-items / place-content / place-self (enhanced.rb:597-599) --
  // These backreferences PARTICIPATE IN MATCH SELECTION: Ruby's `justify-*:\s*\1` consumes
  // exactly the align value and the pattern then ENDS, leaving the trailing chars unconsumed.
  // Ruby-oracle outputs (ruby -Ilib, CSSminify2.compress_enhanced(css, {optimize_shorthand_properties:true})).

  test("Iss1030: place-items collapse — brace-bounded equal values (enhanced.rb:597)") {
    val css = ".a{align-items:center;justify-items:center}"
    val std = standard(css)
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: STD=.a{align-items:center;justify-items:center}  ENH=.a{place-items:center}
    assertEquals(std, ".a{align-items:center;justify-items:center}")
    assertEquals(enh, ".a{place-items:center}")
    assertNotEquals(enh, std)
  }

  test("Iss1030: place-items collapse — semicolon-bounded equal values (enhanced.rb:597)") {
    val css = ".a{align-items:center;justify-items:center;color:red}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH=.a{place-items:center;color:red}
    assertEquals(enh, ".a{place-items:center;color:red}")
  }

  test("Iss1030: place-items NO collapse — differing values (enhanced.rb:597)") {
    val css = ".a{align-items:center;justify-items:start}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH unchanged = .a{align-items:center;justify-items:start}
    assertEquals(enh, ".a{align-items:center;justify-items:start}")
  }

  test("Iss1030: place-content collapse — brace-bounded equal values (enhanced.rb:598)") {
    val css = ".a{align-content:center;justify-content:center}"
    val std = standard(css)
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: STD=.a{align-content:center;justify-content:center}  ENH=.a{place-content:center}
    assertEquals(std, ".a{align-content:center;justify-content:center}")
    assertEquals(enh, ".a{place-content:center}")
    assertNotEquals(enh, std)
  }

  test("Iss1030: place-content collapse — semicolon-bounded equal values (enhanced.rb:598)") {
    val css = ".a{align-content:center;justify-content:center;color:red}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH=.a{place-content:center;color:red}
    assertEquals(enh, ".a{place-content:center;color:red}")
  }

  test("Iss1030: place-content NO collapse — differing values (enhanced.rb:598)") {
    val css = ".a{align-content:center;justify-content:space-between}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH unchanged = .a{align-content:center;justify-content:space-between}
    assertEquals(enh, ".a{align-content:center;justify-content:space-between}")
  }

  test("Iss1030: place-self collapse — brace-bounded equal values (enhanced.rb:599)") {
    val css = ".a{align-self:center;justify-self:center}"
    val std = standard(css)
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: STD=.a{align-self:center;justify-self:center}  ENH=.a{place-self:center}
    assertEquals(std, ".a{align-self:center;justify-self:center}")
    assertEquals(enh, ".a{place-self:center}")
    assertNotEquals(enh, std)
  }

  test("Iss1030: place-self collapse — semicolon-bounded equal values (enhanced.rb:599)") {
    val css = ".a{align-self:center;justify-self:center;color:red}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH=.a{place-self:center;color:red}
    assertEquals(enh, ".a{place-self:center;color:red}")
  }

  test("Iss1030: place-self NO collapse — differing values (enhanced.rb:599)") {
    val css = ".a{align-self:center;justify-self:start}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH unchanged = .a{align-self:center;justify-self:start}
    assertEquals(enh, ".a{align-self:center;justify-self:start}")
  }

  // -- optimize_border_properties: border-color backref \2 (enhanced.rb:417) --
  // The \2 forces the engine to bind the shorthand color token to the trailing
  // border-color value (lazy g1/g3 backtracking). Ruby-oracle outputs as above.

  test("Iss1030: border-color collapse — brace-bounded matching color (enhanced.rb:417)") {
    val css = ".a{border:1px solid red;border-color:red}"
    val std = standard(css)
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: STD=.a{border:1px solid red;border-color:red}  ENH=.a{border:1px solid red}
    assertEquals(std, ".a{border:1px solid red;border-color:red}")
    assertEquals(enh, ".a{border:1px solid red}")
    assertNotEquals(enh, std)
  }

  test("Iss1030: border-color collapse — semicolon-bounded matching color (enhanced.rb:417)") {
    val css = ".a{border:1px solid red;border-color:red;color:blue}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH=.a{border:1px solid red;color:blue}
    assertEquals(enh, ".a{border:1px solid red;color:blue}")
  }

  test("Iss1030: border-color NO collapse — non-matching color (enhanced.rb:417)") {
    val css = ".a{border:1px solid red;border-color:blue}"
    val enh = enhanced(css)(_.optimizeShorthandProperties = true)
    // Oracle: ENH unchanged = .a{border:1px solid red;border-color:blue}
    assertEquals(enh, ".a{border:1px solid red;border-color:blue}")
  }

  // -- advanced_color_optimization (enhanced.rb:724) --

  test("Iss1030: advanced_color_optimization converts hsl(...) to rgb(...) (enhanced.rb:729-733)") {
    val css = ".a{color:hsl(0,100%,50%)}"
    val std = standard(css)
    val enh = enhanced(css)(_.advancedColorOptimization = true)
    // hsl(0,100%,50%) → pure red → rgb(255,0,0)
    assertEquals(std, ".a{color:hsl(0,100%,50%)}")
    assertEquals(enh, ".a{color:rgb(255,0,0)}")
    assertNotEquals(enh, std)
  }

  test("Iss1030: advanced_color_optimization hsl green (enhanced.rb:741 hsl_to_rgb)") {
    val css = ".a{color:hsl(120,100%,50%)}"
    val enh = enhanced(css)(_.advancedColorOptimization = true)
    // hsl(120,100%,50%) → pure green → rgb(0,255,0)
    assertEquals(enh, ".a{color:rgb(0,255,0)}")
  }

  test("Iss1030: advanced_color_optimization hsl yellow exercises hue2rgb (enhanced.rb:747-763)") {
    val css = ".a{color:hsl(60,100%,50%)}"
    val enh = enhanced(css)(_.advancedColorOptimization = true)
    // hsl(60,100%,50%) → yellow → rgb(255,255,0)
    assertEquals(enh, ".a{color:rgb(255,255,0)}")
  }

  // -- compress_css_variables (enhanced.rb:622) --

  test("Iss1030: compress_css_variables removes unused variable (enhanced.rb:690-700)") {
    // A trailing declaration bounds the variable value with `;` so the greedy
    // [^;]+ value capture (enhanced.rb:645) stops before the rest of the block,
    // matching how the gem behaves on real (semicolon-separated) declarations.
    val css = ":root{--unused:#fff;color:red}"
    val std = standard(css)
    val enh = enhanced(css)(_.compressCssVariables = true)
    // --unused declared but never used → removed.
    assert(std.contains("--unused"), s"standard should still contain the variable: $std")
    assert(!enh.contains("--unused"), s"enhanced should remove the unused variable: $enh")
    assert(enh.contains("color:red"), s"the real declaration must survive: $enh")
    assertNotEquals(enh, std)
  }

  test("Iss1030: compress_css_variables inlines a single-use short-value variable (enhanced.rb:667-688)") {
    val css = ":root{--c:red;color:#000}.a{color:var(--c)}"
    val enh = enhanced(css)(_.compressCssVariables = true)
    // usages<=2, declarations==1, value length<=20, no calc()/var() → inline.
    assert(!enh.contains("var(--c)"), s"var() usage should be inlined: $enh")
    assert(enh.contains(".a{color:red}"), s"value should be inlined: $enh")
  }

  // -- preserve_ie_hacks default (enhanced.rb:28) --

  test("Iss1030: preserve_ie_hacks defaults to true and is independent of other flags") {
    val c = CssEnhancedConfig.aggressive
    // aggressive does not flip preserve_ie_hacks (enhanced.rb:40-47) — stays default true.
    assertEquals(c.preserveIeHacks, true)
  }

  // -- compress_with_stats dispatch (enhanced.rb:785 / cssminify2.rb:65) --

  test("Iss1030: compressWithStats returns compressed css + statistics") {
    val css    = ".button{color:red}.button{padding:0}"
    val result = CssEnhancedCompressor.compressWithStats(css, Map("merge_duplicate_selectors" -> true))
    assertEquals(result.compressedCss, ".button{color:red;padding:0}")
    assertEquals(result.statistics.originalSize, css.length)
    assertEquals(result.statistics.compressedSize, result.compressedCss.length)
    // NOTE: faithful to the Ruby source — merge_duplicate_selectors (enhanced.rb:220,231)
    // resets @statistics[:selectors_merged] to the local `selectors_merged = 0` AFTER
    // merge_parsed_rules has incremented it, clobbering the count. So the reported
    // value is 0 even though a merge happened (verified via compressedCss above).
    assertEquals(result.statistics.selectorsMerged, 0)
    assert(result.statistics.compressionRatio > 0.0, "positive compression ratio")
  }

  // -- compress dispatch with options map (enhanced.rb:773 / cssminify2.rb:50) --

  test("Iss1030: compress(css, options) applies the map-configured flags") {
    val css = ".a{margin:5px 5px 5px 5px}"
    val enh = CssEnhancedCompressor.compress(css, Map("optimize_shorthand_properties" -> true))
    assertEquals(enh, ".a{margin:5px}")
  }

  test("Iss1030: compress(css, emptyOptions) falls back to base minify (enhanced.rb:779-782)") {
    val css = ".a{margin:5px 5px 5px 5px}"
    val enh = CssEnhancedCompressor.compress(css, Map.empty[String, Any])
    // No options → base API → no shorthand collapse.
    assertEquals(enh, standard(css))
  }

  // -- strict vs graceful error handling (enhanced.rb:78,94-104,155-191) --

  test("Iss1030: strict_error_handling raises EnhancedCompressionError on unbalanced braces (enhanced.rb:94-95,162-164)") {
    // validate_css_structure raises MalformedCSSError on unbalanced braces; the
    // strict rescue (enhanced.rb:94-95) re-wraps it as EnhancedCompressionError.
    val css = ".a{color:red" // missing closing brace
    val ex  = intercept[ssg.minify.css.EnhancedCompressionError] {
      val config = new CssEnhancedConfig()
      config.strictErrorHandling = true
      config.mergeDuplicateSelectors = true
      CssMinifier.compressEnhanced(css, config)
    }
    // original_error carries the underlying MalformedCSSError (enhanced.rb:801-806).
    assert(ex.originalError.isDefined, "original error should be carried")
  }

  test("Iss1030: graceful (non-strict) handling does not raise on malformed CSS (enhanced.rb:96-103)") {
    val css    = ".a{color:red" // unbalanced, but strict handling off → no validation, no raise
    val config = new CssEnhancedConfig()
    config.mergeDuplicateSelectors = true
    // Should not throw.
    val out = CssMinifier.compressEnhanced(css, config)
    assert(out != null)
  }

  // -- MinifyOptions wiring (jekyll-minifier.rb:629-639 css_enhanced_options) --

  test("Iss1030: MinifyOptions.cssEnhancedOptions is None unless cssEnhancedMode") {
    assertEquals(MinifyOptions().cssEnhancedOptions, None)
  }

  test("Iss1030: MinifyOptions.cssEnhancedOptions carries the five enhancement flags") {
    val opts = MinifyOptions(
      cssEnhancedMode = true,
      cssMergeDuplicateSelectors = true,
      cssOptimizeShorthandProperties = true,
      cssAdvancedColorOptimization = true,
      cssPreserveIeHacks = false,
      cssCompressVariables = true
    )
    val config = opts.cssEnhancedOptions.getOrElse(fail("expected Some(config)"))
    assertEquals(config.mergeDuplicateSelectors, true)
    assertEquals(config.optimizeShorthandProperties, true)
    assertEquals(config.advancedColorOptimization, true)
    assertEquals(config.preserveIeHacks, false)
    assertEquals(config.compressCssVariables, true)
  }
}
