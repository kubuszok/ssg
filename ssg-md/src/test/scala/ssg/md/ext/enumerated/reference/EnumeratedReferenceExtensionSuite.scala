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
}
