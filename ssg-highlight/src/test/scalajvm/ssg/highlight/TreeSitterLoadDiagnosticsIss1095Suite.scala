/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** ISS-1095: Verifies that a native-library load failure produces a clear,
  * actionable `IllegalStateException` that names the library and chains the
  * original cause, instead of surfacing as a buried `ExceptionInInitializerError`.
  *
  * The `loadLibrary` seam on `TreeSitterPlatformImpl` (JVM) accepts a custom
  * loader function, allowing us to simulate a load failure without requiring
  * the real native library to be absent.
  *
  * Proof-of-red: without the `loadLibrary` wrapper, calling the loader directly
  * would propagate a raw `UnsatisfiedLinkError("boom")` whose message does NOT
  * contain "tree_sitter_all" -- the assertion on the message content would fail.
  */
final class TreeSitterLoadDiagnosticsIss1095Suite extends munit.FunSuite {

  test("ISS-1095: loadLibrary wraps load failure in actionable IllegalStateException with chained cause") {
    val cause = new UnsatisfiedLinkError("boom")
    val caught = intercept[IllegalStateException] {
      TreeSitterPlatformImpl.loadLibrary("tree_sitter_all", _ => throw cause)
    }
    assert(
      caught.getMessage.contains("tree_sitter_all"),
      s"Expected message to name the library 'tree_sitter_all' but got: ${caught.getMessage}"
    )
    assert(
      caught.getMessage.contains("java.library.path"),
      s"Expected message to mention java.library.path but got: ${caught.getMessage}"
    )
    assert(
      caught.getCause eq cause,
      s"Expected chained cause to be the original UnsatisfiedLinkError but got: ${caught.getCause}"
    )
    assertEquals(caught.getCause.getMessage, "boom")
  }

  test("ISS-1095: loadLibrary message includes host architecture and OS") {
    val caught = intercept[IllegalStateException] {
      TreeSitterPlatformImpl.loadLibrary("tree_sitter_all", _ => throw new UnsatisfiedLinkError("missing"))
    }
    assert(
      caught.getMessage.contains(System.getProperty("os.arch")),
      s"Expected message to include os.arch but got: ${caught.getMessage}"
    )
    assert(
      caught.getMessage.contains(System.getProperty("os.name")),
      s"Expected message to include os.name but got: ${caught.getMessage}"
    )
  }
}
