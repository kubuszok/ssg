/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/DependentItemMap.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/DependentItemMap.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package dependency

import ssg.md.util.collection.{ CollectionHost, OrderedMap }

class DependentItemMap[D]() extends OrderedMap[Class[?], DependentItem[D]]() {

  def this(capacity: Int) = {
    this()
    // OrderedMap handles capacity in its constructor
  }

  def this(host: CollectionHost[Class[?]]) = {
    this()
    // OrderedMap handles host in its constructor
  }

  def this(capacity: Int, host: CollectionHost[Class[?]]) = {
    this()
    // OrderedMap handles capacity and host in its constructor
  }
}

object DependentItemMap {
  def apply[D]():              DependentItemMap[D] = new DependentItemMap[D]()
  def apply[D](capacity: Int): DependentItemMap[D] = new DependentItemMap[D](capacity)
}
