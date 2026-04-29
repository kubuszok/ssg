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
 *   Convention: Dart final class -> Scala final class
 *   Idiom: _mapLocation returns FileLocation | FileSpan as (FileLocation | FileSpan);
 *          Dart SourceSpanFormatException -> SassFormatException;
 *          Dart MultiSourceSpanFormatException -> MultiSpanSassFormatException;
 *          Dart identical(file, ...) -> eq reference equality via AnyRef
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/interpolation_map.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass

import scala.util.boundary
import scala.util.boundary.break

import ssg.sass.ast.sass.{ Expression, Interpolation }
import ssg.sass.util.{ CharCode, FileLocation, FileSpan }

/** A map from locations in a string generated from an [Interpolation] to the original source code in the interpolation.
  */
final class InterpolationMap(
  val interpolation: Interpolation,
  targetOffsets:     Iterable[Int]
) {

  /** Location offsets in the generated string. */
  val targetOffsetList: List[Int] = targetOffsets.toList

  private val _expectedLocations = math.max(0, interpolation.contents.length - 1)

  require(
    targetOffsetList.length == _expectedLocations,
    s"InterpolationMap must have ${_expectedLocations} targetOffsets if the interpolation has ${interpolation.contents.length} components."
  )

  /** Maps [error]'s span in the string generated from this interpolation to its original source.
    *
    * Returns [error] if its span has already been mapped.
    */
  def mapException(error: SassFormatException): SassFormatException = {
    val target = error.span

    if (interpolation.contents.isEmpty) {
      if (_isMapped(target)) return error
      return SassFormatException(
        error.sassMessage,
        interpolation.span,
        error.loadedUrls
      )
    }

    val source = mapSpan(target)
    if (source eq target) return error

    val startIndex = _indexInContents(target.start)
    val endIndex   = _indexInContents(target.end)

    val hasExpression = interpolation.contents.slice(startIndex, endIndex + 1).exists(_.isInstanceOf[Expression])

    if (!hasExpression) {
      SassFormatException(error.sassMessage, source, error.loadedUrls)
    } else {
      MultiSpanSassFormatException(
        error.sassMessage,
        source,
        "",
        Map(target -> "error in interpolated output"),
        error.loadedUrls
      )
    }
  }

  /** Maps a span in the string generated from this interpolation to its original source.
    *
    * Returns [target] as-is if it's already been mapped.
    */
  def mapSpan(target: FileSpan): FileSpan = {
    if (_isMapped(target)) return target

    val startMapped = _mapLocation(target.start)
    val endMapped   = _mapLocation(target.end)

    (startMapped, endMapped) match {
      case (start: FileSpan, end: FileSpan) =>
        start.expand(end)
      case (start: FileSpan, end: FileLocation) =>
        interpolation.span.file.span(
          _expandInterpolationSpanLeft(start.start),
          end.offset
        )
      case (start: FileLocation, end: FileSpan) =>
        interpolation.span.file.span(
          start.offset,
          _expandInterpolationSpanRight(end.end)
        )
      case (start: FileLocation, end: FileLocation) =>
        interpolation.span.file.span(
          start.offset,
          end.offset
        )
      case _ => throw new AssertionError("[BUG] Unreachable")
    }
  }

  /** Returns whether [span] has already been mapped by this mapper. */
  private def _isMapped(span: FileSpan): Boolean =
    (span.file: AnyRef) eq (interpolation.span.file: AnyRef)

  /** Maps a location in the string generated from this interpolation to its original source.
    *
    * If [target] points to an un-interpolated portion of the original string, this will return the corresponding [FileLocation]. If it points to text generated from interpolation, this will return
    * the full [FileSpan] for that interpolated expression.
    */
  private def _mapLocation(target: FileLocation): FileLocation | FileSpan = {
    if (interpolation.contents.isEmpty) return interpolation.span

    val index = _indexInContents(target)
    interpolation.contents(index) match {
      case chunk: Expression =>
        return chunk.span
      case _ => // fall through to string mapping
    }

    val previousLocation =
      if (index == 0) interpolation.span.start
      else
        interpolation.span.file.location(
          _expandInterpolationSpanRight(
            interpolation.contents(index - 1).asInstanceOf[Expression].span.end
          )
        )
    val offsetInString =
      target.offset - (if (index == 0) 0 else targetOffsetList(index - 1))

    // This produces slightly incorrect mappings if there are _unnecessary_
    // escapes in the source file, but that's unlikely enough that it's probably
    // not worth doing a reparse here to fix it.
    previousLocation.file.location(
      previousLocation.offset + offsetInString
    )
  }

  /** Return the index in [interpolation.contents] at which [target] points. */
  private def _indexInContents(target: FileLocation): Int =
    boundary[Int] {
      var i = 0
      while (i < targetOffsetList.length) {
        if (target.offset < targetOffsetList(i)) break(i)
        i += 1
      }
      interpolation.contents.length - 1
    }

  /** Given the start of a [FileSpan] covering an interpolated expression, returns the offset of the interpolation's opening `#`.
    *
    * Note that this can be tricked by a `#{` that appears within a single-line comment before the expression, but since it's only used for error reporting that's probably fine.
    */
  private def _expandInterpolationSpanLeft(start: FileLocation): Int = {
    val source = start.file.text
    var i      = start.offset - 1
    boundary[Int] {
      while (i >= 0) {
        val prev = source.charAt(i).toInt
        i -= 1
        if (prev == CharCode.$lbrace) {
          if (source.charAt(i).toInt == CharCode.$hash) {
            // found the opening #{
            break(i)
          }
        } else if (prev == CharCode.$slash) {
          val second = source.charAt(i).toInt
          i -= 1
          if (second == CharCode.$asterisk) {
            // skip backwards through a /* ... */ comment
            boundary {
              while (true) {
                var char = source.charAt(i).toInt
                i -= 1
                if (char != CharCode.$asterisk) {
                  // continue
                } else {
                  // consume consecutive asterisks
                  while (char == CharCode.$asterisk) {
                    char = source.charAt(i).toInt
                    i -= 1
                  }
                  if (char == CharCode.$slash) break(())
                }
              }
            }
          }
        }
      }
      i
    }
  }

  /** Given the end of a [FileSpan] covering an interpolated expression, returns the offset of the interpolation's closing `}`.
    */
  private def _expandInterpolationSpanRight(end: FileLocation): Int = {
    val source = end.file.text
    var i      = end.offset
    boundary[Int] {
      while (i < source.length) {
        val next = source.charAt(i).toInt
        i += 1
        if (next == CharCode.$rbrace) break(i)
        if (next == CharCode.$slash) {
          val second = source.charAt(i).toInt
          i += 1
          if (second == CharCode.$slash) {
            // skip forward through a // single-line comment
            while (!CharCode.isNewline(source.charAt(i).toInt)) i += 1
          } else if (second == CharCode.$asterisk) {
            // skip forward through a /* ... */ comment
            boundary {
              while (true) {
                var char = source.charAt(i).toInt
                i += 1
                if (char != CharCode.$asterisk) {
                  // continue
                } else {
                  // consume consecutive asterisks
                  while (char == CharCode.$asterisk) {
                    char = source.charAt(i).toInt
                    i += 1
                  }
                  if (char == CharCode.$slash) break(())
                }
              }
            }
          }
        }
      }
      i
    }
  }
}
