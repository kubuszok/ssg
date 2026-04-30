/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package misc
package test

import java.util.ArrayList

// --- Test enums (must be in static scope for java.lang.Enum) ---

enum BitFieldsSuite_BitFields(val bits: Int) extends java.lang.Enum[BitFieldsSuite_BitFields] with BitField {
  case FIELD_0 extends BitFieldsSuite_BitFields(1)
  case FIELD_1 extends BitFieldsSuite_BitFields(1)
  case FIELD_2 extends BitFieldsSuite_BitFields(2)
  case FIELD_3 extends BitFieldsSuite_BitFields(3)
  case FIELD_4 extends BitFieldsSuite_BitFields(4)
  case FIELD_5 extends BitFieldsSuite_BitFields(5)
  case FIELD_6 extends BitFieldsSuite_BitFields(6)
  case FIELD_7 extends BitFieldsSuite_BitFields(7)
  case FIELD_8 extends BitFieldsSuite_BitFields(8)
  case FIELD_9 extends BitFieldsSuite_BitFields(9)
  case FIELD_10 extends BitFieldsSuite_BitFields(10)
  case FIELD_11 extends BitFieldsSuite_BitFields(4)
  case FIELD_12 extends BitFieldsSuite_BitFields(2)
  case FIELD_13 extends BitFieldsSuite_BitFields(1)
  case FIELD_14 extends BitFieldsSuite_BitFields(1)
}

object BitFieldsSuite_BitFields {
  given EnumBitField[BitFieldsSuite_BitFields] with {
    def elementType: Class[BitFieldsSuite_BitFields] = classOf[BitFieldsSuite_BitFields]
    def typeName:    String                          = "BitFields"
    def values:      Array[BitFieldsSuite_BitFields] = BitFieldsSuite_BitFields.values
    def bitMasks:    Array[Long]                     = EnumBitField.computeBitMasks(BitFieldsSuite_BitFields.values, "BitFields")
  }
}

enum BitFieldsSuite_BitFields2(val bits: Int) extends java.lang.Enum[BitFieldsSuite_BitFields2] with BitField {
  case FIELD_0 extends BitFieldsSuite_BitFields2(1)
  case FIELD_1 extends BitFieldsSuite_BitFields2(1)
  case FIELD_2 extends BitFieldsSuite_BitFields2(2)
  case FIELD_3 extends BitFieldsSuite_BitFields2(3)
  case FIELD_4 extends BitFieldsSuite_BitFields2(4)
  case FIELD_5 extends BitFieldsSuite_BitFields2(5)
  case FIELD_6 extends BitFieldsSuite_BitFields2(6)
  case FIELD_7 extends BitFieldsSuite_BitFields2(7)
  case FIELD_8 extends BitFieldsSuite_BitFields2(8)
  case FIELD_9 extends BitFieldsSuite_BitFields2(9)
  case FIELD_10 extends BitFieldsSuite_BitFields2(10)
  case FIELD_11 extends BitFieldsSuite_BitFields2(4)
  case FIELD_12 extends BitFieldsSuite_BitFields2(2)
  case FIELD_13 extends BitFieldsSuite_BitFields2(1)
  case FIELD_14 extends BitFieldsSuite_BitFields2(1)
  case FIELD_15 extends BitFieldsSuite_BitFields2(1)
}

object BitFieldsSuite_BitFields2 {
  given EnumBitField[BitFieldsSuite_BitFields2] with {
    def elementType: Class[BitFieldsSuite_BitFields2] = classOf[BitFieldsSuite_BitFields2]
    def typeName:    String                           = "BitFields2"
    def values:      Array[BitFieldsSuite_BitFields2] = BitFieldsSuite_BitFields2.values
    def bitMasks:    Array[Long]                      = EnumBitField.computeBitMasks(BitFieldsSuite_BitFields2.values, "BitFields2")
  }
}

enum BitFieldsSuite_IntSet extends java.lang.Enum[BitFieldsSuite_IntSet] {
  case VALUE_0, VALUE_1, VALUE_2, VALUE_3, VALUE_4, VALUE_5, VALUE_6, VALUE_7
  case VALUE_8, VALUE_9, VALUE_10, VALUE_11, VALUE_12, VALUE_13, VALUE_14, VALUE_15
  case VALUE_16, VALUE_17, VALUE_18, VALUE_19, VALUE_20, VALUE_21, VALUE_22, VALUE_23
  case VALUE_24, VALUE_25, VALUE_26, VALUE_27, VALUE_28, VALUE_29, VALUE_30, VALUE_31
}

object BitFieldsSuite_IntSet {
  given EnumBitField[BitFieldsSuite_IntSet] with {
    def elementType: Class[BitFieldsSuite_IntSet] = classOf[BitFieldsSuite_IntSet]
    def typeName:    String                       = "IntSet"
    def values:      Array[BitFieldsSuite_IntSet] = BitFieldsSuite_IntSet.values
    def bitMasks:    Array[Long]                  = EnumBitField.computeBitMasks(BitFieldsSuite_IntSet.values, "IntSet")
  }
}

enum BitFieldsSuite_OverIntSet extends java.lang.Enum[BitFieldsSuite_OverIntSet] {
  case VALUE_0, VALUE_1, VALUE_2, VALUE_3, VALUE_4, VALUE_5, VALUE_6, VALUE_7
  case VALUE_8, VALUE_9, VALUE_10, VALUE_11, VALUE_12, VALUE_13, VALUE_14, VALUE_15
  case VALUE_16, VALUE_17, VALUE_18, VALUE_19, VALUE_20, VALUE_21, VALUE_22, VALUE_23
  case VALUE_24, VALUE_25, VALUE_26, VALUE_27, VALUE_28, VALUE_29, VALUE_30, VALUE_31
  case VALUE_32
}

object BitFieldsSuite_OverIntSet {
  given EnumBitField[BitFieldsSuite_OverIntSet] with {
    def elementType: Class[BitFieldsSuite_OverIntSet] = classOf[BitFieldsSuite_OverIntSet]
    def typeName:    String                           = "OverIntSet"
    def values:      Array[BitFieldsSuite_OverIntSet] = BitFieldsSuite_OverIntSet.values
    def bitMasks:    Array[Long]                      = EnumBitField.computeBitMasks(BitFieldsSuite_OverIntSet.values, "OverIntSet")
  }
}

enum BitFieldsSuite_LongSet extends java.lang.Enum[BitFieldsSuite_LongSet] {
  case VALUE_0, VALUE_1, VALUE_2, VALUE_3, VALUE_4, VALUE_5, VALUE_6, VALUE_7
  case VALUE_8, VALUE_9, VALUE_10, VALUE_11, VALUE_12, VALUE_13, VALUE_14, VALUE_15
  case VALUE_16, VALUE_17, VALUE_18, VALUE_19, VALUE_20, VALUE_21, VALUE_22, VALUE_23
  case VALUE_24, VALUE_25, VALUE_26, VALUE_27, VALUE_28, VALUE_29, VALUE_30, VALUE_31
  case VALUE_32, VALUE_33, VALUE_34, VALUE_35, VALUE_36, VALUE_37, VALUE_38, VALUE_39
  case VALUE_40, VALUE_41, VALUE_42, VALUE_43, VALUE_44, VALUE_45, VALUE_46, VALUE_47
  case VALUE_48, VALUE_49, VALUE_50, VALUE_51, VALUE_52, VALUE_53, VALUE_54, VALUE_55
  case VALUE_56, VALUE_57, VALUE_58, VALUE_59, VALUE_60, VALUE_61, VALUE_62, VALUE_63
}

object BitFieldsSuite_LongSet {
  given EnumBitField[BitFieldsSuite_LongSet] with {
    def elementType: Class[BitFieldsSuite_LongSet] = classOf[BitFieldsSuite_LongSet]
    def typeName:    String                        = "LongSet"
    def values:      Array[BitFieldsSuite_LongSet] = BitFieldsSuite_LongSet.values
    def bitMasks:    Array[Long]                   = EnumBitField.computeBitMasks(BitFieldsSuite_LongSet.values, "LongSet")
  }
}

enum BitFieldsSuite_OverLongSet extends java.lang.Enum[BitFieldsSuite_OverLongSet] {
  case VALUE_0, VALUE_1, VALUE_2, VALUE_3, VALUE_4, VALUE_5, VALUE_6, VALUE_7
  case VALUE_8, VALUE_9, VALUE_10, VALUE_11, VALUE_12, VALUE_13, VALUE_14, VALUE_15
  case VALUE_16, VALUE_17, VALUE_18, VALUE_19, VALUE_20, VALUE_21, VALUE_22, VALUE_23
  case VALUE_24, VALUE_25, VALUE_26, VALUE_27, VALUE_28, VALUE_29, VALUE_30, VALUE_31
  case VALUE_32, VALUE_33, VALUE_34, VALUE_35, VALUE_36, VALUE_37, VALUE_38, VALUE_39
  case VALUE_40, VALUE_41, VALUE_42, VALUE_43, VALUE_44, VALUE_45, VALUE_46, VALUE_47
  case VALUE_48, VALUE_49, VALUE_50, VALUE_51, VALUE_52, VALUE_53, VALUE_54, VALUE_55
  case VALUE_56, VALUE_57, VALUE_58, VALUE_59, VALUE_60, VALUE_61, VALUE_62, VALUE_63
  case VALUE_64
}

object BitFieldsSuite_OverLongSet {
  given EnumBitField[BitFieldsSuite_OverLongSet] with {
    def elementType: Class[BitFieldsSuite_OverLongSet] = classOf[BitFieldsSuite_OverLongSet]
    def typeName:    String                            = "OverLongSet"
    def values:      Array[BitFieldsSuite_OverLongSet] = BitFieldsSuite_OverLongSet.values
    def bitMasks:    Array[Long]                       = EnumBitField.computeBitMasks(BitFieldsSuite_OverLongSet.values, "OverLongSet")
  }
}

// --- Test suite ---

final class BitFieldSetSuite extends munit.FunSuite {

  import BitFieldsSuite_BitFields as BitFields
  import BitFieldsSuite_BitFields2 as BitFields2
  import BitFieldsSuite_IntSet as IntSet
  import BitFieldsSuite_OverIntSet as OverIntSet
  import BitFieldsSuite_LongSet as LongSet
  import BitFieldsSuite_OverLongSet as OverLongSet

  test("test_bitField") {
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_0), 0x0000_0000_0000_0001L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_1), 0x0000_0000_0000_0002L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_2), 0x0000_0000_0000_000cL)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_3), 0x0000_0000_0000_0070L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_4), 0x0000_0000_0000_0780L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_5), 0x0000_0000_0000_f800L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_6), 0x0000_0000_003f_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_7), 0x0000_0000_1fc0_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_8), 0x0000_001f_e000_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_9), 0x0000_3fe0_0000_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_10), 0x00ff_c000_0000_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_11), 0x0f00_0000_0000_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_12), 0x3000_0000_0000_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_13), 0x4000_0000_0000_0000L)
    assertEquals(BitFieldSet.noneOf[BitFields].mask(BitFields.FIELD_14), 0x8000_0000_0000_0000L)

    val ex = intercept[IllegalArgumentException] {
      BitFieldSet.noneOf[BitFields2]
    }
    assert(ex.getMessage.contains("Enum bit field BitFields2.FIELD_15 bits exceed available 64 bits by 1"))
  }

  test("test_bitSetGet") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    for (fields <- BitFields.values) {
      val mask = (1 << fields.bits) - 1
      val iMin = -(1 << fields.bits / 2)
      val iMax = (1 << fields.bits / 2) - 1
      for (i <- iMin until iMax) {
        bitFields.setBitField(fields, i)
        assertEquals(bitFields.get(fields), (i & mask).toLong, s"field: ${fields.name()} value: $i")

        bitFields.setBitField(fields, i)
        assertEquals(bitFields.getLong(fields).toInt, i, s"field: ${fields.name()} value: $i")

        bitFields.setBitField(fields, i)
        assertEquals(bitFields.getInt(fields), i, s"field: ${fields.name()} value: $i")

        bitFields.setBitField(fields, i.toShort)
        assertEquals(bitFields.getShort(fields).toInt, i, s"field: ${fields.name()} value: $i")

        if (fields.bits <= 8) {
          bitFields.setBitField(fields, i.toByte)
          assertEquals(bitFields.getByte(fields).toInt, i, s"field: ${fields.name()} value: $i")
        }
      }
    }
  }

  test("test_BitFieldIterator") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    bitFields.add(BitFields.FIELD_1)
    bitFields.setBitField(BitFields.FIELD_3, 2)
    bitFields.setBitField(BitFields.FIELD_5, -10)
    bitFields.setBitField(BitFields.FIELD_10, 381)
    bitFields.setBitField(BitFields.FIELD_11, -6)
    val expected   = Array(-1, 2, -10, 381, -6)
    val actualList = new ArrayList[Integer]()

    val iter = bitFields.iterator()
    while (iter.hasNext) {
      val fields = iter.next()
      actualList.add(bitFields.getInt(fields))
    }

    val iMax   = actualList.size()
    val actual = new Array[Int](iMax)
    for (i <- 0 until iMax)
      actual(i) = actualList.get(i)

    assert(expected.sameElements(actual))
  }

  test("test_BitFieldToString") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    bitFields.add(BitFields.FIELD_1)
    bitFields.setBitField(BitFields.FIELD_3, 2)
    bitFields.setBitField(BitFields.FIELD_5, -10)
    bitFields.setBitField(BitFields.FIELD_10, 381)
    bitFields.setBitField(BitFields.FIELD_11, -6)
    assertEquals(bitFields.toString, "BitFields: { FIELD_1, FIELD_3(2), FIELD_5(-10), FIELD_10(381), FIELD_11(-6) }")
  }

  test("test_BitSetIterator") {
    val bitFields  = BitFieldSet.of(IntSet.VALUE_2, IntSet.VALUE_4, IntSet.VALUE_12, IntSet.VALUE_21)
    val expected   = Array(IntSet.VALUE_2, IntSet.VALUE_4, IntSet.VALUE_12, IntSet.VALUE_21)
    val actualList = new ArrayList[IntSet]()

    val iter = bitFields.iterator()
    while (iter.hasNext) {
      val fields = iter.next()
      actualList.add(fields)
    }

    assert(expected.sameElements(actualList.toArray))
  }

  test("test_BitSetIteratorRemove") {
    val bitFields = BitFieldSet.of(IntSet.VALUE_2, IntSet.VALUE_4, IntSet.VALUE_12, IntSet.VALUE_21, IntSet.VALUE_17)
    val expected  = Array(IntSet.VALUE_2, IntSet.VALUE_4, IntSet.VALUE_12, IntSet.VALUE_21)

    val iterator = bitFields.iterator()
    while (iterator.hasNext) {
      val fields = iterator.next()
      if (fields == IntSet.VALUE_17) {
        iterator.remove()
      }
    }

    assert(expected.sameElements(bitFields.toArray))
  }

  test("test_BitFieldIteratorRemove") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    bitFields.add(BitFields.FIELD_1)
    bitFields.setBitField(BitFields.FIELD_3, 2)
    bitFields.setBitField(BitFields.FIELD_5, -10)
    bitFields.setBitField(BitFields.FIELD_10, 381)
    bitFields.setBitField(BitFields.FIELD_11, -6)
    bitFields.setBitField(BitFields.FIELD_7, 57)

    val expected   = Array(-1, 2, -10, 381, -6)
    val actualList = new ArrayList[Integer]()

    val iterator = bitFields.iterator()
    while (iterator.hasNext) {
      val fields = iterator.next()
      if (fields == BitFields.FIELD_7) {
        iterator.remove()
      }
    }

    val iter2 = bitFields.iterator()
    while (iter2.hasNext) {
      val fields = iter2.next()
      actualList.add(bitFields.getInt(fields))
    }

    val iMax   = actualList.size()
    val actual = new Array[Int](iMax)
    for (i <- 0 until iMax)
      actual(i) = actualList.get(i)

    assert(expected.sameElements(actual))
  }

  test("test_bitSetGetErr") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    val ex        = intercept[IllegalArgumentException] {
      bitFields.setBitField(BitFields.FIELD_11, 16)
    }
    assert(ex.getMessage.contains("Enum field BitFields.FIELD_11 is 4 bits, value range is [-8, 7], cannot be set to 16"))
  }

  test("test_bitSetGetErr2") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    val ex        = intercept[IllegalArgumentException] {
      bitFields.setBitField(BitFields.FIELD_11, -9)
    }
    assert(ex.getMessage.contains("Enum field BitFields.FIELD_11 is 4 bits, value range is [-8, 7], cannot be set to -9"))
  }

  test("test_bitSetGetErr3") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    val ex        = intercept[IllegalArgumentException] {
      bitFields.setBitField(BitFields.FIELD_1, -2)
    }
    assert(ex.getMessage.contains("Enum field BitFields.FIELD_1 is 1 bit, value range is [-1, 0], cannot be set to -2"))
  }

  test("test_bitSetGetErr4") {
    val bitFields = BitFieldSet.noneOf[BitFields]
    val ex        = intercept[IllegalArgumentException] {
      bitFields.setBitField(BitFields.FIELD_1, 1)
    }
    assert(ex.getMessage.contains("Enum field BitFields.FIELD_1 is 1 bit, value range is [-1, 0], cannot be set to 1"))
  }

  test("test_toEnumSetLong") {
    assertEquals(BitFieldSet.noneOf[IntSet], BitFieldSet.of(classOf[IntSet], 0))
    for (i <- IntSet.values.indices)
      assertEquals(BitFieldSet.of(IntSet.values(i)), BitFieldSet.of(classOf[IntSet], 1L << i))

    assertEquals(BitFieldSet.noneOf[OverIntSet], BitFieldSet.of(classOf[OverIntSet], 0))
    for (i <- OverIntSet.values.indices)
      assertEquals(BitFieldSet.of(OverIntSet.values(i)), BitFieldSet.of(classOf[OverIntSet], 1L << i))

    assertEquals(BitFieldSet.noneOf[LongSet], BitFieldSet.of(classOf[LongSet], 0))
    for (i <- LongSet.values.indices)
      assertEquals(BitFieldSet.of(LongSet.values(i)), BitFieldSet.of(classOf[LongSet], 1L << i))

    intercept[IllegalArgumentException] {
      BitFieldSet.noneOf[OverLongSet]
    }
  }

  test("test_ToEnumSet") {
    assertEquals(BitFieldSet.noneOf[IntSet], BitFieldSet.of(classOf[IntSet], 0))
    for (i <- IntSet.values.indices)
      assertEquals(BitFieldSet.of(IntSet.values(i)), BitFieldSet.of(IntSet.values(i)))

    assertEquals(BitFieldSet.noneOf[OverIntSet], BitFieldSet.of(classOf[OverIntSet], 0))
    for (i <- OverIntSet.values.indices)
      assertEquals(BitFieldSet.of(OverIntSet.values(i)), BitFieldSet.of(OverIntSet.values(i)))

    assertEquals(BitFieldSet.noneOf[LongSet], BitFieldSet.of(classOf[LongSet], 0))
    for (i <- LongSet.values.indices)
      assertEquals(BitFieldSet.of(LongSet.values(i)), BitFieldSet.of(LongSet.values(i)))

    intercept[IllegalArgumentException] {
      BitFieldSet.noneOf[OverLongSet]
    }
  }

  test("test_toLong") {
    assertEquals(BitFieldSet.noneOf[IntSet], BitFieldSet.of(classOf[IntSet], 0))
    for (i <- IntSet.values.indices)
      assertEquals(BitFieldSet.of(IntSet.values(i)).toLong, 1L << i)

    assertEquals(BitFieldSet.noneOf[OverIntSet], BitFieldSet.of(classOf[OverIntSet], 0))
    for (i <- OverIntSet.values.indices)
      assertEquals(BitFieldSet.of(OverIntSet.values(i)).toLong, 1L << i)

    assertEquals(BitFieldSet.noneOf[LongSet], BitFieldSet.of(classOf[LongSet], 0))
    for (i <- LongSet.values.indices)
      assertEquals(BitFieldSet.of(LongSet.values(i)).toLong, 1L << i)

    intercept[IllegalArgumentException] {
      BitFieldSet.noneOf[OverLongSet]
    }
  }

  test("test_toInt") {
    assertEquals(BitFieldSet.noneOf[IntSet], BitFieldSet.of(classOf[IntSet], 0))
    for (i <- IntSet.values.indices)
      assertEquals(BitFieldSet.of(IntSet.values(i)).toInt, (1L << i).toInt)

    assertEquals(BitFieldSet.noneOf[OverIntSet], BitFieldSet.of(classOf[OverIntSet], 0))
    intercept[IllegalArgumentException] {
      BitFieldSet.of(OverIntSet.values(0)).toInt
    }
  }

  test("someNoneAll") {
    assertEquals(BitFieldSet.noneOf[IntSet], BitFieldSet.of(classOf[IntSet], 0))
    for (i <- IntSet.values.indices) {
      assertEquals(BitFieldSet.of(classOf[IntSet], 1L << i).mask(IntSet.values(i)), 1L << i)
      assert(BitFieldSet.of(classOf[IntSet], 1L << i).any(1L << i))
      assert(BitFieldSet.of(classOf[IntSet], 1L << i).any(IntSet.values(i)))
      assert(!BitFieldSet.of(classOf[IntSet], 1L << i).none(1L << i))
      assert(!BitFieldSet.of(classOf[IntSet], 1L << i).noneOf(IntSet.values*))
      assert(BitFieldSet.of(classOf[IntSet], 1L << i).all(IntSet.values(i)))
      assert(!BitFieldSet.of(classOf[IntSet], 1L << i).allOf(IntSet.values*))
    }

    assertEquals(BitFieldSet.noneOf[OverIntSet], BitFieldSet.of(classOf[OverIntSet], 0))
    for (i <- OverIntSet.values.indices) {
      assertEquals(BitFieldSet.of(classOf[OverIntSet], 1L << i).mask(OverIntSet.values(i)), 1L << i)
      assert(BitFieldSet.of(classOf[OverIntSet], 1L << i).any(1L << i))
      assert(BitFieldSet.of(classOf[OverIntSet], 1L << i).any(OverIntSet.values(i)))
      assert(!BitFieldSet.of(classOf[OverIntSet], 1L << i).none(1L << i))
      assert(!BitFieldSet.of(classOf[OverIntSet], 1L << i).noneOf(OverIntSet.values*))
      assert(BitFieldSet.of(classOf[OverIntSet], 1L << i).all(OverIntSet.values(i)))
      assert(!BitFieldSet.of(classOf[OverIntSet], 1L << i).allOf(OverIntSet.values*))
    }

    assertEquals(BitFieldSet.noneOf[LongSet], BitFieldSet.of(classOf[LongSet], 0))
    for (i <- LongSet.values.indices) {
      assertEquals(BitFieldSet.of(classOf[LongSet], 1L << i).mask(LongSet.values(i)), 1L << i)
      assert(BitFieldSet.of(classOf[LongSet], 1L << i).any(1L << i))
      assert(BitFieldSet.of(classOf[LongSet], 1L << i).any(LongSet.values(i)))
      assert(!BitFieldSet.of(classOf[LongSet], 1L << i).none(1L << i))
      assert(!BitFieldSet.of(classOf[LongSet], 1L << i).noneOf(LongSet.values*))
      assert(BitFieldSet.of(classOf[LongSet], 1L << i).all(LongSet.values(i)))
      assert(!BitFieldSet.of(classOf[LongSet], 1L << i).allOf(LongSet.values*))
    }
  }

  test("test_outsideUniverseInt") {
    intercept[IllegalArgumentException] {
      BitFieldSet.of(IntSet.values(31)).all(1L << 32)
    }
  }

  test("test_outsideUniverseOverInt") {
    intercept[IllegalArgumentException] {
      BitFieldSet.of(OverIntSet.values(32)).all(1L << 33)
    }
  }

  test("test_toString") {
    assertEquals(BitFieldSet.noneOf[IntSet].toString, "IntSet: { }")
    assertEquals(BitFieldSet.of(IntSet.VALUE_0).toString, "IntSet: { VALUE_0 }")
    assertEquals(BitFieldSet.of(IntSet.VALUE_0, IntSet.VALUE_2).toString, "IntSet: { VALUE_0, VALUE_2 }")
    assertEquals(BitFieldSet.of(IntSet.VALUE_0, IntSet.VALUE_31).toString, "IntSet: { VALUE_0, VALUE_31 }")
  }
}
