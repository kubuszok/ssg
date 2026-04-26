/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/PegdownExtensions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/PegdownExtensions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser

object PegdownExtensions {

  /** The default, standard markup mode without any extensions. */
  val NONE: Int = 0x00

  /** Pretty ellipses, dashes and apostrophes. */
  val SMARTS: Int = 0x01

  /** Pretty single and double quotes. */
  val QUOTES: Int = 0x02

  /** All of the smartypants prettyfications. Equivalent to SMARTS + QUOTES.
    *
    * @see
    *   [[http://daringfireball.net/projects/smartypants/ Smartypants]]
    */
  val SMARTYPANTS: Int = SMARTS + QUOTES

  /** PHP Markdown Extra style abbreviations.
    *
    * @see
    *   [[https://michelf.ca/projects/php-markdown/extra/#abbr PHP Markdown Extra]]
    */
  val ABBREVIATIONS: Int = 0x04

  /** Enables the parsing of hard wraps as HTML linebreaks. Similar to what github does.
    *
    * @see
    *   [[http://github.github.com/github-flavored-markdown Github-flavored-Markdown]]
    */
  val HARDWRAPS: Int = 0x08

  /** Enables plain autolinks the way github flavoured markdown implements them.
    *
    * @see
    *   [[http://github.github.com/github-flavored-markdown Github-flavored-Markdown]]
    */
  val AUTOLINKS: Int = 0x10

  /** Table support similar to what Multimarkdown offers.
    *
    * @see
    *   [[http://fletcherpenney.net/multimarkdown/users_guide/ MultiMarkdown]]
    */
  val TABLES: Int = 0x20

  /** PHP Markdown Extra style definition lists.
    *
    * @see
    *   [[https://michelf.ca/projects/php-markdown/extra/#def-list PHP Markdown Extra]]
    */
  val DEFINITIONS: Int = 0x40

  /** PHP Markdown Extra style fenced code blocks.
    *
    * @see
    *   [[https://michelf.ca/projects/php-markdown/extra/#fenced-code-blocks PHP Markdown Extra]]
    */
  val FENCED_CODE_BLOCKS: Int = 0x80

  /** Support `[[Wiki-style links]]`. */
  val WIKILINKS: Int = 0x100

  /** Support `~~strikethroughs~~` as supported in Pandoc and Github. */
  val STRIKETHROUGH: Int = 0x200

  /** Enables anchor links in headers. */
  val ANCHORLINKS: Int = 0x400

  /** All available extensions excluding the high word options. */
  val UNUSED_ALL: Int = 0x0000f800
  val ALL:        Int = 0x0000ffff

  /** Suppresses HTML blocks. */
  val SUPPRESS_HTML_BLOCKS: Int = 0x00010000

  /** Suppresses inline HTML tags. */
  val SUPPRESS_INLINE_HTML: Int = 0x00020000

  /** Suppresses HTML blocks as well as inline HTML tags. */
  val SUPPRESS_ALL_HTML: Int = 0x00030000

  /** Requires a space char after Atx # header prefixes. */
  val ATXHEADERSPACE: Int = 0x00040000

  /** Force List and Definition Paragraph wrapping if it includes more than just a single paragraph. */
  val SUBSCRIPT: Int = 0x00080000

  /** Allow horizontal rules without a blank line following them. */
  val RELAXEDHRULES: Int = 0x00100000

  /** GitHub style task list items: `- [ ]` and `- [x]`. */
  val TASKLISTITEMS: Int = 0x00200000

  /** Generate anchor links for headers using complete contents of the header. */
  val EXTANCHORLINKS: Int = 0x00400000

  /** EXTANCHORLINKS should wrap header content instead of creating an empty anchor. */
  val EXTANCHORLINKS_WRAP: Int = 0x00800000

  /** Enables footnote processing. */
  val FOOTNOTES: Int = 0x01000000

  /** Enables TOC extension. */
  val TOC: Int = 0x02000000

  /** Enables MULTI_LINE_IMAGE_URLS extension. */
  val MULTI_LINE_IMAGE_URLS: Int = 0x04000000

  /** Trace parsing elements to console. */
  val SUPERSCRIPT: Int = 0x08000000

  /** Force List and Definition Paragraph wrapping if it includes more than just a single paragraph. */
  val FORCELISTITEMPARA: Int = 0x10000000

  /** Spare bits. */
  val NOT_USED: Int = 0x20000000

  /** Enables adding a dummy reference key node to RefLink and RefImage. */
  val INSERTED: Int = 0x40000000

  val UNUSABLE: Int = 0x80000000

  /** All Optionals other than Suppress and FORCELISTITEMPARA. */
  val ALL_OPTIONALS: Int =
    ATXHEADERSPACE | RELAXEDHRULES | TASKLISTITEMS | EXTANCHORLINKS | FOOTNOTES |
      SUBSCRIPT | SUPERSCRIPT | TOC | MULTI_LINE_IMAGE_URLS | INSERTED

  val ALL_WITH_OPTIONALS: Int = ALL | ALL_OPTIONALS

  /** These are GitHub main repo document processing compatibility flags. */
  val GITHUB_DOCUMENT_COMPATIBLE: Int =
    FENCED_CODE_BLOCKS | TABLES | AUTOLINKS | ANCHORLINKS | TASKLISTITEMS |
      STRIKETHROUGH | ATXHEADERSPACE | RELAXEDHRULES

  /** These are GitHub wiki page processing compatibility flags. */
  val GITHUB_WIKI_COMPATIBLE: Int = GITHUB_DOCUMENT_COMPATIBLE | WIKILINKS

  /** These are GitHub comment (issues, pull requests and comments) processing compatibility flags. */
  val GITHUB_COMMENT_COMPATIBLE: Int = GITHUB_DOCUMENT_COMPATIBLE | HARDWRAPS
}
