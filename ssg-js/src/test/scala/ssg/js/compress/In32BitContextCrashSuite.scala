/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compressor.in32BitContext() crashes with IndexOutOfBoundsException when
 * compressing JS containing bitwise operators (e.g. `b|0`, `d&0xff`, `~f`)
 * under default options (compress + evaluate enabled).
 *
 * Root cause: in32BitContext reads the COMPRESSOR's own stack via self() and
 * parent() which is EMPTY during a compress pass (compression runs on a
 * separate TreeTransformer exposed via activeWalker). The sibling methods
 * inComputedKey and liveParent correctly walk the activeWalker's stack.
 *
 * Oracle: terser lib/compress/index.js:387-417 `in_32_bit_context`. In terser
 * the Compressor IS the TreeWalker so self()/parent() read the live ancestry.
 * The SSG port must use activeWalker to get the same behavior.
 *
 * Runs on JVM, JS, and Native (no java.nio dependency).
 */
package ssg
package js
package compress

final class In32BitContextCrashSuite extends munit.FunSuite {

  // -----------------------------------------------------------------------
  // The primary RED case: any JS with a bitwise op + default compress
  // (evaluate=true) triggers the crash. Without the fix, this throws
  // java.lang.IndexOutOfBoundsException: -1 is out of bounds.
  // -----------------------------------------------------------------------

  test("bitwise OR does not crash in32BitContext") {
    val out = Terser.minifyToString(
      "var a=b|0, c=(d&0xff)>>2, e=~f;",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
    // The output should contain bitwise operators (not all folded away since
    // b, d, f are free variables)
    assert(out.contains("|") || out.contains("&") || out.contains("~") || out.contains(">>"),
      s"output should retain bitwise ops for free variables: $out")
  }

  // -----------------------------------------------------------------------
  // Additional bitwise shapes that exercise in32BitContext walk-through
  // paths: the &&/||/?? right-side walk-through, ternary non-condition
  // walk-through, and sequence tail walk-through.
  // -----------------------------------------------------------------------

  test("bitwise OR with constant folding (0|0)") {
    // Oracle: terser folds `0|0` to `0` (evaluate + 32-bit context).
    val out = Terser.minifyToString(
      "var x = 0|0;",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
  }

  test("unary bitwise NOT does not crash") {
    val out = Terser.minifyToString(
      "var x = ~y;",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
    assert(out.contains("~"), s"output should retain ~ for free variable y: $out")
  }

  test("chained bitwise ops do not crash") {
    val out = Terser.minifyToString(
      "var x = a & b | c;",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
  }

  test("bitwise inside logical AND right operand does not crash") {
    // Exercises the walk-through path: && right side -> bitwise context
    val out = Terser.minifyToString(
      "var x = cond && (a | 0);",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
  }

  test("bitwise inside ternary consequence does not crash") {
    // Exercises the walk-through path: ternary non-condition branch
    val out = Terser.minifyToString(
      "var x = cond ? (a | 0) : b;",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
  }

  test("bitwise inside sequence tail does not crash") {
    // Exercises the walk-through path: sequence last expression
    val out = Terser.minifyToString(
      "var x = (1, 2, a | 0);",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
  }

  test("bitwise shift operators do not crash") {
    val out = Terser.minifyToString(
      "var x = a << 2, y = b >> 1, z = c >>> 3;",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
  }

  test("bitwise XOR does not crash") {
    val out = Terser.minifyToString(
      "var x = a ^ b;",
      MinifyOptions(compress = CompressorOptions(), mangle = false)
    )
    assert(out.nonEmpty, "output must be non-empty valid JS")
  }
}
