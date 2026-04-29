/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/IndexedItemBitSetMap.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/IndexedItemBitSetMap.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection

import java.util.BitSet

class IndexedItemBitSetMap[K, M](
  val computable: M => K,
  capacity:       Int
) extends IndexedItemSetMapBase[K, BitSet, M](capacity) {

  def this(computable: M => K) = this(computable, 0)

  override def mapKey(key: M): K = computable(key)

  override def newSet(): BitSet = new BitSet()

  override def addSetItem(set: BitSet, item: Int): Boolean = {
    val old = set.get(item)
    set.set(item)
    old
  }

  override def removeSetItem(set: BitSet, item: Int): Boolean = {
    val old = set.get(item)
    set.clear(item)
    old
  }

  override def containsSetItem(set: BitSet, item: Int): Boolean =
    set.get(item)
}
