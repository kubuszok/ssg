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
    var aggregate: Nullable[Integer] = null
    for (item <- items)
      aggregate = aggregator.apply(aggregate, item)
    aggregate
  }

  test("test_Basic") {
    assert(reduce(new BoundedMaxAggregator(3)).isEmpty)
    assert(reduce(new BoundedMaxAggregator(3), null).isEmpty)
    assertEquals(
      reduce(
        new BoundedMaxAggregator(3),
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ).get.intValue(),
      2
    )
    assertEquals(
      reduce(
        new BoundedMaxAggregator(5),
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ).get.intValue(),
      4
    )
    assert(reduce(new BoundedMaxAggregator(10), 10, 11, 12, 13).isEmpty)
  }
}
