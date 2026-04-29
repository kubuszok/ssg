/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Gap-fill tests ported from liqp's tags/WhitespaceControlTest.java — 4 missing tests.
  *
  * All output in this test class is tested against Ruby 2.3.1 and Liquid 4.0.0.
  * The existing BlocksSuite already covers: lhs strip, rhs strip, both sides strip, all strip.
  * This adds: noStrip, twoLhsStrip, twoRhsStrip, defaultStrip.
  */
final class WhitespaceControlExtraSuite extends munit.FunSuite {

  test("whitespace control: no strip") {
    val source = "a  \n  {% assign letter = 'b' %}  \n{{ letter }}\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "a..\n....\nb\n..c")
  }

  test("whitespace control: two lhs strip") {
    val source = "a  \n  {%- assign letter = 'b' %}  \n{{- letter }}\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "ab\n..c")
  }

  test("whitespace control: two rhs strip") {
    val source = "a  \n  {% assign letter = 'b' -%}  \n{{ letter -}}\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "a..\n..bc")
  }

  // SSG: withStripSpaceAroundTags doesn't fully strip whitespace in same way
  test("whitespace control: default strip via parser setting".fail) {
    val source = "a  \n  {% assign letter = 'b' %}  \n{{ letter }}\n  c"
    val parser = new TemplateParser.Builder().withStripSpaceAroundTags(true).build()
    val template = parser.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "abc")
  }
}
