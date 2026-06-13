/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

/** ISS-994: `Compile.compile(path)` must throw a clear runtime error that directs the caller to the real entry point (`CompileFile.compile` on JVM).
  *
  * This test pins the fail-fast behavior so the method cannot silently become a no-op, and verifies the exception message is truthful (names the actual alternative: `CompileFile.compile`).
  */
final class CompileCompileHonestIss994Suite extends munit.FunSuite {

  test("ISS-994: Compile.compile throws loudly and points to CompileFile.compile") {
    val ex = intercept[RuntimeException] {
      Compile.compile("any.scss")
    }
    // The message must name the real alternative so callers know where to go.
    assert(
      ex.getMessage.contains("CompileFile.compile"),
      s"expected message to name CompileFile.compile, got: ${ex.getMessage}"
    )
  }

  test("ISS-994: Compile.compile error message mentions filesystem access") {
    val ex = intercept[RuntimeException] {
      Compile.compile("test.sass")
    }
    assert(
      ex.getMessage.contains("filesystem"),
      s"expected message to mention filesystem access, got: ${ex.getMessage}"
    )
  }
}
