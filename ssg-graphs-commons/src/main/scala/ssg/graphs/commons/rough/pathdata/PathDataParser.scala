/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * path-data-parser public entry point — Scala 3 port
 *
 * Original source: path-data-parser (src/index.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 93d3fa8
 *
 * Migration notes:
 *   Idiom: the TS barrel re-export
 *     `export { parsePath, serialize } from './parser.js';`
 *     `export { absolutize } from './absolutize.js';`
 *     `export { normalize } from './normalize.js';`
 *   is reproduced with Scala 3 `export` clauses so downstream chips can refer to a
 *   single `PathDataParser` facade, mirroring the original module surface. The
 *   re-exported `Segment` type lives in `Parser.scala` (same package), so it is
 *   directly visible without an explicit re-export.
 */
package ssg
package graphs
package commons
package rough
package pathdata

/** Public entry point for the path-data-parser port (port of `index.ts`). */
object PathDataParser {

  export Parser.{parsePath, serialize}
  export Absolutize.absolutize
  export Normalize.normalize
}
