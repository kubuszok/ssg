/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Tablerow.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 *   Idiom: TablerowloopDrop inner class → companion object class
 */
package ssg
package liquid
package blocks

import ssg.liquid.nodes.LNode
import ssg.liquid.parser.LiquidSupport

import java.util
import java.util.{ HashMap, Map => JMap }

/** HTML table iteration with <tr> and <td> elements. */
class Tablerow extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = {
    val valueName  = asString(nodes(0).render(context), context)
    var collection = asArray(nodes(1).render(context), context)
    val block      = nodes(2)
    val attributes = getAttributes(collection, 3, context, nodes)

    val cols   = attributes.get(Tablerow.COLS).intValue()
    val limit  = attributes.get(Tablerow.LIMIT).intValue()
    val offset = attributes.get(Tablerow.OFFSET).intValue()

    if (offset != 0) {
      if (collection.length > 0 && offset < collection.length) {
        collection = util.Arrays.copyOfRange(collection.asInstanceOf[Array[AnyRef]], offset, collection.length).asInstanceOf[Array[Any]]
      } else {
        collection = Array.empty[Any]
      }
    }

    val nestedContext    = context.newChildContext()
    val total            = Math.min(collection.length, limit)
    val tablerowloopDrop = new Tablerow.TablerowloopDrop(total, cols)
    nestedContext.put(Tablerow.TABLEROWLOOP, tablerowloopDrop)

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
        i += 1
        c += 1
      }
    }

    nestedContext.remove(Tablerow.TABLEROWLOOP)
    nestedContext.remove(valueName)

    builder.getResult
  }

  private def getAttributes(collection: Array[Any], fromIndex: Int, context: TemplateContext, tokens: Array[LNode]): JMap[String, Integer] = {
    val attributes = new HashMap[String, Integer]()
    attributes.put(Tablerow.COLS, Integer.valueOf(collection.length))
    attributes.put(Tablerow.LIMIT, Integer.valueOf(Int.MaxValue))
    attributes.put(Tablerow.OFFSET, Integer.valueOf(0))

    var i = fromIndex
    while (i < tokens.length) {
      val attribute = asArray(tokens(i).render(context), context)
      try
        attributes.put(asString(attribute(0), context), Integer.valueOf(asNumber(attribute(1)).intValue()))
      catch {
        case _: Exception => // just ignore incorrect attributes
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

  class TablerowloopDrop(private val length: Long, private val cols: Long) extends LiquidSupport {
    private var row:   Long = 1
    private var col:   Long = 1
    private var index: Long = 0
    private val tablerowloopContext = new HashMap[String, Any]()

    override def toLiquid(): JMap[String, Any] = {
      tablerowloopContext.put(LENGTH, java.lang.Long.valueOf(length))
      tablerowloopContext.put(INDEX0, java.lang.Long.valueOf(index))
      tablerowloopContext.put(INDEX, java.lang.Long.valueOf(index + 1))
      tablerowloopContext.put(RINDEX0, java.lang.Long.valueOf(length - index - 1))
      tablerowloopContext.put(RINDEX, java.lang.Long.valueOf(length - index))
      tablerowloopContext.put(FIRST, java.lang.Boolean.valueOf(index == 0))
      tablerowloopContext.put(LAST, java.lang.Boolean.valueOf(index == length - 1))
      tablerowloopContext.put(COL0, java.lang.Long.valueOf(col - 1))
      tablerowloopContext.put(COL, java.lang.Long.valueOf(col))
      tablerowloopContext.put(COL_FIRST, java.lang.Boolean.valueOf(col == 1))
      tablerowloopContext.put(COL_LAST, java.lang.Boolean.valueOf(col == cols))
      tablerowloopContext.put(ROW, java.lang.Long.valueOf(row))
      tablerowloopContext
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
