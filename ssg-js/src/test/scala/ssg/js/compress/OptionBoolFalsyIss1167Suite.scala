/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1167: CompressorLike.optionBool must mirror JS truthiness of
 * `compressor.option(name)`. The current implementation has a blanket-truthy
 * catch-all (`case _ => true`) that wrongly reports SSG values modelling a
 * FALSY upstream state as truthy.
 *
 * Two concrete falsy-modelling cases:
 *   1. scala.None  -- an unset option (e.g. terser `top_retain`, which upstream
 *      is null/undefined and therefore FALSY in boolean position). The catch-all
 *      reports it truthy; it must be false.
 *   2. ToplevelConfig(false, false) -- SSG's representation of "toplevel
 *      disabled" (terser `toplevel: false`, FALSY). The catch-all reports it
 *      truthy; it must be false. A ToplevelConfig with either flag set models
 *      an ENABLED toplevel and is correctly truthy.
 *
 * The bug is latent (no current call site feeds these values to optionBool),
 * so the suite drives optionBool DIRECTLY via a test double whose option(name)
 * is keyed on the option name.
 *
 * Runs on JVM, JS, and Native.
 */
package ssg
package js
package compress

import scala.reflect.ClassTag

import ssg.js.ast.*

final class OptionBoolFalsyIss1167Suite extends munit.FunSuite {

  // A test double for CompressorLike: option(name) returns a value keyed on the
  // option name so a single instance can probe optionBool across many cases.
  // Every other abstract member is a no-op that is never exercised here.
  final class OptionDouble(values: Map[String, Any]) extends CompressorLike {
    def option(name:                       String):      Any            = values.getOrElse(name, null)
    def optimizeNode(node:                 AstNode):     AstNode        = node
    def parent(n:                          Int = 0):     AstNode | Null = null
    def findParent[T <: AstNode](using ct: ClassTag[T]): T | Null       = null
    def inBooleanContext():                              Boolean        = false
    def in32BitContext():                                Boolean        = false
    def hasDirective(directive:            String):      AstNode | Null = null
    def exposed(theDef:                    Any):         Boolean        = false
    def pureFuncs(call:                    AstCall):     Boolean        = false
  }

  private val double = new OptionDouble(
    Map(
      // Falsy-modelling values (the bug): currently caught by `case _ => true`.
      "top_retain" -> None,
      "toplevel_off" -> CompressorLike.ToplevelConfig(false, false),
      // Truthy ToplevelConfig variants (contract: an enabled toplevel).
      "toplevel_both" -> CompressorLike.ToplevelConfig(true, true),
      "toplevel_funcs" -> CompressorLike.ToplevelConfig(true, false),
      "toplevel_vars" -> CompressorLike.ToplevelConfig(false, true),
      // Primitive truthiness controls (already handled correctly today).
      "bool_true" -> true,
      "bool_false" -> false,
      "int_five" -> 5,
      "int_zero" -> 0,
      "str_x" -> "x",
      "str_empty" -> "",
      "null_val" -> null
      // (absent keys default to null via getOrElse)
    )
  )

  // ---- The bug: falsy-modelling values wrongly reported truthy (RED) ----

  test("optionBool(None) is false -- upstream null/undefined is falsy (ISS-1167)") {
    assert(
      !double.optionBool("top_retain"),
      "option() == scala.None models an unset upstream option (terser top_retain " +
        "is null/undefined, which is FALSY in JS boolean position); optionBool must be false"
    )
  }

  test("optionBool(ToplevelConfig(false,false)) is false -- toplevel disabled is falsy (ISS-1167)") {
    assert(
      !double.optionBool("toplevel_off"),
      "ToplevelConfig(false,false) models terser `toplevel: false` (toplevel optimization " +
        "OFF), which is FALSY in JS boolean position; optionBool must be false"
    )
  }

  // ---- Contract: enabled toplevel and primitives (should stay GREEN) ----

  test("optionBool(ToplevelConfig with a flag set) is true -- toplevel enabled is truthy") {
    assert(double.optionBool("toplevel_both"), "ToplevelConfig(true,true) is an enabled toplevel -> truthy")
    assert(double.optionBool("toplevel_funcs"), "ToplevelConfig(true,false) is an enabled toplevel -> truthy")
    assert(double.optionBool("toplevel_vars"), "ToplevelConfig(false,true) is an enabled toplevel -> truthy")
  }

  test("optionBool primitive truthiness mirrors JS") {
    assert(double.optionBool("bool_true"), "true is truthy")
    assert(double.optionBool("int_five"), "non-zero number is truthy")
    assert(double.optionBool("str_x"), "non-empty string is truthy")
    assert(!double.optionBool("bool_false"), "false is falsy")
    assert(!double.optionBool("int_zero"), "0 is falsy")
    assert(!double.optionBool("str_empty"), "empty string is falsy")
    assert(!double.optionBool("null_val"), "null is falsy")
  }
}
