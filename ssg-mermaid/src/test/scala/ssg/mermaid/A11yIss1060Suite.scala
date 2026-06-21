/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression suite for ISS-1060: accessibility (a11y) wiring into every diagram renderer.
 *
 * Upstream reference: mermaid/packages/mermaid/src/accessibility.ts +
 * mermaid/packages/mermaid/src/mermaidAPI.ts:437-439, :521-529 (addA11yInfo).
 *
 *   - setA11yDiagramInfo(svg, diagramType) ALWAYS sets:
 *       role="graphics-document document"  (SVG_ROLE, accessibility.ts:19)
 *       aria-roledescription="<diagramType>" (only when diagramType != "")
 *   - addSVGa11yTitleDescription(svg, title, desc, baseId) is presence-gated:
 *       when desc present: aria-describedby="chart-desc-<baseId>" + <desc id="chart-desc-<baseId>">desc</desc>
 *       when title present: aria-labelledby="chart-title-<baseId>" + <title id="chart-title-<baseId>">title</title>
 *
 * The baseId is the svg element id (mermaidAPI.ts:528 svgNode.attr('id')); SSG renderers carry a
 * deterministic id of "mermaid-<diagramType>" assigned by Accessibility.applyTo when none is set,
 * so the a11y child ids are well-formed and deterministic.
 */
package ssg
package mermaid

import munit.FunSuite

final class A11yIss1060Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // Diagram inputs WITH accTitle/accDescr and WITHOUT (presence-gating)
  // The a11y syntax is `accTitle: <text>` / `accDescr: <text>` (per each diagram's parser).
  // ──────────────────────────────────────────────────────────────────────────

  private val accTitleText = "My Accessible Title"
  private val accDescrText = "My accessible description text"

  /** Each case: human name, the diagramType id mermaid emits as aria-roledescription, the diagram body WITHOUT a11y lines, and the same body WITH the two a11y lines spliced in.
    */
  final private case class Case(
    name:        String,
    diagramType: String,
    without:     String,
    `with`:      String
  )

  // Helper to splice accTitle/accDescr after the first (header) line.
  private def withA11y(header: String, rest: String): String =
    s"$header\n    accTitle: $accTitleText\n    accDescr: $accDescrText\n$rest"

  private val cases: List[Case] = List(
    Case(
      "block",
      "block",
      "block-beta\n    columns 3\n    A B C",
      withA11y("block-beta", "    columns 3\n    A B C")
    ),
    Case(
      "gantt",
      "gantt",
      "gantt\n    section S\n    Task :2024-01-01, 3d",
      withA11y("gantt", "    section S\n    Task :2024-01-01, 3d")
    ),
    Case(
      "flowchart",
      "flowchart-v2",
      "flowchart TD\n    A-->B",
      withA11y("flowchart TD", "    A-->B")
    ),
    Case(
      "sequence",
      "sequence",
      "sequenceDiagram\n    Alice->>Bob: Hello",
      withA11y("sequenceDiagram", "    Alice->>Bob: Hello")
    ),
    Case(
      "class",
      "classDiagram",
      "classDiagram\n    class Animal",
      withA11y("classDiagram", "    class Animal")
    ),
    Case(
      "state",
      "stateDiagram",
      "stateDiagram-v2\n    [*] --> Still",
      withA11y("stateDiagram-v2", "    [*] --> Still")
    ),
    Case(
      "pie",
      "pie",
      "pie\n    \"Dogs\" : 386\n    \"Cats\" : 85",
      withA11y("pie", "    \"Dogs\" : 386\n    \"Cats\" : 85")
    ),
    Case(
      "er",
      "er",
      "erDiagram\n    CUSTOMER ||--o{ ORDER : places",
      withA11y("erDiagram", "    CUSTOMER ||--o{ ORDER : places")
    )
  )

  // ──────────────────────────────────────────────────────────────────────────
  // setA11yDiagramInfo: role + aria-roledescription on EVERY diagram (no a11y text needed)
  // ──────────────────────────────────────────────────────────────────────────

  cases.foreach { c =>
    test(s"${c.name}: svg always carries role + aria-roledescription (no accTitle)") {
      val svg = Mermaid.render(c.without)
      assert(svg.contains("<svg"), s"expected an svg for ${c.name}, got: ${svg.take(120)}")
      // accessibility.ts:19,28 — role is ALWAYS set to SVG_ROLE.
      assert(
        svg.contains("role=\"graphics-document document\""),
        s"${c.name} svg should set role=\"graphics-document document\", got: ${svg.take(400)}"
      )
      // accessibility.ts:29-31 — aria-roledescription is the diagram type id.
      assert(
        svg.contains(s"aria-roledescription=\"${c.diagramType}\""),
        s"${c.name} svg should set aria-roledescription=\"${c.diagramType}\", got: ${svg.take(400)}"
      )
    }

    test(s"${c.name}: presence-gating — no title/desc/labelledby when accTitle/accDescr absent") {
      val svg = Mermaid.render(c.without)
      // addSVGa11yTitleDescription only inserts <title>/<desc> when the strings are present
      // (accessibility.ts:55-64). With no acc lines, none of these a11y child elements appear.
      assert(
        !svg.contains("chart-title-"),
        s"${c.name} without accTitle must not emit chart-title-* id, got: ${svg.take(400)}"
      )
      assert(
        !svg.contains("chart-desc-"),
        s"${c.name} without accDescr must not emit chart-desc-* id, got: ${svg.take(400)}"
      )
      assert(
        !svg.contains("aria-labelledby="),
        s"${c.name} without accTitle must not set aria-labelledby, got: ${svg.take(400)}"
      )
      assert(
        !svg.contains("aria-describedby="),
        s"${c.name} without accDescr must not set aria-describedby, got: ${svg.take(400)}"
      )
    }

    test(s"${c.name}: emits a11y title + desc + labelledby/describedby when present") {
      val svg     = Mermaid.render(c.`with`)
      val titleId = s"chart-title-mermaid-${c.diagramType}"
      val descId  = s"chart-desc-mermaid-${c.diagramType}"
      assert(svg.contains("<svg"), s"expected an svg for ${c.name}, got: ${svg.take(120)}")

      // role + aria-roledescription still present (setA11yDiagramInfo runs unconditionally).
      assert(
        svg.contains("role=\"graphics-document document\""),
        s"${c.name} (with a11y) should keep role=\"graphics-document document\""
      )
      assert(
        svg.contains(s"aria-roledescription=\"${c.diagramType}\""),
        s"${c.name} (with a11y) should keep aria-roledescription=\"${c.diagramType}\""
      )

      // accessibility.ts:60-63 — <title id="chart-title-<id>"> + aria-labelledby.
      assert(
        svg.contains(s"aria-labelledby=\"$titleId\""),
        s"${c.name} should set aria-labelledby=\"$titleId\", got: ${svg.take(600)}"
      )
      assert(
        svg.contains(s"id=\"$titleId\""),
        s"${c.name} should emit <title id=\"$titleId\">, got: ${svg.take(600)}"
      )
      assert(
        svg.contains("<title"),
        s"${c.name} should emit a <title> element, got: ${svg.take(600)}"
      )
      assert(
        svg.contains(accTitleText),
        s"${c.name} should emit the accessible title text, got: ${svg.take(600)}"
      )

      // accessibility.ts:55-58 — <desc id="chart-desc-<id>"> + aria-describedby.
      assert(
        svg.contains(s"aria-describedby=\"$descId\""),
        s"${c.name} should set aria-describedby=\"$descId\", got: ${svg.take(600)}"
      )
      assert(
        svg.contains(s"id=\"$descId\""),
        s"${c.name} should emit <desc id=\"$descId\">, got: ${svg.take(600)}"
      )
      assert(
        svg.contains("<desc"),
        s"${c.name} should emit a <desc> element, got: ${svg.take(600)}"
      )
      assert(
        svg.contains(accDescrText),
        s"${c.name} should emit the accessible description text, got: ${svg.take(600)}"
      )
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Direct unit checks on the Accessibility helpers against accessibility.ts.
  // ──────────────────────────────────────────────────────────────────────────

  test("setA11yDiagramInfo: empty diagramType omits aria-roledescription (accessibility.ts:29)") {
    val svg = ssg.graphs.commons.svg.SvgBuilder.create("svg")
    Accessibility.setA11yDiagramInfo(svg, "")
    val markup = svg.build().toMarkup()
    assert(
      markup.contains("role=\"graphics-document document\""),
      s"role must still be set, got: $markup"
    )
    assert(
      !markup.contains("aria-roledescription"),
      s"empty diagramType must omit aria-roledescription, got: $markup"
    )
  }

  test("addSVGa11yTitleDescription: desc inserted before title (accessibility.ts:55-64 insert order)") {
    val svg = ssg.graphs.commons.svg.SvgBuilder.create("svg")
    Accessibility.addSVGa11yTitleDescription(svg, "T", "D", "x")
    val markup = svg.build().toMarkup()
    // Both insert(":first-child"); desc is inserted first then title, so the <title> ELEMENT ends
    // up before the <desc> ELEMENT (matching D3/mermaid). Check element positions, not attribute
    // occurrences (the svg attr line lists aria-describedby before aria-labelledby).
    val titleElemIdx = markup.indexOf("<title")
    val descElemIdx  = markup.indexOf("<desc")
    assert(titleElemIdx >= 0 && descElemIdx >= 0, s"both elements must be present, got: $markup")
    assert(
      titleElemIdx < descElemIdx,
      s"<title> (last :first-child insert) must precede <desc>, got: $markup"
    )
  }
}
