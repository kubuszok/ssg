/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package md

final class MdSuite extends munit.FunSuite {

  test("ssg-md module loads") {
    assertEquals(Version, "0.1.0-SNAPSHOT")
  }
}
