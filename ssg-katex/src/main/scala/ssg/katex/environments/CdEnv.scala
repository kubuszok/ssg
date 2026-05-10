/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Commutative diagram environment (\begin{CD}).
 * Parses arrow labels, creates grid layout.
 *
 * Also defines the internal CD label functions: \\cdleft, \\cdright, \\cdparent.
 *
 * Original source: katex src/environments/cd.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: parseCD -> CdEnv.parseCD
 *   Convention: defineFunction -> FunctionDef.defineFunction
 *   Idiom: TypeScript closures -> Scala closures
 */
package ssg
package katex
package environments

import scala.collection.mutable.ArrayBuffer

import ssg.commons.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML }
import ssg.katex.data.Units
import ssg.katex.functions.{ FunctionDef, FunctionDefSpec, FunctionPropSpec }
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object CdEnv {

  private val cdArrowFunctionName: Map[String, String] = Map(
    ">" -> "\\\\cdrightarrow",
    "<" -> "\\\\cdleftarrow",
    "=" -> "\\\\cdlongequal",
    "A" -> "\\uparrow",
    "V" -> "\\downarrow",
    "|" -> "\\Vert",
    "." -> "no arrow"
  )

  private def newCell(): ParseNodeStyling =
    // Create an empty cell, to be filled below with parse nodes.
    // The parseTree from this module must be constructed like the
    // one created by parseArray(), so an empty CD cell must
    // be a ParseNode<"styling">. And CD is always displaystyle.
    ParseNodeStyling(body = Array.empty, mode = Mode.Math, style = StyleStr.Display)

  private def isStartOfArrow(node: AnyParseNode): Boolean =
    node.nodeType == "textord" && node.asInstanceOf[ParseNodeTextord].text == "@"

  private def isLabelEnd(node: AnyParseNode, endChar: String): Boolean =
    (node.nodeType == "mathord" || node.nodeType == "atom") &&
      node.asInstanceOf[SymbolParseNode].text == endChar

  private def cdArrow(
    arrowChar: String,
    labels:    Array[ParseNodeOrdgroup],
    parser:    Parser
  ): AnyParseNode = {
    // Return a parse tree of an arrow and its labels.
    // This acts in a way similar to a macro expansion.
    val funcName = cdArrowFunctionName(arrowChar)
    funcName match {
      case "\\\\cdrightarrow" | "\\\\cdleftarrow" =>
        parser.callFunction(
          funcName,
          Array(labels(0)),
          Array(Nullable(labels(1)))
        )
      case "\\uparrow" | "\\downarrow" =>
        val leftLabel = parser.callFunction(
          "\\\\cdleft",
          Array(labels(0)),
          Array.empty
        )
        val bareArrow: ParseNodeAtom = ParseNodeAtom(
          family = "rel",
          mode = Mode.Math,
          text = funcName
        )
        val sizedArrow = parser.callFunction("\\Big", Array(bareArrow), Array.empty)
        val rightLabel = parser.callFunction(
          "\\\\cdright",
          Array(labels(1)),
          Array.empty
        )
        val arrowGroup: ParseNodeOrdgroup = ParseNodeOrdgroup(
          mode = Mode.Math,
          body = Array(leftLabel, sizedArrow, rightLabel)
        )
        parser.callFunction("\\\\cdparent", Array(arrowGroup), Array.empty)
      case "\\\\cdlongequal" =>
        parser.callFunction("\\\\cdlongequal", Array.empty, Array.empty)
      case "\\Vert" =>
        val arrow: ParseNodeTextord = ParseNodeTextord(mode = Mode.Math, text = "\\Vert")
        parser.callFunction("\\Big", Array(arrow), Array.empty)
      case _ =>
        ParseNodeTextord(mode = Mode.Math, text = " ")
    }
  }

  def parseCD(parser: Parser): ParseNodeArray = {
    // Get the array's parse nodes with \\ temporarily mapped to \cr.
    val parsedRows = ArrayBuffer.empty[Array[AnyParseNode]]
    parser.gullet.beginGroup()
    parser.gullet.macros.set("\\cr", Nullable(MacroDefinition.StringDef("\\\\\\relax")))
    parser.gullet.beginGroup()
    var continue = true
    while (continue) {
      // Get the parse nodes for the next row.
      parsedRows += parser.parseExpression(false, Nullable("\\\\"))
      parser.gullet.endGroup()
      parser.gullet.beginGroup()
      val next = parser.fetch().text
      if (next == "&" || next == "\\\\") {
        parser.consume()
      } else if (next == "\\end") {
        if (parsedRows.last.length == 0) {
          parsedRows.remove(parsedRows.length - 1) // final row ended in \\
        }
        continue = false
      } else {
        throw new ParseError("Expected \\\\ or \\cr or \\end", parser.nextToken.asInstanceOf[Nullable[SourceLocation.HasLoc]])
      }
    }

    var row  = ArrayBuffer.empty[ParseNodeStyling]
    val body = ArrayBuffer.empty[Array[ParseNodeStyling]]
    body += Array.empty[ParseNodeStyling] // placeholder for first row (removed at end)

    // Loop thru the parse nodes. Collect them into cells and arrows.
    var i = 0
    while (i < parsedRows.length) {
      // Start a new row.
      val rowNodes = parsedRows(i)
      // Create the first cell.
      var cell = newCell()

      var j = 0
      while (j < rowNodes.length) {
        if (!isStartOfArrow(rowNodes(j))) {
          // If a parseNode is not an arrow, it goes into a cell.
          cell.body = cell.body :+ rowNodes(j)
        } else {
          // Parse node j is an "@", the start of an arrow.
          // Before starting on the arrow, push the cell into `row`.
          row += cell

          // Now collect parseNodes into an arrow.
          // The character after "@" defines the arrow type.
          j += 1
          val arrowChar = ParseNode.assertSymbolNodeType(Nullable(rowNodes(j))).text

          // Create two empty label nodes. We may or may not use them.
          val labels = Array(
            ParseNodeOrdgroup(mode = Mode.Math, body = Array.empty),
            ParseNodeOrdgroup(mode = Mode.Math, body = Array.empty)
          )

          // Process the arrow.
          if ("=|.".contains(arrowChar)) {
            // Three "arrows", ``@=`, `@|`, and `@.`, do not take labels.
            // Do nothing here.
          } else if ("<>AV".contains(arrowChar)) {
            // Four arrows, `@>>>`, `@<<<`, `@AAA`, and `@VVV`, each take
            // two optional labels. E.g. the right-point arrow syntax is
            // really:  @>{optional label}>{optional label}>
            // Collect parseNodes into labels.
            var labelNum = 0
            while (labelNum < 2) {
              var inLabel = true
              var k       = j + 1
              while (k < rowNodes.length)
                if (isLabelEnd(rowNodes(k), arrowChar)) {
                  inLabel = false
                  j = k
                  k = rowNodes.length // break inner loop
                } else {
                  if (isStartOfArrow(rowNodes(k))) {
                    throw new ParseError("Missing a " + arrowChar +
                                           " character to complete a CD arrow.",
                                         Nullable(rowNodes(k))
                    )
                  }
                  labels(labelNum).body = labels(labelNum).body :+ rowNodes(k)
                  k += 1
                }
              if (inLabel) {
                // isLabelEnd never returned a true.
                throw new ParseError("Missing a " + arrowChar +
                                       " character to complete a CD arrow.",
                                     Nullable(rowNodes(j))
                )
              }
              labelNum += 1
            }
          } else {
            throw new ParseError("Expected one of \"<>AV=|.\" after @", Nullable(rowNodes(j)))
          }

          // Now join the arrow to its labels.
          val arrow: AnyParseNode = cdArrow(arrowChar, labels, parser)

          // Wrap the arrow in  ParseNode<"styling">.
          // This is done to match parseArray() behavior.
          val wrappedArrow: ParseNodeStyling = ParseNodeStyling(
            body = Array(arrow),
            mode = Mode.Math,
            style = StyleStr.Display // CD is always displaystyle.
          )
          row += wrappedArrow
          // In CD's syntax, cells are implicit. That is, everything that
          // is not an arrow gets collected into a cell. So create an empty
          // cell now. It will collect upcoming parseNodes.
          cell = newCell()
        }
        j += 1
      }
      if (i % 2 == 0) {
        // Even-numbered rows consist of: cell, arrow, cell, arrow, ... cell
        // The last cell is not yet pushed into `row`, so:
        row += cell
      } else {
        // Odd-numbered rows consist of: vert arrow, empty cell, ... vert arrow
        // Remove the empty cell that was placed at the beginning of `row`.
        if (row.nonEmpty) {
          row.remove(0)
        }
      }
      body += row.toArray
      row = ArrayBuffer.empty[ParseNodeStyling]
      i += 1
    }
    // The original JS code pushes an empty row at the end of each iteration.
    // Since body starts as [row_ref] where row_ref is filled in iteration 0,
    // and each iteration ends with row=[]; body.push(row), the net effect
    // is body having N+1 entries where the last is always empty.
    // Add the trailing empty row to match.
    body += Array.empty[ParseNodeStyling]

    // End row group
    parser.gullet.endGroup()
    // End array group defining \\
    parser.gullet.endGroup()

    // Remove the initial null placeholder
    body.remove(0)

    // define column separation.
    val numCols = if (body.nonEmpty) body(0).length else 0
    val cols    = Array.fill(numCols)(
      AlignSpec.Align(
        align = "c",
        pregap = 0.25, // CD package sets \enskip between columns.
        postgap = 0.25 // So pre and post each get half an \enskip, i.e. 0.25em.
      )
    )

    // Convert body from Array[Array[ParseNodeStyling]] to Array[Array[AnyParseNode]]
    val arrayBody: Array[Array[AnyParseNode]] = body.toArray.map(_.map(x => x: AnyParseNode))

    ParseNodeArray(
      mode = Mode.Math,
      body = arrayBody,
      arraystretch = 1,
      addJot = Nullable(true),
      rowGaps = Array(Nullable.Null),
      cols = Nullable(cols),
      colSeparationType = Nullable("CD"),
      hLinesBeforeRow = Array.fill(arrayBody.length + 1)(Array.empty[Boolean])
    )
  }

  // The functions below are not available for general use.
  // They are here only for internal use by the {CD} environment in placing labels
  // next to vertical arrows.

  // We don't need any such functions for horizontal arrows because we can reuse
  // the functionality that already exists for extensible arrows.

  def register(): Unit = {
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "cdlabel",
        names = Array("\\\\cdleft", "\\\\cdright"),
        props = FunctionPropSpec(
          numArgs = 1
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeCdlabel(
            mode = parser.mode,
            side = context.funcName.substring(4),
            label = args(0)
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g          = group.asInstanceOf[ParseNodeCdlabel]
          val opts       = options.asInstanceOf[Options]
          val newOptions = opts.havingStyle(opts.style.sup())
          val label      = BuildCommon.wrapFragment(
            BuildHTML.buildGroup(Nullable(g.label), newOptions, Nullable(opts)),
            opts
          )
          label.classes += ("cd-label-" + g.side)
          label.style = label.style.copy(bottom = Nullable(Units.makeEm(0.8 - label.depth)))
          // Zero out label height & depth, so vertical align of arrow is set
          // by the arrow height, not by the label.
          label.height = 0
          label.depth = 0
          label
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g     = group.asInstanceOf[ParseNodeCdlabel]
          val opts  = options.asInstanceOf[Options]
          var label = new MathNode("mrow", ArrayBuffer(BuildMathML.buildGroup(g.label, opts)))
          label = new MathNode("mpadded", ArrayBuffer(label))
          label.setAttribute("width", "0")
          if (g.side == "left") {
            label.setAttribute("lspace", "-1width")
          }
          // We have to guess at vertical alignment. We know the arrow is 1.8em tall,
          // But we don't know the height or depth of the label.
          label.setAttribute("voffset", "0.7em")
          label = new MathNode("mstyle", ArrayBuffer(label))
          label.setAttribute("displaystyle", "false")
          label.setAttribute("scriptlevel", "1")
          label
        }
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "cdlabelparent",
        names = Array("\\\\cdparent"),
        props = FunctionPropSpec(
          numArgs = 1
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeCdlabelparent(
            mode = parser.mode,
            fragment = args(0)
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeCdlabelparent]
          val opts = options.asInstanceOf[Options]
          // Wrap the vertical arrow and its labels.
          // The parent gets position: relative. The child gets position: absolute.
          // So CSS can locate the label correctly.
          val parent = BuildCommon.wrapFragment(
            BuildHTML.buildGroup(Nullable(g.fragment), opts),
            opts
          )
          parent.classes += "cd-vert-arrow"
          parent
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeCdlabelparent]
          val opts = options.asInstanceOf[Options]
          new MathNode("mrow", ArrayBuffer(BuildMathML.buildGroup(g.fragment, opts)))
        }
      )
    )
  }
}
