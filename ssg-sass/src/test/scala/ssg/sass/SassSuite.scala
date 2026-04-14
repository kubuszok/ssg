/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

final class SassSuite extends munit.FunSuite {

  test("ssg-sass module loads") {
    assertEquals(LibVersion, "0.1.0-SNAPSHOT")
  }
}
