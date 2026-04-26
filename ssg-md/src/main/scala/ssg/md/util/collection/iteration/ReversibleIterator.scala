/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/ReversibleIterator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/ReversibleIterator.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package collection
package iteration

trait ReversibleIterator[E] extends java.util.Iterator[E] {
  def isReversed: Boolean
}
