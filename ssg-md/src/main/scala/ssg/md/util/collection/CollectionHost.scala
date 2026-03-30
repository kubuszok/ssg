/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/CollectionHost.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable

trait CollectionHost[K] {
  def adding(index:      Int, k: Nullable[K], v: Nullable[Object]): Unit
  def removing(index:    Int, k: Nullable[K]):                      Nullable[Object]
  def clearing():                                                   Unit
  def addingNulls(index: Int):                                      Unit // adding an empty place holder at index

  def skipHostUpdate():             Boolean // if should not call back host
  def getIteratorModificationCount: Int // return version stamp used to detect concurrent modifications
}
