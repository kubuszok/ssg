/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Runs a CompressFixture through the Terser compression pipeline
 * and compares the output against the expected result.
 */
package ssg
package js

import ssg.js.parse.{ Parser, ParserOptions }
import ssg.js.output.{ OutputOptions, OutputStream }
import ssg.js.scope.ScopeAnalysis
import ssg.js.compress.{ Compressor, CompressorOptions, ToplevelConfig }

/** Result of running a single fixture. */
final case class FixtureResult(
  passed:  Boolean,
  actual:  String,
  message: String
)

/** Runs compress fixtures through the minification pipeline. */
object FixtureRunner {

  /** Run a single fixture.
    *
    * Note: Previously used CompletableFuture with timeout, but that requires
    * threading support which isn't available on Scala Native. Running
    * synchronously is simpler and works cross-platform.
    */
  def run(fixture: CompressFixture): FixtureResult =
    try runUnsafe(fixture)
    catch {
      case e: Exception =>
        FixtureResult(passed = false, actual = "", message = s"Error: ${e.getMessage}")
    }

  private def runUnsafe(fixture: CompressFixture): FixtureResult =
    try {
      // 1. Parse input
      val parser = new Parser(ParserOptions())
      val ast    = parser.parse(fixture.input)

      // 2. Build compressor options from fixture options (force single pass to avoid hangs)
      val compressorOpts = buildOptions(fixture.options).copy(passes = 1)

      // 3. Run scope analysis + compression
      ScopeAnalysis.figureOutScope(ast)
      val compressor = new Compressor(compressorOpts)
      val compressed = compressor.compress(ast)

      // 4. Output
      val out = new OutputStream(OutputOptions(beautify = false))
      out.printNode(compressed)
      val actual = out.get()

      // 5. Compare
      if (fixture.expectExact != null) {
        val expected = fixture.expectExact.nn
        if (actual == expected) {
          FixtureResult(passed = true, actual = actual, message = "OK (exact)")
        } else {
          FixtureResult(passed = false, actual = actual, message = s"Expected exact: $expected")
        }
      } else if (fixture.expect != null) {
        // Parse + output the expected code to normalize formatting
        val expectedAst = parser.parse(fixture.expect.nn)
        val expectedOut = new OutputStream(OutputOptions(beautify = false))
        expectedOut.printNode(expectedAst)
        val expected = expectedOut.get()

        if (actual == expected) {
          FixtureResult(passed = true, actual = actual, message = "OK")
        } else {
          FixtureResult(passed = false, actual = actual, message = s"Expected: $expected")
        }
      } else {
        // No expected output — just check it doesn't crash
        FixtureResult(passed = true, actual = actual, message = "OK (no expected)")
      }
    } catch {
      case e: Exception =>
        FixtureResult(passed = false, actual = "", message = s"Error: ${e.getMessage}")
    }

  /** Build CompressorOptions from a fixture's options map. */
  private def buildOptions(opts: Map[String, Any]): CompressorOptions = {
    var co = CompressorOptions()
    for ((key, value) <- opts)
      key match {
        case "booleans"      => co = co.copy(booleans = asBool(value))
        case "collapse_vars" => co = co.copy(collapseVars = asBool(value))
        case "comparisons"   => co = co.copy(comparisons = asBool(value))
        case "conditionals"  => co = co.copy(conditionals = asBool(value))
        case "dead_code"     => co = co.copy(deadCode = asBool(value))
        case "drop_debugger" => co = co.copy(dropDebugger = asBool(value))
        case "evaluate"      => co = co.copy(evaluate = asBool(value))
        case "hoist_funs"    => co = co.copy(hoistFuns = asBool(value))
        case "hoist_vars"    => co = co.copy(hoistVars = asBool(value))
        case "if_return"     => co = co.copy(ifReturn = asBool(value))
        case "join_vars"     => co = co.copy(joinVars = asBool(value))
        case "keep_fargs"    => co = co.copy(keepFargs = asBool(value))
        case "keep_fnames"   => co = co.copy(keepFnames = asBool(value))
        case "loops"         => co = co.copy(loops = asBool(value))
        case "negate_iife"   => co = co.copy(negateIife = asBool(value))
        case "passes"        => co = co.copy(passes = asInt(value))
        case "properties"    => co = co.copy(properties = asBool(value))
        case "pure_getters"  => co = co.copy(pureGetters = if (asBool(value)) "strict" else "")
        case "reduce_funcs"  => co = co.copy(reduceFuncs = asBool(value))
        case "reduce_vars"   => co = co.copy(reduceVars = asBool(value))
        case "sequences"     => co = co.copy(sequencesLimit = if (asBool(value)) 200 else 0)
        case "side_effects"  => co = co.copy(sideEffects = asBool(value))
        case "switches"      => co = co.copy(switches = asBool(value))
        case "toplevel"      =>
          val b = asBool(value)
          co = co.copy(toplevel = ToplevelConfig(funcs = b, vars = b))
        case "unused"   => co = co.copy(unused = asBool(value))
        case "defaults" => // defaults=false means all options off — not supported in fixture runner
        case _          => // ignore unknown options
      }
    co
  }

  private def asBool(v: Any): Boolean =
    v match {
      case b: Boolean => b
      case _ => false
    }

  private def asInt(v: Any): Int =
    v match {
      case d: Double => d.toInt
      case i: Int    => i
      case _ => 1
    }
}
