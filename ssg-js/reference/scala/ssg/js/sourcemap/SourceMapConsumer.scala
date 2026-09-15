/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Source map consumer — parses V3 source maps and provides position lookup.
 *
 * Replaces @jridgewell/source-map SourceMapConsumer for the Scala port.
 *
 * Original source: @jridgewell/source-map (used by terser)
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-js-reference: @jridgewell/source-map (used by terser)
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package sourcemap

import scala.collection.mutable.ArrayBuffer

/** Consumes a Source Map V3 and provides original position lookups.
  *
  * Parses the VLQ-encoded mappings string into a searchable structure. Supports `originalPositionFor` to map generated positions back to original source.
  *
  * @param mapData
  *   the parsed source map data
  */
class SourceMapConsumer(val mapData: SourceMapData) {

  /** The source file names from the map. */
  val sources: Array[String] = mapData.sources.toArray

  /** The source content strings (may be null for sources without inline content). */
  val sourcesContent: Array[String | Null] = mapData.sourcesContent.toArray

  // Parsed segments: Array[line][segment](genCol, srcIdx, origLine, origCol[, nameIdx])
  private val decoded: Array[Array[Array[Int]]] = VlqCodec.decodeMappings(mapData.mappings)

  // Resolve absolute values from deltas
  private val resolved: Array[Array[ResolvedSegment]] = resolveDeltas()

  final private case class ResolvedSegment(
    genCol:   Int,
    srcIdx:   Int,
    origLine: Int,
    origCol:  Int,
    nameIdx:  Int
  )

  /** Resolve all VLQ deltas into absolute values. */
  private def resolveDeltas(): Array[Array[ResolvedSegment]] = {
    val result       = ArrayBuffer.empty[Array[ResolvedSegment]]
    var prevSrcIdx   = 0
    var prevOrigLine = 0
    var prevOrigCol  = 0
    var prevNameIdx  = 0

    for (line <- decoded) {
      val segs       = ArrayBuffer.empty[ResolvedSegment]
      var prevGenCol = 0
      // Track sortedness per line — mirrors @jridgewell/sourcemap-codec
      // decode (sourcemap-codec.ts:49-59,79): `if (!sorted) sort(line)`
      var sorted     = true
      var lastGenCol = 0
      for (seg <- line)
        if (seg.length >= 4) {
          val genCol   = prevGenCol + seg(0)
          val srcIdx   = prevSrcIdx + seg(1)
          val origLine = prevOrigLine + seg(2)
          val origCol  = prevOrigCol + seg(3)
          val nameIdx  = if (seg.length >= 5) prevNameIdx + seg(4) else -1

          prevGenCol = genCol
          prevSrcIdx = srcIdx
          prevOrigLine = origLine
          prevOrigCol = origCol
          if (seg.length >= 5) prevNameIdx = nameIdx

          if (genCol < lastGenCol) sorted = false
          lastGenCol = genCol

          segs.addOne(ResolvedSegment(genCol, srcIdx, origLine, origCol, nameIdx))
        } else if (seg.length >= 1) {
          // Segment with only generated column (no original position)
          val genCol = prevGenCol + seg(0)
          prevGenCol = genCol
          // Update sortedness tracking even for genCol-only segments
          // (they affect the column sequence but are dropped from segs)
          if (genCol < lastGenCol) sorted = false
          lastGenCol = genCol
        }
      // Sort by generated column when out of order — stable sort preserves
      // file order for equal genCol values, matching @jridgewell's
      // sort(line) = line.sort(sortComparator); sortComparator(a,b) = a[0] - b[0]
      if (!sorted) {
        val arr = segs.sortBy(_.genCol).toArray
        result.addOne(arr)
      } else {
        result.addOne(segs.toArray)
      }
    }
    result.toArray
  }

  /** Look up the original position for a given generated position.
    *
    * Uses binary search within the line's segments.
    *
    * @param line
    *   1-based generated line number
    * @param column
    *   0-based generated column number
    * @return
    *   the original position, or null fields if no mapping exists
    */
  def originalPositionFor(line: Int, column: Int): OriginalPosition = {
    val lineIdx = line - 1 // convert to 0-based
    if (lineIdx < 0 || lineIdx >= resolved.length) {
      return OriginalPosition(null, 0, 0, null) // @nowarn
    }

    val segs = resolved(lineIdx)
    if (segs.isEmpty) {
      return OriginalPosition(null, 0, 0, null) // @nowarn
    }

    // Greatest-lower-bound search for the segment whose genCol is <= column.
    // @jridgewell's GREATEST_LOWER_BOUND returns the FIRST segment of a run of
    // equal generated columns (its binarySearch lower-bounds on ties), so when
    // several segments share a genCol we must pick the lowest index — otherwise a
    // generated column landing on a duplicate boundary resolves to the wrong
    // original column (off by the duplicate's delta).
    var lo = 0
    var hi = segs.length - 1
    while (lo < hi) {
      val mid = (lo + hi + 1) / 2
      if (segs(mid).genCol <= column) lo = mid
      else hi = mid - 1
    }

    val seg = segs(lo)
    if (seg.genCol > column) {
      return OriginalPosition(null, 0, 0, null) // @nowarn
    }
    // Walk back to the first segment sharing this generated column (lower bound).
    while (lo > 0 && segs(lo - 1).genCol == seg.genCol) lo -= 1
    val first = segs(lo)

    val sourceName = if (first.srcIdx >= 0 && first.srcIdx < sources.length) sources(first.srcIdx) else null
    val names      = mapData.names
    val name       = if (first.nameIdx >= 0 && first.nameIdx < names.size) names(first.nameIdx) else null

    OriginalPosition(
      source = sourceName,
      line = first.origLine + 1, // convert back to 1-based
      column = first.origCol,
      name = name
    )
  }

  /** Cleanup (no-op in Scala — no native resources to free). */
  def destroy(): Unit = ()
}
