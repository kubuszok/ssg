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

import ssg.liquid.exceptions.VariableNotExistException
import ssg.liquid.parser.{ Inspectable, LiquidSupport }

import java.util.{ ArrayList, Collection => JCollection, List => JList, Map => JMap }

import scala.util.boundary
import scala.util.boundary.break

class LookupNode(private val id: String) extends LNode {

  private val indexes: ArrayList[LookupNode.Indexable] = new ArrayList[LookupNode.Indexable]()

  def add(indexable: LookupNode.Indexable): Unit =
    indexes.add(indexable)

  override def render(context: TemplateContext): Any = {
    var value: Any = null

    // Check if there's a [var] lookup, AST: ^(LOOKUP Id["@var"])
    val realId = if (id.startsWith("@")) {
      String.valueOf(context.get(id.substring(1)))
    } else {
      id
    }

    if (context.containsKey(realId)) {
      value = context.get(realId)
    }
    if (value == null) {
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

    if (value == null && context.parser.strictVariables) {
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
    def get(value: Any, context: TemplateContext): Any
  }

  /** Hash (property) access: value.property */
  class Hash(private val hash: String) extends Indexable {

    override def get(value: Any, context: TemplateContext): Any = boundary {
      if (value == null) {
        break(null)
      }

      if (hash == "size") {
        value match {
          case col: JCollection[?] => break(col.size())
          case map: JMap[?, ?]     =>
            break(if (map.containsKey(hash)) map.get(hash) else map.size())
          case insp: Inspectable =>
            val evaluated = context.parser.evaluate(insp)
            val map       = evaluated.toLiquid()
            break(if (map.containsKey(hash)) map.get(hash) else map.size())
          case arr: Array[?]     => break(arr.length)
          case cs:  CharSequence => break(cs.length())
          case _ => // fall through
        }
      } else if (hash == "first") {
        value match {
          case list: JList[?] => break(if (list.isEmpty) null else list.get(0))
          case arr:  Array[?] => break(if (arr.length == 0) null else arr(0))
          case _ => // fall through
        }
      } else if (hash == "last") {
        value match {
          case list: JList[?] => break(if (list.isEmpty) null else list.get(list.size() - 1))
          case arr:  Array[?] => break(if (arr.length == 0) null else arr(arr.length - 1))
          case _ => // fall through
        }
      }

      value match {
        case map: JMap[?, ?] =>
          map.get(hash)
        case ls: LiquidSupport =>
          ls.toLiquid().get(hash)
        case insp: Inspectable =>
          val evaluated = context.parser.evaluate(insp)
          val map       = evaluated.toLiquid()
          map.get(hash)
        case ctx: TemplateContext =>
          ctx.get(hash)
        case _ =>
          null
      }
    }

    override def toString: String = s".$hash"
  }

  /** Index access: value[expression] */
  class Index(private val expression: LNode, private val text: String) extends Indexable {

    override def get(value: Any, context: TemplateContext): Any = boundary {
      if (value == null) {
        break(null)
      }

      val key = expression.render(context)

      key match {
        case n: Number =>
          var index = n.intValue()
          value match {
            case arr: Array[?] =>
              val size = arr.length
              if (index >= size) break(null)
              if (index < 0) {
                index = size + index
                if (index < 0) break(null)
              }
              arr(index)
            case list: JList[?] =>
              val size = list.size()
              if (index >= size) break(null)
              if (index < 0) {
                index = size + index
                if (index < 0) break(null)
              }
              list.get(index)
            case col: JCollection[?] =>
              val size = col.size()
              if (index >= size) break(null)
              if (index < 0) {
                index = size + index
                if (index < 0) break(null)
              }
              var i     = 0
              var found = false
              var result: Any = null
              val it = col.iterator()
              while (it.hasNext && !found) {
                val obj = it.next()
                if (i == index) {
                  result = obj
                  found = true
                }
                i += 1
              }
              result
            case _ =>
              null
          }
        case _ =>
          // hashes only work on maps, not on arrays/lists
          value match {
            case _: Array[?] | _: JList[?] => null
            case _                         =>
              val hash = String.valueOf(key)
              new Hash(hash).get(value, context)
          }
      }
    }

    override def toString: String = s"[$text]"
  }
}
