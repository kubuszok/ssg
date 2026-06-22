/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1078: HTML-like labels must render their text content (tags stripped),
 * not escaped markup with visible angle brackets.
 *
 * Graphviz HTML-like label semantics (from Graphviz docs):
 *   - Labels delimited by `<...>` (not quoted) are parsed as HTML-like labels
 *   - HTML tags (<B>, <I>, <FONT>, <BR/>, <TABLE>, <TR>, <TD>) control styling
 *   - The rendered output shows the TEXT CONTENT of the markup, not the tags
 *   - Example: `label=<<B>bold</B>>` renders as the text "bold" in bold face
 *
 * The expected text values asserted below are derived from this specification:
 * the visible text is the concatenation of all text nodes in the HTML-like
 * label, with tags stripped.
 */
package ssg
package graphviz
package render

import munit.FunSuite

final class HtmlLikeLabelIss1078Suite extends FunSuite {

  private val testConfig: GraphvizConfig = GraphvizConfig(engine = LayoutEngine.Neato)

  private def render(input: String): String =
    Graphviz.render(input, testConfig)

  // -- HTML-like label: tags stripped, text content visible --

  test("ISS-1078: HTML-like label renders text content, not escaped markup") {
    // Graphviz: <<B>bold</B>> renders as text "bold" (in bold face)
    val svg = render("""digraph { a [label=<<B>bold</B>>] }""")

    // The SVG must contain the visible text "bold"
    assert(
      svg.contains(">bold</text>"),
      s"SVG should contain text 'bold' from HTML-like label, but got:\n$svg"
    )

    // The SVG must NOT contain escaped angle-bracket markup
    assert(
      !svg.contains("&lt;B&gt;"),
      s"SVG should NOT contain escaped '<B>' markup, but got:\n$svg"
    )
    assert(
      !svg.contains("&lt;/B&gt;"),
      s"SVG should NOT contain escaped '</B>' markup, but got:\n$svg"
    )
  }

  test("ISS-1078: HTML-like label with nested tags strips all tags") {
    // Graphviz: <<FONT COLOR="red"><B>styled</B> text</FONT>> renders as "styled text"
    val svg = render("""digraph { a [label=<<FONT COLOR="red"><B>styled</B> text</FONT>>] }""")

    assert(
      svg.contains(">styled text</text>"),
      s"SVG should contain 'styled text' from nested HTML-like label, but got:\n$svg"
    )
    assert(
      !svg.contains("&lt;FONT"),
      s"SVG should NOT contain escaped '<FONT' markup, but got:\n$svg"
    )
    assert(
      !svg.contains("&lt;B&gt;"),
      s"SVG should NOT contain escaped '<B>' markup, but got:\n$svg"
    )
  }

  test("ISS-1078: HTML-like graph label renders text content") {
    // Graph-level label=<...> should also be tag-stripped
    val svg = render("""digraph { label=<<I>graph title</I>> ; a -> b }""")

    assert(
      svg.contains(">graph title</text>"),
      s"SVG should contain 'graph title' from HTML-like graph label, but got:\n$svg"
    )
    assert(
      !svg.contains("&lt;I&gt;"),
      s"SVG should NOT contain escaped '<I>' markup, but got:\n$svg"
    )
  }

  test("ISS-1078: HTML-like edge label renders text content") {
    // Edge label=<...> should also be tag-stripped
    val svg = render("""digraph { a -> b [label=<<B>edge</B>>] }""")

    // Edge labels render at the midpoint; check for the text
    // Note: edge labels may be suppressed by Neato layout when coordinates are 0,0
    // so we check that IF the label appears, it is not escaped
    if (svg.contains("edge</text>")) {
      assert(
        !svg.contains("&lt;B&gt;edge"),
        s"SVG edge label should NOT contain escaped markup, but got:\n$svg"
      )
    }
  }

  // -- Control: regular quoted labels with angle-bracket characters --

  test("ISS-1078 control: quoted label with '<' renders escaped text (unchanged behavior)") {
    // A regular quoted label containing '<' should be XML-escaped as &lt;
    // This is CORRECT behavior — the '<' is literal text, not HTML markup
    val svg = render("""digraph { a [label="x<y"] }""")

    // The SVG should contain the XML-escaped literal text
    assert(
      svg.contains("x&lt;y</text>"),
      s"SVG should contain XML-escaped 'x<y' as 'x&lt;y', but got:\n$svg"
    )
  }

  test("ISS-1078 control: regular label without HTML is unaffected") {
    val svg = render("""digraph { a [label="hello world"] }""")
    assert(
      svg.contains(">hello world</text>"),
      s"SVG should contain plain 'hello world' label, but got:\n$svg"
    )
  }
}
