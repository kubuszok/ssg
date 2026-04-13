/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js stub for LiquidSupportFromInspectable.
 * Reflection is not available on JS, so this returns an empty map.
 * For full functionality, implement LiquidSupport directly on your objects.
 */
package ssg
package liquid
package parser

import java.util.{ HashMap, Map => JMap }

object LiquidSupportPlatform {

  /** Stub implementation that returns an empty map on Scala.js.
    *
    * Reflection is not available on JS. For objects to be usable in Liquid templates, implement LiquidSupport directly.
    */
  class LiquidSupportFromInspectable(variable: Any) extends LiquidSupport {

    override def toLiquid(): JMap[String, Any] = new HashMap[String, Any]()
  }
}
