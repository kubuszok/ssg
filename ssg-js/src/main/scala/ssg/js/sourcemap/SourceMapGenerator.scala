/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Source map generator — accumulates mappings and produces V3 source maps.
 *
 * Replaces @jridgewell/source-map SourceMapGenerator for the Scala port.
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

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/** Generates Source Map V3 data from accumulated mappings.
  *
  * @param file
  *   the name of the generated file
  * @param sourceRoot
  *   the root directory for source files
  */
class SourceMapGenerator(
  file:       String | Null = null,
  sourceRoot: String | Null = null
) {

  private val mappings = ArrayBuffer.empty[SourceMapping]

  private val sourceIndex = mutable.Map.empty[String, Int]
  private val nameIndex   = mutable.Map.empty[String, Int]

  private val sources        = ArrayBuffer.empty[String]
  private val sourcesContent = mutable.Map.empty[String, String | Null]
  private val names          = ArrayBuffer.empty[String]

  /** Add a mapping from a generated position to an original position. */
  def addMapping(mapping: SourceMapping): Unit =
    mappings.addOne(mapping)

  /** Set the source content for a given source file. */
  def setSourceContent(source: String, content: String | Null): Unit =
    if (source != null) sourcesContent(source) = content

  /** Convert all accumulated mappings to a JSON-compatible SourceMapData.
    *
    * The mappings field is a VLQ-encoded string.
    */
  def toJSON(): SourceMapData = {
    buildIndices()
    val encoded = encodeMappings()

    val sc = ArrayBuffer.empty[String | Null]
    sources.foreach { s =>
      sc.addOne(sourcesContent.getOrElse(s, null))
    }

    SourceMapData(
      version = 3,
      file = file,
      sourceRoot = sourceRoot,
      sources = sources.clone(),
      sourcesContent = sc,
      names = names.clone(),
      mappings = encoded
    )
  }

  /** Convert to decoded map format (same structure, but mappings is empty since we provide the structured data).
    */
  def toDecodedMap(): SourceMapData =
    toJSON()

  // -----------------------------------------------------------------------
  // Internal: build source/name index maps and encode
  // -----------------------------------------------------------------------

  private def buildIndices(): Unit = {
    sourceIndex.clear()
    nameIndex.clear()
    sources.clear()
    names.clear()

    for (m <- mappings) {
      if (m.source != null && !sourceIndex.contains(m.source.nn)) {
        sourceIndex(m.source.nn) = sources.size
        sources.addOne(m.source.nn)
      }
      if (m.name != null && !nameIndex.contains(m.name.nn)) {
        nameIndex(m.name.nn) = names.size
        names.addOne(m.name.nn)
      }
    }
  }

  /** Encode all mappings to a VLQ string.
    *
    * Mappings are sorted by generated position. Each position component is delta-encoded from the previous segment.
    */
  private def encodeMappings(): String = {
    if (mappings.isEmpty) return "" // @nowarn

    // Sort by generated line, then column
    val sorted = mappings.sortWith { (a, b) =>
      if (a.generatedLine != b.generatedLine) a.generatedLine < b.generatedLine
      else a.generatedColumn < b.generatedColumn
    }

    val sb           = new StringBuilder
    var prevGenCol   = 0
    var prevOrigLine = 0
    var prevOrigCol  = 0
    var prevSource   = 0
    var prevName     = 0
    var prevGenLine  = 1

    for (m <- sorted) {
      // Add semicolons for line breaks
      while (prevGenLine < m.generatedLine) {
        sb.append(';')
        prevGenLine += 1
        prevGenCol = 0
      }

      // Add comma separator between segments on the same line
      if (sb.nonEmpty && sb.last != ';') sb.append(',')

      // Always encode generated column (delta from previous on this line)
      sb.append(VlqCodec.encode(m.generatedColumn - prevGenCol))
      prevGenCol = m.generatedColumn

      if (m.source != null) {
        val srcIdx = sourceIndex(m.source.nn)
        // Source index delta
        sb.append(VlqCodec.encode(srcIdx - prevSource))
        prevSource = srcIdx

        // Original line delta (1-based in source map spec)
        sb.append(VlqCodec.encode(m.originalLine - prevOrigLine))
        prevOrigLine = m.originalLine

        // Original column delta
        sb.append(VlqCodec.encode(m.originalColumn - prevOrigCol))
        prevOrigCol = m.originalColumn

        if (m.name != null) {
          val nmIdx = nameIndex(m.name.nn)
          sb.append(VlqCodec.encode(nmIdx - prevName))
          prevName = nmIdx
        }
      }
    }

    sb.toString()
  }
}
