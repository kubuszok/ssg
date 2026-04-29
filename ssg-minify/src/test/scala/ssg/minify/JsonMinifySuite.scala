/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

import ssg.minify.json.JsonMinifier

final class JsonMinifySuite extends munit.FunSuite {

  test("empty string") {
    assertEquals(JsonMinifier.minify(""), "")
  }

  test("empty object") {
    assertEquals(JsonMinifier.minify("{ }"), "{}")
  }

  test("empty array") {
    assertEquals(JsonMinifier.minify("[ ]"), "[]")
  }

  test("simple object with whitespace") {
    val input = """{ "a" : 1 , "b" : 2 }"""
    assertEquals(JsonMinifier.minify(input), """{"a":1,"b":2}""")
  }

  test("nested object") {
    val input =
      """{
        |  "name": "test",
        |  "nested": {
        |    "x": 1,
        |    "y": 2
        |  }
        |}""".stripMargin
    assertEquals(JsonMinifier.minify(input), """{"name":"test","nested":{"x":1,"y":2}}""")
  }

  test("array with whitespace") {
    val input = """[ 1 , 2 , 3 , 4 ]"""
    assertEquals(JsonMinifier.minify(input), "[1,2,3,4]")
  }

  test("preserves whitespace inside strings") {
    val input = """{ "message" : "hello   world\tfoo" }"""
    assertEquals(JsonMinifier.minify(input), """{"message":"hello   world\tfoo"}""")
  }

  test("preserves escape sequences in strings") {
    val input = """{ "path" : "C:\\Users\\test" }"""
    assertEquals(JsonMinifier.minify(input), """{"path":"C:\\Users\\test"}""")
  }

  test("preserves escaped quotes in strings") {
    val input = """{ "say" : "he said \"hi\"" }"""
    assertEquals(JsonMinifier.minify(input), """{"say":"he said \"hi\""}""")
  }

  test("removes single-line comments") {
    val input =
      """{
        |  // this is a comment
        |  "a": 1
        |}""".stripMargin
    assertEquals(JsonMinifier.minify(input), """{"a":1}""")
  }

  test("removes block comments") {
    val input =
      """{
        |  /* block comment */
        |  "a": 1
        |}""".stripMargin
    assertEquals(JsonMinifier.minify(input), """{"a":1}""")
  }

  test("removes multi-line block comments") {
    val input =
      """{
        |  /*
        |   * multi-line
        |   * block comment
        |   */
        |  "a": 1
        |}""".stripMargin
    assertEquals(JsonMinifier.minify(input), """{"a":1}""")
  }

  test("does not remove // inside strings") {
    val input = """{ "url" : "http://example.com" }"""
    assertEquals(JsonMinifier.minify(input), """{"url":"http://example.com"}""")
  }

  test("does not remove /* inside strings") {
    val input = """{ "code" : "/* not a comment */" }"""
    assertEquals(JsonMinifier.minify(input), """{"code":"/* not a comment */"}""")
  }

  test("already minified input is idempotent") {
    val input = """{"a":1,"b":[2,3]}"""
    assertEquals(JsonMinifier.minify(input), input)
  }

  test("unicode content preserved") {
    val input = """{ "emoji" : "🎉 hello 世界" }"""
    assertEquals(JsonMinifier.minify(input), """{"emoji":"🎉 hello 世界"}""")
  }

  test("boolean and null values") {
    val input = """{ "a" : true , "b" : false , "c" : null }"""
    assertEquals(JsonMinifier.minify(input), """{"a":true,"b":false,"c":null}""")
  }

  test("tabs and carriage returns removed") {
    val input = "{\r\n\t\"a\"\t:\t1\r\n}"
    assertEquals(JsonMinifier.minify(input), """{"a":1}""")
  }

  test("mixed comments and data") {
    val input =
      """{
        |  // first comment
        |  "x": 1, /* inline comment */
        |  "y": 2
        |  // trailing comment
        |}""".stripMargin
    assertEquals(JsonMinifier.minify(input), """{"x":1,"y":2}""")
  }

  test("malformed: trailing comma") {
    val input  = """{"a": 1,}"""
    val result = JsonMinifier.minify(input)
    // Should not throw; should return something containing the data
    assert(result.contains("\"a\""), s"Expected key preserved despite trailing comma, got: $result")
  }

  test("malformed: unclosed string") {
    val input  = """{"a": "hello}"""
    val result = JsonMinifier.minify(input)
    // Should not throw; should return something
    assert(result.nonEmpty, s"Expected non-empty result for unclosed string, got: $result")
  }

  test("malformed: unclosed array") {
    val input  = """[1, 2, 3"""
    val result = JsonMinifier.minify(input)
    // Should not throw; should return something containing the data
    assert(result.contains("1"), s"Expected data preserved despite unclosed array, got: $result")
  }
}
