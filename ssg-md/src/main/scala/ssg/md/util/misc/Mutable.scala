/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/Mutable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package misc

trait Mutable[M <: Mutable[M, I], I <: Immutable[I, M]] {
  def toImmutable: I
}
