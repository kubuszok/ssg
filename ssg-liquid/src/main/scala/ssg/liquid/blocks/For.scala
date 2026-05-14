/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/For.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 *   Idiom: ForLoopDrop inner class → companion object class
 *   Idiom: ArrayDeque → java.util.ArrayDeque
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/blocks/For.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package blocks

import ssg.data.DataView
import ssg.liquid.nodes.{ AtomNode, BlockNode, LNode }

import java.util.{ ArrayDeque, HashMap, List => JList, Map => JMap }

import scala.collection.immutable.VectorMap
import scala.util.boundary
import scala.util.boundary.break

/** Liquid for loop tag. */
class For extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): DataView = {
    val array = asBoolean(nodes(0).render(context))

    val id       = asString(nodes(1).render(context), context)
    val tagName  = id + "-" + nodes(5).render(context).toString
    val reversed = asBoolean(nodes(6).render(context))

    val nestedContext = context.newChildContext()

    val rendered =
      if (array) renderArray(id, nestedContext, tagName, reversed, nodes)
      else renderRange(id, nestedContext, tagName, reversed, nodes)

    val nestedErrors = nestedContext.errors()
    var i            = 0
    while (i < nestedErrors.size()) {
      context.addError(nestedErrors.get(i))
      i += 1
    }

    rendered
  }

  private def renderArray(id: String, context: TemplateContext, tagName: String, reversed: Boolean, tokens: Array[LNode]): DataView = boundary {
    var data: DataView = tokens(2).render(context)
    if (AtomNode.isEmpty(data) || data.toString == "") {
      data = DataView.from(Vector.empty[DataView])
    }

    val attributes = getAttributes(7, context, tagName, tokens)

    val from  = attributes.get(For.OFFSET).intValue()
    val limit = attributes.get(For.LIMIT).intValue()

    // Convert map to array of key-value pairs
    if (isMap(data)) {
      data = DataView.from(mapAsVector(asMap(data)))
    }

    val array = asArray(data, context)

    val block              = tokens(3)
    val blockIfEmptyOrNull = tokens(4)

    if (array.isEmpty) {
      break(if (blockIfEmptyOrNull == null) DataView.nil else blockIfEmptyOrNull.render(context))
    }

    val to            = if (limit > -1) Math.min(from + limit, array.size) else array.size
    val effectiveFrom = Math.min(from, array.size)
    val length        = to - effectiveFrom

    var arrayList = array.slice(effectiveFrom, to)
    if (reversed) {
      arrayList = arrayList.reverse
    }

    val registry: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_FOR)
    registry.put(tagName, java.lang.Integer.valueOf(effectiveFrom + length))

    val forLoopDrop = createLoopDropInArrayDeque(context, tagName, length)

    val builder = context.newObjectAppender(arrayList.size)
    try
      for (o <- arrayList) {
        context.incrementIterations()
        context.put(id, o)
        val isBreak = renderForLoopBody(context, builder, block.asInstanceOf[BlockNode].getChildren)
        forLoopDrop.increment()
        context.put(For.FORLOOP, forLoopDrop.toDataView())
        if (isBreak) {
          break(DataView.from(builder.getResult.toString))
        }
      }
    finally
      popLoopDropFromArrayDeque(context)

    DataView.from(builder.getResult.toString)
  }

  private def createLoopDropInArrayDeque(context: TemplateContext, tagName: String, length: Int): For.ForLoopDrop = {
    val stack       = getParentForloopDropArrayDeque(context)
    val parent      = if (!stack.isEmpty) stack.peek() else null
    val forLoopDrop = new For.ForLoopDrop(tagName, length, parent)
    stack.push(forLoopDrop)
    context.put(For.FORLOOP, forLoopDrop.toDataView())
    forLoopDrop
  }

  def popLoopDropFromArrayDeque(context: TemplateContext): Unit = {
    val stack = getParentForloopDropArrayDeque(context)
    if (!stack.isEmpty) {
      stack.pop()
    }
  }

  private def renderForLoopBody(
    context:  TemplateContext,
    builder:  RenderTransformer.ObjectAppender.Controller,
    children: JList[LNode]
  ): Boolean = boundary {
    var isBreak = false
    var i       = 0
    while (i < children.size() && !isBreak) {
      val node  = children.get(i)
      val value = node.render(context)

      if (!value.isNull) {
        if (value eq DataView.CONTINUE) {
          break(false)
        }
        if (value eq DataView.BREAK) {
          isBreak = true
        } else if (isArray(value)) {
          val vec = asArray(value, context)
          vec.foreach(dv => builder.append(dv.toString))
        } else {
          builder.append(asAppendableObject(value, context))
        }
      }
      i += 1
    }
    isBreak
  }

  private def renderRange(id: String, context: TemplateContext, tagName: String, reversed: Boolean, tokens: Array[LNode]): DataView = boundary {
    val attributes = getAttributes(7, context, tagName, tokens)

    val offset = attributes.get(For.OFFSET).intValue()
    val limit  = attributes.get(For.LIMIT).intValue()

    val block = tokens(4)

    val from        = asNumber(tokens(2).render(context)).intValue()
    val to          = asNumber(tokens(3).render(context)).intValue()
    val effectiveTo =
      if (limit < 0) to
      else Math.min(to, from + limit - 1)

    val length = to - from

    val forLoopDrop = createLoopDropInArrayDeque(context, tagName, length)

    val builder = context.newObjectAppender(effectiveTo - from + offset + 1)
    try {
      var i = from + offset
      while (i <= effectiveTo) {
        val realI =
          if (reversed) effectiveTo - (i - from - offset)
          else i

        context.incrementIterations()
        context.put(id, DataView.from(realI))
        val isBreak = renderForLoopBody(context, builder, block.asInstanceOf[BlockNode].getChildren)
        forLoopDrop.increment()
        context.put(For.FORLOOP, forLoopDrop.toDataView())
        if (isBreak) {
          break(DataView.from(builder.getResult.toString))
        }
        i += 1
      }
    } finally
      popLoopDropFromArrayDeque(context)

    DataView.from(builder.getResult.toString)
  }

  @SuppressWarnings(Array("unchecked"))
  private def getParentForloopDropArrayDeque(context: TemplateContext): ArrayDeque[For.ForLoopDrop] = {
    val registry: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_FOR_STACK)
    val stack = registry.get(TemplateContext.REGISTRY_FOR_STACK)
    if (stack == null) {
      val newArrayDeque = new ArrayDeque[For.ForLoopDrop]()
      registry.put(TemplateContext.REGISTRY_FOR_STACK, newArrayDeque)
      newArrayDeque
    } else {
      stack.asInstanceOf[ArrayDeque[For.ForLoopDrop]]
    }
  }

  private def getAttributes(fromIndex: Int, context: TemplateContext, tagName: String, tokens: Array[LNode]): JMap[String, Integer] = {
    val attributes = new HashMap[String, Integer]()
    attributes.put(For.OFFSET, Integer.valueOf(0))
    attributes.put(For.LIMIT, Integer.valueOf(-1))

    var i = fromIndex
    while (i < tokens.length) {
      val token     = tokens(i)
      val attribute = asArray(token.render(context), context)
      // offset:continue
      if (attribute.size >= 2 && For.OFFSET.equals(asString(attribute(0), context)) && (attribute(1) eq DataView.CONTINUE)) {
        val offsets: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_FOR)
        val v = offsets.get(tagName)
        if (v != null) {
          attributes.put(For.OFFSET, v.asInstanceOf[Integer])
        }
      } else if (attribute.size >= 2) {
        try
          attributes.put(asString(attribute(0), context), Integer.valueOf(asNumber(attribute(1)).intValue()))
        catch {
          case _: Exception => // just ignore incorrect attributes
        }
      }
      i += 1
    }

    attributes
  }
}

object For {
  private val OFFSET     = "offset"
  private val LIMIT      = "limit"
  val FORLOOP            = "forloop"
  private val LENGTH     = "length"
  private val INDEX      = "index"
  private val INDEX0     = "index0"
  private val RINDEX     = "rindex"
  private val RINDEX0    = "rindex0"
  private val FIRST      = "first"
  private val LAST       = "last"
  private val NAME       = "name"
  private val PARENTLOOP = "parentloop"

  class ForLoopDrop(forName: String, private val length: Int, private val parentloop: ForLoopDrop) {
    private var index: Int = 0

    def toDataView(): DataView = {
      var m = VectorMap.empty[String, DataView]
      m = m.updated(NAME, DataView.from(forName))
      m = m.updated(LENGTH, DataView.from(length))
      m = m.updated(INDEX, DataView.from(index + 1))
      m = m.updated(INDEX0, DataView.from(index))
      m = m.updated(RINDEX, DataView.from(length - index))
      m = m.updated(RINDEX0, DataView.from(length - index - 1))
      val first = index == 0
      val last  = index == length - 1
      m = m.updated(FIRST, DataView.from(first))
      m = m.updated(LAST, DataView.from(last))
      if (parentloop != null) {
        m = m.updated(PARENTLOOP, parentloop.toDataView())
      }
      DataView.from(m)
    }

    def increment(): Unit =
      index += 1
  }
}
