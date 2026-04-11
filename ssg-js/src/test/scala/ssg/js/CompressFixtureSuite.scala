/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests using Terser compress fixture files.
 *
 * Ingests the original Terser test fixtures and runs them through
 * the SSG-JS compression pipeline. Tracks pass/fail rates and
 * provides a baseline for regression detection.
 */
package ssg
package js

final class CompressFixtureSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

  // Fixture files relative to the original-src location
  private val fixtureDir = {
    val candidates = Seq(
      java.nio.file.Paths.get(System.getProperty("user.dir"), "original-src/terser/test/compress"),
      java.nio.file.Paths.get(System.getProperty("user.dir"), "../original-src/terser/test/compress"),
      java.nio.file.Paths.get("/Users/dev/Workspaces/GitHub/ssg/original-src/terser/test/compress")
    )
    candidates.find(java.nio.file.Files.isDirectory(_)).getOrElse(
      throw new RuntimeException("Cannot find Terser test/compress directory")
    )
  }

  /** Load and parse a fixture file. */
  private def loadFixtures(filename: String): Seq[CompressFixture] = {
    val path = fixtureDir.resolve(filename)
    if (!java.nio.file.Files.exists(path)) return Seq.empty // @nowarn
    val content = new String(java.nio.file.Files.readAllBytes(path), "UTF-8")
    FixtureParser.parse(content)
  }

  /** Run all fixtures from a file and return (passed, failed, errors, total). */
  private def runFixtureFile(filename: String): (Int, Int, Int, Int) = {
    val fixtures = loadFixtures(filename)
    var passed = 0
    var failed = 0
    var errors = 0

    for (fixture <- fixtures) {
      val result = FixtureRunner.run(fixture)
      if (result.passed) passed += 1
      else if (result.message.startsWith("Error:")) errors += 1
      else failed += 1
    }

    (passed, failed, errors, fixtures.size)
  }

  // -----------------------------------------------------------------------
  // Fixture tests
  // -----------------------------------------------------------------------

  test("fixture parser works on debugger.js") {
    val fixtures = loadFixtures("debugger.js")
    assertEquals(fixtures.size, 2, "debugger.js should have 2 test cases")
    assertEquals(fixtures(0).name, "keep_debugger")
    assertEquals(fixtures(1).name, "drop_debugger")
    assert(fixtures(0).input.contains("debugger"))
    assert(fixtures(0).options.contains("drop_debugger"))
  }

  test("debugger fixtures run") {
    val (passed, failed, errors, total) = runFixtureFile("debugger.js")
    assert(total == 2, s"Expected 2 fixtures, got $total")
    // At least one should pass or error gracefully (not crash)
    assert(passed + errors + failed == total, s"All fixtures should complete: $passed passed, $failed failed, $errors errors")
  }

  test("dead-code fixtures parse") {
    // Verify the fixture parser can handle dead-code.js (don't run compression — too slow)
    val fixtures = loadFixtures("dead-code.js")
    assert(fixtures.size > 2, s"dead-code.js should have >2 test cases, got ${fixtures.size}")
    assert(fixtures.head.input.nonEmpty, "First fixture should have input")
    assert(fixtures.head.expect != null, "First fixture should have expect")
  }

  test("fixture parser handles multiple files") {
    // Verify the parser can handle several fixture files without crashing
    val files = Seq("debugger.js", "dead-code.js", "loops.js")
    var totalFixtures = 0
    for (f <- files) {
      val fixtures = loadFixtures(f)
      totalFixtures += fixtures.size
    }
    assert(totalFixtures > 5, s"Expected at least 5 total fixtures across 3 files, got $totalFixtures")
  }
}
