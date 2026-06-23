/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.{ HashMap => JHashMap }

import ssg.data.DataView

/** Pinning tests for ISS-1301: Strip_HTML must replicate liqp's MULTILINE
  * semantics where `.*?` does NOT cross line terminators.
  *
  * Ground truth: liqp Strip_HTML.java:14 compiles STRIP_HTML_BLOCKS with
  * Pattern.MULTILINE (not DOTALL), and line 17 compiles STRIP_HTML_TAGS the
  * same way. Java MULTILINE only changes `^`/`$` anchors -- it does NOT make
  * `.` match `\n`. Therefore `<script.*?</script>` only removes a script
  * block when the entire construct is on ONE line. Multi-line blocks are left
  * intact; only their individual single-line tags are stripped by STRIP_HTML_TAGS.
  *
  * Verified against liqp 0.9.0 Strip_HTML.java:14,17,28-29.
  */
final class StripHtmlMultilineIss1301Suite extends munit.FunSuite {

  private def stripHtml(input: String): String = {
    val vars = new JHashMap[String, DataView]()
    vars.put("html", TestHelper.dv(input))
    Template.parse("{{ html | strip_html }}").render(vars)
  }

  // ---- Single-line cases (baseline, should always pass) ----

  test("single-line script block is removed entirely") {
    // liqp: "<script>alert('hi')</script>text" => "text"
    assertEquals(stripHtml("<script>alert('hi')</script>text"), "text")
  }

  test("single-line style block is removed entirely") {
    // liqp: "<style>body{}</style>text" => "text"
    assertEquals(stripHtml("<style>body{}</style>text"), "text")
  }

  test("single-line comment is removed entirely") {
    // liqp: "a<!--comment-->b" => "ab"
    assertEquals(stripHtml("a<!--comment-->b"), "ab")
  }

  test("single-line tags are removed") {
    // liqp: "<p>hello</p>" => "hello"
    assertEquals(stripHtml("<p>hello</p>"), "hello")
  }

  // ---- Multi-line cases (the ISS-1301 fix) ----

  test("multi-line script block: content between tags preserved") {
    // liqp verified: "a<script>\nvar x=1;\n</script>b"
    // BLOCKS pass: <script.*?</script> with MULTILINE does NOT match across
    // newlines, so the block is left intact.
    // TAGS pass: <script> on line 1 removed, </script> on line 3 removed.
    // Result: "a\nvar x=1;\nb"
    assertEquals(stripHtml("a<script>\nvar x=1;\n</script>b"), "a\nvar x=1;\nb")
  }

  test("multi-line style block: content between tags preserved") {
    // liqp verified: same MULTILINE logic applies to <style>
    assertEquals(stripHtml("a<style>\np { color: red; }\n</style>b"), "a\np { color: red; }\nb")
  }

  test("multi-line comment preserved verbatim") {
    // liqp verified: <!--.*?--> with MULTILINE does NOT cross newlines.
    // <!-- on line 1 has no matching --> on the same line.
    // TAGS pass: <!-- does not end with > on the same line, so not matched.
    // --> on line 3 starts with -, not <, so TAGS does not touch it either.
    // Result: the multi-line comment is preserved verbatim.
    assertEquals(stripHtml("a<!--\ncomment\n-->b"), "a<!--\ncomment\n-->b")
  }

  test("multi-line tag with attributes preserved") {
    // liqp verified: <.*?> with MULTILINE does NOT cross newlines.
    // <div\nclass="x"> has < and > on different lines, no match.
    assertEquals(stripHtml("a<div\nclass=\"x\">b"), "a<div\nclass=\"x\">b")
  }

  // ---- Regression guards ----

  test("single-line script still removed end-to-end") {
    assertEquals(stripHtml("a<script>alert('x')</script>b"), "ab")
  }

  test("unclosed script tag is only stripped as a tag") {
    // liqp: "<script>" alone -- BLOCKS needs </script> which is absent,
    // BLOCKS pass leaves it. TAGS pass matches <script> and removes it.
    assertEquals(stripHtml("a<script>b"), "ab")
  }

  test("adjacent single-line blocks are all removed") {
    // liqp: replaceAll is global, removes all matches left-to-right
    assertEquals(stripHtml("<script>a</script><style>b</style><!--c-->text"), "text")
  }
}
