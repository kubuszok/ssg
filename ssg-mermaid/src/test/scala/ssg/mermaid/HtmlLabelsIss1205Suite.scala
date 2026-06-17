/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Differential suite for ISS-1205: the htmlLabels foreignObject HTML-label
 * rendering path.
 *
 * Expected values are taken verbatim from the upstream Mermaid sources:
 *   - rendering-util/createText.ts:21-57  — addHtmlSpan foreignObject structure
 *       (<foreignObject> > <div xmlns=…> > <span class="nodeLabel">…)
 *   - rendering-util/createText.ts:215-230 — the useHtmlLabels branch
 *   - dagre-wrapper/shapes/util.js:9       — useHtmlLabels resolution
 *   - diagrams/common/common.ts:66-94      — sanitizeMore / sanitizeText gate
 *       (loose → raw; antiscript|strict → removeScript; otherwise escape < > =)
 *   - diagrams/mindmap/mindmapRenderer.ts:171 — mindmap FORCES htmlLabels=false
 *
 * Non-regression: with htmlLabels OFF every diagram emits the same SVG <text>
 * labels it did before this chokepoint existed (no <foreignObject>), so dagre
 * layout geometry is unchanged.
 *
 * Per-case proof-of-red: reverting the HTML branch in
 * HtmlLabelHelper.createText / ShapeLabel.renderNodeLabel (so the foreignObject
 * is never emitted) makes every "ON" assertion below fail — the foreignObject /
 * span.nodeLabel disappears.
 */
package ssg
package mermaid

import ssg.mermaid.diagrams.flowchart.FlowchartDiagram
import ssg.mermaid.diagrams.class_.ClassDiagram
import ssg.mermaid.diagrams.state.StateDiagram
import ssg.mermaid.diagrams.er.ErDiagram
import ssg.mermaid.diagrams.mindmap.MindmapDiagram
import ssg.mermaid.render.text.TextUtils

import munit.FunSuite

final class HtmlLabelsIss1205Suite extends FunSuite {

  // A daemon-thread timeout guard: any dagre-heavy render that hangs fails fast
  // instead of stalling the suite.
  private def withTimeout[A](millis: Long)(body: => A): A = {
    @volatile var result: Option[A]         = None
    @volatile var error:  Option[Throwable] = None
    val t = new Thread(() =>
      try result = Some(body)
      catch { case e: Throwable => error = Some(e) }
    )
    t.setDaemon(true)
    t.start()
    t.join(millis)
    error.foreach(e => throw e)
    result.getOrElse(throw new AssertionError(s"render timed out after ${millis}ms"))
  }

  private def htmlOn(securityLevel: String = "loose"): MermaidConfig =
    MermaidConfig(
      htmlLabels = true,
      securityLevel = securityLevel,
      flowchart = FlowchartConfig(htmlLabels = true)
    )

  private val htmlOff: MermaidConfig =
    MermaidConfig(
      htmlLabels = false,
      flowchart = FlowchartConfig(htmlLabels = false)
    )

  // ──────────────────────────────────────────────────────────────────────────
  // 1. Flowchart — htmlLabels=true emits <foreignObject> + <span class="nodeLabel";
  //    default (htmlLabels off) emits <text> with NO <foreignObject>.
  //    createText.ts:22 (foreignObject), :29-32 (span class="nodeLabel").
  // ──────────────────────────────────────────────────────────────────────────

  private val flowInput = "flowchart TD\n    A[Hello]"

  test("flowchart: htmlLabels=true emits foreignObject + span.nodeLabel") {
    val out = withTimeout(15000)(FlowchartDiagram.render(flowInput, htmlOn()))
    assert(out.contains("<foreignObject"), s"expected <foreignObject> (createText.ts:22), got: ${out.take(800)}")
    assert(
      out.contains("<span class=\"nodeLabel\""),
      s"expected <span class=\"nodeLabel\" (createText.ts:28,32), got: ${out.take(800)}"
    )
    assert(
      out.contains("xmlns=\"http://www.w3.org/1999/xhtml\""),
      s"expected xhtml div xmlns (createText.ts:40), got: ${out.take(800)}"
    )
    assert(out.contains("Hello"), s"label text must survive, got: ${out.take(800)}")
  }

  test("flowchart: htmlLabels=true via %%{init}%% directive emits foreignObject") {
    val initInput =
      "%%{init:{'flowchart':{'htmlLabels':true},'htmlLabels':true,'securityLevel':'loose'}}%%\nflowchart TD\n    A[Hello]"
    val out = withTimeout(15000)(FlowchartDiagram.render(initInput, MermaidConfig()))
    assert(
      out.contains("<foreignObject"),
      s"init-directive htmlLabels=true must emit foreignObject, got: ${out.take(800)}"
    )
  }

  test("flowchart: htmlLabels OFF emits <text>/<tspan>, NO foreignObject (non-regression)") {
    val out = withTimeout(15000)(FlowchartDiagram.render(flowInput, htmlOff))
    assert(!out.contains("<foreignObject"), s"htmlLabels off must NOT emit foreignObject, got: ${out.take(800)}")
    assert(out.contains("<text"), s"htmlLabels off must emit <text>, got: ${out.take(800)}")
    assert(
      out.contains("class=\"node-label\""),
      s"htmlLabels off must keep the SVG node-label class, got: ${out.take(800)}"
    )
    // Byte-identical SVG-text node label (the legacy inline block reproduced by
    // ShapeLabel's SVG-text branch).
    assert(
      out.contains("dominant-baseline=\"central\" text-anchor=\"middle\" class=\"node-label\">Hello</text>"),
      s"SVG-text label block must be byte-identical to the legacy inline path, got: ${out.take(800)}"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 2. class / state / er — htmlLabels=true emits foreignObject (one case each).
  // ──────────────────────────────────────────────────────────────────────────

  test("class: htmlLabels=true emits foreignObject + span.nodeLabel") {
    val out = withTimeout(15000)(ClassDiagram.render("classDiagram\n    class Animal", htmlOn()))
    assert(out.contains("<foreignObject"), s"class htmlLabels=true must emit foreignObject, got: ${out.take(800)}")
    // createText.ts:31-32 applies the label style BEFORE the class attr, so a styled
    // span serialises as <span style=… class="nodeLabel">; assert the class is present.
    assert(out.contains("class=\"nodeLabel\""), s"class title must be a span.nodeLabel, got: ${out.take(800)}")
  }

  test("class: htmlLabels OFF emits SVG classTitle text, NO foreignObject (non-regression)") {
    val out = withTimeout(15000)(ClassDiagram.render("classDiagram\n    class Animal", htmlOff))
    assert(!out.contains("<foreignObject"), s"class htmlLabels off must NOT emit foreignObject, got: ${out.take(800)}")
    assert(out.contains("classTitle"), s"class htmlLabels off must keep classTitle text, got: ${out.take(800)}")
  }

  test("state: htmlLabels=true emits foreignObject + span.nodeLabel") {
    val out = withTimeout(15000)(StateDiagram.render("stateDiagram-v2\n    [*] --> Still", htmlOn()))
    assert(out.contains("<foreignObject"), s"state htmlLabels=true must emit foreignObject, got: ${out.take(800)}")
    assert(out.contains("<span class=\"nodeLabel\""), s"state label must be a span.nodeLabel, got: ${out.take(800)}")
  }

  test("state: htmlLabels OFF emits SVG stateLabel text, NO foreignObject (non-regression)") {
    val out = withTimeout(15000)(StateDiagram.render("stateDiagram-v2\n    [*] --> Still", htmlOff))
    assert(!out.contains("<foreignObject"), s"state htmlLabels off must NOT emit foreignObject, got: ${out.take(800)}")
    assert(out.contains("stateLabel"), s"state htmlLabels off must keep stateLabel text, got: ${out.take(800)}")
  }

  test("er: htmlLabels=true emits foreignObject + span.nodeLabel") {
    val out = withTimeout(15000)(ErDiagram.render("erDiagram\n    CUSTOMER ||--o{ ORDER : places", htmlOn()))
    assert(out.contains("<foreignObject"), s"er htmlLabels=true must emit foreignObject, got: ${out.take(800)}")
    // createText.ts:31-32 — styled span serialises as <span style=… class="nodeLabel">.
    assert(out.contains("class=\"nodeLabel\""), s"er entity name must be a span.nodeLabel, got: ${out.take(800)}")
  }

  test("er: htmlLabels OFF emits SVG entityLabel text, NO foreignObject (non-regression)") {
    val out = withTimeout(15000)(ErDiagram.render("erDiagram\n    CUSTOMER ||--o{ ORDER : places", htmlOff))
    assert(!out.contains("<foreignObject"), s"er htmlLabels off must NOT emit foreignObject, got: ${out.take(800)}")
    assert(out.contains("entityLabel"), s"er htmlLabels off must keep entityLabel text, got: ${out.take(800)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 3. Security gate — common.ts:66-94 (sanitizeMore / sanitizeText).
  //    loose  → raw HTML passthrough (common.ts:71 the !== 'loose' branch is skipped)
  //    strict → removeScript (DOMPurify): <b> is harmless and kept, but a <script>
  //             payload is stripped (common.ts:69-70 / :58-64)
  //    other  → escape < > = (common.ts:71-76)
  // ──────────────────────────────────────────────────────────────────────────

  test("security: <b> under loose is raw HTML in the emitted label") {
    val out = withTimeout(15000)(FlowchartDiagram.render("flowchart TD\n    A[\"<b>x</b>\"]", htmlOn("loose")))
    assert(out.contains("<foreignObject"), s"loose+htmlLabels must emit foreignObject, got: ${out.take(800)}")
    assert(
      out.contains("<b>x</b>"),
      s"loose security must pass raw <b> through (common.ts:71 loose branch), got: ${out.take(900)}"
    )
  }

  test("security: sanitizeMore escapes < > = under a non-loose, non-strict level") {
    // common.ts:71-76 — any level other than loose/antiscript/strict escapes.
    assertEquals(
      TextUtils.sanitizeMore("<b>x</b>", "default", true),
      "&lt;b&gt;x&lt;/b&gt;"
    )
    // common.ts:74 — '=' becomes '&equals;'
    assertEquals(
      TextUtils.sanitizeMore("a=b", "default", true),
      "a&equals;b"
    )
    // common.ts:71-76 — <br> round-trips through #br# so it survives escaping.
    assertEquals(
      TextUtils.sanitizeMore("a<br>b", "default", true),
      "a<br/>b"
    )
  }

  test("security: strict strips <script> (removeScript / common.ts:69-70)") {
    assertEquals(
      TextUtils.sanitizeTextHtml("<script>alert(1)</script>hi", "strict", true),
      "hi"
    )
    // antiscript behaves identically (common.ts:69).
    assertEquals(
      TextUtils.sanitizeTextHtml("<script>alert(1)</script>hi", "antiscript", true),
      "hi"
    )
  }

  test("security: the gate is skipped entirely when htmlLabels is false (common.ts:67)") {
    assertEquals(
      TextUtils.sanitizeMore("<b>x</b>", "strict", false),
      "<b>x</b>"
    )
  }

  test("security: style tags are always forbidden (common.ts:89-91 FORBID_TAGS)") {
    assert(
      !TextUtils.sanitizeTextHtml("a<style>b{}</style>c", "loose", true).contains("<style"),
      "style tags must always be stripped"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 3b. Edge labels — under htmlLabels=true the edge label is emitted as a
  //     <foreignObject> with <span class="edgeLabel"> (isNode=false, createText.ts:28),
  //     inside the <g class="edgeLabel"> > <g class="label"> wrapper (edges.js:39-43 /
  //     classRenderer-v2.ts:263-266). OFF → SVG <text>, no foreignObject.
  // ──────────────────────────────────────────────────────────────────────────

  private val flowEdgeInput = "flowchart TD\n    A[Hi] -->|edgelbl| B[Bye]"

  test("flowchart edge label: htmlLabels=true emits foreignObject + span.edgeLabel") {
    val out = withTimeout(15000)(FlowchartDiagram.render(flowEdgeInput, htmlOn()))
    assert(out.contains("<foreignObject"), s"edge htmlLabels=true must emit foreignObject, got: ${out.take(1200)}")
    assert(
      out.contains("<span class=\"edgeLabel\""),
      s"edge label must be a span.edgeLabel (createText.ts:28, isNode=false), got: ${out.take(1200)}"
    )
    assert(out.contains("edgelbl"), s"edge label text must survive, got: ${out.take(1200)}")
    assert(
      out.contains("class=\"edgeLabel\""),
      s"the upstream <g class=\"edgeLabel\"> wrapper must be present (edges.js:39), got: ${out.take(1200)}"
    )
  }

  test("flowchart edge label: htmlLabels OFF emits SVG text, NO span.edgeLabel (non-regression)") {
    val out = withTimeout(15000)(FlowchartDiagram.render(flowEdgeInput, htmlOff))
    assert(!out.contains("<foreignObject"), s"edge htmlLabels off must NOT emit foreignObject, got: ${out.take(1200)}")
    assert(
      !out.contains("<span class=\"edgeLabel\""),
      s"edge htmlLabels off must NOT emit span.edgeLabel, got: ${out.take(1200)}"
    )
    assert(out.contains("edgelbl"), s"edge label text must survive on the SVG path, got: ${out.take(1200)}")
    assert(out.contains("class=\"edge-label-text\""), s"SVG-text edge label class must be kept, got: ${out.take(1200)}")
  }

  test("class edge label: htmlLabels=true emits foreignObject + span.edgeLabel (classRenderer-v2.ts:263-266)") {
    val out = withTimeout(15000)(ClassDiagram.render("classDiagram\n    Animal <|-- Dog : extends", htmlOn()))
    assert(out.contains("<span class=\"edgeLabel\""), s"class relation title must be a span.edgeLabel, got: ${out.take(1500)}")
    assert(out.contains("extends"), s"relation title text must survive, got: ${out.take(1500)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 3c. Class member/method rows — under htmlLabels each row is its own
  //     <foreignObject> carrying a <span class="nodeLabel"> (nodes.js:953-1015,
  //     createLabel isNode=true), with < > pre-escaped to &lt; &gt; (nodes.js:957/:991).
  //     OFF → SVG <text> rows, no foreignObject (non-regression).
  // ──────────────────────────────────────────────────────────────────────────

  private val classMembersInput =
    "classDiagram\n    class Animal {\n      +int age\n      +bark() void\n    }"

  test("class members: htmlLabels=true emits HTML member rows (span.nodeLabel in foreignObject)") {
    val out = withTimeout(15000)(ClassDiagram.render(classMembersInput, htmlOn()))
    assert(out.contains("<foreignObject"), s"class members htmlLabels=true must emit foreignObject, got: ${out.take(2000)}")
    assert(out.contains("class=\"nodeLabel\""), s"member rows must be span.nodeLabel, got: ${out.take(2000)}")
    // Discriminating: each row (title + member + method) is its OWN foreignObject — so a class with
    // members emits MORE than the single title foreignObject (nodes.js:953-1015 createLabel per row).
    // (If members stayed SVG <text>, only the title would be a foreignObject → count == 1.)
    val foCount = "<foreignObject".r.findAllMatchIn(out).size
    assert(foCount >= 3, s"expected >=3 foreignObjects (title + member + method rows), got $foCount in: ${out.take(2000)}")
    // The member/method text must NOT appear inside an SVG <text> element on the HTML path.
    assert(!out.contains("<text"), s"htmlLabels=true class body must have NO SVG <text> rows, got: ${out.take(2000)}")
    // member + method text survives (entity-decoded inside the span)
    assert(out.contains("age"), s"member text must survive, got: ${out.take(2000)}")
    assert(out.contains("bark"), s"method text must survive, got: ${out.take(2000)}")
  }

  test("class members: htmlLabels OFF emits SVG <text> member rows, NO foreignObject (non-regression)") {
    val out = withTimeout(15000)(ClassDiagram.render(classMembersInput, htmlOff))
    assert(!out.contains("<foreignObject"), s"class members htmlLabels off must NOT emit foreignObject, got: ${out.take(2000)}")
    assert(out.contains("<text"), s"class members htmlLabels off must keep SVG <text> rows, got: ${out.take(2000)}")
    assert(out.contains("age"), s"member text must survive on the SVG path, got: ${out.take(2000)}")
    assert(out.contains("bark"), s"method text must survive on the SVG path, got: ${out.take(2000)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 4. mindmap — upstream FORCES htmlLabels=false (mindmapRenderer.ts:171), so it
  //    stays SVG text even when htmlLabels=true is requested.
  // ──────────────────────────────────────────────────────────────────────────

  test("mindmap: stays SVG text (no foreignObject) even with htmlLabels=true (mindmapRenderer.ts:171)") {
    val out = withTimeout(15000)(MindmapDiagram.render("mindmap\n  root((Origin))\n    A\n    B", htmlOn()))
    assert(
      !out.contains("<foreignObject"),
      s"mindmap forces htmlLabels=false; must NOT emit foreignObject, got: ${out.take(800)}"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 4b. Non-regression: with htmlLabels OFF the default render is geometrically
  //     stable — no foreignObject anywhere across the label-bearing diagrams.
  // ──────────────────────────────────────────────────────────────────────────

  test("non-regression: htmlLabels OFF produces no foreignObject across diagrams") {
    val renders = List(
      FlowchartDiagram.render(flowInput, htmlOff),
      ClassDiagram.render("classDiagram\n    class Animal", htmlOff),
      StateDiagram.render("stateDiagram-v2\n    [*] --> Still", htmlOff),
      ErDiagram.render("erDiagram\n    CUSTOMER ||--o{ ORDER : places", htmlOff)
    )
    renders.foreach(out => assert(!out.contains("<foreignObject"), s"htmlLabels off must be foreignObject-free, got: ${out.take(400)}"))
  }
}
