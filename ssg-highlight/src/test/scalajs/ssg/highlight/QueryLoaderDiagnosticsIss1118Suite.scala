/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

import scala.scalajs.js

/** ISS-1118: Verifies that the JS QueryLoaderPlatform emits a clear diagnostic to console.error when neither TREE_SITTER_QUERIES_DIR nor TREE_SITTER_WASM_DIR is set and a query file is not found --
  * instead of silently degrading to no-highlighting. Also verifies the at-most-once guard (no per-grammar spam) and that configured environments stay silent on missing grammars.
  */
final class QueryLoaderDiagnosticsIss1118Suite extends munit.FunSuite {

  test("ISS-1118: loadHighlightQuery emits diagnostic when env vars are unset and query is missing") {
    val env           = js.Dynamic.global.process.env
    val origQDir      = env.TREE_SITTER_QUERIES_DIR
    val origWDir      = env.TREE_SITTER_WASM_DIR
    val console       = js.Dynamic.global.console
    val originalError = console.error

    // Reset the at-most-once guard so this test is independent of execution order
    QueryLoaderPlatform.warnedMissingEnv = false

    val captured = scala.collection.mutable.ArrayBuffer.empty[String]
    val stub: js.Function1[js.Any, Unit] = (msg: js.Any) => captured += msg.toString

    try {
      // Unset both env vars (js.special.delete removes from process.env)
      js.special.delete(env, "TREE_SITTER_QUERIES_DIR")
      js.special.delete(env, "TREE_SITTER_WASM_DIR")

      // Replace console.error with a capturing function
      console.error = stub.asInstanceOf[js.Any]

      // Call loadHighlightQuery with a non-existent grammar
      val result = QueryLoaderPlatform.loadHighlightQuery("nonexistent_grammar_iss1118")

      // (a) Still returns None -- Option contract preserved (ISS-1096 QueryLoadFailed path)
      assertEquals(result, None)

      // (b) Diagnostic was emitted exactly once
      assertEquals(captured.size, 1, s"Expected exactly 1 diagnostic but got ${captured.size}")
      val msg = captured(0)
      assert(
        msg.contains("TREE_SITTER_QUERIES_DIR"),
        s"Diagnostic must name TREE_SITTER_QUERIES_DIR but got: $msg"
      )
      assert(
        msg.contains("TREE_SITTER_WASM_DIR"),
        s"Diagnostic must name TREE_SITTER_WASM_DIR but got: $msg"
      )
      assert(
        msg.contains("queries"),
        s"Diagnostic must mention the resolved cwd path but got: $msg"
      )

      // (c) At-most-once guard: second call must NOT emit another diagnostic
      val result2 = QueryLoaderPlatform.loadHighlightQuery("another_nonexistent_iss1118")
      assertEquals(result2, None)
      assertEquals(captured.size, 1, "At-most-once guard failed: diagnostic emitted more than once")
    } finally {
      // Restore console.error
      console.error = originalError

      // Restore env vars
      if (!js.isUndefined(origQDir)) {
        env.TREE_SITTER_QUERIES_DIR = origQDir
      }
      if (!js.isUndefined(origWDir)) {
        env.TREE_SITTER_WASM_DIR = origWDir
      }

      // Reset the at-most-once guard for subsequent test isolation
      QueryLoaderPlatform.warnedMissingEnv = false
    }
  }

  test("ISS-1118: loadHighlightQuery does NOT emit diagnostic when env vars are set") {
    // With TREE_SITTER_WASM_DIR set (normal test environment), a missing query
    // should return None silently -- no diagnostic, since the env IS configured
    // and this is a legitimate missing-grammar case.
    val console       = js.Dynamic.global.console
    val originalError = console.error

    QueryLoaderPlatform.warnedMissingEnv = false

    val captured = scala.collection.mutable.ArrayBuffer.empty[String]
    val stub: js.Function1[js.Any, Unit] = (msg: js.Any) => captured += msg.toString

    try {
      console.error = stub.asInstanceOf[js.Any]
      val result = QueryLoaderPlatform.loadHighlightQuery("nonexistent_grammar_envset_iss1118")
      assertEquals(result, None)
      assertEquals(captured.size, 0, "Diagnostic should NOT be emitted when env vars are set")
    } finally {
      console.error = originalError
      QueryLoaderPlatform.warnedMissingEnv = false
    }
  }
}
