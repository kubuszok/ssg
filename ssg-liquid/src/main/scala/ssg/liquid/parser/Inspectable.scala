/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/parser/Inspectable.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.parser → ssg.liquid.parser
 *   Convention: Java marker interface → Scala marker trait
 */
package ssg
package liquid
package parser

/** Marker trait for objects that can be introspected for template rendering.
  *
  * Objects implementing this trait will be converted to Map[String, Any] for use in Liquid templates.
  */
trait Inspectable
