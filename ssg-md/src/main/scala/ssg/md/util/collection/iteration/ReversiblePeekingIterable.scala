/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/ReversiblePeekingIterable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection
package iteration

trait ReversiblePeekingIterable[E] extends ReversibleIterable[E] {
  override def iterator():         ReversiblePeekingIterator[E]
  override def reversed():         ReversiblePeekingIterable[E]
  override def reversedIterator(): ReversiblePeekingIterator[E]
}
