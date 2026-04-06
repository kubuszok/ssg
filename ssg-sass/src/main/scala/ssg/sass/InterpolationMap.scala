/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/interpolation_map.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: interpolation_map.dart -> InterpolationMap.scala
 *   Skeleton: holds the interpolation and target offsets; mapSpan logic lands
 *     in a later pass alongside the parsers that actually build these.
 */
package ssg
package sass

import ssg.sass.ast.sass.Interpolation
import ssg.sass.util.FileSpan

/** A map from locations in a string generated from an [Interpolation] to the
  * original source code in the interpolation.
  */
final class InterpolationMap(
  val interpolation: Interpolation,
  targetOffsets: Iterable[Int]
) {

  /** Location offsets in the generated string. */
  val targetOffsetList: List[Int] = targetOffsets.toList

  private val _expectedLocations = math.max(0, interpolation.contents.length - 1)

  require(
    targetOffsetList.length == _expectedLocations,
    s"InterpolationMap must have ${_expectedLocations} targetOffsets if the interpolation has ${interpolation.contents.length} components."
  )

  /** Maps a span in the generated string back to the source interpolation. */
  def mapSpan(target: FileSpan): FileSpan =
    throw new UnsupportedOperationException("InterpolationMap.mapSpan: not yet implemented in skeleton")
}
