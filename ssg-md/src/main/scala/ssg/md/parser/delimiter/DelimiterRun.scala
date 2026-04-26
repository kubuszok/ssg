/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/delimiter/DelimiterRun.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/delimiter/DelimiterRun.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package delimiter

import ssg.md.ast.Text

/** A delimiter run is one or more of the same delimiter character.
  */
trait DelimiterRun {

  def previous:      DelimiterRun
  def next:          DelimiterRun
  def delimiterChar: Char
  def node:          Text

  /** @return whether this can open a delimiter */
  def canOpen: Boolean

  /** @return whether this can close a delimiter */
  def canClose: Boolean

  /** @return the number of characters in this delimiter run (that are left for processing) */
  def length: Int
}
