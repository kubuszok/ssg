/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for SiteConfig.load — ISS-1207 Phase 1.
 * Verifies that _config.yml YAML text parses into a typed SiteConfig with
 * exact field values, plus a raw DataView holding the full config.
 */
package ssg
package site

class SiteConfigSuite extends munit.FunSuite {

  test("SiteConfig.load parses a _config.yml with all typed fields") {
    val yaml =
      """title: My Test Site
        |description: A site for testing
        |source: src
        |destination: build
        |layouts_dir: _my_layouts
        |includes_dir: _my_includes
        |sass_dir: _my_sass
        |permalink: pretty
        |baseurl: /blog
        |minify: true
        |""".stripMargin

    val config = SiteConfig.load(yaml)

    // Typed fields: exact value assertions (not non-emptiness / contains).
    assertEquals(config.source.pathString, "src")
    assertEquals(config.destination.pathString, "build")
    assertEquals(config.layoutsDir, "_my_layouts")
    assertEquals(config.includesDir, "_my_includes")
    assertEquals(config.sassDir, "_my_sass")
    assertEquals(config.permalink, PermalinkStyle.Pretty)
    assertEquals(config.baseurl, "/blog")
    assertEquals(config.minify, true)

    // raw DataView must hold the full config including arbitrary keys.
    val rawMap = config.raw.asMap.toOption
    assert(rawMap.isDefined, "raw must be a mapping")
    val map = rawMap.get

    // Verify arbitrary keys are preserved (Q2 lenient).
    val titleDv = map.get("title")
    assert(titleDv.isDefined, "raw must contain 'title'")
    assertEquals(titleDv.get.asString.toOption, Some("My Test Site"))

    val descDv = map.get("description")
    assert(descDv.isDefined, "raw must contain 'description'")
    assertEquals(descDv.get.asString.toOption, Some("A site for testing"))
  }

  test("SiteConfig.load uses defaults for missing fields") {
    val yaml = "title: Minimal Site\n"

    val config = SiteConfig.load(yaml)

    assertEquals(config.source.pathString, ".")
    assertEquals(config.destination.pathString, "_site")
    assertEquals(config.layoutsDir, "_layouts")
    assertEquals(config.includesDir, "_includes")
    assertEquals(config.sassDir, "_sass")
    assertEquals(config.permalink, PermalinkStyle.Date)
    assertEquals(config.baseurl, "")
    assertEquals(config.minify, false)

    // raw still holds the parsed YAML with the title key.
    val rawMap = config.raw.asMap.toOption
    assert(rawMap.isDefined, "raw must be a mapping")
    val titleDv = rawMap.get.get("title")
    assert(titleDv.isDefined, "raw must contain 'title'")
    assertEquals(titleDv.get.asString.toOption, Some("Minimal Site"))
  }

  test("SiteConfig.load recognizes permalink style 'date'") {
    val yaml   = "permalink: date\n"
    val config = SiteConfig.load(yaml)
    assertEquals(config.permalink, PermalinkStyle.Date)
  }

  test("SiteConfig.load recognizes permalink style 'none'") {
    val yaml   = "permalink: none\n"
    val config = SiteConfig.load(yaml)
    assertEquals(config.permalink, PermalinkStyle.None)
  }

  test("SiteConfig.load falls back to Date for unknown permalink style") {
    val yaml   = "permalink: /custom/:title/\n"
    val config = SiteConfig.load(yaml)
    assertEquals(config.permalink, PermalinkStyle.Date)
  }

  test("SiteConfig.load preserves boolean and integer values in raw DataView") {
    val yaml =
      """minify: true
        |port: 4000
        |title: Test
        |""".stripMargin

    val config = SiteConfig.load(yaml)
    val rawMap = config.raw.asMap.toOption.get

    // Boolean value preserved as boolean (not string "true").
    assertEquals(rawMap("minify").asBoolean.toOption, Some(true))

    // Integer value preserved as long (kindlings-yaml uses Long for ints).
    assertEquals(rawMap("port").asLong.toOption, Some(4000L))

    // String value preserved as string.
    assertEquals(rawMap("title").asString.toOption, Some("Test"))
  }

  test("SiteConfig.load returns default config for malformed YAML") {
    val yaml   = "{{{{invalid yaml"
    val config = SiteConfig.load(yaml)

    // Should fall back to all defaults.
    assertEquals(config.source.pathString, ".")
    assertEquals(config.destination.pathString, "_site")
    assertEquals(config.minify, false)
  }

  test("SiteConfig.load returns default config for empty input") {
    val config = SiteConfig.load("")

    assertEquals(config.source.pathString, ".")
    assertEquals(config.destination.pathString, "_site")
    assertEquals(config.layoutsDir, "_layouts")
    assertEquals(config.includesDir, "_includes")
    assertEquals(config.sassDir, "_sass")
    assertEquals(config.permalink, PermalinkStyle.Date)
    assertEquals(config.baseurl, "")
    assertEquals(config.minify, false)
  }

  test("SiteConfig.load preserves nested map in raw DataView") {
    val yaml =
      """title: Nested Test
        |sass:
        |  style: compressed
        |  sourcemap: never
        |""".stripMargin

    val config = SiteConfig.load(yaml)
    val rawMap = config.raw.asMap.toOption.get

    // Verify nested map is preserved.
    val sassDv     = rawMap("sass")
    val sassMapOpt = sassDv.asMap.toOption
    assert(sassMapOpt.isDefined, "sass must be a nested mapping")
    val sassMap = sassMapOpt.get
    assertEquals(sassMap("style").asString.toOption, Some("compressed"))
    assertEquals(sassMap("sourcemap").asString.toOption, Some("never"))
  }

  test("SiteConfig flavor defaults to Jekyll") {
    val config = SiteConfig.load("title: test\n")
    assertEquals(config.flavor.liquidFlavor, ssg.liquid.parser.Flavor.JEKYLL)
    assertEquals(config.flavor.layoutKey, "layout")
  }
}
