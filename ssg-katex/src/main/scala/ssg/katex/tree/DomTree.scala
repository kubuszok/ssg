/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * These objects store the data about the DOM nodes we create, as well as some
 * extra data. They can then be transformed into real DOM nodes with the
 * `toNode` function or HTML markup using `toMarkup`. They are useful for both
 * storing extra properties on the nodes, as well as providing a way to easily
 * work with the DOM.
 *
 * Similar functions for working with MathML nodes exist in MathMLTree.scala.
 *
 * TODO: refactor `span` and `anchor` into common superclass when
 * target environments support class inheritance
 *
 * Original source: katex src/domTree.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: domTree -> DomTree (object), Span -> Span, Anchor -> Anchor
 *   Convention: Record<string,string> -> mutable.LinkedHashMap[String,String]
 *   Idiom: CssStyle partial type -> final case class with Nullable fields
 *   Idiom: document.createElement -> InMemoryNode.element
 */
package ssg
package katex
package tree

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LinkedHashMap
import scala.util.matching.Regex

import lowlevel.Nullable
import ssg.katex.ParseError
import ssg.katex.data.{ SvgGeometry, UnicodeScripts, Units }
import ssg.katex.util.Utils

// Making the type below exact with all optional fields doesn't work due to
// - https://github.com/facebook/flow/issues/4582
// - https://github.com/facebook/flow/issues/5688
// However, since *all* fields are optional, $Shape<> works as suggested in 5688
// above.
// This type does not include all CSS properties. Additional properties should
// be added as needed.
final case class CssStyle(
  backgroundColor:   Nullable[String] = Nullable.Null,
  borderBottomWidth: Nullable[String] = Nullable.Null,
  borderColor:       Nullable[String] = Nullable.Null,
  borderRightStyle:  Nullable[String] = Nullable.Null,
  borderRightWidth:  Nullable[String] = Nullable.Null,
  borderTopWidth:    Nullable[String] = Nullable.Null,
  borderStyle:       Nullable[String] = Nullable.Null,
  borderWidth:       Nullable[String] = Nullable.Null,
  bottom:            Nullable[String] = Nullable.Null,
  color:             Nullable[String] = Nullable.Null,
  height:            Nullable[String] = Nullable.Null,
  left:              Nullable[String] = Nullable.Null,
  margin:            Nullable[String] = Nullable.Null,
  marginLeft:        Nullable[String] = Nullable.Null,
  marginRight:       Nullable[String] = Nullable.Null,
  marginTop:         Nullable[String] = Nullable.Null,
  minWidth:          Nullable[String] = Nullable.Null,
  paddingLeft:       Nullable[String] = Nullable.Null,
  position:          Nullable[String] = Nullable.Null,
  textShadow:        Nullable[String] = Nullable.Null,
  top:               Nullable[String] = Nullable.Null,
  width:             Nullable[String] = Nullable.Null,
  verticalAlign:     Nullable[String] = Nullable.Null
) {

  /** Iterate over all non-null (name, value) pairs in camelCase form. */
  def foreachEntry(f: (String, String) => Unit): Unit = {
    backgroundColor.foreach(v => f("backgroundColor", v))
    borderBottomWidth.foreach(v => f("borderBottomWidth", v))
    borderColor.foreach(v => f("borderColor", v))
    borderRightStyle.foreach(v => f("borderRightStyle", v))
    borderRightWidth.foreach(v => f("borderRightWidth", v))
    borderTopWidth.foreach(v => f("borderTopWidth", v))
    borderStyle.foreach(v => f("borderStyle", v))
    borderWidth.foreach(v => f("borderWidth", v))
    bottom.foreach(v => f("bottom", v))
    color.foreach(v => f("color", v))
    height.foreach(v => f("height", v))
    left.foreach(v => f("left", v))
    margin.foreach(v => f("margin", v))
    marginLeft.foreach(v => f("marginLeft", v))
    marginRight.foreach(v => f("marginRight", v))
    marginTop.foreach(v => f("marginTop", v))
    minWidth.foreach(v => f("minWidth", v))
    paddingLeft.foreach(v => f("paddingLeft", v))
    position.foreach(v => f("position", v))
    textShadow.foreach(v => f("textShadow", v))
    top.foreach(v => f("top", v))
    width.foreach(v => f("width", v))
    verticalAlign.foreach(v => f("verticalAlign", v))
  }

  /** True if no CSS properties are set. */
  def isEmpty: Boolean =
    backgroundColor.isEmpty &&
      borderBottomWidth.isEmpty &&
      borderColor.isEmpty &&
      borderRightStyle.isEmpty &&
      borderRightWidth.isEmpty &&
      borderTopWidth.isEmpty &&
      borderStyle.isEmpty &&
      borderWidth.isEmpty &&
      bottom.isEmpty &&
      color.isEmpty &&
      height.isEmpty &&
      left.isEmpty &&
      margin.isEmpty &&
      marginLeft.isEmpty &&
      marginRight.isEmpty &&
      marginTop.isEmpty &&
      minWidth.isEmpty &&
      paddingLeft.isEmpty &&
      position.isEmpty &&
      textShadow.isEmpty &&
      top.isEmpty &&
      width.isEmpty &&
      verticalAlign.isEmpty

  def nonEmpty: Boolean = !isEmpty
}

trait HtmlDomNode extends VirtualNode {
  var classes:     ArrayBuffer[String]
  var height:      Double
  var depth:       Double
  var maxFontSize: Double
  var style:       CssStyle
  def hasClass(className: String): Boolean
}

// Span wrapping other DOM nodes.
type DomSpan = Span[HtmlDomNode]
// Span wrapping an SVG node.
type SvgSpan = Span[SvgNode]

type SvgChildNode         = PathNode | LineNode
type HtmlDocumentFragment = DocumentFragment[HtmlDomNode]

/** Trait for nodes that carry attributes (Span, Anchor). */
private[tree] trait AttributedNode {
  var attributes: LinkedHashMap[String, String]
}

/** Trait for nodes that carry typed children. */
private[tree] trait ChildBearing {
  def virtualChildren: collection.Seq[VirtualNode]
}

object DomTree {

  /** Create an HTML className based on a list of classes. In addition to joining with spaces, we also remove empty classes.
    */
  def createClass(classes: collection.Seq[String]): String =
    classes.filter(_.nonEmpty).mkString(" ")

  /** https://w3c.github.io/html-reference/syntax.html#syntax-attributes
    *
    * > Attribute Names must consist of one or more characters other than the space characters, U+0000 NULL, '"', "'", ">", "/", "=", the control characters, and any characters that are not defined by
    * Unicode.
    */
  private val invalidAttributeNameRegex: Regex = """[\s"'>/=\x00-\x1f]""".r

  /** Initialize common node fields (classes, attributes, height, depth, maxFontSize, style) and apply options if present.
    */
  private[tree] def initNode(
    node:    HtmlDomNode & AttributedNode,
    classes: ArrayBuffer[String],
    options: Nullable[Options],
    style:   CssStyle
  ): Unit = {
    node.classes = classes
    node.attributes = LinkedHashMap.empty
    node.height = 0.0
    node.depth = 0.0
    node.maxFontSize = 0.0
    node.style = style
    options.foreach { opts =>
      if (opts.style.isTight()) {
        node.classes += "mtight"
      }
      val color = opts.getColor()
      color.foreach { c =>
        node.style = node.style.copy(color = Nullable(c))
      }
    }
  }

  /** Convert into an HTML node
    */
  private[tree] def toInMemoryNode(
    data:    HtmlDomNode & AttributedNode & ChildBearing,
    tagName: String
  ): InMemoryNode = {
    val node = InMemoryNode.element(tagName)

    // Apply the class
    node.className = createClass(data.classes)

    // Apply inline styles
    data.style.foreachEntry { (key, value) =>
      node.style(key) = value
    }

    // Apply attributes
    data.attributes.foreach { case (attr, value) =>
      node.setAttribute(attr, value)
    }

    // Append the children, also as HTML nodes
    val children = data.virtualChildren
    var i        = 0
    while (i < children.length) {
      node.appendChild(children(i).toNode())
      i += 1
    }

    node
  }

  /** Convert into an HTML markup string
    */
  private[tree] def toMarkup(
    data:    HtmlDomNode & AttributedNode & ChildBearing,
    tagName: String
  ): String = {
    val markup = new StringBuilder
    markup.append('<')
    markup.append(tagName)

    // Add the class
    if (data.classes.nonEmpty) {
      markup.append(" class=\"")
      markup.append(Utils.escape(createClass(data.classes)))
      markup.append('"')
    }

    val styles = new StringBuilder

    // Add the styles, after hyphenation
    data.style.foreachEntry { (key, value) =>
      styles.append(Utils.hyphenate(key))
      styles.append(':')
      styles.append(value)
      styles.append(';')
    }

    if (styles.nonEmpty) {
      markup.append(" style=\"")
      markup.append(Utils.escape(styles.toString))
      markup.append('"')
    }

    // Add the attributes
    data.attributes.foreach { case (attr, value) =>
      if (invalidAttributeNameRegex.findFirstIn(attr).isDefined) {
        throw new ParseError(s"Invalid attribute name '$attr'")
      }
      markup.append(' ')
      markup.append(attr)
      markup.append("=\"")
      markup.append(Utils.escape(value))
      markup.append('"')
    }

    markup.append('>')

    // Add the markup of the children, also as markup
    val children = data.virtualChildren
    var i        = 0
    while (i < children.length) {
      markup.append(children(i).toMarkup())
      i += 1
    }

    markup.append("</")
    markup.append(tagName)
    markup.append('>')

    markup.toString
  }

  /** Assert that the given node is a SymbolNode.
    */
  def assertSymbolDomNode(group: HtmlDomNode): SymbolNode =
    group match {
      case s: SymbolNode => s
      case _ => throw new Error(s"Expected symbolNode but got ${group.toString}.")
    }

  /** Assert that the given node is a Span.
    */
  def assertSpan(group: HtmlDomNode): Span[HtmlDomNode] =
    group match {
      case s: Span[?] => s.asInstanceOf[Span[HtmlDomNode]]
      case _ => throw new Error(s"Expected span<HtmlDomNode> but got ${group.toString}.")
    }

  /** Whether an HtmlDomNode has HtmlDomNode children. HtmlDomNode is a base type representing a union of SymbolNode, SvgSpan, DomSpan, Anchor, and documentFragment. In the last three cases, the
    * children are HtmlDomNode[].
    */
  def hasHtmlDomChildren(node: HtmlDomNode): Boolean =
    node.isInstanceOf[Span[?]] ||
      node.isInstanceOf[Anchor] ||
      node.isInstanceOf[DocumentFragment[?]]
}

/** This node represents a span node, with a className, a list of children, and an inline style. It also contains information about its height, depth, and maxFontSize.
  *
  * Represents two types with different uses: SvgSpan to wrap an SVG and DomSpan otherwise. This typesafety is important when HTML builders access a span's children.
  */
class Span[ChildType <: VirtualNode](
  classesInit:  ArrayBuffer[String] = ArrayBuffer.empty,
  childrenInit: ArrayBuffer[ChildType] = ArrayBuffer.empty,
  options:      Nullable[Options] = Nullable.Null,
  styleInit:    CssStyle = CssStyle()
) extends HtmlDomNode
    with AttributedNode
    with ChildBearing {

  var classes:     ArrayBuffer[String]           = classesInit
  var attributes:  LinkedHashMap[String, String] = LinkedHashMap.empty
  var height:      Double                        = 0.0
  var depth:       Double                        = 0.0
  var maxFontSize: Double                        = 0.0
  var style:       CssStyle                      = styleInit
  var children:    ArrayBuffer[ChildType]        = childrenInit
  var width:       Nullable[Double]              = Nullable.Null

  DomTree.initNode(this, classes, options, style)

  def virtualChildren: collection.Seq[VirtualNode] = children

  /** Sets an arbitrary attribute on the span. Warning: use this wisely. Not all browsers support attributes the same, and having too many custom attributes is probably bad.
    */
  def setAttribute(attribute: String, value: String): Unit =
    attributes(attribute) = value

  def hasClass(className: String): Boolean =
    classes.contains(className)

  def toNode(): InMemoryNode =
    DomTree.toInMemoryNode(this, "span")

  def toMarkup(): String =
    DomTree.toMarkup(this, "span")
}

/** This node represents an anchor (<a>) element with a hyperlink. See `span` for further details.
  */
class Anchor(
  href:         String,
  classesInit:  ArrayBuffer[String],
  childrenInit: ArrayBuffer[HtmlDomNode],
  options:      Options
) extends HtmlDomNode
    with AttributedNode
    with ChildBearing {

  var classes:     ArrayBuffer[String]           = classesInit
  var attributes:  LinkedHashMap[String, String] = LinkedHashMap.empty
  var height:      Double                        = 0.0
  var depth:       Double                        = 0.0
  var maxFontSize: Double                        = 0.0
  var style:       CssStyle                      = CssStyle()
  var children:    ArrayBuffer[HtmlDomNode]      =
    if (childrenInit != null) childrenInit else ArrayBuffer.empty // @nowarn -- null safety at API boundary

  DomTree.initNode(this, classes, Nullable(options), style)
  setAttribute("href", href)

  def virtualChildren: collection.Seq[VirtualNode] = children

  def setAttribute(attribute: String, value: String): Unit =
    attributes(attribute) = value

  def hasClass(className: String): Boolean =
    classes.contains(className)

  def toNode(): InMemoryNode =
    DomTree.toInMemoryNode(this, "a")

  def toMarkup(): String =
    DomTree.toMarkup(this, "a")
}

/** This node represents an image embed (<img>) element.
  */
class Img(
  val src:   String,
  val alt:   String,
  styleInit: CssStyle
) extends HtmlDomNode {

  var classes:     ArrayBuffer[String] = ArrayBuffer("mord")
  var height:      Double              = 0.0
  var depth:       Double              = 0.0
  var maxFontSize: Double              = 0.0
  var style:       CssStyle            = styleInit

  def hasClass(className: String): Boolean =
    classes.contains(className)

  def toNode(): InMemoryNode = {
    val node = InMemoryNode.element("img")
    node.setAttribute("src", src)
    node.setAttribute("alt", alt)
    node.className = "mord"

    // Apply inline styles
    style.foreachEntry { (key, value) =>
      node.style(key) = value
    }

    node
  }

  def toMarkup(): String = {
    val markup = new StringBuilder
    markup.append("<img src=\"")
    markup.append(Utils.escape(src))
    markup.append("\" alt=\"")
    markup.append(Utils.escape(alt))
    markup.append('"')

    // Add the styles, after hyphenation
    val styles = new StringBuilder
    style.foreachEntry { (key, value) =>
      styles.append(Utils.hyphenate(key))
      styles.append(':')
      styles.append(value)
      styles.append(';')
    }
    if (styles.nonEmpty) {
      markup.append(" style=\"")
      markup.append(Utils.escape(styles.toString))
      markup.append('"')
    }

    markup.append("'/>")
    markup.toString
  }
}

private val iCombinations: Map[String, String] = Map(
  "î" -> "ı̂", // î -> dotless-i + combining circumflex
  "ï" -> "ı̈", // ï -> dotless-i + combining diaeresis
  "í" -> "ı́", // í -> dotless-i + combining acute
  // "ī" -> "ı̄", // ī -> enable when we add Extended Latin
  "ì" -> "ı̀" // ì -> dotless-i + combining grave
)

/** A symbol node contains information about a single symbol. It either renders to a single text node, or a span with a single text node in it, depending on whether it has CSS classes, styles, or
  * needs italic correction.
  */
class SymbolNode(
  textInit:    String,
  heightInit:  Double = 0.0,
  depthInit:   Double = 0.0,
  italicInit:  Double = 0.0,
  skewInit:    Double = 0.0,
  widthInit:   Double = 0.0,
  classesInit: ArrayBuffer[String] = ArrayBuffer.empty,
  styleInit:   CssStyle = CssStyle()
) extends HtmlDomNode {

  var text:        String              = textInit
  var height:      Double              = heightInit
  var depth:       Double              = depthInit
  var italic:      Double              = italicInit
  var skew:        Double              = skewInit
  var width:       Double              = widthInit
  var classes:     ArrayBuffer[String] = classesInit
  var style:       CssStyle            = styleInit
  var maxFontSize: Double              = 0.0

  // Mark text from non-Latin scripts with specific classes so that we
  // can specify which fonts to use.  This allows us to render these
  // characters with a serif font in situations where the browser would
  // either default to a sans serif or render a placeholder character.
  // We use CSS class names like cjk_fallback, hangul_fallback and
  // brahmic_fallback. See ./unicodeScripts.js for the set of possible
  // script names
  locally {
    val script = UnicodeScripts.scriptFromCodepoint(text.charAt(0).toInt)
    script.foreach { s =>
      classes += (s + "_fallback")
    }

    if (text == "î" || text == "ï" || text == "í" || text == "ì") { // add ī when we add Extended Latin
      text = iCombinations(text)
    }
  }

  def hasClass(className: String): Boolean =
    classes.contains(className)

  /** Creates a text node or span from a symbol node. Note that a span is only created if it is needed.
    */
  def toNode(): InMemoryNode = {
    val textNode = InMemoryNode.text(text)
    var span: Nullable[InMemoryNode] = Nullable.Null

    if (italic > 0) {
      span = Nullable(InMemoryNode.element("span"))
      span.foreach(_.style("marginRight") = Units.makeEm(italic))
    }

    if (classes.nonEmpty) {
      val s = span.getOrElse(InMemoryNode.element("span"))
      s.className = DomTree.createClass(classes)
      span = Nullable(s)
    }

    style.foreachEntry { (key, value) =>
      val s = span.getOrElse(InMemoryNode.element("span"))
      s.style(key) = value
      span = Nullable(s)
    }

    span.fold[InMemoryNode] {
      textNode
    } { s =>
      s.appendChild(textNode)
      s
    }
  }

  /** Creates markup for a symbol node.
    */
  def toMarkup(): String = {
    // TODO(alpert): More duplication than I'd like from
    // span.prototype.toMarkup and symbolNode.prototype.toNode...
    var needsSpan = false

    val markup = new StringBuilder
    markup.append("<span")

    if (classes.nonEmpty) {
      needsSpan = true
      markup.append(" class=\"")
      markup.append(Utils.escape(DomTree.createClass(classes)))
      markup.append('"')
    }

    val styles = new StringBuilder

    if (italic > 0) {
      styles.append("margin-right:")
      styles.append(Units.makeEm(italic))
      styles.append(';')
    }
    style.foreachEntry { (key, value) =>
      styles.append(Utils.hyphenate(key))
      styles.append(':')
      styles.append(value)
      styles.append(';')
    }

    if (styles.nonEmpty) {
      needsSpan = true
      markup.append(" style=\"")
      markup.append(Utils.escape(styles.toString))
      markup.append('"')
    }

    val escaped = Utils.escape(text)
    if (needsSpan) {
      markup.append('>')
      markup.append(escaped)
      markup.append("</span>")
      markup.toString
    } else {
      escaped
    }
  }
}

/** SVG nodes are used to render stretchy wide elements.
  */
class SvgNode(
  val children:   ArrayBuffer[SvgChildNode] = ArrayBuffer.empty,
  val attributes: LinkedHashMap[String, String] = LinkedHashMap.empty
) extends VirtualNode {

  def toNode(): InMemoryNode = {
    val svgNS = "http://www.w3.org/2000/svg"
    val node  = InMemoryNode.elementNS(svgNS, "svg")

    // Apply attributes
    attributes.foreach { case (attr, value) =>
      node.setAttribute(attr, value)
    }

    var i = 0
    while (i < children.length) {
      val child: VirtualNode = children(i) match {
        case p: PathNode => p
        case l: LineNode => l
      }
      node.appendChild(child.toNode())
      i += 1
    }
    node
  }

  def toMarkup(): String = {
    val markup = new StringBuilder
    markup.append("<svg xmlns=\"http://www.w3.org/2000/svg\"")

    // Apply attributes
    attributes.foreach { case (attr, value) =>
      markup.append(' ')
      markup.append(attr)
      markup.append("=\"")
      markup.append(Utils.escape(value))
      markup.append('"')
    }

    markup.append('>')

    var i = 0
    while (i < children.length) {
      val child: VirtualNode = children(i) match {
        case p: PathNode => p
        case l: LineNode => l
      }
      markup.append(child.toMarkup())
      i += 1
    }

    markup.append("</svg>")

    markup.toString
  }
}

class PathNode(
  val pathName:  String,
  val alternate: Nullable[String] = Nullable.Null // Used only for \sqrt, \phase, & tall delims
) extends VirtualNode {

  def toNode(): InMemoryNode = {
    val svgNS = "http://www.w3.org/2000/svg"
    val node  = InMemoryNode.elementNS(svgNS, "path")

    alternate.fold {
      node.setAttribute("d", SvgGeometry.path(pathName))
    } { alt =>
      node.setAttribute("d", alt)
    }

    node
  }

  def toMarkup(): String =
    alternate.fold {
      s"""<path d="${Utils.escape(SvgGeometry.path(pathName))}"/>"""
    } { alt =>
      s"""<path d="${Utils.escape(alt)}"/>"""
    }
}

class LineNode(
  val attributes: LinkedHashMap[String, String] = LinkedHashMap.empty
) extends VirtualNode {

  def toNode(): InMemoryNode = {
    val svgNS = "http://www.w3.org/2000/svg"
    val node  = InMemoryNode.elementNS(svgNS, "line")

    // Apply attributes
    attributes.foreach { case (attr, value) =>
      node.setAttribute(attr, value)
    }

    node
  }

  def toMarkup(): String = {
    val markup = new StringBuilder
    markup.append("<line")

    attributes.foreach { case (attr, value) =>
      markup.append(' ')
      markup.append(attr)
      markup.append("=\"")
      markup.append(Utils.escape(value))
      markup.append('"')
    }

    markup.append("/>")

    markup.toString
  }
}
