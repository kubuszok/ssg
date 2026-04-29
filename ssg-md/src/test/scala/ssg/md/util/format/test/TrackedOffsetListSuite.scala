/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package format
package test

import ssg.md.util.sequence.BasedSequence

import java.util.Arrays

import scala.language.implicitConversions

final class TrackedOffsetListSuite extends munit.FunSuite {

  private def offsets(trackedOffsets: java.util.List[TrackedOffset]): Array[Int] = {
    val result = new Array[Int](trackedOffsets.size())
    var i      = 0
    val iter   = trackedOffsets.iterator()
    while (iter.hasNext) {
      result(i) = iter.next().offset
      i += 1
    }
    result
  }

  private def offsetsFromTrackedList(trackedOffsets: TrackedOffsetList): Array[Int] =
    offsets(trackedOffsets.getTrackedOffsets)

  test("test_findTrackedOffsetEmpty") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array[Int]())

    val expected = Array[Int]()
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 10))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetEmpty1") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(0))

    val expected = Array[Int]()
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(2, 10))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetBefore1") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(0))

    val expected = Array[Int]()
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(1, 10))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetEmpty2") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(8))

    val expected = Array[Int]()
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 7))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetAfter1") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(8))

    val expected = Array(8)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 8))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetBefore") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(0))

    val expected = Array(0)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 10))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetFirst") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(2))

    val expected = Array(2)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 10))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetBeforePrefix") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(0, 2, 3, 1))

    val expected = Array[Int]()
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 2))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetPrefix") {
    val input          = BasedSequence.of("* 234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(0, 2, 3, 1))

    val expected = Array(0, 1)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 2))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetDoublePrefix1") {
    val input          = BasedSequence.of("* [ ] 234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(0, 2, 3, 1, 4, 5, 6))

    val expected = Array(0, 1)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 2))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetDoublePrefix2") {
    val input          = BasedSequence.of("* [ ] 234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(0, 2, 3, 1, 4, 5, 6))

    val expected = Array(2, 3, 4, 5)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(2, 6))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetLast") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(8))

    val expected = Array(8)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 10))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }

  test("test_findTrackedOffsetAfter") {
    val input          = BasedSequence.of("  234567  ")
    val trackedOffsets = TrackedOffsetList.create(input.trim(), Array(9))

    val expected = Array(9)
    val actual   = offsetsFromTrackedList(trackedOffsets.getTrackedOffsets(0, 10))
    assert(expected.sameElements(actual), s"\nexpected: ${Arrays.toString(expected)}\nactual: ${Arrays.toString(actual)}\n")
  }
}
