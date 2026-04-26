/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/BoundedMinAggregator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/BoundedMinAggregator.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable

import java.util.function.BiFunction

class BoundedMinAggregator(val minBound: Int) extends BiFunction[Nullable[Integer], Nullable[Integer], Nullable[Integer]] {

  override def apply(aggregate: Nullable[Integer], next: Nullable[Integer]): Nullable[Integer] =
    if (next.isDefined && next.get > minBound) MinAggregator.apply(aggregate, next)
    else aggregate
}
