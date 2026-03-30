/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/MinAggregator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable

import java.util.function.BiFunction

object MinAggregator extends BiFunction[Nullable[Integer], Nullable[Integer], Nullable[Integer]] {

  override def apply(aggregate: Nullable[Integer], next: Nullable[Integer]): Nullable[Integer] =
    if (next.isEmpty || (aggregate.isDefined && aggregate.get <= next.get)) aggregate
    else next
}
