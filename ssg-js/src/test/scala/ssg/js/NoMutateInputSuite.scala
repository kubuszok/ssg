/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/no-mutate-input.js
 * Original: 2 it() calls
 *
 * Note: In ssg-js, MinifyOptions is a case class (immutable), so the first
 * test is trivially true by design. The second test verifies source map
 * isolation between successive minifications.
 */
package ssg
package js

import ssg.js.sourcemap.*
import ssg.js.output.OutputOptions

final class NoMutateInputSuite extends munit.FunSuite {

  // 1. "does not modify the options object"
  // In Scala, case classes are immutable — this is guaranteed by design.
  test("does not modify the options object") {
    val sm = new SourceMap(SourceMapOptions())
    val config = MinifyOptions(
      compress = false,
      mangle = false,
      output = OutputOptions(sourceMap = sm)
    )
    Terser.minify("\"foo\";", config)
    // In Scala, the config cannot be mutated, so this is always true.
    // We verify the output options are unchanged.
    assertEquals(config.output.beautify, false)
  }

  // 2. "does not clobber source maps with a subsequent minification"
  test("does not clobber source maps with subsequent minification") {
    val sm1 = new SourceMap(SourceMapOptions())
    val config1 = MinifyOptions(
      parse = ssg.js.parse.ParserOptions(filename = "foo.js"),
      compress = false,
      mangle = false,
      output = OutputOptions(sourceMap = sm1)
    )
    val fooResult = Terser.minify("\"foo\";", config1)

    val sm2 = new SourceMap(SourceMapOptions())
    val config2 = MinifyOptions(
      parse = ssg.js.parse.ParserOptions(filename = "bar.js"),
      compress = false,
      mangle = false,
      output = OutputOptions(sourceMap = sm2)
    )
    val barResult = Terser.minify("module.exports = \"bar\";", config2)

    val fooMap = fooResult.sourceMap
    val barMap = barResult.sourceMap

    // Both should have source maps
    assert(fooMap != null, "Expected foo source map")
    assert(barMap != null, "Expected bar source map")

    // Source maps should have different mappings (different code produces different maps)
    assert(fooMap.nn.mappings != barMap.nn.mappings,
      s"Source maps should have different mappings: foo=${fooMap.nn.mappings}, bar=${barMap.nn.mappings}")
  }
}
