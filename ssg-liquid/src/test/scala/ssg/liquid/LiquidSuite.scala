/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

final class LiquidSuite extends munit.FunSuite {

  test("ssg-liquid module loads") {
    assertEquals(Version, "0.1.0-SNAPSHOT")
  }
}
