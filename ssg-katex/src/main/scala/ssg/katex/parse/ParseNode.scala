/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Parse node type hierarchy for the KaTeX parser.
 *
 * Each parse node type from the original TypeScript mapped type becomes a
 * final case class extending the sealed AnyParseNode trait. The node type
 * string is stored in `nodeType`.
 *
 * Original source: katex src/parseNode.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: ParseNodeTypes mapped type -> sealed trait hierarchy
 *   Convention: TypeScript union type for "op" -> single class with Nullable fields
 *   Idiom: TypeScript optional fields -> Nullable[A]
 */
package ssg
package katex
package parse

import scala.collection.mutable

import ssg.commons.Nullable
import ssg.katex.data.{Measurement, Symbols}

// ParseNode's corresponding to Symbol `Group`s in symbols.ts.
// (Some of these have "-token" suffix to distinguish them from existing
// `ParseNode` types.)
type NodeType = String

/** Union of all possible parse node types. */
sealed trait AnyParseNode extends SourceLocation.HasLoc {
  /** The node type string (e.g. "ordgroup", "color", "font", "mathord", etc.). */
  def nodeType: String

  var mode: Mode

  var loc: Nullable[SourceLocation]

  // --- Back-compat bridge used by Utils.getBaseElem / isCharacterBox ---
  // (These methods provide the minimal shape the old ParseNode trait had.)

  /** For nodes that carry a body as a sequence of child nodes (ordgroup, color, etc.). */
  def bodyNodes: Seq[AnyParseNode] = Seq.empty

  /** For nodes that carry a single body node (font, etc.). */
  def bodyNode: Nullable[AnyParseNode] = Nullable.Null
}

// ParseNode's corresponding to Symbol `Group`s in symbols.ts.
// SymbolParseNode = atom | accent-token | mathord | op-token | spacing | textord
sealed trait SymbolParseNode extends AnyParseNode {
  def text: String
}

// ParseNode from `Parser.formatUnsupportedCmd`
type UnsupportedCmdParseNode = ParseNodeColor

// -----------------------------------------------------------------------
// Array
// -----------------------------------------------------------------------

/** Separator or alignment spec for array columns. */
enum AlignSpec {
  case Separator(separator: String)
  case Align(align: String, pregap: Double = 0, postgap: Double = 0)
}

/** Type to indicate column separation in MathML. */
type ColSeparationType = String
// Valid values: "align", "alignat", "gather", "small", "CD"

final case class ParseNodeArray(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var colSeparationType: Nullable[ColSeparationType] = Nullable.Null,
    var hskipBeforeAndAfter: Nullable[Boolean] = Nullable.Null,
    var addJot: Nullable[Boolean] = Nullable.Null,
    var cols: Nullable[Array[AlignSpec]] = Nullable.Null,
    var arraystretch: Double = 1.0,
    var body: Array[Array[AnyParseNode]] = Array.empty,
    // List of rows in the (2D) array.
    var rowGaps: Array[Nullable[Measurement]] = Array.empty,
    var hLinesBeforeRow: Array[Array[Boolean]] = Array.empty,
    // Whether each row should be automatically numbered, or an explicit tag
    var tags: Nullable[Array[Either[Boolean, Array[AnyParseNode]]]] = Nullable.Null,
    var leqno: Nullable[Boolean] = Nullable.Null,
    var isCD: Nullable[Boolean] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "array"
}

// -----------------------------------------------------------------------
// cdlabel
// -----------------------------------------------------------------------

final case class ParseNodeCdlabel(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var side: String = "",
    var label: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "cdlabel"
}

// -----------------------------------------------------------------------
// cdlabelparent
// -----------------------------------------------------------------------

final case class ParseNodeCdlabelparent(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var fragment: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "cdlabelparent"
}

// -----------------------------------------------------------------------
// color
// -----------------------------------------------------------------------

final case class ParseNodeColor(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var color: String = "",
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "color"
  override def bodyNodes: Seq[AnyParseNode] = body.toSeq
}

// -----------------------------------------------------------------------
// color-token
// -----------------------------------------------------------------------

final case class ParseNodeColorToken(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var color: String = ""
) extends AnyParseNode {
  override def nodeType: String = "color-token"
}

// -----------------------------------------------------------------------
// op
// -----------------------------------------------------------------------

// To avoid requiring run-time type assertions, this more carefully captures
// the requirements on the fields per the op.js htmlBuilder logic:
// - `body` and `name` are NEVER set simultaneously.
// - When `symbol` is true, `name` is set (body is void).
// - When `symbol` is false, `body` is set (name is void).
// In Scala we model this as a single class with Nullable fields.
final case class ParseNodeOp(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var limits: Boolean = false,
    var alwaysHandleSupSub: Nullable[Boolean] = Nullable.Null,
    var suppressBaseShift: Nullable[Boolean] = Nullable.Null,
    var parentIsSupSub: Boolean = false,
    var symbol: Boolean = false,
    // If 'symbol' is true, `name` is set; if false, `body` is set.
    var name: Nullable[String] = Nullable.Null,
    var body: Nullable[Array[AnyParseNode]] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "op"
}

// -----------------------------------------------------------------------
// ordgroup
// -----------------------------------------------------------------------

final case class ParseNodeOrdgroup(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: Array[AnyParseNode] = Array.empty,
    var semisimple: Nullable[Boolean] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "ordgroup"
  override def bodyNodes: Seq[AnyParseNode] = body.toSeq
}

// -----------------------------------------------------------------------
// raw
// -----------------------------------------------------------------------

final case class ParseNodeRaw(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var string: String = ""
) extends AnyParseNode {
  override def nodeType: String = "raw"
}

// -----------------------------------------------------------------------
// size
// -----------------------------------------------------------------------

final case class ParseNodeSize(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var value: Measurement = Measurement(0, ""),
    var isBlank: Boolean = false
) extends AnyParseNode {
  override def nodeType: String = "size"
}

// -----------------------------------------------------------------------
// styling
// -----------------------------------------------------------------------

final case class ParseNodeStyling(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var style: StyleStr = StyleStr.TextStyle,
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "styling"
}

// -----------------------------------------------------------------------
// supsub
// -----------------------------------------------------------------------

final case class ParseNodeSupsub(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var base: Nullable[AnyParseNode] = Nullable.Null,
    var sup: Nullable[AnyParseNode] = Nullable.Null,
    var sub: Nullable[AnyParseNode] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "supsub"
}

// -----------------------------------------------------------------------
// tag
// -----------------------------------------------------------------------

final case class ParseNodeTag(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: Array[AnyParseNode] = Array.empty,
    var tag: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "tag"
}

// -----------------------------------------------------------------------
// text
// -----------------------------------------------------------------------

final case class ParseNodeText(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: Array[AnyParseNode] = Array.empty,
    var font: Nullable[String] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "text"
}

// -----------------------------------------------------------------------
// url
// -----------------------------------------------------------------------

final case class ParseNodeUrl(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var url: String = ""
) extends AnyParseNode {
  override def nodeType: String = "url"
}

// -----------------------------------------------------------------------
// verb
// -----------------------------------------------------------------------

final case class ParseNodeVerb(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: String = "",
    var star: Boolean = false
) extends AnyParseNode {
  override def nodeType: String = "verb"
}

// -----------------------------------------------------------------------
// Symbol parse nodes (from symbol groups, constructed in Parser via
// `symbols` lookup)
// -----------------------------------------------------------------------

// atom
final case class ParseNodeAtom(
    var family: String = "", // Atom type: bin, close, inner, open, punct, rel
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var text: String = ""
) extends SymbolParseNode {
  override def nodeType: String = "atom"
}

// mathord
final case class ParseNodeMathord(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var text: String = ""
) extends SymbolParseNode {
  override def nodeType: String = "mathord"
}

// spacing
final case class ParseNodeSpacing(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var text: String = ""
) extends SymbolParseNode {
  override def nodeType: String = "spacing"
}

// textord
final case class ParseNodeTextord(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var text: String = ""
) extends SymbolParseNode {
  override def nodeType: String = "textord"
}

// -----------------------------------------------------------------------
// Token-suffix types (don't have corresponding HTML/MathML builders)
// -----------------------------------------------------------------------

// accent-token
final case class ParseNodeAccentToken(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var text: String = ""
) extends SymbolParseNode {
  override def nodeType: String = "accent-token"
}

// op-token
final case class ParseNodeOpToken(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var text: String = ""
) extends SymbolParseNode {
  override def nodeType: String = "op-token"
}

// -----------------------------------------------------------------------
// From functions.ts and functions/*.ts
// -----------------------------------------------------------------------

// accent
final case class ParseNodeAccent(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var label: String = "",
    var isStretchy: Nullable[Boolean] = Nullable.Null,
    var isShifty: Nullable[Boolean] = Nullable.Null,
    var base: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "accent"
}

// accentUnder
final case class ParseNodeAccentUnder(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var label: String = "",
    var isStretchy: Nullable[Boolean] = Nullable.Null,
    var isShifty: Nullable[Boolean] = Nullable.Null,
    var base: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "accentUnder"
}

// cr
final case class ParseNodeCr(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var newLine: Boolean = false,
    var size: Nullable[Measurement] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "cr"
}

// delimsizing
final case class ParseNodeDelimsizing(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var size: Int = 1, // 1 | 2 | 3 | 4
    var mclass: String = "mord", // "mopen" | "mclose" | "mrel" | "mord"
    var delim: String = ""
) extends AnyParseNode {
  override def nodeType: String = "delimsizing"
}

// enclose
final case class ParseNodeEnclose(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var label: String = "",
    var backgroundColor: Nullable[String] = Nullable.Null,
    var borderColor: Nullable[String] = Nullable.Null,
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "enclose"
}

// environment
final case class ParseNodeEnvironment(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var name: String = "",
    var nameGroup: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "environment"
}

// font
final case class ParseNodeFont(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var font: String = "",
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "font"
  override def bodyNode: Nullable[AnyParseNode] = Nullable(body)
}

// genfrac
final case class ParseNodeGenfrac(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var continued: Boolean = false,
    var numer: AnyParseNode,
    var denom: AnyParseNode,
    var hasBarLine: Boolean = true,
    var leftDelim: Nullable[String] = Nullable.Null,
    var rightDelim: Nullable[String] = Nullable.Null,
    var barSize: Nullable[Measurement] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "genfrac"
}

// hbox
final case class ParseNodeHbox(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "hbox"
}

// horizBrace
final case class ParseNodeHorizBrace(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var label: String = "",
    var isOver: Boolean = false,
    var base: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "horizBrace"
}

// href
final case class ParseNodeHref(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var href: String = "",
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "href"
}

// html
final case class ParseNodeHtml(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var attributes: mutable.Map[String, String] = mutable.Map.empty,
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "html"
}

// htmlmathml
final case class ParseNodeHtmlmathml(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var html: Array[AnyParseNode] = Array.empty,
    var mathml: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "htmlmathml"
}

// includegraphics
final case class ParseNodeIncludegraphics(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var alt: String = "",
    var width: Measurement = Measurement(0, ""),
    var height: Measurement = Measurement(0, ""),
    var totalheight: Measurement = Measurement(0, ""),
    var src: String = ""
) extends AnyParseNode {
  override def nodeType: String = "includegraphics"
}

// infix
final case class ParseNodeInfix(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var replaceWith: String = "",
    var size: Nullable[Measurement] = Nullable.Null,
    var token: Nullable[Token] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "infix"
}

// internal
final case class ParseNodeInternal(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "internal"
}

// kern
final case class ParseNodeKern(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var dimension: Measurement = Measurement(0, "")
) extends AnyParseNode {
  override def nodeType: String = "kern"
}

// lap
final case class ParseNodeLap(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var alignment: String = "",
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "lap"
}

// leftright
final case class ParseNodeLeftright(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: Array[AnyParseNode] = Array.empty,
    var left: String = "",
    var right: String = "",
    var rightColor: Nullable[String] = Nullable.Null // Null means "inherit"
) extends AnyParseNode {
  override def nodeType: String = "leftright"
}

// leftright-right
final case class ParseNodeLeftrightRight(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var delim: String = "",
    var color: Nullable[String] = Nullable.Null // Null means "inherit"
) extends AnyParseNode {
  override def nodeType: String = "leftright-right"
}

// mathchoice
final case class ParseNodeMathchoice(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var display: Array[AnyParseNode] = Array.empty,
    var text: Array[AnyParseNode] = Array.empty,
    var script: Array[AnyParseNode] = Array.empty,
    var scriptscript: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "mathchoice"
}

// middle
final case class ParseNodeMiddle(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var delim: String = ""
) extends AnyParseNode {
  override def nodeType: String = "middle"
}

// mclass
final case class ParseNodeMclass(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var mclass: String = "",
    var body: Array[AnyParseNode] = Array.empty,
    var isCharacterBox: Boolean = false
) extends AnyParseNode {
  override def nodeType: String = "mclass"
}

// operatorname
final case class ParseNodeOperatorname(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: Array[AnyParseNode] = Array.empty,
    var alwaysHandleSupSub: Boolean = false,
    var limits: Boolean = false,
    var parentIsSupSub: Boolean = false
) extends AnyParseNode {
  override def nodeType: String = "operatorname"
}

// overline
final case class ParseNodeOverline(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "overline"
}

// phantom
final case class ParseNodePhantom(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "phantom"
}

// vphantom
final case class ParseNodeVphantom(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "vphantom"
}

// pmb
final case class ParseNodePmb(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var mclass: String = "",
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "pmb"
}

// raisebox
final case class ParseNodeRaisebox(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var dy: Measurement = Measurement(0, ""),
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "raisebox"
}

// rule
final case class ParseNodeRule(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var shift: Nullable[Measurement] = Nullable.Null,
    var width: Measurement = Measurement(0, ""),
    var height: Measurement = Measurement(0, "")
) extends AnyParseNode {
  override def nodeType: String = "rule"
}

// sizing
final case class ParseNodeSizing(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var size: Int = 1,
    var body: Array[AnyParseNode] = Array.empty
) extends AnyParseNode {
  override def nodeType: String = "sizing"
}

// smash
final case class ParseNodeSmash(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: AnyParseNode,
    var smashHeight: Boolean = false,
    var smashDepth: Boolean = false
) extends AnyParseNode {
  override def nodeType: String = "smash"
}

// sqrt
final case class ParseNodeSqrt(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: AnyParseNode,
    var index: Nullable[AnyParseNode] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "sqrt"
}

// underline
final case class ParseNodeUnderline(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "underline"
}

// vcenter
final case class ParseNodeVcenter(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var body: AnyParseNode) extends AnyParseNode {
  override def nodeType: String = "vcenter"
}

// xArrow
final case class ParseNodeXArrow(
    var mode: Mode,
    var loc: Nullable[SourceLocation] = Nullable.Null,
    var label: String = "",
    var body: AnyParseNode,
    var below: Nullable[AnyParseNode] = Nullable.Null
) extends AnyParseNode {
  override def nodeType: String = "xArrow"
}

// -----------------------------------------------------------------------
// Utility functions
// -----------------------------------------------------------------------

object ParseNode {

  /**
   * Asserts that the node is of the given type and returns it with stricter
   * typing. Throws if the node's type does not match.
   */
  def assertNodeType(
      node: Nullable[AnyParseNode],
      nodeType: String
  ): AnyParseNode = {
    if (node.isEmpty || node.get.nodeType != nodeType) {
      throw new Error(
        s"Expected node of type $nodeType, but got " +
        (if (node.isDefined) s"node of type ${node.get.nodeType}" else node.toString))
    }
    node.get
  }

  /**
   * Returns the node more strictly typed iff it is of symbol group type.
   * Otherwise, throws.
   */
  def assertSymbolNodeType(node: Nullable[AnyParseNode]): SymbolParseNode = {
    val typedNode = checkSymbolNodeType(node)
    if (typedNode.isEmpty) {
      throw new Error(
        "Expected node of symbol group type, but got " +
        (if (node.isDefined) s"node of type ${node.get.nodeType}" else node.toString))
    }
    typedNode.get
  }

  /**
   * Returns the node more strictly typed iff it is of symbol group type.
   * Otherwise, returns null.
   */
  def checkSymbolNodeType(node: Nullable[AnyParseNode]): Nullable[SymbolParseNode] = {
    if (node.isDefined && (node.get.nodeType == "atom" ||
        Symbols.NON_ATOMS.contains(node.get.nodeType))) {
      Nullable(node.get.asInstanceOf[SymbolParseNode])
    } else {
      Nullable.Null
    }
  }
}
