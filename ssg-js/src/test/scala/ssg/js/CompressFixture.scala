/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Terser compress test fixture parser and runner.
 *
 * Parses Terser's compress test fixture format and runs the
 * compression pipeline to verify output matches expectations.
 */
package ssg
package js

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

/** A single compress test fixture. */
final case class CompressFixture(
  name:        String,
  options:     Map[String, Any],
  input:       String,
  expect:      String | Null,
  expectExact: String | Null
)

/** Parses Terser's compress fixture files (labeled blocks with options, input, expect sections). */
object FixtureParser {

  /** Parse a fixture file into a sequence of test fixtures.
    *
    * Uses a brace-counting parser to extract labeled blocks and their sub-sections.
    */
  def parse(content: String): Seq[CompressFixture] = {
    val fixtures = ArrayBuffer.empty[CompressFixture]
    val lines = content.split('\n')
    var i = 0

    while (i < lines.length) {
      val line = lines(i).trim
      // Look for top-level labeled block: "name: {"
      if (line.matches("^[a-zA-Z0-9_]+\\s*:\\s*\\{\\s*$")) {
        val name = line.takeWhile(c => c != ':').trim
        i += 1
        val blockEnd = findMatchingBrace(lines, i)
        if (blockEnd > i) {
          val blockLines = lines.slice(i, blockEnd)
          val fixture = parseBlock(name, blockLines)
          if (fixture != null) fixtures.addOne(fixture.nn)
          i = blockEnd + 1
        } else {
          i += 1
        }
      } else {
        i += 1
      }
    }

    fixtures.toSeq
  }

  /** Find the line index of the matching closing brace, given that the opening brace is on the previous line. */
  private def findMatchingBrace(lines: Array[String], startLine: Int): Int = {
    var depth = 1 // the opening brace was on the line before startLine
    var i = startLine
    while (i < lines.length && depth > 0) {
      for (c <- lines(i)) {
        if (c == '{') depth += 1
        else if (c == '}') depth -= 1
      }
      if (depth > 0) i += 1
    }
    i
  }

  /** Parse a single test block's sub-sections. */
  private def parseBlock(name: String, lines: Array[String]): CompressFixture | Null = {
    val options = mutable.Map.empty[String, Any]
    var input: String | Null = null
    var expect: String | Null = null
    var expectExact: String | Null = null

    var i = 0
    while (i < lines.length) {
      val line = lines(i).trim

      if (line.startsWith("options")) {
        // options = { ... }
        val optBlock = extractBlock(lines, i, "=")
        if (optBlock != null) {
          parseOptions(optBlock.nn, options)
          i = skipBlock(lines, i)
        } else {
          i += 1
        }
      } else if (line.startsWith("input")) {
        val block = extractBlock(lines, i, ":")
        if (block != null) input = block
        i = skipBlock(lines, i)
      } else if (line.startsWith("expect_exact")) {
        // expect_exact: "string" or expect_exact: 'string'
        val afterColon = line.dropWhile(_ != ':').drop(1).trim
        if (afterColon.startsWith("\"") || afterColon.startsWith("'")) {
          val quote = afterColon.charAt(0)
          val endIdx = afterColon.indexOf(quote, 1)
          if (endIdx > 0) {
            expectExact = afterColon.substring(1, endIdx)
          }
        }
        i += 1
      } else if (line.startsWith("expect")) {
        val block = extractBlock(lines, i, ":")
        if (block != null) expect = block
        i = skipBlock(lines, i)
      } else {
        i += 1
      }
    }

    if (input != null) {
      CompressFixture(
        name = name,
        options = options.toMap,
        input = input.nn,
        expect = expect,
        expectExact = expectExact
      )
    } else {
      null
    }
  }

  /** Extract the content of a brace-delimited block. */
  private def extractBlock(lines: Array[String], startLine: Int, separator: String): String | Null =
    boundary[String | Null] {
      val line = lines(startLine).trim
      val sepIdx = line.indexOf(separator)
      if (sepIdx < 0) break(null)

      val afterSep = line.substring(sepIdx + separator.length).trim
      if (!afterSep.startsWith("{")) break(null)

      // Find the matching closing brace using char-by-char scanning
      var depth = 0
      val sb = new StringBuilder
      var i = startLine
      var started = false
      var done = false
      while (i < lines.length && !done) {
        val l = lines(i)
        var j = 0
        while (j < l.length && !done) {
          val c = l.charAt(j)
          if (c == '{') {
            if (started) sb.append(c)
            depth += 1
            started = true
          } else if (c == '}') {
            depth -= 1
            if (depth == 0) {
              done = true
            } else {
              sb.append(c)
            }
          } else if (started) {
            sb.append(c)
          }
          j += 1
        }
        if (started && !done) sb.append('\n')
        i += 1
      }
      if (done) sb.toString().trim else null
    }

  /** Skip past a brace-delimited block. */
  private def skipBlock(lines: Array[String], startLine: Int): Int =
    boundary[Int] {
      var depth = 0
      var i = startLine
      while (i < lines.length) {
        val l = lines(i)
        var j = 0
        while (j < l.length) {
          if (l.charAt(j) == '{') depth += 1
          else if (l.charAt(j) == '}') depth -= 1
          j += 1
        }
        i += 1
        if (depth <= 0) break(i)
      }
      i
    }

  /** Parse option key-value pairs from the block content. */
  private def parseOptions(content: String, options: mutable.Map[String, Any]): Unit = {
    // Simple line-by-line parser for key: value pairs
    for (line <- content.split('\n')) {
      val trimmed = line.trim.stripSuffix(",")
      val colonIdx = trimmed.indexOf(':')
      if (colonIdx > 0) {
        val key = trimmed.substring(0, colonIdx).trim
        val value = trimmed.substring(colonIdx + 1).trim
        if (key.nonEmpty && value.nonEmpty) {
          val parsed: Any = value match {
            case "true"  => true
            case "false" => false
            case s if s.forall(c => c.isDigit || c == '.') => s.toDouble
            case s if s.startsWith("\"") && s.endsWith("\"") => s.substring(1, s.length - 1)
            case s => s
          }
          options(key) = parsed
        }
      }
    }
  }
}
