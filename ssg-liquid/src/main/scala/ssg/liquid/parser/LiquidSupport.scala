/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/parser/LiquidSupport.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.parser → ssg.liquid.parser
 *   Convention: Java interface → Scala trait
 *   Idiom: Replaced Jackson-based LiquidSupportFromInspectable with trait-based conversion
 *
 * NOTE: This is an initial stub. Full implementation in Phase 4.
 */
package ssg
package liquid
package parser

import java.util.{ Map => JMap }

/** Trait for objects that can provide a Liquid-compatible Map representation.
  *
  * This replaces Jackson's ObjectMapper-based conversion. Objects implement toLiquid() to provide their own Map representation for template rendering.
  */
trait LiquidSupport {

  /** Returns a Map representation of this object for use in Liquid templates. */
  def toLiquid(): JMap[String, Any]
}
