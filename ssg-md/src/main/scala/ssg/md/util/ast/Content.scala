/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/Content.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.util.sequence.BasedSequence

/** A node that uses delimiters in the source form (e.g. `*bold*`).
  */
trait Content {
  def spanningChars:                              BasedSequence
  def lineCount:                                  Int
  def lineChars(index:        Int):               BasedSequence
  def contentChars:                               BasedSequence
  def contentChars(startLine: Int, endLine: Int): BasedSequence
  def contentLines:                               java.util.List[BasedSequence]
  def contentLines(startLine: Int, endLine: Int): java.util.List[BasedSequence]
}
