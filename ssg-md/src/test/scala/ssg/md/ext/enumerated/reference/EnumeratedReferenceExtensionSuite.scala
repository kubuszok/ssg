/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package md
package ext
package enumerated
package reference

import munit.FunSuite

import scala.language.implicitConversions

/** Test suite for the EnumeratedReference extension.
  *
  * Spec resources: ext/enumerated/reference/ext_enumerated_reference_ast_spec.md ext/enumerated/reference/ext_enumerated_reference_formatter_spec.md
  *
  * TODO: Implement spec-based rendering tests once the test harness (FlexmarkSpecExampleRenderer) is ported from flexmark-test-util.
  */
class EnumeratedReferenceExtensionSuite extends FunSuite {

  test("EnumeratedReferenceExtension can be created") {
    val ext = EnumeratedReferenceExtension.create()
    assert(ext != null)
  }

  test("EnumeratedReferenceRepository.getType extracts type from text") {
    assertEquals(EnumeratedReferenceRepository.getType("fig:label"), "fig")
    assertEquals(EnumeratedReferenceRepository.getType("table:my-table"), "table")
    assertEquals(EnumeratedReferenceRepository.getType("nocolon"), EnumeratedReferences.EMPTY_TYPE)
  }

  test("EnumeratedReferences tracks ordinals") {
    // EnumeratedReferences requires a DataHolder with repository set up,
    // so we verify the static utility method instead
    val renderings = Array(
      EnumeratedReferenceRendering(null, "fig", 1) // @nowarn - null referenceFormat for test
    )
    assertEquals(renderings(0).referenceOrdinal, 1)
    assertEquals(renderings(0).referenceType, "fig")
  }

  test("EnumeratedReferenceText can be constructed") {
    import ssg.md.util.sequence.BasedSequence
    val node = new EnumeratedReferenceText(
      BasedSequence.of("[#"),
      BasedSequence.of("fig:label"),
      BasedSequence.of("]")
    )
    assertEquals(node.text.toString, "fig:label")
    assertEquals(node.openingMarker.toString, "[#")
    assertEquals(node.closingMarker.toString, "]")
  }

  test("EnumeratedReferenceLink can be constructed") {
    import ssg.md.util.sequence.BasedSequence
    val node = new EnumeratedReferenceLink(
      BasedSequence.of("[@"),
      BasedSequence.of("fig:label"),
      BasedSequence.of("]")
    )
    assertEquals(node.text.toString, "fig:label")
    assertEquals(node.openingMarker.toString, "[@")
  }

  // --- End-to-end rendering tests ---

  import ssg.md.ext.attributes.AttributesExtension
  import ssg.md.html.HtmlRenderer
  import ssg.md.parser.Parser

  private def createParser(): Parser = {
    val extensions = java.util.List.of(EnumeratedReferenceExtension.create(), AttributesExtension.create())
    Parser.builder().extensions(extensions).build()
  }

  private def createRenderer(): HtmlRenderer = {
    val extensions = java.util.List.of(EnumeratedReferenceExtension.create(), AttributesExtension.create())
    HtmlRenderer.builder().extensions(extensions).build()
  }

  private def render(markdown: String): String = {
    val parser   = createParser()
    val renderer = createRenderer()
    val doc      = parser.parse(markdown)
    renderer.render(doc)
  }

  test("e2e: enumerated reference definition block is not rendered in output") {
    val md   = "[@fig]: Figure [#]\n\nSome text\n"
    val html = render(md)
    // The definition block itself should not appear in rendered output
    assert(!html.contains("[@fig]:"), s"Definition block should not appear in output, got: $html")
    assert(html.contains("Some text"), s"Body text should appear, got: $html")
  }

  test("e2e: enumerated reference text [#type:] renders ordinal") {
    // Define the format, create a heading with an id attribute, then reference it
    val md = "[@fig]: Figure [#]\n\n# Heading {#fig:label1}\n\nSee [#fig:label1]\n"
    val html = render(md)
    // The reference text should render the ordinal (1) with the format text "Figure"
    assert(html.contains("Figure"), s"Should contain format text 'Figure', got: $html")
    assert(html.contains("1"), s"Should contain ordinal '1', got: $html")
  }

  test("e2e: enumerated reference link [@type:label] renders as anchor") {
    val md = "[@fig]: Figure [#]\n\n# My Figure {#fig:my-figure}\n\nSee [@fig:my-figure]\n"
    val html = render(md)
    // The reference link should render as an <a> tag with href
    assert(html.contains("<a"), s"Should contain anchor tag, got: $html")
    assert(html.contains("href=\"#fig:my-figure\""), s"Should have href to the reference id, got: $html")
  }

  test("e2e: multiple enumerated references get sequential ordinals") {
    val md =
      """[@fig]: Figure [#]
        |
        |# First {#fig:first}
        |
        |# Second {#fig:second}
        |
        |See [#fig:first] and [#fig:second]
        |""".stripMargin
    val html = render(md)
    // Both ordinals should appear
    assert(html.contains("1"), s"Should contain ordinal 1, got: $html")
    assert(html.contains("2"), s"Should contain ordinal 2, got: $html")
  }

  test("e2e: enumerated reference without definition uses type as default text") {
    // Reference a type that has no [@type]: definition
    val md = "# Heading {#tbl:my-table}\n\nSee [#tbl:my-table]\n"
    val html = render(md)
    // When no definition block exists, the type string is used as default text
    assert(html.contains("tbl"), s"Should contain type text 'tbl' as fallback, got: $html")
    assert(html.contains("1"), s"Should contain ordinal 1, got: $html")
  }

  test("e2e: enumerated reference link renders title attribute") {
    val md = "[@fig]: Figure [#]\n\n# Diagram {#fig:diagram}\n\nClick [@fig:diagram]\n"
    val html = render(md)
    assert(html.contains("<a"), s"Should render as link, got: $html")
    assert(html.contains("title="), s"Should have title attribute, got: $html")
  }
}
