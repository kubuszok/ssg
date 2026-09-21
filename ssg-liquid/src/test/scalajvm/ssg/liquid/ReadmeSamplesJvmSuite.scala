/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** The one test of liqp's ReadmeSamplesTest.java that renders an arbitrary object in EAGER mode.
  *
  * JVM-only: EAGER mode reads an object's properties by reflection, which Scala.js and Scala Native do not have. The other tests are in the shared ReadmeSamplesSuite.
  */
final class ReadmeSamplesJvmSuite extends munit.FunSuite {

  // SSG: EAGER mode object access differs
  test("readme: eager mode".fail) { // ISS-1267 (ISS-1024 umbrella)
    val data   = TestHelper.mapOf("a" -> new ReadmeSamplesJvmSuite.ValHolder())
    val parser = new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.EAGER).build()
    val res    = parser.parse("hi {{a.val}}").render(data)
    assertEquals(res, "hi tobi")
  }
}

object ReadmeSamplesJvmSuite {

  class ValHolder {
    @SuppressWarnings(Array("unused"))
    val `val`: String = "tobi"
  }
}
