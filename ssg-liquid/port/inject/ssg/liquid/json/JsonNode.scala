/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the jackson-databind JsonNode and ObjectNode that
 *   src/main/java/liqp/filters/where/LiquidWhereImpl.java compares for equality and
 *   src/main/java/liqp/parser/LiquidSupport.java converts through
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: jackson-databind is a JVM-only artifact.
 *   Convention: a node is a value, and two nodes are equal when their values are — which is the
 *     one thing liqp asks of a JsonNode (`Objects.equals(jsonNode, jsonProperty)`).
 */
package ssg
package liquid
package json

import java.util.{ LinkedHashMap => JLinkedHashMap, Map => JMap }

/** A value in JSON's own shape, compared by value. */
class JsonNode(val value: Object) {

  override def equals(other: Any): Boolean =
    other match {
      case that: JsonNode => java.util.Objects.equals(this.value, that.value)
      case _              => false
    }

  override def hashCode(): Int = java.util.Objects.hashCode(value)

  override def toString: String = Json.write(value)
}

/** A JSON object, which is the one node shape liqp names by type. */
class ObjectNode(val fields: JMap[String, Object]) extends JsonNode(fields.asInstanceOf[Object]) {

  def this() = this(new JLinkedHashMap[String, Object]())
}
