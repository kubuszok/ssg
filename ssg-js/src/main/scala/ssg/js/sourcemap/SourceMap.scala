/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Source map wrapper — the main API for creating and managing source maps
 * during JavaScript minification.
 *
 * Original source: terser lib/sourcemap.js (149 LOC)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: SourceMap -> SourceMap (same)
 *   Convention: Class instead of generator function
 *   Idiom: Options case class instead of JS defaults() pattern
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/sourcemap.js (149 LOC)
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package sourcemap

/** Source map wrapper that coordinates generation and optional chaining.
  *
  * Wraps a `SourceMapGenerator` and an optional `SourceMapConsumer` (for source map chaining when compressing already-mapped code).
  *
  * @param options
  *   source map options (file, root, orig, files)
  */
class SourceMap(options: SourceMapOptions) {

  private val generator = new SourceMapGenerator(
    file = options.file,
    sourceRoot = options.root
  )

  // If an original source map is provided, create a consumer for chain resolution
  private val origMap: SourceMapConsumer | Null =
    options.orig match {
      case orig: SourceMapData => new SourceMapConsumer(orig)
      case null => null
    }

  // Populate source content from the `files` option
  private val sourcesContent: scala.collection.mutable.Map[String, String | Null] = {
    val m = scala.collection.mutable.Map.empty[String, String | Null]
    for ((name, content) <- options.files) m(name) = content
    // Also pull from the original map's sourcesContent
    if (origMap != null) {
      val om = origMap.nn
      var i  = 0
      while (i < om.sources.length) {
        val content = if (i < om.sourcesContent.length) om.sourcesContent(i) else null
        if (content != null) {
          m(om.sources(i)) = content
        }
        i += 1
      }
    }
    m
  }

  /** Add a mapping from a generated position to an original position.
    *
    * If an original source map is provided, the original position is resolved through the chain.
    *
    * @param source
    *   the source file name
    * @param genLine
    *   generated line (1-based)
    * @param genCol
    *   generated column (0-based)
    * @param origLine
    *   original line (1-based)
    * @param origCol
    *   original column (0-based)
    * @param name
    *   optional identifier name
    */
  def add(
    source:   String,
    genLine:  Int,
    genCol:   Int,
    origLine: Int,
    origCol:  Int,
    name:     String | Null = null
  ): Unit = {
    var actualSource   = source
    var actualOrigLine = origLine
    var actualOrigCol  = origCol
    var actualName     = name

    if (origMap != null) {
      val info = origMap.nn.originalPositionFor(origLine, origCol)
      if (info.source == null) {
        // No mapping in the original map — emit an unmapped segment
        generator.addMapping(
          SourceMapping(
            generatedLine = genLine,
            generatedColumn = genCol,
            source = null,
            originalLine = 0,
            originalColumn = 0,
            name = null
          )
        )
        return // @nowarn
      }
      actualSource = info.source.nn
      actualOrigLine = info.line
      actualOrigCol = info.column
      if (info.name != null) actualName = info.name
    }

    generator.addMapping(
      SourceMapping(
        generatedLine = genLine,
        generatedColumn = genCol,
        source = actualSource,
        originalLine = actualOrigLine,
        originalColumn = actualOrigCol,
        name = actualName
      )
    )
    generator.setSourceContent(actualSource, sourcesContent.getOrElse(actualSource, null))
  }

  /** Get the decoded source map (object form). */
  def getDecoded(): SourceMapData | Null = {
    val map = generator.toDecodedMap()
    clean(map)
  }

  /** Get the encoded source map (JSON-compatible form). */
  def getEncoded(): SourceMapData = {
    val map = generator.toJSON()
    clean(map)
  }

  /** Cleanup (release resources held by the original map consumer). */
  def destroy(): Unit =
    if (origMap != null) origMap.nn.destroy()

  /** Strip null/empty fields from the map. */
  private def clean(map: SourceMapData): SourceMapData = {
    var result = map
    // Strip all-null sourcesContent
    if (result.sourcesContent.nonEmpty && result.sourcesContent.forall(_ == null)) {
      result = result.copy(sourcesContent = scala.collection.mutable.ArrayBuffer.empty)
    }
    result
  }
}
