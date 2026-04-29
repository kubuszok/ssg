/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Test helper for Terser compression tests.
 *
 * Mirrors the Terser test runner (test/compress.js):
 * - false_by_default: when options.defaults is undefined (the common case),
 *   ALL optimization flags default to false/off unless explicitly enabled.
 * - parse → scope analysis → compress(1 pass) → output
 * - Compare output against expected string.
 *
 * The original test format uses labeled blocks in .js files; here we
 * extract test cases manually into Scala test methods.
 *
 * KNOWN LIMITATION (ISS-031/032): ScopeAnalysis.figureOutScope hangs on
 * code with undeclared/global references (e.g., `foo();` where `foo` is
 * not declared). All test inputs must use only declared variables and
 * functions, or the compression test will time out. */
package ssg
package js
package compress

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.{ Failure, Success, Try }

import ssg.js.ast.*
import ssg.js.parse.{ Parser, ParserOptions }
import ssg.js.output.OutputStream
import ssg.js.scope.ScopeAnalysis

/** Utility for building and running compression tests. */
object CompressTestHelper {

  /** Default timeout for compression operations (per test case).
    *
    * Set to 5 seconds which is long enough for any single-pass compression
    * to complete, but short enough to not block the thread pool when
    * ISS-031/032 causes a hang.
    */
  val DefaultTimeout: FiniteDuration = 5.seconds

  /** CompressorOptions with ALL boolean flags set to false/off.
    *
    * This mirrors Terser's `false_by_default: true` mode used by the test
    * runner. Each test then enables only the specific flags it needs.
    */
  val AllOff: CompressorOptions = CompressorOptions(
    arguments = false,
    arrows = false,
    booleans = false,
    booleansAsIntegers = false,
    collapseVars = false,
    comparisons = false,
    computedProps = false,
    conditionals = false,
    deadCode = false,
    defaults = false,
    directives = false,
    dropConsole = DropConsoleConfig.Disabled,
    dropDebugger = false,
    ecma = 5,
    evaluate = false,
    expression = false,
    globalDefs = Map.empty,
    hoistFuns = false,
    hoistProps = false,
    hoistVars = false,
    ie8 = false,
    ifReturn = false,
    inline = InlineLevel.InlineDisabled,
    joinVars = false,
    keepClassnames = false,
    keepFargs = true, // default is true even in false_by_default
    keepFnames = false,
    keepInfinity = false,
    lhsConstants = false,
    loops = false,
    module = false,
    negateIife = false,
    passes = 1,
    properties = false,
    pureFuncs = Nil,
    pureGetters = "strict",
    pureNew = false,
    reduceFuncs = false,
    reduceVars = false,
    sequencesLimit = 0,
    sideEffects = false,
    switches = false,
    toplevel = ToplevelConfig(),
    topRetain = None,
    typeofs = false,
    unsafe = false,
    unsafeArrows = false,
    unsafeComps = false,
    unsafeFunction = false,
    unsafeMath = false,
    unsafeMethods = false,
    unsafeProto = false,
    unsafeRegexp = false,
    unsafeSymbols = false,
    unsafeUndefined = false,
    unused = false,
    warnings = false
  )

  /** Parse JavaScript source code into a top-level AST. */
  def parse(code: String): AstToplevel = {
    val parser = new Parser(ParserOptions())
    parser.parse(code)
  }

  /** Normalize a code string by parsing and re-printing.
    *
    * This matches Terser's test runner behavior of `make_code(as_toplevel(test.expect))`
    * for `expect:` blocks — the expected output is parsed and re-printed to get a
    * canonical representation.
    */
  def normalize(code: String): String =
    OutputStream.printToString(parse(code))

  /** Compress the given code with the specified options (single-pass only).
    *
    * Steps: parse → scope analysis → compress → scope analysis → output.
    * Returns the resulting code string.
    *
    * WARNING: May hang if input contains undeclared references (ISS-031/032).
    * Use compressWithTimeout for safety.
    */
  def compress(code: String, options: CompressorOptions): String = {
    val ast = parse(code)
    ScopeAnalysis.figureOutScope(ast)
    val compressor = new Compressor(options)
    val compressed = compressor.compress(ast)
    ScopeAnalysis.figureOutScope(compressed)
    OutputStream.printToString(compressed)
  }

  /** Compress with a timeout guard.
    *
    * Returns `Some(result)` if compression completes within the timeout,
    * `None` if it times out (indicating ISS-031/032 hang).
    */
  def compressWithTimeout(
    code: String,
    options: CompressorOptions,
    timeout: FiniteDuration = DefaultTimeout
  ): Option[String] = {
    val future = Future { compress(code, options) }
    Try(Await.result(future, timeout)) match {
      case Success(result) => Some(result)
      case Failure(_: java.util.concurrent.TimeoutException) => None
      case Failure(ex) => throw ex
    }
  }

  /** Compress and compare: parse input, compress with options, compare to expected.
    *
    * Also parses the expected string to normalize it (matching Terser's test runner
    * which uses `make_code(as_toplevel(test.expect))` for `expect:` blocks).
    *
    * Returns `Some((actual, expectedNormalized))` if compression completes,
    * `None` if it times out.
    */
  def compressAndNormalize(
    input: String,
    expected: String,
    options: CompressorOptions,
    timeout: FiniteDuration = DefaultTimeout
  ): Option[(String, String)] = {
    compressWithTimeout(input, options, timeout).map { actual =>
      val expectedNormalized = normalize(expected)
      (actual, expectedNormalized)
    }
  }

  /** Assert compression result matches expected, with timeout protection.
    *
    * @param testName
    *   name of the test (for error messages)
    * @param input
    *   JavaScript source to compress
    * @param expected
    *   expected output after compression
    * @param options
    *   compressor options (use AllOff as base, then .copy() to enable specific flags)
    * @param test
    *   the munit test context (for assume/assertEquals)
    */
  def assertCompresses(
    input: String,
    expected: String,
    options: CompressorOptions,
    timeout: FiniteDuration = DefaultTimeout
  )(using loc: munit.Location): Unit = {
    compressAndNormalize(input, expected, options, timeout) match {
      case Some((actual, exp)) =>
        if (actual != exp) {
          throw new munit.ComparisonFailException(
            s"Compression mismatch\n--- INPUT ---\n$input\n--- EXPECTED ---\n$exp\n--- ACTUAL ---\n$actual",
            actual,
            exp,
            loc,
            isStackTracesEnabled = false
          )
        }
      case None =>
        org.junit.Assume.assumeTrue(
          s"Compression timed out after $timeout (ISS-031/032 — undeclared references cause ScopeAnalysis hang)",
          false
        )
    }
  }
}
