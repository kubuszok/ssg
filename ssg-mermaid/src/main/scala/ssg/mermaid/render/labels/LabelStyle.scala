/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid (label styling configuration)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Extracts label style configuration from inline JS objects
 *   Idiom: Immutable case class with defaults
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package labels

/** Style configuration for rendering text labels.
  *
  * @param fontSize
  *   font size in pixels (default 14)
  * @param fontFamily
  *   CSS font-family value (default "sans-serif")
  * @param fontWeight
  *   CSS font-weight value (default "normal")
  * @param fontStyle
  *   CSS font-style value (default "normal")
  * @param fill
  *   text fill color (default "#333")
  * @param cssClass
  *   CSS class to apply to the label group
  * @param style
  *   additional inline CSS styles
  * @param textAlign
  *   text alignment: "left", "center", or "right" (default "center")
  */
final case class LabelStyle(
  fontSize:   Double = 14.0,
  fontFamily: String = "sans-serif",
  fontWeight: String = "normal",
  fontStyle:  String = "normal",
  fill:       String = "#333",
  cssClass:   String = "",
  style:      String = "",
  textAlign:  String = "center"
)
