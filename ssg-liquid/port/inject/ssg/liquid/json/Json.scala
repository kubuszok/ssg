/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/Template.java (renderToObject(String), which reads a JSON
 *   object with jackson's ObjectMapper) and src/main/java/liqp/filters/Json.java (the `json`
 *   filter, which writes one)
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: jackson-databind is a JVM-only artifact, so reading and writing JSON is done here.
 *   Convention: RFC 8259, and where jackson leaves the standard its own behaviour — a non-finite
 *     number is written as a quoted name, an integer is bound to the narrowest of Integer, Long
 *     and BigInteger that holds it, and a fractional number is bound to Double.
 *   Idiom: values are JAVA's — java.util maps and lists, java.lang boxes — because that is what
 *     liqp's own code reads back out of them.
 */
package ssg
package liquid
package json

import java.util.{ ArrayList => JArrayList, HashMap => JHashMap, List => JList, Map => JMap }

/** Reads and writes the JSON that liqp reads and writes. */
object Json {

  /** Writes a value as JSON, the way `ObjectMapper.writeValueAsString` wrote it.
    *
    * Every value reaching here is java-shaped: the port bridges a retyped collection through
    * `JavaCollections.Reified.toJavaValue` at the call, because jackson was a reflective sink.
    */
  def write(value: Any): String = {
    val sb = new StringBuilder()
    writeTo(sb, value)
    sb.toString()
  }

  /** Reads a JSON object into the `java.util.HashMap` java asked `readValue` for.
    *
    * Throws on anything that is not a well-formed JSON object, which is what java's caller
    * catches to report `invalid json map`.
    */
  def readObject(text: String): JHashMap[String, Object] = {
    val reader = new Reader(text)
    reader.skipWhitespace()
    val value = reader.readValue()
    reader.skipWhitespace()
    if (!reader.atEnd) reader.fail("trailing content after the JSON value")
    value match {
      case m: JHashMap[?, ?] => m.asInstanceOf[JHashMap[String, Object]]
      case other             => throw new IllegalArgumentException("expected a JSON object, found " + describe(other))
    }
  }

  /** Reads any JSON value. */
  def read(text: String): Object = {
    val reader = new Reader(text)
    reader.skipWhitespace()
    val value = reader.readValue()
    reader.skipWhitespace()
    if (!reader.atEnd) reader.fail("trailing content after the JSON value")
    value
  }

  private def describe(value: Any): String =
    if (value == null) "null" else value.getClass.getName

  // --- writing ---

  private def writeTo(sb: StringBuilder, value: Any): Unit =
    value match {
      case null                       => sb.append("null")
      case b: java.lang.Boolean       => sb.append(if (b.booleanValue()) "true" else "false")
      case d: java.lang.Double        => writeDouble(sb, d.doubleValue())
      case f: java.lang.Float         => writeDouble(sb, f.doubleValue())
      case n: java.lang.Number        => sb.append(n.toString)
      case s: String                  => writeString(sb, s)
      case c: Char                    => writeString(sb, c.toString)
      case m: JMap[?, ?]              => writeMap(sb, m)
      case c: java.util.Collection[?] => writeIterator(sb, c.iterator())
      case a: Array[?]                => writeIterator(sb, java.util.Arrays.asList(a*).iterator())
      case cs: CharSequence           => writeString(sb, cs.toString)
      // A value jackson would have serialised by reading its fields. `convertValue` refuses that
      // for the same reason (`Json.refuseBeans`), and here the string form is the closest thing
      // the port can write without reflection.
      case other => writeString(sb, other.toString)
    }

  /** jackson writes a non-finite number as a QUOTED name, which is not JSON but is what it does
    * (`JsonGenerator.Feature.QUOTE_NON_NUMERIC_NUMBERS`, enabled by default).
    */
  private def writeDouble(sb: StringBuilder, d: Double): Unit =
    if (d.isNaN) sb.append("\"NaN\"")
    else if (d == Double.PositiveInfinity) sb.append("\"Infinity\"")
    else if (d == Double.NegativeInfinity) sb.append("\"-Infinity\"")
    else sb.append(java.lang.Double.toString(d))

  private def writeMap(sb: StringBuilder, map: JMap[?, ?]): Unit = {
    sb.append("{")
    val it    = map.entrySet().iterator()
    var first = true
    while (it.hasNext) {
      val entry = it.next()
      if (!first) sb.append(",")
      writeString(sb, String.valueOf(entry.getKey))
      sb.append(":")
      writeTo(sb, entry.getValue)
      first = false
    }
    sb.append("}")
  }

  private def writeIterator(sb: StringBuilder, it: java.util.Iterator[?]): Unit = {
    sb.append("[")
    var first = true
    while (it.hasNext) {
      if (!first) sb.append(",")
      writeTo(sb, it.next())
      first = false
    }
    sb.append("]")
  }

  private def writeString(sb: StringBuilder, s: String): Unit = {
    sb.append("\"")
    var i = 0
    while (i < s.length()) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case _    =>
          if (c < 0x20) sb.append("\\u").append(pad4(java.lang.Integer.toHexString(c.toInt)))
          else sb.append(c)
      }
      i += 1
    }
    sb.append("\"")
  }

  private def pad4(hex: String): String =
    if (hex.length >= 4) hex else "0" * (4 - hex.length) + hex

  // --- reading ---

  /** A recursive-descent JSON reader over one string. */
  private final class Reader(val text: String) {

    private var pos: Int = 0

    def atEnd: Boolean = pos >= text.length()

    def fail(what: String): Nothing =
      throw new IllegalArgumentException(what + " at index " + pos)

    def skipWhitespace(): Unit =
      while (pos < text.length() && isWhitespace(text.charAt(pos))) pos += 1

    private def isWhitespace(c: Char): Boolean =
      c == ' ' || c == '\t' || c == '\n' || c == '\r'

    private def peek(): Char =
      if (pos < text.length()) text.charAt(pos) else fail("unexpected end of input")

    private def expect(c: Char): Unit =
      if (pos < text.length() && text.charAt(pos) == c) pos += 1
      else fail("expected '" + c + "'")

    private def literal(word: String): Unit =
      if (text.regionMatches(pos, word, 0, word.length())) pos += word.length()
      else fail("expected '" + word + "'")

    def readValue(): Object = {
      skipWhitespace()
      peek() match {
        case '{' => readObjectValue()
        case '[' => readArray()
        case '"' => readString()
        case 't' => literal("true"); java.lang.Boolean.TRUE
        case 'f' => literal("false"); java.lang.Boolean.FALSE
        case 'n' => literal("null"); null
        case c   =>
          if (c == '-' || (c >= '0' && c <= '9')) readNumber()
          else fail("unexpected character '" + c + "'")
      }
    }

    private def readObjectValue(): JHashMap[String, Object] = {
      expect('{')
      val map = new JHashMap[String, Object]()
      skipWhitespace()
      if (peek() == '}') { pos += 1; return map }
      var more = true
      while (more) {
        skipWhitespace()
        val key = readString()
        skipWhitespace()
        expect(':')
        val value = readValue()
        map.put(key, value)
        skipWhitespace()
        peek() match {
          case ',' => pos += 1
          case '}' => pos += 1; more = false
          case c   => fail("expected ',' or '}', found '" + c + "'")
        }
      }
      map
    }

    private def readArray(): JList[Object] = {
      expect('[')
      val list = new JArrayList[Object]()
      skipWhitespace()
      if (peek() == ']') { pos += 1; return list }
      var more = true
      while (more) {
        list.add(readValue())
        skipWhitespace()
        peek() match {
          case ',' => pos += 1
          case ']' => pos += 1; more = false
          case c   => fail("expected ',' or ']', found '" + c + "'")
        }
      }
      list
    }

    private def readString(): String = {
      expect('"')
      val sb = new StringBuilder()
      scala.util.boundary {
      while (true) {
        if (pos >= text.length()) fail("unterminated string")
        val c = text.charAt(pos)
        pos += 1
        if (c == '"') scala.util.boundary.break()
        else if (c == '\\') {
          if (pos >= text.length()) fail("unterminated escape")
          val e = text.charAt(pos)
          pos += 1
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
              if (pos + 4 > text.length()) fail("truncated unicode escape")
              val hex = text.substring(pos, pos + 4)
              pos += 4
              sb.append(java.lang.Integer.parseInt(hex, 16).toChar)
            case other => fail("unknown escape '\\" + other + "'")
          }
        } else sb.append(c)
      }
      }
      sb.toString()
    }

    /** jackson binds an untyped integer to the narrowest of Integer, Long and BigInteger that
      * holds it, and an untyped fractional number to Double; liqp's own `LValue.asNumber` reads
      * any of them.
      */
    private def readNumber(): java.lang.Number = {
      val start = pos
      if (pos < text.length() && text.charAt(pos) == '-') pos += 1
      while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos += 1
      var fractional = false
      if (pos < text.length() && text.charAt(pos) == '.') {
        fractional = true
        pos += 1
        while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos += 1
      }
      if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
        fractional = true
        pos += 1
        if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos += 1
        while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos += 1
      }
      val literalText = text.substring(start, pos)
      if (literalText.isEmpty || literalText == "-") fail("expected a number")
      if (fractional) java.lang.Double.valueOf(literalText)
      else
        try java.lang.Integer.valueOf(literalText)
        catch {
          case _: NumberFormatException =>
            try java.lang.Long.valueOf(literalText)
            catch { case _: NumberFormatException => new java.math.BigInteger(literalText) }
        }
    }
  }
}
