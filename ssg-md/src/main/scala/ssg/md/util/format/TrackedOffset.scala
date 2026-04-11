/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TrackedOffset.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.misc.{ BitFieldSet, EnumBitField }

/** Tracked Offset information <p> NOTE: purposefully equals compares the offset only and will equal an integer of the same value to allow use of TrackedOffset as a key but lookup to be done by offset
  */
final class TrackedOffset private (
  private val original: Nullable[TrackedOffset],
  val offset:           Int,
  private val flags:    Int
) extends Comparable[TrackedOffset] {

  var spacesBefore:   Int     = -1
  var spacesAfter:    Int     = -1
  var isSpliced:      Boolean = false // spaces reset to 0
  private var _index: Int     = -1

  private def this(offset: Int, afterSpaceEdit: Boolean, afterInsert: Boolean, afterDelete: Boolean) =
    this(
      Nullable(null),
      offset, {
        var f = 0
        if (afterSpaceEdit) f |= TrackedOffset.F_AFTER_SPACE_EDIT
        if (afterInsert) f |= TrackedOffset.F_AFTER_INSERT
        if (afterDelete) f |= TrackedOffset.F_AFTER_DELETE
        f
      }
    )

  private def this(other: TrackedOffset) = {
    this(other.original, other.offset, other.flags)
    this.spacesBefore = other.spacesBefore
    this.spacesAfter = other.spacesAfter
  }

  private def this(other: TrackedOffset, offset: Int) = {
    this(Nullable(other), offset, other.flags)
    this.spacesBefore = other.spacesBefore
    this.spacesAfter = other.spacesAfter
  }

  def isResolved: Boolean =
    _index != -1

  def getIndex: Int =
    if (_index == -1) offset else _index

  def setIndex(index: Int): Unit = {
    if (this.original.isDefined) this.original.get._index = index
    this._index = index
  }

  def isAfterSpaceEdit: Boolean =
    BitFieldSet.any(flags, TrackedOffset.F_AFTER_SPACE_EDIT)

  def isAfterInsert: Boolean =
    BitFieldSet.any(flags, TrackedOffset.F_AFTER_INSERT)

  def isAfterDelete: Boolean =
    BitFieldSet.any(flags, TrackedOffset.F_AFTER_DELETE)

  def plusOffsetDelta(delta: Int): TrackedOffset =
    new TrackedOffset(this, offset + delta)

  def withOffset(offset: Int): TrackedOffset =
    new TrackedOffset(this, offset)

  override def compareTo(o: TrackedOffset): Int =
    Integer.compare(offset, o.offset)

  def compareTo(o: Integer): Int =
    Integer.compare(offset, o.intValue())

  def compareTo(offset: Int): Int =
    Integer.compare(this.offset, offset)

  override def equals(o: Any): Boolean =
    if (this eq o.asInstanceOf[AnyRef]) {
      true
    } else {
      o match {
        case i: Integer       => i.intValue() == offset
        case t: TrackedOffset => this.offset == t.offset
        case _ => false
      }
    }

  override def hashCode: Int =
    offset

  override def toString: String =
    "{" + offset +
      (if (isSpliced) " ><" else "") +
      (if (spacesBefore >= 0 || spacesAfter >= 0) " " + (if (spacesBefore >= 0) Integer.toString(spacesBefore) else "?") + "|" + (if (spacesAfter >= 0) Integer.toString(spacesAfter) else "?")
       else "") +
      (if (BitFieldSet.any(flags, TrackedOffset.F_AFTER_SPACE_EDIT | TrackedOffset.F_AFTER_INSERT | TrackedOffset.F_AFTER_DELETE))
         " " + (if (isAfterSpaceEdit) "s" else "") + (if (isAfterInsert) "i" else "") + (if (isAfterDelete) "d" else "")
       else "") +
      (if (isResolved) " -> " + _index else "") +
      "}"
}

object TrackedOffset {

  private enum Flags extends java.lang.Enum[Flags] {
    case AFTER_SPACE_EDIT
    case AFTER_INSERT
    case AFTER_DELETE
  }

  private given EnumBitField[Flags] with {
    def elementType: Class[Flags] = classOf[Flags]
    def typeName:    String       = "Flags"
    val values:      Array[Flags] = Flags.values
    val bitMasks:    Array[Long]  = EnumBitField.computeBitMasks(values, "Flags")
  }

  private val F_AFTER_SPACE_EDIT: Int = BitFieldSet.intMask(Flags.AFTER_SPACE_EDIT)
  private val F_AFTER_INSERT:     Int = BitFieldSet.intMask(Flags.AFTER_INSERT)
  private val F_AFTER_DELETE:     Int = BitFieldSet.intMask(Flags.AFTER_DELETE)

  def track(other: TrackedOffset): TrackedOffset =
    new TrackedOffset(other)

  def track(offset: Int): TrackedOffset =
    track(offset, afterSpaceEdit = false, afterInsert = false, afterDelete = false)

  def track(offset: Int, c: Nullable[Character], afterDelete: Boolean): TrackedOffset =
    track(offset, c.isDefined && c.get == ' ', c.isDefined && !afterDelete, afterDelete)

  def track(offset: Int, afterSpaceEdit: Boolean, afterInsert: Boolean, afterDelete: Boolean): TrackedOffset = {
    assert(!afterInsert && !afterDelete || afterInsert != afterDelete, "Cannot have both afterInsert and afterDelete true")
    new TrackedOffset(offset, afterSpaceEdit, afterInsert, afterDelete)
  }
}
