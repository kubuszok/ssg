/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9a: config + look/seed plumbing + handDrawnShapeStyles.
 *
 * Proves the FOUNDATION wiring for the mermaid `look=handDrawn` port (no shape
 * render change yet):
 *   - MermaidConfig.handDrawnSeed defaults to 0 (config.schema.yaml:84) and a set
 *     value round-trips through the frontmatter/init-directive overlay path
 *     (MermaidConfig.applyOverlay), exactly as `look` is handled.
 *   - ShapeConfig carries look/handDrawnSeed (defaults + set values).
 *   - FlowchartRenderer.buildShapeConfig threads config.look/config.handDrawnSeed
 *     onto every ShapeConfig it builds (flowDb.ts:882/918 copy config.look onto nodes).
 *   - HandDrawnShapeStyles.{userNodeOverrides,solidStateFill,styles2Map,styles2String}
 *     match the vendored handDrawnShapeStyles.ts (expected values derived from a Node
 *     transcription of the upstream logic).
 */
package ssg
package mermaid
package diagrams
package flowchart

import scala.collection.immutable.{ SeqMap, VectorMap }

import lowlevel.Nullable

import munit.FunSuite

import ssg.data.DataView
import ssg.graphs.commons.layout.dagre.NodeLabel
import ssg.graphs.commons.rough.Options
import ssg.mermaid.render.shapes.{ CompiledStyles, HandDrawnNode, HandDrawnShapeStyles, NodeStyleStrings, ShapeConfig }
import ssg.mermaid.theme.ThemeVariables

final class FlowchartHandDrawnConfigIss1204Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // MermaidConfig.handDrawnSeed — default 0 + overlay round-trip
  // ──────────────────────────────────────────────────────────────────────────

  test("MermaidConfig.handDrawnSeed defaults to 0 (config.schema.yaml:84 random-seed sentinel)") {
    assertEquals(MermaidConfig().handDrawnSeed, 0)
  }

  test("MermaidConfig.handDrawnSeed set value is retained") {
    assertEquals(MermaidConfig(handDrawnSeed = 12345).handDrawnSeed, 12345)
  }

  test("MermaidConfig.handDrawnSeed round-trips through the overlay path (as `look` does)") {
    // The frontmatter/init-directive overlay is applied via applyOverlay (the same
    // AsDataView/FromDataView round-trip that carries `look`). A non-zero seed set in
    // the overlay must land on the effective config.
    val overlay = DataView.from(VectorMap("handDrawnSeed" -> DataView.from(99)))
    val cfg     = MermaidConfig.applyOverlay(MermaidConfig(), overlay)
    assertEquals(cfg.handDrawnSeed, 99)
  }

  test("MermaidConfig.look round-trips through the overlay path (parity guard)") {
    val overlay = DataView.from(VectorMap("look" -> DataView.from("handDrawn")))
    val cfg     = MermaidConfig.applyOverlay(MermaidConfig(), overlay)
    assertEquals(cfg.look, "handDrawn")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // ShapeConfig carries look/handDrawnSeed
  // ──────────────────────────────────────────────────────────────────────────

  test("ShapeConfig defaults: look=classic, handDrawnSeed=0") {
    val sc = ShapeConfig()
    assertEquals(sc.look, "classic")
    assertEquals(sc.handDrawnSeed, 0)
  }

  test("ShapeConfig carries a set look/handDrawnSeed") {
    val sc = ShapeConfig(look = "handDrawn", handDrawnSeed = 55)
    assertEquals(sc.look, "handDrawn")
    assertEquals(sc.handDrawnSeed, 55)
  }

  // ──────────────────────────────────────────────────────────────────────────
  // FlowchartRenderer.buildShapeConfig threads config.look/config.handDrawnSeed
  // ──────────────────────────────────────────────────────────────────────────

  private def nodeLabel(w: Double, h: Double, x: Double, y: Double): NodeLabel = {
    val nl = new NodeLabel
    nl.width = w
    nl.height = h
    nl.x = x
    nl.y = y
    nl
  }

  test("FlowchartRenderer.buildShapeConfig threads config.look/handDrawnSeed onto the ShapeConfig") {
    val node   = FlowNode(id = "A", text = "A")
    val nl     = nodeLabel(40, 30, 10, 20)
    val config = MermaidConfig(look = "handDrawn", handDrawnSeed = 77)
    val sc     = FlowchartRenderer.buildShapeConfig("A", node, nl, "rect", config, 8.0)
    assertEquals(sc.look, "handDrawn")
    assertEquals(sc.handDrawnSeed, 77)
  }

  test("FlowchartRenderer.buildShapeConfig defaults look=classic/seed=0 for a default config") {
    val node = FlowNode(id = "A", text = "A")
    val nl   = nodeLabel(40, 30, 10, 20)
    val sc   = FlowchartRenderer.buildShapeConfig("A", node, nl, "rect", MermaidConfig(), 8.0)
    assertEquals(sc.look, "classic")
    assertEquals(sc.handDrawnSeed, 0)
  }

  // ──────────────────────────────────────────────────────────────────────────
  // HandDrawnShapeStyles — expected values from the vendored handDrawnShapeStyles.ts
  // ──────────────────────────────────────────────────────────────────────────

  private def theme(nodeBorder: String, mainBkg: String): ThemeVariables = {
    val tv = new ThemeVariables
    tv.nodeBorder = nodeBorder
    tv.mainBkg = mainBkg
    tv
  }

  test("solidStateFill builds the striped-fill rough Options (color + seed)") {
    val opts = HandDrawnShapeStyles.solidStateFill("#123", 7)
    assertEquals(
      opts,
      Options(
        fill = Some("#123"),
        hachureAngle = Some(120.0),
        hachureGap = Some(4.0),
        fillWeight = Some(2.0),
        roughness = Some(0.7),
        stroke = Some("#123"),
        seed = Some(7)
      )
    )
  }

  test("userNodeOverrides: node fill/stroke styles win, defaults fill the rest (oracle case A)") {
    val node = HandDrawnNode(cssCompiledStyles = Vector("fill: #f00", "stroke: #00f"))
    val opts = HandDrawnShapeStyles.userNodeOverrides(node, Options(), theme("#333", "#eee"), 42)
    assertEquals(
      opts,
      Options(
        roughness = Some(0.7),
        fill = Some("#f00"),
        fillStyle = Some("hachure"),
        fillWeight = Some(4.0),
        stroke = Some("#00f"),
        seed = Some(42),
        strokeWidth = Some(1.3)
      )
    )
    // field-level guards (mutation resistance)
    assertEquals(opts.roughness, Some(0.7))
    assertEquals(opts.fill, Some("#f00"))
    assertEquals(opts.fillStyle, Some("hachure"))
    assertEquals(opts.fillWeight, Some(4.0))
    assertEquals(opts.stroke, Some("#00f"))
    assertEquals(opts.seed, Some(42))
    assertEquals(opts.strokeWidth, Some(1.3))
  }

  test("userNodeOverrides: no node fill/stroke → theme mainBkg/nodeBorder (oracle case B)") {
    val opts = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), theme("#333", "#eee"), 7)
    assertEquals(opts.fill, Some("#eee"))
    assertEquals(opts.stroke, Some("#333"))
    assertEquals(opts.seed, Some(7))
  }

  test("userNodeOverrides: empty-string fill value is falsy → theme mainBkg (oracle case C)") {
    val opts = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(cssStyles = Vector("fill:")), Options(), theme("#333", "#eee"), 1)
    assertEquals(opts.fill, Some("#eee"))
  }

  test("userNodeOverrides: cssStyles override cssCompiledStyles on duplicate keys (oracle case D)") {
    val node = HandDrawnNode(cssCompiledStyles = Vector("fill: red"), cssStyles = Vector("fill: blue"))
    val opts = HandDrawnShapeStyles.userNodeOverrides(node, Options(), theme("#333", "#eee"), 2)
    assertEquals(opts.fill, Some("blue"))
  }

  test("userNodeOverrides: present options fields win over defaults and pass-through fields survive (oracle case E)") {
    // options carries roughness=2 (overrides 0.7), fill="#abc" (overrides node/theme),
    // and bowing=9 (a field with no default — must pass through, like Object.assign).
    val opts = HandDrawnShapeStyles.userNodeOverrides(
      HandDrawnNode(),
      Options(roughness = Some(2.0), fill = Some("#abc"), bowing = Some(9.0)),
      theme("#333", "#eee"),
      3
    )
    assertEquals(opts.roughness, Some(2.0))
    assertEquals(opts.fill, Some("#abc"))
    assertEquals(opts.stroke, Some("#333"))
    assertEquals(opts.seed, Some(3))
    assertEquals(opts.bowing, Some(9.0)) // passed through unchanged
    assertEquals(opts.fillStyle, Some("hachure"))
    assertEquals(opts.fillWeight, Some(4.0))
    assertEquals(opts.strokeWidth, Some(1.3))
  }

  // ──────────────────────────────────────────────────────────────────────────
  // helpers: styles2Map / compileStyles / styles2String
  // ──────────────────────────────────────────────────────────────────────────

  test("styles2Map: split on ':', undefined value for no-colon entries, insertion order") {
    val m = HandDrawnShapeStyles.styles2Map(Vector("a:b:c", "d", "fill: x "))
    assertEquals(m.keys.toVector, Vector("a", "d", "fill"))
    // JS `const [key, value] = "a:b:c".split(':')` keeps only the first two parts: value="b", "c" discarded.
    assertEquals(m("a").toOption, Some("b"))
    assertEquals(m("d"), Nullable.empty[String]) // no colon → undefined
    assertEquals(m("fill").toOption, Some("x")) // trimmed
  }

  test("styles2Map: a trailing colon yields an empty-string value, NOT undefined (JS split(':') keeps the trailing field)") {
    // JS `"fill:".split(':')` == ["fill", ""] → value = "".trim() == "" (defined, empty).
    // Scala's default `split(":")` DROPS trailing empties, so the port uses `split(":", -1)`.
    // This is the distinguishing case: the faithful form maps "fill" -> Some(""); the
    // dropped-limit mutant would map it to Nullable.empty (undefined), diverging from JS.
    val m = HandDrawnShapeStyles.styles2Map(Vector("fill:"))
    assertEquals(m.keys.toVector, Vector("fill"))
    assertEquals(m("fill").toOption, Some(""))
    assertNotEquals(m("fill"), Nullable.empty[String])
  }

  test("styles2Map: extra colons beyond the first stay discarded (JS [key, value] destructure keeps 2 parts)") {
    // JS `const [key, value] = "style:a:b".split(':')` → key="style", value="a"; "b" is discarded.
    val m = HandDrawnShapeStyles.styles2Map(Vector("style:a:b"))
    assertEquals(m("style").toOption, Some("a"))
  }

  test("compileStyles: cssCompiledStyles ++ cssStyles, cssStyles wins duplicates, order preserved") {
    val node                     = HandDrawnNode(cssCompiledStyles = Vector("fill: red", "stroke: green"), cssStyles = Vector("fill: blue"))
    val CompiledStyles(m, array) = HandDrawnShapeStyles.compileStyles(node)
    assertEquals(m("fill").toOption, Some("blue")) // cssStyles override
    assertEquals(m("stroke").toOption, Some("green"))
    assertEquals(array.map(_._1), Vector("fill", "stroke")) // fill keeps its original position
  }

  test("styles2String: partitions into label/node/border/background (oracle case S)") {
    val node                                                                         = HandDrawnNode(cssCompiledStyles = Vector("color: red", "fill: blue", "stroke-width: 2", "stroke: green"))
    val NodeStyleStrings(labelStyles, nodeStyles, _, borderStyles, backgroundStyles) =
      HandDrawnShapeStyles.styles2String(node)
    assertEquals(labelStyles, "color:red !important")
    assertEquals(nodeStyles, "fill:blue !important;stroke-width:2 !important;stroke:green !important")
    assertEquals(borderStyles, Vector("stroke-width:2 !important", "stroke:green !important"))
    assertEquals(backgroundStyles, Vector("fill:blue !important"))
  }

  test("styles2Map: re-setting a key keeps its original position (SeqMap ordering guard)") {
    val m: SeqMap[String, Nullable[String]] =
      HandDrawnShapeStyles.styles2Map(Vector("a: 1", "b: 2", "a: 3"))
    assertEquals(m.keys.toVector, Vector("a", "b"))
    assertEquals(m("a").toOption, Some("3"))
  }
}
