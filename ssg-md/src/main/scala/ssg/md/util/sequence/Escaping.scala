/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/Escaping.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.misc.CharPredicate

import java.nio.charset.StandardCharsets
import java.util.Random
import java.util.regex.Pattern

/** Character escaping utilities for markdown processing.
  *
  * NOTE: Methods that reference BasedSequence, ReplacedTextMapper, PrefixedSubSequence, Html5Entities, and Utils are commented out or omitted since those types are not yet ported. They will be added
  * when those forward references become available.
  */
object Escaping {

  // pure chars not for pattern
  val ESCAPABLE_CHARS: String = "\"#$%&'()*+,./:;<=>?@[]\\^_`{|}~-"

  val ESCAPABLE: String = "[!" +
    ESCAPABLE_CHARS.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]") +
    "]"

  private val ENTITY: String = "&(?:#x[a-f0-9]{1,8}|#[0-9]{1,8}|[a-z][a-z0-9]{1,31});"

  private val BACKSLASH_ONLY: Pattern = Pattern.compile("[\\\\]")

  private val ESCAPED_CHAR: Pattern =
    Pattern.compile("\\\\" + ESCAPABLE, Pattern.CASE_INSENSITIVE)

  private val BACKSLASH_OR_AMP: Pattern = Pattern.compile("[\\\\&]")

  private val AMP_ONLY: Pattern = Pattern.compile("[\\&]")

  private val ENTITY_OR_ESCAPED_CHAR: Pattern =
    Pattern.compile("\\\\" + ESCAPABLE + '|' + ENTITY, Pattern.CASE_INSENSITIVE)

  private val ENTITY_ONLY: Pattern =
    Pattern.compile(ENTITY, Pattern.CASE_INSENSITIVE)

  private val XML_SPECIAL: String = "[&<>\"]"

  private val XML_SPECIAL_RE: Pattern = Pattern.compile(XML_SPECIAL)

  private val XML_SPECIAL_OR_ENTITY: Pattern =
    Pattern.compile(ENTITY + '|' + XML_SPECIAL, Pattern.CASE_INSENSITIVE)

  // From RFC 3986 (see "reserved", "unreserved") except don't escape '[' or ']' to be compatible with JS encodeURI
  private val ESCAPE_IN_URI: Pattern =
    Pattern.compile("(%[a-fA-F0-9]{0,2}|[^:/?#@!$&'()*+,;=a-zA-Z0-9\\-._~])")

  private val ESCAPE_URI_DECODE: Pattern =
    Pattern.compile("(%[a-fA-F0-9]{2})")

  val HEX_DIGITS: Array[Char] =
    Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  @annotation.nowarn("msg=unused private member") // used by BasedSequence overloads not yet ported
  private val WHITESPACE: Pattern = Pattern.compile("[ \t\r\n]+")

  @annotation.nowarn("msg=unused private member") // used by BasedSequence overloads not yet ported
  private val COLLAPSE_WHITESPACE: Pattern = Pattern.compile("[ \t]{2,}")

  val AMP_BACKSLASH_SET: CharPredicate = CharPredicate.anyOf('\\', '&')

  // --- String-based Replacer trait and implementations ---

  /** Simplified Replacer that only handles String-based replacement (no BasedSequence). */
  private trait StringReplacer {
    def replace(s: String, sb: StringBuilder): Unit
  }

  private val UNSAFE_CHAR_REPLACER: StringReplacer = new StringReplacer {
    override def replace(s: String, sb: StringBuilder): Unit =
      s match {
        case "&"  => sb.append("&amp;")
        case "<"  => sb.append("&lt;")
        case ">"  => sb.append("&gt;")
        case "\"" => sb.append("&quot;")
        case _    => sb.append(s)
      }
  }

  @annotation.nowarn("msg=unused private member") // used by BasedSequence overloads not yet ported
  private val COLLAPSE_WHITESPACE_REPLACER: StringReplacer = new StringReplacer {
    override def replace(s: String, sb: StringBuilder): Unit =
      sb.append(" ")
  }

  // NOTE: UNESCAPE_REPLACER, ENTITY_REPLACER, REMOVE_REPLACER require Html5Entities (not yet ported)
  // They are represented as stubs that will be filled in when Html5Entities is available.

  private val URL_ENCODE_REPLACER: StringReplacer = new StringReplacer {
    override def replace(s: String, sb: StringBuilder): Unit =
      if (s.startsWith("%")) {
        if (s.length == 3) {
          // Already percent-encoded, preserve
          sb.append(s)
        } else {
          // %25 is the percent-encoding for %
          sb.append("%25")
          sb.underlying.append(s: CharSequence, 1, s.length)
        }
      } else {
        val bytes = s.getBytes(StandardCharsets.UTF_8)
        for (b <- bytes) {
          sb.append('%')
          sb.append(HEX_DIGITS((b >> 4) & 0xf))
          sb.append(HEX_DIGITS(b & 0xf))
        }
      }
  }

  def escapeHtml(s: CharSequence, preserveEntities: Boolean): String = {
    val p = if (preserveEntities) XML_SPECIAL_OR_ENTITY else XML_SPECIAL_RE
    replaceAll(p, s, UNSAFE_CHAR_REPLACER)
  }

  /** Replace entities and backslash escapes with literal characters.
    *
    * @param s
    *   string to un-escape
    * @return
    *   un-escaped string
    *
    * NOTE: Full implementation requires Html5Entities. This is a simplified version that handles backslash escapes only.
    */
  def unescapeString(s: CharSequence): String =
    if (BACKSLASH_OR_AMP.matcher(s).find()) {
      replaceAll(
        ENTITY_OR_ESCAPED_CHAR,
        s,
        new StringReplacer {
          override def replace(s: String, sb: StringBuilder): Unit =
            if (s.charAt(0) == '\\') {
              sb.underlying.append(s: CharSequence, 1, s.length)
            } else {
              sb.append(Html5Entities.entityToString(s))
            }
        }
      )
    } else {
      String.valueOf(s)
    }

  /** Replace entities and backslash escapes with literal characters.
    *
    * @param s
    *   string to un-escape
    * @param unescapeEntities
    *   true if HTML entities are to be unescaped
    * @return
    *   un-escaped string
    */
  def unescapeString(s: CharSequence, unescapeEntities: Boolean): String = {
    val unescapeReplacer = new StringReplacer {
      override def replace(s: String, sb: StringBuilder): Unit =
        if (s.charAt(0) == '\\') {
          sb.underlying.append(s: CharSequence, 1, s.length)
        } else {
          sb.append(Html5Entities.entityToString(s))
        }
    }

    if (unescapeEntities) {
      if (BACKSLASH_OR_AMP.matcher(s).find()) {
        replaceAll(ESCAPED_CHAR, s, unescapeReplacer)
      } else {
        String.valueOf(s)
      }
    } else {
      if (BACKSLASH_ONLY.matcher(s).find()) {
        replaceAll(ENTITY_OR_ESCAPED_CHAR, s, unescapeReplacer)
      } else {
        String.valueOf(s)
      }
    }
  }

  /** Replace entities and backslash escapes with literal characters.
    *
    * @param s
    *   string to un-escape
    * @return
    *   un-escaped string
    */
  def unescapeHtml(s: CharSequence): String =
    if (AMP_ONLY.matcher(s).find()) {
      replaceAll(
        ENTITY_ONLY,
        s,
        new StringReplacer {
          override def replace(s: String, sb: StringBuilder): Unit =
            sb.append(Html5Entities.entityToString(s))
        }
      )
    } else {
      String.valueOf(s)
    }

  /** Normalize eol: embedded \r and \r\n are converted to \n
    *
    * Append EOL sequence if sequence does not already end in EOL
    *
    * @param s
    *   sequence to convert
    * @return
    *   converted sequence
    */
  def normalizeEndWithEOL(s: CharSequence): String = normalizeEOL(s, endWithEOL = true)

  /** Normalize eol: embedded \r and \r\n are converted to \n
    *
    * @param s
    *   sequence to convert
    * @return
    *   converted sequence
    */
  def normalizeEOL(s: CharSequence): String = normalizeEOL(s, endWithEOL = false)

  /** Normalize eol: embedded \r and \r\n are converted to \n
    *
    * @param s
    *   sequence to convert
    * @param endWithEOL
    *   true if an EOL is to be appended to the end of the sequence if not already ending with one.
    * @return
    *   converted sequence
    */
  def normalizeEOL(s: CharSequence, endWithEOL: Boolean): String = {
    val sb     = new StringBuilder(s.length())
    val iMax   = s.length()
    var hadCR  = false
    var hadEOL = false

    var i = 0
    while (i < iMax) {
      val c = s.charAt(i)
      if (c == '\r') {
        hadCR = true
      } else if (c == '\n') {
        sb.append("\n")
        hadCR = false
        hadEOL = true
      } else {
        if (hadCR) sb.append('\n')
        sb.append(c)
        hadCR = false
        hadEOL = false
      }
      i += 1
    }
    if (endWithEOL && !hadEOL) sb.append('\n')
    sb.toString()
  }

  /** @param s
    *   string to encode
    * @return
    *   encoded string
    */
  def percentEncodeUrl(s: CharSequence): String =
    replaceAll(ESCAPE_IN_URI, s, URL_ENCODE_REPLACER)

  /** @param s
    *   string to encode
    * @return
    *   encoded string
    */
  def percentDecodeUrl(s: CharSequence): String =
    replaceAll(
      ESCAPE_URI_DECODE,
      s,
      new StringReplacer {
        override def replace(s: String, sb: StringBuilder): Unit = {
          val urlDecoded = ssg.md.util.misc.Utils.urlDecode(Nullable(s), Nullable.empty)
          sb.append(urlDecoded)
        }
      }
    )

  /** Normalize the link reference id
    *
    * @param s
    *   sequence containing the link reference id
    * @param changeCase
    *   if true then reference will be converted to lowercase
    * @return
    *   normalized link reference id
    */
  def normalizeReference(s: CharSequence, changeCase: Boolean): String =
    if (changeCase) Escaping.collapseWhitespace(s.toString, trim = true).toLowerCase
    else Escaping.collapseWhitespace(s.toString, trim = true)

  private def encode(c: Char): Nullable[String] =
    c match {
      case '&'  => Nullable("&amp;")
      case '<'  => Nullable("&lt;")
      case '>'  => Nullable("&gt;")
      case '"'  => Nullable("&quot;")
      case '\'' => Nullable("&#39;")
      case _    => Nullable.empty
    }

  private var random: Random = new Random(0x2626)

  /** e-mail obfuscation from pegdown
    *
    * @param email
    *   e-mail url
    * @param randomize
    *   true to randomize, false for testing
    * @return
    *   obfuscated e-mail url
    */
  def obfuscate(email: String, randomize: Boolean): String = {
    if (!randomize) random = new Random(0)

    val sb = new StringBuilder()
    var i  = 0
    while (i < email.length) {
      val c = email.charAt(i)
      random.nextInt(5) match {
        case 0 | 1 =>
          sb.append("&#").append(c.toInt).append(';')
        case 2 | 3 =>
          sb.append("&#x").append(Integer.toHexString(c.toInt)).append(';')
        case 4 =>
          val encoded = encode(c)
          if (encoded.isDefined) sb.append(encoded.get)
          else sb.append(c)
        case _ => // unreachable
      }
      i += 1
    }
    sb.toString()
  }

  /** Get a normalized the link reference id from reference characters
    *
    * Will remove leading ![ or [ and trailing ], collapse multiple whitespaces to a space and optionally convert the id to lowercase.
    *
    * @param s
    *   sequence containing the link reference id
    * @param changeCase
    *   if true then reference will be converted to lowercase
    * @return
    *   normalized link reference id
    */
  def normalizeReferenceChars(s: CharSequence, changeCase: Boolean): String =
    // Strip '[' and ']', then trim and convert to lowercase
    if (s.length > 1) {
      val stripEnd   = if (s.charAt(s.length - 1) == ':') 2 else 1
      val stripStart = if (s.charAt(0) == '!') 2 else 1
      normalizeReference(s.subSequence(stripStart, s.length - stripEnd), changeCase)
    } else {
      String.valueOf(s)
    }

  /** Collapse regions of multiple white spaces to a single space
    *
    * @param s
    *   sequence to process
    * @param trim
    *   true if the sequence should also be trimmed
    * @return
    *   processed sequence
    */
  def collapseWhitespace(s: CharSequence, trim: Boolean): String = {
    val sb       = new StringBuilder(s.length())
    val iMax     = s.length()
    var hadSpace = false

    var i = 0
    while (i < iMax) {
      val c = s.charAt(i)
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        hadSpace = true
      } else {
        if (hadSpace && (!trim || sb.length() > 0)) sb.append(' ')
        sb.append(c)
        hadSpace = false
      }
      i += 1
    }
    if (hadSpace && !trim) sb.append(' ')
    sb.toString()
  }

  private def replaceAll(p: Pattern, s: CharSequence, replacer: StringReplacer): String = {
    val matcher = p.matcher(s)

    if (!matcher.find()) {
      String.valueOf(s)
    } else {
      val sb      = new StringBuilder(s.length() + 16)
      var lastEnd = 0
      var found   = true
      while (found) {
        sb.underlying.append(s, lastEnd, matcher.start())
        replacer.replace(matcher.group(), sb)
        lastEnd = matcher.end()
        found = matcher.find()
      }

      if (lastEnd != s.length()) {
        sb.underlying.append(s, lastEnd, s.length())
      }
      sb.toString()
    }
  }

  // --- BasedSequence+ReplacedTextMapper overloads ---

  /** Full Replacer trait handling both String and BasedSequence replacements. */
  private trait Replacer {
    def replace(s:        String, sb:                StringBuilder):                                      Unit
    def replace(original: BasedSequence, startIndex: Int, endIndex: Int, textMapper: ReplacedTextMapper): Unit
  }

  private val REMOVE_REPLACER_FULL: Replacer = new Replacer {
    override def replace(s: String, sb: StringBuilder): Unit = {
      // remove — append nothing
    }

    override def replace(original: BasedSequence, startIndex: Int, endIndex: Int, textMapper: ReplacedTextMapper): Unit =
      textMapper.addReplacedText(startIndex, endIndex, original.subSequence(endIndex, endIndex))
  }

  private val ENTITY_REPLACER_FULL: Replacer = new Replacer {
    override def replace(s: String, sb: StringBuilder): Unit =
      sb.append(Html5Entities.entityToString(s))

    override def replace(original: BasedSequence, startIndex: Int, endIndex: Int, textMapper: ReplacedTextMapper): Unit =
      textMapper.addReplacedText(startIndex, endIndex, Html5Entities.entityToSequence(original.subSequence(startIndex, endIndex)))
  }

  private val UNESCAPE_REPLACER_FULL: Replacer = new Replacer {
    override def replace(s: String, sb: StringBuilder): Unit =
      if (s.charAt(0) == '\\') {
        sb.underlying.append(s: CharSequence, 1, s.length)
      } else {
        // Html5Entities.entityToString(s)
        sb.append(Html5Entities.entityToString(s))
      }

    override def replace(original: BasedSequence, startIndex: Int, endIndex: Int, textMapper: ReplacedTextMapper): Unit =
      if (original.charAt(startIndex) == '\\') {
        textMapper.addReplacedText(startIndex, endIndex, original.subSequence(startIndex + 1, endIndex))
      } else {
        textMapper.addReplacedText(startIndex, endIndex, Html5Entities.entityToSequence(original.subSequence(startIndex, endIndex)))
      }
  }

  private def replaceAll(p: java.util.regex.Pattern, s: BasedSequence, replacer: Replacer, textMapper: ReplacedTextMapper): BasedSequence =
    replaceAll(p, s, 0, s.length(), replacer, textMapper)

  private def replaceAll(p: java.util.regex.Pattern, s: BasedSequence, startOffset: Int, endOffset: Int, replacer: Replacer, textMapper: ReplacedTextMapper): BasedSequence = {
    val matcher = p.matcher(s)
    matcher.region(startOffset, endOffset)

    if (textMapper.isModified) {
      textMapper.startNestedReplacement(s)
    }

    if (!matcher.find()) {
      textMapper.addOriginalText(0, s.length())
      s
    } else {
      var lastEnd   = 0
      var continue_ = true
      while (continue_) {
        textMapper.addOriginalText(lastEnd, matcher.start())
        replacer.replace(s, matcher.start(), matcher.end(), textMapper)
        lastEnd = matcher.end()
        continue_ = matcher.find()
      }

      if (lastEnd < s.length()) {
        textMapper.addOriginalText(lastEnd, s.length())
      }

      textMapper.replacedSequence
    }
  }

  private def replaceAll(p: java.util.regex.Pattern, s: BasedSequence, ranges: java.util.List[Range], replacer: Replacer, textMapper: ReplacedTextMapper): BasedSequence = {
    val matcher = p.matcher(s)

    if (textMapper.isModified) {
      textMapper.startNestedReplacement(s)
    }

    var lastEnd = 0

    val it = ranges.iterator()
    while (it.hasNext) {
      val range = it.next()
      val start = ssg.md.util.misc.Utils.rangeLimit(range.start, lastEnd, s.length())
      val end   = ssg.md.util.misc.Utils.rangeLimit(range.end, start, s.length())
      matcher.region(start, end)

      while (matcher.find()) {
        textMapper.addOriginalText(lastEnd, matcher.start())
        replacer.replace(s, matcher.start(), matcher.end(), textMapper)
        lastEnd = matcher.end()
      }
    }

    if (lastEnd < s.length()) {
      textMapper.addOriginalText(lastEnd, s.length())
    }

    textMapper.replacedSequence
  }

  /** Replace entities and backslash escapes with literal characters, tracking replacements via the given [[ReplacedTextMapper]].
    */
  def unescape(s: BasedSequence, textMapper: ReplacedTextMapper): BasedSequence = {
    val indexOfAny = s.indexOfAny(AMP_BACKSLASH_SET)
    if (indexOfAny != -1) {
      replaceAll(ENTITY_OR_ESCAPED_CHAR, s, UNESCAPE_REPLACER_FULL, textMapper)
    } else {
      s
    }
  }

  /** Replace entities in HTML entity ranges, tracking replacements via the given [[ReplacedTextMapper]].
    */
  def unescapeHtml(s: BasedSequence, ranges: java.util.List[Range], textMapper: ReplacedTextMapper): BasedSequence = {
    val indexOfAny = s.indexOf('&')
    if (indexOfAny != -1) {
      replaceAll(ENTITY_ONLY, s, ranges, ENTITY_REPLACER_FULL, textMapper)
    } else {
      s
    }
  }

  /** Replace entities and backslash escapes, tracking replacements via the given [[ReplacedTextMapper]].
    */
  def unescapeHtml(s: BasedSequence, textMapper: ReplacedTextMapper): BasedSequence = {
    val indexOfAny = s.indexOf('&')
    if (indexOfAny != -1) {
      replaceAll(ENTITY_ONLY, s, ENTITY_REPLACER_FULL, textMapper)
    } else {
      s
    }
  }

  /** Remove all occurrences of a string from the sequence, tracking replacements via the given [[ReplacedTextMapper]].
    */
  def removeAll(s: BasedSequence, remove: CharSequence, textMapper: ReplacedTextMapper): BasedSequence = {
    val indexOf = s.indexOf(remove)
    if (indexOf != -1) {
      // Cross-platform: \Q...\E not supported on Scala Native re2
      replaceAll(Pattern.compile(RegexCompat.regexEscape(remove.toString)), s, REMOVE_REPLACER_FULL, textMapper)
    } else {
      s
    }
  }

  /** Normalize eol: embedded \r and \r\n are converted to \n, tracking replacements via the given [[ReplacedTextMapper]].
    */
  def normalizeEOL(s: BasedSequence, textMapper: ReplacedTextMapper): BasedSequence =
    throw new UnsupportedOperationException("Escaping.normalizeEOL(BasedSequence, ReplacedTextMapper) not yet ported")

  /** Normalize eol and ensure sequence ends with EOL, tracking replacements via the given [[ReplacedTextMapper]].
    */
  def normalizeEndWithEOL(s: BasedSequence, textMapper: ReplacedTextMapper): BasedSequence =
    throw new UnsupportedOperationException("Escaping.normalizeEndWithEOL(BasedSequence, ReplacedTextMapper) not yet ported")
}
