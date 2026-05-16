/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Original source: mermaid
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3.js selection/chaining API with mutable builder
 *   Idiom: Builder pattern with method chaining; build() produces immutable SvgElement
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package svg

import lowlevel.Nullable
import ssg.graphs.commons.util.FormatUtil.formatNumber

import scala.collection.mutable.{ ArrayBuffer, LinkedHashMap }
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** A mutable builder that mimics D3.js's selection/chaining pattern for constructing SVG elements.
  *
  * Mermaid's renderers use D3 like:
  * {{{
  * const svg = d3.select(container).append("svg").attr("viewBox", "...");
  * const g = svg.append("g").attr("transform", "translate(...)");
  * g.append("rect").attr("width", 100).attr("height", 50).attr("class", "node");
  * g.append("text").text("Hello");
  * }}}
  *
  * In Scala, the equivalent is:
  * {{{
  * val svg = SvgBuilder.create("svg").attr("viewBox", "0 0 800 600")
  * val g = svg.append("g").attr("transform", "translate(50,50)")
  * g.append("rect").attr("width", "100").attr("height", "50").classed("node", true)
  * g.append("text").text("Hello")
  * val svgStr = svg.build().toMarkup()
  * }}}
  *
  * The builder is mutable: attributes and children accumulate as renderers build the diagram. The `build()` method produces an immutable [[SvgElement]] snapshot of the current state.
  */
final class SvgBuilder private (
  private val _tagName: String,
  private val _parent:  Nullable[SvgBuilder]
) {

  /** Insertion-ordered attributes for deterministic XML output. */
  private val _attributes: LinkedHashMap[String, String] = LinkedHashMap.empty

  /** Child builders in document order. */
  private val _children: ArrayBuffer[SvgBuilder] = ArrayBuffer.empty

  /** Text content (for text/tspan/title/desc elements). */
  private var _textContent: Nullable[String] = Nullable.empty

  /** Raw HTML content (for foreignObject). */
  private var _htmlContent: Nullable[String] = Nullable.empty

  /** Inline styles stored separately, merged into `style` attribute on build. */
  private val _styles: LinkedHashMap[String, String] = LinkedHashMap.empty

  // --- Tag and identity ---

  /** Returns the tag name of this element. */
  def tagName: String = _tagName

  /** Returns the parent builder, or empty if this is the root. */
  def parent: Nullable[SvgBuilder] = _parent

  // --- Attribute methods ---

  /** Sets an attribute value. Returns this builder for chaining.
    *
    * Mirrors D3's `selection.attr(name, value)`.
    */
  def attr(name: String, value: String): SvgBuilder = {
    _attributes(name) = value
    this
  }

  /** Sets a numeric attribute value. Returns this builder for chaining. */
  def attr(name: String, value: Double): SvgBuilder = {
    // Use integer representation when there is no fractional part
    val strValue = formatNumber(value)
    _attributes(name) = strValue
    this
  }

  /** Sets an integer attribute value. Returns this builder for chaining. */
  def attr(name: String, value: Int): SvgBuilder = {
    _attributes(name) = value.toString
    this
  }

  /** Gets the current value of an attribute. */
  def getAttr(name: String): Nullable[String] =
    _attributes.get(name) match {
      case Some(v) => Nullable(v)
      case None    => Nullable.empty
    }

  /** Removes an attribute. Returns this builder for chaining. */
  def removeAttr(name: String): SvgBuilder = {
    _attributes.remove(name)
    this
  }

  // --- Style methods ---

  /** Sets an inline CSS style property. Returns this builder for chaining.
    *
    * Mirrors D3's `selection.style(name, value)`. Styles are merged into the `style` attribute on build.
    */
  def style(name: String, value: String): SvgBuilder = {
    _styles(name) = value
    this
  }

  /** Gets the current value of an inline style property. */
  def getStyle(name: String): Nullable[String] =
    _styles.get(name) match {
      case Some(v) => Nullable(v)
      case None    => Nullable.empty
    }

  /** Removes an inline style property. Returns this builder for chaining. */
  def removeStyle(name: String): SvgBuilder = {
    _styles.remove(name)
    this
  }

  // --- Class methods ---

  /** Toggles a CSS class on or off. Returns this builder for chaining.
    *
    * Mirrors D3's `selection.classed(name, value)`.
    *
    * @param className
    *   the CSS class name to toggle
    * @param add
    *   true to add the class, false to remove it
    */
  def classed(className: String, add: Boolean): SvgBuilder = {
    val currentClasses = _attributes.getOrElse("class", "")
    val classSet       = currentClasses.split("\\s+").filter(_.nonEmpty).toSet
    val newClasses     = if (add) {
      classSet + className
    } else {
      classSet - className
    }
    if (newClasses.isEmpty) {
      _attributes.remove("class")
    } else {
      _attributes("class") = newClasses.mkString(" ")
    }
    this
  }

  /** Returns true if this element has the given CSS class. */
  def hasClass(className: String): Boolean =
    _attributes.getOrElse("class", "").split("\\s+").contains(className)

  // --- Text and HTML content ---

  /** Sets the text content of this element. Returns this builder for chaining.
    *
    * Mirrors D3's `selection.text(value)`.
    */
  def text(content: String): SvgBuilder = {
    _textContent = Nullable(content)
    this
  }

  /** Gets the current text content. */
  def getText: Nullable[String] = _textContent

  /** Sets raw HTML content (for foreignObject elements). Returns this builder for chaining.
    *
    * Mirrors D3's `selection.html(value)`.
    */
  def html(content: String): SvgBuilder = {
    _htmlContent = Nullable(content)
    this
  }

  /** Gets the current HTML content. */
  def getHtml: Nullable[String] = _htmlContent

  // --- Child element methods ---

  /** Appends a new child element with the given tag name. Returns the builder for the new child.
    *
    * Mirrors D3's `selection.append(type)`. The returned builder allows chaining attributes on the child.
    */
  def append(tag: String): SvgBuilder = {
    val child = new SvgBuilder(tag, Nullable(this))
    _children += child
    child
  }

  /** Inserts a new child element before the first child matching the selector. Returns the builder for the new child.
    *
    * Mirrors D3's `selection.insert(type, before)`. If no child matches `beforeSelector`, the new element is appended at the end.
    *
    * @param tag
    *   the tag name of the new element
    * @param beforeSelector
    *   simple CSS selector (.class, #id, or tag) to find the insertion point
    */
  def insert(tag: String, beforeSelector: String): SvgBuilder = {
    val child = new SvgBuilder(tag, Nullable(this))
    val idx   = findChildIndex(beforeSelector)
    if (idx >= 0) {
      _children.insert(idx, child)
    } else {
      _children += child
    }
    child
  }

  /** Removes this element from its parent. */
  def remove(): Unit =
    _parent.foreach { p =>
      val idx = p._children.indexOf(this)
      if (idx >= 0) {
        p._children.remove(idx)
      }
    }

  // --- Selection methods (D3-like) ---

  /** Finds the first child matching a simple CSS selector.
    *
    * Supported selectors:
    *   - `.className` — match by CSS class
    *   - `#id` — match by id attribute
    *   - `tagName` — match by tag name
    *
    * Searches this element's immediate children and their descendants (depth-first).
    *
    * @param selector
    *   simple CSS selector
    * @return
    *   the first matching builder, or empty if not found
    */
  def select(selector: String): Nullable[SvgBuilder] =
    boundary[Nullable[SvgBuilder]] {
      _children.foreach { child =>
        if (matchesSelector(child, selector)) {
          break(Nullable(child))
        }
        val result = child.select(selector)
        if (result.isDefined) {
          break(result)
        }
      }
      Nullable.empty
    }

  /** Finds all descendants matching a simple CSS selector.
    *
    * Supported selectors: `.className`, `#id`, `tagName`.
    *
    * @param selector
    *   simple CSS selector
    * @return
    *   all matching builders in depth-first order
    */
  def selectAll(selector: String): ArrayBuffer[SvgBuilder] = {
    val results = ArrayBuffer.empty[SvgBuilder]
    _children.foreach { child =>
      if (matchesSelector(child, selector)) {
        results += child
      }
      results ++= child.selectAll(selector)
    }
    results
  }

  // --- Ordering methods ---

  /** Moves this element to the end of its parent's children list.
    *
    * Mirrors D3's `selection.raise()`.
    */
  def raise(): SvgBuilder = {
    _parent.foreach { p =>
      val idx = p._children.indexOf(this)
      if (idx >= 0 && idx < p._children.size - 1) {
        p._children.remove(idx)
        p._children += this
      }
    }
    this
  }

  /** Moves this element to the beginning of its parent's children list.
    *
    * Mirrors D3's `selection.lower()`.
    */
  def lower(): SvgBuilder = {
    _parent.foreach { p =>
      val idx = p._children.indexOf(this)
      if (idx > 0) {
        p._children.remove(idx)
        p._children.insert(0, this)
      }
    }
    this
  }

  // --- Bounding box estimation ---

  /** Estimates the bounding box of this element.
    *
    * For shapes (rect, circle, ellipse, line, path), the bounding box is computed from attributes. For text elements, a rough estimate is produced using average character width (since no layout
    * engine is available server-side). For group elements, the bounding box is the union of all children's bounding boxes.
    *
    * This is an approximation — browser layout engines compute precise bounding boxes that account for font metrics, transforms, and stroke widths.
    */
  def getBBox(): BBox =
    _tagName match {
      case "rect" | "foreignObject" =>
        BBox(
          parseDouble("x"),
          parseDouble("y"),
          parseDouble("width"),
          parseDouble("height")
        )

      case "circle" =>
        val cx = parseDouble("cx")
        val cy = parseDouble("cy")
        val r  = parseDouble("r")
        BBox(cx - r, cy - r, r * 2, r * 2)

      case "ellipse" =>
        val cx = parseDouble("cx")
        val cy = parseDouble("cy")
        val rx = parseDouble("rx")
        val ry = parseDouble("ry")
        BBox(cx - rx, cy - ry, rx * 2, ry * 2)

      case "line" =>
        val x1 = parseDouble("x1")
        val y1 = parseDouble("y1")
        val x2 = parseDouble("x2")
        val y2 = parseDouble("y2")
        BBox(math.min(x1, x2), math.min(y1, y2), math.abs(x2 - x1), math.abs(y2 - y1))

      case "text" | "tspan" =>
        // Rough text bounding box estimate
        val x    = parseDouble("x")
        val y    = parseDouble("y")
        val text = _textContent.getOrElse("")
        estimateTextBBox(x, y, text)

      case "g" | "svg" =>
        // Union of children bounding boxes
        if (_children.isEmpty) {
          BBox.Empty
        } else {
          _children.foldLeft(BBox.Empty) { (acc, child) =>
            val childBox = child.getBBox()
            if (acc.isEmpty) childBox
            else if (childBox.isEmpty) acc
            else acc.union(childBox)
          }
        }

      case "path" =>
        // For paths, estimate from the d attribute is complex;
        // return empty box as a conservative default.
        // Renderers that need accurate path bounds should compute them
        // from the PathData control points directly.
        BBox.Empty

      case _ =>
        // For other elements, union of children or empty
        if (_children.isEmpty) {
          BBox.Empty
        } else {
          _children.foldLeft(BBox.Empty) { (acc, child) =>
            val childBox = child.getBBox()
            if (acc.isEmpty) childBox
            else if (childBox.isEmpty) acc
            else acc.union(childBox)
          }
        }
    }

  // --- Build methods ---

  /** Returns the underlying SvgElement for this builder (snapshot of current state). */
  def node(): SvgElement = buildSingle()

  /** Builds the complete SvgElement tree (recursive snapshot).
    *
    * The built element is immutable. Further modifications to this builder will not affect the returned element.
    */
  def build(): SvgElement = {
    val mergedAttrs = mergeStylesIntoAttributes()
    val children    = _children.map(_.build())
    SvgElement(
      tagName = _tagName,
      attributes = mergedAttrs,
      children = children,
      textContent = _textContent,
      htmlContent = _htmlContent
    )
  }

  /** Returns the number of direct children. */
  def childCount: Int = _children.size

  /** Returns a snapshot of the direct children builders. */
  def children: ArrayBuffer[SvgBuilder] = ArrayBuffer.from(_children)

  // --- Private helpers ---

  /** Builds only this element (no recursive children). */
  private def buildSingle(): SvgElement = {
    val mergedAttrs = mergeStylesIntoAttributes()
    SvgElement(
      tagName = _tagName,
      attributes = mergedAttrs,
      children = ArrayBuffer.empty,
      textContent = _textContent,
      htmlContent = _htmlContent
    )
  }

  /** Merges inline styles into the attributes map, producing a new map. */
  private def mergeStylesIntoAttributes(): LinkedHashMap[String, String] = {
    val merged = LinkedHashMap.from(_attributes)
    if (_styles.nonEmpty) {
      val styleStr = _styles.map { case (k, v) => s"$k: $v" }.mkString("; ")
      val existing = merged.getOrElse("style", "")
      if (existing.isEmpty) {
        merged("style") = styleStr
      } else {
        // Append to existing style attribute
        val separator = if (existing.endsWith(";") || existing.endsWith("; ")) " " else "; "
        merged("style") = existing + separator + styleStr
      }
    }
    merged
  }

  /** Finds the index of the first child matching a selector. Returns -1 if not found. */
  private def findChildIndex(selector: String): Int =
    boundary[Int] {
      var i = 0
      while (i < _children.size) {
        if (matchesSelector(_children(i), selector)) {
          break(i)
        }
        i += 1
      }
      -1
    }

  /** Tests whether a builder matches a simple CSS selector. */
  private def matchesSelector(builder: SvgBuilder, selector: String): Boolean =
    if (selector.startsWith(".")) {
      // Class selector
      val className = selector.substring(1)
      builder.hasClass(className)
    } else if (selector.startsWith("#")) {
      // ID selector
      val idValue = selector.substring(1)
      builder._attributes.get("id").contains(idValue)
    } else {
      // Tag name selector
      builder._tagName == selector
    }

  /** Parses a double attribute value, returning 0.0 if missing or invalid. */
  private def parseDouble(name: String): Double =
    _attributes.get(name) match {
      case Some(v) =>
        try v.toDouble
        catch { case _: NumberFormatException => 0.0 }
      case None => 0.0
    }

  /** Estimates a bounding box for text content.
    *
    * Uses a rough average character width of 8px and a default font size of 16px. This is a coarse approximation — real text metrics depend on font family, weight, size, and the text itself.
    */
  private def estimateTextBBox(x: Double, y: Double, text: String): BBox =
    if (text.isEmpty) {
      BBox.Empty
    } else {
      // Try to get font-size from attributes or styles
      val fontSize = _attributes
        .get("font-size")
        .orElse(_styles.get("font-size"))
        .flatMap { s =>
          try Some(s.replaceAll("[^0-9.]", "").toDouble)
          catch { case _: NumberFormatException => None }
        }
        .getOrElse(16.0)

      // Average character width is roughly 0.6x the font size for proportional fonts
      val avgCharWidth = fontSize * 0.6
      val width        = text.length * avgCharWidth
      val height       = fontSize * 1.2 // Line height ~1.2x font size

      // Text baseline is at y, so the box extends upward
      BBox(x, y - fontSize, width, height)
    }
}

object SvgBuilder {

  /** Creates a new root builder with the given tag name.
    *
    * Mirrors D3's `d3.create(name)`.
    */
  def create(tag: String): SvgBuilder =
    new SvgBuilder(tag, Nullable.empty)

  /** Creates an SVG root builder with standard namespace attributes pre-set.
    *
    * @param viewBox
    *   the viewBox attribute value (e.g. "0 0 800 600")
    */
  def createSvg(viewBox: String): SvgBuilder = {
    val builder = create("svg")
    builder.attr("xmlns", SvgMarkup.SvgNamespace)
    builder.attr("xmlns:xlink", SvgMarkup.XlinkNamespace)
    builder.attr("viewBox", viewBox)
    builder
  }

  /** Creates an SVG root builder with standard namespace attributes and dimensions.
    *
    * @param width
    *   SVG width
    * @param height
    *   SVG height
    * @param viewBox
    *   the viewBox attribute value
    */
  def createSvg(width: Double, height: Double, viewBox: String): SvgBuilder = {
    val builder = createSvg(viewBox)
    builder.attr("width", width)
    builder.attr("height", height)
    builder
  }

  /** Creates an SVG root builder with string dimensions (may include units like "100%").
    *
    * @param width
    *   SVG width (may include units)
    * @param height
    *   SVG height (may include units)
    * @param viewBox
    *   the viewBox attribute value
    */
  def createSvg(width: String, height: String, viewBox: String): SvgBuilder = {
    val builder = createSvg(viewBox)
    builder.attr("width", width)
    builder.attr("height", height)
    builder
  }
}
