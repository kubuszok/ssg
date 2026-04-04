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
 */
package ssg
package liquid
package blocks

import ssg.liquid.nodes.{ AtomNode, BlockNode, LNode }
import ssg.liquid.parser.LiquidSupport

import java.util
import java.util.{ ArrayDeque, ArrayList, Collections, HashMap, List => JList, Map => JMap }

import scala.util.boundary
import scala.util.boundary.break

/** Liquid for loop tag.
  *
  * Documentation: https://shopify.dev/docs/themes/liquid/reference/tags/iteration-tags https://shopify.github.io/liquid/tags/iteration/
  */
class For extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = {
    // The first node denotes whether this is a for-tag over an array or a range.
    val array = asBoolean(nodes(0).render(context))

    val id       = asString(nodes(1).render(context), context)
    val tagName  = id + "-" + nodes(5).render(context)
    val reversed = asBoolean(nodes(6).render(context))

    // Each for tag has its own context that keeps track of its own variables (scope)
    val nestedContext = context.newChildContext()

    val rendered =
      if (array) renderArray(id, nestedContext, tagName, reversed, nodes)
      else renderRange(id, nestedContext, tagName, reversed, nodes)

    // When context.renderSettings.raiseExceptionsInStrictMode=false,
    // don't allow nested errors to be lost
    val nestedErrors = nestedContext.errors()
    var i            = 0
    while (i < nestedErrors.size()) {
      context.addError(nestedErrors.get(i))
      i += 1
    }

    rendered
  }

  private def renderArray(id: String, context: TemplateContext, tagName: String, reversed: Boolean, tokens: Array[LNode]): Any = boundary {
    var data: Any = tokens(2).render(context)
    if (AtomNode.isEmpty(data) || "".equals(data)) {
      data = new ArrayList[Any]()
    }

    // attributes start from index 7
    val attributes = getAttributes(7, context, tagName, tokens)

    val from  = attributes.get(For.OFFSET).intValue()
    val limit = attributes.get(For.LIMIT).intValue()

    if (data.isInstanceOf[parser.Inspectable]) {
      val evaluated = context.parser.evaluate(data)
      data = evaluated.toLiquid()
    }
    if (data.isInstanceOf[JMap[?, ?]]) {
      data = mapAsArray(data.asInstanceOf[JMap[?, ?]])
    }
    val array = asArray(data, context)

    val block              = tokens(3)
    val blockIfEmptyOrNull = tokens(4)

    if (array == null || array.length == 0) {
      break(if (blockIfEmptyOrNull == null) null else blockIfEmptyOrNull.render(context))
    }

    val to            = if (limit > -1) Math.min(from + limit, array.length) else array.length
    val effectiveFrom = Math.min(from, array.length)
    val length        = to - effectiveFrom

    var arrayList: JList[Any] = util.Arrays.asList(array*).subList(effectiveFrom, to)
    if (reversed) {
      val listCopy = new ArrayList[Any](arrayList)
      Collections.reverse(listCopy)
      arrayList = listCopy
    }

    // now the current offset and limit is known, so set "continue" lexem
    val registry: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_FOR)
    registry.put(tagName, java.lang.Integer.valueOf(effectiveFrom + length))

    val forLoopDrop = createLoopDropInArrayDeque(context, tagName, length)

    val builder = context.newObjectAppender(arrayList.size())
    try {
      val it = arrayList.iterator()
      while (it.hasNext) {
        val o = it.next()
        context.incrementIterations()
        context.put(id, o)
        val isBreak = renderForLoopBody(context, builder, block.asInstanceOf[BlockNode].getChildren)
        forLoopDrop.increment()
        if (isBreak) {
          break(builder.getResult)
        }
      }
    } finally
      popLoopDropFromArrayDeque(context)

    builder.getResult
  }

  private def createLoopDropInArrayDeque(context: TemplateContext, tagName: String, length: Int): For.ForLoopDrop = {
    val stack       = getParentForloopDropArrayDeque(context)
    val parent      = if (!stack.isEmpty) stack.peek() else null
    val forLoopDrop = new For.ForLoopDrop(tagName, length, parent)
    stack.push(forLoopDrop)
    context.put(For.FORLOOP, forLoopDrop)
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

      if (value != null) {
        if (value.asInstanceOf[AnyRef] eq LValue.CONTINUE) {
          // break from inner loop: equals continue outer loop
          break(false)
        }
        if (value.asInstanceOf[AnyRef] eq LValue.BREAK) {
          isBreak = true
        } else if (isArray(value)) {
          val arr = asArray(value, context)
          var j   = 0
          while (j < arr.length) {
            builder.append(arr(j))
            j += 1
          }
        } else {
          builder.append(asAppendableObject(value, context))
        }
      }
      i += 1
    }
    isBreak
  }

  private def renderRange(id: String, context: TemplateContext, tagName: String, reversed: Boolean, tokens: Array[LNode]): Any = boundary {
    // attributes start from index 7
    val attributes = getAttributes(7, context, tagName, tokens)

    val offset = attributes.get(For.OFFSET).intValue()
    val limit  = attributes.get(For.LIMIT).intValue()

    val block = tokens(4)

    val from        = asNumber(tokens(2).render(context)).intValue()
    val to          = asNumber(tokens(3).render(context)).intValue()
    val effectiveTo =
      if (limit < 0) to
      else Math.min(to, from + limit - 1) // 1 because ranges right is inclusive

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
        context.put(id, java.lang.Integer.valueOf(realI))
        val isBreak = renderForLoopBody(context, builder, block.asInstanceOf[BlockNode].getChildren)
        forLoopDrop.increment()
        if (isBreak) {
          break(builder.getResult)
        }
        i += 1
      }
    } finally
      popLoopDropFromArrayDeque(context)

    builder.getResult
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
      if (For.OFFSET.equals(asString(attribute(0), context)) && (attribute(1).asInstanceOf[AnyRef] eq LValue.CONTINUE)) {
        val offsets: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_FOR)
        val v = offsets.get(tagName)
        if (v != null) {
          attributes.put(For.OFFSET, v.asInstanceOf[Integer])
        }
      } else {
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

  class ForLoopDrop(forName: String, private val length: Int, private val parentloop: ForLoopDrop) extends LiquidSupport {
    private val map = new HashMap[String, Any]()
    private var index: Int = 0

    map.put(NAME, forName)

    override def toLiquid(): JMap[String, Any] = {
      map.put(LENGTH, Integer.valueOf(length))
      map.put(INDEX, Integer.valueOf(index + 1))
      map.put(INDEX0, Integer.valueOf(index))
      map.put(RINDEX, Integer.valueOf(length - index))
      map.put(RINDEX0, Integer.valueOf(length - index - 1))
      val first = index == 0
      val last  = index == length - 1
      map.put(FIRST, java.lang.Boolean.valueOf(first))
      map.put(LAST, java.lang.Boolean.valueOf(last))
      if (parentloop != null) {
        map.put(PARENTLOOP, parentloop)
      }
      map
    }

    def increment(): Unit =
      index += 1
  }
}
