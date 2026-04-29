/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/BitField.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/BitField.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package misc

trait BitField {
  def bits: Int // return number of bits for the field, must be > 0
}

/** Type class providing pre-computed bit masks and enum values for a given enum type, eliminating the need for JVM-only reflection APIs (getDeclaringClass, isEnum, getEnumConstants).
  *
  * Each enum type that is used with [[BitFieldSet]] must provide a given instance of this type class in its companion object.
  *
  * @tparam E
  *   the enum type
  */
trait EnumBitField[E <: java.lang.Enum[E]] {

  /** The enum element type class. */
  def elementType: Class[E]

  /** A short display name for the enum type (replaces Class.getSimpleName). */
  def typeName: String

  /** All enum values in ordinal order. */
  def values: Array[E]

  /** Pre-computed bit masks for each enum value. For simple enums, mask(i) = 1L << i. For BitField enums, masks are computed based on each field's bit width.
    */
  def bitMasks: Array[Long]
}

object EnumBitField {

  /** Compute bit masks for an array of enum values. Handles both simple enums (1 bit each) and BitField enums (variable bit widths).
    */
  def computeBitMasks[E <: java.lang.Enum[E]](universe: Array[E], typeName: String): Array[Long] =
    if (universe.length == 0) {
      new Array[Long](0)
    } else if (universe.length > 0 && universe(0).isInstanceOf[BitField]) {
      var bitCount = 0
      val masks    = new Array[Long](universe.length)
      for (e <- universe) {
        val bits = e.asInstanceOf[BitField].bits
        if (bits <= 0)
          throw new IllegalArgumentException(
            s"Enum bit field $typeName.${e.name()} bits must be >= 1, got: $bits"
          )
        if (bitCount + bits > 64)
          throw new IllegalArgumentException(
            s"Enum bit field $typeName.${e.name()} bits exceed available 64 bits by ${bitCount + bits - 64}"
          )
        masks(e.ordinal()) = BitFieldSet.nextBitMask(bitCount, bits)
        bitCount += bits
      }
      masks
    } else {
      if (universe.length <= 64) {
        val masks = new Array[Long](universe.length)
        for (e <- universe)
          masks(e.ordinal()) = 1L << e.ordinal()
        masks
      } else {
        throw new IllegalArgumentException("Enums with more than 64 values are not supported")
      }
    }
}
