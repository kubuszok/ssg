/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-979 [R0610-P0]: Scala.js resource loading is repo-relative.
 *
 * PlatformResourcesImpl.scala (ssg-md/src/main/scalajs/ssg/md/util/misc/, lines 21-33)
 * resolves resources via Node `require("fs")` against paths relative to the current
 * working directory ("ssg-md/target/js-3/classes", "ssg-md/src/main/resources", ...).
 * Any consumer running outside this repository tree (published artifact, different
 * cwd, browser bundle) gets an empty lookup for every resource, and Html5Entities
 * (ssg-md/src/main/scala/ssg/md/util/sequence/Html5Entities.scala:85-86) throws
 * IllegalStateException("Could not load /ssg/md/util/sequence/entities.properties").
 *
 * This suite simulates an out-of-repo consumer by switching process.cwd() to the
 * OS temp directory before each test and restoring it afterwards. The fix (resources
 * embedded at build time) must make both tests pass regardless of cwd.
 *
 * Expected values (anti-cheat C11) are cited from the resource files themselves:
 * - ssg-md/src/main/resources/ssg/md/util/sequence/entities.properties:292 — "copy=©"
 * - original flexmark-java fixture, identical line:
 *   original-src/flexmark-java/flexmark-util-sequence/src/main/resources/com/vladsch/flexmark/util/sequence/entities.properties:292 — "copy=©"
 * - upstream behavior: on the JVM, Html5Entities.java:74 loads the entity table from
 *   the classpath via Html5Entities.class.getResourceAsStream(ENTITY_PATH), which
 *   works independently of the process working directory; the Scala.js port must
 *   provide the same guarantee.
 *
 * Note for the implementer (init-order caveat): Html5Entities is an object whose
 * NAMED_CHARACTER_REFERENCES val performs the resource lookup during object
 * initialization, i.e. on first access. Test (a) exercises the PlatformResources
 * lookup directly so the red reason is unambiguously the cwd-relative resolution;
 * test (b) then asserts the user-visible symptom. If another suite in the same JS
 * runtime touches Html5Entities before test (b) runs (with the repo-root cwd still
 * in effect), the object may already hold a populated table and test (b) alone
 * could pass — which is why test (a) is the primary red assertion. */
package ssg
package md
package util
package misc

import java.io.{ BufferedReader, InputStreamReader }
import java.nio.charset.StandardCharsets

import scala.scalajs.js

import ssg.md.util.sequence.Html5Entities

final class PlatformResourcesIss979JsSuite extends munit.FunSuite {

  private val ENTITY_PATH: String = "/ssg/md/util/sequence/entities.properties"

  private val process: js.Dynamic = js.Dynamic.global.process
  private val os:      js.Dynamic = js.Dynamic.global.require("os")

  /** Original working directory, captured before each test so it can ALWAYS be restored. */
  private var savedCwd: String = ""

  override def beforeEach(context: BeforeEach): Unit = {
    savedCwd = process.cwd().asInstanceOf[String]
    // Simulate a published-artifact consumer: run from a directory that is not the repo root.
    process.chdir(os.tmpdir().asInstanceOf[String])
  }

  override def afterEach(context: AfterEach): Unit =
    // Restore the original cwd unconditionally so other suites in the same JS runtime are unaffected.
    process.chdir(savedCwd)

  test("ISS-979 (a): PlatformResources resolves entities.properties when cwd is outside the repo tree") {
    val lookup = PlatformResources.getResourceAsStream(classOf[PlatformResourcesIss979JsSuite], ENTITY_PATH)
    assert(
      lookup.isDefined,
      "PlatformResources.getResourceAsStream(" + ENTITY_PATH + ") returned empty with cwd=" +
        process.cwd().asInstanceOf[String] +
        " — resource resolution must not depend on the repository working directory" +
        " (JVM equivalent: classpath lookup in flexmark Html5Entities.java:74 works from any cwd)"
    )

    val stream = lookup.getOrElse {
      fail("unreachable: lookup.isDefined was asserted above")
    }
    // Verify actual content, not just non-emptiness (anti-cheat C8): line 292 of
    // ssg-md/src/main/resources/ssg/md/util/sequence/entities.properties is "copy=©".
    val reader    = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
    var foundCopy = false
    var line      = reader.readLine()
    while (line != null) { // @nowarn — null check at Java interop boundary (readLine returns null at EOF)
      if (line == "copy=©") {
        foundCopy = true
      }
      line = reader.readLine()
    }
    assert(
      foundCopy,
      "entities.properties content is missing the entry \"copy=©\"" +
        " (expected per ssg-md/src/main/resources/ssg/md/util/sequence/entities.properties:292" +
        " and the identical flexmark-java fixture line 292)"
    )
  }

  test("ISS-979 (b): Html5Entities decodes &copy; when cwd is outside the repo tree") {
    // Html5Entities.scala:85-86 throws IllegalStateException("Could not load " + ENTITY_PATH)
    // when the platform resource lookup comes back empty — exactly what happens here while
    // the lookup is cwd-relative. Expected decoded value "©" per entities.properties:292
    // (same value in the flexmark-java original fixture, line 292).
    assertEquals(Html5Entities.entityToString("&copy;"), "©")
  }
}
