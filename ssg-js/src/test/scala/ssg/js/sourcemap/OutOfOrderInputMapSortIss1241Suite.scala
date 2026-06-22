/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1241: Verify that SourceMapConsumer sorts per-line segments by
 * generated column when the input source map has segments out of
 * generated-column order, matching @jridgewell/sourcemap-codec's
 * `if (!sorted) sort(line)` at line 79 of sourcemap-codec.ts.
 *
 * Proof-of-red: without the sort in resolveDeltas(), the binary search
 * in originalPositionFor resolves to the wrong segment (A instead of C)
 * for a column that falls between two segments that are only correctly
 * ordered after sorting.
 */
package ssg
package js
package sourcemap

import scala.collection.mutable.ArrayBuffer

final class OutOfOrderInputMapSortIss1241Suite extends munit.FunSuite {

  // --- helpers ---------------------------------------------------------------

  /** Build a SourceMapData with a single source and no names. */
  private def makeMap(mappings: String): SourceMapData = SourceMapData(
    version = 3,
    sources = ArrayBuffer("input.js"),
    sourcesContent = ArrayBuffer("/* original source */"),
    names = ArrayBuffer.empty,
    mappings = mappings
  )

  // --- test 1: out-of-order segments are resolved correctly ------------------

  test("originalPositionFor returns correct position when segments are out of generated-column order (ISS-1241)") {
    // Construct a single-line mapping with three 4-field segments whose
    // absolute generated columns, when accumulated from the VLQ deltas in
    // file order, are OUT of ascending order:
    //
    //   Segment A: genCol=5,  srcIdx=0, origLine=0, origCol=100
    //   Segment B: genCol=20, srcIdx=0, origLine=0, origCol=200
    //   Segment C: genCol=10, srcIdx=0, origLine=0, origCol=150
    //
    // File order:  [A(5), B(20), C(10)]  <-- NOT sorted by genCol
    // Sorted order: [A(5), C(10), B(20)]
    //
    // VLQ deltas (genCol resets per line; srcIdx/origLine/origCol accumulate):
    //   A: [5, 0, 0, 100]
    //   B: [15, 0, 0, 100]    (genCol: 5->20 = +15;  origCol: 100->200 = +100)
    //   C: [-10, 0, 0, -50]   (genCol: 20->10 = -10; origCol: 200->150 = -50)
    val mappings = VlqCodec.encodeMappings(
      Array(
        Array(
          Array(5, 0, 0, 100), // A
          Array(15, 0, 0, 100), // B
          Array(-10, 0, 0, -50) // C
        )
      )
    )

    val consumer = new SourceMapConsumer(makeMap(mappings))

    // Query column=12: falls between C(genCol=10) and B(genCol=20).
    // Correct GLB = C (genCol=10, origCol=150, origLine=1 one-based).
    // Without the sort, binary search over unsorted [A(5),B(20),C(10)]
    // yields A(genCol=5, origCol=100) -- WRONG.
    val pos = consumer.originalPositionFor(line = 1, column = 12)
    assertEquals(pos.source, "input.js")
    assertEquals(pos.line, 1, "origLine should be 1 (1-based)")
    assertEquals(pos.column, 150, "origCol should be 150 (from segment C, the correct GLB)")

    // Also check that exact matches work after sorting:
    // column=10 should match C exactly.
    val posExact = consumer.originalPositionFor(line = 1, column = 10)
    assertEquals(posExact.column, 150, "column=10 should resolve to C(origCol=150)")

    // column=5 should match A exactly.
    val posA = consumer.originalPositionFor(line = 1, column = 5)
    assertEquals(posA.column, 100, "column=5 should resolve to A(origCol=100)")

    // column=20 should match B exactly.
    val posB = consumer.originalPositionFor(line = 1, column = 20)
    assertEquals(posB.column, 200, "column=20 should resolve to B(origCol=200)")

    consumer.destroy()
  }

  // --- test 2: well-formed (already sorted) map is unaffected ----------------

  test("originalPositionFor works correctly for a normally-ordered map (control, ISS-1241)") {
    // Same three segments but in correct generated-column order:
    //   A: genCol=5,  origCol=100
    //   C: genCol=10, origCol=150
    //   B: genCol=20, origCol=200
    //
    // VLQ deltas when segments are in sorted order:
    //   A: [5, 0, 0, 100]
    //   C: [5, 0, 0, 50]     (genCol: 5->10 = +5;  origCol: 100->150 = +50)
    //   B: [10, 0, 0, 50]    (genCol: 10->20 = +10; origCol: 150->200 = +50)
    val mappings = VlqCodec.encodeMappings(
      Array(
        Array(
          Array(5, 0, 0, 100), // A
          Array(5, 0, 0, 50), // C
          Array(10, 0, 0, 50) // B
        )
      )
    )

    val consumer = new SourceMapConsumer(makeMap(mappings))

    // Same queries as above -- results must be identical.
    val pos = consumer.originalPositionFor(line = 1, column = 12)
    assertEquals(pos.source, "input.js")
    assertEquals(pos.line, 1)
    assertEquals(pos.column, 150, "column=12 should resolve to C(origCol=150)")

    val posExact = consumer.originalPositionFor(line = 1, column = 10)
    assertEquals(posExact.column, 150, "column=10 should resolve to C(origCol=150)")

    val posA = consumer.originalPositionFor(line = 1, column = 5)
    assertEquals(posA.column, 100, "column=5 should resolve to A(origCol=100)")

    val posB = consumer.originalPositionFor(line = 1, column = 20)
    assertEquals(posB.column, 200, "column=20 should resolve to B(origCol=200)")

    consumer.destroy()
  }

  // --- test 3: multi-line with only one out-of-order line --------------------

  test("sort is per-line: only the out-of-order line is corrected (ISS-1241)") {
    // Line 0: sorted segments (genCol 3, 8)
    // Line 1: out-of-order segments (genCol 12, 4, 20 in file order)
    //
    // Line 0 deltas:
    //   seg0: [3, 0, 0, 10]   -> genCol=3,  origCol=10
    //   seg1: [5, 0, 0, 20]   -> genCol=8,  origCol=30
    // Line 1 deltas (srcIdx/origLine/origCol carry from line 0 end state):
    //   srcIdx state=0, origLine state=0, origCol state=30
    //   seg0: [12, 0, 1, -20] -> genCol=12, srcIdx=0, origLine=1, origCol=10
    //   seg1: [-8, 0, 0, 15]  -> genCol=4,  srcIdx=0, origLine=1, origCol=25
    //   seg2: [16, 0, 0, 5]   -> genCol=20, srcIdx=0, origLine=1, origCol=30
    val mappings = VlqCodec.encodeMappings(
      Array(
        Array(
          Array(3, 0, 0, 10),
          Array(5, 0, 0, 20)
        ),
        Array(
          Array(12, 0, 1, -20),
          Array(-8, 0, 0, 15),
          Array(16, 0, 0, 5)
        )
      )
    )

    val consumer = new SourceMapConsumer(makeMap(mappings))

    // Line 1 (1-based): well-ordered, should work as-is.
    val p1 = consumer.originalPositionFor(line = 1, column = 3)
    assertEquals(p1.column, 10, "line 1, column=3 -> origCol=10")

    val p2 = consumer.originalPositionFor(line = 1, column = 6)
    assertEquals(p2.column, 10, "line 1, column=6 -> GLB is seg0(genCol=3), origCol=10")

    // Line 2 (1-based): out-of-order in file order [12,4,20], sorted [4,12,20].
    // Query column=10: GLB in sorted order is seg1(genCol=4, origCol=25).
    val p3 = consumer.originalPositionFor(line = 2, column = 10)
    assertEquals(p3.line, 2, "origLine should be 2 (1-based, from origLine=1)")
    assertEquals(p3.column, 25, "line 2, column=10 -> GLB is seg1(genCol=4, origCol=25)")

    // Query column=15: GLB in sorted order is seg0(genCol=12, origCol=10).
    val p4 = consumer.originalPositionFor(line = 2, column = 15)
    assertEquals(p4.column, 10, "line 2, column=15 -> GLB is seg0(genCol=12, origCol=10)")

    consumer.destroy()
  }
}
