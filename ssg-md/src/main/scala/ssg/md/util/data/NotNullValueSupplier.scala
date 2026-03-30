/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/NotNullValueSupplier.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package data

/** Supplier that always produces a non-null value of type T. Replaces Java's Supplier[T] with non-null guarantee.
  */
trait NotNullValueSupplier[T] {
  def get: T
}
