/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data types for Source Map V3 (ECMA-426).
 *
 * Original source: @jridgewell/source-map (used by terser)
 * Original license: MIT
 */
package ssg
package js
package sourcemap

import scala.collection.mutable.ArrayBuffer

/** A single mapping from generated position to original position. */
final case class SourceMapping(
  generatedLine:   Int,
  generatedColumn: Int,
  source:          String | Null = null,
  originalLine:    Int = 0,
  originalColumn:  Int = 0,
  name:            String | Null = null
)

/** The complete source map data structure (V3). */
final case class SourceMapData(
  version:        Int = 3,
  file:           String | Null = null,
  sourceRoot:     String | Null = null,
  sources:        ArrayBuffer[String] = ArrayBuffer.empty,
  sourcesContent: ArrayBuffer[String | Null] = ArrayBuffer.empty,
  names:          ArrayBuffer[String] = ArrayBuffer.empty,
  mappings:       String = ""
)

/** Result of looking up an original position in a source map. */
final case class OriginalPosition(
  source: String | Null,
  line:   Int,
  column: Int,
  name:   String | Null
)

/** Options for creating a SourceMap. */
final case class SourceMapOptions(
  file:  String | Null = null,
  root:  String | Null = null,
  orig:  SourceMapData | Null = null,
  files: Map[String, String] = Map.empty
)
