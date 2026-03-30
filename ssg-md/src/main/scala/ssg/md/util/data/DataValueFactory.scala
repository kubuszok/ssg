/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataValueFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

/** Factory that produces a value of type T given a DataHolder. Replaces Java's Function[DataHolder, T].
  */
trait DataValueFactory[T] {
  def apply(dataHolder: DataHolder): Nullable[T]
}
