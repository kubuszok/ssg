/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/SpecialLeadInHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/SpecialLeadInHandler.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package mappers

import ssg.md.Nullable
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

/** Handler for escaping/unescaping special lead-in characters which start a block element if first non-whitespace on the line.
  */
trait SpecialLeadInHandler {

  /** Escape special lead-in characters which start a block element if first non-whitespace on the line
    *
    * The leadIn sequence is always followed by a space or EOL so if lead in does not require a space to start a block element then test if it starts with the special sequence, otherwise test if it
    * equals the special sequence
    *
    * @param sequence
    *   char sequence appearing as first non-whitespace on a line
    * @param options
    *   additional options if needed
    * @param consumer
    *   consumer of char sequences to be called for the leadIn if it is changed by this handler
    * @return
    *   true if sequence was a lead in for the handler
    */
  def escape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean

  /** UnEscape special lead-in characters which start a block element if first non-whitespace on the line
    *
    * The leadIn sequence is always followed by a space or EOL so if lead in does not require a space to start a block element then test if it starts with the special sequence, otherwise test if it
    * equals the special sequence
    *
    * @param sequence
    *   char sequence appearing as first non-whitespace on a line
    * @param options
    *   additional options if needed
    * @param consumer
    *   consumer of char sequences to be called for the leadIn if it is changed by this handler
    * @return
    *   true if sequence was a lead in for the handler
    */
  def unEscape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean
}
