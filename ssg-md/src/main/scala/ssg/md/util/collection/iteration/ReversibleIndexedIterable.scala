/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/ReversibleIndexedIterable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection
package iteration

trait ReversibleIndexedIterable[E] extends ReversibleIterable[E] {
  override def iterator():         ReversibleIndexedIterator[E]
  override def reversed():         ReversibleIndexedIterable[E]
  override def reversedIterator(): ReversibleIndexedIterator[E]
}
