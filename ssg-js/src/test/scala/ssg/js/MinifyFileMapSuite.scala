/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/minify-file-map.js
 * Original: 3 it() calls
 *
 * Note: These tests exercise the multi-file input API (passing a map of
 * filename→code or an array of strings). ssg-js currently only accepts
 * a single string. These tests are marked .fail until multi-file input
 * is supported.
 */
package ssg
package js

final class MinifyFileMapSuite extends munit.FunSuite {

  // 1. "Should accept object"
  test("should accept object (multi-file input)".fail) {
    // Requires multi-file input API: minify({ "/scripts/foo.js": "..." })
    fail("Multi-file input API not yet supported")
  }

  // 2. "Should accept array of strings"
  test("should accept array of strings".fail) {
    // Requires array input API: minify(["...", "..."])
    fail("Array input API not yet supported")
  }

  // 3. "Should correctly include source"
  test("should correctly include source".fail) {
    // Requires multi-file input + sourceMap.includeSources
    fail("Multi-file input API not yet supported")
  }
}
