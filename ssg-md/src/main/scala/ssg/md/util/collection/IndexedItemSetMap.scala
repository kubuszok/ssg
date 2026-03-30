/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/IndexedItemSetMap.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection

trait IndexedItemSetMap[M, S, K] extends java.util.Map[M, S] {
  def mapKey(key: K): M

  def newSet():                         S
  def addSetItem(s:      S, item: Int): Boolean
  def removeSetItem(s:   S, item: Int): Boolean
  def containsSetItem(s: S, item: Int): Boolean

  def addItem(key:      K, item: Int): Boolean
  def removeItem(key:   K, item: Int): Boolean
  def containsItem(key: K, item: Int): Boolean
}
