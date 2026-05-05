/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

final class HighlightSuite extends munit.FunSuite {

  private def highlighter: SyntaxHighlighter = SyntaxHighlighter.default

  private def assertHighlights(language: String, snippet: String)(implicit loc: munit.Location): Unit = {
    val result = highlighter.highlight(snippet, language)
    assert(result.isDefined, s"highlight($language) returned None — language not supported or engine failed to load")
    val html = result.get
    assert(html.contains("<span class=\"hl-"), s"highlight($language) produced no token spans:\n$html")
  }

  // ── Tier 1: Most popular languages ────────────────────────────────────

  test("highlight: bash") {
    assertHighlights("bash", HighlightFixtures.bash)
  }

  test("highlight: c") {
    assertHighlights("c", HighlightFixtures.c)
  }

  test("highlight: cpp") {
    assertHighlights("cpp", HighlightFixtures.cpp)
  }

  test("highlight: c_sharp") {
    assertHighlights("c_sharp", HighlightFixtures.cSharp)
  }

  test("highlight: css") {
    assertHighlights("css", HighlightFixtures.css)
  }

  test("highlight: go") {
    assertHighlights("go", HighlightFixtures.go)
  }

  test("highlight: html") {
    assertHighlights("html", HighlightFixtures.html)
  }

  test("highlight: java") {
    assertHighlights("java", HighlightFixtures.java)
  }

  test("highlight: javascript") {
    assertHighlights("javascript", HighlightFixtures.javascript)
  }

  test("highlight: json") {
    assertHighlights("json", HighlightFixtures.json)
  }

  test("highlight: markdown") {
    assertHighlights("markdown", HighlightFixtures.markdown)
  }

  test("highlight: python") {
    assertHighlights("python", HighlightFixtures.python)
  }

  test("highlight: regex") {
    assertHighlights("regex", HighlightFixtures.regex)
  }

  test("highlight: ruby") {
    assertHighlights("ruby", HighlightFixtures.ruby)
  }

  test("highlight: rust") {
    assertHighlights("rust", HighlightFixtures.rust)
  }

  test("highlight: scala") {
    assertHighlights("scala", HighlightFixtures.scala)
  }

  test("highlight: sql") {
    assertHighlights("sql", HighlightFixtures.sql)
  }

  test("highlight: toml") {
    assertHighlights("toml", HighlightFixtures.toml)
  }

  test("highlight: typescript") {
    assertHighlights("typescript", HighlightFixtures.typescript)
  }

  test("highlight: tsx") {
    assertHighlights("tsx", HighlightFixtures.tsx)
  }

  test("highlight: yaml") {
    assertHighlights("yaml", HighlightFixtures.yaml)
  }

  // ── Tier 2: Broadly used languages ────────────────────────────────────

  test("highlight: cmake") {
    assertHighlights("cmake", HighlightFixtures.cmake)
  }

  test("highlight: dockerfile") {
    assertHighlights("dockerfile", HighlightFixtures.dockerfile)
  }

  test("highlight: dtd") {
    assertHighlights("dtd", HighlightFixtures.dtd)
  }

  test("highlight: elixir") {
    assertHighlights("elixir", HighlightFixtures.elixir)
  }

  test("highlight: erlang") {
    assertHighlights("erlang", HighlightFixtures.erlang)
  }

  test("highlight: haskell") {
    assertHighlights("haskell", HighlightFixtures.haskell)
  }

  test("highlight: julia") {
    assertHighlights("julia", HighlightFixtures.julia)
  }

  test("highlight: kotlin") {
    assertHighlights("kotlin", HighlightFixtures.kotlin)
  }

  test("highlight: lua") {
    assertHighlights("lua", HighlightFixtures.lua)
  }

  test("highlight: make") {
    assertHighlights("make", HighlightFixtures.make)
  }

  test("highlight: ocaml") {
    assertHighlights("ocaml", HighlightFixtures.ocaml)
  }

  test("highlight: ocaml_interface") {
    assertHighlights("ocaml_interface", HighlightFixtures.ocamlInterface)
  }

  test("highlight: php") {
    assertHighlights("php", HighlightFixtures.php)
  }

  test("highlight: php_only") {
    assertHighlights("php_only", HighlightFixtures.phpOnly)
  }

  test("highlight: r") {
    assertHighlights("r", HighlightFixtures.r)
  }

  test("highlight: swift") {
    assertHighlights("swift", HighlightFixtures.swift)
  }

  test("highlight: vim") {
    assertHighlights("vim", HighlightFixtures.vim)
  }

  test("highlight: xml") {
    assertHighlights("xml", HighlightFixtures.xml)
  }

  test("highlight: zig") {
    assertHighlights("zig", HighlightFixtures.zig)
  }

  // ── Tier 3: Specialized languages ────────────────────────────────────

  test("highlight: arduino") {
    assertHighlights("arduino", HighlightFixtures.arduino)
  }

  test("highlight: bicep") {
    assertHighlights("bicep", HighlightFixtures.bicep)
  }

  test("highlight: cairo") {
    assertHighlights("cairo", HighlightFixtures.cairo)
  }

  test("highlight: cpon") {
    assertHighlights("cpon", HighlightFixtures.cpon)
  }

  test("highlight: cuda") {
    assertHighlights("cuda", HighlightFixtures.cuda)
  }

  test("highlight: embedded_template") {
    assertHighlights("embedded_template", HighlightFixtures.embeddedTemplate)
  }

  test("highlight: func") {
    assertHighlights("func", HighlightFixtures.func)
  }

  test("highlight: gitattributes") {
    assertHighlights("gitattributes", HighlightFixtures.gitattributes)
  }

  test("highlight: glsl") {
    assertHighlights("glsl", HighlightFixtures.glsl)
  }

  test("highlight: gosum") {
    assertHighlights("gosum", HighlightFixtures.gosum)
  }

  test("highlight: hare") {
    assertHighlights("hare", HighlightFixtures.hare)
  }

  test("highlight: jsdoc") {
    assertHighlights("jsdoc", HighlightFixtures.jsdoc)
  }

  test("highlight: kconfig") {
    assertHighlights("kconfig", HighlightFixtures.kconfig)
  }

  test("highlight: kdl") {
    assertHighlights("kdl", HighlightFixtures.kdl)
  }

  test("highlight: luadoc") {
    assertHighlights("luadoc", HighlightFixtures.luadoc)
  }

  test("highlight: luap") {
    assertHighlights("luap", HighlightFixtures.luap)
  }

  test("highlight: luau") {
    assertHighlights("luau", HighlightFixtures.luau)
  }

  test("highlight: objc") {
    assertHighlights("objc", HighlightFixtures.objc)
  }

  test("highlight: odin") {
    assertHighlights("odin", HighlightFixtures.odin)
  }

  test("highlight: po") {
    assertHighlights("po", HighlightFixtures.po)
  }

  test("highlight: pony") {
    assertHighlights("pony", HighlightFixtures.pony)
  }

  test("highlight: printf") {
    assertHighlights("printf", HighlightFixtures.printf)
  }

  test("highlight: properties") {
    assertHighlights("properties", HighlightFixtures.properties)
  }

  test("highlight: puppet") {
    assertHighlights("puppet", HighlightFixtures.puppet)
  }

  test("highlight: qmldir") {
    assertHighlights("qmldir", HighlightFixtures.qmldir)
  }

  test("highlight: requirements") {
    assertHighlights("requirements", HighlightFixtures.requirements)
  }

  test("highlight: ron") {
    assertHighlights("ron", HighlightFixtures.ron)
  }

  test("highlight: scss") {
    assertHighlights("scss", HighlightFixtures.scss)
  }

  test("highlight: squirrel") {
    assertHighlights("squirrel", HighlightFixtures.squirrel)
  }

  test("highlight: starlark") {
    assertHighlights("starlark", HighlightFixtures.starlark)
  }

  test("highlight: svelte") {
    assertHighlights("svelte", HighlightFixtures.svelte)
  }

  test("highlight: ungrammar") {
    assertHighlights("ungrammar", HighlightFixtures.ungrammar)
  }

  test("highlight: yuck") {
    assertHighlights("yuck", HighlightFixtures.yuck)
  }
}
