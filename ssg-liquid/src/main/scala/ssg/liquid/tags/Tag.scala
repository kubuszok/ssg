/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Tag.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 */
package ssg
package liquid
package tags

/** Tags are used for the logic in a template. */
abstract class Tag(_name: String) extends Insertion(_name) {

  def this() = {
    this(null)
  }
}
