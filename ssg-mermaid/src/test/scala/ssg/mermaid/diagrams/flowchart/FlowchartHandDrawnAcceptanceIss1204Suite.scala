/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 acceptance (sub-chip 9j): the end-to-end hand-drawn flowchart pipeline.
 *
 * This is the ISS-1204 acceptance criterion. It drives the FULL flowchart -> SVG
 * entry point (`FlowchartRenderer.render(db, config)`) on a small flowchart (two
 * rectangle nodes joined by an edge, wrapped in a subgraph/cluster) and asserts
 * that with `look = "handDrawn"` EVERY drawn primitive is a rough sketch, while
 * with `look = "classic"` every primitive is the classic plain shape. It also
 * pins seed determinism and the `rough-node` container-class activation (Part 2).
 *
 * Faithful to:
 *   - flowDb.ts:882/918 (copy `config.look`/`handDrawnSeed` onto every node)
 *   - rendering-util/rendering-elements/shapes/drawRect.ts (rc.path/rc.rectangle node)
 *   - rendering-util/rendering-elements/shapes/util.js:135-136 (getNodeClasses ->
 *     the hand-drawn shape group carries `rough-node` in place of `node`)
 *   - edges.js:513 (rc.path(linePath, { roughness: 0.3, seed }) sketch edge)
 *   - clusters.js:66-84 (rc.path(createRoundedRectPathD(...)) sketch cluster)
 *   - diagrams/flowchart/styles.ts:62 (`.rough-node .label text , .node .label text`)
 *
 * Differential note: a full upstream mermaid render requires a browser/jsdom DOM
 * plus a built rough.js bundle, which this cross-platform (JVM/JS/Native) test
 * harness cannot execute. This suite therefore proves acceptance STRUCTURALLY
 * (every primitive routes into the rough sketch path end-to-end) and leans on the
 * per-primitive byte-exactness already established against Node oracles by the
 * Chip 1-8 suites (RNG sequences, OpSet derivation, and path `d` emission are all
 * byte-identical there). No upstream byte-diff is claimed here because none was run.
 */
package ssg
package mermaid
package diagrams
package flowchart

import munit.FunSuite

import ssg.mermaid.MermaidConfig

final class FlowchartHandDrawnAcceptanceIss1204Suite extends FunSuite {

  /** Two rectangle nodes joined by an edge, wrapped in a subgraph/cluster. */
  private val source: String =
    "flowchart TD\n" +
      "  subgraph one\n" +
      "    A[Start] --> B[End]\n" +
      "  end"

  private def render(look: String, seed: Int): String = {
    val db = FlowchartParser.parse(source)
    FlowchartRenderer.render(db, MermaidConfig(look = look, handDrawnSeed = seed))
  }

  /** Value of the first `d="..."` path attribute strictly after `marker` (the marker itself is skipped so a marker such as `id="L-A-B-0"`, which contains the substring `d="`, does not shadow the real
    * path attribute).
    */
  private def firstPathD(s: String, marker: String): String = {
    val i = s.indexOf(marker)
    assert(i >= 0, s"expected to find `$marker` in:\n$s")
    val region = s.substring(i + marker.length)
    val open   = region.indexOf("d=\"")
    assert(open >= 0, s"expected a path `d` attribute after `$marker`")
    val start = open + 3
    val end   = region.indexOf("\"", start)
    region.substring(start, end)
  }

  // A rough sketch path is a sequence of cubic bezier ops; RoughGenerator.opsToPath emits
  // each bcurveTo as a `C` command, so the presence of a `C` op in a shape's `d` is the
  // signature of rough-sketched geometry (the classic straight edge / rounded rect never
  // emits `C` for this graph).
  private def isRoughSketch(d: String): Boolean = d.contains("C")

  test("Iss1204 acceptance: handDrawn renders every drawn primitive as a rough sketch") {
    val svg = render("handDrawn", 42)

    // NODE: the rectangle node's shape is a rough `<g class="node-shape">` holding bcurveTo
    // paths (drawRect.ts), NOT the classic `<rect class="node-shape">`.
    assert(
      svg.contains("<g class=\"node-shape\">"),
      s"hand-drawn node shape must be a rough <g>, got:\n$svg"
    )
    val nodeD = firstPathD(svg, "<g class=\"node-shape\">")
    assert(isRoughSketch(nodeD), s"hand-drawn node shape must be a rough bcurve path, got d=$nodeD")

    // EDGE: rough sketch path (roughness 0.3, edges.js:513) carrying the `transition` class,
    // in place of the plain interpolated `<path>`.
    assert(
      svg.contains("class=\"transition\""),
      s"hand-drawn edge must be a rough sketch path with the `transition` class, got:\n$svg"
    )
    val edgeD = firstPathD(svg, "id=\"L-A-B-0\"")
    assert(isRoughSketch(edgeD), s"hand-drawn edge must be a rough bcurve sketch, got d=$edgeD")

    // CLUSTER/subgraph: rough `<g class="cluster-bg">` of bcurveTo paths (clusters.js:66),
    // in place of the classic rounded `<rect class="cluster-bg">`.
    assert(
      svg.contains("<g class=\"cluster-bg\">"),
      s"hand-drawn cluster must be a rough <g>, got:\n$svg"
    )
    val clusterD = firstPathD(svg, "<g class=\"cluster-bg\">")
    assert(isRoughSketch(clusterD), s"hand-drawn cluster must be a rough bcurve path, got d=$clusterD")

    // Every drawn shape is sketchy: no classic `<rect>` survives in the hand-drawn render
    // (node + cluster both become rough paths; markers are paths; there are no edge labels).
    assert(!svg.contains("<rect"), s"hand-drawn render must emit no classic <rect> shape, got:\n$svg")
  }

  test("Iss1204 acceptance: classic renders plain primitives and no rough sketch") {
    val svg = render("classic", 42)

    // NODE: classic `<rect class="node-shape">`, not a rough `<g class="node-shape">`.
    assert(svg.contains("<rect"), s"classic render must emit plain <rect> shapes, got:\n$svg")
    assert(svg.contains("class=\"node-shape\""), s"classic node must carry the node-shape class, got:\n$svg")
    assert(
      !svg.contains("<g class=\"node-shape\">"),
      s"classic node shape must be a plain <rect>, not a rough <g>, got:\n$svg"
    )

    // EDGE: plain interpolated `<path>`, with no rough `transition` sketch.
    assert(!svg.contains("class=\"transition\""), s"classic edge must not carry the rough `transition` class, got:\n$svg")
    val edgeD = firstPathD(svg, "id=\"L-A-B-0\"")
    assert(!isRoughSketch(edgeD), s"classic straight edge must be a plain (non-bcurve) path, got d=$edgeD")

    // CLUSTER: rounded `<rect class="cluster-bg">`, not a rough `<g>`.
    assert(
      !svg.contains("<g class=\"cluster-bg\">"),
      s"classic cluster must be a plain rounded <rect>, not a rough <g>, got:\n$svg"
    )
    assert(svg.contains("class=\"cluster-bg\""), s"classic cluster must carry the cluster-bg class, got:\n$svg")
  }

  test("Iss1204 acceptance: hand-drawn output is deterministic in handDrawnSeed") {
    // Same seed -> byte-identical SVG (the rough PRNG is seeded by config.handDrawnSeed).
    val a1 = render("handDrawn", 42)
    val a2 = render("handDrawn", 42)
    assertEquals(a1, a2, "same seed must produce identical hand-drawn SVG")

    // Different seed -> a different sketch (the node geometry wobbles differently).
    val b = render("handDrawn", 99)
    assert(a1 != b, "a different seed must produce a different hand-drawn sketch")
    val nodeA = firstPathD(a1, "<g class=\"node-shape\">")
    val nodeB = firstPathD(b, "<g class=\"node-shape\">")
    assert(nodeA != nodeB, s"a different seed must change the node sketch path\n seed42=$nodeA\n seed99=$nodeB")
  }

  test("Iss1204 rough-node: hand-drawn node group carries rough-node; classic keeps node (styles.ts:62 live)") {
    val hd = render("handDrawn", 42)
    val cl = render("classic", 42)

    // Part 2: the hand-drawn shape group (the one holding the `.label`) carries the
    // `rough-node` container class in place of `node` (getNodeClasses, util.js:136).
    assert(hd.contains("class=\"rough-node default\" id=\"A\""), s"hand-drawn node A group must carry rough-node, got:\n$hd")
    assert(hd.contains("class=\"rough-node default\" id=\"B\""), s"hand-drawn node B group must carry rough-node, got:\n$hd")

    // Classic keeps the byte-identical `node` container class on the shape group; no
    // element carries a `rough-node` class attribute (the `.rough-node` CSS selector text
    // is present in both stylesheets, hence the `class="rough-node` attribute-form check).
    assert(cl.contains("class=\"node default\" id=\"A\""), s"classic node A group must keep the node class, got:\n$cl")
    assert(!cl.contains("class=\"rough-node"), s"classic render must apply no rough-node class attribute, got:\n$cl")

    // The `.rough-node .label text` rule (styles.ts:62) is no longer dead: the hand-drawn
    // render both emits the rule AND an element matching it (a `.rough-node` group whose
    // subtree contains a `.label` with text).
    assert(hd.contains(".rough-node .label text"), s"the rough-node label rule must be emitted, got:\n$hd")
    assert(hd.contains("class=\"rough-node"), s"an element must match the .rough-node rule, got:\n$hd")
  }
}
