/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package sequence

import munit.FunSuite
import ssg.mermaid.render.text.TextMetrics

/** Tests for ISS-1202: sequence message wrap (wrapLabel + breakString) rendering.
  *
  * Verifies that when wrap is enabled (config.wrap=true), long message labels are line-broken in the SVG output using tspan elements, and that the wrap-off path remains unchanged (single text
  * element).
  */
final class SequenceWrapIss1202Suite extends FunSuite {

  // A long message that exceeds the default conf.width (150) when measured
  private val LongMessage: String =
    "This is a very long message that should be wrapped when wrapping is enabled in the configuration"

  // A short message that fits on one line
  private val ShortMessage: String = "Hello"

  // --- Unit tests for wrapLabel / breakString ---

  test("wrapLabel: long multi-word label is line-broken") {
    val maxWidth = 100.0
    val result   = SequenceRenderer.wrapLabel(LongMessage, maxWidth, 12, "sans-serif", "normal")
    assert(result.contains("\n"), s"Expected line break in wrapped result but got: $result")
    // Verify no emitted line exceeds maxWidth when measured
    val lines = result.split("\n")
    for (line <- lines) {
      val width = TextMetrics.measureText(line, 12, "sans-serif").width
      // Allow a small tolerance for the final word on a line (greedy wrap may slightly exceed
      // when a word is placed because currentLine + word < maxWidth but the word itself pushes it)
      assert(
        width <= maxWidth + 20,
        s"Line '$line' measured $width which exceeds maxWidth $maxWidth by more than tolerance"
      )
    }
  }

  test("wrapLabel: empty label returns empty") {
    val result = SequenceRenderer.wrapLabel("", 100.0, 12, "sans-serif", "normal")
    assertEquals(result, "")
  }

  test("wrapLabel: label with existing line break is returned unchanged") {
    val labelWithBreak = "Line one\nLine two"
    val result         = SequenceRenderer.wrapLabel(labelWithBreak, 100.0, 12, "sans-serif", "normal")
    assertEquals(result, labelWithBreak)
  }

  test("wrapLabel: short label that fits is returned as single line") {
    val result = SequenceRenderer.wrapLabel(ShortMessage, 200.0, 12, "sans-serif", "normal")
    assert(!result.contains("\n"), s"Short label should not be wrapped but got: $result")
    assertEquals(result, ShortMessage)
  }

  test("breakString: single over-long word is hyphen-broken") {
    val longWord = "Supercalifragilisticexpialidocious"
    val result   = SequenceRenderer.breakString(longWord, 60.0, "-", 12, "sans-serif", "normal")
    assert(
      result.hyphenatedStrings.exists(_.endsWith("-")),
      s"Expected hyphenated lines but got: ${result.hyphenatedStrings.mkString(", ")}"
    )
  }

  test("wrapLabel: over-long single word gets hyphen-broken into result") {
    val longWord = "Supercalifragilisticexpialidocious"
    val result   = SequenceRenderer.wrapLabel(longWord, 60.0, 12, "sans-serif", "normal")
    assert(result.contains("-"), s"Expected hyphen in broken word but got: $result")
  }

  // --- Structural SVG differential tests ---

  test("render wrap OFF: long message renders as single messageText element") {
    val diagram =
      s"""sequenceDiagram
         |    Alice->>Bob: $LongMessage""".stripMargin
    val config = MermaidConfig(wrap = false)
    val svg    = SequenceDiagram.render(diagram, config)

    // With wrap OFF the message should appear as a single text element with class="messageText"
    assert(svg.contains("""class="messageText""""), "SVG should contain messageText class attribute")
    // Count occurrences of class="messageText" — should be exactly 1 (the CSS .messageText also
    // contains the string but not as a class attribute)
    val messageTextAttrCount = """class="messageText"""".r.findAllIn(svg).length
    assertEquals(
      messageTextAttrCount,
      1,
      s"With wrap OFF, there should be exactly 1 messageText element"
    )
    // No tspan elements inside the message text element
    assert(!messageTextHasTspan(svg), "Wrap OFF should not produce tspan in message text")
  }

  test("render wrap ON: long message renders as multiple tspan elements") {
    val diagram =
      s"""sequenceDiagram
         |    Alice->>Bob: $LongMessage""".stripMargin
    val config = MermaidConfig(wrap = true)
    val svg    = SequenceDiagram.render(diagram, config)

    assert(svg.contains("messageText"), "SVG should contain messageText class")
    // With wrap ON the long message should have multiple tspan elements inside the messageText
    val tspanPattern = """<tspan[^>]*>""".r
    // Find all tspan elements that are part of message text (between messageText text element)
    val messageTextSection = extractMessageTextSection(svg)
    val tspanCount         = tspanPattern.findAllIn(messageTextSection).length
    assert(
      tspanCount >= 2,
      s"With wrap ON, long message should have >=2 tspan lines but found $tspanCount in: $messageTextSection"
    )
  }

  test("render wrap ON: short message that fits renders as single line") {
    val diagram =
      s"""sequenceDiagram
         |    Alice->>Bob: $ShortMessage""".stripMargin
    val config = MermaidConfig(wrap = true)
    val svg    = SequenceDiagram.render(diagram, config)

    assert(svg.contains(ShortMessage), "SVG should contain the short message")
  }

  test("render wrap OFF: output is byte-identical to no-config render for non-wrapped message") {
    val diagram =
      s"""sequenceDiagram
         |    Alice->>Bob: $ShortMessage""".stripMargin
    val svgDefault  = SequenceDiagram.render(diagram)
    val svgExplicit = SequenceDiagram.render(diagram, MermaidConfig(wrap = false))
    assertEquals(svgDefault, svgExplicit, "Wrap OFF should produce identical output to default config")
  }

  test("render wrap ON vs OFF: same long message produces structurally different output") {
    val diagram =
      s"""sequenceDiagram
         |    Alice->>Bob: $LongMessage""".stripMargin
    val svgOff = SequenceDiagram.render(diagram, MermaidConfig(wrap = false))
    val svgOn  = SequenceDiagram.render(diagram, MermaidConfig(wrap = true))
    assertNotEquals(svgOff, svgOn, "Wrap ON and OFF should produce different SVG for a long message")
    // Wrap ON should have tspan elements that wrap OFF does not
    val tspanInOn  = "<tspan".r.findAllIn(svgOn).length
    val tspanInOff = "<tspan".r.findAllIn(svgOff).length
    assert(
      tspanInOn > tspanInOff,
      s"Wrap ON should have more tspan elements ($tspanInOn) than wrap OFF ($tspanInOff)"
    )
  }

  // --- Helpers ---

  /** Checks if any tspan inside a messageText text element exists. */
  private def messageTextHasTspan(svg: String): Boolean = {
    val section = extractMessageTextSection(svg)
    section.contains("<tspan")
  }

  /** Extracts the section of SVG containing the messageText text element. */
  private def extractMessageTextSection(svg: String): String = {
    // Search for class="messageText" attribute (not CSS .messageText)
    val classAttr = """class="messageText""""
    val idx       = svg.indexOf(classAttr)
    if (idx < 0) ""
    else {
      // Find the enclosing <text...>...</text> around the messageText class
      val textStart = svg.lastIndexOf("<text", idx)
      val textEnd   = svg.indexOf("</text>", idx)
      if (textStart >= 0 && textEnd >= 0) svg.substring(textStart, textEnd + "</text>".length)
      else ""
    }
  }
}
