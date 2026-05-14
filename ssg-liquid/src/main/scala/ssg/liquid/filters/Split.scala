/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Split.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaced look-behind regex with manual split for JS compatibility
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Split.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import java.util.regex.Pattern

class Split extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    val original = asString(value, context)
    if (original.isEmpty) {
      DataView.from(Vector.empty[DataView])
    } else {
      val delimiter = asString(get(0, params), context)
      if (delimiter.isEmpty) {
        // Empty delimiter: return the whole string as a single-element array
        DataView.from(Vector(DataView.from(original)))
      } else {
        // Split preserving trailing empties (limit = -1), cross-platform (no look-behind)
        val parts = original.split(Pattern.quote(delimiter), -1)
        // The original Java used (?<!^) look-behind to avoid splitting at position 0.
        // Look-behinds aren't supported on Scala Native (re2), so we strip the leading
        // empty string in post-processing instead.
        if (parts.length > 0 && parts(0).isEmpty) {
          DataView.from(parts.drop(1).map(DataView.from(_)).toVector)
        } else {
          DataView.from(parts.map(DataView.from(_)).toVector)
        }
      }
    }
  }
}
