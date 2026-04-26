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
import ssg.md.util.sequence.RegexCompat
import ssg.md.util.sequence.SequenceUtils

import java.{ util => ju }
import java.util.function.{ Function => JFunction }
import java.util.regex.Pattern

import scala.language.implicitConversions

class Parsing(val options: DataHolder) {

  import Parsing._

  val CODE_BLOCK_INDENT: Int = Parser.CODE_BLOCK_INDENT.get(options)

  private val patternTypeFlags = new PatternTypeFlags(options)

  val intellijDummyIdentifier:         Boolean = patternTypeFlags.intellijDummyIdentifier == true
  val htmlForTranslator:               Boolean = patternTypeFlags.htmlForTranslator == true
  val translationHtmlInlineTagPattern: String  = patternTypeFlags.translationHtmlInlineTagPattern
  val translationAutolinkTagPattern:   String  = patternTypeFlags.translationAutolinkTagPattern
  val spaceInLinkUrl:                  Boolean = patternTypeFlags.spaceInLinkUrl == true
  val parseJekyllMacroInLinkUrl:       Boolean = patternTypeFlags.parseJekyllMacroInLinkUrl == true
  val itemPrefixChars:                 String  = patternTypeFlags.itemPrefixChars
  val listsItemMarkerSpace:            Boolean = patternTypeFlags.listsItemMarkerSpace == true
  val listsOrderedItemDotOnly:         Boolean = patternTypeFlags.listsOrderedItemDotOnly == true
  val allowNameSpace:                  Boolean = patternTypeFlags.allowNameSpace == true

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

  // Cross-platform: original Java regex used lookaheads (?=[ \t]) and (?= |\t|$)
  // to assert space/tab/EOL after the list marker without consuming it. Lookaheads
  // are unavailable on Scala.js and Scala Native. Rewritten without lookaheads;
  // the post-match space/tab/EOL check is performed in ListBlockParser.parseListMarker.
  // Original (listsItemMarkerSpace=true): "^([\\Q...\\E])(?=[ \\t])|^(\\d{1,9})([.)])(?=[ \\t])"
  // Original (listsItemMarkerSpace=false): "^([\\Q...\\E])(?= |\\t|$)|^(\\d{1,9})([.)])(?= |\\t|$)"
  // Revert to originals if/when Scala.js and Scala Native add full java.util.regex support.
  val LIST_ITEM_MARKER: Pattern = cachedPatterns.synchronized {
    getCachedPattern(
      "LIST_ITEM_MARKER",
      patternTypeFlags.withItemPrefixChars(),
      entry =>
        if (listsOrderedItemDotOnly) {
          // Cross-platform: \Q...\E not supported on Scala Native re2
          Pattern.compile("^([" + RegexCompat.charClassEscape(itemPrefixChars) + "])|^(\\d{1,9})([.])")
        } else {
          // Cross-platform: \Q...\E not supported on Scala Native re2
          Pattern.compile("^([" + RegexCompat.charClassEscape(itemPrefixChars) + "])|^(\\d{1,9})([.)])")
        }
    )
  }

  /** Whether list markers require space/tab (true) or also allow EOL (false). */
  val listsItemMarkerSpaceFlag: Boolean = listsItemMarkerSpace

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
  // Cross-platform: original Java regex used \p{Pc}\p{Pd}\p{Pe}\p{Pf}\p{Pi}\p{Po}\p{Ps}
  // Unicode categories and [...]&&[^...] character class intersection, which are
  // unavailable on Scala.js and Scala Native. Rewritten using explicit Unicode ranges
  // covering the most common BMP punctuation characters from each category.
  // Original ST_PUNCTUATION: "^[" + ascii + "\\p{Pc}\\p{Pd}...\\p{Ps}]"
  // Original ST_PUNCTUATION_OPEN: "^[ascii_open]|[\\p{...}]&&[^ascii_close]"
  // Original ST_PUNCTUATION_CLOSE: "^[ascii_close]|[\\p{...}]&&[^ascii_open]"
  // Original ST_PUNCTUATION_ONLY: "^[ascii]|[\\p{...}]&&[^ascii_open ascii_close]"
  // Revert to originals if/when Scala.js and Scala Native add full java.util.regex support.

  // Unicode punctuation character class fragments for use inside [...].
  // These cover commonly-used BMP punctuation characters from each Unicode category.
  // Hyphen-minus (\u002D) is placed at the end to avoid being interpreted as a range.
  // Note: ASCII punctuation handled separately by ST_ASCII_* constants.
  //
  // Practical subset of non-ASCII Unicode punctuation used in real-world text:
  //   Pc: _ (already ASCII), ‿ ⁀ ⁔ (connectors)
  //   Pd: ‐ ‑ ‒ – — ― ⸗ (dashes)
  //   Pe: » ' " › (closing quotes — also Pf, but some engines classify differently)
  //       ） ］ ｝ ｠ ｣ (fullwidth close brackets)
  //   Pf: » ' " › (final quotes)
  //   Pi: « ' " ‹ (initial quotes)
  //   Po: ¡ § ¶ · ¿ … † ‡ • ‣ ′ ″ ‴ ※ ‼ ⁇ ⁈ ⁉ (common other punct)
  //       、 。 〃 〽 ・ (CJK punctuation)
  //   Ps: « " ' ‚ „ (open quotes — also Pi, overlap)
  //       （ ［ ｛ ｟ ｢ 〈 《 「 『 【 〔 〖 〘 〚 (fullwidth/CJK open brackets)

  // Non-ASCII Unicode punctuation — all categories combined, safe for [...]
  // Ranges are sorted by codepoint; hyphen-minus placed at the very end.
  private val UNICODE_PUNCT_ALL =
    "\u00A1\u00A7\u00AB\u00B6\u00B7\u00BB\u00BF" + // common Latin punct
      "\u2010\u2011\u2012\u2013\u2014\u2015" + // dashes (Pd)
      "\u2016\u2017" + // double vertical line, double low line (Po)
      "\u2018\u2019\u201A\u201B\u201C\u201D\u201E\u201F" + // smart quotes (Pi/Pf/Ps)
      "\u2020\u2021\u2022\u2023\u2024\u2025\u2026\u2027" + // daggers, bullets, ellipsis (Po)
      "\u2030\u2031\u2032\u2033\u2034\u2035\u2036\u2037\u2038" + // per-mille, primes (Po)
      "\u2039\u203A" + // single angle quotes (Pi/Pf)
      "\u203B\u203C\u203D\u203E" + // reference mark, double excl, interrobang (Po)
      "\u203F\u2040" + // undertie, char tie (Pc)
      "\u2041\u2042\u2043" + // caret insertion, asterism, hyphen bullet (Po)
      "\u2045\u2046" + // square bracket with quill (Ps/Pe)
      "\u2047\u2048\u2049\u204A\u204B\u204C\u204D\u204E\u204F\u2050\u2051" + // double ?!, etc. (Po)
      "\u2053\u2054\u2055\u2056\u2057\u2058\u2059\u205A\u205B\u205C\u205D\u205E" + // swung dash, connectors, dots (Pc/Po)
      "\u207D\u207E\u208D\u208E" + // super/subscript parens (Ps/Pe)
      "\u2308\u2309\u230A\u230B" + // ceiling/floor brackets (Ps/Pe)
      "\u2329\u232A" + // angle brackets (Ps/Pe)
      "\u2768\u2769\u276A\u276B\u276C\u276D\u276E\u276F" + // ornamental brackets (Ps/Pe)
      "\u2770\u2771\u2772\u2773\u2774\u2775" + // ornamental brackets (Ps/Pe)
      "\u27C5\u27C6" + // S-shaped bag delimiters (Ps/Pe)
      "\u27E6\u27E7\u27E8\u27E9\u27EA\u27EB\u27EC\u27ED\u27EE\u27EF" + // math brackets (Ps/Pe)
      "\u2983\u2984\u2985\u2986\u2987\u2988\u2989\u298A\u298B\u298C\u298D\u298E\u298F\u2990" + // math brackets (Ps/Pe)
      "\u2991\u2992\u2993\u2994\u2995\u2996\u2997\u2998" + // math brackets (Ps/Pe)
      "\u29D8\u29D9\u29DA\u29DB\u29FC\u29FD" + // math brackets (Ps/Pe)
      "\u2CF9\u2CFA\u2CFB\u2CFC\u2CFE\u2CFF" + // Coptic punct (Po)
      "\u2E00\u2E01\u2E02\u2E03\u2E04\u2E05\u2E06\u2E07\u2E08\u2E09\u2E0A\u2E0B" + // supplemental punct (Pi/Pf/Po)
      "\u2E0C\u2E0D\u2E0E\u2E0F\u2E10\u2E11\u2E12\u2E13\u2E14\u2E15\u2E16\u2E17" + // supplemental punct
      "\u2E18\u2E19\u2E1A\u2E1B\u2E1C\u2E1D\u2E1E\u2E1F\u2E20\u2E21" + // supplemental punct
      "\u2E22\u2E23\u2E24\u2E25\u2E26\u2E27\u2E28\u2E29" + // half brackets (Ps/Pe)
      "\u2E2A\u2E2B\u2E2C\u2E2D\u2E2E" + // supplemental punct (Po)
      "\u2E30\u2E31\u2E32\u2E33\u2E34\u2E35\u2E36\u2E37\u2E38\u2E39\u2E3A\u2E3B" + // supplemental punct
      "\u2E3C\u2E3D\u2E3E\u2E3F\u2E40\u2E41\u2E42\u2E43\u2E44\u2E45\u2E46\u2E47\u2E48\u2E49\u2E4A\u2E4B\u2E4C\u2E4D\u2E4E\u2E4F" +
      "\u3001\u3002\u3003\u3008\u3009\u300A\u300B\u300C\u300D\u300E\u300F" + // CJK punct (Po/Ps/Pe)
      "\u3010\u3011\u3014\u3015\u3016\u3017\u3018\u3019\u301A\u301B\u301C\u301D\u301E\u301F" + // CJK brackets
      "\u3030\u303D\u30A0\u30FB" + // wavy dash, part alt mark, katakana-hiragana (Pd/Po)
      "\uFD3E\uFD3F" + // ornamental parens (Pe/Ps)
      "\uFE10\uFE11\uFE12\uFE13\uFE14\uFE15\uFE16\uFE17\uFE18\uFE19" + // presentation forms (Po/Ps/Pe)
      "\uFE30\uFE31\uFE32\uFE33\uFE34\uFE35\uFE36\uFE37\uFE38\uFE39\uFE3A\uFE3B\uFE3C\uFE3D\uFE3E\uFE3F\uFE40" + // compat forms
      "\uFE41\uFE42\uFE43\uFE44\uFE45\uFE46\uFE47\uFE48\uFE49\uFE4A\uFE4B\uFE4C\uFE4D\uFE4E\uFE4F" + // compat forms
      "\uFE50\uFE51\uFE52\uFE54\uFE55\uFE56\uFE57\uFE58\uFE59\uFE5A\uFE5B\uFE5C\uFE5D\uFE5E\uFE5F\uFE60\uFE61" + // small forms
      "\uFE63\uFE68\uFE6A\uFE6B" + // small forms
      "\uFF01\uFF02\uFF03\uFF05\uFF06\uFF07\uFF08\uFF09\uFF0A\uFF0C\uFF0E\uFF0F" + // fullwidth punct
      "\uFF1A\uFF1B\uFF1F\uFF20\uFF3B\uFF3C\uFF3D\uFF3F\uFF5B\uFF5D\uFF5F\uFF60\uFF61\uFF62\uFF63\uFF64\uFF65" + // fullwidth punct
      "\uFF0D" // fullwidth hyphen-minus (Pd) — placed last to avoid range issues

  // Open punctuation subset: all of UNICODE_PUNCT_ALL except ASCII close chars ()>]})
  // and the Unicode close punctuation characters
  private val UNICODE_PUNCT_NOT_CLOSE =
    "\u00A1\u00A7\u00AB\u00B6\u00B7\u00BB\u00BF" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u2016\u2017" +
      "\u2018\u2019\u201A\u201B\u201C\u201D\u201E\u201F" +
      "\u2020\u2021\u2022\u2023\u2024\u2025\u2026\u2027" +
      "\u2030\u2031\u2032\u2033\u2034\u2035\u2036\u2037\u2038" +
      "\u2039\u203A\u203B\u203C\u203D\u203E\u203F\u2040" +
      "\u2041\u2042\u2043\u2045" + // skip \u2046 (Pe)
      "\u2047\u2048\u2049\u204A\u204B\u204C\u204D\u204E\u204F\u2050\u2051" +
      "\u2053\u2054\u2055\u2056\u2057\u2058\u2059\u205A\u205B\u205C\u205D\u205E" +
      "\u207D" + // skip \u207E (Pe)
      "\u208D" + // skip \u208E (Pe)
      "\u2308" + // skip \u2309 (Pe)
      "\u230A" + // skip \u230B (Pe)
      "\u2329" + // skip \u232A (Pe)
      "\u2768\u276A\u276C\u276E\u2770\u2772\u2774" + // open only
      "\u27C5" + // skip \u27C6 (Pe)
      "\u27E6\u27E8\u27EA\u27EC\u27EE" + // open only
      "\u2983\u2985\u2987\u2989\u298B\u298D\u298F\u2991\u2993\u2995\u2997" + // open only
      "\u29D8\u29DA\u29FC" + // open only
      "\u2CF9\u2CFA\u2CFB\u2CFC\u2CFE\u2CFF" +
      "\u2E00\u2E01\u2E02\u2E03\u2E04\u2E05\u2E06\u2E07\u2E08\u2E09\u2E0A\u2E0B" +
      "\u2E0C\u2E0D\u2E0E\u2E0F\u2E10\u2E11\u2E12\u2E13\u2E14\u2E15\u2E16\u2E17" +
      "\u2E18\u2E19\u2E1A\u2E1B\u2E1C\u2E1D\u2E1E\u2E1F\u2E20\u2E21" +
      "\u2E22\u2E24\u2E26\u2E28" + // open only, skip close halves
      "\u2E2A\u2E2B\u2E2C\u2E2D\u2E2E" +
      "\u2E30\u2E31\u2E32\u2E33\u2E34\u2E35\u2E36\u2E37\u2E38\u2E39\u2E3A\u2E3B" +
      "\u2E3C\u2E3D\u2E3E\u2E3F\u2E40\u2E41\u2E42\u2E43\u2E44\u2E45\u2E46\u2E47\u2E48\u2E49\u2E4A\u2E4B\u2E4C\u2E4D\u2E4E\u2E4F" +
      "\u3001\u3002\u3003\u3008\u300A\u300C\u300E" + // open only CJK
      "\u3010\u3014\u3016\u3018\u301A\u301C\u301D" + // open only CJK
      "\u3030\u303D\u30A0\u30FB" +
      "\uFD3F" + // Ps only (skip \uFD3E Pe)
      "\uFE10\uFE11\uFE12\uFE13\uFE14\uFE15\uFE16\uFE17\uFE19" + // skip \uFE18 (Pe)
      "\uFE30\uFE31\uFE32\uFE33\uFE34\uFE35\uFE37\uFE39\uFE3B\uFE3D\uFE3F" + // open halves
      "\uFE41\uFE43\uFE45\uFE46\uFE47\uFE49\uFE4A\uFE4B\uFE4C\uFE4D\uFE4E\uFE4F" + // open halves
      "\uFE50\uFE51\uFE52\uFE54\uFE55\uFE56\uFE57\uFE58\uFE59\uFE5B\uFE5D\uFE5F\uFE60\uFE61" + // skip close forms
      "\uFE63\uFE68\uFE6A\uFE6B" +
      "\uFF01\uFF02\uFF03\uFF05\uFF06\uFF07\uFF08\uFF0A\uFF0C\uFF0E\uFF0F" + // skip \uFF09 (Pe)
      "\uFF1A\uFF1B\uFF1F\uFF20\uFF3B\uFF3C\uFF3F\uFF5B\uFF5F\uFF61\uFF62\uFF64\uFF65" + // skip close forms
      "\uFF0D"

  // Close punctuation subset: all of UNICODE_PUNCT_ALL except ASCII open chars ((<[{)
  // and the Unicode open punctuation characters
  private val UNICODE_PUNCT_NOT_OPEN =
    "\u00A1\u00A7\u00AB\u00B6\u00B7\u00BB\u00BF" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u2016\u2017" +
      "\u2018\u2019\u201A\u201B\u201C\u201D\u201E\u201F" +
      "\u2020\u2021\u2022\u2023\u2024\u2025\u2026\u2027" +
      "\u2030\u2031\u2032\u2033\u2034\u2035\u2036\u2037\u2038" +
      "\u2039\u203A\u203B\u203C\u203D\u203E\u203F\u2040" +
      "\u2041\u2042\u2043\u2046" + // skip \u2045 (Ps)
      "\u2047\u2048\u2049\u204A\u204B\u204C\u204D\u204E\u204F\u2050\u2051" +
      "\u2053\u2054\u2055\u2056\u2057\u2058\u2059\u205A\u205B\u205C\u205D\u205E" +
      "\u207E" + // skip \u207D (Ps)
      "\u208E" + // skip \u208D (Ps)
      "\u2309" + // skip \u2308 (Ps)
      "\u230B" + // skip \u230A (Ps)
      "\u232A" + // skip \u2329 (Ps)
      "\u2769\u276B\u276D\u276F\u2771\u2773\u2775" + // close only
      "\u27C6" + // skip \u27C5 (Ps)
      "\u27E7\u27E9\u27EB\u27ED\u27EF" + // close only
      "\u2984\u2986\u2988\u298A\u298C\u298E\u2990\u2992\u2994\u2996\u2998" + // close only
      "\u29D9\u29DB\u29FD" + // close only
      "\u2CF9\u2CFA\u2CFB\u2CFC\u2CFE\u2CFF" +
      "\u2E00\u2E01\u2E02\u2E03\u2E04\u2E05\u2E06\u2E07\u2E08\u2E09\u2E0A\u2E0B" +
      "\u2E0C\u2E0D\u2E0E\u2E0F\u2E10\u2E11\u2E12\u2E13\u2E14\u2E15\u2E16\u2E17" +
      "\u2E18\u2E19\u2E1A\u2E1B\u2E1C\u2E1D\u2E1E\u2E1F\u2E20\u2E21" +
      "\u2E23\u2E25\u2E27\u2E29" + // close only, skip open halves
      "\u2E2A\u2E2B\u2E2C\u2E2D\u2E2E" +
      "\u2E30\u2E31\u2E32\u2E33\u2E34\u2E35\u2E36\u2E37\u2E38\u2E39\u2E3A\u2E3B" +
      "\u2E3C\u2E3D\u2E3E\u2E3F\u2E40\u2E41\u2E42\u2E43\u2E44\u2E45\u2E46\u2E47\u2E48\u2E49\u2E4A\u2E4B\u2E4C\u2E4D\u2E4E\u2E4F" +
      "\u3001\u3002\u3003\u3009\u300B\u300D\u300F" + // close only CJK
      "\u3011\u3015\u3017\u3019\u301B\u301C\u301E\u301F" + // close only CJK
      "\u3030\u303D\u30A0\u30FB" +
      "\uFD3E" + // Pe only (skip \uFD3F Ps)
      "\uFE10\uFE11\uFE12\uFE13\uFE14\uFE15\uFE16\uFE18\uFE19" + // skip \uFE17 (Ps)
      "\uFE30\uFE31\uFE32\uFE33\uFE34\uFE36\uFE38\uFE3A\uFE3C\uFE3E\uFE40" + // close halves
      "\uFE42\uFE44\uFE45\uFE46\uFE48\uFE49\uFE4A\uFE4B\uFE4C\uFE4D\uFE4E\uFE4F" + // close halves
      "\uFE50\uFE51\uFE52\uFE54\uFE55\uFE56\uFE57\uFE58\uFE5A\uFE5C\uFE5E\uFE5F\uFE60\uFE61" + // skip open forms
      "\uFE63\uFE68\uFE6A\uFE6B" +
      "\uFF01\uFF02\uFF03\uFF05\uFF06\uFF07\uFF09\uFF0A\uFF0C\uFF0E\uFF0F" + // skip \uFF08 (Ps)
      "\uFF1A\uFF1B\uFF1F\uFF20\uFF3C\uFF3D\uFF3F\uFF5D\uFF60\uFF61\uFF63\uFF64\uFF65" + // skip open forms
      "\uFF0D"

  // Punctuation that is neither open nor close — non-ASCII subset
  private val UNICODE_PUNCT_NOT_OPEN_CLOSE =
    "\u00A1\u00A7\u00AB\u00B6\u00B7\u00BB\u00BF" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u2016\u2017" +
      "\u2018\u2019\u201A\u201B\u201C\u201D\u201E\u201F" +
      "\u2020\u2021\u2022\u2023\u2024\u2025\u2026\u2027" +
      "\u2030\u2031\u2032\u2033\u2034\u2035\u2036\u2037\u2038" +
      "\u2039\u203A\u203B\u203C\u203D\u203E\u203F\u2040" +
      "\u2041\u2042\u2043" + // skip both \u2045 (Ps) and \u2046 (Pe)
      "\u2047\u2048\u2049\u204A\u204B\u204C\u204D\u204E\u204F\u2050\u2051" +
      "\u2053\u2054\u2055\u2056\u2057\u2058\u2059\u205A\u205B\u205C\u205D\u205E" +
      // skip all bracket pairs (Ps/Pe)
      "\u2CF9\u2CFA\u2CFB\u2CFC\u2CFE\u2CFF" +
      "\u2E00\u2E01\u2E02\u2E03\u2E04\u2E05\u2E06\u2E07\u2E08\u2E09\u2E0A\u2E0B" +
      "\u2E0C\u2E0D\u2E0E\u2E0F\u2E10\u2E11\u2E12\u2E13\u2E14\u2E15\u2E16\u2E17" +
      "\u2E18\u2E19\u2E1A\u2E1B\u2E1C\u2E1D\u2E1E\u2E1F\u2E20\u2E21" +
      // skip bracket pairs \u2E22-\u2E29
      "\u2E2A\u2E2B\u2E2C\u2E2D\u2E2E" +
      "\u2E30\u2E31\u2E32\u2E33\u2E34\u2E35\u2E36\u2E37\u2E38\u2E39\u2E3A\u2E3B" +
      "\u2E3C\u2E3D\u2E3E\u2E3F\u2E40\u2E41\u2E42\u2E43\u2E44\u2E45\u2E46\u2E47\u2E48\u2E49\u2E4A\u2E4B\u2E4C\u2E4D\u2E4E\u2E4F" +
      "\u3001\u3002\u3003" + // skip all CJK brackets
      "\u301C\u3030\u303D\u30A0\u30FB" +
      "\uFE10\uFE11\uFE12\uFE13\uFE14\uFE15\uFE16\uFE19" + // skip bracket forms
      "\uFE30\uFE31\uFE32\uFE33\uFE34\uFE45\uFE46\uFE49\uFE4A\uFE4B\uFE4C\uFE4D\uFE4E\uFE4F" + // skip bracket forms
      "\uFE50\uFE51\uFE52\uFE54\uFE55\uFE56\uFE57\uFE58\uFE5F\uFE60\uFE61" + // skip bracket forms
      "\uFE63\uFE68\uFE6A\uFE6B" +
      "\uFF01\uFF02\uFF03\uFF05\uFF06\uFF07\uFF0A\uFF0C\uFF0E\uFF0F" + // skip brackets
      "\uFF1A\uFF1B\uFF1F\uFF20\uFF3C\uFF3F\uFF61\uFF64\uFF65" + // skip brackets
      "\uFF0D"

  private val ST_PUNCTUATION = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + ST_ASCII_OPEN_PUNCTUATION + ST_ASCII_CLOSE_PUNCTUATION + UNICODE_PUNCT_ALL + "]"
  )
  private val ST_PUNCTUATION_OPEN = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + ST_ASCII_OPEN_PUNCTUATION + UNICODE_PUNCT_NOT_CLOSE + "]"
  )
  private val ST_PUNCTUATION_CLOSE = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + ST_ASCII_CLOSE_PUNCTUATION + UNICODE_PUNCT_NOT_OPEN + "]"
  )
  private val ST_PUNCTUATION_ONLY = Pattern.compile(
    "^[" + ST_ASCII_PUNCTUATION + UNICODE_PUNCT_NOT_OPEN_CLOSE + "]"
  )
  private val ST_PUNCTUATION_OPEN_ONLY  = Pattern.compile("^[" + ST_ASCII_OPEN_PUNCTUATION + "]")
  private val ST_PUNCTUATION_CLOSE_ONLY = Pattern.compile("^[" + ST_ASCII_CLOSE_PUNCTUATION + "]")

  private val ST_ESCAPABLE    = Pattern.compile('^' + Escaping.ESCAPABLE)
  private val ST_TICKS        = Pattern.compile("`+")
  private val ST_TICKS_HERE   = Pattern.compile("^`+")
  private val ST_SPNL         = Pattern.compile("^(?:[ \t])*(?:" + ST_EOL + "(?:[ \t])*)?")
  private val ST_SPNL_URL     = Pattern.compile("^(?:[ \t])*" + ST_EOL)
  private val ST_SPNI         = Pattern.compile("^ {0,3}")
  private val ST_SP           = Pattern.compile("^(?:[ \t])*")
  private val ST_REST_OF_LINE = Pattern.compile("^.*" + ST_EOL)
  // Cross-platform: original Java regex used \p{Zs} (space separator Unicode category)
  // which is unavailable on Scala.js and Scala Native. Expanded to explicit BMP ranges.
  // \p{Zs} = U+0020, U+00A0, U+1680, U+2000-U+200A, U+202F, U+205F, U+3000
  // Original: "^[\\p{Zs}\t\r\n\f]"
  // Revert to original if/when Scala.js and Scala Native add full java.util.regex support.
  private val UNICODE_Zs                 = "\u0020\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000"
  private val ST_UNICODE_WHITESPACE_CHAR = Pattern.compile("^[" + UNICODE_Zs + "\t\r\n\f]")
  private val ST_WHITESPACE              = Pattern.compile("\\s+")
  private val ST_FINAL_SPACE             = Pattern.compile(" *$")
  private val ST_LINE_END                = Pattern.compile("^[ \t]*(?:" + ST_EOL + "|$)")
  // Cross-platform: original Java regex used negative lookahead (?![\"']) to prevent
  // matching a space followed by a quote character inside angle-bracket links.
  // Lookaheads are unavailable on Scala.js and Scala Native. Rewritten to allow
  // all spaces inside <...>; the space-before-quote restriction from the original
  // ` (?![\"'])` is now checked programmatically in InlineParserImpl.parseLinkDestination.
  // Original: "^(?:[<](?:[^<> \\t\\n\\\\\\x00]|" + ESCAPED_CHAR + "|\\\\| (?![\"']))*[>])"
  // Revert to original if/when Scala.js and Scala Native add full java.util.regex support.
  private val ST_LINK_DESTINATION_ANGLES_SPC    = Pattern.compile("^(?:[<](?:[^<>\\t\\n\\\\\\x00]" + '|' + ST_ESCAPED_CHAR + '|' + "\\\\)*[>])")
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

  // Cross-platform: original Java regex used negative lookahead (?![\"']) to prevent
  // matching a space followed by a quote character in link destinations with spaces.
  // Lookaheads are unavailable on Scala.js and Scala Native. Rewritten to allow all
  // spaces; the space-before-quote edge case is a minor semantic change that only
  // affects the rarely-used SPACE_IN_LINK_URLS option.
  // Original: "[^\\\\()" + EXCLUDED + "]| (?![\"'])"
  // Revert to originals if/when Scala.js and Scala Native add full java.util.regex support.
  private val ST_REG_CHAR_SP_IDI    = "[^\\\\()" + ST_EXCLUDED_0_TO_SPACE_IDI + "]| "
  private val ST_REG_CHAR_SP_NO_IDI = "[^\\\\()" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]| "

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

  // Cross-platform: same lookahead removal as ST_REG_CHAR_SP above.
  // Original: "[^\\\\" + EXCLUDED + "]| (?![\"'])"
  // Revert to originals if/when Scala.js and Scala Native add full java.util.regex support.
  private val ST_REG_CHAR_SP_PARENS_IDI    = "[^\\\\" + ST_EXCLUDED_0_TO_SPACE_IDI + "]| "
  private val ST_REG_CHAR_SP_PARENS_NO_IDI = "[^\\\\" + ST_EXCLUDED_0_TO_SPACE_NO_IDI + "]| "

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

    def this(options: DataHolder) =
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
