/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1146 (R0610-P3, bug): Compressor.resetOptFlags diverges from terser's
 * reset_opt_flags (lib/compress/index.js:618-642) in the TOP-flag logic.
 *
 * terser oracle (index.js:624-630):
 *   if (reduce_vars) {
 *     if (compressor.top_retain
 *         && node instanceof AST_Defun
 *         && preparation.parent() === self  // self = the AST_Toplevel
 *     ) { set_flag(node, TOP); }
 *     ...
 *   }
 *
 * SSG divergences:
 *   (1) topRetain compared against a fresh lambda -- always true (dead clause)
 *   (2) topRetain predicate called in resetOptFlags -- terser only calls it in
 *       retain_top_func (common.js:369-375), NOT in reset_opt_flags
 *   (3) no parent==toplevel guard -- nested AstDefun nodes get TOP
 *   (4) no reduce_vars guard -- TOP is set even when reduce_vars is off
 *
 * The TOP flag means "this is a top-level function definition eligible for the
 * top_retain mechanism". It is set when ALL of: reduce_vars is on, top_retain
 * is present, the node is AstDefun, and the walker's parent is the toplevel.
 * The actual retain_top_func predicate (common.js:374) is applied later, in
 * drop_unused / inline, NOT during flag-setting.
 *
 * This suite drives compress() (which calls resetOptFlags internally) and then
 * inspects the TOP flag on surviving AstDefun nodes. With all optimization
 * flags off the AST survives the transform pass unchanged, so flags persist.
 *
 * Runs on JVM, JS, and Native. */
package ssg
package js
package compress

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.{ TOP, hasFlag }
import ssg.js.parse.Parser

final class TopRetainResetOptFlagsIss1146Suite extends munit.FunSuite {

  // Input: two top-level defuns (alpha, beta) and one nested defun (inner,
  // nested inside outer). All are declared -- no undeclared references
  // (avoids ISS-031/032 scope-analysis hang).
  private val input: String =
    "function alpha() { var x = 1; }" +
      "function beta() { var y = 2; }" +
      "function outer() { function inner() { var z = 3; } }"

  /** Parse, compress with the given options (1 pass, all optimizations off except the flags under test), and collect all AstDefun nodes from the resulting AST together with their name and TOP-flag
    * state.
    */
  private def compressAndCollectFlags(
    opts: CompressorOptions
  ): Map[String, Boolean] = {
    val ast        = new Parser().parse(input)
    val compressor = new Compressor(opts)
    val result     = compressor.compress(ast)

    val flags = scala.collection.mutable.Map.empty[String, Boolean]
    ssg.js.ast.walk(
      result,
      (node, _) => {
        node match {
          case defun: AstDefun =>
            defun.name match {
              case sym: AstSymbol =>
                flags(sym.name) = hasFlag(defun, TOP)
              case _ =>
            }
          case _ =>
        }
        null // continue walking
      }
    )
    flags.toMap
  }

  // All-off base with a single pass -- the AST is minimally transformed so
  // AstDefun nodes survive and their flags are inspectable after compress().
  private val baseOpts: CompressorOptions = CompressorOptions.NoDefaults

  // ---- PRIMARY: reduce_vars ON + topRetain present ----
  // Per terser oracle: EVERY top-level AstDefun gets TOP, regardless of whether
  // it matches the predicate. The predicate (matching only "alpha") is NOT
  // invoked in reset_opt_flags -- it is invoked later in retain_top_func.

  test("ISS-1146 all top-level defuns get TOP when topRetain is present and reduce_vars is on") {
    val opts = baseOpts.copy(
      reduceVars = true,
      topRetain = Some((_: String) => true) // predicate matches everything -- irrelevant here
    )
    val flags = compressAndCollectFlags(opts)

    assert(
      flags.getOrElse("alpha", false),
      s"alpha (top-level) must have TOP when topRetain is present + reduce_vars on, flags=$flags"
    )
    assert(
      flags.getOrElse("beta", false),
      s"beta (top-level) must have TOP when topRetain is present + reduce_vars on, flags=$flags"
    )
    assert(
      flags.getOrElse("outer", false),
      s"outer (top-level) must have TOP when topRetain is present + reduce_vars on, flags=$flags"
    )
  }

  // Key distinguisher: even when the predicate does NOT match a top-level
  // defun, it must STILL get TOP. The predicate is not called in
  // reset_opt_flags -- only in retain_top_func.
  test("ISS-1146 top-level defun gets TOP even when predicate does not match it") {
    val opts = baseOpts.copy(
      reduceVars = true,
      topRetain = Some((n: String) => n == "alpha") // matches only "alpha"
    )
    val flags = compressAndCollectFlags(opts)

    // alpha matches the predicate, but that is irrelevant -- TOP is set on ALL
    // top-level defuns when top_retain is present.
    assert(
      flags.getOrElse("alpha", false),
      s"alpha must have TOP (it is a top-level defun), flags=$flags"
    )
    // beta does NOT match the predicate, but must STILL have TOP.
    // This is the core fix: the old code only flagged predicate-matching defuns.
    assert(
      flags.getOrElse("beta", false),
      s"beta must have TOP even though predicate does not match it (terser " +
        s"index.js:625-629 flags ALL top-level AST_Defun, not just predicate-matching ones), flags=$flags"
    )
    assert(
      flags.getOrElse("outer", false),
      s"outer must have TOP (it is a top-level defun), flags=$flags"
    )
  }

  // ---- PARENT GUARD: nested defun must NOT get TOP ----
  test("ISS-1146 nested defun does NOT get TOP (parent guard: preparation.parent() === self)") {
    val opts = baseOpts.copy(
      reduceVars = true,
      topRetain = Some((_: String) => true) // matches everything
    )
    val flags = compressAndCollectFlags(opts)

    assert(
      !flags.getOrElse("inner", true),
      s"inner (nested inside outer) must NOT have TOP -- terser index.js:627 " +
        s"gates on preparation.parent() === self (the toplevel), flags=$flags"
    )
  }

  // ---- REDUCE_VARS GUARD: no TOP when reduce_vars is off ----
  test("ISS-1146 no TOP flags when reduce_vars is off") {
    val opts = baseOpts.copy(
      reduceVars = false,
      topRetain = Some((_: String) => true)
    )
    val flags = compressAndCollectFlags(opts)

    assert(
      !flags.getOrElse("alpha", true),
      s"alpha must NOT have TOP when reduce_vars is off (terser index.js:624 " +
        s"gates on reduce_vars), flags=$flags"
    )
    assert(
      !flags.getOrElse("beta", true),
      s"beta must NOT have TOP when reduce_vars is off, flags=$flags"
    )
    assert(
      !flags.getOrElse("outer", true),
      s"outer must NOT have TOP when reduce_vars is off, flags=$flags"
    )
    assert(
      !flags.getOrElse("inner", true),
      s"inner must NOT have TOP when reduce_vars is off, flags=$flags"
    )
  }

  // ---- NO TOP_RETAIN: no TOP flags when topRetain is absent ----
  test("ISS-1146 no TOP flags when topRetain is absent") {
    val opts = baseOpts.copy(
      reduceVars = true,
      topRetain = None
    )
    val flags = compressAndCollectFlags(opts)

    assert(
      !flags.getOrElse("alpha", true),
      s"alpha must NOT have TOP when topRetain is absent, flags=$flags"
    )
    assert(
      !flags.getOrElse("beta", true),
      s"beta must NOT have TOP when topRetain is absent, flags=$flags"
    )
  }
}
