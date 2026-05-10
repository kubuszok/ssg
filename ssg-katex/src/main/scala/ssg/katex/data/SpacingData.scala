/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Describes spaces between different classes of atoms.
 *
 * Original source: katex src/spacingData.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

/** Describes spaces between different classes of atoms.
  */
object SpacingData {

  private val thinspace:   Measurement = Measurement(3, "mu")
  private val mediumspace: Measurement = Measurement(4, "mu")
  private val thickspace:  Measurement = Measurement(5, "mu")

  // Making the type below exact with all optional fields doesn't work due to
  // - https://github.com/facebook/flow/issues/4582
  // - https://github.com/facebook/flow/issues/5688
  // However, since *all* fields are optional, $Shape<> works as suggested in 5688
  // above.

  /** Spacing relationships for display and text styles.
    *
    * The outer key is the left atom type, the inner key is the right atom type, and the value is the measurement of space to insert between them.
    */
  val spacings: Map[String, Map[String, Measurement]] = Map(
    "mord" -> Map(
      "mop" -> thinspace,
      "mbin" -> mediumspace,
      "mrel" -> thickspace,
      "minner" -> thinspace
    ),
    "mop" -> Map(
      "mord" -> thinspace,
      "mop" -> thinspace,
      "mrel" -> thickspace,
      "minner" -> thinspace
    ),
    "mbin" -> Map(
      "mord" -> mediumspace,
      "mop" -> mediumspace,
      "mopen" -> mediumspace,
      "minner" -> mediumspace
    ),
    "mrel" -> Map(
      "mord" -> thickspace,
      "mop" -> thickspace,
      "mopen" -> thickspace,
      "minner" -> thickspace
    ),
    "mopen" -> Map.empty,
    "mclose" -> Map(
      "mop" -> thinspace,
      "mbin" -> mediumspace,
      "mrel" -> thickspace,
      "minner" -> thinspace
    ),
    "mpunct" -> Map(
      "mord" -> thinspace,
      "mop" -> thinspace,
      "mrel" -> thickspace,
      "mopen" -> thinspace,
      "mclose" -> thinspace,
      "mpunct" -> thinspace,
      "minner" -> thinspace
    ),
    "minner" -> Map(
      "mord" -> thinspace,
      "mop" -> thinspace,
      "mbin" -> mediumspace,
      "mrel" -> thickspace,
      "mopen" -> thinspace,
      "mpunct" -> thinspace,
      "minner" -> thinspace
    )
  )

  /** Spacing relationships for script and scriptscript styles.
    */
  val tightSpacings: Map[String, Map[String, Measurement]] = Map(
    "mord" -> Map(
      "mop" -> thinspace
    ),
    "mop" -> Map(
      "mord" -> thinspace,
      "mop" -> thinspace
    ),
    "mbin" -> Map.empty,
    "mrel" -> Map.empty,
    "mopen" -> Map.empty,
    "mclose" -> Map(
      "mop" -> thinspace
    ),
    "mpunct" -> Map.empty,
    "minner" -> Map(
      "mop" -> thinspace
    )
  )
}
