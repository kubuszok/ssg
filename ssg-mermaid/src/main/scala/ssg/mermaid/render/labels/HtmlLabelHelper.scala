/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/rendering-util/createText.ts
 *   (addHtmlSpan :21-57 + the useHtmlLabels branch of createText :215-230)
 *   plus the htmlLabels half of dagre-wrapper/shapes/util.js labelHelper (:9, :44-103).
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces the D3 `el.append('foreignObject')` chain with SvgBuilder.
 *     `createText.ts` is the single upstream chokepoint for label emission; this is its
 *     Scala mirror. The 11 flowchart shapes + cluster/class/state/er label sites all route
 *     through here so the htmlLabels-vs-SVG-text decision lives in one place.
 *   Idiom: Pure SvgBuilder construction; no DOM. Sizing uses TextMetrics in place of the
 *     browser `getBoundingClientRect()` (shapes/util.js:90/:104-106), so the foreignObject
 *     node geometry matches the SVG-text path and dagre layout inputs do not shift.
 *   Renames: addHtmlSpan() → appendHtmlSpan(); createText() useHtmlLabels branch → createText().
 *   Out of scope: markdownToHTML / markdownAutoWrap (ISS-1203) — the markdown→HTML seam uses
 *     TextUtils.stripMarkdown passthrough. The `look` (handDrawn) branch (ISS-1204) is NOT
 *     implemented; it would slot in alongside the htmlLabels branch in [[ShapeLabel]].
 *   Note: KaTeX rendering (createText.ts:25-26/:222-226) is not wired here.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-06-17
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package labels

import ssg.mermaid.render.text.{ TextMetrics, TextUtils }
import ssg.graphs.commons.svg.SvgBuilder

/** HTML-label rendering helper.
  *
  * This is the Scala mirror of upstream's single `createText` chokepoint (`rendering-util/createText.ts`). When `htmlLabels` is enabled, mermaid emits an SVG `<foreignObject>` wrapping an XHTML
  * `<div>`/`<span>` instead of an SVG `<text>`; this object builds that structure faithfully to `addHtmlSpan` (:21-57) and the `useHtmlLabels` branch of `createText` (:215-230).
  */
object HtmlLabelHelper {

  /** The XHTML namespace stamped on the inner `<div>` (createText.ts:40 / createLabel.js:37). */
  val XhtmlNamespace: String = "http://www.w3.org/1999/xhtml"

  /** Appends a `<foreignObject>` HTML label to `element`.
    *
    * Faithful port of `addHtmlSpan` (createText.ts:21-57):
    * {{{
    * const fo = element.append('foreignObject');
    * const div = fo.append('xhtml:div');
    * const span = div.append('span');
    * span.html(label);
    * span.attr('class', `${labelClass} ${classes}`);
    * div.style('display', 'table-cell');
    * div.style('white-space', 'nowrap');
    * div.style('line-height', '1.5');
    * div.style('max-width', width + 'px');
    * div.style('text-align', 'center');
    * div.attr('xmlns', 'http://www.w3.org/1999/xhtml');
    * }}}
    *
    * The browser's `getBoundingClientRect()` resize branch (createText.ts:46-51) is a layout-time concern with no server-side equivalent and is intentionally omitted; the `fo` width/height attributes
    * are set by the caller ([[createText]]) from [[TextMetrics]].
    *
    * @param element
    *   parent SvgBuilder to append the `<foreignObject>` to
    * @param labelHtml
    *   already-sanitized HTML string for the `<span>` content
    * @param width
    *   wrapping width in px, used for the div `max-width`
    * @param classes
    *   extra CSS classes appended after the `nodeLabel`/`edgeLabel` class
    * @param isNode
    *   true → `nodeLabel`, false → `edgeLabel` (createText.ts:28)
    * @param labelStyle
    *   optional inline style applied to both the span and div (createText.ts:31/:34)
    * @param addBackground
    *   when true, the div gets the `labelBkg` class (createText.ts:41-43)
    * @return
    *   the `<foreignObject>` SvgBuilder
    */
  def appendHtmlSpan(
    element:       SvgBuilder,
    labelHtml:     String,
    width:         Double,
    classes:       String,
    isNode:        Boolean,
    labelStyle:    String = "",
    addBackground: Boolean = false
  ): SvgBuilder = {
    val fo  = element.append("foreignObject")
    val div = fo.append("div")
    // createText.ts:28
    val labelClass = if (isNode) "nodeLabel" else "edgeLabel"
    val span       = div.append("span")
    // createText.ts:30 — span.html(label): raw, unescaped HTML content
    span.html(labelHtml)
    // createText.ts:31 — applyStyle(span, node.labelStyle)
    if (labelStyle.nonEmpty) {
      span.attr("style", labelStyle)
    }
    // createText.ts:32 — span.attr('class', `${labelClass} ${classes}`)
    val spanClass = if (classes.nonEmpty) s"$labelClass $classes" else labelClass
    span.attr("class", spanClass)

    // createText.ts:34 — applyStyle(div, node.labelStyle)
    if (labelStyle.nonEmpty) {
      div.attr("style", labelStyle)
    }
    // createText.ts:35-39
    div.style("display", "table-cell")
    div.style("white-space", "nowrap")
    div.style("line-height", "1.5")
    div.style("max-width", s"${formatPx(width)}px")
    div.style("text-align", "center")
    // createText.ts:40
    div.attr("xmlns", XhtmlNamespace)
    // createText.ts:41-43
    if (addBackground) {
      div.classed("labelBkg", true)
    }
    fo
  }

  /** Creates a label, emitting either HTML (`useHtmlLabels`) or SVG text.
    *
    * Faithful port of `createText` (createText.ts:190-277). When `useHtmlLabels` is true it decodes entities + applies the fontawesome icon substitution (createText.ts:218-219) and appends an HTML
    * span via [[appendHtmlSpan]]; otherwise it delegates to the SVG-text path ([[LabelRenderer.renderLabel]]).
    *
    * The caller is responsible for running the security gate ([[ssg.mermaid.render.text.TextUtils.sanitizeTextHtml]]) before passing `text`, mirroring `labelHelper` (shapes/util.js:44-48
    * `sanitizeText(decodeEntities(labelText), config)`).
    *
    * markdown→HTML (createText.ts:218 `markdownToHTML`) is OUT OF SCOPE (ISS-1203); the `text` is used as-is for the HTML branch.
    *
    * @param el
    *   parent SvgBuilder
    * @param text
    *   the (already security-gated) label text
    * @param useHtmlLabels
    *   whether to emit HTML (createText.ts:215)
    * @param isNode
    *   node vs edge label
    * @param classes
    *   extra CSS classes for the HTML span
    * @param width
    *   wrapping width in px
    * @param style
    *   inline label style
    * @param addBackground
    *   add a label background (svg path) / labelBkg (html path)
    * @return
    *   the created label SvgBuilder (foreignObject for HTML, label group for SVG text)
    */
  def createText(
    el:            SvgBuilder,
    text:          String,
    useHtmlLabels: Boolean,
    isNode:        Boolean,
    classes:       String = "",
    width:         Double = 200.0,
    style:         String = "",
    addBackground: Boolean = false
  ): SvgBuilder =
    if (useHtmlLabels) {
      // createText.ts:218-219 — markdownToHTML is out of scope (ISS-1203); use text as-is,
      // then decode entities + apply fontawesome icon substitution.
      val htmlText            = text
      val decodedReplacedText = replaceIconSubstring(TextUtils.entityDecode(htmlText))
      // createText.ts:227 — labelStyle: style.replace('fill:', 'color:')
      val labelStyle = style.replace("fill:", "color:")
      val fo         = appendHtmlSpan(el, decodedReplacedText, width, classes, isNode, labelStyle, addBackground)
      // shapes/util.js:104-106 — size the foreignObject from the (estimated) bbox so that dagre
      // layout inputs match the SVG-text path. Browser uses getBoundingClientRect(); we use
      // TextMetrics on the plain-text content.
      val plain = TextUtils.entityDecode(stripTags(decodedReplacedText))
      val bbox  = TextMetrics.measureText(plain, 14.0, "sans-serif")
      fo.attr("width", bbox.width)
      fo.attr("height", bbox.height)
      fo
    } else {
      // createText.ts:231-275 — SVG text path. Delegated to LabelRenderer (the SVG-text
      // chokepoint) so the geometry is byte-identical to the legacy inline path.
      LabelRenderer.renderLabel(el, text, 0.0, 0.0, LabelStyle(cssClass = classes, style = style))
    }

  /** Convert fontawesome labels into fontawesome icons via a regex pattern.
    *
    * Faithful port of `replaceIconSubstring` (createText.ts:180-186):
    * {{{
    * return text.replace(
    *   /fa[bklrs]?:fa-[\w-]+/g,
    *   (s) => `<i class='${s.replace(':', ' ')}'></i>`
    * );
    * }}}
    *
    * @param text
    *   the raw string to convert
    * @return
    *   string with fontawesome icons as `<i>` tags
    */
  def replaceIconSubstring(text: String): String =
    FaIconRegex.replaceAllIn(
      text,
      m => {
        val s = m.matched
        // `${s.replace(':', ' ')}` — only the FIRST ':' is replaced (JS String.replace, no /g)
        val cls = s.replaceFirst(":", " ")
        s"<i class='${java.util.regex.Matcher.quoteReplacement(cls)}'></i>"
      }
    )

  /** `/fa[bklrs]?:fa-[\w-]+/g` (createText.ts:183). */
  private val FaIconRegex = """fa[bklrs]?:fa-[\w-]+""".r

  /** Strips HTML tags for plain-text width measurement (no upstream analogue; server-side substitute for the browser measuring the rendered span).
    */
  private def stripTags(html: String): String =
    html.replaceAll("<[^>]*>", "")

  /** Formats a px width without a trailing `.0` for integral values. */
  private def formatPx(width: Double): String =
    if (width == width.toLong.toDouble) width.toLong.toString else width.toString
}
