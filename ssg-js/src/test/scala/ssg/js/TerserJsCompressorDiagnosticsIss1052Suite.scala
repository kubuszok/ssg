/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package js

/** ISS-1052: TerserJsCompressor.compress silently swallows all exceptions with no diagnostics channel — JS minification failures are invisible to the build.
  *
  * The fix adds an injectable `ssg.commons.Logger` parameter (defaulting to `Logger.quiet`) and surfaces the exception via `logger.warn(...)` before returning the original input (graceful degradation
  * preserved).
  *
  * Test approach: feed syntactically invalid JavaScript that makes `Terser.minifyToString` throw a `JsParseError`, inject a collecting `ssg.commons.Logger` test double, and verify that (a) the
  * diagnostic IS emitted via the logger, (b) the original input is returned (graceful degradation), and (c) the warning message contains the exception details.
  */
final class TerserJsCompressorDiagnosticsIss1052Suite extends munit.FunSuite {

  /** A Logger test double that collects all warnings into a mutable buffer. */
  final private class CollectingLogger extends ssg.commons.Logger {
    val warnings: scala.collection.mutable.ListBuffer[String] =
      scala.collection.mutable.ListBuffer.empty[String]

    override def warn(message: String): Unit =
      warnings += message
  }

  // Syntactically invalid JavaScript that will cause a JsParseError when
  // Terser.minifyToString tries to parse it.  The missing closing paren in
  // the parameter list triggers "Unexpected token" from the parser.
  private val invalidJs = "function f(a{}"

  // Valid JavaScript for the success-path test.
  private val validJs = "var x = 1 + 2;"

  test("ISS-1052: diagnostic emitted on JS compression failure") {
    val logger = new CollectingLogger
    val result = TerserJsCompressor.compress(invalidJs, logger)

    // (a) The diagnostic must have been emitted exactly once.
    assertEquals(
      logger.warnings.size,
      1,
      s"Expected exactly 1 warning, got ${logger.warnings.size}: ${logger.warnings.toList}"
    )

    // (b) Graceful degradation: original input returned.
    assertEquals(result, invalidJs)

    // (c) The warning message must include the subsystem prefix, exception
    // type, and the original exception message.
    val warning = logger.warnings.head
    assert(
      warning.contains("JS compression failed"),
      s"Expected 'JS compression failed' in warning, got: $warning"
    )
    assert(
      warning.contains("JsParseError"),
      s"Expected 'JsParseError' in warning, got: $warning"
    )
    assert(
      warning.contains("Using original source"),
      s"Expected 'Using original source' in warning, got: $warning"
    )
  }

  test("ISS-1052: graceful degradation returns input on failure (no logger)") {
    // Even without an explicit logger (default quiet), the input must be
    // returned on failure (backward compatibility).
    val result = TerserJsCompressor.compress(invalidJs)
    assertEquals(result, invalidJs)
  }

  test("ISS-1052: no diagnostic emitted on successful compression") {
    val logger = new CollectingLogger
    val result = TerserJsCompressor.compress(validJs, logger)

    // No warnings should be emitted on success.
    assertEquals(
      logger.warnings.size,
      0,
      s"Expected 0 warnings on success, got ${logger.warnings.size}: ${logger.warnings.toList}"
    )
    // The result should be minified (shorter than the input).
    assert(
      result.length <= validJs.length,
      s"Expected minified output to be at most as long as input, got: $result"
    )
  }

  test("ISS-1052: quiet logger (default) does not throw on failure") {
    // Verify the default (quiet) logger does not cause any issues when a
    // parse error occurs.
    val result = TerserJsCompressor.compress(invalidJs, ssg.commons.Logger.quiet)
    assertEquals(result, invalidJs)
  }
}
