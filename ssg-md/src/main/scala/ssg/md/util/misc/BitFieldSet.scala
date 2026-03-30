/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/BitFieldSet.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package misc

import java.io.Serializable
import java.util.{ AbstractSet, Collection, Iterator, NoSuchElementException }
import java.util.concurrent.ConcurrentHashMap

/** Re-Implementation of RegularEnumSet class for EnumSet, for "regular sized" enum types (i.e., those with 64 or fewer enum constants)
  *
  * Modification allows access and manipulation of the bit mask for the elements so this class can be easily converted between long/int and BitFieldSet to use as efficient option flags in
  * implementation but convenient enum sets for manipulation.
  *
  * If the Enum implements [[BitField]] then each field can have 1..N bits up to a maximum total of 64 bits per enum. The class provides methods for setting and getting values from these fields as
  * long, int, short or byte values, either signed or unsigned.
  *
  * @author
  *   Vladimir Schneider
  * @author
  *   Josh Bloch
  * @since 1.5
  *
  * Migration notes:
  *   - Renames: com.vladsch.flexmark.util.misc -> ssg.md.util.misc
  *   - Convention: Java static methods -> companion object methods
  *   - Convention: Java inner classes -> Scala inner classes
  *   - Idiom: @SafeVarargs -> Scala varargs with Seq
  *   - Idiom: Java serialVersionUID -> @SerialVersionUID annotation
  *   - Idiom: getBits() -> bits (Scala property style)
  */
@SerialVersionUID(3411599620347842686L)
class BitFieldSet[E <: java.lang.Enum[E]] private (
  val elementType: Class[E],
  val universe:    Array[E],
  val bitMasks:    Array[Long]
) extends AbstractSet[E]
    with Cloneable
    with Serializable {

  /** Bit vector representation of this set. The 2^k bit indicates the presence of universe[k] in this set.
    */
  var elements: Long = 0L

  /** total number of bits used by all fields */
  val totalBits: Int = BitFieldSet.getTotalBits(bitMasks)

  // FIX: this for bit fields of more than 1 bit
  private[misc] def addRange(from: E, to: E): Unit =
    elements = (-1L >>> (from.ordinal() - to.ordinal() - 1)) << from.ordinal()

  private[misc] def addAll(): Unit =
    if (totalBits != 0)
      elements = -1L >>> -totalBits

  def complement(): Unit =
    if (totalBits != 0) {
      elements = ~elements
      elements &= -1L >>> -totalBits // Mask unused bits
    }

  def toLong: Long = elements

  def toInt: Int = {
    if (totalBits > 32)
      throw new IllegalArgumentException(
        s"Enum fields use $totalBits bits, which is more than 32 bits available in an int"
      )
    elements.toInt
  }

  def toShort: Short = {
    if (totalBits > 16)
      throw new IllegalArgumentException(
        s"Enum fields use $totalBits bits, which is more than 16 bits available in a short"
      )
    elements.toShort
  }

  def toByte: Byte = {
    if (totalBits > 8)
      throw new IllegalArgumentException(
        s"Enum fields use $totalBits bits, which is more than 8 bits available in a byte"
      )
    elements.toByte
  }

  def allBitsMask(): Long = -1L >>> -totalBits

  def orMask(mask: Long): Boolean = {
    val allValues = -1L >>> -totalBits
    if ((mask & ~allValues) != 0) {
      throw new IllegalArgumentException(
        s"bitMask $mask value contains elements outside the universe ${java.lang.Long.toBinaryString(mask & ~allValues)}"
      )
    }
    val oldElements = elements
    elements |= mask
    oldElements != elements
  }

  /** Set all bit fields to values in mask
    *
    * @param mask
    *   bit fields values
    * @return
    *   true if any field values were modified
    * @deprecated
    *   use [[setAll]]
    */
  @deprecated("use setAll", "0.1.0")
  def replaceAll(mask: Long): Boolean = setAll(mask)

  def setAll(mask: Long): Boolean = {
    val allValues = -1L >>> -totalBits
    if ((mask & ~allValues) != 0) {
      throw new IllegalArgumentException(
        s"mask $mask(0b${java.lang.Long.toBinaryString(mask)}) value contains elements outside the universe 0b${java.lang.Long.toBinaryString(mask & ~allValues)}"
      )
    }
    val oldElements = elements
    elements = mask
    oldElements != elements
  }

  override def toString: String =
    if (elements == 0) {
      elementType.getSimpleName + ": { }"
    } else {
      val out = new DelimitedBuilder(", ")
      out.append(elementType.getSimpleName).append(": { ")
      for (e <- universe)
        if (any(mask(e))) {
          out.append(e.name())
          e match {
            case bf: BitField if bf.bits > 1 =>
              out.append("(").append(getLong(e)).append(")")
            case _ =>
          }
          out.mark()
        }
      out.unmark().append(" }")
      out.toString
    }

  def andNotMask(mask: Long): Boolean = {
    val oldElements = elements
    elements &= ~mask
    oldElements != elements
  }

  def any(mask: Long): Boolean = (elements & mask) != 0

  def none(mask: Long): Boolean = (elements & mask) == 0

  def all(mask: Long): Boolean = {
    val allValues = -1L >>> -totalBits
    if ((mask & ~allValues) != 0) {
      throw new IllegalArgumentException(
        s"mask $mask(0b${java.lang.Long.toBinaryString(mask)}) value contains elements outside the universe 0b${java.lang.Long.toBinaryString(mask & ~allValues)}"
      )
    }
    (elements & mask) == mask
  }

  /** Returns unsigned value for the field, except if the field is 64 bits
    *
    * @param e1
    *   field to get
    * @return
    *   unsigned value
    */
  def get(e1: E): Long = {
    val bitMask = bitMasks(e1.ordinal())
    (elements & bitMask) >>> java.lang.Long.numberOfTrailingZeros(bitMask)
  }

  /** Set a signed value for the field
    *
    * @param e1
    *   field
    * @param value
    *   value to set
    * @return
    *   true if elements changed by operation
    */
  def setUnsigned(e1: E, value: Long): Boolean = {
    val oldElements = elements
    elements = BitFieldSet.setUnsigned(elementType, bitMasks, elements, e1, value)
    oldElements != elements
  }

  /** Set a signed value for the field
    *
    * @param e1
    *   field
    * @param value
    *   value to set
    * @return
    *   true if elements changed by operation
    */
  def setSigned(e1: E, value: Long): Boolean = {
    val oldElements = elements
    elements = BitFieldSet.setSigned(elementType, bitMasks, elements, e1, value)
    oldElements != elements
  }

  def setBitField(e1: E, value: Long): Unit = setSigned(e1, value)

  def setBitField(e1: E, value: Int): Unit = setSigned(e1, value.toLong)

  def setBitField(e1: E, value: Short): Unit = setSigned(e1, value.toLong)

  def setBitField(e1: E, value: Byte): Unit = setSigned(e1, value.toLong)

  def setUnsignedField(e1: E, value: Long): Unit = setUnsigned(e1, value)

  def setUnsignedField(e1: E, value: Int): Unit = setUnsigned(e1, value.toLong)

  def setUnsignedField(e1: E, value: Short): Unit = setUnsigned(e1, value.toLong)

  def setUnsignedField(e1: E, value: Byte): Unit = setUnsigned(e1, value.toLong)

  def getUnsigned(e1: E, maxBits: Int, typeName: String): Long =
    BitFieldSet.getUnsignedBitField(elements, e1, maxBits, typeName)

  def getSigned(e1: E, maxBits: Int, typeName: String): Long =
    BitFieldSet.getSignedBitField(elements, e1, maxBits, typeName)

  /** Returns signed value for the field, except if the field is 64 bits
    *
    * @param e1
    *   field to get
    * @return
    *   unsigned value
    */
  def getLong(e1: E): Long = getSigned(e1, 64, "long")

  def getInt(e1: E): Int = getSigned(e1, 32, "int").toInt

  def getShort(e1: E): Short = getSigned(e1, 16, "short").toShort

  def getByte(e1: E): Byte = getSigned(e1, 8, "byte").toByte

  def getUInt(e1: E): Int = getSigned(e1, 32, "int").toInt

  def getUShort(e1: E): Short = getSigned(e1, 16, "short").toShort

  def getUByte(e1: E): Byte = getSigned(e1, 8, "byte").toByte

  def mask(e1: E): Long = bitMasks(e1.ordinal())

  def mask(e1: E, e2: E): Long = bitMasks(e1.ordinal()) | bitMasks(e2.ordinal())

  def mask(e1: E, e2: E, e3: E): Long =
    bitMasks(e1.ordinal()) | bitMasks(e2.ordinal()) | bitMasks(e3.ordinal())

  def mask(e1: E, e2: E, e3: E, e4: E): Long =
    bitMasks(e1.ordinal()) | bitMasks(e2.ordinal()) | bitMasks(e3.ordinal()) | bitMasks(e4.ordinal())

  def mask(e1: E, e2: E, e3: E, e4: E, e5: E): Long =
    bitMasks(e1.ordinal()) | bitMasks(e2.ordinal()) | bitMasks(e3.ordinal()) | bitMasks(e4.ordinal()) | bitMasks(e5.ordinal())

  def mask(rest: E*): Long = {
    var m = 0L
    for (e <- rest)
      m |= bitMasks(e.ordinal())
    m
  }

  def add(e1: E, e2: E): Boolean = orMask(mask(e1, e2))

  def add(e1: E, e2: E, e3: E): Boolean = orMask(mask(e1, e2, e3))

  def add(e1: E, e2: E, e3: E, e4: E): Boolean = orMask(mask(e1, e2, e3, e4))

  def add(e1: E, e2: E, e3: E, e4: E, e5: E): Boolean = orMask(mask(e1, e2, e3, e4, e5))

  def addAll(rest: E*): Boolean = orMask(mask(rest*))

  def remove(e1: E, e2: E): Boolean = andNotMask(mask(e1, e2))

  def remove(e1: E, e2: E, e3: E): Boolean = andNotMask(mask(e1, e2, e3))

  def remove(e1: E, e2: E, e3: E, e4: E): Boolean = andNotMask(mask(e1, e2, e3, e4))

  def remove(e1: E, e2: E, e3: E, e4: E, e5: E): Boolean = andNotMask(mask(e1, e2, e3, e4, e5))

  def removeAll(rest: E*): Boolean = andNotMask(mask(rest*))

  def any(e1: E): Boolean = any(mask(e1))

  def any(e1: E, e2: E): Boolean = any(mask(e1, e2))

  def any(e1: E, e2: E, e3: E): Boolean = any(mask(e1, e2, e3))

  def any(e1: E, e2: E, e3: E, e4: E): Boolean = any(mask(e1, e2, e3, e4))

  def any(e1: E, e2: E, e3: E, e4: E, e5: E): Boolean = any(mask(e1, e2, e3, e4, e5))

  def anyOf(rest: E*): Boolean = any(mask(rest*))

  def all(e1: E): Boolean = all(mask(e1))

  def all(e1: E, e2: E): Boolean = all(mask(e1, e2))

  def all(e1: E, e2: E, e3: E): Boolean = all(mask(e1, e2, e3))

  def all(e1: E, e2: E, e3: E, e4: E): Boolean = all(mask(e1, e2, e3, e4))

  def all(e1: E, e2: E, e3: E, e4: E, e5: E): Boolean = all(mask(e1, e2, e3, e4, e5))

  def allOf(rest: E*): Boolean = all(mask(rest*))

  def none(e1: E): Boolean = none(mask(e1))

  def none(e1: E, e2: E): Boolean = none(mask(e1, e2))

  def none(e1: E, e2: E, e3: E): Boolean = none(mask(e1, e2, e3))

  def none(e1: E, e2: E, e3: E, e4: E): Boolean = none(mask(e1, e2, e3, e4))

  def none(e1: E, e2: E, e3: E, e4: E, e5: E): Boolean = none(mask(e1, e2, e3, e4, e5))

  def noneOf(rest: E*): Boolean = none(mask(rest*))

  /** Returns an iterator over the elements contained in this set. The iterator traverses the elements in their <i>natural order</i> (which is the order in which the enum constants are declared). The
    * returned Iterator is a "snapshot" iterator that will never throw ConcurrentModificationException; the elements are traversed as they existed when this call was invoked.
    *
    * NOTE: bit field iteration requires skipping fields whose bits are all 0 so constant time is violated
    *
    * @return
    *   an iterator over the elements contained in this set
    */
  override def iterator(): Iterator[E] =
    if (bitMasks.length == totalBits) new EnumBitSetIterator()
    else new EnumBitFieldIterator()

  private class EnumBitSetIterator extends Iterator[E] {

    /** A bit vector representing the elements in the set not yet returned by this iterator.
      */
    private var unseen: Long = elements

    /** The bit representing the last element returned by this iterator but not removed, or zero if no such element exists.
      */
    private var lastReturned: Long = 0

    override def hasNext: Boolean = unseen != 0

    override def next(): E = {
      if (unseen == 0)
        throw new NoSuchElementException()
      lastReturned = unseen & -unseen
      unseen -= lastReturned
      universe(java.lang.Long.numberOfTrailingZeros(lastReturned))
    }

    override def remove(): Unit = {
      if (lastReturned == 0)
        throw new IllegalStateException()
      elements &= ~lastReturned
      lastReturned = 0
    }
  }

  private class EnumBitFieldIterator extends Iterator[E] {
    private var nextIndex:         Int = -1
    private var lastReturnedIndex: Int = -1

    findNext()

    override def hasNext: Boolean = nextIndex < universe.length

    override def next(): E = {
      if (nextIndex >= universe.length)
        throw new NoSuchElementException()
      lastReturnedIndex = nextIndex
      findNext()
      universe(lastReturnedIndex)
    }

    private def findNext(): Unit = {
      var continue = true
      while (continue) {
        nextIndex += 1
        if (nextIndex >= universe.length) {
          continue = false
        } else if ((elements & bitMasks(nextIndex)) != 0) {
          continue = false
        }
      }
    }

    override def remove(): Unit = {
      if (lastReturnedIndex == -1)
        throw new IllegalStateException()
      elements &= ~bitMasks(lastReturnedIndex)
      lastReturnedIndex = -1
    }
  }

  /** Returns the number of elements in this set.
    *
    * @return
    *   the number of elements in this set
    */
  override def size(): Int = totalBits

  /** @return
    *   true if this set contains no elements
    */
  override def isEmpty: Boolean = elements == 0

  /** Returns true if this set contains the specified element.
    *
    * @param e
    *   element to be checked for containment in this collection
    * @return
    *   true if this set contains the specified element
    */
  override def contains(e: Any): Boolean =
    if (e == null) false
    else {
      val eClass = e.getClass
      if (eClass != elementType && eClass.getSuperclass != elementType) false
      else (elements & bitMasks(e.asInstanceOf[java.lang.Enum[?]].ordinal())) != 0
    }

  // Modification Operations

  /** Adds the specified element to this set if it is not already present.
    *
    * @param e
    *   element to be added to this set
    * @return
    *   true if the set changed as a result of the call
    * @throws NullPointerException
    *   if e is null
    */
  override def add(e: E): Boolean = {
    typeCheck(e)
    val oldElements = elements
    elements |= bitMasks(e.ordinal())
    elements != oldElements
  }

  /** Removes the specified element from this set if it is present.
    *
    * @param e
    *   element to be removed from this set, if present
    * @return
    *   true if the set contained the specified element
    */
  override def remove(e: Any): Boolean =
    if (e == null) false
    else {
      val eClass = e.getClass
      if (eClass != elementType && eClass.getSuperclass != elementType) false
      else {
        val oldElements = elements
        elements &= ~bitMasks(e.asInstanceOf[java.lang.Enum[?]].ordinal())
        elements != oldElements
      }
    }

  // Bulk Operations

  /** Returns true if this set contains all of the elements in the specified collection.
    *
    * @param c
    *   collection to be checked for containment in this set
    * @return
    *   true if this set contains all of the elements in the specified collection
    * @throws NullPointerException
    *   if the specified collection is null
    */
  override def containsAll(c: Collection[?]): Boolean =
    c match {
      case es: BitFieldSet[?] =>
        if (es.elementType != elementType) es.isEmpty
        else (es.elements & ~elements) == 0
      case _ => super.containsAll(c)
    }

  /** Adds all of the elements in the specified collection to this set.
    *
    * @param c
    *   collection whose elements are to be added to this set
    * @return
    *   true if this set changed as a result of the call
    * @throws NullPointerException
    *   if the specified collection or any of its elements are null
    */
  override def addAll(c: Collection[? <: E]): Boolean =
    c match {
      case es: BitFieldSet[?] =>
        if (es.elementType != elementType) {
          if (es.isEmpty) false
          else throw new ClassCastException(s"${es.elementType} != $elementType")
        } else {
          val oldElements = elements
          elements |= es.elements
          elements != oldElements
        }
      case _ => super.addAll(c)
    }

  /** Removes from this set all of its elements that are contained in the specified collection.
    *
    * @param c
    *   elements to be removed from this set
    * @return
    *   true if this set changed as a result of the call
    * @throws NullPointerException
    *   if the specified collection is null
    */
  override def removeAll(c: Collection[?]): Boolean =
    c match {
      case es: BitFieldSet[?] =>
        if (es.elementType != elementType) false
        else {
          val oldElements = elements
          elements &= ~es.elements
          elements != oldElements
        }
      case _ => super.removeAll(c)
    }

  /** Retains only the elements in this set that are contained in the specified collection.
    *
    * @param c
    *   elements to be retained in this set
    * @return
    *   true if this set changed as a result of the call
    * @throws NullPointerException
    *   if the specified collection is null
    */
  override def retainAll(c: Collection[?]): Boolean =
    c match {
      case es: BitFieldSet[?] =>
        if (es.elementType != elementType) {
          val changed = elements != 0
          elements = 0
          changed
        } else {
          val oldElements = elements
          elements &= es.elements
          elements != oldElements
        }
      case _ => super.retainAll(c)
    }

  /** Removes all of the elements from this set.
    */
  override def clear(): Unit =
    elements = 0

  /** Returns a copy of this set.
    *
    * @return
    *   a copy of this set
    */
  override def clone(): BitFieldSet[E] =
    try
      super.clone().asInstanceOf[BitFieldSet[E]]
    catch {
      case e: CloneNotSupportedException => throw new AssertionError(e)
    }

  /** Throws an exception if e is not of the correct type for this enum set. */
  final private[misc] def typeCheck(e: E): Unit = {
    val eClass = e.getClass
    if (eClass != elementType && eClass.getSuperclass != elementType)
      throw new ClassCastException(s"$eClass != $elementType")
  }

  private[misc] def writeReplace(): AnyRef = new BitFieldSet.SerializationProxy[E](this)

  // readObject method for the serialization proxy pattern
  // See Effective Java, Second Ed., Item 78.
  @throws[java.io.InvalidObjectException]
  private def readObject(stream: java.io.ObjectInputStream): Unit =
    throw new java.io.InvalidObjectException("Proxy required")

  /** Compares the specified object with this set for equality. Returns true if the given object is also a set, the two sets have the same size, and every member of the given set is contained in this
    * set.
    *
    * @param o
    *   object to be compared for equality with this set
    * @return
    *   true if the specified object is equal to this set
    */
  override def equals(o: Any): Boolean =
    o match {
      case es: BitFieldSet[?] =>
        if (es.elementType != elementType) elements == 0 && es.elements == 0
        else es.elements == elements
      case _ => super.equals(o)
    }
}

object BitFieldSet {

  // Cached universe arrays and bit masks
  private object UniverseLoader {
    @SuppressWarnings(Array("unchecked"))
    val enumUniverseMap: ConcurrentHashMap[Class[?], Array[java.lang.Enum[?]]] = new ConcurrentHashMap()
    val enumBitMasksMap: ConcurrentHashMap[Class[?], Array[Long]]              = new ConcurrentHashMap()

    def getUniverseSlow[E <: java.lang.Enum[E]](elementType: Class[E]): Array[java.lang.Enum[?]] = {
      assert(elementType.isEnum)
      val cached = enumUniverseMap.get(elementType)
      if (cached != null) cached // at Java interop boundary
      else {
        val constants = elementType.getEnumConstants
        val enums     = constants.length
        val result: Array[java.lang.Enum[?]] =
          if (enums > 0) {
            val arr = new Array[java.lang.Enum[?]](enums)
            var i   = 0
            for (c <- constants) {
              arr(i) = c
              i += 1
            }
            arr
          } else {
            ZeroLengthEnumArray
          }
        enumUniverseMap.put(elementType, result)
        result
      }
    }
  }

  private val ZeroLengthEnumArray: Array[java.lang.Enum[?]] = new Array[java.lang.Enum[?]](0)

  def nextBitMask(nextAvailableBit: Int, bits: Int): Long =
    (-1L >>> -bits) << nextAvailableBit.toLong

  /** Returns all of the values comprising E. The result is cloned and slower than SharedSecrets use but works in Java 11 and Java 8 because SharedSecrets are not shared publicly
    * @tparam E
    *   type of enum
    * @param elementType
    *   class of enum
    * @return
    *   array of enum values
    */
  def getUniverse[E <: java.lang.Enum[E]](elementType: Class[E]): Array[E] =
    UniverseLoader.getUniverseSlow(elementType).asInstanceOf[Array[E]]

  /** Returns all of the values comprising E. The result is cloned and slower than SharedSecrets use but works in Java 11 and Java 8 because SharedSecrets are not shared publicly
    *
    * @tparam E
    *   type of enum
    * @param elementType
    *   class of enum
    * @return
    *   array of bit masks for enum values
    */
  def getBitMasks[E <: java.lang.Enum[E]](elementType: Class[E]): Array[Long] = {
    val cached = UniverseLoader.enumBitMasksMap.get(elementType)
    if (cached != null) cached // at Java interop boundary
    else {
      // compute the bit masks for the enum
      val universe = UniverseLoader.getUniverseSlow(elementType).asInstanceOf[Array[E]]
      val bitMasks: Array[Long] =
        if (classOf[BitField].isAssignableFrom(elementType)) {
          var bitCount = 0
          val masks    = new Array[Long](universe.length)
          for (e <- universe) {
            val bits = e.asInstanceOf[BitField].bits
            if (bits <= 0)
              throw new IllegalArgumentException(
                s"Enum bit field ${elementType.getSimpleName}.${e.name()} bits must be >= 1, got: $bits"
              )
            if (bitCount + bits > 64)
              throw new IllegalArgumentException(
                s"Enum bit field ${elementType.getSimpleName}.${e.name()} bits exceed available 64 bits by ${bitCount + bits - 64}"
              )
            masks(e.ordinal()) = nextBitMask(bitCount, bits)
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
      UniverseLoader.enumBitMasksMap.put(elementType, bitMasks)
      bitMasks
    }
  }

  def getTotalBits(bitMasks: Array[Long]): Int =
    if (bitMasks.length == 0) 0
    else 64 - java.lang.Long.numberOfLeadingZeros(bitMasks(bitMasks.length - 1))

  def longMask[E <: java.lang.Enum[E]](e1: E): Long = {
    val bitMasks = getBitMasks(e1.getDeclaringClass)
    // if we are here then there is no overflow
    bitMasks(e1.ordinal())
  }

  def intMask[E <: java.lang.Enum[E]](e1: E): Int = {
    val bitMasks  = getBitMasks(e1.getDeclaringClass)
    val totalBits = getTotalBits(bitMasks)
    if (totalBits > 32)
      throw new IllegalArgumentException(
        s"Enum fields use $totalBits, which is more than 32 available in int"
      )
    bitMasks(e1.ordinal()).toInt
  }

  // Static bit manipulation methods

  def orMask(flags: Long, mask: Long): Long = flags | mask

  def andNotMask(flags: Long, mask: Long): Long = flags & ~mask

  def any(flags: Long, mask: Long): Boolean = (flags & mask) != 0

  def all(flags: Long, mask: Long): Boolean = (flags & mask) == mask

  def none(flags: Long, mask: Long): Boolean = (flags & mask) == 0

  /** Set a signed value for the field
    *
    * @param e1
    *   field
    * @param value
    *   value to set
    */
  private[misc] def setSigned[E <: java.lang.Enum[E]](elements: Long, e1: E, value: Long): Long = {
    val elementType = e1.getDeclaringClass
    val bitMasks    = getBitMasks(elementType)
    setSigned(elementType, bitMasks, elements, e1, value)
  }

  /** Set a signed value for the field
    *
    * @param e1
    *   field
    * @param value
    *   value to set
    */
  private[misc] def setSigned[E <: java.lang.Enum[E]](
    elementType: Class[E],
    bitMasks:    Array[Long],
    elements:    Long,
    e1:          E,
    value:       Long
  ): Long = {
    val bitMask   = bitMasks(e1.ordinal())
    val bitCount  = java.lang.Long.bitCount(bitMask)
    val halfValue = 1L << bitCount - 1

    if (bitCount < 64) {
      if (value < -halfValue || value > halfValue - 1)
        throw new IllegalArgumentException(
          s"Enum field ${elementType.getSimpleName}.${e1.name()} is $bitCount bit${if (bitCount > 1) "s" else ""}, value range is [${-halfValue}, ${halfValue - 1}], cannot be set to $value"
        )
    }

    val shiftedValue = value << java.lang.Long.numberOfTrailingZeros(bitMask)
    elements ^ ((elements ^ shiftedValue) & bitMask)
  }

  /** Set an unsigned value for the field
    *
    * @param e1
    *   field
    * @param value
    *   value to set
    */
  private[misc] def setUnsigned[E <: java.lang.Enum[E]](elements: Long, e1: E, value: Long): Long = {
    val elementType = e1.getDeclaringClass
    val bitMasks    = getBitMasks(elementType)
    setUnsigned(elementType, bitMasks, elements, e1, value)
  }

  /** Set an unsigned value for the field
    *
    * @param e1
    *   field
    * @param value
    *   value to set
    */
  private[misc] def setUnsigned[E <: java.lang.Enum[E]](
    elementType: Class[E],
    bitMasks:    Array[Long],
    elements:    Long,
    e1:          E,
    value:       Long
  ): Long = {
    val bitMask  = bitMasks(e1.ordinal())
    val bitCount = java.lang.Long.bitCount(bitMask)
    val maxValue = 1L << bitCount

    if (bitCount < 64) {
      if (!(value >= 0 && value < maxValue))
        throw new IllegalArgumentException(
          s"Enum field ${elementType.getSimpleName}.${e1.name()} is $bitCount bit${if (bitCount > 1) "s" else ""}, value range is [0, ${maxValue - 1}), cannot be set to $value"
        )
    }

    val shiftedValue = value << java.lang.Long.numberOfTrailingZeros(bitMask)
    elements ^ ((elements ^ shiftedValue) & bitMask)
  }

  def setBitField[E <: java.lang.Enum[E]](elements: Long, e1: E, value: Int): Long =
    setUnsigned(elements, e1, value.toLong)

  def setBitField[E <: java.lang.Enum[E]](elements: Int, e1: E, value: Int): Int =
    setUnsigned(elements.toLong, e1, value.toLong).toInt

  def setBitFieldShort[E <: java.lang.Enum[E]](elements: Short, e1: E, value: Short): Short =
    setUnsigned(elements.toLong, e1, value.toLong).toShort

  def setBitFieldByte[E <: java.lang.Enum[E]](elements: Byte, e1: E, value: Byte): Byte =
    setUnsigned(elements.toLong, e1, value.toLong).toByte

  /** Returns unsigned value for the field, except if the field is 64 bits
    *
    * @tparam E
    *   type of enum
    * @param elements
    *   bit mask for elements
    * @param e1
    *   field to get
    * @param maxBits
    *   maximum bits for type
    * @param typeName
    *   name of type
    * @return
    *   unsigned value
    */
  def getUnsignedBitField[E <: java.lang.Enum[E]](elements: Long, e1: E, maxBits: Int, typeName: String): Long = {
    val elementType = e1.getDeclaringClass
    val bitMasks    = getBitMasks(elementType)
    val bitMask     = bitMasks(e1.ordinal())
    val bitCount    = java.lang.Long.bitCount(bitMask)

    if (bitCount > maxBits)
      throw new IllegalArgumentException(
        s"Enum field ${elementType.getSimpleName}.${e1.name()} uses $bitCount, which is more than $maxBits available in $typeName"
      )

    (elements & bitMask) >>> java.lang.Long.numberOfTrailingZeros(bitMask)
  }

  private[misc] def getSignedBitField[E <: java.lang.Enum[E]](elements: Long, e1: E, maxBits: Int, typeName: String): Long = {
    val elementType = e1.getDeclaringClass
    val bitMasks    = getBitMasks(elementType)
    val bitMask     = bitMasks(e1.ordinal())
    val bitCount    = java.lang.Long.bitCount(bitMask)

    if (bitCount > maxBits)
      throw new IllegalArgumentException(
        s"Enum field ${elementType.getSimpleName}.${e1.name()} uses $bitCount, which is more than $maxBits available in $typeName"
      )

    elements << java.lang.Long.numberOfLeadingZeros(bitMask) >> 64 - bitCount
  }

  /** Returns signed value for the field, except if the field is 64 bits
    *
    * @tparam E
    *   type of enum
    * @param elements
    *   bit mask for elements
    * @param e1
    *   field to get
    * @return
    *   unsigned value
    */
  def getBitField[E <: java.lang.Enum[E]](elements: Long, e1: E): Long =
    getUnsignedBitField(elements, e1, 64, "long")

  def getBitField[E <: java.lang.Enum[E]](elements: Int, e1: E): Int =
    getUnsignedBitField(elements.toLong, e1, 32, "int").toInt

  def getBitFieldShort[E <: java.lang.Enum[E]](elements: Short, e1: E): Short =
    getUnsignedBitField(elements.toLong, e1, 16, "short").toShort

  def getBitFieldByte[E <: java.lang.Enum[E]](elements: Byte, e1: E): Byte =
    getUnsignedBitField(elements.toLong, e1, 8, "byte").toByte

  // Factory methods

  /** Creates an empty enum set with the specified element type.
    *
    * @tparam E
    *   The class of the elements in the set
    * @param elementType
    *   the class object of the element type for this enum set
    * @return
    *   An empty enum set of the specified type.
    * @throws NullPointerException
    *   if elementType is null
    */
  def noneOf[E <: java.lang.Enum[E]](elementType: Class[E]): BitFieldSet[E] = {
    if (!elementType.isEnum)
      throw new ClassCastException(s"$elementType not an enum")

    val universe = getUniverse(elementType)
    new BitFieldSet[E](elementType, universe, getBitMasks(elementType))
  }

  /** Creates an enum set containing all of the elements in the specified element type.
    *
    * @tparam E
    *   The class of the elements in the set
    * @param elementType
    *   the class object of the element type for this enum set
    * @return
    *   An enum set containing all the elements in the specified type.
    * @throws NullPointerException
    *   if elementType is null
    */
  def allOf[E <: java.lang.Enum[E]](elementType: Class[E]): BitFieldSet[E] = {
    val result = noneOf(elementType)
    result.addAll()
    result
  }

  /** Creates an enum set with the same element type as the specified enum set, initially containing the same elements (if any).
    *
    * @tparam E
    *   The class of the elements in the set
    * @param s
    *   the enum set from which to initialize this enum set
    * @return
    *   A copy of the specified enum set.
    * @throws NullPointerException
    *   if s is null
    */
  def copyOf[E <: java.lang.Enum[E]](s: BitFieldSet[E]): BitFieldSet[E] = s.clone()

  /** Creates an enum set initialized from the specified collection. If the specified collection is an BitFieldSet instance, this static factory method behaves identically to [[copyOf(BitFieldSet)]].
    * Otherwise, the specified collection must contain at least one element (in order to determine the new enum set's element type).
    *
    * @tparam E
    *   The class of the elements in the collection
    * @param c
    *   the collection from which to initialize this enum set
    * @return
    *   An enum set initialized from the given collection.
    * @throws IllegalArgumentException
    *   if c is not an BitFieldSet instance and contains no elements
    * @throws NullPointerException
    *   if c is null
    */
  def copyOf[E <: java.lang.Enum[E]](c: Collection[E]): BitFieldSet[E] =
    c match {
      case bfs: BitFieldSet[E @unchecked] => bfs.clone()
      case _ =>
        if (c.isEmpty)
          throw new IllegalArgumentException("Collection is empty")
        val i      = c.iterator()
        val first  = i.next()
        val result = BitFieldSet.of(first)
        while (i.hasNext) result.add(i.next())
        result
    }

  /** Creates an enum set with the same element type as the specified enum set, initially containing all the elements of this type that are <i>not</i> contained in the specified set.
    *
    * @tparam E
    *   The class of the elements in the enum set
    * @param s
    *   the enum set from whose complement to initialize this enum set
    * @return
    *   The complement of the specified set in this set
    * @throws NullPointerException
    *   if s is null
    */
  def complementOf[E <: java.lang.Enum[E]](s: BitFieldSet[E]): BitFieldSet[E] = {
    val result = copyOf(s)
    result.complement()
    result
  }

  /** Create a bit enum set from a bit mask
    *
    * @param enumClass
    *   class of the enum
    * @param mask
    *   bit mask for items
    * @tparam T
    *   enum type
    * @return
    *   bit enum set
    */
  def of[T <: java.lang.Enum[T]](enumClass: Class[T], mask: Long): BitFieldSet[T] = {
    val optionSet = BitFieldSet.noneOf(enumClass)
    optionSet.orMask(mask)
    optionSet
  }

  /** Creates an enum set initially containing the specified element.
    *
    * @tparam E
    *   The class of the specified element and of the set
    * @param e
    *   the element that this set is to contain initially
    * @return
    *   an enum set initially containing the specified element
    * @throws NullPointerException
    *   if e is null
    */
  def of[E <: java.lang.Enum[E]](e: E): BitFieldSet[E] = {
    val result = noneOf(e.getDeclaringClass)
    result.add(e)
    result
  }

  /** Creates an enum set initially containing the specified elements.
    *
    * @tparam E
    *   The class of the parameter elements and of the set
    * @param e1
    *   an element that this set is to contain initially
    * @param e2
    *   another element that this set is to contain initially
    * @return
    *   an enum set initially containing the specified elements
    * @throws NullPointerException
    *   if any parameters are null
    */
  def of[E <: java.lang.Enum[E]](e1: E, e2: E): BitFieldSet[E] = {
    val result = noneOf(e1.getDeclaringClass)
    result.add(e1)
    result.add(e2)
    result
  }

  /** Creates an enum set initially containing the specified elements.
    *
    * @tparam E
    *   The class of the parameter elements and of the set
    * @param e1
    *   an element that this set is to contain initially
    * @param e2
    *   another element that this set is to contain initially
    * @param e3
    *   another element that this set is to contain initially
    * @return
    *   an enum set initially containing the specified elements
    * @throws NullPointerException
    *   if any parameters are null
    */
  def of[E <: java.lang.Enum[E]](e1: E, e2: E, e3: E): BitFieldSet[E] = {
    val result = noneOf(e1.getDeclaringClass)
    result.add(e1)
    result.add(e2)
    result.add(e3)
    result
  }

  /** Creates an enum set initially containing the specified elements.
    *
    * @tparam E
    *   The class of the parameter elements and of the set
    * @param e1
    *   an element that this set is to contain initially
    * @param e2
    *   another element that this set is to contain initially
    * @param e3
    *   another element that this set is to contain initially
    * @param e4
    *   another element that this set is to contain initially
    * @return
    *   an enum set initially containing the specified elements
    * @throws NullPointerException
    *   if any parameters are null
    */
  def of[E <: java.lang.Enum[E]](e1: E, e2: E, e3: E, e4: E): BitFieldSet[E] = {
    val result = noneOf(e1.getDeclaringClass)
    result.add(e1)
    result.add(e2)
    result.add(e3)
    result.add(e4)
    result
  }

  /** Creates an enum set initially containing the specified elements.
    *
    * @tparam E
    *   The class of the parameter elements and of the set
    * @param e1
    *   an element that this set is to contain initially
    * @param e2
    *   another element that this set is to contain initially
    * @param e3
    *   another element that this set is to contain initially
    * @param e4
    *   another element that this set is to contain initially
    * @param e5
    *   another element that this set is to contain initially
    * @return
    *   an enum set initially containing the specified elements
    * @throws NullPointerException
    *   if any parameters are null
    */
  def of[E <: java.lang.Enum[E]](e1: E, e2: E, e3: E, e4: E, e5: E): BitFieldSet[E] = {
    val result = noneOf(e1.getDeclaringClass)
    result.add(e1)
    result.add(e2)
    result.add(e3)
    result.add(e4)
    result.add(e5)
    result
  }

  /** Creates an enum set initially containing the specified elements. This factory, whose parameter list uses the varargs feature, may be used to create an enum set initially containing an arbitrary
    * number of elements, but it is likely to run slower than the overloads that do not use varargs.
    *
    * @tparam E
    *   The class of the parameter elements and of the set
    * @param first
    *   an element that the set is to contain initially
    * @param rest
    *   the remaining elements the set is to contain initially
    * @return
    *   an enum set initially containing the specified elements
    * @throws NullPointerException
    *   if any of the specified elements are null, or if rest is null
    */
  def of[E <: java.lang.Enum[E]](first: E, rest: E*): BitFieldSet[E] = {
    val result = noneOf(first.getDeclaringClass)
    result.add(first)
    for (e <- rest) result.add(e)
    result
  }

  /** Creates an enum set initially containing the specified elements.
    *
    * @tparam E
    *   The class of the parameter elements and of the set
    * @param declaringClass
    *   declaring class of enum
    * @param rest
    *   the elements the set is to contain initially
    * @return
    *   an enum set initially containing the specified elements
    * @throws NullPointerException
    *   if any of the specified elements are null, or if rest is null
    */
  def of[E <: java.lang.Enum[E]](declaringClass: Class[E], rest: Array[E]): BitFieldSet[E] = {
    val result = noneOf(declaringClass)
    for (e <- rest) result.add(e)
    result
  }

  /** Creates an enum set initially containing all of the elements in the range defined by the two specified endpoints. The returned set will contain the endpoints themselves, which may be identical
    * but must not be out of order.
    *
    * @tparam E
    *   The class of the parameter elements and of the set
    * @param from
    *   the first element in the range
    * @param to
    *   the last element in the range
    * @return
    *   an enum set initially containing all of the elements in the range defined by the two specified endpoints
    * @throws NullPointerException
    *   if from or to are null
    * @throws IllegalArgumentException
    *   if from.compareTo(to) > 0
    */
  def range[E <: java.lang.Enum[E]](from: E, to: E): BitFieldSet[E] = {
    if (from.compareTo(to) > 0)
      throw new IllegalArgumentException(s"$from > $to")
    val result = noneOf(from.getDeclaringClass)
    result.addRange(from, to)
    result
  }

  /** This class is used to serialize all BitFieldSet instances, regardless of implementation type. It captures their "logical contents" and they are reconstructed using public static factories. This
    * is necessary to ensure that the existence of a particular implementation type is an implementation detail.
    */
  @SerialVersionUID(362491234563181265L)
  private class SerializationProxy[E <: java.lang.Enum[E]](set: BitFieldSet[E]) extends Serializable {

    /** The element type of this enum set. */
    private val elementType: Class[E] = set.elementType

    /** The bit mask for elements contained in this enum set. */
    private val bits: Long = set.elements

    private def readResolve(): AnyRef = {
      val result = BitFieldSet.noneOf(elementType)
      result.orMask(bits)
      result
    }
  }
}
