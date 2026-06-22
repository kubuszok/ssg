/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Test for ISS-1086: KaTeX.renderToString should accept KaTeXOptions directly.
 *
 * The KaTeX object's renderToString method only accepted the internal Settings
 * type. Users had to either construct Settings manually (exposing internal
 * details) or use KaTeXOptions.renderToString (a companion static that is less
 * discoverable). This test proves that the new KaTeX.renderToString(String,
 * KaTeXOptions) overload exists, compiles, and correctly delegates through
 * KaTeXOptions.toSettings so that options flow through to rendering.
 *
 * Expected values: all three rendering paths (new overload, manual
 * toSettings bridge, KaTeXOptions companion) must produce identical markup.
 * Display mode produces a "katex-display" wrapper class that inline mode does
 * not, proving the option is honored (upstream katex.ts buildTree wraps
 * display-mode output in a katex-display span — original-src/katex/src/
 * buildTree.ts:155).
 */
package ssg
package katex

class KaTeXOptionsEntryPointIss1086Suite extends munit.FunSuite {

  test("KaTeX.renderToString(expr, KaTeXOptions) overload exists and delegates correctly") {
    val expr = "x^2"
    val opts = KaTeXOptions(displayMode = true)

    // The NEW overload — this line would not compile without the fix
    val viaNewOverload = KaTeX.renderToString(expr, opts)

    // The manual bridge (calling toSettings explicitly)
    val viaManualBridge = KaTeX.renderToString(expr, opts.toSettings)

    // The existing KaTeXOptions companion path
    val viaCompanion = KaTeXOptions.renderToString(expr, opts)

    // All three must produce identical output
    assertEquals(viaNewOverload, viaManualBridge)
    assertEquals(viaNewOverload, viaCompanion)

    // Display mode must produce the "katex-display" wrapper
    assert(
      viaNewOverload.contains("katex-display"),
      s"Display-mode output should contain 'katex-display' class but got: $viaNewOverload"
    )
  }

  test("KaTeX.renderToString(expr, KaTeXOptions) display mode differs from inline mode") {
    val expr = "x^2"

    val displayOutput = KaTeX.renderToString(expr, KaTeXOptions(displayMode = true))
    val inlineOutput = KaTeX.renderToString(expr, KaTeXOptions(displayMode = false))

    // Display mode wraps in katex-display, inline does not
    assert(
      displayOutput.contains("katex-display"),
      s"Display-mode output should contain 'katex-display' but got: $displayOutput"
    )
    assert(
      !inlineOutput.contains("katex-display"),
      s"Inline-mode output should NOT contain 'katex-display' but got: $inlineOutput"
    )

    // They must be different (the display wrapper changes the output)
    assertNotEquals(displayOutput, inlineOutput)
  }

  test("KaTeX.renderToString(expr) still resolves to the Settings-default overload") {
    // This proves no overload-resolution ambiguity: calling with just one arg
    // still works and produces valid output
    val result = KaTeX.renderToString("x^2")
    assert(
      result.contains("katex"),
      s"Single-arg renderToString should produce KaTeX markup but got: $result"
    )
    assert(
      !result.contains("katex-display"),
      "Single-arg renderToString should default to inline mode (no katex-display)"
    )
  }
}
