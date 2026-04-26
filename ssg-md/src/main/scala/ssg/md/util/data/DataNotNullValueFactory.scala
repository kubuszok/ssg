/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataNotNullValueFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataNotNullValueFactory.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

/** Factory that produces a non-null value of type T given a DataHolder.
  */
trait DataNotNullValueFactory[T] extends DataValueFactory[T] {
  override def apply(dataHolder: DataHolder): Nullable[T]
}
