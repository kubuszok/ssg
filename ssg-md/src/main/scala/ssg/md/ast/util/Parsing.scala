/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/Parsing.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast
package util

import ssg.md.parser.Parser
import ssg.md.util.data.DataHolder
import ssg.md.util.format.TableFormatOptions
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.Escaping
import ssg.md.util.sequence.SequenceUtils

import java.{ util => ju }
import java.util.function.{ Function => JFunction }
import java.util.regex.Pattern

import scala.language.implicitConversions

class Parsing(val options: DataHolder) {

  import Parsing._

  val CODE_BLOCK_INDENT: Int = Parser.CODE_BLOCK_INDENT.get(options)

  private val patternTypeFlags = new PatternTypeFlags(options)

  val intellijDummyIdentifier:         Boolean = java.lang.Boolean.TRUE == patternTypeFlags.intellijDummyIdentifier
  val htmlForTranslator:               Boolean = java.lang.Boolean.TRUE == patternTypeFlags.htmlForTranslator
  val translationHtmlInlineTagPattern: String  = patternTypeFlags.translationHtmlInlineTagPattern
  val translationAutolinkTagPattern:   String  = patternTypeFlags.translationAutolinkTagPattern
  val spaceInLinkUrl:                  Boolean = java.lang.Boolean.TRUE == patternTypeFlags.spaceInLinkUrl
  val parseJekyllMacroInLinkUrl:       Boolean = java.lang.Boolean.TRUE == patternTypeFlags.parseJekyllMacroInLinkUrl
  val itemPrefixChars:                 String  = patternTypeFlags.itemPrefixChars
  val listsItemMarkerSpace:            Boolean = java.lang.Boolean.TRUE == patternTypeFlags.listsItemMarkerSpace
  val listsOrderedItemDotOnly:         Boolean = java.lang.Boolean.TRUE == patternTypeFlags.listsOrderedItemDotOnly
  val allowNameSpace:                  Boolean = java.lang.Boolean.TRUE == patternTypeFlags.allowNameSpace

  val EOL:               String  = ST_EOL
  val ESCAPED_CHAR:      String  = ST_ESCAPED_CHAR
  val LINK_LABEL:        Pattern = ST_LINK_LABEL
  val LINK_TITLE_STRING: String  = ST_LINK_TITLE_STRING
  val LINK_TITLE:        Pattern = ST_LINK_TITLE

  val HTMLCOMMENT:           String = ST_HTMLCOMMENT
  val PROCESSINGINSTRUCTION: String = ST_PROCESSINGINSTRUCTION
  val CDATA:                 String = ST_CDATA
  val SINGLEQUOTEDVALUE:     String = ST_SINGLEQUOTEDVALUE
  val DOUBLEQUOTEDVALUE:     String = ST_DOUBLEQUOTEDVALUE

  val ASCII_PUNCTUATION:       String = ST_ASCII_PUNCTUATION
  val ASCII_OPEN_PUNCTUATION:  String = ST_ASCII_OPEN_PUNCTUATION
  val ASCII_CLOSE_PUNCTUATION: String = ST_ASCII_CLOSE_PUNCTUATION

  val PUNCTUATION:            Pattern = ST_PUNCTUATION
  val PUNCTUATION_OPEN:       Pattern = ST_PUNCTUATION_OPEN
  val PUNCTUATION_CLOSE:      Pattern = ST_PUNCTUATION_CLOSE
  val PUNCTUATION_ONLY:       Pattern = ST_PUNCTUATION_ONLY
  val PUNCTUATION_OPEN_ONLY:  Pattern = ST_PUNCTUATION_OPEN_ONLY
  val PUNCTUATION_CLOSE_ONLY: Pattern = ST_PUNCTUATION_CLOSE_ONLY

  val ESCAPABLE:               Pattern = ST_ESCAPABLE
  val TICKS:                   Pattern = ST_TICKS
  val TICKS_HERE:              Pattern = ST_TICKS_HERE
  val SPNL:                    Pattern = ST_SPNL
  val SPNL_URL:                Pattern = ST_SPNL_URL
  val SPNI:                    Pattern = ST_SPNI
  val SP:                      Pattern = ST_SP
  val REST_OF_LINE:            Pattern = ST_REST_OF_LINE
  val UNICODE_WHITESPACE_CHAR: Pattern = ST_UNICODE_WHITESPACE_CHAR
  val WHITESPACE:              Pattern = ST_WHITESPACE
  val FINAL_SPACE:             Pattern = ST_FINAL_SPACE
  val LINE_END:                Pattern = ST_LINE_END

  // IDI-dependent fields
  val ADDITIONAL_CHARS:       String = if (intellijDummyIdentifier) ST_ADDITIONAL_CHARS_IDI else ST_ADDITIONAL_CHARS_NO_IDI
  val EXCLUDED_0_TO_SPACE:    String = if (intellijDummyIdentifier) ST_EXCLUDED_0_TO_SPACE_IDI else ST_EXCLUDED_0_TO_SPACE_NO_IDI
  val REG_CHAR:               String = if (intellijDummyIdentifier) ST_REG_CHAR_IDI else ST_REG_CHAR_NO_IDI
  val REG_CHAR_PARENS:        String = if (intellijDummyIdentifier) ST_REG_CHAR_PARENS_IDI else ST_REG_CHAR_PARENS_NO_IDI
  val REG_CHAR_SP:            String = if (intellijDummyIdentifier) ST_REG_CHAR_SP_IDI else ST_REG_CHAR_SP_NO_IDI
  val REG_CHAR_SP_PARENS:     String = if (intellijDummyIdentifier) ST_REG_CHAR_SP_PARENS_IDI else ST_REG_CHAR_SP_PARENS_NO_IDI
  val IN_PARENS_NOSP:         String = if (intellijDummyIdentifier) ST_IN_PARENS_NOSP_IDI else ST_IN_PARENS_NOSP_NO_IDI
  val IN_PARENS_W_SP:         String = if (intellijDummyIdentifier) ST_IN_PARENS_W_SP_IDI else ST_IN_PARENS_W_SP_NO_IDI
  val IN_MATCHED_PARENS_NOSP: String = if (intellijDummyIdentifier) ST_IN_MATCHED_PARENS_NOSP_IDI else ST_IN_MATCHED_PARENS_NOSP_NO_IDI
  val IN_MATCHED_PARENS_W_SP: String = if (intellijDummyIdentifier) ST_IN_MATCHED_PARENS_W_SP_IDI else ST_IN_MATCHED_PARENS_W_SP_NO_IDI
  val IN_BRACES_W_SP:         String = if (intellijDummyIdentifier) ST_IN_BRACES_W_SP_IDI else ST_IN_BRACES_W_SP_NO_IDI
  val DECLARATION:            String = if (intellijDummyIdentifier) ST_DECLARATION_IDI else ST_DECLARATION_NO_IDI
  val ENTITY:                 String = if (intellijDummyIdentifier) ST_ENTITY_IDI else ST_ENTITY_NO_IDI
  val TAGNAME:                String = if (intellijDummyIdentifier) ST_TAGNAME_IDI else ST_TAGNAME_NO_IDI
  val ATTRIBUTENAME:          String = if (intellijDummyIdentifier) ST_ATTRIBUTENAME_IDI else ST_ATTRIBUTENAME_NO_IDI
  val UNQUOTEDVALUE:          String = if (intellijDummyIdentifier) ST_UNQUOTEDVALUE_IDI else ST_UNQUOTEDVALUE_NO_IDI
  val ATTRIBUTEVALUE:         String = if (intellijDummyIdentifier) ST_ATTRIBUTEVALUE_IDI else ST_ATTRIBUTEVALUE_NO_IDI
  val ATTRIBUTEVALUESPEC:     String = if (intellijDummyIdentifier) ST_ATTRIBUTEVALUESPEC_IDI else ST_ATTRIBUTEVALUESPEC_NO_IDI
  val ATTRIBUTE:              String = if (intellijDummyIdentifier) ST_ATTRIBUTE_IDI else ST_ATTRIBUTE_NO_IDI
  val OPENTAG:                String =
    if (intellijDummyIdentifier) {
      if (allowNameSpace) ST_NS_OPENTAG_IDI else ST_OPENTAG_IDI
    } else {
      if (allowNameSpace) ST_NS_OPENTAG_NO_IDI else ST_OPENTAG_NO_IDI
    }
  val CLOSETAG: String =
    if (intellijDummyIdentifier) {
      if (allowNameSpace) ST_NS_CLOSETAG_IDI else ST_CLOSETAG_IDI
    } else {
      if (allowNameSpace) ST_NS_CLOSETAG_NO_IDI else ST_CLOSETAG_NO_IDI
    }

  // init flag based patterns
  val LINK_DESTINATION_ANGLES: Pattern = if (spaceInLinkUrl) ST_LINK_DESTINATION_ANGLES_SPC else ST_LINK_DESTINATION_ANGLES_NO_SPC
  val ENTITY_HERE:             Pattern = if (intellijDummyIdentifier) ST_ENTITY_HERE_IDI else ST_ENTITY_HERE_NO_IDI

  // init dynamic patterns (synchronized on cachedPatterns)
  val LINK_DESTINATION_MATCHED_PARENS_NOSP: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "LINK_DESTINATION_MATCHED_PARENS_NOSP",
      patternTypeFlags.withJekyllMacroInLinkUrl(),
      entry =>
        Pattern.compile(
          "^(?:" + (if (parseJekyllMacroInLinkUrl) IN_BRACES_W_SP + "|" else "") +
            (REG_CHAR + "|") +
            ESCAPED_CHAR + "|\\\\|\\(|\\))*"
        )
    )
  }

  val LINK_DESTINATION: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "LINK_DESTINATION",
      patternTypeFlags.withJekyllMacroSpaceInLinkUrl(),
      entry =>
        Pattern.compile(
          "^(?:" + (if (parseJekyllMacroInLinkUrl) IN_BRACES_W_SP + "|" else "") +
            (if (spaceInLinkUrl) "(?:" + REG_CHAR_SP + ")|" else REG_CHAR + "|") +
            ESCAPED_CHAR + "|\\\\|" + (if (spaceInLinkUrl) IN_PARENS_W_SP else IN_PARENS_NOSP) + ")*"
        )
    )
  }

  val LINK_DESTINATION_MATCHED_PARENS: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "LINK_DESTINATION_MATCHED_PARENS",
      patternTypeFlags.withJekyllMacroSpaceInLinkUrl(),
      entry =>
        Pattern.compile(
          "^(?:" + (if (parseJekyllMacroInLinkUrl) IN_BRACES_W_SP + "|" else "") +
            (if (spaceInLinkUrl) "(?:" + REG_CHAR_SP + ")|" else REG_CHAR + "|") +
            ESCAPED_CHAR + "|\\\\|\\(|\\))*"
        )
    )
  }

  val EMAIL_AUTOLINK: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "EMAIL_AUTOLINK",
      patternTypeFlags.withHtmlTranslator(),
      entry =>
        Pattern.compile(
          "^<(" +
            "(?:[a-zA-Z0-9" + ADDITIONAL_CHARS + ".!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9" + ADDITIONAL_CHARS + "](?:[a-zA-Z0-9" + ADDITIONAL_CHARS + "-]{0,61}[a-zA-Z0-9" + ADDITIONAL_CHARS + "])?(?:\\.[a-zA-Z0-9" + ADDITIONAL_CHARS + "](?:[a-zA-Z0-9" + ADDITIONAL_CHARS + "-]{0,61}[a-zA-Z0-9" + ADDITIONAL_CHARS + "])?)*)" +
            (if (htmlForTranslator) "|(?:" + translationAutolinkTagPattern + ")" else "") +
            ")>"
        )
    )
  }

  val AUTOLINK: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "AUTOLINK",
      patternTypeFlags.withHtmlTranslator(),
      entry =>
        Pattern.compile(
          "^<(" +
            "(?:[a-zA-Z][a-zA-Z0-9" + ADDITIONAL_CHARS + ".+-]{1,31}:[^<>" + EXCLUDED_0_TO_SPACE + "]*)" +
            (if (htmlForTranslator) "|(?:" + translationAutolinkTagPattern + ")" else "") +
            ")>"
        )
    )
  }

  val WWW_AUTOLINK: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "WWW_AUTOLINK",
      patternTypeFlags.withHtmlTranslator(),
      entry =>
        Pattern.compile(
          "^<(" +
            "(?:w" + ADDITIONAL_CHARS + "?){3,3}\\.[^<>" + EXCLUDED_0_TO_SPACE + "]*" +
            (if (htmlForTranslator) "|(?:" + translationAutolinkTagPattern + ")" else "") +
            ")>"
        )
    )
  }

  val HTML_TAG: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "HTML_TAG",
      patternTypeFlags.withHtmlTranslator(),
      entry =>
        Pattern.compile(
          '^' + ("(?:" + OPENTAG + "|" + CLOSETAG + "|" + HTMLCOMMENT +
            "|" + PROCESSINGINSTRUCTION + "|" + DECLARATION + "|" + CDATA +
            (if (htmlForTranslator) "|<(?:" + translationHtmlInlineTagPattern + ")>|</(?:" + translationHtmlInlineTagPattern + ")>" else "") + ")"),
          Pattern.CASE_INSENSITIVE
        )
    )
  }

  val LIST_ITEM_MARKER: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "LIST_ITEM_MARKER",
      patternTypeFlags.withItemPrefixChars(),
      entry =>
        if (listsItemMarkerSpace) {
          if (listsOrderedItemDotOnly) {
            Pattern.compile("^([\\Q" + itemPrefixChars + "\\E])(?=[ \t])|^(\\d{1,9})([.])(?=[ \t])")
          } else {
            Pattern.compile("^([\\Q" + itemPrefixChars + "\\E])(?=[ \t])|^(\\d{1,9})([.)])(?=[ \t])")
          }
        } else {
          if (listsOrderedItemDotOnly) {
            Pattern.compile("^([\\Q" + itemPrefixChars + "\\E])(?= |\t|$)|^(\\d{1,9})([.])(?= |\t|$)")
          } else {
            Pattern.compile("^([\\Q" + itemPrefixChars + "\\E])(?= |\t|$)|^(\\d{1,9})([.)])(?= |\t|$)")
          }
        }
    )
  }

  val HTMLTAG: String = HTML_TAG.pattern()
}

object Parsing {

  val INTELLIJ_DUMMY_IDENTIFIER_CHAR: Char   = TableFormatOptions.INTELLIJ_DUMMY_IDENTIFIER_CHAR
  val INTELLIJ_DUMMY_IDENTIFIER:      String = TableFormatOptions.INTELLIJ_DUMMY_IDENTIFIER

  //    final public static String XML_NAMESPACE_START = "[_A-Za-z]";
  //    final public static String XML_NAMESPACE_CHAR = XML_NAME_SPACE_START + "|-|.|[0-9]";
  val XML_NAMESPACE_START: String =
    "[_A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD]" // excluded  [#x10000-#xEFFFF]
  val XML_NAMESPACE_CHAR: String = XML_NAMESPACE_START + "|[.0-9\u00B7\u0300-\u036F\u203F-\u2040-]"
  val XML_NAMESPACE:      String = "(?:(?:" + XML_NAMESPACE_START + ")(?:" + XML_NAMESPACE_CHAR + ")*:)?"

  private val ST_EOL               = "(?:\r\n|\r|\n)"
  private val ST_ESCAPED_CHAR      = "\\\\" + Escaping.ESCAPABLE
  private val ST_LINK_LABEL        = Pattern.compile("^\\[(?:[^\\\\\\[\\]]|" + ST_ESCAPED_CHAR + "|\\\\){0,999}\\]")
  private val ST_LINK_TITLE_STRING = "(?:\"(" + ST_ESCAPED_CHAR + "|[^\"\\x00])*\"" +
    '|' +
    "'(" + ST_ESCAPED_CHAR + "|[^'\\x00])*'" +
    '|' +
    "\\((" + ST_ESCAPED_CHAR + "|[^)\\x00])*\\))"
  private val ST_LINK_TITLE = Pattern.compile("^" + ST_LINK_TITLE_STRING)

  private val ST_EXCLUDED_0_TO_SPACE_IDI     = "\u0000-\u001e\u0020"
  private val ST_EXCLUDED_0_TO_SPACE_NO_IDI  = "\u0000-\u0020"
  private val ST_ADDITIONAL_CHARS_IDI        = "\u001f"
  private val ST_ADDITIONAL_CHARS_NO_IDI     = ""
  private val ST_ADDITIONAL_CHARS_SET_IDI    = "[\u001f]"
  private val ST_ADDITIONAL_CHARS_SET_NO_IDI = ""

  val ST_HTMLCOMMENT:           String = "<!---->|<!--(?:-?[^>-])(?:-?[^-])*-->"
  val ST_PROCESSINGINSTRUCTION: String = "[<][?].*?[?][>]"
  val ST_CDATA:                 String = "<!\\[CDATA\\[[\\s\\S]*?\\]\\]>"
  val ST_SINGLEQUOTEDVALUE:     String = "'[^']*'"
  val ST_DOUBLEQUOTEDVALUE:     String = "\"[^\"]*\""

  private val ST_ASCII_PUNCTUATION       = "'!\"#\\$%&\\*\\+,\\-\\./:;=\\?@\\\\\\^_`\\|~"
  private val ST_ASCII_OPEN_PUNCTUATION  = "\\(<\\[\\{"
  private val ST_ASCII_CLOSE_PUNCTUATION = "\\)>\\]\\}"
  private val ST_PUNCTUATION             = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + ST_ASCII_OPEN_PUNCTUATION + ST_ASCII_CLOSE_PUNCTUATION + "\\p{Pc}\\p{Pd}\\p{Pe}\\p{Pf}\\p{Pi}\\p{Po}\\p{Ps}]"
  )
  private val ST_PUNCTUATION_OPEN = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + ST_ASCII_OPEN_PUNCTUATION + "]|[\\p{Pc}\\p{Pd}\\p{Pe}\\p{Pf}\\p{Pi}\\p{Po}\\p{Ps}]&&[^" + ST_ASCII_CLOSE_PUNCTUATION + "]"
  )
  private val ST_PUNCTUATION_CLOSE = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + ST_ASCII_CLOSE_PUNCTUATION + "]|[\\p{Pc}\\p{Pd}\\p{Pe}\\p{Pf}\\p{Pi}\\p{Po}\\p{Ps}]&&[^" + ST_ASCII_OPEN_PUNCTUATION + "]"
  )
  private val ST_PUNCTUATION_ONLY = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + "\\p{Pc}\\p{Pd}\\p{Pe}\\p{Pf}\\p{Pi}\\p{Po}\\p{Ps}]&&[^" + ST_ASCII_OPEN_PUNCTUATION + ST_ASCII_CLOSE_PUNCTUATION + "]"
  )
  private val ST_PUNCTUATION_OPEN_ONLY  = Pattern.compile("^[" + ST_ASCII_OPEN_PUNCTUATION + "]")
  private val ST_PUNCTUATION_CLOSE_ONLY = Pattern.compile("^[" + ST_ASCII_CLOSE_PUNCTUATION + "]")

  private val ST_ESCAPABLE                      = Pattern.compile('^' + Escaping.ESCAPABLE)
  private val ST_TICKS                          = Pattern.compile("`+")
  private val ST_TICKS_HERE                     = Pattern.compile("^`+")
  private val ST_SPNL                           = Pattern.compile("^(?:[ \t])*(?:" + ST_EOL + "(?:[ \t])*)?")
  private val ST_SPNL_URL                       = Pattern.compile("^(?:[ \t])*" + ST_EOL)
  private val ST_SPNI                           = Pattern.compile("^ {0,3}")
  private val ST_SP                             = Pattern.compile("^(?:[ \t])*")
  private val ST_REST_OF_LINE                   = Pattern.compile("^.*" + ST_EOL)
  private val ST_UNICODE_WHITESPACE_CHAR        = Pattern.compile("^[\\p{Zs}\t\r\n\f]")
  private val ST_WHITESPACE                     = Pattern.compile("\\s+")
  private val ST_FINAL_SPACE                    = Pattern.compile(" *$")
  private val ST_LINE_END                       = Pattern.compile("^[ \t]*(?:" + ST_EOL + "|$)")
  private val ST_LINK_DESTINATION_ANGLES_SPC    = Pattern.compile("^(?:[<](?:[^<> \\t\\n\\\\\\x00]" + '|' + ST_ESCAPED_CHAR + '|' + "\\\\| (?![\"']))*[>])")
  private val ST_LINK_DESTINATION_ANGLES_NO_SPC = Pattern.compile("^(?:[<](?:[^<> \\t\\n\\\\\\x00]" + '|' + ST_ESCAPED_CHAR + '|' + "\\\\)*[>])")

  // IntelliJDummyIdentifier dependent
  private val ST_TAGNAME_IDI    = "[A-Za-z" + ST_ADDITIONAL_CHARS_IDI + "][A-Za-z0-9" + ST_ADDITIONAL_CHARS_IDI + "-]*"
  private val ST_TAGNAME_NO_IDI = "[A-Za-z" + ST_ADDITIONAL_CHARS_NO_IDI + "][A-Za-z0-9" + ST_ADDITIONAL_CHARS_NO_IDI + "-]*"

  private val ST_UNQUOTEDVALUE_IDI    = "[^\"'=<>{}`" + ST_EXCLUDED_0_TO_SPACE_IDI + "]+"
  private val ST_UNQUOTEDVALUE_NO_IDI = "[^\"'=<>{}`" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]+"

  private val ST_ATTRIBUTENAME_IDI    = "[a-zA-Z" + ST_ADDITIONAL_CHARS_IDI + "_:][a-zA-Z0-9" + ST_ADDITIONAL_CHARS_IDI + ":._-]*"
  private val ST_ATTRIBUTENAME_NO_IDI = "[a-zA-Z" + ST_ADDITIONAL_CHARS_NO_IDI + "_:][a-zA-Z0-9" + ST_ADDITIONAL_CHARS_NO_IDI + ":._-]*"

  private val ST_ATTRIBUTEVALUE_IDI    = "(?:" + ST_UNQUOTEDVALUE_IDI + "|" + ST_SINGLEQUOTEDVALUE + "|" + ST_DOUBLEQUOTEDVALUE + ")"
  private val ST_ATTRIBUTEVALUE_NO_IDI = "(?:" + ST_UNQUOTEDVALUE_NO_IDI + "|" + ST_SINGLEQUOTEDVALUE + "|" + ST_DOUBLEQUOTEDVALUE + ")"

  private val ST_ATTRIBUTEVALUESPEC_IDI    = "(?:" + "\\s*=" + "\\s*" + ST_ATTRIBUTEVALUE_IDI + ")"
  private val ST_ATTRIBUTEVALUESPEC_NO_IDI = "(?:" + "\\s*=" + "\\s*" + ST_ATTRIBUTEVALUE_NO_IDI + ")"

  private val ST_CLOSETAG_IDI       = "</" + ST_TAGNAME_IDI + "\\s*[>]"
  private val ST_CLOSETAG_NO_IDI    = "</" + ST_TAGNAME_NO_IDI + "\\s*[>]"
  private val ST_NS_CLOSETAG_IDI    = "</" + XML_NAMESPACE + ST_TAGNAME_IDI + "\\s*[>]"
  private val ST_NS_CLOSETAG_NO_IDI = "</" + XML_NAMESPACE + ST_TAGNAME_NO_IDI + "\\s*[>]"

  private val ST_ATTRIBUTE_IDI    = "(?:" + "\\s+" + ST_ATTRIBUTENAME_IDI + ST_ATTRIBUTEVALUESPEC_IDI + "?)"
  private val ST_ATTRIBUTE_NO_IDI = "(?:" + "\\s+" + ST_ATTRIBUTENAME_NO_IDI + ST_ATTRIBUTEVALUESPEC_NO_IDI + "?)"

  private val ST_DECLARATION_IDI    = "<![A-Z" + ST_ADDITIONAL_CHARS_IDI + "]+\\s+[^>]*>"
  private val ST_DECLARATION_NO_IDI = "<![A-Z" + ST_ADDITIONAL_CHARS_NO_IDI + "]+\\s+[^>]*>"

  private val ST_ENTITY_IDI    = "&(?:#x[a-f0-9" + ST_ADDITIONAL_CHARS_IDI + "]{1,8}|#[0-9]{1,8}|[a-z" + ST_ADDITIONAL_CHARS_IDI + "][a-z0-9" + ST_ADDITIONAL_CHARS_IDI + "]{1,31});"
  private val ST_ENTITY_NO_IDI = "&(?:#x[a-f0-9" + ST_ADDITIONAL_CHARS_NO_IDI + "]{1,8}|#[0-9]{1,8}|[a-z" + ST_ADDITIONAL_CHARS_NO_IDI + "][a-z0-9" + ST_ADDITIONAL_CHARS_NO_IDI + "]{1,31});"

  private val ST_IN_BRACES_W_SP_IDI    = "\\{\\{(?:[^{}\\\\" + ST_EXCLUDED_0_TO_SPACE_IDI + "]| |\t)*\\}\\}"
  private val ST_IN_BRACES_W_SP_NO_IDI = "\\{\\{(?:[^{}\\\\" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]| |\t)*\\}\\}"

  private val ST_REG_CHAR_IDI    = "[^\\\\()" + ST_EXCLUDED_0_TO_SPACE_IDI + "]"
  private val ST_REG_CHAR_NO_IDI = "[^\\\\()" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]"

  private val ST_IN_MATCHED_PARENS_NOSP_IDI    = "\\((" + ST_REG_CHAR_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"
  private val ST_IN_MATCHED_PARENS_NOSP_NO_IDI = "\\((" + ST_REG_CHAR_NO_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"

  private val ST_REG_CHAR_SP_IDI    = "[^\\\\()" + ST_EXCLUDED_0_TO_SPACE_IDI + "]| (?![\"'])"
  private val ST_REG_CHAR_SP_NO_IDI = "[^\\\\()" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]| (?![\"'])"

  private val ST_IN_MATCHED_PARENS_W_SP_IDI    = "\\((" + ST_REG_CHAR_SP_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"
  private val ST_IN_MATCHED_PARENS_W_SP_NO_IDI = "\\((" + ST_REG_CHAR_SP_NO_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"

  private val ST_IN_PARENS_NOSP_IDI    = "\\((" + ST_REG_CHAR_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"
  private val ST_IN_PARENS_NOSP_NO_IDI = "\\((" + ST_REG_CHAR_NO_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"

  private val ST_IN_PARENS_W_SP_IDI    = "\\((" + ST_REG_CHAR_SP_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"
  private val ST_IN_PARENS_W_SP_NO_IDI = "\\((" + ST_REG_CHAR_SP_NO_IDI + '|' + ST_ESCAPED_CHAR + ")*\\)"

  private val ST_OPENTAG_IDI       = "<" + ST_TAGNAME_IDI + ST_ATTRIBUTE_IDI + "*" + "\\s*/?>"
  private val ST_OPENTAG_NO_IDI    = "<" + ST_TAGNAME_NO_IDI + ST_ATTRIBUTE_NO_IDI + "*" + "\\s*/?>"
  private val ST_NS_OPENTAG_IDI    = "<" + XML_NAMESPACE + ST_TAGNAME_IDI + ST_ATTRIBUTE_IDI + "*" + "\\s*/?>"
  private val ST_NS_OPENTAG_NO_IDI = "<" + XML_NAMESPACE + ST_TAGNAME_NO_IDI + ST_ATTRIBUTE_NO_IDI + "*" + "\\s*/?>"

  private val ST_REG_CHAR_PARENS_IDI    = "[^\\\\" + ST_EXCLUDED_0_TO_SPACE_IDI + "]"
  private val ST_REG_CHAR_PARENS_NO_IDI = "[^\\\\" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]"

  private val ST_REG_CHAR_SP_PARENS_IDI    = "[^\\\\" + ST_EXCLUDED_0_TO_SPACE_IDI + "]| (?![\"'])"
  private val ST_REG_CHAR_SP_PARENS_NO_IDI = "[^\\\\" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]| (?![\"'])"

  private val ST_ENTITY_HERE_IDI    = Pattern.compile('^' + ST_ENTITY_IDI, Pattern.CASE_INSENSITIVE)
  private val ST_ENTITY_HERE_NO_IDI = Pattern.compile('^' + ST_ENTITY_NO_IDI, Pattern.CASE_INSENSITIVE)

  private val cachedPatterns: ju.HashMap[String, ju.HashMap[PatternTypeFlags, Pattern]] = new ju.HashMap()

  private def getCachedPattern(patternName: String, cachedTypeFlags: PatternTypeFlags, factory: JFunction[PatternTypeFlags, Pattern]): Pattern = {
    val patternMap = cachedPatterns.computeIfAbsent(patternName, (_: String) => new ju.HashMap())
    patternMap.computeIfAbsent(cachedTypeFlags, factory)
  }

  final private class PatternTypeFlags(
    val intellijDummyIdentifier:         java.lang.Boolean,
    val htmlForTranslator:               java.lang.Boolean,
    val translationHtmlInlineTagPattern: String,
    val translationAutolinkTagPattern:   String,
    val spaceInLinkUrl:                  java.lang.Boolean,
    val parseJekyllMacroInLinkUrl:       java.lang.Boolean,
    val itemPrefixChars:                 String,
    val listsItemMarkerSpace:            java.lang.Boolean,
    val listsOrderedItemDotOnly:         java.lang.Boolean,
    val allowNameSpace:                  java.lang.Boolean
  ) {

    def this(options: DataHolder) = {
      this(
        intellijDummyIdentifier = Parser.INTELLIJ_DUMMY_IDENTIFIER.get(options),
        htmlForTranslator = Parser.HTML_FOR_TRANSLATOR.get(options),
        translationHtmlInlineTagPattern = Parser.TRANSLATION_HTML_INLINE_TAG_PATTERN.get(options),
        translationAutolinkTagPattern = Parser.TRANSLATION_AUTOLINK_TAG_PATTERN.get(options),
        spaceInLinkUrl = Parser.SPACE_IN_LINK_URLS.get(options),
        parseJekyllMacroInLinkUrl = Parser.PARSE_JEKYLL_MACROS_IN_URLS.get(options),
        itemPrefixChars = Parser.LISTS_ITEM_PREFIX_CHARS.get(options),
        listsItemMarkerSpace = Parser.LISTS_ITEM_MARKER_SPACE.get(options),
        listsOrderedItemDotOnly = Parser.LISTS_ORDERED_ITEM_DOT_ONLY.get(options),
        allowNameSpace = Parser.HTML_ALLOW_NAME_SPACE.get(options)
      )
    }

    def withJekyllMacroInLinkUrl(): PatternTypeFlags = new PatternTypeFlags(intellijDummyIdentifier, null, null, null, null, parseJekyllMacroInLinkUrl, null, null, null, null)

    def withJekyllMacroSpaceInLinkUrl(): PatternTypeFlags = new PatternTypeFlags(intellijDummyIdentifier, null, null, null, spaceInLinkUrl, parseJekyllMacroInLinkUrl, null, null, null, null)

    def withHtmlTranslator(): PatternTypeFlags = new PatternTypeFlags(
      intellijDummyIdentifier,
      htmlForTranslator,
      translationHtmlInlineTagPattern,
      translationAutolinkTagPattern,
      null,
      null,
      null,
      null,
      null,
      null
    )

    def withItemPrefixChars(): PatternTypeFlags = new PatternTypeFlags(null, null, null, null, null, null, itemPrefixChars, listsItemMarkerSpace, listsOrderedItemDotOnly, null)

    def withAllowNameSpace(): PatternTypeFlags = new PatternTypeFlags(null, null, null, null, null, null, null, null, null, allowNameSpace)

    /** Compare where null entry equals any other value
      */
    override def equals(o: Any): Boolean =
      if (this eq o.asInstanceOf[AnyRef]) true
      else
        o match {
          case that: PatternTypeFlags =>
            (intellijDummyIdentifier == null || intellijDummyIdentifier == that.intellijDummyIdentifier) &&
            (htmlForTranslator == null || htmlForTranslator == that.htmlForTranslator) &&
            (translationHtmlInlineTagPattern == null || translationHtmlInlineTagPattern == that.translationHtmlInlineTagPattern) &&
            (translationAutolinkTagPattern == null || translationAutolinkTagPattern == that.translationAutolinkTagPattern) &&
            (spaceInLinkUrl == null || spaceInLinkUrl == that.spaceInLinkUrl) &&
            (parseJekyllMacroInLinkUrl == null || parseJekyllMacroInLinkUrl == that.parseJekyllMacroInLinkUrl) &&
            (itemPrefixChars == null || itemPrefixChars == that.itemPrefixChars) &&
            (listsItemMarkerSpace == null || listsItemMarkerSpace == that.listsItemMarkerSpace) &&
            (allowNameSpace == null || allowNameSpace == that.allowNameSpace) &&
            (listsOrderedItemDotOnly == null || listsOrderedItemDotOnly == that.listsOrderedItemDotOnly)
          case _ => false
        }

    override def hashCode(): Int = {
      var result = if (intellijDummyIdentifier != null) intellijDummyIdentifier.hashCode else 0
      result = 31 * result + (if (htmlForTranslator != null) htmlForTranslator.hashCode else 0)
      result = 31 * result + (if (translationHtmlInlineTagPattern != null) translationHtmlInlineTagPattern.hashCode else 0)
      result = 31 * result + (if (translationAutolinkTagPattern != null) translationAutolinkTagPattern.hashCode else 0)
      result = 31 * result + (if (spaceInLinkUrl != null) spaceInLinkUrl.hashCode else 0)
      result = 31 * result + (if (parseJekyllMacroInLinkUrl != null) parseJekyllMacroInLinkUrl.hashCode else 0)
      result = 31 * result + (if (itemPrefixChars != null) itemPrefixChars.hashCode else 0)
      result = 31 * result + (if (listsItemMarkerSpace != null) listsItemMarkerSpace.hashCode else 0)
      result = 31 * result + (if (listsOrderedItemDotOnly != null) listsOrderedItemDotOnly.hashCode else 0)
      result = 31 * result + (if (allowNameSpace != null) allowNameSpace.hashCode else 0)
      result
    }
  }

  /** @deprecated
    *   in version (0.62.2), to be removed
    */
  @deprecated("to be removed", "0.62.2")
  def EXCLUDED_0_TO_SPACE(intellijDummyIdentifier: Boolean): String =
    if (intellijDummyIdentifier) ST_EXCLUDED_0_TO_SPACE_IDI else ST_EXCLUDED_0_TO_SPACE_NO_IDI

  /** @deprecated
    *   in version (0.62.2), to be removed
    */
  @deprecated("to be removed", "0.62.2")
  def ADDITIONAL_CHARS(intellijDummyIdentifier: Boolean): String =
    if (intellijDummyIdentifier) ST_ADDITIONAL_CHARS_IDI else ST_ADDITIONAL_CHARS_NO_IDI

  /** @deprecated
    *   in version (0.62.2), to be removed
    */
  @deprecated("to be removed", "0.62.2")
  def ADDITIONAL_CHARS_SET(intellijDummyIdentifier: Boolean, quantifier: String): String =
    if (intellijDummyIdentifier) ST_ADDITIONAL_CHARS_SET_IDI + quantifier else ST_ADDITIONAL_CHARS_SET_NO_IDI

  def columnsToNextTabStop(column: Int): Int =
    // Tab stop is 4
    4 - (column % 4)

  def findLineBreak(s: CharSequence, startIndex: Int): Int =
    SequenceUtils.indexOfAny(s, CharPredicate.ANY_EOL, startIndex)

  def isBlank(s: CharSequence): Boolean =
    SequenceUtils.indexOfAnyNot(s, CharPredicate.BLANKSPACE) == -1

  def isLetter(s: CharSequence, index: Int): Boolean = {
    val codePoint = Character.codePointAt(s, index)
    Character.isLetter(codePoint)
  }

  def isSpaceOrTab(s: CharSequence, index: Int): Boolean =
    CharPredicate.SPACE_TAB.test(SequenceUtils.safeCharAt(s, index))
}
