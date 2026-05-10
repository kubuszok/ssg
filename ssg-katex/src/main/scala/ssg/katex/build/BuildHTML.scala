/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file does the main work of building a domTree structure from a parse
 * tree. The entry point is the `buildHTML` function, which takes a parse tree.
 * Then, the buildExpression, buildGroup, and various groupBuilders functions
 * are called, to produce a final HTML tree.
 *
 * Original source: katex src/buildHTML.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: buildHTML -> BuildHTML (object)
 *   Convention: DomType string enum -> DomEnum map lookup
 *   Idiom: TypeScript destructuring callback -> explicit parameters
 */
package ssg
package katex
package build

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.ParseError
import ssg.katex.data.{ Measurement, SpacingData, Units }
import ssg.katex.functions.FunctionDef
import ssg.katex.parse.AnyParseNode
import ssg.katex.tree.{ Anchor, DocumentFragment, DomSpan, HtmlDomNode, Span }

object BuildHTML {

  // Binary atoms (first class `mbin`) change into ordinary atoms (`mord`)
  // depending on their surroundings. See TeXbook pg. 442-446, Rules 5 and 6,
  // and the text before Rule 19.
  private val binLeftCanceller: Set[String] = Set(
    "leftmost",
    "mbin",
    "mopen",
    "mrel",
    "mop",
    "mpunct"
  )
  private val binRightCanceller: Set[String] = Set(
    "rightmost",
    "mrel",
    "mclose",
    "mpunct"
  )

  private val styleMap: Map[String, Style] = Map(
    "display" -> Style.DISPLAY,
    "text" -> Style.TEXT,
    "script" -> Style.SCRIPT,
    "scriptscript" -> Style.SCRIPTSCRIPT
  )

  private val DomEnum: Map[String, String] = Map(
    "mord" -> "mord",
    "mop" -> "mop",
    "mbin" -> "mbin",
    "mrel" -> "mrel",
    "mopen" -> "mopen",
    "mclose" -> "mclose",
    "mpunct" -> "mpunct",
    "minner" -> "minner"
  )

  /** Take a list of nodes, build them in order, and return a list of the built nodes. documentFragments are flattened into their contents, so the returned list contains no fragments. `isRealGroup` is
    * true if `expression` is a real group (no atoms will be added on either side), as opposed to a partial group (e.g. one created by \color). `surrounding` is an array consisting type of nodes that
    * will be added to the left and right.
    */
  def buildExpression(
    expression:  Array[AnyParseNode],
    options:     Options,
    isRealGroup: Boolean,
    isRoot:      Boolean = false,
    surrounding: (Nullable[String], Nullable[String]) = (Nullable.Null, Nullable.Null)
  ): ArrayBuffer[HtmlDomNode] = boundary {
    // Parse expressions into `groups`.
    val groups = ArrayBuffer.empty[HtmlDomNode]
    var i      = 0
    while (i < expression.length) {
      val output = buildGroup(expression(i), options)
      output match {
        case frag: DocumentFragment[?] =>
          val children = frag.children
          var j        = 0
          while (j < children.length) {
            groups += children(j).asInstanceOf[HtmlDomNode]
            j += 1
          }
        case node =>
          groups += node
      }
      i += 1
    }

    // Combine consecutive domTree.symbolNodes into a single symbolNode.
    BuildCommon.tryCombineChars(groups)

    // If `expression` is a partial group, let the parent handle spacings
    // to avoid processing groups multiple times.
    if (!isRealGroup) {
      break(groups)
    }

    var glueOptions = options
    if (expression.length == 1) {
      val node = expression(0)
      if (node.nodeType == "sizing") {
        glueOptions = options.havingSize(node.asInstanceOf[ssg.katex.parse.ParseNodeSizing].size)
      } else if (node.nodeType == "styling") {
        glueOptions = options.havingStyle(styleMap(node.asInstanceOf[ssg.katex.parse.ParseNodeStyling].style.value))
      }
    }

    // Dummy spans for determining spacings between surrounding atoms.
    // If `expression` has no atoms on the left or right, class "leftmost"
    // or "rightmost", respectively, is used to indicate it.
    val dummyPrev = BuildCommon.makeSpan(ArrayBuffer(surrounding._1.getOrElse("leftmost")), ArrayBuffer.empty, Nullable(options))
    val dummyNext = BuildCommon.makeSpan(ArrayBuffer(surrounding._2.getOrElse("rightmost")), ArrayBuffer.empty, Nullable(options))

    // TODO: These code assumes that a node's math class is the first element
    // of its `classes` array. A later cleanup should ensure this, for
    // instance by changing the signature of `makeSpan`.

    // Before determining what spaces to insert, perform bin cancellation.
    // Binary operators change to ordinary symbols in some contexts.
    val isRootFlag = isRoot
    traverseNonSpaceNodes(
      groups,
      (node, prev) => {
        // In JS, accessing classes[0] on an empty array yields undefined,
        // which silently makes all comparisons false. Guard here.
        if (prev.classes.nonEmpty && node.classes.nonEmpty) {
          val prevType = prev.classes(0)
          val nodeType = node.classes(0)
          if (prevType == "mbin" && binRightCanceller.contains(nodeType)) {
            prev.classes(0) = "mord"
          } else if (nodeType == "mbin" && binLeftCanceller.contains(prevType)) {
            node.classes(0) = "mord"
          }
        }
        Nullable.Null
      },
      dummyPrev,
      dummyNext,
      isRootFlag
    )

    traverseNonSpaceNodes(
      groups,
      (node, prev) => {
        val prevType = getTypeOfDomTree(Nullable(prev))
        val nodeType = getTypeOfDomTree(Nullable(node))

        // 'mtight' indicates that the node is script or scriptscript style.
        val space: Nullable[Measurement] = if (prevType.isDefined && nodeType.isDefined) {
          if (node.hasClass("mtight")) {
            SpacingData.tightSpacings.get(prevType.get).flatMap(_.get(nodeType.get)) match {
              case Some(m) => Nullable(m)
              case None    => Nullable.Null
            }
          } else {
            SpacingData.spacings.get(prevType.get).flatMap(_.get(nodeType.get)) match {
              case Some(m) => Nullable(m)
              case None    => Nullable.Null
            }
          }
        } else {
          Nullable.Null
        }

        if (space.isDefined) { // Insert glue (spacing) after the `prev`.
          Nullable(BuildCommon.makeGlue(space.get, glueOptions))
        } else {
          Nullable.Null
        }
      },
      dummyPrev,
      dummyNext,
      isRootFlag
    )

    groups
  }

  // Depth-first traverse non-space `nodes`, calling `callback` with the current and
  // previous node as arguments, optionally returning a node to insert after the
  // previous node. `prev` is an object with the previous node and `insertAfter`
  // function to insert after it. `next` is a node that will be added to the right.
  // Used for bin cancellation and inserting spacings.
  private def traverseNonSpaceNodes(
    nodes:        ArrayBuffer[HtmlDomNode],
    callback:     (HtmlDomNode, HtmlDomNode) => Nullable[HtmlDomNode],
    prevNodeInit: HtmlDomNode,
    next:         HtmlDomNode,
    isRoot:       Boolean
  ): Unit = {
    var prevNode = prevNodeInit
    var prevInsertAfter: Nullable[(HtmlDomNode) => Unit] = Nullable.Null

    // temporarily append the right node, if exists
    nodes += next

    var i = 0
    while (i < nodes.length) {
      val node         = nodes(i)
      val partialGroup = checkPartialGroup(node)

      if (partialGroup.isDefined) { // Recursive DFS
        // TODO(ts): make nodes a $ReadOnlyArray by returning a new array
        val pg         = partialGroup.get
        val pgChildren = pg match {
          case f: DocumentFragment[?] => ArrayBuffer.from(f.children.map(_.asInstanceOf[HtmlDomNode]))
          case a: Anchor              => ArrayBuffer.from(a.children)
          case s: Span[?]             => ArrayBuffer.from(s.children.map(_.asInstanceOf[HtmlDomNode]))
          case _ => ArrayBuffer.empty[HtmlDomNode]
        }
        traverseNonSpaceNodesInner(pgChildren, callback, prevNode, prevInsertAfter, isRoot) match {
          case (newPrev, newInsert) =>
            prevNode = newPrev
            prevInsertAfter = newInsert
        }
        i += 1
      } else {
        // Ignore explicit spaces (e.g., \;, \,) when determining what implicit
        // spacing should go between atoms of different classes
        val nonspace = !node.hasClass("mspace")
        if (nonspace) {
          val result = callback(node, prevNode)
          result.foreach { r =>
            prevInsertAfter.fold {
              // insert at front
              nodes.insert(0, r)
              i += 1
            } { insertFn =>
              insertFn(r)
              i += 1
            }
          }
        }

        if (nonspace) {
          prevNode = node
        } else if (isRoot && node.hasClass("newline")) {
          prevNode = BuildCommon.makeSpan(ArrayBuffer("leftmost")) // treat like beginning of line
        }
        val capturedI = i
        prevInsertAfter = Nullable { (n: HtmlDomNode) =>
          nodes.insert(capturedI + 1, n)
          i += 1
        }
        i += 1
      }
    }

    // Remove the temporarily appended next node
    nodes.remove(nodes.length - 1)
  }

  // Inner traversal helper that handles children of partial groups
  private def traverseNonSpaceNodesInner(
    nodes:               ArrayBuffer[HtmlDomNode],
    callback:            (HtmlDomNode, HtmlDomNode) => Nullable[HtmlDomNode],
    prevNodeInit:        HtmlDomNode,
    prevInsertAfterInit: Nullable[(HtmlDomNode) => Unit],
    isRoot:              Boolean
  ): (HtmlDomNode, Nullable[(HtmlDomNode) => Unit]) = {
    var prevNode        = prevNodeInit
    var prevInsertAfter = prevInsertAfterInit

    var i = 0
    while (i < nodes.length) {
      val node         = nodes(i)
      val partialGroup = checkPartialGroup(node)

      if (partialGroup.isDefined) {
        val pg         = partialGroup.get
        val pgChildren = pg match {
          case f: DocumentFragment[?] => ArrayBuffer.from(f.children.map(_.asInstanceOf[HtmlDomNode]))
          case a: Anchor              => ArrayBuffer.from(a.children)
          case s: Span[?]             => ArrayBuffer.from(s.children.map(_.asInstanceOf[HtmlDomNode]))
          case _ => ArrayBuffer.empty[HtmlDomNode]
        }
        val (newPrev, newInsert) = traverseNonSpaceNodesInner(pgChildren, callback, prevNode, prevInsertAfter, isRoot)
        prevNode = newPrev
        prevInsertAfter = newInsert
      } else {
        val nonspace = !node.hasClass("mspace")
        if (nonspace) {
          val result = callback(node, prevNode)
          result.foreach { r =>
            prevInsertAfter.fold {
              nodes.insert(0, r)
              i += 1
            } { insertFn =>
              insertFn(r)
              i += 1
            }
          }
        }

        if (nonspace) {
          prevNode = node
        } else if (isRoot && node.hasClass("newline")) {
          prevNode = BuildCommon.makeSpan(ArrayBuffer("leftmost"))
        }
        val capturedI = i
        prevInsertAfter = Nullable((n: HtmlDomNode) => nodes.insert(capturedI + 1, n))
      }
      i += 1
    }

    (prevNode, prevInsertAfter)
  }

  // Check if given node is a partial group, i.e., does not affect spacing around.
  private def checkPartialGroup(
    node: HtmlDomNode
  ): Nullable[HtmlDomNode] =
    node match {
      case _: DocumentFragment[?]                => Nullable(node)
      case _: Anchor                             => Nullable(node)
      case s: Span[?] if s.hasClass("enclosing") => Nullable(node)
      case _ => Nullable.Null
    }

  // Return the outermost node of a domTree.
  private def getOutermostNode(
    node: HtmlDomNode,
    side: String // "left" | "right"
  ): HtmlDomNode = {
    val partialGroup = checkPartialGroup(node)
    partialGroup.fold {
      node
    } { pg =>
      val children: IndexedSeq[HtmlDomNode] = pg match {
        case f: DocumentFragment[?] => f.children.map(_.asInstanceOf[HtmlDomNode])
        case a: Anchor              => a.children.toIndexedSeq
        case s: Span[?]             => s.children.map(_.asInstanceOf[HtmlDomNode]).toIndexedSeq
        case _ => IndexedSeq.empty
      }
      if (children.nonEmpty) {
        if (side == "right") {
          getOutermostNode(children.last, "right")
        } else {
          getOutermostNode(children.head, "left")
        }
      } else {
        node
      }
    }
  }

  // Return math atom class (mclass) of a domTree.
  // If `side` is given, it will get the type of the outermost node at given side.
  def getTypeOfDomTree(
    node: Nullable[HtmlDomNode],
    side: Nullable[String] = Nullable.Null
  ): Nullable[String] = boundary {
    if (node.isEmpty) {
      break(Nullable.Null)
    }
    var n = node.get
    side.foreach { s =>
      n = getOutermostNode(n, s)
    }
    // This makes a lot of assumptions as to where the type of atom
    // appears.  We should do a better job of enforcing this.
    if (n.classes.nonEmpty) {
      val className = n.classes(0)
      DomEnum.get(className) match {
        case Some(dt) => Nullable(dt)
        case None     => Nullable.Null
      }
    } else {
      Nullable.Null
    }
  }

  def makeNullDelimiter(
    options: Options,
    classes: Array[String]
  ): DomSpan = {
    val moreClasses = ArrayBuffer("nulldelimiter") ++ options.baseSizingClasses()
    BuildCommon.makeSpan(ArrayBuffer.from(classes) ++ moreClasses)
  }

  /** buildGroup is the function that takes a group and calls the correct groupType function for it. It also handles the interaction of size and style changes between parents and children.
    */
  def buildGroup(
    group:       Nullable[AnyParseNode],
    options:     Options,
    baseOptions: Nullable[Options] = Nullable.Null
  ): HtmlDomNode = boundary {
    if (group.isEmpty) {
      break(BuildCommon.makeSpan())
    }

    val g             = group.get
    val groupBuilders = FunctionDef._htmlGroupBuilders

    if (groupBuilders.contains(g.nodeType)) {
      // Call the groupBuilders function
      // TODO(ts)
      var groupNode: HtmlDomNode =
        groupBuilders(g.nodeType)(g, options.asInstanceOf[AnyRef]).asInstanceOf[HtmlDomNode]

      // If the size changed between the parent and the current group, account
      // for that size difference.
      if (baseOptions.isDefined && options.size != baseOptions.get.size) {
        groupNode = BuildCommon.makeSpan(ArrayBuffer.from(options.sizingClasses(baseOptions.get)), ArrayBuffer(groupNode), Nullable(options))

        val multiplier =
          options.sizeMultiplier / baseOptions.get.sizeMultiplier

        groupNode.height *= multiplier
        groupNode.depth *= multiplier
      }

      groupNode
    } else {
      throw new ParseError("Got group of unknown type: '" + g.nodeType + "'")
    }
  }

  /** Combine an array of HTML DOM nodes (e.g., the output of `buildExpression`) into an unbreakable HTML node of class .base, with proper struts to guarantee correct vertical extent. `buildHTML`
    * calls this repeatedly to make up the entire expression as a sequence of unbreakable units.
    */
  private def buildHTMLUnbreakable(
    children: ArrayBuffer[HtmlDomNode],
    options:  Options
  ): DomSpan = {
    // Compute height and depth of this chunk.
    val body = BuildCommon.makeSpan(ArrayBuffer("base"), children, Nullable(options))

    // Add strut, which ensures that the top of the HTML element falls at
    // the height of the expression, and the bottom of the HTML element
    // falls at the depth of the expression.
    val strut = BuildCommon.makeSpan(ArrayBuffer("strut"))
    strut.style = strut.style.copy(
      height = Nullable(Units.makeEm(body.height + body.depth))
    )
    if (body.depth != 0) {
      strut.style = strut.style.copy(
        verticalAlign = Nullable(Units.makeEm(-body.depth))
      )
    }
    body.children.insert(0, strut)

    body
  }

  /** Take an entire parse tree, and build it into an appropriate set of HTML nodes.
    */
  def buildHTML(tree: Array[AnyParseNode], options: Options): DomSpan = {
    var treeNodes = tree
    // Strip off outer tag wrapper for processing below.
    var tag: Nullable[Array[AnyParseNode]] = Nullable.Null
    if (treeNodes.length == 1 && treeNodes(0).nodeType == "tag") {
      val tagNode = treeNodes(0).asInstanceOf[ssg.katex.parse.ParseNodeTag]
      tag = Nullable(tagNode.tag)
      treeNodes = tagNode.body
    }

    // Build the expression contained in the tree
    val expression = buildExpression(treeNodes, options, isRealGroup = true, isRoot = true)

    var eqnNum: Nullable[HtmlDomNode] = Nullable.Null
    if (expression.length == 2 && expression(1).hasClass("tag")) {
      // An environment with automatic equation numbers, e.g. {gather}.
      eqnNum = Nullable(expression.remove(expression.length - 1))
    }

    val children = ArrayBuffer.empty[HtmlDomNode]

    // Create one base node for each chunk between potential line breaks.
    // The TeXBook [p.173] says "A formula will be broken only after a
    // relation symbol like $=$ or $<$ or $\rightarrow$, or after a binary
    // operation symbol like $+$ or $-$ or $\times$, where the relation or
    // binary operation is on the ``outer level'' of the formula (i.e., not
    // enclosed in {...} and not part of an \over construction)."

    var parts = ArrayBuffer.empty[HtmlDomNode]
    var i     = 0
    while (i < expression.length) {
      parts += expression(i)
      if (
        expression(i).hasClass("mbin") ||
        expression(i).hasClass("mrel") ||
        expression(i).hasClass("allowbreak")
      ) {
        // Put any post-operator glue on same line as operator.
        // Watch for \nobreak along the way, and stop at \newline.
        var nobreak = false
        while (
          i < expression.length - 1 &&
          expression(i + 1).hasClass("mspace") &&
          !expression(i + 1).hasClass("newline")
        ) {
          i += 1
          parts += expression(i)
          if (expression(i).hasClass("nobreak")) {
            nobreak = true
          }
        }
        // Don't allow break if \nobreak among the post-operator glue.
        if (!nobreak) {
          children += buildHTMLUnbreakable(parts, options)
          parts = ArrayBuffer.empty
        }
      } else if (expression(i).hasClass("newline")) {
        // Write the line except the newline
        parts.remove(parts.length - 1)
        if (parts.nonEmpty) {
          children += buildHTMLUnbreakable(parts, options)
          parts = ArrayBuffer.empty
        }
        // Put the newline at the top level
        children += expression(i)
      }
      i += 1
    }
    if (parts.nonEmpty) {
      children += buildHTMLUnbreakable(parts, options)
    }

    // Now, if there was a tag, build it too and append it as a final child.
    var tagChild: Nullable[DomSpan] = Nullable.Null
    tag.foreach { t =>
      val tc = buildHTMLUnbreakable(
        buildExpression(t, options, isRealGroup = true),
        options
      )
      tc.classes = ArrayBuffer("tag")
      children += tc
      tagChild = Nullable(tc)
    }
    if (tagChild.isEmpty) {
      eqnNum.foreach { eq =>
        children += eq
      }
    }

    val htmlNode = BuildCommon.makeSpan(ArrayBuffer("katex-html"), children)
    htmlNode.setAttribute("aria-hidden", "true")

    // Adjust the strut of the tag to be the maximum height of all children
    // (the height of the enclosing htmlNode) for proper vertical alignment.
    tagChild.foreach { tc =>
      val strut = tc.children(0)
      strut.style = strut.style.copy(
        height = Nullable(Units.makeEm(htmlNode.height + htmlNode.depth))
      )
      if (htmlNode.depth != 0) {
        strut.style = strut.style.copy(
          verticalAlign = Nullable(Units.makeEm(-htmlNode.depth))
        )
      }
    }

    htmlNode
  }
}
