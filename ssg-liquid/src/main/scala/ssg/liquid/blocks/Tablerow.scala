/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Tablerow.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/blocks/Tablerow.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package blocks

import ssg.data.DataView
import ssg.liquid.nodes.LNode

import java.util.{ HashMap, Map => JMap }

import scala.collection.immutable.VectorMap

class Tablerow extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): DataView = {
    val valueName  = asString(nodes(0).render(context), context)
    var collection = asArray(nodes(1).render(context), context)
    val block      = nodes(2)
    val attributes = getAttributes(collection, 3, context, nodes)

    val cols   = attributes.get(Tablerow.COLS).intValue()
    val limit  = attributes.get(Tablerow.LIMIT).intValue()
    val offset = attributes.get(Tablerow.OFFSET).intValue()

    if (offset != 0) {
      if (collection.nonEmpty && offset < collection.size) {
        collection = collection.drop(offset)
      } else {
        collection = Vector.empty
      }
    }

    val nestedContext    = context.newChildContext()
    val total            = Math.min(collection.size, limit)
    val tablerowloopDrop = new Tablerow.TablerowloopDrop(total, cols)
    nestedContext.put(Tablerow.TABLEROWLOOP, tablerowloopDrop.toDataView())

    val builder = context.newObjectAppender(total * 5)
    if (total == 0) {
      builder.append("<tr class=\"row1\">\n</tr>\n")
    } else {
      var i = 0
      var c = 1
      var r = 0
      while (i < total) {
        context.incrementIterations()
        nestedContext.put(valueName, collection(i))
        if (c == 1) {
          r += 1
          builder.append("<tr class=\"row")
          builder.append(r)
          builder.append("\">")
          builder.append(if (r == 1) "\n" else "")
        }

        builder.append("<td class=\"col")
        builder.append(c)
        builder.append("\">")
        builder.append(asAppendableObject(block.render(nestedContext), context))
        builder.append("</td>")

        if (c == cols || i == total - 1) {
          builder.append("</tr>\n")
          c = 0
        }
        tablerowloopDrop.increment()
        nestedContext.put(Tablerow.TABLEROWLOOP, tablerowloopDrop.toDataView())
        i += 1
        c += 1
      }
    }

    nestedContext.remove(Tablerow.TABLEROWLOOP)
    nestedContext.remove(valueName)

    DataView.from(builder.getResult.toString)
  }

  private def getAttributes(collection: Vector[DataView], fromIndex: Int, context: TemplateContext, tokens: Array[LNode]): JMap[String, Integer] = {
    val attributes = new HashMap[String, Integer]()
    attributes.put(Tablerow.COLS, Integer.valueOf(collection.size))
    attributes.put(Tablerow.LIMIT, Integer.valueOf(Int.MaxValue))
    attributes.put(Tablerow.OFFSET, Integer.valueOf(0))

    var i = fromIndex
    while (i < tokens.length) {
      val attribute = asArray(tokens(i).render(context), context)
      if (attribute.size >= 2) {
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

object Tablerow {
  private val COLS         = "cols"
  private val LIMIT        = "limit"
  private val OFFSET       = "offset"
  private val TABLEROWLOOP = "tablerowloop"
  private val LENGTH       = "length"
  private val INDEX        = "index"
  private val INDEX0       = "index0"
  private val RINDEX       = "rindex"
  private val RINDEX0      = "rindex0"
  private val FIRST        = "first"
  private val LAST         = "last"
  private val COL          = "col"
  private val COL0         = "col0"
  private val COL_FIRST    = "col_first"
  private val COL_LAST     = "col_last"
  private val ROW          = "row"

  class TablerowloopDrop(private val length: Long, private val cols: Long) {
    private var row:   Long = 1
    private var col:   Long = 1
    private var index: Long = 0

    def toDataView(): DataView = {
      var m = VectorMap.empty[String, DataView]
      m = m.updated(LENGTH, DataView.from(length))
      m = m.updated(INDEX0, DataView.from(index))
      m = m.updated(INDEX, DataView.from(index + 1))
      m = m.updated(RINDEX0, DataView.from(length - index - 1))
      m = m.updated(RINDEX, DataView.from(length - index))
      m = m.updated(FIRST, DataView.from(index == 0))
      m = m.updated(LAST, DataView.from(index == length - 1))
      m = m.updated(COL0, DataView.from(col - 1))
      m = m.updated(COL, DataView.from(col))
      m = m.updated(COL_FIRST, DataView.from(col == 1))
      m = m.updated(COL_LAST, DataView.from(col == cols))
      m = m.updated(ROW, DataView.from(row))
      DataView.from(m)
    }

    def increment(): Unit = {
      index += 1
      if (col == cols) {
        col = 1
        row += 1
      } else {
        col += 1
      }
    }
  }
}
