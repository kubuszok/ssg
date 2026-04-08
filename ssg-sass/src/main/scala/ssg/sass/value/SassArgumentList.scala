/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/argument_list.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: argument_list.dart → SassArgumentList.scala
 *   Convention: Extends SassList
 */
package ssg
package sass
package value

import scala.collection.immutable.ListMap

/** A SassScript argument list — a list with keyword arguments. */
final class SassArgumentList(
  contents:              List[Value],
  private val _keywords: ListMap[String, Value],
  separator:             ListSeparator
) extends SassList(contents, separator) {

  private var _wereKeywordsAccessed: Boolean = false

  /** Returns the keyword arguments. Marks them as accessed. */
  def keywords: ListMap[String, Value] = {
    _wereKeywordsAccessed = true
    _keywords
  }

  /** Returns keyword arguments without marking them as accessed. */
  def keywordsWithoutMarking: ListMap[String, Value] = _keywords

  /** Whether keywords were accessed (used for "unknown keyword" error checking). */
  def wereKeywordsAccessed: Boolean = _wereKeywordsAccessed
}
