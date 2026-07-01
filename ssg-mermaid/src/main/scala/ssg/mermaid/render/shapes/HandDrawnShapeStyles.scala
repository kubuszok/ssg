/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/rendering-util/rendering-elements/shapes/handDrawnShapeStyles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: the upstream helpers read their inputs from a global `getConfig()`
 *     (themeVariables + handDrawnSeed) and from a `Node`. SSG has no ambient config,
 *     so the theme variables and `handDrawnSeed` are threaded as explicit parameters,
 *     and the `Node` fields actually consumed here (`cssCompiledStyles`, `cssStyles`)
 *     are modelled by [[HandDrawnNode]]. The other `Node` members are irrelevant to
 *     these style helpers.
 *   Renames: the rough drawing options object returned by `solidStateFill` /
 *     `userNodeOverrides` -> the Chip 4 [[ssg.graphs.commons.rough.Options]] case class.
 *   Idiom (Object.assign): `Object.assign({ ...defaults }, options)` is a present-keys-win
 *     merge — the defaults are kept unless the passed `options` supplies that key. With
 *     `Options`' `Option[T]` members, "present" == `Some`, so each defaulted field is
 *     `options.field.getOrElse(default)` and every other member of `options` passes
 *     through unchanged (`options.copy(...)`).
 *   Idiom (`||`): the JS truthiness fallback `stylesMap.get('x') || themeVar` treats
 *     both an absent entry and an empty-string value as falsy, so the default computation
 *     uses `.filter(_.nonEmpty).getOrElse(themeVar)`. The `options` override, mirroring
 *     `Object.assign`, is applied unconditionally when the key is present (no truthiness
 *     check), so an explicit empty string in `options` still wins.
 *   Idiom (undefined map value): `style.split(':')` destructured as `[key, value]` leaves
 *     `value` undefined when the style has no colon; `value?.trim()` then stores undefined.
 *     Modelled as `Nullable[String]` (per the no-`null` rule) so the undefined-vs-empty
 *     distinction is preserved exactly. `String.split(":", -1)` keeps trailing empty
 *     fields (unlike the default limit) to match JS `split(':')`.
 *   Idiom: JS `Map` preserves insertion order and re-setting a key keeps its original
 *     position while updating the value -> `mutable.LinkedHashMap` collected into a
 *     `SeqMap`, so later duplicates (from `cssStyles`) override earlier `cssCompiledStyles`
 *     values without moving them.
 *
 * upstream-commit: 56a2762 (mermaid handDrawnShapeStyles.ts)
 */
package ssg
package mermaid
package render
package shapes

import scala.collection.immutable.SeqMap
import scala.collection.mutable

import lowlevel.Nullable

import ssg.graphs.commons.rough.Options
import ssg.mermaid.theme.ThemeVariables

/** The subset of a Mermaid `Node` consumed by the hand-drawn style helpers.
  *
  * Upstream `handDrawnShapeStyles.ts` reads only `node.cssCompiledStyles` (styles inherited from the node's CSS classes) and `node.cssStyles` (styles set directly on the node). Both are optional
  * upstream (`node.cssCompiledStyles || []`); here they default to empty vectors.
  *
  * @param cssCompiledStyles
  *   styles of this node coming from the classes it is using (each `"key: value"`)
  * @param cssStyles
  *   styles set directly on the node (each `"key: value"`)
  */
final case class HandDrawnNode(
  cssCompiledStyles: Vector[String] = Vector.empty,
  cssStyles:         Vector[String] = Vector.empty
)

/** Result of [[HandDrawnShapeStyles.compileStyles]]: the deduplicated style map plus its entries as an ordered array. Port of the `{ stylesMap, stylesArray }` object.
  *
  * @param stylesMap
  *   css-property -> value (value is `Nullable.empty` when the source style had no colon)
  * @param stylesArray
  *   `[...stylesMap]` — the map entries in insertion order
  */
final case class CompiledStyles(
  stylesMap:   SeqMap[String, Nullable[String]],
  stylesArray: Vector[(String, Nullable[String])]
)

/** Result of [[HandDrawnShapeStyles.styles2String]]. Port of the returned `{ labelStyles, nodeStyles, stylesArray, borderStyles, backgroundStyles }` object.
  *
  * @param labelStyles
  *   the label-affecting styles, `;`-joined
  * @param nodeStyles
  *   the node-shape styles, `;`-joined
  * @param stylesArray
  *   the ordered style entries (as produced by [[compileStyles]])
  * @param borderStyles
  *   the subset of node styles whose key contains `stroke`
  * @param backgroundStyles
  *   the subset of node styles whose key is `fill`
  */
final case class NodeStyleStrings(
  labelStyles:      String,
  nodeStyles:       String,
  stylesArray:      Vector[(String, Nullable[String])],
  borderStyles:     Vector[String],
  backgroundStyles: Vector[String]
)

/** Hand-drawn (`look: "handDrawn"`) shape style helpers — port of `handDrawnShapeStyles.ts`.
  *
  * These build the rough.js drawing [[ssg.graphs.commons.rough.Options]] each hand-drawn shape passes to `rough.svg().<shape>(...)`, plus the CSS-style extraction helpers shared with the label
  * rendering path.
  */
object HandDrawnShapeStyles {

  /** The label-affecting CSS property keys — styles under these keys are routed to `labelStyles` rather than `nodeStyles` (port of the `key === '...' || ...` chain in `styles2String`). */
  private val LabelStyleKeys: Set[String] = Set(
    "color",
    "font-size",
    "font-family",
    "font-weight",
    "font-style",
    "text-decoration",
    "text-align",
    "text-transform",
    "line-height",
    "letter-spacing",
    "word-spacing",
    "text-shadow",
    "text-overflow",
    "white-space",
    "word-wrap",
    "word-break",
    "overflow-wrap",
    "hyphens"
  )

  // Striped fill like start or fork nodes in state diagrams
  def solidStateFill(color: String, handDrawnSeed: Int): Options =
    Options(
      fill = Some(color),
      hachureAngle = Some(120), // angle of hachure,
      hachureGap = Some(4),
      fillWeight = Some(2),
      roughness = Some(0.7),
      stroke = Some(color),
      seed = Some(handDrawnSeed)
    )

  def compileStyles(node: HandDrawnNode): CompiledStyles = {
    // node.cssCompiledStyles is an array of strings in the form of 'key: value' where jey is the css property and value is the value
    // the array is the styles of node node from the classes it is using
    // node.cssStyles is an array of styles directly set on the node
    // concat the arrays and remove duplicates such that the values from node.cssStyles are used if there are duplicates
    val stylesMap = styles2Map(node.cssCompiledStyles ++ node.cssStyles)
    CompiledStyles(stylesMap, stylesMap.toVector)
  }

  def styles2Map(styles: Vector[String]): SeqMap[String, Nullable[String]] = {
    val styleMap = mutable.LinkedHashMap.empty[String, Nullable[String]]
    styles.foreach { style =>
      val parts               = style.split(":", -1)
      val key                 = parts(0)
      val value: Nullable[String] = if (parts.length > 1) Nullable(parts(1).trim) else Nullable.empty
      styleMap.update(key.trim, value)
    }
    SeqMap.from(styleMap)
  }

  def styles2String(node: HandDrawnNode): NodeStyleStrings = {
    val stylesArray                      = compileStyles(node).stylesArray
    val labelStyles      = mutable.ArrayBuffer.empty[String]
    val nodeStyles       = mutable.ArrayBuffer.empty[String]
    val borderStyles     = mutable.ArrayBuffer.empty[String]
    val backgroundStyles = mutable.ArrayBuffer.empty[String]

    stylesArray.foreach { style =>
      val key    = style._1
      val joined = style._1 + ":" + style._2.toOption.getOrElse("")
      if (LabelStyleKeys.contains(key)) {
        labelStyles += joined + " !important"
      } else {
        nodeStyles += joined + " !important"
        if (key.contains("stroke")) {
          borderStyles += joined + " !important"
        }
        if (key == "fill") {
          backgroundStyles += joined + " !important"
        }
      }
    }

    NodeStyleStrings(
      labelStyles = labelStyles.mkString(";"),
      nodeStyles = nodeStyles.mkString(";"),
      stylesArray = stylesArray,
      borderStyles = borderStyles.toVector,
      backgroundStyles = backgroundStyles.toVector
    )
  }

  // Striped fill like start or fork nodes in state diagrams
  // Upstream note: the JS `options` parameter was typed `any` (marked for later typing);
  // the port types it as the rough [[Options]] case class, so no untyped fallthrough remains.
  def userNodeOverrides(node: HandDrawnNode, options: Options, themeVariables: ThemeVariables, handDrawnSeed: Int): Options = {
    val nodeBorder = themeVariables.nodeBorder
    val mainBkg    = themeVariables.mainBkg
    val stylesMap  = compileStyles(node).stylesMap

    // index the style array to a map object
    val fillDefault   = stylesMap.get("fill").flatMap(_.toOption).filter(_.nonEmpty).getOrElse(mainBkg)
    val strokeDefault = stylesMap.get("stroke").flatMap(_.toOption).filter(_.nonEmpty).getOrElse(nodeBorder)

    options.copy(
      roughness = Some(options.roughness.getOrElse(0.7)),
      fill = Some(options.fill.getOrElse(fillDefault)),
      fillStyle = Some(options.fillStyle.getOrElse("hachure")), // solid fill
      fillWeight = Some(options.fillWeight.getOrElse(4)),
      stroke = Some(options.stroke.getOrElse(strokeDefault)),
      seed = Some(options.seed.getOrElse(handDrawnSeed)),
      strokeWidth = Some(options.strokeWidth.getOrElse(1.3))
    )
  }
}
