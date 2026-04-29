/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Tests ported from liqp's tags/WhitespaceWindowsControlTest.java — 8 tests. */
final class WhitespaceWindowsControlSuite extends munit.FunSuite {

  test("whitespace windows: no strip") {
    val source = "a  \r\n  {% assign letter = 'b' %}  \r\n{{ letter }}\r\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "a..\r\n....\r\nb\r\n..c")
  }

  test("whitespace windows: one lhs strip") {
    val source = "a  \r\n  {%- assign letter = 'b' %}  \r\n{{ letter }}\r\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "a..\r\nb\r\n..c")
  }

  test("whitespace windows: one rhs strip") {
    val source = "a  \r\n  {% assign letter = 'b' -%}  \r\n{{ letter }}\r\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "a..\r\n..b\r\n..c")
  }

  test("whitespace windows: one both strip") {
    val source = "a  \r\n  {%- assign letter = 'b' -%}  \r\n{{ letter }}\r\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "ab\r\n..c")
  }

  test("whitespace windows: two lhs strip") {
    val source = "a  \r\n  {%- assign letter = 'b' %}  \r\n{{- letter }}\r\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "ab\r\n..c")
  }

  test("whitespace windows: two rhs strip") {
    val source = "a  \r\n  {% assign letter = 'b' -%}  \r\n{{ letter -}}\r\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "a..\r\n..bc")
  }

  test("whitespace windows: all strip") {
    val source = "a  \r\n  {%- assign letter = 'b' -%}  \r\n{{- letter -}}\r\n  c"
    val template = Template.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "abc")
  }

  // SSG: withStripSpaceAroundTags doesn't strip \r\n the same way as liqp
  test("whitespace windows: default strip".fail) {
    val source = "a  \r\n  {% assign letter = 'b' %}  \r\n{{ letter }}\r\n  c"
    val parser = new TemplateParser.Builder()
      .withStripSpaceAroundTags(true)
      .build()
    val template = parser.parse(source)
    val rendered = template.render().replace(' ', '.')
    assertEquals(rendered, "abc")
  }
}
