/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces browser DOM Element/SVGElement with immutable data structure
 *   Idiom: ADT with sealed trait hierarchy for type-safe SVG element representation
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package svg

import ssg.commons.Nullable

import scala.collection.mutable.{ ArrayBuffer, LinkedHashMap }

/** An immutable representation of an SVG element.
  *
  * Each element has a tag name, insertion-ordered attributes (for deterministic XML output), an optional list of children, optional text content, and optional raw HTML content (for `foreignObject`).
  *
  * This replaces browser-native DOM `Element` / `SVGElement` for server-side SVG generation.
  *
  * @param tagName
  *   the SVG/XML tag name (e.g. "svg", "g", "rect", "text")
  * @param attributes
  *   insertion-ordered attribute map for deterministic output
  * @param children
  *   child elements in document order
  * @param textContent
  *   text content for the element (mutually exclusive with htmlContent for rendering)
  * @param htmlContent
  *   raw HTML content, used for foreignObject (inserted without escaping)
  */
final case class SvgElement(
  tagName:     String,
  attributes:  LinkedHashMap[String, String] = LinkedHashMap.empty,
  children:    ArrayBuffer[SvgElement] = ArrayBuffer.empty,
  textContent: Nullable[String] = Nullable.empty,
  htmlContent: Nullable[String] = Nullable.empty
) {

  /** Returns the value of the given attribute, or empty if not set. */
  def attr(name: String): Nullable[String] =
    attributes.get(name) match {
      case Some(v) => Nullable(v)
      case None    => Nullable.empty
    }

  /** Returns the `id` attribute value, or empty if not set. */
  def id: Nullable[String] = attr("id")

  /** Returns the CSS classes as a set, parsed from the `class` attribute. */
  def classes: Set[String] =
    attr("class").fold(Set.empty[String]) { cls =>
      cls.split("\\s+").filter(_.nonEmpty).toSet
    }

  /** Returns true if this element has the given CSS class. */
  def hasClass(className: String): Boolean = classes.contains(className)

  /** Returns a deep copy of this element with the given attribute set. */
  def withAttr(name: String, value: String): SvgElement = {
    val newAttrs = LinkedHashMap.from(attributes)
    newAttrs(name) = value
    copy(attributes = newAttrs)
  }

  /** Returns a deep copy with a child appended. */
  def withChild(child: SvgElement): SvgElement = {
    val newChildren = ArrayBuffer.from(children)
    newChildren += child
    copy(children = newChildren)
  }

  /** Returns a deep copy with text content set. */
  def withText(text: String): SvgElement =
    copy(textContent = Nullable(text))

  /** Returns a deep copy with HTML content set. */
  def withHtml(html: String): SvgElement =
    copy(htmlContent = Nullable(html))

  /** Serializes this element and its subtree to an XML string.
    *
    * @param pretty
    *   whether to add indentation and newlines (default true)
    * @param indent
    *   the indentation string per level (default 2 spaces)
    * @return
    *   the serialized XML string
    */
  def toMarkup(pretty: Boolean = true, indent: String = SvgMarkup.DefaultIndent): String =
    SvgMarkup.serialize(this, indent = indent, level = 0, pretty = pretty)

  /** Finds the first descendant element matching the given tag name. */
  def findByTag(tag: String): Nullable[SvgElement] = {
    import scala.util.boundary
    import scala.util.boundary.break
    boundary[Nullable[SvgElement]] {
      children.foreach { child =>
        if (child.tagName == tag) {
          break(Nullable(child))
        }
        val result = child.findByTag(tag)
        if (result.isDefined) {
          break(result)
        }
      }
      Nullable.empty
    }
  }

  /** Finds the first descendant element with the given id attribute. */
  def findById(idValue: String): Nullable[SvgElement] = {
    import scala.util.boundary
    import scala.util.boundary.break
    boundary[Nullable[SvgElement]] {
      children.foreach { child =>
        if (child.id.exists(_ == idValue)) {
          break(Nullable(child))
        }
        val result = child.findById(idValue)
        if (result.isDefined) {
          break(result)
        }
      }
      Nullable.empty
    }
  }

  /** Finds all descendant elements matching the given tag name. */
  def findAllByTag(tag: String): ArrayBuffer[SvgElement] = {
    val results = ArrayBuffer.empty[SvgElement]
    children.foreach { child =>
      if (child.tagName == tag) {
        results += child
      }
      results ++= child.findAllByTag(tag)
    }
    results
  }

  /** Finds all descendant elements that have the given CSS class. */
  def findAllByClass(className: String): ArrayBuffer[SvgElement] = {
    val results = ArrayBuffer.empty[SvgElement]
    children.foreach { child =>
      if (child.hasClass(className)) {
        results += child
      }
      results ++= child.findAllByClass(className)
    }
    results
  }

  /** Returns the total number of elements in this subtree (including self). */
  def subtreeSize: Int =
    1 + children.foldLeft(0)((acc, child) => acc + child.subtreeSize)
}

object SvgElement {

  /** Creates an element with just a tag name. */
  def apply(tagName: String): SvgElement =
    new SvgElement(tagName)

  /** Creates an SVG root element with standard namespace attributes. */
  def svgRoot(width: Double, height: Double, viewBox: String): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "xmlns" -> SvgMarkup.SvgNamespace,
      "xmlns:xlink" -> SvgMarkup.XlinkNamespace,
      "width" -> width.toString,
      "height" -> height.toString,
      "viewBox" -> viewBox
    )
    new SvgElement("svg", attributes = attrs)
  }

  /** Creates an SVG root element with string dimensions (may include units). */
  def svgRoot(width: String, height: String, viewBox: String): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "xmlns" -> SvgMarkup.SvgNamespace,
      "xmlns:xlink" -> SvgMarkup.XlinkNamespace,
      "width" -> width,
      "height" -> height,
      "viewBox" -> viewBox
    )
    new SvgElement("svg", attributes = attrs)
  }

  // --- Factory methods for common SVG elements ---

  /** Creates a `<g>` group element. */
  def g(): SvgElement = SvgElement("g")

  /** Creates a `<defs>` element. */
  def defs(): SvgElement = SvgElement("defs")

  /** Creates a `<clipPath>` element with the given id. */
  def clipPath(id: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("id" -> id)
    new SvgElement("clipPath", attributes = attrs)
  }

  /** Creates a `<mask>` element with the given id. */
  def mask(id: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("id" -> id)
    new SvgElement("mask", attributes = attrs)
  }

  /** Creates a `<symbol>` element. */
  def symbol(): SvgElement = SvgElement("symbol")

  /** Creates a `<use>` element referencing the given href. */
  def use(href: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("href" -> href)
    new SvgElement("use", attributes = attrs)
  }

  /** Creates a `<rect>` element. */
  def rect(x: Double, y: Double, width: Double, height: Double): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "x" -> x.toString,
      "y" -> y.toString,
      "width" -> width.toString,
      "height" -> height.toString
    )
    new SvgElement("rect", attributes = attrs)
  }

  /** Creates a `<circle>` element. */
  def circle(cx: Double, cy: Double, r: Double): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "cx" -> cx.toString,
      "cy" -> cy.toString,
      "r" -> r.toString
    )
    new SvgElement("circle", attributes = attrs)
  }

  /** Creates an `<ellipse>` element. */
  def ellipse(cx: Double, cy: Double, rx: Double, ry: Double): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "cx" -> cx.toString,
      "cy" -> cy.toString,
      "rx" -> rx.toString,
      "ry" -> ry.toString
    )
    new SvgElement("ellipse", attributes = attrs)
  }

  /** Creates a `<line>` element. */
  def line(x1: Double, y1: Double, x2: Double, y2: Double): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "x1" -> x1.toString,
      "y1" -> y1.toString,
      "x2" -> x2.toString,
      "y2" -> y2.toString
    )
    new SvgElement("line", attributes = attrs)
  }

  /** Creates a `<polyline>` element from a sequence of points. */
  def polyline(points: Seq[(Double, Double)]): SvgElement = {
    val pointsStr = points.map { case (x, y) => s"$x,$y" }.mkString(" ")
    val attrs     = LinkedHashMap[String, String]("points" -> pointsStr)
    new SvgElement("polyline", attributes = attrs)
  }

  /** Creates a `<polygon>` element from a sequence of points. */
  def polygon(points: Seq[(Double, Double)]): SvgElement = {
    val pointsStr = points.map { case (x, y) => s"$x,$y" }.mkString(" ")
    val attrs     = LinkedHashMap[String, String]("points" -> pointsStr)
    new SvgElement("polygon", attributes = attrs)
  }

  /** Creates a `<path>` element from a PathData builder. */
  def path(pathData: PathData): SvgElement = {
    val attrs = LinkedHashMap[String, String]("d" -> pathData.toString)
    new SvgElement("path", attributes = attrs)
  }

  /** Creates a `<path>` element from a raw `d` attribute string. */
  def path(d: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("d" -> d)
    new SvgElement("path", attributes = attrs)
  }

  /** Creates a `<text>` element with the given content. */
  def text(content: String): SvgElement =
    new SvgElement("text", textContent = Nullable(content))

  /** Creates a `<tspan>` element with the given content. */
  def tspan(content: String): SvgElement =
    new SvgElement("tspan", textContent = Nullable(content))

  /** Creates a `<textPath>` element referencing the given href. */
  def textPath(href: String, content: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("href" -> href)
    new SvgElement("textPath", attributes = attrs, textContent = Nullable(content))
  }

  /** Creates a `<foreignObject>` element. */
  def foreignObject(x: Double, y: Double, width: Double, height: Double): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "x" -> x.toString,
      "y" -> y.toString,
      "width" -> width.toString,
      "height" -> height.toString
    )
    new SvgElement("foreignObject", attributes = attrs)
  }

  /** Creates an `<image>` element. */
  def image(href: String, width: Double, height: Double): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "href" -> href,
      "width" -> width.toString,
      "height" -> height.toString
    )
    new SvgElement("image", attributes = attrs)
  }

  /** Creates a `<linearGradient>` element with the given id. */
  def linearGradient(id: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("id" -> id)
    new SvgElement("linearGradient", attributes = attrs)
  }

  /** Creates a `<radialGradient>` element with the given id. */
  def radialGradient(id: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("id" -> id)
    new SvgElement("radialGradient", attributes = attrs)
  }

  /** Creates a `<stop>` element for gradients. */
  def stop(offset: String, stopColor: String): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "offset" -> offset,
      "stop-color" -> stopColor
    )
    new SvgElement("stop", attributes = attrs)
  }

  /** Creates a `<pattern>` element with the given id. */
  def pattern(id: String, width: Double, height: Double): SvgElement = {
    val attrs = LinkedHashMap[String, String](
      "id" -> id,
      "width" -> width.toString,
      "height" -> height.toString
    )
    new SvgElement("pattern", attributes = attrs)
  }

  /** Creates a `<marker>` element with the given id. */
  def marker(id: String): SvgElement = {
    val attrs = LinkedHashMap[String, String]("id" -> id)
    new SvgElement("marker", attributes = attrs)
  }

  /** Creates a `<style>` element with CSS content (wrapped in CDATA). */
  def style(css: String): SvgElement =
    new SvgElement("style", textContent = Nullable(css))

  /** Creates a `<title>` element. */
  def title(content: String): SvgElement =
    new SvgElement("title", textContent = Nullable(content))

  /** Creates a `<desc>` element. */
  def desc(content: String): SvgElement =
    new SvgElement("desc", textContent = Nullable(content))
}
