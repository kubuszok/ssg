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

import java.util.{ HashMap, Map => JMap }

/** Trait for objects that can provide a Liquid-compatible Map representation.
  *
  * This replaces Jackson's ObjectMapper-based conversion. Objects implement toLiquid() to provide their own Map representation for template rendering.
  */
trait LiquidSupport {

  /** Returns a Map representation of this object for use in Liquid templates. */
  def toLiquid(): JMap[String, Any]
}

object LiquidSupport {

  /** Converts an Inspectable (or any object) to LiquidSupport by introspecting public fields and getter methods via Java reflection.
    *
    * On platforms where reflection is unavailable (e.g., Scala.js), this degrades to an empty map. For full control, implement LiquidSupport directly.
    */
  class LiquidSupportFromInspectable(variable: Any) extends LiquidSupport {

    override def toLiquid(): JMap[String, Any] = {
      val result = new HashMap[String, Any]()
      if (variable == null) {
        result
      } else {
        try {
          val clazz = variable.getClass

          // Public fields (including those from Inspectable anonymous classes)
          val fields = clazz.getFields
          var i      = 0
          while (i < fields.length) {
            val f = fields(i)
            result.put(f.getName, f.get(variable))
            i += 1
          }

          // Public getter methods (getX/isX patterns, no-arg, not from Object)
          val methods = clazz.getMethods
          i = 0
          while (i < methods.length) {
            val m = methods(i)
            if (m.getParameterCount == 0 && m.getDeclaringClass != classOf[Object]) {
              val name = m.getName
              if (name.startsWith("get") && name.length > 3 && name != "getClass") {
                val key = name.charAt(3).toLower.toString + name.substring(4)
                if (!result.containsKey(key)) {
                  try result.put(key, m.invoke(variable))
                  catch { case _: Exception => }
                }
              } else if (name.startsWith("is") && name.length > 2 && (m.getReturnType == classOf[Boolean] || m.getReturnType == java.lang.Boolean.TYPE)) {
                val key = name.charAt(2).toLower.toString + name.substring(3)
                if (!result.containsKey(key)) {
                  try result.put(key, m.invoke(variable))
                  catch { case _: Exception => }
                }
              }
            }
            i += 1
          }
        } catch {
          case _: Exception =>
          // Reflection not available (e.g., Scala.js) — return empty map
        }
        result
      }
    }
  }
}
