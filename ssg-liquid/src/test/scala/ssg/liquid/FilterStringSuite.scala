/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.HashMap

final class FilterStringSuite extends munit.FunSuite {

  // append

  test("append strings") {
    assertEquals(Template.parse("{{ 'a' | append: 'b' }}").render(), "ab")
  }

  test("append numbers") {
    assertEquals(Template.parse("{{ 1 | append: 23 }}").render(), "123")
  }

  // capitalize

  test("capitalize single char") {
    assertEquals(Template.parse("{{'a' | capitalize}}").render(), "A")
  }

  test("capitalize empty string") {
    assertEquals(Template.parse("{{'' | capitalize}}").render(), "")
  }

  // downcase

  test("downcase") {
    assertEquals(Template.parse("{{ 'Abc' | downcase }}").render(), "abc")
  }

  // upcase

  test("upcase") {
    assertEquals(Template.parse("{{ 'abc' | upcase }}").render(), "ABC")
  }

  // escape

  test("escape html entities") {
    assertEquals(
      Template.parse("{{ '<foo>&\"' | escape }}").render(),
      "&lt;foo&gt;&amp;&quot;"
    )
  }

  // escape_once

  test("escape_once") {
    assertEquals(
      Template.parse("{{ '&&amp;' | escape_once }}").render(),
      "&amp;&amp;"
    )
  }

  // lstrip

  test("lstrip") {
    assertEquals(Template.parse("{{ ' ab c  ' | lstrip }}").render(), "ab c  ")
  }

  // rstrip

  test("rstrip") {
    assertEquals(Template.parse("{{ ' ab c  ' | rstrip }}").render(), " ab c")
  }

  // strip

  test("strip") {
    assertEquals(Template.parse("{{ ' ab c  ' | strip }}").render(), "ab c")
  }

  // strip_html

  test("strip_html removes script tags") {
    val vars = new HashMap[String, Any]()
    vars.put("html", "<script>alert('hi')</script>text")
    assertEquals(Template.parse("{{ html | strip_html }}").render(vars), "text")
  }

  test("strip_html removes style tags") {
    val vars = new HashMap[String, Any]()
    vars.put("html", "<style>body{}</style>text")
    assertEquals(Template.parse("{{ html | strip_html }}").render(vars), "text")
  }

  test("strip_html removes regular tags") {
    val vars = new HashMap[String, Any]()
    vars.put("html", "<p>hello</p>")
    assertEquals(Template.parse("{{ html | strip_html }}").render(vars), "hello")
  }

  // strip_newlines

  test("strip_newlines removes line breaks") {
    val vars = new HashMap[String, Any]()
    vars.put("text", "a\r\nb\nc")
    assertEquals(Template.parse("{{ text | strip_newlines }}").render(vars), "abc")
  }

  // newline_to_br

  test("newline_to_br") {
    val vars = new HashMap[String, Any]()
    vars.put("text", "a\nb")
    val result = Template.parse("{{ text | newline_to_br }}").render(vars)
    assert(result.contains("<br />"), s"Expected <br /> in: $result")
  }

  // prepend

  test("prepend") {
    assertEquals(Template.parse("{{ 'a' | prepend: 'b' }}").render(), "ba")
  }

  // remove

  test("remove") {
    assertEquals(Template.parse("{{ 'ababab' | remove:'a' }}").render(), "bbb")
  }

  // remove_first

  test("remove_first") {
    assertEquals(Template.parse("{{ 'ababab' | remove_first:'a' }}").render(), "babab")
  }

  // replace

  test("replace") {
    assertEquals(Template.parse("{{ 'ababab' | replace:'a', 'A' }}").render(), "AbAbAb")
  }

  // replace_first

  test("replace_first") {
    assertEquals(Template.parse("{{ 'ababab' | replace_first:'a', 'A' }}").render(), "Ababab")
  }

  // split

  test("split with join") {
    assertEquals(Template.parse("{{ 'a-b-c' | split:'-' | join:',' }}").render(), "a,b,c")
  }

  // truncate

  test("truncate") {
    assertEquals(Template.parse("{{ 'abcdefghij' | truncate: 5 }}").render(), "ab...")
  }

  test("truncate null") {
    assertEquals(Template.parse("{{ nothing | truncate: 5 }}").render(), "")
  }

  // truncatewords

  test("truncatewords") {
    assertEquals(
      Template.parse("{{ 'a b c d e f' | truncatewords: 3 }}").render(),
      "a b c..."
    )
  }

  // url_encode

  test("url_encode") {
    assertEquals(
      Template.parse("{{ 'foo+1@example.com' | url_encode }}").render(),
      "foo%2B1%40example.com"
    )
  }

  // url_decode

  test("url_decode") {
    assertEquals(
      Template.parse("{{ 'foo%2B1%40example.com' | url_decode }}").render(),
      "foo+1@example.com"
    )
  }

  // h (alias for escape)

  test("h filter") {
    assertEquals(Template.parse("{{ '<foo>' | h }}").render(), "&lt;foo&gt;")
  }
}
