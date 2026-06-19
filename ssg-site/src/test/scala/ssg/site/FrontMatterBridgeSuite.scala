/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for FrontMatterBridge — ISS-1207 Phase 1.
 * Verifies that front-matter blocks are detected, split, and re-parsed
 * with kindlings-yaml into faithful DataView values including nested maps,
 * booleans, and scalars (exact equality, not substring/contains).
 */
package ssg
package site

class FrontMatterBridgeSuite extends munit.FunSuite {

  test("FrontMatterBridge.parse splits front matter and body") {
    val input =
      "---\ntitle: Hello World\n---\nThis is the body.\n"

    val (frontMatter, body) = FrontMatterBridge.parse(input)

    // Body is the text after the closing ---.
    assertEquals(body, "This is the body.\n")

    // Front matter is a DataView mapping with the title key.
    val mapOpt = frontMatter.asMap.toOption
    assert(mapOpt.isDefined, "front matter must be a mapping")
    val map = mapOpt.get
    assertEquals(map("title").asString.toOption, Some("Hello World"))
  }

  test("FrontMatterBridge.parse recovers boolean + scalar + nested map") {
    // This test satisfies the DoD requirement: "a front-matter block parses
    // to the expected DataView including a nested map + boolean + scalar
    // (exact equality, NOT substring/contains)."
    val input =
      "---\n" +
        "title: My Page\n" +
        "draft: true\n" +
        "author:\n" +
        "  name: Alice\n" +
        "  email: alice@example.com\n" +
        "---\n" +
        "Page content here.\n"

    val (frontMatter, body) = FrontMatterBridge.parse(input)

    assertEquals(body, "Page content here.\n")

    val map = frontMatter.asMap.toOption
    assert(map.isDefined, "front matter must be a mapping")
    val fm = map.get

    // Scalar string value.
    assertEquals(fm("title").asString.toOption, Some("My Page"))

    // Boolean value — must be a real boolean, not a string "true".
    assertEquals(fm("draft").asBoolean.toOption, Some(true))

    // Nested map value.
    val authorDv  = fm("author")
    val authorMap = authorDv.asMap.toOption
    assert(authorMap.isDefined, "author must be a nested mapping")
    val author = authorMap.get
    assertEquals(author("name").asString.toOption, Some("Alice"))
    assertEquals(author("email").asString.toOption, Some("alice@example.com"))
  }

  test("FrontMatterBridge.parse returns empty mapping and full body when no front matter") {
    val input = "No front matter here.\nJust plain text.\n"

    val (frontMatter, body) = FrontMatterBridge.parse(input)

    assertEquals(body, input)
    val map = frontMatter.asMap.toOption
    assert(map.isDefined, "no-front-matter result must be an empty mapping")
    assert(map.get.isEmpty, "mapping must be empty")
  }

  test("FrontMatterBridge.parse handles empty front matter block") {
    val input = "---\n---\nBody after empty front matter.\n"

    val (frontMatter, body) = FrontMatterBridge.parse(input)

    assertEquals(body, "Body after empty front matter.\n")
    val map = frontMatter.asMap.toOption
    assert(map.isDefined, "empty front matter must be an empty mapping")
    assert(map.get.isEmpty, "mapping must be empty")
  }

  test("FrontMatterBridge.parse preserves integer values") {
    val input = "---\nweight: 42\n---\nbody\n"

    val (frontMatter, _) = FrontMatterBridge.parse(input)

    val map = frontMatter.asMap.toOption.get
    // kindlings-yaml parses untagged integers as Long.
    assertEquals(map("weight").asLong.toOption, Some(42L))
  }

  test("FrontMatterBridge.parse preserves list values") {
    val input =
      "---\ntags:\n  - scala\n  - ssg\n  - yaml\n---\nbody\n"

    val (frontMatter, _) = FrontMatterBridge.parse(input)

    val map  = frontMatter.asMap.toOption.get
    val tags = map("tags").asVector.toOption
    assert(tags.isDefined, "tags must be a vector")
    val tagValues = tags.get.map(_.asString.toOption)
    assertEquals(tagValues, Vector(Some("scala"), Some("ssg"), Some("yaml")))
  }

  test("FrontMatterBridge.hasFrontMatter detects presence") {
    assert(FrontMatterBridge.hasFrontMatter("---\ntitle: test\n---\nbody\n"))
    assert(!FrontMatterBridge.hasFrontMatter("No front matter here."))
  }

  test("FrontMatterBridge.hasFrontMatter detects empty front matter") {
    assert(FrontMatterBridge.hasFrontMatter("---\n---\nbody\n"))
  }

  test("FrontMatterBridge.parse handles false boolean") {
    val input = "---\npublished: false\n---\nbody\n"

    val (frontMatter, _) = FrontMatterBridge.parse(input)

    val map = frontMatter.asMap.toOption.get
    assertEquals(map("published").asBoolean.toOption, Some(false))
  }

  test("FrontMatterBridge.parse non-mapping top-level yields empty mapping") {
    // A front-matter block that is just a scalar (not key-value pairs)
    // should degrade to an empty mapping, not crash.
    val input = "---\njust a string\n---\nbody\n"

    val (frontMatter, body) = FrontMatterBridge.parse(input)

    assertEquals(body, "body\n")
    val map = frontMatter.asMap.toOption
    assert(map.isDefined, "non-mapping front matter must degrade to empty mapping")
    assert(map.get.isEmpty, "mapping must be empty")
  }
}
