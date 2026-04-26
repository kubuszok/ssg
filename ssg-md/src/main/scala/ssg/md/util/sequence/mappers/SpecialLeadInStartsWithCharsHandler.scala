/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/SpecialLeadInStartsWithCharsHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/SpecialLeadInStartsWithCharsHandler.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package mappers

import ssg.md.Nullable
import ssg.md.util.data.DataHolder
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence

/** Handles lead-in characters that match a predicate when the sequence starts with such a character.
  */
class SpecialLeadInStartsWithCharsHandler protected (val predicate: CharPredicate) extends SpecialLeadInHandler {

  /** Escape special lead-in characters which start a block element if first non-whitespace on the line
    *
    * The leadIn sequence is always followed by a space or EOL so if lead in does not require a space to start a block element then test if it starts with the special sequence, otherwise test if it
    * equals the special sequence
    *
    * @param sequence
    *   char sequence appearing as first non-whitespace on a line
    * @param options
    *   options
    * @param consumer
    *   consumer of char sequences to be called for the leadIn if it is changed by this handler
    * @return
    *   true if sequence was a lead in for the handler
    */
  override def escape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean =
    if (sequence.length() >= 1 && predicate.test(sequence.charAt(0))) {
      consumer("\\")
      consumer(sequence)
      true
    } else {
      false
    }

  /** UnEscape special lead-in characters which start a block element if first non-whitespace on the line
    *
    * The leadIn sequence is always followed by a space or EOL so if lead in does not require a space to start a block element then test if it starts with the special sequence, otherwise test if it
    * equals the special sequence
    *
    * @param sequence
    *   char sequence appearing as first non-whitespace on a line
    * @param options
    *   options
    * @param consumer
    *   consumer of char sequences to be called for the leadIn if it is changed by this handler
    * @return
    *   true if sequence was a lead in for the handler
    */
  override def unEscape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean =
    if (sequence.length() >= 2 && sequence.charAt(0) == '\\' && predicate.test(sequence.charAt(1))) {
      consumer(sequence.subSequence(1))
      true
    } else {
      false
    }
}

object SpecialLeadInStartsWithCharsHandler {

  def create(leadInChar: Char): SpecialLeadInStartsWithCharsHandler =
    new SpecialLeadInStartsWithCharsHandler(CharPredicate.anyOf(leadInChar))

  def create(leadInChars: CharSequence): SpecialLeadInStartsWithCharsHandler =
    new SpecialLeadInStartsWithCharsHandler(CharPredicate.anyOf(leadInChars))
}
