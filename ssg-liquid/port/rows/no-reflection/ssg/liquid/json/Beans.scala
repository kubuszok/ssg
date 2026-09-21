/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the jackson-databind bean serialisation reached from
 *   src/main/java/liqp/parser/LiquidSupport.java's objectToMap, which liqp uses for Inspectable
 *   values and for its EAGER evaluate mode
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala.js and Scala Native rows, where there is no way to ask an object what
 *     members it has. This is the same file the JVM row answers; only the answer differs.
 *   Refusal: raised BY NAME rather than answered with an empty map, because an empty map is a
 *     template that silently renders nothing.
 */
package ssg
package liquid
package json

import java.util.{ Map => JMap }

/** Reads an object's properties — which this platform cannot do. */
object Beans {

  def toMap(value: Object): JMap[String, Object] =
    throw new UnsupportedOperationException(
      "cannot convert " + value.getClass.getName + " to a liquid data map: this platform reads no object's FIELDS. " +
        "Java did it with jackson's bean reflection, which neither Scala.js nor Scala Native has; the JVM build of " +
        "this library still does. Pass a java.util.Map, or implement ssg.liquid.parser.LiquidSupport and answer " +
        "with toLiquid()."
    )
}
