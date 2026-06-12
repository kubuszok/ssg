/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1082: KaTeX.ensureRegistered() (KaTeX.scala) invokes
 * Macros.registerAll() on EVERY render, and unlike Functions.registerAll /
 * Environments.registerAll (both guarded by a @volatile `registered` flag),
 * Macros.registerAll() has no idempotence guard. Every render therefore
 * re-defines ~340 builtin macros into the global MacroDef._macros table,
 * silently clobbering any user macro registered via KaTeX.__defineMacro
 * that shadows a builtin name.
 *
 * Upstream reference (original-src/katex, v0.16.45), cited per C11:
 *   - src/defineMacro.ts:119-124 — the single global table
 *     `export const _macros: MacroMap = {};` and `defineMacro` which writes
 *     `_macros[name] = body` into it.
 *   - src/macros.ts — the builtin macro table is built ONCE at module load
 *     (top-level `defineMacro(...)` calls; e.g. macros.ts:270 defines
 *     `defineMacro("\\Bbbk", "\\Bbb{k}");`). ES modules evaluate once, so
 *     the builtins are never re-defined afterwards.
 *   - katex.ts:168 and katex.ts:241 — `defineMacro as __defineMacro` is
 *     exported directly, so user macros registered via __defineMacro write
 *     into the SAME `_macros` table and persist across renders — including
 *     user macros that shadow builtin names.
 *
 * Expected values, from the original source:
 *   - builtin `\Bbbk` expands to `\Bbb{k}` (macros.ts:270) and `\Bbb` is the
 *     `\mathbb` font alias (src/functions/font.js oldFontFuncsMap), so the
 *     builtin markup carries the "mathbb" font class;
 *   - after `__defineMacro("\\Bbbk", "z")` the markup must contain the
 *     rendered `z` glyph (`>z<`) and no "mathbb" class, on EVERY subsequent
 *     render, exactly as upstream behaves.
 */
package ssg
package katex

class MacrosGuardIss1082Suite extends KaTeXTestSuite {

  // The registries are global mutable state shared by every suite in the run.
  // Snapshot the macro table before each test and restore it afterwards so
  // the shadows/additions made here never leak into neighbouring suites.
  private var macrosSnapshot: Map[String, MacroDefinition] = Map.empty

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    // KaTeXTestSuite.beforeAll has already forced full registration via a
    // render, so this snapshot contains the complete builtin table.
    macrosSnapshot = MacroDef._macros.toMap
  }

  override def afterEach(context: AfterEach): Unit = {
    MacroDef._macros.clear()
    MacroDef._macros ++= macrosSnapshot
    super.afterEach(context)
  }

  test("control: builtin \\Bbbk renders via \\Bbb{k} on every render") {
    val first  = KaTeX.renderToString("\\Bbbk")
    val second = KaTeX.renderToString("\\Bbbk")
    assert(
      first.contains("mathbb"),
      s"builtin \\Bbbk should carry the mathbb font class; markup: $first"
    )
    assertEquals(second, first, "builtin \\Bbbk must render identically on repeated renders")
  }

  test("control: non-shadowing user macro persists across renders") {
    // registerAll() only re-puts builtin names, so a user macro under a
    // fresh name is untouched by re-registration and passes even before the
    // ISS-1082 fix. It pins the upstream persistence contract for the
    // non-shadowing half (katex.ts:241 writes into the same _macros table).
    KaTeX.__defineMacro("\\ssgIssUserMacro", MacroDefinition.StringDef("y"))
    val first  = KaTeX.renderToString("\\ssgIssUserMacro")
    val second = KaTeX.renderToString("\\ssgIssUserMacro")
    assert(
      first.contains(">y<"),
      s"non-shadowing user macro should apply on the first render; markup: $first"
    )
    assert(
      second.contains(">y<"),
      s"non-shadowing user macro should still apply on the second render; markup: $second"
    )
  }

  test("ISS-1082 red: user macro shadowing builtin \\Bbbk applies on the first render") {
    // Upstream: __defineMacro overwrites the builtin entry in _macros and the
    // builtin table is never rebuilt (macros.ts module evaluates once), so
    // the user definition applies immediately and indefinitely.
    KaTeX.__defineMacro("\\Bbbk", MacroDefinition.StringDef("z"))
    val first = KaTeX.renderToString("\\Bbbk")
    assert(
      first.contains(">z<"),
      s"user shadow of \\Bbbk should apply on the first render; markup: $first"
    )
    assert(
      !first.contains("mathbb"),
      s"builtin \\Bbb{k} expansion must not reappear once shadowed; markup: $first"
    )
  }

  test("ISS-1082 red: user macro shadowing builtin \\Bbbk survives a second render") {
    KaTeX.__defineMacro("\\Bbbk", MacroDefinition.StringDef("z"))
    val first  = KaTeX.renderToString("\\Bbbk")
    val second = KaTeX.renderToString("\\Bbbk")
    assert(
      second.contains(">z<"),
      s"user shadow of \\Bbbk must survive repeated renders; first: $first; second: $second"
    )
    assert(
      !second.contains("mathbb"),
      s"builtin \\Bbb{k} expansion must not be restored by a later render; markup: $second"
    )
  }
}
