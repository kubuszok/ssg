/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package collection
package test

import ssg.md.Nullable

import java.util.function.BiFunction

final class BoundedMaxAggregatorSuite extends munit.FunSuite {

  private def reduce(aggregator: BiFunction[Nullable[Integer], Nullable[Integer], Nullable[Integer]], items: Nullable[Integer]*): Nullable[Integer] = {
    var aggregate: Nullable[Integer] = Nullable.empty
    for (item <- items)
      aggregate = aggregator.apply(aggregate, item)
    aggregate
  }

  test("test_Basic") {
    assert(reduce(new BoundedMaxAggregator(3)).isEmpty)
    assert(reduce(new BoundedMaxAggregator(3), Nullable.empty).isEmpty)
    assertEquals(
      reduce(
        new BoundedMaxAggregator(3),
        Nullable(1),
        Nullable(2),
        Nullable(3),
        Nullable(4),
        Nullable(5),
        Nullable(6),
        Nullable(7),
        Nullable(8),
        Nullable(9),
        Nullable(10)
      ).get.intValue(),
      2
    )
    assertEquals(
      reduce(
        new BoundedMaxAggregator(5),
        Nullable(1),
        Nullable(2),
        Nullable(3),
        Nullable(4),
        Nullable(5),
        Nullable(6),
        Nullable(7),
        Nullable(8),
        Nullable(9),
        Nullable(10)
      ).get.intValue(),
      4
    )
    assert(reduce(new BoundedMaxAggregator(10), Nullable(10), Nullable(11), Nullable(12), Nullable(13)).isEmpty)
  }
}
