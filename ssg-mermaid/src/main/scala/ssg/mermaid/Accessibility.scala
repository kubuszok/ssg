/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/accessibility.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 selection API with SvgBuilder methods
 *   Idiom: Object methods instead of exported functions; SvgBuilder instead of D3Element
 *   Renames: D3Element → SvgBuilder
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

import ssg.commons.Nullable
import ssg.mermaid.svg.SvgBuilder

/** Accessibility (a11y) functions, types, helpers.
  *
  * @see
  *   https://www.w3.org/WAI/
  * @see
  *   https://www.w3.org/TR/wai-aria-1.1/
  * @see
  *   https://www.w3.org/TR/svg-aam-1.0/
  */
object Accessibility {

  /** SVG element role: The SVG element role _should_ be set to 'graphics-document' per SVG standard but in practice is not always done by browsers, etc. (As of 2022-12-08). A fallback role of
    * 'document' should be set for those browsers, etc., that only support ARIA 1.0.
    *
    * @see
    *   https://www.w3.org/TR/svg-aam-1.0/#roleMappingGeneralRules
    * @see
    *   https://www.w3.org/TR/graphics-aria-1.0/#graphics-document
    */
  val SvgRole: String = "graphics-document document"

  /** Add role and aria-roledescription to the svg element.
    *
    * @param svg - builder for the SVG element
    * @param diagramType - diagram name for the aria-roledescription
    */
  def setA11yDiagramInfo(svg: SvgBuilder, diagramType: String): Unit = {
    svg.attr("role", SvgRole)
    if (diagramType.nonEmpty) {
      svg.attr("aria-roledescription", diagramType)
    }
  }

  /** Add an accessible title and/or description element to a chart.
    * The title is usually not displayed and the description is never displayed.
    *
    * The following charts display their title as a visual and accessibility element: gantt.
    *
    * @param svg - SVG builder to insert the a11y title and desc info
    * @param a11yTitle - a11y title. empty means skip
    * @param a11yDesc - a11y description. empty means skip
    * @param baseId - id used to construct the a11y title and description id
    */
  def addSVGa11yTitleDescription(
    svg:       SvgBuilder,
    a11yTitle: Nullable[String],
    a11yDesc:  Nullable[String],
    baseId:    String
  ): Unit = {
    a11yDesc.foreach { desc =>
      if (desc.nonEmpty) {
        val descId = s"chart-desc-$baseId"
        svg.attr("aria-describedby", descId)
        svg.insert("desc", ":first-child").attr("id", descId).text(desc)
      }
    }
    a11yTitle.foreach { title =>
      if (title.nonEmpty) {
        val titleId = s"chart-title-$baseId"
        svg.attr("aria-labelledby", titleId)
        svg.insert("title", ":first-child").attr("id", titleId).text(title)
      }
    }
  }

  /** Overload accepting plain strings (empty string = skip). */
  def addSVGa11yTitleDescription(
    svg:       SvgBuilder,
    a11yTitle: String,
    a11yDesc:  String,
    baseId:    String
  ): Unit =
    addSVGa11yTitleDescription(
      svg,
      if (a11yTitle.isEmpty) Nullable.empty else Nullable(a11yTitle),
      if (a11yDesc.isEmpty) Nullable.empty else Nullable(a11yDesc),
      baseId
    )
}
