/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/minify-file-map.js
 * Original: 3 it() calls
 *
 * Multi-file input was ported under ISS-1045 (Terser.minifyFiles /
 * Terser.minifySeq). The first two tests now exercise the real API; the
 * source-map assertions of the originals (result.map / map.sources /
 * sourcesContent) are split out — source-map orchestration remains tracked
 * under ISS-1219, so the includeSources test stays .fail.
 */
package ssg
package js

final class MinifyFileMapSuite extends munit.FunSuite {

  // 1. "Should accept object" (code half — map.sources / map.file ⇒ ISS-1219)
  test("should accept object (multi-file input)") {
    val jsMap  = scala.collection.immutable.ListMap("/scripts/foo.js" -> "var foo = {\"x\": 1, y: 2, 'z': 3};")
    val result = Terser.minifyFiles(jsMap, MinifyOptions.NoOptimize)
    assertEquals(result.code, "var foo={x:1,y:2,z:3};")
  }

  // 2. "Should accept array of strings" (code half — map.sources ['0','1'] ⇒ ISS-1219)
  // Upstream uses the default pipeline (compress on), which join_vars-merges the
  // two files' `var` statements ⇒ "var foo={x:1,y:2,z:3},bar=15;".
  test("should accept array of strings") {
    val jsSeq  = Seq("var foo = {\"x\": 1, y: 2, 'z': 3};", "var bar = 15;")
    val result = Terser.minifySeq(jsSeq, MinifyOptions.Defaults)
    assertEquals(result.code, "var foo={x:1,y:2,z:3},bar=15;")
  }

  // 3. "Should correctly include source" — needs sourceMap.includeSources
  //    (sourcesContent). Source-map orchestration: ISS-1219.
  test("should correctly include source".fail) {
    // Requires sourceMap.includeSources — source-map orchestration: ISS-1219
    fail("sourceMap.includeSources — source-map orchestration: ISS-1219")
  }
}
