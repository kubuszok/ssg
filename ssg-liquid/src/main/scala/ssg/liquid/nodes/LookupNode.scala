/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/LookupNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Idiom: Inner classes → companion object inner classes
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/LookupNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import ssg.data.DataView
import ssg.liquid.exceptions.VariableNotExistException

import java.util.ArrayList

import scala.collection.immutable.VectorMap
import scala.util.boundary
import scala.util.boundary.break

class LookupNode(private val id: String) extends LNode {

  private val indexes: ArrayList[LookupNode.Indexable] = new ArrayList[LookupNode.Indexable]()

  def add(indexable: LookupNode.Indexable): Unit =
    indexes.add(indexable)

  override def render(context: TemplateContext): DataView = {
    // Check if there's a [var] lookup, AST: ^(LOOKUP Id["@var"])
    val realId = if (id.startsWith("@")) {
      context.get(id.substring(1)).toString
    } else {
      id
    }

    var value: DataView = DataView.nil

    if (context.containsKey(realId)) {
      value = context.get(realId)
    }
    if (value.isNull) {
      val environmentMap = context.getEnvironmentMap
      if (environmentMap.containsKey(realId)) {
        value = environmentMap.get(realId)
      }
    }

    var i = 0
    while (i < indexes.size()) {
      value = indexes.get(i).get(value, context)
      i += 1
    }

    if (value.isNull && context.parser.strictVariables) {
      val e = new VariableNotExistException(getVariableName)
      context.addError(e)
      if (context.getErrorMode == TemplateParser.ErrorMode.STRICT) {
        throw e
      }
    }

    value
  }

  private def getVariableName: String = {
    val sb = new StringBuilder(id)
    var i  = 0
    while (i < indexes.size()) {
      sb.append(indexes.get(i).toString)
      i += 1
    }
    sb.toString()
  }

  override def toString: String = {
    val builder = new StringBuilder()
    builder.append(id)
    var i = 0
    while (i < indexes.size()) {
      builder.append(indexes.get(i).toString)
      i += 1
    }
    builder.toString()
  }
}

object LookupNode {

  /** Interface for property/index access on Liquid values. */
  trait Indexable {
    def get(value: DataView, context: TemplateContext): DataView
  }

  /** Hash (property) access: value.property */
  class Hash(private val hash: String) extends Indexable {

    override def get(value: DataView, context: TemplateContext): DataView = boundary {
      if (value.isNull) {
        break(DataView.nil)
      }

      value.view match {
        case m: VectorMap[?, ?] =>
          val map = m.asInstanceOf[VectorMap[String, DataView]]
          if (hash == "size") {
            if (map.contains(hash)) break(map(hash))
            else break(DataView.from(map.size))
          } else {
            map.get(hash) match {
              case Some(inner) => break(inner)
              case None        => break(DataView.nil)
            }
          }
        case v: Vector[?] =>
          val vec = v.asInstanceOf[Vector[DataView]]
          hash match {
            case "size"  => break(DataView.from(vec.size))
            case "first" => break(if (vec.isEmpty) DataView.nil else vec.head)
            case "last"  => break(if (vec.isEmpty) DataView.nil else vec.last)
            case _       => break(DataView.nil)
          }
        case s: String =>
          if (hash == "size") break(DataView.from(s.length()))
          else break(DataView.nil)
        case _ =>
          break(DataView.nil)
      }
    }

    override def toString: String = s".$hash"
  }

  /** Index access: value[expression] */
  class Index(private val expression: LNode, private val text: String) extends Indexable {

    override def get(value: DataView, context: TemplateContext): DataView = boundary {
      if (value.isNull) {
        break(DataView.nil)
      }

      val key = expression.render(context)

      // Check if key is a number for index access
      if (!key.isNull) {
        key.view match {
          case _: (Short | Int | Long | Float | Double | java.math.BigDecimal) =>
            val n     = key.view.asInstanceOf[Number]
            var index = n.intValue()
            value.view match {
              case v: Vector[?] =>
                val vec  = v.asInstanceOf[Vector[DataView]]
                val size = vec.size
                if (index >= size) break(DataView.nil)
                if (index < 0) {
                  index = size + index
                  if (index < 0) break(DataView.nil)
                }
                break(vec(index))
              case _ =>
                break(DataView.nil)
            }
          case _ => // fall through to hash-style access
        }
      }

      // String key — hash-style access on maps
      value.view match {
        case _: Vector[?] => DataView.nil // arrays don't support string key access
        case _ =>
          val hashStr = key.toString
          new Hash(hashStr).get(value, context)
      }
    }

    override def toString: String = s"[$text]"
  }
}
