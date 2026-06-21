/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Mangle + compression tests for issue-1704 (catch variable mangling).
 * Ported from: terser/test/compress/issue-1704.js — 18 of 20 upstream tests
 * ported (those with expect_exact); 2 stdout-only tests omitted
 * (mangle_catch_redef_3_toplevel :372, mangle_catch_redef_3_ie8_toplevel :417).
 * All 18 ported tests are live (ISS-1231 catch-var redefinition crash is fixed).
 *
 * Auto-ported by hand since gen-compress-tests.js does not support mangle format. */
package ssg
package js
package compress

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.scope.ManglerOptions

final class CompressIssue1704Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // The upstream issue-1704 fixtures are `false_by_default` (test/compress.js:
  // 409-412): each `options = { ie8, toplevel }` block enables ONLY the listed
  // flags, with every other compress flag off. We therefore build the compress
  // options on CompressTestHelper.AllOff (the established false_by_default base)
  // and copy in just ie8/toplevel, mirroring the Terser test runner. Per ISS-1144
  // the previous helper used a defaults-ON CompressorOptions, which diverged from
  // the upstream harness.
  private def minifyWithMangleAndCompress(
    input:    String,
    ie8:      Boolean = false,
    toplevel: Boolean = false
  ): String =
    Terser.minifyToString(
      input,
      MinifyOptions(
        compress = CompressTestHelper.AllOff.copy(
          ie8 = ie8,
          toplevel = if (toplevel) ToplevelConfig(funcs = true, vars = true) else ToplevelConfig()
        ),
        mangle = ManglerOptions(ie8 = ie8, toplevel = toplevel)
      )
    )

  private val catchInput = "var a = \"FAIL\";\ntry {\n    throw 1;\n} catch (args) {\n    a = \"PASS\";\n}\nconsole.log(a);"

  // Known gap: exact mangled variable names differ from upstream due to scope
  // analysis ordering differences.

  // =========================================================================
  // mangle_catch
  // =========================================================================
  test("mangle_catch") {
    val result = minifyWithMangleAndCompress(catchInput)
    assertEquals(result, "var a=\"FAIL\";try{throw 1}catch(o){a=\"PASS\"}console.log(a);")
  }

  // =========================================================================
  // mangle_catch_ie8
  // =========================================================================
  test("mangle_catch_ie8") {
    val result = minifyWithMangleAndCompress(catchInput, ie8 = true)
    assertEquals(result, "var a=\"FAIL\";try{throw 1}catch(args){a=\"PASS\"}console.log(a);")
  }

  // =========================================================================
  // mangle_catch_var
  // =========================================================================
  // Expectation is upstream terser/test/compress/issue-1704.js:63 expect_exact,
  // verbatim. Was pinned on ISS-1136 (mangled catch-arg name never reached printed
  // output; harness rebuilt per ISS-1144); pin retired by the ISS-1136 fix. NOT
  // ISS-1035: with the false_by_default base, drop_unused is off and the `var`
  // keyword is retained, so the only blocker was the un-printed mangled name.
  test("mangle_catch_var") {
    val input  = "var a = \"FAIL\";\ntry {\n    throw 1;\n} catch (args) {\n    var a = \"PASS\";\n}\nconsole.log(a);"
    val result = minifyWithMangleAndCompress(input)
    assertEquals(result, "var a=\"FAIL\";try{throw 1}catch(o){var a=\"PASS\"}console.log(a);")
  }

  // =========================================================================
  // mangle_catch_var_ie8
  // =========================================================================
  // Upstream keeps catch arg `args` under ie8 (no mangle) and retains the hoisted
  // `var a`. Un-pinned: passes after rebuilding the harness on the false_by_default
  // base (ISS-1144) — drop_unused is off so the `var` keyword is retained, ie8
  // suppresses catch-arg mangling, and toplevel=false leaves `a` un-mangled, so no
  // mangled name needs to reach output. Expectation is issue-1704.js:85 verbatim.
  test("mangle_catch_var_ie8") {
    val input  = "var a = \"FAIL\";\ntry {\n    throw 1;\n} catch (args) {\n    var a = \"PASS\";\n}\nconsole.log(a);"
    val result = minifyWithMangleAndCompress(input, ie8 = true)
    // upstream terser/test/compress/issue-1704.js:85 expect_exact
    assertEquals(result, "var a=\"FAIL\";try{throw 1}catch(args){var a=\"PASS\"}console.log(a);")
  }

  // =========================================================================
  // mangle_catch_toplevel
  // =========================================================================
  // upstream terser/test/compress/issue-1704.js:107 expect_exact — `catch(c)`,
  // corrected from the previous `catch(t)` which contradicted that line.
  test("mangle_catch_toplevel") {
    val result = minifyWithMangleAndCompress(catchInput, toplevel = true)
    assertEquals(result, "var o=\"FAIL\";try{throw 1}catch(c){o=\"PASS\"}console.log(o);")
  }

  // =========================================================================
  // mangle_catch_ie8_toplevel
  // =========================================================================
  // upstream terser/test/compress/issue-1704.js:129 expect_exact — `catch(c)`,
  // corrected from the previous `catch(args)` which contradicted that line.
  test("mangle_catch_ie8_toplevel") {
    val result = minifyWithMangleAndCompress(catchInput, ie8 = true, toplevel = true)
    assertEquals(result, "var o=\"FAIL\";try{throw 1}catch(c){o=\"PASS\"}console.log(o);")
  }

  // =========================================================================
  // mangle_catch_var_toplevel
  // =========================================================================
  test("mangle_catch_var_toplevel") {
    val input  = "var a = \"FAIL\";\ntry {\n    throw 1;\n} catch (args) {\n    var a = \"PASS\";\n}\nconsole.log(a);"
    val result = minifyWithMangleAndCompress(input, toplevel = true)
    // upstream terser/test/compress/issue-1704.js:151 expect_exact
    assertEquals(result, "var o=\"FAIL\";try{throw 1}catch(r){var o=\"PASS\"}console.log(o);")
  }

  // =========================================================================
  // mangle_catch_var_ie8_toplevel
  // =========================================================================
  test("mangle_catch_var_ie8_toplevel") {
    val input  = "var a = \"FAIL\";\ntry {\n    throw 1;\n} catch (args) {\n    var a = \"PASS\";\n}\nconsole.log(a);"
    val result = minifyWithMangleAndCompress(input, ie8 = true, toplevel = true)
    // upstream terser/test/compress/issue-1704.js:173 expect_exact — `catch(r)`,
    // corrected from the previous `catch(args)` which contradicted that line.
    assertEquals(result, "var o=\"FAIL\";try{throw 1}catch(r){var o=\"PASS\"}console.log(o);")
  }

  // =========================================================================
  // mangle_catch_redef — catch var redefinitions with ie8/toplevel combos.
  // 18 of 20 upstream tests ported (those with expect_exact); 2 omitted
  // (mangle_catch_redef_3_toplevel issue-1704.js:372 and
  // mangle_catch_redef_3_ie8_toplevel issue-1704.js:417) because they have
  // only expect_stdout — SSG has no JS runtime to verify stdout, and
  // asserting SSG's own output would be a tautology (C11).
  // All 8 redef_1/redef_2 tests are live (ISS-1231 ClassCastException in
  // ScopeAnalysis.handleVarLikeDecl is fixed; output matches upstream expect_exact).
  // =========================================================================

  private val redef1Input =
    "var a = \"PASS\";\ntry {\n    throw \"FAIL1\";\n} catch (a) {\n    var a = \"FAIL2\";\n}\nconsole.log(a);"
  private val redef2Input =
    "try {\n    throw \"FAIL1\";\n} catch (a) {\n    var a = \"FAIL2\";\n}\nconsole.log(a);"
  private val redef3Input =
    "var o = \"PASS\";\ntry {\n    throw 0;\n} catch (o) {\n    (function() {\n        function f() {\n            o = \"FAIL\";\n        }\n        f(), f();\n    })();\n}\nconsole.log(o);"

  test("mangle_catch_redef_1") { // issue-1704.js:195
    assertEquals(
      minifyWithMangleAndCompress(redef1Input),
      "var a=\"PASS\";try{throw\"FAIL1\"}catch(a){var a=\"FAIL2\"}console.log(a);"
    )
  }
  test("mangle_catch_redef_1_ie8") { // issue-1704.js:217
    assertEquals(
      minifyWithMangleAndCompress(redef1Input, ie8 = true),
      "var a=\"PASS\";try{throw\"FAIL1\"}catch(a){var a=\"FAIL2\"}console.log(a);"
    )
  }
  test("mangle_catch_redef_1_toplevel") { // issue-1704.js:239
    assertEquals(
      minifyWithMangleAndCompress(redef1Input, toplevel = true),
      "var o=\"PASS\";try{throw\"FAIL1\"}catch(o){var o=\"FAIL2\"}console.log(o);"
    )
  }
  test("mangle_catch_redef_1_ie8_toplevel") { // issue-1704.js:261
    assertEquals(
      minifyWithMangleAndCompress(redef1Input, ie8 = true, toplevel = true),
      "var o=\"PASS\";try{throw\"FAIL1\"}catch(o){var o=\"FAIL2\"}console.log(o);"
    )
  }
  test("mangle_catch_redef_2") { // issue-1704.js:282
    assertEquals(
      minifyWithMangleAndCompress(redef2Input),
      "try{throw\"FAIL1\"}catch(a){var a=\"FAIL2\"}console.log(a);"
    )
  }
  test("mangle_catch_redef_2_ie8") { // issue-1704.js:303
    assertEquals(
      minifyWithMangleAndCompress(redef2Input, ie8 = true),
      "try{throw\"FAIL1\"}catch(a){var a=\"FAIL2\"}console.log(a);"
    )
  }
  test("mangle_catch_redef_2_toplevel") { // issue-1704.js:325
    assertEquals(
      minifyWithMangleAndCompress(redef2Input, toplevel = true),
      "try{throw\"FAIL1\"}catch(o){var o=\"FAIL2\"}console.log(o);"
    )
  }
  test("mangle_catch_redef_2_ie8_toplevel") { // issue-1704.js:347
    assertEquals(
      minifyWithMangleAndCompress(redef2Input, ie8 = true, toplevel = true),
      "try{throw\"FAIL1\"}catch(o){var o=\"FAIL2\"}console.log(o);"
    )
  }

  test("mangle_catch_redef_3") { // issue-1704.js:368
    assertEquals(
      minifyWithMangleAndCompress(redef3Input),
      "var o=\"PASS\";try{throw 0}catch(o){(function(){function c(){o=\"FAIL\"}c(),c()})()}console.log(o);"
    )
  }
  test("mangle_catch_redef_ie8_3") { // issue-1704.js:413
    assertEquals(
      minifyWithMangleAndCompress(redef3Input, ie8 = true),
      "var o=\"PASS\";try{throw 0}catch(o){(function(){function c(){o=\"FAIL\"}c(),c()})()}console.log(o);"
    )
  }
}
