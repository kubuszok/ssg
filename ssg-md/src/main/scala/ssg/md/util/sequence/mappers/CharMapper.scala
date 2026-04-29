/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/CharMapper.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/CharMapper.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package mappers

import scala.language.implicitConversions

/** Maps characters to characters.
  */
trait CharMapper {

  /** Map characters
    *
    * @param c
    *   code point
    * @return
    *   mapped character
    */
  def map(c: Char): Char

  /** Returns a composed operator that first applies the `before` operator to its input, and then applies this operator to the result. If evaluation of either operator throws an exception, it is
    * relayed to the caller of the composed operator.
    *
    * @param before
    *   the operator to apply before this operator is applied
    * @return
    *   a composed operator that first applies the `before` operator and then applies this operator
    * @see
    *   [[andThen]]
    */
  def compose(before: CharMapper): CharMapper =
    if (before eq CharMapper.IDENTITY) this
    else {
      val self = this
      (v: Char) => self.map(before.map(v))
    }

  /** Returns a composed operator that first applies this operator to its input, and then applies the `after` operator to the result. If evaluation of either operator throws an exception, it is
    * relayed to the caller of the composed operator.
    *
    * @param after
    *   the operator to apply after this operator is applied
    * @return
    *   a composed operator that first applies this operator and then applies the `after` operator
    * @see
    *   [[compose]]
    */
  def andThen(after: CharMapper): CharMapper =
    if (after eq CharMapper.IDENTITY) this
    else {
      val self = this
      (t: Char) => after.map(self.map(t))
    }
}

object CharMapper {

  /** Identity mapper that returns its input unchanged. */
  val IDENTITY: CharMapper = (t: Char) => t

  /** Returns a unary operator that always returns its input argument.
    *
    * @return
    *   a unary operator that always returns its input argument
    */
  def identity(): CharMapper = IDENTITY

  /** Creates a CharMapper from a function. */
  implicit def fromFunction(f: Char => Char): CharMapper =
    new CharMapper {
      def map(c: Char): Char = f(c)
    }
}
