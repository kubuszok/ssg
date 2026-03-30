/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataValueNullableFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

/** Factory that produces a Nullable value of type T given a Nullable[DataHolder].
  */
trait DataValueNullableFactory[T] extends DataValueFactory[T] {
  def apply(dataHolder: Nullable[DataHolder]): Nullable[T]
}
