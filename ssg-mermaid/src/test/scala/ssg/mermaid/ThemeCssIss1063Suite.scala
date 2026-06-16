/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1063: themeCSS must be honored by ALL diagram renderers, not just flowchart.
 * Upstream: mermaidAPI.ts:119-121 — createCssStyles applies config.themeCSS to every
 * diagram type centrally; SSG does it per-renderer, so each renderer must append it.
 */
package ssg
package mermaid

import munit.FunSuite
import ssg.mermaid.diagrams.pie.PieDiagram
import ssg.mermaid.diagrams.sequence.SequenceDiagram
import ssg.mermaid.diagrams.gantt.GanttDiagram
import ssg.mermaid.diagrams.state.StateDiagram

final class ThemeCssIss1063Suite extends FunSuite {

  private val customCss = ".iss1063-marker{fill:red}"

  private val configWithThemeCss = MermaidConfig(themeCSS = customCss)
  private val configWithoutThemeCss = MermaidConfig()

  // ── Pie ─────────────────────────────────────────────────────────────────────

  test("pie: themeCSS present in rendered SVG when configured") {
    val svg = PieDiagram.render("pie\n    \"A\" : 60\n    \"B\" : 40", configWithThemeCss)
    assert(
      svg.contains(customCss),
      s"Pie SVG should contain themeCSS '$customCss' (mermaidAPI.ts:119-121)"
    )
  }

  test("pie: themeCSS absent when not configured") {
    val svg = PieDiagram.render("pie\n    \"A\" : 60\n    \"B\" : 40", configWithoutThemeCss)
    assert(
      !svg.contains(customCss),
      "Pie SVG should NOT contain themeCSS when not configured"
    )
  }

  // ── Sequence ────────────────────────────────────────────────────────────────

  test("sequence: themeCSS present in rendered SVG when configured") {
    val svg = SequenceDiagram.render(
      "sequenceDiagram\n    Alice->>Bob: Hello",
      configWithThemeCss
    )
    assert(
      svg.contains(customCss),
      s"Sequence SVG should contain themeCSS '$customCss' (mermaidAPI.ts:119-121)"
    )
  }

  test("sequence: themeCSS absent when not configured") {
    val svg = SequenceDiagram.render(
      "sequenceDiagram\n    Alice->>Bob: Hello",
      configWithoutThemeCss
    )
    assert(
      !svg.contains(customCss),
      "Sequence SVG should NOT contain themeCSS when not configured"
    )
  }

  // ── Gantt ───────────────────────────────────────────────────────────────────

  test("gantt: themeCSS present in rendered SVG when configured") {
    val svg = GanttDiagram.render(
      "gantt\n    title Test\n    section A\n    Task1 :a1, 2024-01-01, 30d",
      configWithThemeCss
    )
    assert(
      svg.contains(customCss),
      s"Gantt SVG should contain themeCSS '$customCss' (mermaidAPI.ts:119-121)"
    )
  }

  test("gantt: themeCSS absent when not configured") {
    val svg = GanttDiagram.render(
      "gantt\n    title Test\n    section A\n    Task1 :a1, 2024-01-01, 30d",
      configWithoutThemeCss
    )
    assert(
      !svg.contains(customCss),
      "Gantt SVG should NOT contain themeCSS when not configured"
    )
  }

  // ── State ───────────────────────────────────────────────────────────────────

  test("state: themeCSS present in rendered SVG when configured") {
    val svg = StateDiagram.render(
      "stateDiagram-v2\n    [*] --> Active\n    Active --> [*]",
      configWithThemeCss
    )
    assert(
      svg.contains(customCss),
      s"State SVG should contain themeCSS '$customCss' (mermaidAPI.ts:119-121)"
    )
  }

  test("state: themeCSS absent when not configured") {
    val svg = StateDiagram.render(
      "stateDiagram-v2\n    [*] --> Active\n    Active --> [*]",
      configWithoutThemeCss
    )
    assert(
      !svg.contains(customCss),
      "State SVG should NOT contain themeCSS when not configured"
    )
  }
}
