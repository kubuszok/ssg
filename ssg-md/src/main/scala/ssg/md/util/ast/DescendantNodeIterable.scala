/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DescendantNodeIterable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DescendantNodeIterable.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.util.collection.iteration.ReversiblePeekingIterable
import ssg.md.util.collection.iteration.ReversiblePeekingIterator

/** iterate nodes, with descendants, depth first until all are done
  */
class DescendantNodeIterable(iterable: ReversiblePeekingIterable[Node]) extends ReversiblePeekingIterable[Node] {

  private val _iterable: ReversiblePeekingIterable[Node] = iterable match {
    case d: DescendantNodeIterable => d._iterable
    case _ => iterable
  }

  override def iterator(): ReversiblePeekingIterator[Node] =
    new DescendantNodeIterator(_iterable.iterator())

  override def reversed(): ReversiblePeekingIterable[Node] =
    new DescendantNodeIterable(_iterable.reversed())

  override def reversedIterator(): ReversiblePeekingIterator[Node] =
    new DescendantNodeIterator(_iterable.reversedIterator())

  override def isReversed: Boolean = _iterable.isReversed
}
