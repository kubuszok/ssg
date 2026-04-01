/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedOptionsHolder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, DataKeyBase, NullableDataKey }
import ssg.md.util.misc.{ BitFieldSet, EnumBitField }
import ssg.md.util.sequence.builder.SegmentedSequenceStats

/** Implemented by BasedOptionsSequence, use instance of it to pass to [[BasedSequence.of]] and options enabled in it will be accessible to all based sequences or uses of these for testing for options
  * or getting options.
  *
  * Only works with SubSequence base not CharArraySequence
  */
trait BasedOptionsHolder {

  /** Options test for options for this sequence
    *
    * default reports true for global default options (if any), variation available on BasedSequenceWithOptions
    *
    * @return
    *   option flags for this sequence
    */
  def optionFlags: Int

  /** Options test for options for this sequence
    *
    * default reports true for global default options (if any), variation available on BasedSequenceWithOptions
    *
    * @param options
    *   option flags
    * @return
    *   true if all option flags passed are set for this sequence
    */
  def allOptions(options: Int): Boolean

  /** Options test for options for this sequence
    *
    * default reports true for global default options (if any), variation available on BasedSequenceWithOptions
    *
    * @param options
    *   option flags
    * @return
    *   true if any option flags passed are set for this sequence
    */
  def anyOptions(options: Int): Boolean

  /** Options holder, default has none, only available on BasedSequenceWithOptions
    *
    * @param dataKey
    *   in options
    * @tparam T
    *   type of value held by key
    * @return
    *   value for the data key or null if none
    */
  def getOption[T](dataKey: DataKeyBase[T]): Nullable[T]

  /** Options holder, default has none, only available on BasedSequenceWithOptions
    *
    * @return
    *   data holder with options or null if none for this sequence
    */
  def options: Nullable[DataHolder]
}

object BasedOptionsHolder {

  enum Options extends java.lang.Enum[Options] {
    case COLLECT_SEGMENTED_STATS
    case COLLECT_FIRST256_STATS
    case NO_ANCHORS
    case FULL_SEGMENTED_SEQUENCES
    case TREE_SEGMENTED_SEQUENCES
  }

  given EnumBitField[Options] with {
    def elementType: Class[Options] = classOf[Options]
    def typeName: String = "Options"
    val values: Array[Options] = Options.values
    val bitMasks: Array[Long] = EnumBitField.computeBitMasks(values, "Options")
  }

  val O_COLLECT_SEGMENTED_STATS:  Options = Options.COLLECT_SEGMENTED_STATS
  val O_COLLECT_FIRST256_STATS:   Options = Options.COLLECT_FIRST256_STATS
  val O_NO_ANCHORS:               Options = Options.NO_ANCHORS
  val O_FULL_SEGMENTED_SEQUENCES: Options = Options.FULL_SEGMENTED_SEQUENCES
  val O_TREE_SEGMENTED_SEQUENCES: Options = Options.TREE_SEGMENTED_SEQUENCES

  // NOTE: if no data holder or one with no SEGMENTED_STATS is passed to BasedOptionsSequence, then F_COLLECT_SEGMENTED_STATS flag will be removed from options
  val F_COLLECT_SEGMENTED_STATS: Int = BitFieldSet.intMask(O_COLLECT_SEGMENTED_STATS) // set if segmented stats collector key is set to non-null value
  val F_COLLECT_FIRST256_STATS:  Int = BitFieldSet.intMask(O_COLLECT_FIRST256_STATS) // collect statistics for segments sequence on chars < code 256, used to optimize out of base chars for ascii
  val F_NO_ANCHORS:              Int = BitFieldSet.intMask(O_NO_ANCHORS) // do not include anchors in segment builder, test only, not guaranteed to be stable for general use

  // NOTE: if neither is specified then one will be chosen, most likely tree
  //   but may be full for short sequences or ones where number of segments vs
  //   sequence length makes tree based one wasteful and slow
  val F_FULL_SEGMENTED_SEQUENCES: Int = BitFieldSet.intMask(O_FULL_SEGMENTED_SEQUENCES) // use full segmented sequences
  val F_TREE_SEGMENTED_SEQUENCES: Int = BitFieldSet.intMask(O_TREE_SEGMENTED_SEQUENCES) // use tree based segmented sequences

  val F_LIBRARY_OPTIONS:     Int = 0x0000ffff // reserved for library use, extensions must use data keys since there is no way to manage bit allocations
  val F_APPLICATION_OPTIONS: Int = 0xffff0000 // open for user application defined use, extensions must use data keys since there is no way to manage bit allocations

  // NOTE: if no data holder or one with no SEGMENTED_STATS is passed to BasedOptionsSequence, then F_COLLECT_SEGMENTED_STATS flag will be removed from options
  val SEGMENTED_STATS: NullableDataKey[SegmentedSequenceStats] = new NullableDataKey[SegmentedSequenceStats]("SEGMENTED_STATS")

  def optionsToString(options: Int): String =
    BitFieldSet.of(classOf[Options], options).toString
}
