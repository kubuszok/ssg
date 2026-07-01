/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Regression + parity test for ISS-1193: CompressorOptions.resolveDefaults must
 * replicate terser's HOP-based defaults() (lib/utils/index.js:66-92 +
 * lib/compress/index.js:220-278) EXACTLY, and must NOT clobber explicitly-enabled
 * passes.
 *
 * The pre-fix bug: resolveDefaults used a majority-match vote (matchesDefaults vs
 * matchesNoDefaults counting). When `NoDefaults.copy(...)` enabled >=13 gated
 * passes and routed through a public entry (Terser.minify / Compressor.apply),
 * the count tied/flipped and the "resolve to off" branch CLOBBERED every
 * explicitly-enabled pass (turning e.g. `evaluate = true` back off).
 *
 * Oracle: terser (vendored at upstream-commit 6080510). Under
 * `{ defaults: false, <pass>: true, ... }` terser's defaults() keeps every
 * caller-set key (HOP true) and turns unset gated keys off. So enabling N passes
 * on top of `defaults:false` keeps exactly those N on — for any N.
 */
package ssg
package js
package compress

final class CompressorResolveDefaultsIss1193Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  import CompressorOptions.{ Defaults, NoDefaults, gatedFieldNames, resolveDefaults }

  // The 13 boolean gated passes whose enabling (on top of NoDefaults) creates the
  // exact 13-on / 13-off tie the issue describes (26 gated fields total). Under the
  // OLD majority-match vote, matchesNoDefaults == matchesDefaults == 13, so the vote
  // did NOT keep the options as-is and the resolve branch clobbered all 13.
  private val thirteenPasses: Set[String] = Set(
    "arrows",
    "booleans",
    "collapseVars",
    "comparisons",
    "computedProps",
    "conditionals",
    "deadCode",
    "directives",
    "dropDebugger",
    "evaluate",
    "hoistProps",
    "ifReturn",
    "joinVars"
  )

  /** Read a gated boolean field from resolved options by its Scala field name. */
  private def boolField(o: CompressorOptions, name: String): Boolean =
    o.get(camelToSnake(name)) match {
      case b: Boolean => b
      case other      => fail(s"expected Boolean for $name, got $other")
    }

  private def camelToSnake(name: String): String =
    name match {
      case "collapseVars"   => "collapse_vars"
      case "computedProps"  => "computed_props"
      case "deadCode"       => "dead_code"
      case "dropDebugger"   => "drop_debugger"
      case "hoistProps"     => "hoist_props"
      case "ifReturn"       => "if_return"
      case "joinVars"       => "join_vars"
      case "lhsConstants"   => "lhs_constants"
      case "negateIife"     => "negate_iife"
      case "reduceFuncs"    => "reduce_funcs"
      case "reduceVars"     => "reduce_vars"
      case "sideEffects"    => "side_effects"
      case other            => other
    }

  // -- CORE FIX: the 13/13 tie/flip scenario, resolved directly --
  // NoDefaults.copy enabling exactly 13 gated passes. Pre-fix, the vote tied and
  // the resolve branch turned every one of the 13 back OFF. Post-fix, all 13 stay
  // ON and the other 13 stay OFF (faithful HOP: present -> keep).
  test("ISS-1193: NoDefaults.copy enabling 13 passes keeps ALL 13 enabled (tie no longer clobbers)") {
    val opts = NoDefaults.copy(
      arrows = true,
      booleans = true,
      collapseVars = true,
      comparisons = true,
      computedProps = true,
      conditionals = true,
      deadCode = true,
      directives = true,
      dropDebugger = true,
      evaluate = true,
      hoistProps = true,
      ifReturn = true,
      joinVars = true
    )
    val resolved = resolveDefaults(opts)

    thirteenPasses.foreach { name =>
      assert(boolField(resolved, name), s"$name must STAY enabled after resolveDefaults (pre-fix: clobbered by the 13/13 tie)")
    }
    // The other 13 gated fields must remain OFF.
    (gatedFieldNames -- thirteenPasses).foreach { name =>
      name match {
        case "inline"         => assertEquals(resolved.inline, InlineLevel.InlineDisabled, "inline must stay disabled")
        case "sequencesLimit" => assertEquals(resolved.sequencesLimit, 0, "sequencesLimit must stay 0")
        case "pureGetters"    => assertEquals(resolved.pureGetters, "", "pureGetters must stay falsy \"\"")
        case bool             => assert(!boolField(resolved, bool), s"$bool must stay disabled")
      }
    }
  }

  // -- CORE FIX end-to-end through the PUBLIC entry (Terser.minify) --
  // Same 13-pass tie, but observed through minify output. `evaluate` is among the
  // 13, so `var x=1+2;` must fold to `var x=3;`. Pre-fix the clobber turned
  // evaluate off, producing `var x=1+2;`.
  test("ISS-1193: 13 passes via Terser.minify keeps evaluate ON (folds 1+2 -> 3)") {
    val opts = NoDefaults.copy(
      arrows = true,
      booleans = true,
      collapseVars = true,
      comparisons = true,
      computedProps = true,
      conditionals = true,
      deadCode = true,
      directives = true,
      dropDebugger = true,
      evaluate = true,
      hoistProps = true,
      ifReturn = true,
      joinVars = true
    )
    val out = Terser.minifyToString("var x = 1 + 2;", MinifyOptions(compress = opts, mangle = false))
    assertEquals(out, "var x=3;", "evaluate (1 of 13 enabled) must fold 1+2 (pre-fix: clobbered -> 'var x=1+2;')")
  }

  // Enabling ALL 26 gated passes on top of NoDefaults must keep all 26 on
  // (this equals `defaults: true` behavior — every pass on).
  test("ISS-1193: NoDefaults enabling all 26 gated passes keeps them all enabled") {
    val allOn = NoDefaults.copy(
      arrows = true,
      booleans = true,
      collapseVars = true,
      comparisons = true,
      computedProps = true,
      conditionals = true,
      deadCode = true,
      directives = true,
      dropDebugger = true,
      evaluate = true,
      hoistProps = true,
      ifReturn = true,
      inline = InlineLevel.InlineFull,
      joinVars = true,
      lhsConstants = true,
      loops = true,
      negateIife = true,
      properties = true,
      pureGetters = "strict",
      reduceFuncs = true,
      reduceVars = true,
      sequencesLimit = 800,
      sideEffects = true,
      switches = true,
      typeofs = true,
      unused = true
    )
    val resolved = resolveDefaults(allOn)
    gatedFieldNames.foreach { name =>
      name match {
        case "inline"         => assertEquals(resolved.inline, InlineLevel.InlineFull, "inline must stay full")
        case "sequencesLimit" => assertEquals(resolved.sequencesLimit, 800, "sequencesLimit must stay 800")
        case "pureGetters"    => assertEquals(resolved.pureGetters, "strict", "pureGetters must stay strict")
        case bool             => assert(boolField(resolved, bool), s"$bool must stay enabled")
      }
    }
  }

  // -- terser HOP parity: defaults=false + ONE enabled pass --
  test("ISS-1193: NoDefaults.copy(evaluate=true) keeps evaluate ON, all others OFF (HOP)") {
    val resolved = resolveDefaults(NoDefaults.copy(evaluate = true))
    assert(resolved.evaluate, "evaluate must be ON")
    (gatedFieldNames - "evaluate").foreach { name =>
      name match {
        case "inline"         => assertEquals(resolved.inline, InlineLevel.InlineDisabled, "inline OFF")
        case "sequencesLimit" => assertEquals(resolved.sequencesLimit, 0, "sequencesLimit OFF")
        case "pureGetters"    => assertEquals(resolved.pureGetters, "", "pureGetters OFF")
        case bool             => assert(!boolField(resolved, bool), s"$bool must be OFF")
      }
    }
  }

  // -- defaults=false alone -> ALL gated OFF --
  test("ISS-1193: NoDefaults alone resolves to all gated OFF") {
    val resolved = resolveDefaults(NoDefaults)
    gatedFieldNames.foreach { name =>
      name match {
        case "inline"         => assertEquals(resolved.inline, InlineLevel.InlineDisabled, "inline OFF")
        case "sequencesLimit" => assertEquals(resolved.sequencesLimit, 0, "sequencesLimit OFF")
        case "pureGetters"    => assertEquals(resolved.pureGetters, "", "pureGetters OFF")
        case bool             => assert(!boolField(resolved, bool), s"$bool must be OFF")
      }
    }
  }

  // `CompressorOptions(defaults = false)` (Unspecified presence) must also resolve
  // to all-off, matching terser `{ defaults: false }`.
  test("ISS-1193: CompressorOptions(defaults=false) resolves to all gated OFF") {
    val resolved = resolveDefaults(CompressorOptions(defaults = false))
    gatedFieldNames.foreach { name =>
      name match {
        case "inline"         => assertEquals(resolved.inline, InlineLevel.InlineDisabled, "inline OFF")
        case "sequencesLimit" => assertEquals(resolved.sequencesLimit, 0, "sequencesLimit OFF")
        case "pureGetters"    => assertEquals(resolved.pureGetters, "", "pureGetters OFF")
        case bool             => assert(!boolField(resolved, bool), s"$bool must be OFF")
      }
    }
  }

  // -- defaults=true / omitted -> ALL gated ON --
  test("ISS-1193: Defaults (defaults=true) resolves to all gated ON") {
    val resolved = resolveDefaults(Defaults)
    gatedFieldNames.foreach { name =>
      name match {
        case "inline"         => assertEquals(resolved.inline, InlineLevel.InlineFull, "inline ON")
        case "sequencesLimit" => assertEquals(resolved.sequencesLimit, 800, "sequencesLimit ON")
        case "pureGetters"    => assertEquals(resolved.pureGetters, "strict", "pureGetters ON")
        case bool             => assert(boolField(resolved, bool), s"$bool must be ON")
      }
    }
  }

  // -- explicitly-DISABLED pass under defaults=true stays OFF --
  test("ISS-1193: defaults=true with evaluate=false keeps evaluate OFF, others ON") {
    val resolved = resolveDefaults(CompressorOptions(evaluate = false))
    assert(!resolved.evaluate, "evaluate must stay OFF (explicit disable under defaults=true)")
    assert(resolved.deadCode, "deadCode must stay ON")
    assert(resolved.reduceVars, "reduceVars must stay ON")
  }

  // -- presence semantics: explicit Provided set (true HOP for DIRECT construction) --
  // This is the capability the old value-comparison limitation lacked: an explicitly
  // ENABLED normally-on pass under defaults=false is now EXPRESSIBLE and kept.
  test("ISS-1193: Provided(evaluate) under defaults=false keeps evaluate ON, others OFF (limitation gone)") {
    val opts = CompressorOptions(
      defaults = false,
      evaluate = true,
      gatedPresence = GatedPresence.Provided(Set("evaluate"))
    )
    val resolved = resolveDefaults(opts)
    assert(resolved.evaluate, "explicitly-Provided evaluate must be KEPT ON under defaults=false")
    assert(!resolved.deadCode, "unset deadCode must resolve OFF under defaults=false")
    assert(!resolved.reduceVars, "unset reduceVars must resolve OFF under defaults=false")
    // Absent NON-boolean gated fields must resolve to their falsy off-defaults
    // (terser: pure_getters -> "", inline -> 0/disabled, sequences -> 0).
    assertEquals(resolved.pureGetters, "", "unset pureGetters must resolve to falsy \"\" under defaults=false")
    assertEquals(resolved.inline, InlineLevel.InlineDisabled, "unset inline must resolve to disabled under defaults=false")
    assertEquals(resolved.sequencesLimit, 0, "unset sequencesLimit must resolve to 0 under defaults=false")
  }

  // Provided with a partial set under defaults=TRUE: absent non-boolean gated
  // fields must resolve to their ON-defaults (terser !false_by_default).
  test("ISS-1193: Provided(evaluate) under defaults=true resolves absent non-booleans to ON-defaults") {
    val opts = CompressorOptions(
      evaluate = true,
      gatedPresence = GatedPresence.Provided(Set("evaluate"))
    )
    val resolved = resolveDefaults(opts)
    assert(resolved.evaluate, "Provided evaluate must be kept ON")
    assertEquals(resolved.pureGetters, "strict", "unset pureGetters must resolve to \"strict\" under defaults=true")
    assertEquals(resolved.inline, InlineLevel.InlineFull, "unset inline must resolve to full under defaults=true")
    assertEquals(resolved.sequencesLimit, 800, "unset sequencesLimit must resolve to 800 under defaults=true")
    assert(resolved.deadCode, "unset deadCode must resolve ON under defaults=true")
  }

  // presence semantics: a Provided field set to FALSE is kept false (not defaulted on).
  test("ISS-1193: Provided(evaluate=false) under defaults=true keeps evaluate OFF, unset others ON") {
    val opts = CompressorOptions(
      evaluate = false,
      gatedPresence = GatedPresence.Provided(Set("evaluate"))
    )
    val resolved = resolveDefaults(opts)
    assert(!resolved.evaluate, "Provided evaluate=false must be KEPT off")
    assert(resolved.deadCode, "unset deadCode must default ON under defaults=true")
  }
}
