/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JSON (de)serialization for Source Map V3 data.
 *
 * Two halves, both of which terser/minify.js relies on but delegates to the JS
 * runtime:
 *
 *   - `parse` ports the `JSON.parse(...)` that `new SourceMapConsumer(orig)`
 *     performs on the JSON string returned by `read_source_map` / passed as
 *     `options.sourceMap.content` (lib/minify.js:33-40,226-230; lib/sourcemap.js:73).
 *     The Scala [[SourceMapConsumer]] consumes a structured [[SourceMapData]],
 *     so the decoded inline JSON must be parsed into one here.
 *
 *   - `stringify` ports the `JSON.stringify(map)` terser applies to the encoded
 *     map (lib/minify.js:336,347-348) for the `asObject == false` result form and
 *     the `url == "inline"` data-URI. The key order mirrors @jridgewell's
 *     `toEncodedMap()` object shape (version, file?, names, sourceRoot?, sources,
 *     sourcesContent?, mappings) so the Base64 of the stringified map is
 *     byte-identical to upstream.
 *
 * This is a map-shaped parser (the V3 source-map fields), not a
 * general JSON library — ssg-js depends solely on ssg-commons, which carries no
 * JSON parser.
 *
 * Original source: @jridgewell/gen-mapping toEncodedMap + JSON (parse via sourcemap.js:73)
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: JSON.stringify(toEncodedMap()) -> stringify, JSON.parse -> parse.
 *   Idiom: stringify reproduces @jridgewell's toEncodedMap() key order
 *     (version, file?, names, sourceRoot?, sources, sourcesContent?, mappings)
 *     so the Base64 of the stringified map is byte-identical to upstream; the
 *     recursive-descent reader stands in for the JS runtime's JSON.parse, which
 *     ssg-commons has no dependency for.
 *
 * Covenant: full-port
 * Covenant-js-reference: @jridgewell/gen-mapping toEncodedMap + JSON
 * Covenant-verified: 2026-06-20
 */
package ssg
package js
package sourcemap

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary

/** Parse/serialize [[SourceMapData]] to and from a JSON V3 source-map string. */
object SourceMapJson {

  // ==========================================================================
  // Serialization — mirrors @jridgewell toEncodedMap() + JSON.stringify order.
  // ==========================================================================

  /** Serialize a source map to its JSON string, matching `JSON.stringify(map)` over
    * @jridgewell's
    *   `toEncodedMap()` object (minify.js:336,347-348).
    *
    * Field order and omission follow the cleaned encoded map (sourcemap.js `clean`, lib/sourcemap.js:115-121): `file`/`sourceRoot` are emitted only when non-null, and `sourcesContent` only when
    * present (the `clean` step drops all-null content).
    */
  def stringify(map: SourceMapData): String = {
    val sb = new StringBuilder
    sb.append('{')
    sb.append("\"version\":").append(map.version)
    if (map.file != null) {
      sb.append(",\"file\":")
      writeString(sb, map.file.nn)
    }
    sb.append(",\"names\":")
    writeStringArray(sb, map.names)
    if (map.sourceRoot != null) {
      sb.append(",\"sourceRoot\":")
      writeString(sb, map.sourceRoot.nn)
    }
    sb.append(",\"sources\":")
    writeStringArray(sb, map.sources)
    if (map.sourcesContent.nonEmpty) {
      sb.append(",\"sourcesContent\":")
      writeNullableStringArray(sb, map.sourcesContent)
    }
    sb.append(",\"mappings\":")
    writeString(sb, map.mappings)
    sb.append('}')
    sb.toString()
  }

  private def writeStringArray(sb: StringBuilder, arr: ArrayBuffer[String]): Unit = {
    sb.append('[')
    var i = 0
    while (i < arr.length) {
      if (i > 0) sb.append(',')
      writeString(sb, arr(i))
      i += 1
    }
    sb.append(']')
  }

  private def writeNullableStringArray(sb: StringBuilder, arr: ArrayBuffer[String | Null]): Unit = {
    sb.append('[')
    var i = 0
    while (i < arr.length) {
      if (i > 0) sb.append(',')
      arr(i) match {
        case null => sb.append("null")
        case s: String => writeString(sb, s)
      }
      i += 1
    }
    sb.append(']')
  }

  /** JSON-escape a string exactly as `JSON.stringify` does (control chars, quote, backslash). */
  private def writeString(sb: StringBuilder, s: String): Unit = {
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _    =>
          if (c < 0x20) {
            sb.append("\\u")
            val hex = Integer.toHexString(c.toInt)
            var p   = hex.length
            while (p < 4) { sb.append('0'); p += 1 }
            sb.append(hex)
          } else {
            sb.append(c)
          }
      }
      i += 1
    }
    sb.append('"')
  }

  // ==========================================================================
  // Parsing — JSON.parse of a V3 source-map string into SourceMapData.
  // ==========================================================================

  /** Parse failure for an inline/input source-map JSON string. */
  final class SourceMapParseError(msg: String) extends RuntimeException(msg)

  /** Parse a JSON V3 source-map string into a [[SourceMapData]] (the `JSON.parse` a `SourceMapConsumer` performs on `options.sourceMap.content`, sourcemap.js:73).
    */
  def parse(json: String): SourceMapData = {
    val p = new JsonReader(json)
    p.skipWs()
    val obj = p.readObject()
    p.skipWs()
    if (!p.atEnd) throw new SourceMapParseError("trailing data after source map JSON")

    var version:        Int                        = 3
    var file:           String | Null              = null
    var sourceRoot:     String | Null              = null
    val sources:        ArrayBuffer[String]        = ArrayBuffer.empty
    val sourcesContent: ArrayBuffer[String | Null] = ArrayBuffer.empty
    val names:          ArrayBuffer[String]        = ArrayBuffer.empty
    var mappings:       String                     = ""

    obj.foreach { case (key, value) =>
      key match {
        case "version" =>
          value match {
            case JsonNum(n) => version = n.toInt
            case JsonNull   => // leave default
            case _          => // ignore unexpected type
          }
        case "file" =>
          value match {
            case JsonStr(s) => file = s
            case _          => // null/absent
          }
        case "sourceRoot" =>
          value match {
            case JsonStr(s) => sourceRoot = s
            case _          =>
          }
        case "sources" =>
          value match {
            case JsonArr(items) =>
              items.foreach {
                case JsonStr(s) => sources.addOne(s)
                case JsonNull   => sources.addOne("")
                case _          =>
              }
            case _ =>
          }
        case "sourcesContent" =>
          value match {
            case JsonArr(items) =>
              items.foreach {
                case JsonStr(s) => sourcesContent.addOne(s)
                case JsonNull   => sourcesContent.addOne(null)
                case _          => sourcesContent.addOne(null)
              }
            case _ =>
          }
        case "names" =>
          value match {
            case JsonArr(items) =>
              items.foreach {
                case JsonStr(s) => names.addOne(s)
                case _          =>
              }
            case _ =>
          }
        case "mappings" =>
          value match {
            case JsonStr(s) => mappings = s
            case _          =>
          }
        case _ => // ignore unknown fields (e.g. "sections")
      }
    }

    SourceMapData(
      version = version,
      file = file,
      sourceRoot = sourceRoot,
      sources = sources,
      sourcesContent = sourcesContent,
      names = names,
      mappings = mappings
    )
  }

  // --- JSON value model + recursive-descent reader ---

  sealed private trait JsonValue
  private case object JsonNull extends JsonValue
  final private case class JsonStr(value: String) extends JsonValue
  final private case class JsonNum(value: Double) extends JsonValue
  final private case class JsonBool(value: Boolean) extends JsonValue
  final private case class JsonArr(items: List[JsonValue]) extends JsonValue
  final private case class JsonObj(fields: List[(String, JsonValue)]) extends JsonValue

  final private class JsonReader(s: String) {
    private var i = 0

    def atEnd: Boolean = i >= s.length

    def skipWs(): Unit =
      while (i < s.length && isWs(s.charAt(i))) i += 1

    private def isWs(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    private def fail(msg: String): Nothing =
      throw new SourceMapParseError(s"$msg at offset $i")

    private def expect(c: Char): Unit = {
      if (i >= s.length || s.charAt(i) != c) fail(s"expected '$c'")
      i += 1
    }

    def readObject(): List[(String, JsonValue)] = {
      expect('{')
      skipWs()
      val fields = scala.collection.mutable.ListBuffer.empty[(String, JsonValue)]
      if (i < s.length && s.charAt(i) == '}') {
        // empty object `{}` — consume the close brace, leave `fields` empty.
        i += 1
      } else {
        var more = true
        while (more) {
          skipWs()
          val key = readString()
          skipWs()
          expect(':')
          skipWs()
          val value = readValue()
          fields.addOne(key -> value)
          skipWs()
          if (i < s.length && s.charAt(i) == ',') {
            i += 1
          } else {
            expect('}')
            more = false
          }
        }
      }
      fields.toList
    }

    private def readArray(): List[JsonValue] = {
      expect('[')
      skipWs()
      val items = scala.collection.mutable.ListBuffer.empty[JsonValue]
      if (i < s.length && s.charAt(i) == ']') {
        // empty array `[]` — consume the close bracket, leave `items` empty.
        i += 1
      } else {
        var more = true
        while (more) {
          skipWs()
          items.addOne(readValue())
          skipWs()
          if (i < s.length && s.charAt(i) == ',') {
            i += 1
          } else {
            expect(']')
            more = false
          }
        }
      }
      items.toList
    }

    private def readValue(): JsonValue = {
      if (i >= s.length) fail("unexpected end of input")
      s.charAt(i) match {
        case '"'                                     => JsonStr(readString())
        case '{'                                     => JsonObj(readObject())
        case '['                                     => JsonArr(readArray())
        case 't'                                     => readLiteral("true"); JsonBool(true)
        case 'f'                                     => readLiteral("false"); JsonBool(false)
        case 'n'                                     => readLiteral("null"); JsonNull
        case c if c == '-' || (c >= '0' && c <= '9') => JsonNum(readNumber())
        case _                                       => fail("unexpected character")
      }
    }

    private def readLiteral(lit: String): Unit = {
      if (i + lit.length > s.length || s.substring(i, i + lit.length) != lit) fail(s"expected '$lit'")
      i += lit.length
    }

    private def readNumber(): Double = {
      val start = i
      if (i < s.length && s.charAt(i) == '-') i += 1
      while (
        i < s.length && {
          val c = s.charAt(i)
          (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-'
        }
      ) i += 1
      java.lang.Double.parseDouble(s.substring(start, i))
    }

    private def readString(): String = {
      expect('"')
      val sb = new StringBuilder
      boundary {
        while (true) {
          if (i >= s.length) fail("unterminated string")
          val c = s.charAt(i)
          i += 1
          if (c == '"') {
            boundary.break()
          } else if (c == '\\') {
            if (i >= s.length) fail("unterminated escape")
            val e = s.charAt(i)
            i += 1
            e match {
              case '"'  => sb.append('"')
              case '\\' => sb.append('\\')
              case '/'  => sb.append('/')
              case 'b'  => sb.append('\b')
              case 'f'  => sb.append('\f')
              case 'n'  => sb.append('\n')
              case 'r'  => sb.append('\r')
              case 't'  => sb.append('\t')
              case 'u'  =>
                if (i + 4 > s.length) fail("bad unicode escape")
                val hex = s.substring(i, i + 4)
                i += 4
                sb.append(Integer.parseInt(hex, 16).toChar)
              case _ => fail("bad escape")
            }
          } else {
            sb.append(c)
          }
        }
      }
      sb.toString()
    }
  }
}
