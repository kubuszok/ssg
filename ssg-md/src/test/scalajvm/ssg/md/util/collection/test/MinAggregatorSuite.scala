/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package collection
package test

import ssg.md.Nullable

import java.util.function.BiFunction

// NOTE: Original Java file MinAggregatorTest.java actually tests MaxAggregator.INSTANCE
// (this is the original code's naming — MinAggregatorTest tests MaxAggregator)
final class MinAggregatorSuite extends munit.FunSuite {

  private def reduce(aggregator: BiFunction[Nullable[Integer], Nullable[Integer], Nullable[Integer]], items: Nullable[Integer]*): Nullable[Integer] = {
    var aggregate: Nullable[Integer] = Nullable.empty
    for (item <- items)
      aggregate = aggregator.apply(aggregate, item)
    aggregate
  }

  test("test_Basic") {
    assert(reduce(MaxAggregator).isEmpty)
    assert(reduce(MaxAggregator, Nullable.empty).isEmpty)
    assertEquals(reduce(MaxAggregator, Nullable(-1), Nullable(-2), Nullable(-5), Nullable(0), Nullable(1)).get, Integer.valueOf(1))
    assertEquals(
      reduce(MaxAggregator, Nullable(-1), Nullable(-2), Nullable(-5), Nullable(0), Nullable(1), Nullable(5)).get,
      Integer.valueOf(5)
    )
  }
}
