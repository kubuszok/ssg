/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/Node.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/Node.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.collection.iteration.ReversiblePeekingIterable
import ssg.md.util.collection.iteration.ReversiblePeekingIterator
import ssg.md.util.misc.Pair
import ssg.md.util.misc.Utils
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.Range
import ssg.md.util.sequence.SegmentedSequence
import ssg.md.util.sequence.builder.SequenceBuilder
import ssg.md.util.visitor.AstNode

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

abstract class Node {

  private var _parent:          Nullable[Node] = Nullable.empty
  private[ast] var _firstChild: Nullable[Node] = Nullable.empty
  private var _lastChild:       Nullable[Node] = Nullable.empty
  private var _prev:            Nullable[Node] = Nullable.empty
  private[ast] var _next:       Nullable[Node] = Nullable.empty
  private var _chars:           BasedSequence  = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    _chars = chars
  }

  /*
   * chars convenience delegates
   */
  def startOffset:                                     Int           = _chars.startOffset
  def endOffset:                                       Int           = _chars.endOffset
  def textLength:                                      Int           = _chars.length
  def baseSequence:                                    BasedSequence = _chars.getBaseSequence
  def sourceRange:                                     Range         = _chars.getSourceRange
  def baseSubSequence(startIndex: Int, endIndex: Int): BasedSequence = _chars.baseSubSequence(startIndex, endIndex)
  def baseSubSequence(startIndex: Int):                BasedSequence = _chars.baseSubSequence(startIndex)
  def emptyPrefix:                                     BasedSequence = _chars.getEmptyPrefix
  def emptySuffix:                                     BasedSequence = _chars.getEmptySuffix

  def startOfLine:                   Int                    = _chars.baseStartOfLine()
  def endOfLine:                     Int                    = _chars.baseEndOfLine()
  def startOfLine(index:       Int): Int                    = _chars.baseStartOfLine(index)
  def endOfLine(index:         Int): Int                    = _chars.baseEndOfLine(index)
  def lineColumnAtIndex(index: Int): Pair[Integer, Integer] = _chars.baseLineColumnAtIndex(index)
  def lineColumnAtStart:             Pair[Integer, Integer] = _chars.baseLineColumnAtStart()
  def lineColumnAtEnd:               Pair[Integer, Integer] = _chars.baseLineColumnAtEnd()

  def ancestorOfType(classes: Class[?]*): Nullable[Node] = boundary {
    var p = parent
    while (p.isDefined) {
      for (nodeType <- classes)
        if (nodeType.isInstance(p.get)) {
          break(Nullable(p.get))
        }
      p = p.get.parent
    }
    Nullable.empty
  }

  def countAncestorsOfType(classes: Class[?]*): Int = {
    var p     = parent
    var count = 0
    while (p.isDefined) {
      boundary {
        for (nodeType <- classes)
          if (nodeType.isInstance(p.get)) {
            count += 1
            break()
          }
      }
      p = p.get.parent
    }
    count
  }

  def countDirectAncestorsOfType(skip: Nullable[Class[?]], classes: Class[?]*): Int = {
    var p     = parent
    var count = 0
    boundary {
      while (p.isDefined) {
        var hadMatch = false
        boundary {
          for (nodeType <- classes) {
            if (nodeType.isInstance(p.get)) {
              count += 1
              hadMatch = true
              break()
            }
            if (skip.isDefined && skip.get.isInstance(p.get)) {
              hadMatch = true
              break()
            }
          }
        }
        if (!hadMatch) {
          break()
        }
        p = p.get.parent
      }
    }
    count
  }

  def oldestAncestorOfTypeAfter(ancestor: Class[?], after: Class[?]): Nullable[Node] = boundary {
    var p = parent
    var oldestAncestor: Nullable[Node] = Nullable.empty
    while (p.isDefined) {
      if (ancestor.isInstance(p.get)) {
        oldestAncestor = p
      } else if (after.isInstance(p.get)) {
        break(oldestAncestor)
      }
      p = p.get.parent
    }
    oldestAncestor
  }

  def childOfType(classes: Class[?]*): Nullable[Node] = boundary {
    var child = firstChild
    while (child.isDefined) {
      for (nodeType <- classes)
        if (nodeType.isInstance(child.get)) {
          break(child)
        }
      child = child.get.next
    }
    Nullable.empty
  }

  def isOrDescendantOfType(classes: Class[?]*): Boolean = boundary {
    var node: Nullable[Node] = Nullable(this)
    while (node.isDefined) {
      if (Node.getNodeOfTypeIndex(node.get, classes*) != -1) {
        break(true)
      }
      node = node.get.parent
    }
    false
  }

  def nodeOfTypeIndex(classes: Class[?]*): Int =
    Node.getNodeOfTypeIndex(this, classes*)

  /** Overridden by ListBlock and any others whose children propagate their blank line to parent
    *
    * @return
    *   return a child block that can contain the parent's last blank line
    */
  def lastBlankLineChild: Nullable[Node] = Nullable.empty

  def children: ReversiblePeekingIterable[Node] =
    if (_firstChild.isEmpty) {
      NodeIterable.EMPTY
    } else {
      new NodeIterable(_firstChild.get, _lastChild.get, false)
    }

  def reversedChildren: ReversiblePeekingIterable[Node] =
    if (_firstChild.isEmpty) {
      NodeIterable.EMPTY
    } else {
      new NodeIterable(_firstChild.get, _lastChild.get, true)
    }

  def descendants: ReversiblePeekingIterable[Node] =
    if (_firstChild.isEmpty) {
      NodeIterable.EMPTY
    } else {
      new DescendantNodeIterable(children)
    }

  def reversedDescendants: ReversiblePeekingIterable[Node] =
    if (_firstChild.isEmpty) {
      NodeIterable.EMPTY
    } else {
      new DescendantNodeIterable(reversedChildren)
    }

  def childIterator: ReversiblePeekingIterator[Node] =
    if (_firstChild.isEmpty) {
      NodeIterator.EMPTY
    } else {
      new NodeIterator(_firstChild.get, _lastChild.get, false)
    }

  def reversedChildIterator: ReversiblePeekingIterator[Node] =
    if (_firstChild.isEmpty) {
      NodeIterator.EMPTY
    } else {
      new NodeIterator(_firstChild.get, _lastChild.get, true)
    }

  // full document char sequence
  def chars: BasedSequence = _chars

  def removeChildren(): Unit = {
    var child = _firstChild
    while (child.isDefined) {
      val nextChild = child.get.next
      child.get.unlink()
      child = nextChild
    }
  }

  def hasChildren: Boolean = _firstChild.isDefined

  def hasOrMoreChildren(childCount: Int): Boolean = boundary {
    if (_firstChild.isDefined) {
      var count = 0
      val it    = children.iterator()
      while (it.hasNext) {
        it.next()
        count += 1
        if (count >= childCount) {
          break(true)
        }
      }
    }
    false
  }

  def document: Document = {
    var node: Nullable[Node] = Nullable(this)
    while (node.isDefined && !node.get.isInstanceOf[Document])
      node = node.get.parent
    assert(node.isDefined, "Node should always have Document ancestor")
    node.get.asInstanceOf[Document]
  }

  def chars_=(chars: BasedSequence): Unit =
    _chars = chars

  def next: Nullable[Node] = _next

  def lastInChain: Node = {
    var lastNode: Node = this
    while (this.getClass.isInstance(lastNode.next.getOrElse(null.asInstanceOf[Node])))
      lastNode = lastNode.next.get
    lastNode
  }

  def previous: Nullable[Node] = _prev

  def extractToFirstInChain(node: Node): Unit =
    firstInChain.extractChainTo(node)

  def extractChainTo(node: Node): Unit = {
    var lastNode: Nullable[Node] = Nullable(this)
    // Cross-platform: Class.isInstance(null) returns false on JVM but NPEs on Scala Native.
    // Check isDefined before calling isInstance.
    while (lastNode.isDefined && this.getClass.isInstance(lastNode.get)) {
      val n = lastNode.get.next
      node.appendChild(lastNode.get)
      lastNode = n
    }
  }

  def firstInChain: Node = {
    var lastNode: Node = this
    // Cross-platform: same isInstance(null) fix as extractChainTo
    while (lastNode.previous.isDefined && this.getClass.isInstance(lastNode.previous.get))
      lastNode = lastNode.previous.get
    lastNode
  }

  def previousAnyNot(classes: Class[?]*): Nullable[Node] = {
    var node = _prev
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) != -1)
        node = node.get._prev
    }
    node
  }

  def previousAny(classes: Class[?]*): Nullable[Node] = {
    var node = _prev
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) == -1)
        node = node.get._prev
    }
    node
  }

  def nextAnyNot(classes: Class[?]*): Nullable[Node] = {
    var node = _next
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) != -1)
        node = node.get._next
    }
    node
  }

  def nextAny(classes: Class[?]*): Nullable[Node] = {
    var node = _next
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) == -1)
        node = node.get._next
    }
    node
  }

  def firstChild: Nullable[Node] = _firstChild

  def firstChildAnyNot(classes: Class[?]*): Nullable[Node] = {
    var node = _firstChild
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) != -1)
        node = node.get._next
    }
    node
  }

  def firstChildAny(classes: Class[?]*): Nullable[Node] = {
    var node = _firstChild
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) == -1)
        node = node.get._next
    }
    node
  }

  def lastChild: Nullable[Node] = _lastChild

  def lastChildAnyNot(classes: Class[?]*): Nullable[Node] = {
    var node = _lastChild
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) != -1)
        node = node.get._prev
    }
    node
  }

  def lastChildAny(classes: Class[?]*): Nullable[Node] = {
    var node = _lastChild
    if (classes.nonEmpty) {
      while (node.isDefined && Node.getNodeOfTypeIndex(node.get, classes*) == -1)
        node = node.get._prev
    }
    node
  }

  def parent: Nullable[Node] = _parent

  def grandParent: Nullable[Node] =
    _parent.flatMap(_.parent)

  protected def parent_=(parent: Nullable[Node]): Unit =
    _parent = parent

  def appendChild(child: Node): Unit = {
    child.unlink()
    child.parent = Nullable(this)
    if (_lastChild.isDefined) {
      _lastChild.get._next = Nullable(child)
      child._prev = _lastChild
      _lastChild = Nullable(child)
    } else {
      _firstChild = Nullable(child)
      _lastChild = Nullable(child)
    }
  }

  def prependChild(child: Node): Unit = {
    child.unlink()
    child.parent = Nullable(this)
    if (_firstChild.isDefined) {
      _firstChild.get._prev = Nullable(child)
      child._next = _firstChild
      _firstChild = Nullable(child)
    } else {
      _firstChild = Nullable(child)
      _lastChild = Nullable(child)
    }
  }

  def unlink(): Unit = {
    if (_prev.isDefined) {
      _prev.get._next = _next
    } else if (_parent.isDefined) {
      _parent.get._firstChild = _next
    }
    if (_next.isDefined) {
      _next.get._prev = _prev
    } else if (_parent.isDefined) {
      _parent.get._lastChild = _prev
    }
    _parent = Nullable.empty
    _next = Nullable.empty
    _prev = Nullable.empty
  }

  def insertAfter(sibling: Node): Unit = {
    sibling.unlink()

    sibling._next = _next
    if (sibling._next.isDefined) {
      sibling._next.get._prev = Nullable(sibling)
    }

    sibling._prev = Nullable(this)
    _next = Nullable(sibling)
    sibling._parent = _parent
    if (sibling._next.isEmpty) {
      assert(sibling._parent.isDefined)
      sibling._parent.get._lastChild = Nullable(sibling)
    }
  }

  def insertBefore(sibling: Node): Unit = {
    sibling.unlink()
    sibling._prev = _prev
    if (sibling._prev.isDefined) {
      sibling._prev.get._next = Nullable(sibling)
    }
    sibling._next = Nullable(this)
    _prev = Nullable(sibling)
    sibling._parent = _parent
    if (sibling._prev.isEmpty) {
      assert(sibling._parent.isDefined)
      sibling._parent.get._firstChild = Nullable(sibling)
    }
  }

  override def toString: String =
    nodeName + "{" + toStringAttributes + "}"

  def astExtra(out: StringBuilder): Unit = {}

  def astExtraChars(out: StringBuilder): Unit =
    if (chars.length > 0) {
      if (chars.length <= 10) {
        Node.segmentSpanChars(out, chars, "chars")
      } else {
        // give the first 5 and last 5
        Node.segmentSpanChars(
          out,
          chars.startOffset,
          chars.endOffset,
          "chars",
          chars.subSequence(0, 5).toVisibleWhitespaceString(),
          Node.SPLICE,
          chars.subSequence(chars.length - 5).toVisibleWhitespaceString()
        )
      }
    }

  protected def toStringAttributes: String = ""

  def segments: Array[BasedSequence]

  /** Get the segments making up the node's characters.
    *
    * Used to get segments after some of the node's elements were modified
    *
    * @return
    *   array of segments
    */
  def segmentsForChars: Array[BasedSequence] = segments

  /** Get the char sequence from segments making up the node's characters.
    *
    * Used to get segments after some of the node's elements were modified
    *
    * @return
    *   concatenated string of all segments
    */
  def charsFromSegments: BasedSequence = {
    val segs = segmentsForChars
    if (segs.isEmpty) BasedSequence.NULL
    else SegmentedSequence.create(segs(0), java.util.Arrays.asList(segs*))
  }

  /** Set the node's char string from segments making up the node's characters.
    *
    * Used to get segments after some of the node's elements were modified
    */
  def setCharsFromSegments(): Unit =
    chars = charsFromSegments

  def setCharsFromContentOnly(): Unit = {
    _chars = BasedSequence.NULL
    setCharsFromContent()
  }

  def setCharsFromContent(): Unit = {
    val segs = segments
    var spanningChars: Nullable[BasedSequence] = Nullable.empty

    if (segs.length > 0) {
      val leadSegment  = Node.getLeadSegment(segs)
      val trailSegment = Node.getTrailSegment(segs)

      if (_firstChild.isEmpty || _lastChild.isEmpty) {
        val sequences = Array(leadSegment, trailSegment)
        spanningChars = Nullable(Node.spanningChars(sequences*))
      } else {
        val sequences = Array(
          leadSegment,
          trailSegment,
          _firstChild.get._chars,
          _lastChild.get._chars
        )
        spanningChars = Nullable(Node.spanningChars(sequences*))
      }
    } else if (_firstChild.isDefined && _lastChild.isDefined) {
      val sequences = Array(
        _firstChild.get._chars,
        _lastChild.get._chars
      )
      spanningChars = Nullable(Node.spanningChars(sequences*))
    }

    if (spanningChars.isDefined) {
      // see if these are greater than already assigned chars
      if (_chars.isNull) {
        chars = spanningChars.get
      } else {
        val start = Utils.min(_chars.startOffset, spanningChars.get.startOffset)
        val end   = Utils.max(_chars.endOffset, spanningChars.get.endOffset)
        chars = _chars.baseSubSequence(start, end)
      }
    }
  }

  def takeChildren(node: Node): Unit =
    if (node._firstChild.isDefined) {
      val fc = node._firstChild.get
      val lc = node._lastChild.get

      if (lc ne fc) {
        node._firstChild = Nullable.empty
        node._lastChild = Nullable.empty

        fc._parent = Nullable(this)
        lc._parent = Nullable(this)

        if (_lastChild.isDefined) {
          _lastChild.get._next = Nullable(fc)
          fc._prev = _lastChild
        } else {
          _firstChild = Nullable(fc)
        }

        _lastChild = Nullable(lc)
      } else {
        // just a single child
        appendChild(fc)
      }
    }

  def nodeName: String = {
    val name    = getClass.getName
    val lastDot = name.lastIndexOf('.')
    if (lastDot >= 0) name.substring(lastDot + 1) else name
  }

  def astString(out: StringBuilder, withExtra: Boolean): Unit = {
    out.append(nodeName)
    out.append("[").append(startOffset).append(", ").append(endOffset).append("]")
    if (withExtra) astExtra(out)
  }

  def toAstString(withExtra: Boolean): String = {
    val sb = new StringBuilder
    astString(sb, withExtra)
    sb.toString()
  }

  def childChars: BasedSequence =
    if (_firstChild.isEmpty || _lastChild.isEmpty) {
      BasedSequence.NULL
    } else {
      _firstChild.get.baseSubSequence(_firstChild.get.startOffset, _lastChild.get.endOffset)
    }

  def exactChildChars: BasedSequence =
    if (_firstChild.isEmpty || _lastChild.isEmpty) {
      BasedSequence.NULL
    } else {
      // this is not just base sequence between first and last child,
      // which will not include any out-of base chars if they are present, this builds a segmented sequence of child chars
      var child = firstChild
      val segs  = SequenceBuilder.emptyBuilder(chars)

      while (child.isDefined) {
        child.get.chars.addSegments(segs.segmentBuilder)
        child = child.get.next
      }

      segs.toSequence
    }

  def blankLineSibling: Node = boundary {
    // need to find the first node that can contain a blank line that is not the last non-blank line of its parent
    assert(_parent.isDefined)

    var p:                    Node = _parent.get
    var lastBlankLineSibling: Node = this
    var nextBlankLineSibling: Node = this

    while (p._parent.isDefined) {
      val wasLastItem = p eq p._parent.get.lastChildAnyNot(classOf[BlankLine]).getOrElse(null.asInstanceOf[Node])
      if (!wasLastItem) {
        break(lastBlankLineSibling)
      }

      lastBlankLineSibling = nextBlankLineSibling
      if (p.isInstanceOf[BlankLineContainer]) {
        nextBlankLineSibling = p
      }

      p = p._parent.get
    }

    lastBlankLineSibling
  }

  def moveTrailingBlankLines(): Unit = {
    val bl = lastChild
    if (bl.isDefined && bl.get.isInstanceOf[BlankLine]) {
      val blankLinePos   = blankLineSibling
      val firstInChainBl = bl.get.firstInChain
      blankLinePos.insertChainAfter(firstInChainBl)

      var p: Nullable[Node] = Nullable(this)
      while (p.isDefined && (p.get ne blankLinePos.parent.getOrElse(null.asInstanceOf[Node]))) {
        p.get.setCharsFromContentOnly()
        p = p.get._parent
      }
    }
  }

  def lineNumber: Int = startLineNumber

  def startLineNumber: Int = document.getLineNumber(chars.startOffset)

  def endLineNumber: Int = {
    val eo = chars.endOffset
    document.getLineNumber(if (eo > 0) eo - 1 else eo)
  }

  /** Append all from child to end of chain to this node
    *
    * @param firstNode
    *   first child in chain
    */
  def appendChain(firstNode: Node): Unit = {
    var node: Nullable[Node] = Nullable(firstNode)
    while (node.isDefined) {
      val n = node.get._next
      appendChild(node.get)
      node = n
    }
  }

  /** Insert chain after this node
    *
    * @param firstNode
    *   first child in chain
    */
  def insertChainAfter(firstNode: Node): Unit = {
    var posNode: Node           = this
    var node:    Nullable[Node] = Nullable(firstNode)
    while (node.isDefined) {
      val n = node.get._next
      posNode.insertAfter(node.get)
      posNode = node.get
      node = n
    }
  }

  /** Insert chain before this node
    *
    * @param firstNode
    *   first child in chain
    */
  def insertChainBefore(firstNode: Node): Unit = {
    val posNode: Node           = this
    var node:    Nullable[Node] = Nullable(firstNode)
    while (node.isDefined) {
      val n = node.get._next
      posNode.insertBefore(node.get)
      node = n
    }
  }
}

object Node {
  val EMPTY_SEGMENTS: Array[BasedSequence] = BasedSequence.EMPTY_ARRAY
  val SPLICE:         String               = " \u2026 "

  val AST_ADAPTER: AstNode[Node] = new AstNode[Node] {
    def firstChild(node: Node): Nullable[Node] = node._firstChild
    def next(node:       Node): Nullable[Node] = node._next
  }

  def getNodeOfTypeIndex(node: Node, classes: Class[?]*): Int = boundary {
    var i = 0
    for (nodeType <- classes) {
      if (nodeType.isInstance(node)) {
        break(i)
      }
      i += 1
    }
    -1
  }

  def getLeadSegment(segments: Array[BasedSequence]): BasedSequence = boundary {
    for (segment <- segments)
      if (segment ne BasedSequence.NULL) {
        break(segment)
      }
    BasedSequence.NULL
  }

  def getTrailSegment(segments: Array[BasedSequence]): BasedSequence = boundary {
    var i = segments.length
    while (i > 0) {
      i -= 1
      val segment = segments(i)
      if (segment ne BasedSequence.NULL) {
        break(segment)
      }
    }
    BasedSequence.NULL
  }

  def spanningChars(segments: BasedSequence*): BasedSequence = {
    var startOffset = Int.MaxValue
    var endOffset   = -1
    var firstSequence: Nullable[BasedSequence] = Nullable.empty
    var lastSequence:  Nullable[BasedSequence] = Nullable.empty
    for (segment <- segments)
      if (segment ne BasedSequence.NULL) {
        if (startOffset > segment.startOffset) {
          startOffset = segment.startOffset
          firstSequence = Nullable(segment)
        }
        if (endOffset <= segment.endOffset) {
          endOffset = segment.endOffset
          lastSequence = Nullable(segment)
        }
      }

    if (firstSequence.isDefined && lastSequence.isDefined) {
      firstSequence.get.baseSubSequence(firstSequence.get.startOffset, lastSequence.get.endOffset)
    } else {
      BasedSequence.NULL
    }
  }

  def segmentSpan(out: StringBuilder, startOffset: Int, endOffset: Int, name: Nullable[String]): Unit = {
    if (name.isDefined && name.get.trim.nonEmpty) out.append(" ").append(name.get).append(":")
    out.append("[").append(startOffset).append(", ").append(endOffset).append("]")
  }

  def segmentSpanChars(out: StringBuilder, startOffset: Int, endOffset: Int, name: Nullable[String], chars: String): Unit =
    segmentSpanChars(out, startOffset, endOffset, name, chars, "", "")

  def segmentSpanChars(
    out:         StringBuilder,
    startOffset: Int,
    endOffset:   Int,
    name:        Nullable[String],
    chars1:      String,
    splice:      String,
    chars2:      String
  ): Unit = {
    if (name.isDefined && name.get.trim.nonEmpty) out.append(" ").append(name.get).append(":")
    out.append("[").append(startOffset).append(", ").append(endOffset)
    if (chars1.nonEmpty || chars2.nonEmpty) {
      out.append(", \"")
      Utils.escapeJavaString(out, chars1)
      out.append(splice)
      Utils.escapeJavaString(out, chars2)
      out.append("\"")
    }
    out.append("]")
  }

  def segmentSpan(out: StringBuilder, sequence: BasedSequence, name: Nullable[String]): Unit =
    if (sequence.isNotNull) segmentSpan(out, sequence.startOffset, sequence.endOffset, name)

  def segmentSpanChars(out: StringBuilder, sequence: BasedSequence, name: String): Unit =
    if (sequence.isNotNull) {
      segmentSpanChars(out, sequence.startOffset, sequence.endOffset, Nullable(name), sequence.toString)
    }

  def segmentSpanCharsToVisible(out: StringBuilder, sequence: BasedSequence, name: String): Unit =
    if (sequence.isNotNull) {
      if (sequence.length <= 10) {
        segmentSpanChars(out, sequence.startOffset, sequence.endOffset, Nullable(name), sequence.toVisibleWhitespaceString())
      } else {
        // give the first 5 and last 5
        segmentSpanChars(
          out,
          sequence.startOffset,
          sequence.endOffset,
          Nullable(name),
          sequence.subSequence(0, 5).toVisibleWhitespaceString(),
          SPLICE,
          sequence.endSequence(sequence.length - 5).toVisibleWhitespaceString()
        )
      }
    }

  def delimitedSegmentSpan(
    out:             StringBuilder,
    openingSequence: BasedSequence,
    sequence:        BasedSequence,
    closingSequence: BasedSequence,
    name:            String
  ): Unit = {
    segmentSpanChars(out, openingSequence.startOffset, openingSequence.endOffset, Nullable(name + "Open"), openingSequence.toString)
    if (sequence.length <= 10) {
      segmentSpanChars(out, sequence.startOffset, sequence.endOffset, Nullable(name), sequence.toVisibleWhitespaceString())
    } else {
      // give the first 5 and last 5
      segmentSpanChars(
        out,
        sequence.startOffset,
        sequence.endOffset,
        Nullable(name),
        sequence.subSequence(0, 5).toVisibleWhitespaceString(),
        SPLICE,
        sequence.endSequence(sequence.length - 5).toVisibleWhitespaceString()
      )
    }
    segmentSpanChars(out, closingSequence.startOffset, closingSequence.endOffset, Nullable(name + "Close"), closingSequence.toString)
  }

  def delimitedSegmentSpanChars(
    out:             StringBuilder,
    openingSequence: BasedSequence,
    sequence:        BasedSequence,
    closingSequence: BasedSequence,
    name:            String
  ): Unit = {
    if (openingSequence.isNotNull) {
      segmentSpanChars(out, openingSequence.startOffset, openingSequence.endOffset, Nullable(name + "Open"), openingSequence.toString)
    }
    if (sequence.isNotNull) {
      segmentSpanChars(out, sequence.startOffset, sequence.endOffset, Nullable(name), sequence.toVisibleWhitespaceString())
    }
    if (closingSequence.isNotNull) {
      segmentSpanChars(out, closingSequence.startOffset, closingSequence.endOffset, Nullable(name + "Close"), closingSequence.toString)
    }
  }

  def astChars(out: StringBuilder, chars: CharSequence, name: String): Unit =
    if (chars.length > 0) {
      if (chars.length <= 10) {
        out.append(' ').append(name).append(" \"").append(chars).append("\"")
      } else {
        // give the first 5 and last 5
        out.append(' ').append(name).append(" \"").append(chars.subSequence(0, 5)).append(SPLICE).append(chars.subSequence(chars.length - 5, chars.length)).append("\"")
      }
    }

  def toSegmentSpan(sequence: BasedSequence, name: Nullable[String]): String = {
    val out = new StringBuilder
    segmentSpan(out, sequence, name)
    out.toString()
  }
}
