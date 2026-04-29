/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/BoundedMaxAggregator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/BoundedMaxAggregator.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable

import java.util.function.BiFunction

class BoundedMaxAggregator(val maxBound: Int) extends BiFunction[Nullable[Integer], Nullable[Integer], Nullable[Integer]] {

  override def apply(aggregate: Nullable[Integer], next: Nullable[Integer]): Nullable[Integer] =
    if (next.isDefined && next.get < maxBound) MaxAggregator.apply(aggregate, next)
    else aggregate
}
