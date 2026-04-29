/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package util

final class BoxSuite extends munit.FunSuite {

  test("ModifiableBox holds mutable value") {
    val box = ModifiableBox(42)
    assertEquals(box.value, 42)
    box.value = 99
    assertEquals(box.value, 99)
  }

  test("Box reflects mutations in underlying ModifiableBox") {
    val mbox    = ModifiableBox("hello")
    val sealed1 = mbox.seal()
    assertEquals(sealed1.value, "hello")
    mbox.value = "world"
    assertEquals(sealed1.value, "world")
  }

  test("Box uses reference equality via inner ModifiableBox") {
    val mbox1 = ModifiableBox(1)
    val mbox2 = ModifiableBox(1)
    val box1a = mbox1.seal()
    val box1b = mbox1.seal()
    val box2  = mbox2.seal()

    assertEquals(box1a, box1b)
    assertNotEquals(box1a, box2)
  }

  test("ModifiableBox uses reference equality") {
    val mbox1 = ModifiableBox(1)
    val mbox2 = ModifiableBox(1)
    assertNotEquals(mbox1, mbox2)
    assertEquals(mbox1, mbox1)
  }
}
