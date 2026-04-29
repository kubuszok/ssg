/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package misc
package test

import java.util.BitSet
import java.util.Objects

final class ArrayUtilsSuite extends munit.FunSuite {

  test("test_contained") {
    assert(ArrayUtils.contained[Object](Integer.valueOf(-339_763_186), Array[Object](Integer.valueOf(-339_763_186))))
    assert(ArrayUtils.contained(0, Array(0)))
    assert(!ArrayUtils.contained[Object](Integer.valueOf(-2_147_483_647), Array[Object]()))
    assert(!ArrayUtils.contained[Object](Integer.valueOf(-1_547_722_752), Array[Object](Integer.valueOf(-339_763_186))))
    assert(!ArrayUtils.contained(0, Array[Int]()))
    assert(!ArrayUtils.contained(0, Array(1)))
  }

  test("test_indexOf") {
    val ints: Array[Integer] = Array(
      1, // 0
      4, // 1
      1, // 2
      null, // 3
      3, // 4
      5, // 5
      null, // 6
      1, // 7
      2, // 8
      4, // 9
      3, // 10
      2, // 11
      0 // 12
    )

    assertEquals(ArrayUtils.indexOf(ints, (i: Integer) => i != null && i == 6), -1)
    assertEquals(ArrayUtils.indexOf(ints, (i: Integer) => i != null && i == 1), 0)
    assertEquals(ArrayUtils.indexOf(ints, 0, (i: Integer) => i != null && i == 1), 0)
    assertEquals(ArrayUtils.indexOf(ints, 1, (i: Integer) => i != null && i == 1), 2)
    assertEquals(ArrayUtils.indexOf(ints, 2, (i: Integer) => i != null && i == 1), 2)
    assertEquals(ArrayUtils.indexOf(ints, 2, (i: Integer) => i != null && i == 1), 2)
    assertEquals(ArrayUtils.indexOf(ints, 0, (i: Integer) => Objects.isNull(i)), 3)
    assertEquals(ArrayUtils.indexOf(ints, 0, (i: Integer) => i != null && i == 5), 5)
    assertEquals(ArrayUtils.indexOf(ints, 0, (i: Integer) => i != null && i == 0), 12)
  }

  test("test_firstOf") {
    val ints: Array[Integer] = Array(
      1, // 0
      4, // 1
      1, // 2
      null, // 3
      3, // 4
      5, // 5
      null, // 6
      1, // 7
      2, // 8
      4, // 9
      3, // 10
      2, // 11
      0 // 12
    )

    assert(ArrayUtils.firstOf(ints, (i: Integer) => i != null && i == 6).isEmpty)
    assertEquals(ArrayUtils.firstOf(ints, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.firstOf(ints, 0, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.firstOf(ints, 1, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.firstOf(ints, 2, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.firstOf(ints, 2, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    // Objects.isNull returns the null element itself, which is null
    assert(
      ArrayUtils.firstOf(ints, 0, (i: Integer) => Objects.isNull(i)).isEmpty || ArrayUtils.firstOf(ints, 0, (i: Integer) => Objects.isNull(i)).get == null
    )
    assertEquals(ArrayUtils.firstOf(ints, 0, (i: Integer) => i != null && i == 5).get, Integer.valueOf(5))
    assertEquals(ArrayUtils.firstOf(ints, 0, (i: Integer) => i != null && i == 0).get, Integer.valueOf(0))
  }

  test("test_lastIndexOf") {
    val ints: Array[Integer] = Array(
      1, // 0
      4, // 1
      1, // 2
      null, // 3
      3, // 4
      5, // 5
      null, // 6
      1, // 7
      2, // 8
      4, // 9
      3, // 10
      2, // 11
      0 // 12
    )

    assertEquals(ArrayUtils.lastIndexOf(ints, (i: Integer) => i != null && i == 6), -1)
    assertEquals(ArrayUtils.lastIndexOf(ints, (i: Integer) => i != null && i == 1), 7)
    assertEquals(ArrayUtils.lastIndexOf(ints, 7, (i: Integer) => i != null && i == 1), 7)
    assertEquals(ArrayUtils.lastIndexOf(ints, 6, (i: Integer) => i != null && i == 1), 2)
    assertEquals(ArrayUtils.lastIndexOf(ints, 3, (i: Integer) => i != null && i == 1), 2)
    assertEquals(ArrayUtils.lastIndexOf(ints, 2, (i: Integer) => i != null && i == 1), 2)
    assertEquals(ArrayUtils.lastIndexOf(ints, 1, (i: Integer) => i != null && i == 1), 0)
    assertEquals(ArrayUtils.lastIndexOf(ints, 1, 1, (i: Integer) => i != null && i == 1), -1)
    assertEquals(ArrayUtils.lastIndexOf(ints, 10, (i: Integer) => i != null && i == 1), 7)
    assertEquals(ArrayUtils.lastIndexOf(ints, 0, (i: Integer) => Objects.isNull(i)), -1)
    assertEquals(ArrayUtils.lastIndexOf(ints, 5, (i: Integer) => Objects.isNull(i)), 3)
    assertEquals(ArrayUtils.lastIndexOf(ints, 3, 5, (i: Integer) => Objects.isNull(i)), 3)
    assertEquals(ArrayUtils.lastIndexOf(ints, 4, 5, (i: Integer) => Objects.isNull(i)), -1)
    assertEquals(ArrayUtils.lastIndexOf(ints, 20, (i: Integer) => i != null && i == 5), 5)
    assertEquals(ArrayUtils.lastIndexOf(ints, 5, (i: Integer) => i != null && i == 5), 5)
    assertEquals(ArrayUtils.lastIndexOf(ints, 4, (i: Integer) => i != null && i == 5), -1)
    assertEquals(ArrayUtils.lastIndexOf(ints, 15, (i: Integer) => i != null && i == 0), 12)
    assertEquals(ArrayUtils.lastIndexOf(ints, 12, (i: Integer) => i != null && i == 0), 12)
    assertEquals(ArrayUtils.lastIndexOf(ints, 11, (i: Integer) => i != null && i == 0), -1)
  }

  test("test_lastOf") {
    val ints: Array[Integer] = Array(
      1, // 0
      4, // 1
      1, // 2
      null, // 3
      3, // 4
      5, // 5
      null, // 6
      1, // 7
      2, // 8
      4, // 9
      3, // 10
      2, // 11
      0 // 12
    )

    assert(ArrayUtils.lastOf(ints, (i: Integer) => i != null && i == 6).isEmpty)
    assertEquals(ArrayUtils.lastOf(ints, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.lastOf(ints, 7, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.lastOf(ints, 6, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.lastOf(ints, 3, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.lastOf(ints, 2, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assertEquals(ArrayUtils.lastOf(ints, 1, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    assert(ArrayUtils.lastOf(ints, 1, 1, (i: Integer) => i != null && i == 1).isEmpty)
    assertEquals(ArrayUtils.lastOf(ints, 10, (i: Integer) => i != null && i == 1).get, Integer.valueOf(1))
    // lastOf finds the element at index, which is null — Nullable wraps it as empty
    assert(
      ArrayUtils.lastOf(ints, 0, (i: Integer) => Objects.isNull(i)).isEmpty || ArrayUtils.lastOf(ints, 0, (i: Integer) => Objects.isNull(i)).get == null
    )
    assert(
      ArrayUtils.lastOf(ints, 5, (i: Integer) => Objects.isNull(i)).isEmpty || ArrayUtils.lastOf(ints, 5, (i: Integer) => Objects.isNull(i)).get == null
    )
    assert(
      ArrayUtils.lastOf(ints, 3, 5, (i: Integer) => Objects.isNull(i)).isEmpty || ArrayUtils.lastOf(ints, 3, 5, (i: Integer) => Objects.isNull(i)).get == null
    )
    assert(
      ArrayUtils.lastOf(ints, 4, 5, (i: Integer) => Objects.isNull(i)).isEmpty || ArrayUtils.lastOf(ints, 4, 5, (i: Integer) => Objects.isNull(i)).get == null
    )
    assertEquals(ArrayUtils.lastOf(ints, 20, (i: Integer) => i != null && i == 5).get, Integer.valueOf(5))
    assertEquals(ArrayUtils.lastOf(ints, 5, (i: Integer) => i != null && i == 5).get, Integer.valueOf(5))
    assert(ArrayUtils.lastOf(ints, 4, (i: Integer) => i != null && i == 5).isEmpty)
    assertEquals(ArrayUtils.lastOf(ints, 15, (i: Integer) => i != null && i == 0).get, Integer.valueOf(0))
    assertEquals(ArrayUtils.lastOf(ints, 12, (i: Integer) => i != null && i == 0).get, Integer.valueOf(0))
    assert(ArrayUtils.lastOf(ints, 11, (i: Integer) => i != null && i == 0).isEmpty)
  }

  test("test_toArrayBitSet") {
    val bitSet = new BitSet()

    assert(ArrayUtils.toArray(bitSet).sameElements(Array[Int]()))
    bitSet.set(0)
    assert(ArrayUtils.toArray(bitSet).sameElements(Array(0)))
    bitSet.set(5)
    assert(ArrayUtils.toArray(bitSet).sameElements(Array(0, 5)))
    bitSet.set(3)
    assert(ArrayUtils.toArray(bitSet).sameElements(Array(0, 3, 5)))
    bitSet.set(100)
    assert(ArrayUtils.toArray(bitSet).sameElements(Array(0, 3, 5, 100)))
  }
}
