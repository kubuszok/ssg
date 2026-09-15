/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid (cluster/subgraph configuration)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Extracts cluster/subgraph configuration into a dedicated case class
 *   Idiom: Immutable case class with defaults; replaces ad-hoc JS config objects
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package clusters

import ssg.mermaid.theme.ThemeVariables

/** Configuration for rendering a cluster (subgraph container).
  *
  * Clusters are visual groupings of nodes in flowcharts and other diagrams. They render as a bordered rectangle with a title, containing child nodes.
  *
  * @param id
  *   unique cluster identifier
  * @param title
  *   display title for the cluster (shown at the top)
  * @param x
  *   center x coordinate (from layout)
  * @param y
  *   center y coordinate (from layout)
  * @param width
  *   cluster width (from layout, including padding)
  * @param height
  *   cluster height (from layout, including padding)
  * @param rx
  *   corner radius for rounded borders (0 for sharp)
  * @param ry
  *   corner radius for rounded borders (0 for sharp)
  * @param cssClass
  *   CSS class for the cluster group
  * @param style
  *   inline CSS styles for the cluster border
  * @param labelStyle
  *   inline CSS styles for the cluster title
  * @param padding
  *   internal padding between the cluster border and child nodes
  * @param borderColor
  *   border stroke color (default from theme)
  * @param backgroundColor
  *   background fill color (default from theme)
  * @param htmlLabels
  *   when true, the cluster title is rendered as an HTML `<foreignObject>` (ISS-1205) instead of an SVG `<text>`. Defaults to false so existing SVG-text geometry is unchanged.
  * @param securityLevel
  *   the resolved `MermaidConfig.securityLevel`; gates HTML-label sanitization (diagrams/common/common.ts:66-94).
  * @param look
  *   the resolved `MermaidConfig.look` ("classic" or "handDrawn"); upstream `clusters.js:66` branches `node.look === "handDrawn"` into `rough.svg(...)` for the subgraph rect. Threaded here so
  *   [[ClusterRenderer]] can select the hand-drawn path (ISS-1204 Chip 9i). Defaults to "classic" so classic geometry is unchanged.
  * @param handDrawnSeed
  *   the resolved `MermaidConfig.handDrawnSeed`; seeds the rough.js PRNG on the hand-drawn path so sketch output is reproducible. Defaults to 0 (the "random seed" sentinel).
  * @param themeVariables
  *   the resolved theme variables (colors etc.). Upstream `clusters.js:19-21` reads `clusterBkg`/`clusterBorder` from the ambient `getConfig().themeVariables` and passes them as the base rough
  *   options merged by `userNodeOverrides`. `borderColor`/`backgroundColor` already carry `clusterBorder`/`clusterBkg`; the full theme is threaded here so `HandDrawnShapeStyles.userNodeOverrides` can
  *   default any absent stroke/fill from `nodeBorder`/`mainBkg`. Only consulted on the hand-drawn path; defaults to a fresh [[ThemeVariables]] (the classic path never reads it).
  */
final case class ClusterConfig(
  id:              String = "",
  title:           String = "",
  x:               Double = 0,
  y:               Double = 0,
  width:           Double = 0,
  height:          Double = 0,
  rx:              Double = 0,
  ry:              Double = 0,
  cssClass:        String = "",
  style:           String = "",
  labelStyle:      String = "",
  padding:         Double = 8,
  borderColor:     String = "#bbb",
  backgroundColor: String = "#ececff",
  htmlLabels:      Boolean = false,
  securityLevel:   String = "strict",
  look:            String = "classic",
  handDrawnSeed:   Int = 0,
  themeVariables:  ThemeVariables = new ThemeVariables()
)
