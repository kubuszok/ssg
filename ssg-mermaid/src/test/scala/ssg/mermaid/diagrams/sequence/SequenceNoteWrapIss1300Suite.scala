/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package sequence

import munit.FunSuite

/** Tests for ISS-1300: sequence NOTE wrap (wrapLabel applied to note labels) rendering.
  *
  * Verifies that when wrap is enabled (config.wrap=true), a long `note` label is line-broken in the SVG output using multiple `<tspan>` elements inside the noteText element, and that the wrap-off
  * path remains a single-line note (no tspan). Mirrors the message-wrap sibling suite SequenceWrapIss1202Suite.
  *
  * Upstream: buildNoteModel wraps the note message with `wrapLabel(msg.message, ...)` and drawNote renders the (possibly `\n`-containing) message multi-line (sequenceRenderer.ts:1361-1431, 240-281).
  */
final class SequenceNoteWrapIss1300Suite extends FunSuite {

  // A long note that exceeds the default conf.width (150) when measured, so it wraps.
  private val LongNote: String =
    "This is a very long note that should be wrapped when wrapping is enabled in the configuration"

  // A short note that fits on one line.
  private val ShortNote: String = "Hello"

  /** Extracts the section of SVG containing the noteText text element. */
  private def extractNoteTextSection(svg: String): String = {
    val classAttr = """class="noteText""""
    val idx       = svg.indexOf(classAttr)
    if (idx < 0) ""
    else {
      val textStart = svg.lastIndexOf("<text", idx)
      val textEnd   = svg.indexOf("</text>", idx)
      if (textStart >= 0 && textEnd >= 0) svg.substring(textStart, textEnd + "</text>".length)
      else ""
    }
  }

  test("render note wrap OFF: long note renders as single noteText element (no tspan)") {
    val diagram =
      s"""sequenceDiagram
         |    participant Alice
         |    Note over Alice: $LongNote""".stripMargin
    val config = MermaidConfig(wrap = false)
    val svg    = SequenceDiagram.render(diagram, config)

    assert(svg.contains("""class="noteText""""), "SVG should contain noteText class attribute")
    val section = extractNoteTextSection(svg)
    assert(section.nonEmpty, "Should locate the noteText element")
    assert(!section.contains("<tspan"), s"Wrap OFF should not produce tspan in note text: $section")
  }

  test("render note wrap ON: long note renders as multiple tspan elements") {
    val diagram =
      s"""sequenceDiagram
         |    participant Alice
         |    Note over Alice: $LongNote""".stripMargin
    val config = MermaidConfig(wrap = true)
    val svg    = SequenceDiagram.render(diagram, config)

    assert(svg.contains("""class="noteText""""), "SVG should contain noteText class attribute")
    val section    = extractNoteTextSection(svg)
    val tspanCount = "<tspan".r.findAllIn(section).length
    assert(
      tspanCount >= 2,
      s"With wrap ON, long note should have >=2 tspan lines but found $tspanCount in: $section"
    )
  }

  test("render note wrap ON: short note that fits renders as single line (no tspan)") {
    val diagram =
      s"""sequenceDiagram
         |    participant Alice
         |    Note over Alice: $ShortNote""".stripMargin
    val config = MermaidConfig(wrap = true)
    val svg    = SequenceDiagram.render(diagram, config)

    assert(svg.contains(ShortNote), "SVG should contain the short note text")
    val section = extractNoteTextSection(svg)
    assert(!section.contains("<tspan"), s"Short note should render single-line (no tspan): $section")
  }

  test("render note wrap ON vs OFF: same long note produces structurally different output") {
    val diagram =
      s"""sequenceDiagram
         |    participant Alice
         |    Note over Alice: $LongNote""".stripMargin
    val svgOff = SequenceDiagram.render(diagram, MermaidConfig(wrap = false))
    val svgOn  = SequenceDiagram.render(diagram, MermaidConfig(wrap = true))
    assertNotEquals(svgOff, svgOn, "Wrap ON and OFF should produce different SVG for a long note")
    val tspanInOn  = "<tspan".r.findAllIn(extractNoteTextSection(svgOn)).length
    val tspanInOff = "<tspan".r.findAllIn(extractNoteTextSection(svgOff)).length
    assert(
      tspanInOn > tspanInOff,
      s"Wrap ON should have more note tspan elements ($tspanInOn) than wrap OFF ($tspanInOff)"
    )
  }
}
