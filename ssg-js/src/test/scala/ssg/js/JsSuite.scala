/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package js

final class JsSuite extends munit.FunSuite {

  test("ssg-js module loads") {
    assertEquals(Version, "0.1.0-SNAPSHOT")
  }
}
