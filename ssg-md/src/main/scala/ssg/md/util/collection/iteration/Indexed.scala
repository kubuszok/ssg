/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/Indexed.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/Indexed.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package collection
package iteration

trait Indexed[E] {
  def get(index:      Int):          E
  def set(index:      Int, item: E): Unit
  def removeAt(index: Int):          Unit
  def size:                          Int
  def modificationCount:             Int
}
