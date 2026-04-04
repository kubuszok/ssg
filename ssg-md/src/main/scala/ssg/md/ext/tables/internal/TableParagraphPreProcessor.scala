/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/internal/TableParagraphPreProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables
package internal

import ssg.md.Nullable
import ssg.md.ast.{ Paragraph, Text, WhiteSpace }
import ssg.md.parser.InlineParser
import ssg.md.parser.block.{ CharacterNodeFactory, ParagraphPreProcessor, ParagraphPreProcessorFactory, ParserState }
import ssg.md.parser.core.ReferencePreProcessorFactory
import ssg.md.util.data.DataHolder
import ssg.md.util.format.TableFormatOptions
import ssg.md.util.sequence.BasedSequence

import java.util.{ ArrayList, HashMap, List as JList }
import java.util.regex.Pattern
import scala.util.boundary
import scala.util.boundary.break
import scala.language.implicitConversions
import ssg.md.util.ast.Node
import ssg.md.util.ast.{ DoNotDecorate, NodeIterator }

class TableParagraphPreProcessor private (options: DataHolder) extends ParagraphPreProcessor {

  private val parserOptions:          TableParserOptions = new TableParserOptions(options)
  private val TABLE_HEADER_SEPARATOR: Pattern            = TableParagraphPreProcessor.getTableHeaderSeparator(parserOptions.minSeparatorDashes, "")

  override def preProcessBlock(block: Paragraph, state: ParserState): Int = {
    val inlineParser = state.inlineParser

    val tableLines          = new ArrayList[BasedSequence]()
    var separatorLineNumber = -1
    var separatorLine: Nullable[BasedSequence] = Nullable.empty
    val blockIndent = block.getLineIndent(0)
    var captionLine: Nullable[BasedSequence] = Nullable.empty
    val separators = TableParagraphPreProcessor.separatorCharacters
    val nodeMap: Map[Char, CharacterNodeFactory] = {
      import scala.jdk.CollectionConverters.*
      TableParagraphPreProcessor.pipeNodeMap.asScala.map { case (k, v) => (k.charValue(), v) }.toMap
    }

    boundary {
      // Inner boundary for the for-loop only (Java: break from for)
      boundary[Unit] {
        for (rowLine <- scala.jdk.CollectionConverters.IterableHasAsScala(block.contentLines).asScala) {
          val rowNumber = tableLines.size()
          if (separatorLineNumber == -1 && rowNumber > parserOptions.maxHeaderRows) break[Int](0) // too many header rows, return 0

          if (rowLine.indexOf('|') < 0) {
            if (separatorLineNumber == -1) break[Int](0) // no separator found yet, return 0

            if (parserOptions.withCaption) {
              val trimmed = rowLine.trim()
              if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                captionLine = Nullable(trimmed)
              }
            }
            break[Unit](()) // break from for loop only
          }

          // NOTE: block lines now contain leading indent spaces which should be ignored
          val trimmedRowLine = rowLine.subSequence(block.getLineIndent(rowNumber))

          if (separatorLineNumber == -1) {
            if (
              rowNumber >= parserOptions.minHeaderRows
              && TABLE_HEADER_SEPARATOR.matcher(trimmedRowLine).matches()
            ) {
              // must start with | or cell, whitespace means its not a separator line
              if (rowLine.charAt(0) != ' ' && rowLine.charAt(0) != '\t' || rowLine.charAt(0) != '|') {
                separatorLineNumber = rowNumber
                separatorLine = Nullable(trimmedRowLine)
              } else if (rowLine.charAt(0) == ' ' || rowLine.charAt(0) == '\t') {
                block.hasTableSeparator = true
              }
            }
          }

          tableLines.add(trimmedRowLine)
        }
      } // end inner boundary

      if (separatorLineNumber == -1) break(0)

      val tableRows = new ArrayList[TableRow]()
      boundary[Unit] {
        for (rowLine <- scala.jdk.CollectionConverters.IterableHasAsScala(tableLines).asScala) {
          val rowNumber = tableRows.size()

          val fullRowLine = if (block.getLineIndent(rowNumber) <= blockIndent) {
            rowLine.trimEOL()
          } else {
            rowLine.baseSubSequence(rowLine.startOffset - (block.getLineIndent(rowNumber) - blockIndent), rowLine.endOffset - rowLine.eolEndLength())
          }

          val isSeparator = rowNumber == separatorLineNumber
          val tableRow    = new TableRow(fullRowLine)

          val tableRowNumber: Int =
            if (isSeparator) 0
            else if (rowNumber < separatorLineNumber) rowNumber + 1
            else rowNumber - separatorLineNumber

          val sepList: Nullable[Any] = if (isSeparator) {
            val fakeRow = new TableParagraphPreProcessor.TableSeparatorRow(fullRowLine)
            val list    = inlineParser.parseCustom(fullRowLine, fakeRow, separators, nodeMap)
            tableRow.takeChildren(fakeRow)
            list
          } else {
            val list = inlineParser.parseCustom(fullRowLine, tableRow, TableParagraphPreProcessor.pipeCharacters, nodeMap)
            // can have table separators embedded inside inline elements, need to convert them to text
            // and remove them from sepList
            import scala.jdk.CollectionConverters.*
            list.map { l =>
              val jList: JList[Node] = l.asJava
              cleanUpInlinedSeparators(inlineParser, tableRow, jList)
            }
          }

          if (sepList.isEmpty) {
            if (rowNumber <= separatorLineNumber) break[Int](0) // return 0 from method
            break[Unit](()) // break from inner for
          }

          tableRow.rowNumber = tableRowNumber
          tableRows.add(tableRow)
        }
      }

      // table is done, could be earlier than the lines tested earlier, may need to truncate lines
      val tableBlock = new TableBlock(tableLines.subList(0, tableRows.size()))
      var section: Node = new TableHead(tableLines.get(0).subSequence(0, 0))
      tableBlock.appendChild(section)

      val alignments = parseAlignment(separatorLine.get)

      var rowNumber        = 0
      val separatorColumns = alignments.size()
      for (tableRow <- scala.jdk.CollectionConverters.IterableHasAsScala(tableRows).asScala) {
        if (rowNumber == separatorLineNumber) {
          section.setCharsFromContent()
          section = new TableSeparator()
          tableBlock.appendChild(section)
        } else if (rowNumber == separatorLineNumber + 1) {
          section.setCharsFromContent()
          section = new TableBody()
          tableBlock.appendChild(section)
        }

        var firstCell   = true
        var cellCount   = 0
        val nodes       = new NodeIterator(tableRow.firstChild.get) // safe: tableRow always has children at this point
        val newTableRow = new TableRow(tableRow.chars)
        newTableRow.rowNumber = tableRow.rowNumber
        var accumulatedSpanOffset = 0

        boundary[Unit] {
          while (nodes.hasNext) {
            if (cellCount >= separatorColumns && parserOptions.discardExtraColumns) {
              if (parserOptions.headerSeparatorColumnMatch && rowNumber < separatorLineNumber) {
                break[Int](0) // header/separator mismatch - return 0 from method
              }
              break[Unit](()) // break from while
            }

            val tableCell = new TableCell()

            if (firstCell && nodes.peek.isDefined && nodes.peek.get.isInstanceOf[TableColumnSeparator]) {
              val columnSep = nodes.next()
              tableCell.openingMarker = columnSep.chars
              columnSep.unlink()
              firstCell = false
            }

            val alignment: Nullable[TableCell.Alignment] =
              if (cellCount + accumulatedSpanOffset < separatorColumns) alignments.get(cellCount + accumulatedSpanOffset)
              else Nullable.empty
            tableCell.header = rowNumber < separatorLineNumber
            tableCell.alignment = alignment

            // take all until separator or end of iterator
            boundary {
              while (nodes.hasNext)
                if (nodes.peek.isDefined && nodes.peek.get.isInstanceOf[TableColumnSeparator]) {
                  break(()) // break from inner while — hit a separator
                } else {
                  tableCell.appendChild(nodes.next())
                }
            }

            // accumulate closers, and optional spans
            var closingMarker: Nullable[BasedSequence] = Nullable.empty
            var span = 1
            boundary {
              while (nodes.hasNext) {
                if (nodes.peek.isEmpty || !nodes.peek.get.isInstanceOf[TableColumnSeparator]) break()
                if (closingMarker.isEmpty) {
                  closingMarker = Nullable(nodes.next().chars)
                  if (!parserOptions.columnSpans) break()
                } else {
                  val nextSep = nodes.peek.get.chars

                  if (!closingMarker.get.isContinuedBy(nextSep)) break()
                  closingMarker = Nullable(closingMarker.get.spliceAtEnd(nextSep))
                  nodes.next().unlink()
                  span += 1
                }
              }
            }

            accumulatedSpanOffset += span - 1

            closingMarker.foreach(cm => tableCell.closingMarker = cm)
            tableCell.chars = tableCell.childChars
            // option to keep cell whitespace, if yes, then convert it to text and merge adjacent text nodes
            if (parserOptions.trimCellWhitespace) tableCell.trimWhiteSpace()
            else tableCell.mergeWhiteSpace()

            // NOTE: here we get only chars which do not reflect out-of-base characters, prefixes and removed text
            tableCell.text = tableCell.childChars

            tableCell.setCharsFromContent()
            tableCell.span = span
            newTableRow.appendChild(tableCell)
            cellCount += 1
          }
        }

        if (parserOptions.headerSeparatorColumnMatch && rowNumber < separatorLineNumber && cellCount < separatorColumns) {
          break[Int](0) // no match - return 0 from method
        }

        while (parserOptions.appendMissingColumns && cellCount < separatorColumns) {
          val tableCell = new TableCell()
          tableCell.header = rowNumber < separatorLineNumber
          tableCell.alignment = alignments.get(cellCount)
          newTableRow.appendChild(tableCell)
          cellCount += 1
        }

        newTableRow.setCharsFromContent()
        section.appendChild(newTableRow)

        rowNumber += 1
      }

      section.setCharsFromContent()

      if (section.isInstanceOf[TableSeparator]) {
        val tableBody = new TableBody(section.chars.subSequence(section.chars.length()))
        tableBlock.appendChild(tableBody)
      }

      // Add caption if the option is enabled
      captionLine.foreach { cl =>
        val caption = new TableCaption(cl.subSequence(0, 1), cl.subSequence(1, cl.length() - 1), cl.subSequence(cl.length() - 1))
        inlineParser.parse(caption.text, caption)
        caption.setCharsFromContent()
        tableBlock.appendChild(caption)
      }

      tableBlock.setCharsFromContent()

      block.insertBefore(tableBlock)
      state.blockAdded(tableBlock)
      tableBlock.chars.length()
    }
  }

  private[tables] def cleanUpInlinedSeparators(inlineParser: InlineParser, tableRow: TableRow, sepList: JList[Node]): Nullable[JList[Node]] = {
    // any separators which do not have tableRow as parent are embedded into inline elements and should be
    // converted back to text
    var removedSeparators: Nullable[ArrayList[Node]] = Nullable.empty
    var mergeTextParents:  Nullable[ArrayList[Node]] = Nullable.empty

    val iter = sepList.iterator()
    while (iter.hasNext) {
      val node = iter.next()
      if (node.parent.isDefined && !node.parent.contains(tableRow)) {
        // embedded, convert it and surrounding whitespace to text
        val firstNode: Node = if (node.previous.isDefined && node.previous.get.isInstanceOf[WhiteSpace]) node.previous.get else node
        val lastNode:  Node = if (node.next.isDefined && node.next.get.isInstanceOf[WhiteSpace]) node.next.get else node

        val text = new Text(node.baseSubSequence(firstNode.startOffset, lastNode.endOffset))
        node.insertBefore(text)
        node.unlink()
        firstNode.unlink()
        lastNode.unlink()

        if (removedSeparators.isEmpty) {
          removedSeparators = Nullable(new ArrayList[Node]())
          mergeTextParents = Nullable(new ArrayList[Node]())
        }

        removedSeparators.foreach(_.add(node))
        text.parent.foreach(p => mergeTextParents.foreach(_.add(p)))
      }
    }

    mergeTextParents.fold(Nullable(sepList)) { parents =>
      parents.forEach { parent =>
        inlineParser.mergeTextNodes(parent.firstChild, parent.lastChild)
      }

      if (removedSeparators.get.size() == sepList.size()) {
        Nullable.empty
      } else {
        val newSeparators = new ArrayList[Node](sepList)
        newSeparators.removeAll(removedSeparators.get)
        Nullable(newSeparators)
      }
    }
  }

  private def parseAlignment(separatorLine: BasedSequence): JList[Nullable[TableCell.Alignment]] = {
    val parts      = TableParagraphPreProcessor.split(separatorLine, columnSpans = false, wantPipes = false)
    val alignments = new ArrayList[Nullable[TableCell.Alignment]]()
    val iter       = parts.iterator()
    while (iter.hasNext) {
      val part      = iter.next()
      val trimmed   = part.trim()
      val left      = trimmed.startsWith(":")
      val right     = trimmed.endsWith(":")
      val alignment = TableParagraphPreProcessor.getAlignment(left, right)
      alignments.add(alignment)
    }
    alignments
  }
}

object TableParagraphPreProcessor {

  private val pipeCharacters: java.util.BitSet = {
    val bs = new java.util.BitSet()
    bs.set('|')
    bs
  }

  private val separatorCharacters: java.util.BitSet = {
    val bs = new java.util.BitSet()
    bs.set('|')
    bs.set(':')
    bs.set('-')
    bs
  }

  private val pipeNodeMap: HashMap[Character, CharacterNodeFactory] = {
    val map = new HashMap[Character, CharacterNodeFactory]()
    map.put(
      '|',
      new CharacterNodeFactory {
        override def skipNext(c: Char):     Boolean = c == ' ' || c == '\t'
        override def skipPrev(c: Char):     Boolean = c == ' ' || c == '\t'
        override def wantSkippedWhitespace: Boolean = true
        override def apply():               Node    = new TableColumnSeparator()
      }
    )
    map
  }

  private class TableSeparatorRow() extends TableRow, DoNotDecorate {
    def this(chars: BasedSequence) = {
      this()
      this.chars = chars
    }
  }

  def Factory(): ParagraphPreProcessorFactory = new ParagraphPreProcessorFactory {
    override def affectsGlobalScope: Boolean = false

    override def afterDependents: Nullable[Set[Class[?]]] =
      Nullable(Set[Class[?]](classOf[ReferencePreProcessorFactory]))

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def apply(state: ParserState): ParagraphPreProcessor =
      new TableParagraphPreProcessor(state.properties)
  }

  def getTableHeaderSeparator(minColumnDashes: Int, intellijDummyIdentifier: String): Pattern = {
    val minCol       = if (minColumnDashes >= 1) minColumnDashes else 1
    val minColDash   = if (minColumnDashes >= 2) minColumnDashes - 1 else 1
    val minColDashes = if (minColumnDashes >= 3) minColumnDashes - 2 else 1
    // to prevent conversion to arabic numbers, using string
    val COL = s"(?:\\s*-{$minCol,}\\s*|\\s*:-{$minColDash,}\\s*|\\s*-{$minColDash,}:\\s*|\\s*:-{$minColDashes,}:\\s*)"

    val noIntelliJ = intellijDummyIdentifier.isEmpty
    val add        = if (noIntelliJ) "" else TableFormatOptions.INTELLIJ_DUMMY_IDENTIFIER
    val sp         = if (noIntelliJ) "\\s" else "(?:\\s" + add + "?)"
    val ds         = if (noIntelliJ) "-" else "(?:-" + add + "?)"
    val pipe       = if (noIntelliJ) "\\|" else "(?:" + add + "?\\|" + add + "?)"

    val regex = "\\|" + COL + "\\|?\\s*" + "|" +
      COL + "\\|\\s*" + "|" +
      "\\|?" + "(?:" + COL + "\\|)+" + COL + "\\|?\\s*"

    val withIntelliJ = regex.replace("\\s", sp).replace("\\|", pipe).replace("-", ds)

    Pattern.compile(withIntelliJ)
  }

  private def split(input: BasedSequence, columnSpans: Boolean, wantPipes: Boolean): JList[BasedSequence] = {
    var line       = input.trim()
    var lineLength = line.length()
    val segments   = new ArrayList[BasedSequence]()

    if (line.startsWith("|")) {
      if (wantPipes) segments.add(line.subSequence(0, 1))
      line = line.subSequence(1, lineLength)
      lineLength -= 1
    }

    var escape    = false
    var lastPos   = 0
    var cellChars = 0
    var i         = 0
    while (i < lineLength) {
      val c = line.charAt(i)
      if (escape) {
        escape = false
        cellChars += 1
      } else {
        c match {
          case '\\' =>
            escape = true
            // Removing the escaping '\' is handled by the inline parser later, so add it to cell
            cellChars += 1
          case '|' =>
            if (!columnSpans || lastPos < i) segments.add(line.subSequence(lastPos, i))
            if (wantPipes) segments.add(line.subSequence(i, i + 1))
            lastPos = i + 1
            cellChars = 0
          case _ =>
            cellChars += 1
        }
      }
      i += 1
    }

    if (cellChars > 0) {
      segments.add(line.subSequence(lastPos, lineLength))
    }
    segments
  }

  private def getAlignment(left: Boolean, right: Boolean): Nullable[TableCell.Alignment] =
    if (left && right) {
      Nullable(TableCell.Alignment.CENTER)
    } else if (left) {
      Nullable(TableCell.Alignment.LEFT)
    } else if (right) {
      Nullable(TableCell.Alignment.RIGHT)
    } else {
      Nullable.empty
    }
}
