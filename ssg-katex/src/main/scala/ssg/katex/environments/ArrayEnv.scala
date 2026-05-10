/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Array environment implementations for KaTeX.
 *
 * Handles \begin{array}, \begin{matrix}, \begin{pmatrix}, etc.
 * Column alignment parsing, \hline handling, cell building.
 *
 * Original source: katex src/environments/array.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: defineEnvironment -> EnvironmentDef.defineEnvironment
 *   Renames: defineFunction -> FunctionDef.defineFunction
 *   Renames: defineMacro -> MacroDef.defineMacro
 *   Convention: TypeScript Record -> Map, AlignSpec -> enum in ParseNode.scala
 *   Idiom: TypeScript optional chaining -> explicit checks
 */
package ssg
package katex
package environments

import scala.collection.mutable.ArrayBuffer

import ssg.commons.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, VListElemAndShift, VListParam }
import ssg.katex.data.{ Measurement, Units }
import ssg.katex.functions.{ FunctionDef, FunctionDefSpec, FunctionPropSpec, HtmlBuilder, MathMLBuilder }
import ssg.katex.parse._
import ssg.katex.tree.{ DomSpan, HtmlDomNode, MathDomNode, MathNode }

/** Configuration options for parseArray.
  */
final case class ParseArrayConfig(
  hskipBeforeAndAfter: Nullable[Boolean] = Nullable.Null,
  addJot:              Nullable[Boolean] = Nullable.Null,
  cols:                Nullable[Array[AlignSpec]] = Nullable.Null,
  arraystretch:        Nullable[Double] = Nullable.Null,
  colSeparationType:   Nullable[ColSeparationType] = Nullable.Null,
  autoTag:             Nullable[Boolean] = Nullable.Null, // null/undefined = no tags, Some(true) = auto, Some(false) = manual
  singleRow:           Boolean = false,
  emptySingleRow:      Boolean = false,
  maxNumCols:          Nullable[Int] = Nullable.Null,
  leqno:               Nullable[Boolean] = Nullable.Null
)

object ArrayEnv {

  // Helper functions
  private def getHLines(parser: Parser): Array[Boolean] = {
    // Return an array. The array length = number of hlines.
    // Each element in the array tells if the line is dashed.
    val hlineInfo = ArrayBuffer.empty[Boolean]
    parser.consumeSpaces()
    var nxt = parser.fetch().text
    if (nxt == "\\relax") { // \relax is an artifact of the \cr macro below
      parser.consume()
      parser.consumeSpaces()
      nxt = parser.fetch().text
    }
    while (nxt == "\\hline" || nxt == "\\hdashline") {
      parser.consume()
      hlineInfo += (nxt == "\\hdashline")
      parser.consumeSpaces()
      nxt = parser.fetch().text
    }
    hlineInfo.toArray
  }

  private def validateAmsEnvironmentContext(context: EnvContext): Unit = {
    val settings = context.parser.asInstanceOf[Parser].settings
    if (!settings.displayMode) {
      throw new ParseError(
        s"{${context.envName}} can be used only in" +
          " display mode."
      )
    }
  }

  private val gatherEnvironments: Set[String] = Set("gather", "gather*")

  // autoTag (an argument to parseArray) can be one of three values:
  // * undefined: Regular (not-top-level) array; no tags on each row
  // * true: Automatic equation numbering, overridable by \tag
  // * false: Tags allowed on each row, but no automatic numbering
  // This function *doesn't* work with the "split" environment name.
  private def getAutoTag(name: String): Nullable[Boolean] =
    if (!name.contains("ed")) {
      Nullable(!name.contains("*"))
    } else {
      // return undefined
      Nullable.Null
    }

  /** Parse the body of the environment, with rows delimited by \\ and columns delimited by &, and create a nested list in row-major order with one group per cell. If given an optional argument style
    * ("text", "display", etc.), then each cell is cast into that style.
    */
  def parseArray(
    parser: Parser,
    config: ParseArrayConfig,
    style:  Nullable[StyleStr]
  ): ParseNodeArray = {
    parser.gullet.beginGroup()
    if (!config.singleRow) {
      // \cr is equivalent to \\ without the optional size argument (see below)
      // TODO: provide helpful error when \cr is used outside array environment
      parser.gullet.macros.set("\\cr", Nullable(MacroDefinition.StringDef("\\\\\\relax")))
    }

    // Get current arraystretch if it's not set by the environment
    val arraystretch: Double = config.arraystretch.getOrElse {
      val stretch = parser.gullet.expandMacroAsText("\\arraystretch")
      if (stretch.isEmpty) {
        // Default \arraystretch from lttab.dtx
        1.0
      } else {
        val parsed = stretch.get.toDoubleOption.getOrElse(Double.NaN)
        if (parsed.isNaN || parsed < 0) {
          throw new ParseError(s"Invalid \\arraystretch: ${stretch.get}")
        }
        parsed
      }
    }

    // Start group for first cell
    parser.gullet.beginGroup()

    var row  = ArrayBuffer.empty[AnyParseNode]
    val body = ArrayBuffer.empty[Array[AnyParseNode]]
    body += Array.empty[AnyParseNode] // placeholder for first row (removed at end)
    val rowGaps         = ArrayBuffer.empty[Nullable[Measurement]]
    val hLinesBeforeRow = ArrayBuffer.empty[Array[Boolean]]
    val tags: Nullable[ArrayBuffer[Either[Boolean, Array[AnyParseNode]]]] =
      if (config.autoTag.isDefined) Nullable(ArrayBuffer.empty) else Nullable.Null

    // amsmath uses \global\@eqnswtrue and \global\@eqnswfalse to represent
    // whether this row should have an equation number.  Simulate this with
    // a \@eqnsw macro set to 1 or 0.
    def beginRow(): Unit =
      if (config.autoTag.isDefined && config.autoTag.get) {
        parser.gullet.macros.set("\\@eqnsw", Nullable(MacroDefinition.StringDef("1")), global = true)
      }
    def endRow(): Unit =
      tags.foreach { t =>
        if (parser.gullet.macros.get("\\df@tag").isDefined) {
          t += Right(parser.subparse(Array(new Token("\\df@tag"))))
          parser.gullet.macros.set("\\df@tag", Nullable.Null, global = true)
        } else {
          val eqnswIsOne = parser.gullet.macros.get("\\@eqnsw") match {
            case v if v.isDefined =>
              v.get match {
                case MacroDefinition.StringDef(s) => s == "1"
                case _                            => false
              }
            case _ => false
          }
          t += Left(config.autoTag.isDefined && config.autoTag.get && eqnswIsOne)
        }
      }
    beginRow()

    // Test for \hline at the top of the array.
    hLinesBeforeRow += getHLines(parser)

    var continue = true
    while (continue) {
      // Parse each cell in its own group (namespace)
      val cellBody = parser.parseExpression(false, if (config.singleRow) Nullable("\\end") else Nullable("\\\\"))
      parser.gullet.endGroup()
      parser.gullet.beginGroup()
      var cell: AnyParseNode = ParseNodeOrdgroup(
        mode = parser.mode,
        body = cellBody
      )
      if (style.isDefined) {
        cell = ParseNodeStyling(
          mode = parser.mode,
          style = style.get,
          body = Array(cell)
        )
      }
      row += cell
      val next = parser.fetch().text
      if (next == "&") {
        if (config.maxNumCols.isDefined && row.length == config.maxNumCols.get) {
          if (config.singleRow || config.colSeparationType.isDefined) {
            // {equation} or {split}
            throw new ParseError("Too many tab characters: &", parser.nextToken.asInstanceOf[Nullable[SourceLocation.HasLoc]])
          } else {
            // {array} environment
            parser.settings.reportNonstrict("textEnv",
                                            "Too few columns " +
                                              "specified in the {array} column argument."
            )
          }
        }
        parser.consume()
      } else if (next == "\\end") {
        endRow()
        // Arrays terminate newlines with `\crcr` which consumes a `\cr` if
        // the last line is empty.  However, AMS environments keep the
        // empty row if it's the only one.
        // NOTE: Currently, `cell` is the last item added into `row`.
        if (
          row.length == 1 && cell.nodeType == "styling" &&
          cell.asInstanceOf[ParseNodeStyling].body.length == 1 &&
          cell.asInstanceOf[ParseNodeStyling].body(0).nodeType == "ordgroup" &&
          cell.asInstanceOf[ParseNodeStyling].body(0).asInstanceOf[ParseNodeOrdgroup].body.length == 0 &&
          (body.length > 1 || !config.emptySingleRow)
        ) {
          // Note: body.length > 1 because we have the first null placeholder + any earlier rows
          // Actually body has the null placeholder at index 0, plus any rows. But in JS
          // body starts with [row] then body.pop() removes the last element. Here we
          // need to handle this differently since we use body.length > 1 (i.e. more than the current row).
          // Since we add to `body` at the end, not at the start, let's check differently.
          // In JS: body = [row]; then row is being built. body.length > 1 means there were
          // previous rows before this one. We haven't added the current row yet here.
          // Our body has null placeholder at 0, so body.length > 1 means body.length >= 2
          // which means there's at least 1 completed row + the placeholder.
          // So body.length > 1 correctly maps to "body has previous rows"
          // Don't add the empty row (by not adding the row to body below)
          // The pop() in the original just removes the last pushed row. Since we haven't
          // pushed the current row yet, this is equivalent to just not adding it.
        } else {
          body += row.toArray
        }
        if (hLinesBeforeRow.length < body.length) {
          // body has the null placeholder at 0, so actual body length is body.length - 1
          // but hLinesBeforeRow needs body.length entries...
          // In original: hLinesBeforeRow.length < body.length + 1
          // After popping, body might have shrunk by 1
          // Let's add empty hlines to fill up
        }
        // Add trailing hlines
        while (hLinesBeforeRow.length < body.length + 1)
          hLinesBeforeRow += Array.empty[Boolean]
        continue = false
      } else if (next == "\\\\") {
        parser.consume()
        var size: Nullable[ParseNodeSize] = Nullable.Null
        // \def\Let@{\let\\\math@cr}
        // \def\math@cr{...\math@cr@}
        // \def\math@cr@{\new@ifnextchar[\math@cr@@{\math@cr@@[\z@]}}
        // \def\math@cr@@[#1]{...\math@cr@@@...}
        // \def\math@cr@@@{\cr}
        if (parser.gullet.future().text != " ") {
          size = parser.parseSizeGroup(true)
        }
        rowGaps += size.map(_.value)
        endRow()

        // check for \hline(s) following the row separator
        hLinesBeforeRow += getHLines(parser)

        body += row.toArray
        row = ArrayBuffer.empty[AnyParseNode]
        beginRow()
      } else {
        throw new ParseError("Expected & or \\\\ or \\cr or \\end", parser.nextToken.asInstanceOf[Nullable[SourceLocation.HasLoc]])
      }
    }

    // End cell group
    parser.gullet.endGroup()
    // End array group defining \cr
    parser.gullet.endGroup()

    // Remove the null placeholder at index 0
    body.remove(0)

    // Convert tags
    val tagsArray: Nullable[Array[Either[Boolean, Array[AnyParseNode]]]] =
      tags.map(_.toArray)

    ParseNodeArray(
      mode = parser.mode,
      addJot = config.addJot,
      arraystretch = arraystretch,
      body = body.toArray,
      cols = config.cols,
      rowGaps = rowGaps.toArray,
      hskipBeforeAndAfter = config.hskipBeforeAndAfter,
      hLinesBeforeRow = hLinesBeforeRow.toArray,
      colSeparationType = config.colSeparationType,
      tags = tagsArray,
      leqno = config.leqno
    )
  }

  // Decides on a style for cells in an array according to whether the given
  // environment name starts with the letter 'd'.
  private def dCellStyle(envName: String): StyleStr =
    if (envName.startsWith("d")) {
      StyleStr.Display
    } else {
      StyleStr.TextStyle
    }

  private val htmlBuilder: HtmlBuilder = (group, options) => {
    val g               = group.asInstanceOf[ParseNodeArray]
    val opts            = options.asInstanceOf[Options]
    var r               = 0
    var c               = 0
    val nr              = g.body.length
    val hLinesBeforeRow = g.hLinesBeforeRow
    var nc              = 0
    val bodyArr         = new Array[ArrayWithRowInfo](nr)
    val hlines          = ArrayBuffer.empty[HlineInfo]
    val ruleThickness   = Math.max(
      // From LaTeX \showthe\arrayrulewidth. Equals 0.04 em.
      opts.fontMetrics().arrayRuleWidth,
      opts.minRuleThickness // User override.
    )

    // Horizontal spacing
    val pt          = 1.0 / opts.fontMetrics().ptPerEm
    var arraycolsep = 5.0 * pt // default value, i.e. \arraycolsep in article.cls
    if (g.colSeparationType.isDefined && g.colSeparationType.get == "small") {
      // We're in a {smallmatrix}. Default column space is \thickspace,
      // i.e. 5/18em = 0.2778em, per amsmath.dtx for {smallmatrix}.
      // But that needs adjustment because LaTeX applies \scriptstyle to the
      // entire array, including the colspace, but this function applies
      // \scriptstyle only inside each element.
      val localMultiplier = opts.havingStyle(Style.SCRIPT).sizeMultiplier
      arraycolsep = 0.2778 * (localMultiplier / opts.sizeMultiplier)
    }

    // Vertical spacing
    val baselineskip: Double =
      if (g.colSeparationType.isDefined && g.colSeparationType.get == "CD") {
        Units.calculateSize(Measurement(3, "ex"), opts)
      } else {
        12 * pt // see size10.clo
      }
    // Default \jot from ltmath.dtx
    // TODO(edemaine): allow overriding \jot via \setlength (#687)
    val jot           = 3 * pt
    val arrayskip     = g.arraystretch * baselineskip
    val arstrutHeight = 0.7 * arrayskip // \strutbox in ltfsstrc.dtx and
    val arstrutDepth  = 0.3 * arrayskip // \@arstrutbox in lttab.dtx

    var totalHeight = 0.0

    // Set a position for \hline(s) at the top of the array, if any.
    def setHLinePos(hlinesInGap: Array[Boolean]): Unit = {
      var i = 0
      while (i < hlinesInGap.length) {
        if (i > 0) {
          totalHeight += 0.25
        }
        hlines += HlineInfo(pos = totalHeight, isDashed = hlinesInGap(i))
        i += 1
      }
    }
    setHLinePos(hLinesBeforeRow(0))

    r = 0
    while (r < g.body.length) {
      val inrow  = g.body(r)
      var height = arstrutHeight // \@array adds an \@arstrut
      var depth  = arstrutDepth // to each row (via the template)

      if (nc < inrow.length) {
        nc = inrow.length
      }

      val outrow = new Array[HtmlDomNode](inrow.length)
      c = 0
      while (c < inrow.length) {
        val elt = BuildHTML.buildGroup(Nullable(inrow(c)), opts)
        if (depth < elt.depth) {
          depth = elt.depth
        }
        if (height < elt.height) {
          height = elt.height
        }
        outrow(c) = elt
        c += 1
      }

      val rowGap: Nullable[Measurement] = if (r < g.rowGaps.length) g.rowGaps(r) else Nullable.Null
      var gap = 0.0
      if (rowGap.isDefined) {
        gap = Units.calculateSize(rowGap.get, opts)
        if (gap > 0) { // \@argarraycr
          gap += arstrutDepth
          if (depth < gap) {
            depth = gap // \@xargarraycr
          }
          gap = 0
        }
      }
      // In AMS multiline environments such as aligned and gathered, rows
      // correspond to lines that have additional \jot added between lines
      // via \openup.
      // We simulate this by adding \jot depth to each row except the last.
      if (g.addJot.isDefined && g.addJot.get && r < g.body.length - 1) {
        depth += jot
      }

      val rowInfo = ArrayWithRowInfo(outrow, height, depth, 0.0)
      totalHeight += height
      rowInfo.pos = totalHeight
      totalHeight += depth + gap // \@yargarraycr
      bodyArr(r) = rowInfo

      // Set a position for \hline(s), if any.
      if (r + 1 < hLinesBeforeRow.length) {
        setHLinePos(hLinesBeforeRow(r + 1))
      }
      r += 1
    }

    val offset = totalHeight / 2 + opts.fontMetrics().axisHeight
    val colDescriptions: Array[AlignSpec] = g.cols.getOrElse(Array.empty)
    val cols = ArrayBuffer.empty[HtmlDomNode]
    var colSep: DomSpan = BuildCommon.makeSpan() // overwritten below before use
    var colDescrNum = 0
    val tagSpans    = ArrayBuffer.empty[VListElemAndShift]

    if (
      g.tags.isDefined && g.tags.get.exists {
        case Left(b)  => b
        case Right(_) => true
      }
    ) {
      // An environment with manual tags and/or automatic equation numbers.
      // Create node(s), the latter of which trigger CSS counter increment.
      r = 0
      while (r < nr) {
        val rw    = bodyArr(r)
        val shift = rw.pos - offset
        val tag   = g.tags.get(r)
        val tagSpan: DomSpan = tag match {
          case Left(true) => // automatic numbering
            BuildCommon.makeSpan(ArrayBuffer("eqn-num"), ArrayBuffer.empty, Nullable(opts))
          case Left(false) =>
            // \nonumber/\notag or starred environment
            BuildCommon.makeSpan(ArrayBuffer.empty, ArrayBuffer.empty, Nullable(opts))
          case Right(tagNodes) => // manual \tag
            BuildCommon.makeSpan(ArrayBuffer.empty, BuildHTML.buildExpression(tagNodes, opts, true), Nullable(opts))
        }
        tagSpan.depth = rw.depth
        tagSpan.height = rw.height
        tagSpans += VListElemAndShift(elem = tagSpan, shift = shift)
        r += 1
      }
    }

    c = 0
    colDescrNum = 0
    // Continue while either there are more columns or more column
    // descriptions, so trailing separators don't get lost.
    while (c < nc || colDescrNum < colDescriptions.length) {
      val colDescr: Nullable[AlignSpec] =
        if (colDescrNum < colDescriptions.length) Nullable(colDescriptions(colDescrNum))
        else Nullable.Null

      var currentDescr   = colDescr
      var firstSeparator = true
      while (currentDescr.isDefined && currentDescr.get.isInstanceOf[AlignSpec.Separator]) {
        val sep = currentDescr.get.asInstanceOf[AlignSpec.Separator]
        // If there is more than one separator in a row, add a space
        // between them.
        if (!firstSeparator) {
          colSep = BuildCommon.makeSpan(ArrayBuffer("arraycolsep"), ArrayBuffer.empty)
          colSep.style = colSep.style.copy(width = Nullable(Units.makeEm(opts.fontMetrics().doubleRuleSep)))
          cols += colSep
        }

        if (sep.separator == "|" || sep.separator == ":") {
          val lineType  = if (sep.separator == "|") "solid" else "dashed"
          val separator = BuildCommon.makeSpan(ArrayBuffer("vertical-separator"), ArrayBuffer.empty, Nullable(opts))
          separator.style = separator.style.copy(
            height = Nullable(Units.makeEm(totalHeight)),
            borderRightWidth = Nullable(Units.makeEm(ruleThickness)),
            borderRightStyle = Nullable(lineType),
            margin = Nullable(s"0 ${Units.makeEm(-ruleThickness / 2)}")
          )
          val vertShift = totalHeight - offset
          if (vertShift != 0) {
            separator.style = separator.style.copy(verticalAlign = Nullable(Units.makeEm(-vertShift)))
          }

          cols += separator
        } else {
          throw new ParseError("Invalid separator type: " + sep.separator)
        }

        colDescrNum += 1
        currentDescr =
          if (colDescrNum < colDescriptions.length) Nullable(colDescriptions(colDescrNum))
          else Nullable.Null
        firstSeparator = false
      }

      if (c >= nc) {
        c += 1
        colDescrNum += 1
        // continue — skip to next iteration
      } else {
        var sepwidth = 0.0
        if (c > 0 || (g.hskipBeforeAndAfter.isDefined && g.hskipBeforeAndAfter.get)) {
          sepwidth = currentDescr
            .map {
              case AlignSpec.Align(_, pregap, _) => pregap
              case _                             => arraycolsep
            }
            .getOrElse(arraycolsep)
          if (sepwidth != 0) {
            colSep = BuildCommon.makeSpan(ArrayBuffer("arraycolsep"), ArrayBuffer.empty)
            colSep.style = colSep.style.copy(width = Nullable(Units.makeEm(sepwidth)))
            cols += colSep
          }
        }

        val colElems = ArrayBuffer.empty[VListElemAndShift]
        r = 0
        while (r < nr) {
          val rowInfo = bodyArr(r)
          val elem    = if (c < rowInfo.elems.length) rowInfo.elems(c) else null
          if (elem != null) { // @nowarn — checking array element
            val shift = rowInfo.pos - offset
            elem.depth = rowInfo.depth
            elem.height = rowInfo.height
            colElems += VListElemAndShift(elem = elem, shift = shift)
          }
          r += 1
        }

        val colVList = BuildCommon.makeVList(
          VListParam.IndividualShift(colElems.toArray),
          opts
        )
        val colAlign = currentDescr
          .map {
            case AlignSpec.Align(align, _, _) => align
            case _                            => "c"
          }
          .getOrElse("c")
        val colSpan = BuildCommon.makeSpan(
          ArrayBuffer("col-align-" + colAlign),
          ArrayBuffer[HtmlDomNode](colVList)
        )
        cols += colSpan

        if (c < nc - 1 || (g.hskipBeforeAndAfter.isDefined && g.hskipBeforeAndAfter.get)) {
          sepwidth = currentDescr
            .map {
              case AlignSpec.Align(_, _, postgap) => postgap
              case _                              => arraycolsep
            }
            .getOrElse(arraycolsep)
          if (sepwidth != 0) {
            colSep = BuildCommon.makeSpan(ArrayBuffer("arraycolsep"), ArrayBuffer.empty)
            colSep.style = colSep.style.copy(width = Nullable(Units.makeEm(sepwidth)))
            cols += colSep
          }
        }
        c += 1
        colDescrNum += 1
      }
    }

    var tableBody: HtmlDomNode = BuildCommon.makeSpan(ArrayBuffer("mtable"), cols)

    // Add \hline(s), if any.
    if (hlines.nonEmpty) {
      val line       = BuildCommon.makeLineSpan("hline", opts, Nullable(ruleThickness))
      val dashes     = BuildCommon.makeLineSpan("hdashline", opts, Nullable(ruleThickness))
      val vListElems = ArrayBuffer[VListElemAndShift](
        VListElemAndShift(elem = tableBody, shift = 0)
      )
      while (hlines.nonEmpty) {
        val hline     = hlines.remove(hlines.length - 1)
        val lineShift = hline.pos - offset
        if (hline.isDashed) {
          vListElems += VListElemAndShift(elem = dashes, shift = lineShift)
        } else {
          vListElems += VListElemAndShift(elem = line, shift = lineShift)
        }
      }

      tableBody = BuildCommon.makeVList(
        VListParam.IndividualShift(vListElems.toArray),
        opts
      )
    }

    if (tagSpans.isEmpty) {
      BuildCommon.makeSpan(ArrayBuffer("mord"), ArrayBuffer(tableBody), Nullable(opts))
    } else {
      val eqnNumCol = BuildCommon.makeVList(
        VListParam.IndividualShift(tagSpans.toArray),
        opts
      )
      val tagCol = BuildCommon.makeSpan(ArrayBuffer("tag"), ArrayBuffer[HtmlDomNode](eqnNumCol), Nullable(opts))
      BuildCommon.makeFragment(ArrayBuffer(tableBody, tagCol))
    }
  }

  private val alignMap: Map[String, String] = Map(
    "c" -> "center ",
    "l" -> "left ",
    "r" -> "right "
  )

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g    = group.asInstanceOf[ParseNodeArray]
    val opts = options.asInstanceOf[Options]
    val tbl  = ArrayBuffer.empty[MathDomNode]
    val glue = new MathNode("mtd", ArrayBuffer.empty, ArrayBuffer("mtr-glue"))
    val tag  = new MathNode("mtd", ArrayBuffer.empty, ArrayBuffer("mml-eqn-num"))
    var i    = 0
    while (i < g.body.length) {
      val rw  = g.body(i)
      val row = ArrayBuffer.empty[MathDomNode]
      var j   = 0
      while (j < rw.length) {
        row += new MathNode("mtd", ArrayBuffer(BuildMathML.buildGroup(rw(j), opts)))
        j += 1
      }
      val hasTag = g.tags.isDefined && i < g.tags.get.length && (g.tags.get(i) match {
        case Left(b)  => b
        case Right(_) => true
      })
      if (hasTag) {
        row.prepend(glue)
        row += glue
        if (g.leqno.isDefined && g.leqno.get) {
          row.prepend(tag)
        } else {
          row += tag
        }
      }
      tbl += new MathNode("mtr", row)
      i += 1
    }
    var table = new MathNode("mtable", tbl)

    // Set column alignment, row spacing, column spacing, and
    // array lines by setting attributes on the table element.

    // Set the row spacing. In MathML, we specify a gap distance.
    // We do not use rowGap[] because MathML automatically increases
    // cell height with the height/depth of the element content.

    // LaTeX \arraystretch multiplies the row baseline-to-baseline distance.
    // We simulate this by adding (arraystretch - 1)em to the gap. This
    // does a reasonable job of adjusting arrays containing 1 em tall content.

    // The 0.16 and 0.09 values are found empirically. They produce an array
    // similar to LaTeX and in which content does not interfere with \hlines.
    val gap: Double =
      if (g.arraystretch == 0.5) 0.1 // {smallmatrix}, {subarray}
      else 0.16 + g.arraystretch - 1 + (if (g.addJot.isDefined && g.addJot.get) 0.09 else 0)
    table.setAttribute("rowspacing", Units.makeEm(gap))

    // MathML table lines go only between cells.
    // To place a line on an edge we'll use <menclose>, if necessary.
    var menclose = ""
    var align    = ""

    if (g.cols.isDefined && g.cols.get.length > 0) {
      // Find column alignment, column spacing, and  vertical lines.
      val colsArr          = g.cols.get
      var columnLines      = ""
      var prevTypeWasAlign = false
      var iStart           = 0
      var iEnd             = colsArr.length

      if (colsArr(0).isInstanceOf[AlignSpec.Separator]) {
        menclose += "top "
        iStart = 1
      }
      if (colsArr(colsArr.length - 1).isInstanceOf[AlignSpec.Separator]) {
        menclose += "bottom "
        iEnd -= 1
      }

      var ci = iStart
      while (ci < iEnd) {
        val col = colsArr(ci)
        col match {
          case AlignSpec.Align(a, _, _) =>
            align += alignMap.getOrElse(a, "")
            if (prevTypeWasAlign) {
              columnLines += "none "
            }
            prevTypeWasAlign = true
          case AlignSpec.Separator(sep) =>
            // MathML accepts only single lines between cells.
            // So we read only the first of consecutive separators.
            if (prevTypeWasAlign) {
              columnLines += (if (sep == "|") "solid " else "dashed ")
              prevTypeWasAlign = false
            }
        }
        ci += 1
      }

      table.setAttribute("columnalign", align.trim())

      if (columnLines.contains("s") || columnLines.contains("d")) {
        table.setAttribute("columnlines", columnLines.trim())
      }
    }

    // Set column spacing.
    if (g.colSeparationType.isDefined) {
      g.colSeparationType.get match {
        case "align" =>
          val colsArr = g.cols.getOrElse(Array.empty[AlignSpec])
          var spacing = ""
          var si      = 1
          while (si < colsArr.length) {
            spacing += (if (si % 2 != 0) "0em " else "1em ")
            si += 1
          }
          table.setAttribute("columnspacing", spacing.trim())
        case "alignat" | "gather" =>
          table.setAttribute("columnspacing", "0em")
        case "small" =>
          table.setAttribute("columnspacing", "0.2778em")
        case "CD" =>
          table.setAttribute("columnspacing", "0.5em")
        case _ =>
          table.setAttribute("columnspacing", "1em")
      }
    } else {
      table.setAttribute("columnspacing", "1em")
    }

    // Address \hline and \hdashline
    var rowLines  = ""
    val hlinesArr = g.hLinesBeforeRow

    menclose += (if (hlinesArr(0).length > 0) "left " else "")
    menclose += (if (hlinesArr(hlinesArr.length - 1).length > 0) "right " else "")

    var hi = 1
    while (hi < hlinesArr.length - 1) {
      rowLines += (
        if (hlinesArr(hi).length == 0) "none "
        // MathML accepts only a single line between rows. Read one element.
        else if (hlinesArr(hi)(0)) "dashed "
        else "solid "
      )
      hi += 1
    }
    if (rowLines.contains("s") || rowLines.contains("d")) {
      table.setAttribute("rowlines", rowLines.trim())
    }

    if (menclose.nonEmpty) {
      table = new MathNode("menclose", ArrayBuffer(table))
      table.setAttribute("notation", menclose.trim())
    }

    if (g.arraystretch > 0 && g.arraystretch < 1) {
      // A small array. Wrap in scriptstyle so row gap is not too large.
      table = new MathNode("mstyle", ArrayBuffer(table))
      table.setAttribute("scriptlevel", "1")
    }

    table
  }

  // Convenience function for align, align*, aligned, alignat, alignat*, alignedat.
  private val alignedHandler: (EnvContext, Array[AnyParseNode], Array[Nullable[AnyParseNode]]) => AnyParseNode =
    (context, args, optArgs) => {
      if (!context.envName.contains("ed")) {
        validateAmsEnvironmentContext(context)
      }
      val cols = ArrayBuffer.empty[AlignSpec]
      val separationType: ColSeparationType =
        if (context.envName.contains("at")) "alignat" else "align"
      val isSplit = context.envName == "split"
      val parser  = context.parser.asInstanceOf[Parser]
      val res     = parseArray(
        parser,
        ParseArrayConfig(
          cols = Nullable(cols.toArray), // will be replaced below
          addJot = Nullable(true),
          autoTag = if (isSplit) Nullable.Null else getAutoTag(context.envName),
          emptySingleRow = true,
          colSeparationType = Nullable(separationType),
          maxNumCols = if (isSplit) Nullable(2) else Nullable.Null,
          leqno = Nullable(parser.settings.leqno)
        ),
        Nullable(StyleStr.Display)
      )

      // Determining number of columns.
      // 1. If the first argument is given, we use it as a number of columns,
      //    and makes sure that each row doesn't exceed that number.
      // 2. Otherwise, just count number of columns = maximum number
      //    of cells in each row ("aligned" mode -- isAligned will be true).
      //
      // At the same time, prepend empty group {} at beginning of every second
      // cell in each row (starting with second cell) so that operators become
      // binary.  This behavior is implemented in amsmath's \start@aligned.
      var numMaths = 0
      var numCols  = 0
      val emptyGroup: ParseNodeOrdgroup = ParseNodeOrdgroup(
        mode = context.mode,
        body = Array.empty
      )
      if (args.nonEmpty && args(0) != null && args(0).nodeType == "ordgroup") { // @nowarn — null check for interop
        val og   = args(0).asInstanceOf[ParseNodeOrdgroup]
        var arg0 = ""
        var ai   = 0
        while (ai < og.body.length) {
          val textord = ParseNode.assertNodeType(Nullable(og.body(ai)), "textord").asInstanceOf[ParseNodeTextord]
          arg0 += textord.text
          ai += 1
        }
        numMaths = arg0.toIntOption.getOrElse(0)
        numCols = numMaths * 2
      }
      val isAligned = numCols == 0
      res.body.foreach { row =>
        var ri = 1
        while (ri < row.length) {
          // Modify ordgroup node within styling node
          val styling  = ParseNode.assertNodeType(Nullable(row(ri)), "styling").asInstanceOf[ParseNodeStyling]
          val ordgroup = ParseNode.assertNodeType(Nullable(styling.body(0)), "ordgroup").asInstanceOf[ParseNodeOrdgroup]
          ordgroup.body = Array(emptyGroup) ++ ordgroup.body
          ri += 2
        }
        if (!isAligned) { // Case 1
          val curMaths = row.length / 2
          if (numMaths < curMaths) {
            throw new ParseError("Too many math in a row: " +
                                   s"expected $numMaths, but got $curMaths",
                                 Nullable(row(0))
            )
          }
        } else if (numCols < row.length) { // Case 2
          numCols = row.length
        }
      }

      // Adjusting alignment.
      // In aligned mode, we add one \qquad between columns;
      // otherwise we add nothing.
      val finalCols = new Array[AlignSpec](numCols)
      var ci        = 0
      while (ci < numCols) {
        var colAlign = "r"
        var pregap   = 0.0
        if (ci % 2 == 1) {
          colAlign = "l"
        } else if (ci > 0 && isAligned) { // "aligned" mode.
          pregap = 1.0 // add one \quad
        }
        finalCols(ci) = AlignSpec.Align(
          align = colAlign,
          pregap = pregap,
          postgap = 0
        )
        ci += 1
      }
      res.cols = Nullable(finalCols)
      res.colSeparationType = Nullable(if (isAligned) "align" else "alignat")
      res
    }

  def register(): Unit = {
    // Arrays are part of LaTeX, defined in lttab.dtx so its documentation
    // is part of the source2e.pdf file of LaTeX2e source documentation.
    // {darray} is an {array} environment where cells are set in \displaystyle,
    // as defined in nccmath.sty.
    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("array", "darray"),
        props = EnvProps(
          numArgs = 1
        ),
        handler = (context, args, optArgs) => {
          val parser = context.parser.asInstanceOf[Parser]
          // Since no types are specified above, the two possibilities are
          // - The argument is wrapped in {} or [], in which case Parser's
          //   parseGroup() returns an "ordgroup" wrapping some symbol node.
          // - The argument is a bare symbol node.
          val symNode = ParseNode.checkSymbolNodeType(Nullable(args(0)))
          val colalign: Array[AnyParseNode] =
            if (symNode.isDefined) Array(args(0))
            else ParseNode.assertNodeType(Nullable(args(0)), "ordgroup").asInstanceOf[ParseNodeOrdgroup].body
          val colsArr: Array[AlignSpec] = colalign.map { nde =>
            val node = ParseNode.assertSymbolNodeType(Nullable(nde))
            val ca   = node.text
            if ("lcr".contains(ca)) {
              AlignSpec.Align(align = ca)
            } else if (ca == "|") {
              AlignSpec.Separator(separator = "|")
            } else if (ca == ":") {
              AlignSpec.Separator(separator = ":")
            } else {
              throw new ParseError("Unknown column alignment: " + ca, Nullable(nde))
            }
          }
          val config = ParseArrayConfig(
            cols = Nullable(colsArr),
            hskipBeforeAndAfter = Nullable(true), // \@preamble in lttab.dtx
            maxNumCols = Nullable(colsArr.length)
          )
          parseArray(parser, config, Nullable(dCellStyle(context.envName)))
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // The matrix environments of amsmath builds on the array environment
    // of LaTeX, which is discussed above.
    // The mathtools package adds starred versions of the same environments.
    // These have an optional argument to choose left|center|right justification.
    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array(
          "matrix",
          "pmatrix",
          "bmatrix",
          "Bmatrix",
          "vmatrix",
          "Vmatrix",
          "matrix*",
          "pmatrix*",
          "bmatrix*",
          "Bmatrix*",
          "vmatrix*",
          "Vmatrix*"
        ),
        props = EnvProps(
          numArgs = 0
        ),
        handler = (context, args, optArgs) => {
          val parser = context.parser.asInstanceOf[Parser]
          val delimitersMap: Map[String, Nullable[Array[String]]] = Map(
            "matrix" -> Nullable.Null,
            "pmatrix" -> Nullable(Array("(", ")")),
            "bmatrix" -> Nullable(Array("[", "]")),
            "Bmatrix" -> Nullable(Array("\\{", "\\}")),
            "vmatrix" -> Nullable(Array("|", "|")),
            "Vmatrix" -> Nullable(Array("\\Vert", "\\Vert"))
          )
          val baseName   = context.envName.replace("*", "")
          val delimiters = delimitersMap(baseName)
          // \hskip -\arraycolsep in amsmath
          var colAlign = "c"
          var payload  = ParseArrayConfig(
            hskipBeforeAndAfter = Nullable(false),
            cols = Nullable(Array(AlignSpec.Align(align = colAlign)))
          )
          if (context.envName.endsWith("*")) {
            // It's one of the mathtools starred functions.
            // Parse the optional alignment argument.
            parser.consumeSpaces()
            if (parser.fetch().text == "[") {
              parser.consume()
              parser.consumeSpaces()
              colAlign = parser.fetch().text
              if (!"lcr".contains(colAlign)) {
                throw new ParseError("Expected l or c or r", parser.nextToken.asInstanceOf[Nullable[SourceLocation.HasLoc]])
              }
              parser.consume()
              parser.consumeSpaces()
              parser.expect("]")
              parser.consume()
              payload = payload.copy(
                cols = Nullable(Array(AlignSpec.Align(align = colAlign)))
              )
            }
          }
          val res: ParseNodeArray =
            parseArray(parser, payload, Nullable(dCellStyle(context.envName)))
          // Populate cols with the correct number of column alignment specs.
          val numColsInRes = if (res.body.isEmpty) 0 else res.body.map(_.length).max
          res.cols = Nullable(
            Array.fill(Math.max(0, numColsInRes))(
              AlignSpec.Align(align = colAlign)
            )
          )
          if (delimiters.isDefined) {
            val d = delimiters.get
            ParseNodeLeftright(
              mode = context.mode,
              body = Array(res),
              left = d(0),
              right = d(1),
              rightColor = Nullable.Null // \right uninfluenced by \color in array
            )
          } else {
            res
          }
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("smallmatrix"),
        props = EnvProps(
          numArgs = 0
        ),
        handler = (context, args, optArgs) => {
          val parser  = context.parser.asInstanceOf[Parser]
          val payload = ParseArrayConfig(arraystretch = Nullable(0.5))
          val res     = parseArray(parser, payload, Nullable(StyleStr.Script))
          res.colSeparationType = Nullable("small")
          res
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("subarray"),
        props = EnvProps(
          numArgs = 1
        ),
        handler = (context, args, optArgs) => {
          val parser = context.parser.asInstanceOf[Parser]
          // Parsing of {subarray} is similar to {array}
          val symNode = ParseNode.checkSymbolNodeType(Nullable(args(0)))
          val colalign: Array[AnyParseNode] =
            if (symNode.isDefined) Array(args(0))
            else ParseNode.assertNodeType(Nullable(args(0)), "ordgroup").asInstanceOf[ParseNodeOrdgroup].body
          val colsArr: Array[AlignSpec] = colalign.map { nde =>
            val node = ParseNode.assertSymbolNodeType(Nullable(nde))
            val ca   = node.text
            // {subarray} only recognizes "l" & "c"
            if ("lc".contains(ca)) {
              AlignSpec.Align(align = ca)
            } else {
              throw new ParseError("Unknown column alignment: " + ca, Nullable(nde))
            }
          }
          if (colsArr.length > 1) {
            throw new ParseError("{subarray} can contain only one column")
          }
          val payload = ParseArrayConfig(
            cols = Nullable(colsArr),
            hskipBeforeAndAfter = Nullable(false),
            arraystretch = Nullable(0.5)
          )
          val res = parseArray(parser, payload, Nullable(StyleStr.Script))
          if (res.body.length > 0 && res.body(0).length > 1) {
            throw new ParseError("{subarray} can contain only one column")
          }
          res
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // A cases environment (in amsmath.sty) is almost equivalent to
    // \def\arraystretch{1.2}%
    // \left\{\begin{array}{@{}l@{\quad}l@{}} ... \end{array}\right.
    // {dcases} is a {cases} environment where cells are set in \displaystyle,
    // as defined in mathtools.sty.
    // {rcases} is another mathtools environment. It's brace is on the right side.
    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array(
          "cases",
          "dcases",
          "rcases",
          "drcases"
        ),
        props = EnvProps(
          numArgs = 0
        ),
        handler = (context, args, optArgs) => {
          val parser  = context.parser.asInstanceOf[Parser]
          val payload = ParseArrayConfig(
            arraystretch = Nullable(1.2),
            cols = Nullable(
              Array(
                AlignSpec.Align(
                  align = "l",
                  pregap = 0,
                  // TODO(kevinb) get the current style.
                  // For now we use the metrics for TEXT style which is what we were
                  // doing before.  Before attempting to get the current style we
                  // should look at TeX's behavior especially for \over and matrices.
                  postgap = 1.0 /* 1em quad */
                ),
                AlignSpec.Align(
                  align = "l",
                  pregap = 0,
                  postgap = 0
                )
              )
            )
          )
          val res: ParseNodeArray =
            parseArray(parser, payload, Nullable(dCellStyle(context.envName)))
          ParseNodeLeftright(
            mode = context.mode,
            body = Array(res),
            left = if (context.envName.contains("r")) "." else "\\{",
            right = if (context.envName.contains("r")) "\\}" else ".",
            rightColor = Nullable.Null
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // In the align environment, one uses ampersands, &, to specify number of
    // columns in each row, and to locate spacing between each column.
    // align gets automatic numbering. align* and aligned do not.
    // The alignedat environment can be used in math mode.
    // Note that we assume \nomallineskiplimit to be zero,
    // so that \strut@ is the same as \strut.
    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("align", "align*", "aligned", "split"),
        props = EnvProps(
          numArgs = 0
        ),
        handler = alignedHandler,
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // A gathered environment is like an array environment with one centered
    // column, but where rows are considered lines so get \jot line spacing
    // and contents are set in \displaystyle.
    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("gathered", "gather", "gather*"),
        props = EnvProps(
          numArgs = 0
        ),
        handler = (context, args, optArgs) => {
          val parser = context.parser.asInstanceOf[Parser]
          if (gatherEnvironments.contains(context.envName)) {
            validateAmsEnvironmentContext(context)
          }
          val config = ParseArrayConfig(
            cols = Nullable(Array(AlignSpec.Align(align = "c"))),
            addJot = Nullable(true),
            colSeparationType = Nullable("gather"),
            autoTag = getAutoTag(context.envName),
            emptySingleRow = true,
            leqno = Nullable(parser.settings.leqno)
          )
          parseArray(parser, config, Nullable(StyleStr.Display))
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // alignat environment is like an align environment, but one must explicitly
    // specify maximum number of columns in each row, and can adjust spacing between
    // each columns.
    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("alignat", "alignat*", "alignedat"),
        props = EnvProps(
          numArgs = 1
        ),
        handler = alignedHandler,
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("equation", "equation*"),
        props = EnvProps(
          numArgs = 0
        ),
        handler = (context, args, optArgs) => {
          val parser = context.parser.asInstanceOf[Parser]
          validateAmsEnvironmentContext(context)
          val config = ParseArrayConfig(
            autoTag = getAutoTag(context.envName),
            emptySingleRow = true,
            singleRow = true,
            maxNumCols = Nullable(1),
            leqno = Nullable(parser.settings.leqno)
          )
          parseArray(parser, config, Nullable(StyleStr.Display))
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    EnvironmentDef.defineEnvironment(
      EnvDefSpec(
        nodeType = "array",
        names = Array("CD"),
        props = EnvProps(
          numArgs = 0
        ),
        handler = (context, args, optArgs) => {
          val parser = context.parser.asInstanceOf[Parser]
          validateAmsEnvironmentContext(context)
          CdEnv.parseCD(parser)
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // Macros
    MacroDef.defineMacro("\\nonumber", MacroDefinition.StringDef("\\gdef\\@eqnsw{0}"))
    MacroDef.defineMacro("\\notag", MacroDefinition.StringDef("\\nonumber"))

    // Catch \hline outside array environment
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "text", // Doesn't matter what this is.
        names = Array("\\hline", "\\hdashline"),
        props = FunctionPropSpec(
          numArgs = 0,
          allowedInText = true,
          allowedInMath = true
        ),
        handler = Nullable((context, args, optArgs) => throw new ParseError(s"${context.funcName} valid only within array environment"))
      )
    )
  }

  // Helper types for the HTML builder
  final private case class HlineInfo(pos: Double, isDashed: Boolean)

  final private class ArrayWithRowInfo(
    val elems:  Array[HtmlDomNode],
    val height: Double,
    val depth:  Double,
    var pos:    Double
  )
}
