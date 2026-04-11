/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/Parser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

import ssg.md.ast.util.ReferenceRepository
import ssg.md.html.HtmlRenderer
import ssg.md.parser.block.{ BlockPreProcessorFactory, CustomBlockParserFactory, ParagraphPreProcessorFactory }
import ssg.md.parser.delimiter.DelimiterProcessor
import ssg.md.parser.internal.{ DocumentParser, InlineParserImpl, LinkRefProcessorData, PostProcessorManager }
import ssg.md.util.ast._
import ssg.md.util.build.BuilderBase
import ssg.md.util.data._
import ssg.md.util.misc.Extension
import ssg.md.util.sequence.{ BasedSequence, ReplacedBasedSequence }
import ssg.md.util.sequence.mappers.SpecialLeadInHandler

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import java.io.{ IOException, Reader }
import java.util.BitSet

import scala.language.implicitConversions

/** Parses input text to a tree of nodes.
  *
  * Start with the `Parser.builder` method, configure the parser and build it. Example:
  * {{{
  * val parser = Parser.builder().build()
  * val document = parser.parse("input text")
  * }}}
  */
class Parser private (builder: Parser.Builder) extends IParse {

  private val _options: DataHolder = {
    val opts             = builder.toImmutable
    val blockParserFacts = DocumentParser.calculateBlockParserFactories(opts, builder.blockParserFactories.toList)

    val specialLeadInHandlersList = ArrayBuffer.from(builder.specialLeadInHandlers)

    for (factory <- blockParserFacts) {
      val escaper = factory.getLeadInHandler(opts)
      escaper.foreach(specialLeadInHandlersList.addOne)
    }

    val optionsWithSpecialLeadInHandlers = MutableDataSet(builder)
    optionsWithSpecialLeadInHandlers.set(Parser.SPECIAL_LEAD_IN_HANDLERS, specialLeadInHandlersList.toList)

    _blockParserFactories = blockParserFacts
    optionsWithSpecialLeadInHandlers.toImmutable
  }

  private var _blockParserFactories: List[CustomBlockParserFactory] = scala.compiletime.uninitialized

  private val _inlineParserFactory: InlineParserFactory =
    builder.inlineParserFactory.getOrElse(DocumentParser.INLINE_PARSER_FACTORY)

  private val _paragraphPreProcessorFactories: List[List[ParagraphPreProcessorFactory]] =
    DocumentParser.calculateParagraphPreProcessors(_options, builder.paragraphPreProcessorFactories.toList, _inlineParserFactory)

  private val _blockPreProcessorDependencies: List[List[BlockPreProcessorFactory]] =
    DocumentParser.calculateBlockPreProcessors(_options, builder.blockPreProcessorFactories.toList)

  private val _delimiterProcessors: Map[Char, DelimiterProcessor] =
    InlineParserImpl.calculateDelimiterProcessors(_options, builder.delimiterProcessors.toList)

  private val _delimiterCharacters: BitSet =
    InlineParserImpl.calculateDelimiterCharacters(_options, _delimiterProcessors.keySet)

  private val _linkRefProcessors: LinkRefProcessorData =
    InlineParserImpl.calculateLinkRefProcessors(_options, builder.linkRefProcessors.toList)

  private val _specialCharacters: BitSet =
    InlineParserImpl.calculateSpecialCharacters(_options, _delimiterCharacters)

  private val _postProcessorDependencies: List[PostProcessorManager.PostProcessorDependencyStage] =
    PostProcessorManager.calculatePostProcessors(_options, builder.postProcessorFactories.toList)

  private val _inlineParserExtensionFactories: List[InlineParserExtensionFactory] =
    builder.inlineParserExtensionFactories.toList

  /** Parse the specified input text into a tree of nodes.
    *
    * Note that this method is thread-safe (a new parser state is used for each invocation).
    *
    * @param input
    *   the text to parse
    * @return
    *   the root node
    */
  def parse(input: BasedSequence): Document = {
    // NOTE: parser can only handle contiguous sequences with no out of base characters
    input match {
      case _: ReplacedBasedSequence =>
        throw IllegalArgumentException(
          "Parser.parse() does not support BasedSequences with replaced or non-contiguous segments.\n" +
            "Use BasedSequence.of(input.toString()) to convert to contiguous based sequence."
        )
      case _ =>
    }

    val documentParser = DocumentParser(
      _options,
      _blockParserFactories,
      _paragraphPreProcessorFactories,
      _blockPreProcessorDependencies,
      _inlineParserFactory.inlineParser(
        _options,
        _specialCharacters,
        _delimiterCharacters,
        _delimiterProcessors,
        _linkRefProcessors,
        _inlineParserExtensionFactories
      )
    )
    val document = documentParser.parse(input)
    postProcess(document)
  }

  /** Parse the specified input text into a tree of nodes.
    *
    * Note that this method is thread-safe (a new parser state is used for each invocation).
    *
    * @param input
    *   the text to parse
    * @return
    *   the root node
    */
  def parse(input: String): Document = {
    val documentParser = DocumentParser(
      _options,
      _blockParserFactories,
      _paragraphPreProcessorFactories,
      _blockPreProcessorDependencies,
      _inlineParserFactory.inlineParser(
        _options,
        _specialCharacters,
        _delimiterCharacters,
        _delimiterProcessors,
        _linkRefProcessors,
        _inlineParserExtensionFactories
      )
    )
    val document = documentParser.parse(BasedSequence.of(input))
    postProcess(document)
  }

  /** Parse the specified reader into a tree of nodes. The caller is responsible for closing the reader.
    *
    * Note that this method is thread-safe (a new parser state is used for each invocation).
    *
    * @param input
    *   the reader to parse
    * @return
    *   the root node
    * @throws IOException
    *   when reading throws an exception
    */
  @throws[IOException]
  def parseReader(input: Reader): Document = {
    val documentParser = DocumentParser(
      _options,
      _blockParserFactories,
      _paragraphPreProcessorFactories,
      _blockPreProcessorDependencies,
      _inlineParserFactory.inlineParser(
        _options,
        _specialCharacters,
        _delimiterCharacters,
        _delimiterProcessors,
        _linkRefProcessors,
        _inlineParserExtensionFactories
      )
    )
    val document = documentParser.parse(input)
    postProcess(document)
  }

  private def postProcess(document: Document): Document =
    PostProcessorManager.processDocument(document, _postProcessorDependencies)

  override val options: Nullable[DataHolder] = Nullable(_options)

  override def transferReferences(document: Document, included: Document, onlyIfUndefined: Nullable[Boolean]): Boolean = {
    // transfer references from included to document
    var transferred = false

    if (_options.contains(Parser.EXTENSIONS)) {
      for (extension <- Parser.EXTENSIONS.get(_options).asScala)
        extension match {
          case parserExtension: Parser.ReferenceHoldingExtension =>
            if (parserExtension.transferReferences(document, included)) transferred = true
          case _ =>
        }
    }

    // transfer references
    if (document.contains(Parser.REFERENCES) && included.contains(Parser.REFERENCES)) {
      val onlyIfUndef = onlyIfUndefined.getOrElse(Parser.REFERENCES_KEEP.get(document) == KeepType.FIRST)
      if (Parser.transferReferences(Parser.REFERENCES.get(document), Parser.REFERENCES.get(included), onlyIfUndef)) {
        transferred = true
      }
    }

    if (transferred) {
      document.set(HtmlRenderer.RECHECK_UNDEFINED_REFERENCES, true)
    }
    transferred
  }
}

object Parser {

  val EXTENSIONS: DataKey[java.util.Collection[Extension]] = SharedDataKeys.EXTENSIONS

  val REFERENCES_KEEP: DataKey[KeepType]            = DataKey("REFERENCES_KEEP", KeepType.FIRST)
  val REFERENCES:      DataKey[ReferenceRepository] = DataKey("REFERENCES", new ReferenceRepository(DataHolder.NULL), (options: DataHolder) => ReferenceRepository(options))

  val ASTERISK_DELIMITER_PROCESSOR: DataKey[Boolean] = DataKey("ASTERISK_DELIMITER_PROCESSOR", true)

  val TRACK_DOCUMENT_LINES: DataKey[Boolean] = DataKey("TRACK_DOCUMENT_LINES", false)

  val BLOCK_QUOTE_PARSER:                                     DataKey[Boolean] = DataKey("BLOCK_QUOTE_PARSER", true)
  val BLOCK_QUOTE_EXTEND_TO_BLANK_LINE:                       DataKey[Boolean] = DataKey("BLOCK_QUOTE_EXTEND_TO_BLANK_LINE", false)
  val BLOCK_QUOTE_IGNORE_BLANK_LINE:                          DataKey[Boolean] = DataKey("BLOCK_QUOTE_IGNORE_BLANK_LINE", false)
  val BLOCK_QUOTE_ALLOW_LEADING_SPACE:                        DataKey[Boolean] = DataKey("BLOCK_QUOTE_ALLOW_LEADING_SPACE", true)
  val BLOCK_QUOTE_INTERRUPTS_PARAGRAPH:                       DataKey[Boolean] = DataKey("BLOCK_QUOTE_INTERRUPTS_PARAGRAPH", true)
  val BLOCK_QUOTE_INTERRUPTS_ITEM_PARAGRAPH:                  DataKey[Boolean] = DataKey("BLOCK_QUOTE_INTERRUPTS_ITEM_PARAGRAPH", true)
  val BLOCK_QUOTE_WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH: DataKey[Boolean] = DataKey("BLOCK_QUOTE_WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH", true)

  val FENCED_CODE_BLOCK_PARSER:       DataKey[Boolean] = DataKey("FENCED_CODE_BLOCK_PARSER", true)
  val MATCH_CLOSING_FENCE_CHARACTERS: DataKey[Boolean] = DataKey("MATCH_CLOSING_FENCE_CHARACTERS", true)
  val FENCED_CODE_CONTENT_BLOCK:      DataKey[Boolean] = DataKey("FENCED_CODE_CONTENT_BLOCK", false)

  val CODE_SOFT_LINE_BREAKS: DataKey[Boolean] = DataKey("CODE_SOFT_LINE_BREAKS", false)
  val HARD_LINE_BREAK_LIMIT: DataKey[Boolean] = DataKey("HARD_LINE_BREAK_LIMIT", false)

  val HEADING_PARSER:                         DataKey[Boolean] = DataKey("HEADING_PARSER", true)
  val HEADING_SETEXT_MARKER_LENGTH:           DataKey[Int]     = DataKey("HEADING_SETEXT_MARKER_LENGTH", 1)
  val HEADING_NO_ATX_SPACE:                   DataKey[Boolean] = SharedDataKeys.HEADING_NO_ATX_SPACE
  val ESCAPE_HEADING_NO_ATX_SPACE:            DataKey[Boolean] = SharedDataKeys.ESCAPE_HEADING_NO_ATX_SPACE
  val HEADING_NO_EMPTY_HEADING_WITHOUT_SPACE: DataKey[Boolean] = DataKey("HEADING_NO_EMPTY_HEADING_WITHOUT_SPACE", false)
  val HEADING_NO_LEAD_SPACE:                  DataKey[Boolean] = DataKey("HEADING_NO_LEAD_SPACE", false)
  val HEADING_CAN_INTERRUPT_ITEM_PARAGRAPH:   DataKey[Boolean] = DataKey("HEADING_CAN_INTERRUPT_ITEM_PARAGRAPH", true)

  val HTML_BLOCK_PARSER:                       DataKey[Boolean] = DataKey("HTML_BLOCK_PARSER", true)
  val HTML_COMMENT_BLOCKS_INTERRUPT_PARAGRAPH: DataKey[Boolean] = DataKey("HTML_COMMENT_BLOCKS_INTERRUPT_PARAGRAPH", true)
  val HTML_FOR_TRANSLATOR:                     DataKey[Boolean] = SharedDataKeys.HTML_FOR_TRANSLATOR

  val INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS: DataKey[Boolean] = DataKey("INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS", false)

  val INDENTED_CODE_BLOCK_PARSER:            DataKey[Boolean] = DataKey("INDENTED_CODE_BLOCK_PARSER", true)
  val INDENTED_CODE_NO_TRAILING_BLANK_LINES: DataKey[Boolean] = DataKey("INDENTED_CODE_NO_TRAILING_BLANK_LINES", true)

  val INTELLIJ_DUMMY_IDENTIFIER: DataKey[Boolean] = SharedDataKeys.INTELLIJ_DUMMY_IDENTIFIER

  val MATCH_NESTED_LINK_REFS_FIRST:     DataKey[Boolean] = DataKey("MATCH_NESTED_LINK_REFS_FIRST", true)
  val PARSE_INNER_HTML_COMMENTS:        DataKey[Boolean] = SharedDataKeys.PARSE_INNER_HTML_COMMENTS
  val PARSE_MULTI_LINE_IMAGE_URLS:      DataKey[Boolean] = DataKey("PARSE_MULTI_LINE_IMAGE_URLS", false)
  val PARSE_JEKYLL_MACROS_IN_URLS:      DataKey[Boolean] = DataKey("PARSE_JEKYLL_MACROS_IN_URLS", false)
  val SPACE_IN_LINK_URLS:               DataKey[Boolean] = DataKey("SPACE_IN_LINK_URLS", false)
  val SPACE_IN_LINK_ELEMENTS:           DataKey[Boolean] = DataKey("SPACE_IN_LINK_ELEMENTS", false)
  val WWW_AUTO_LINK_ELEMENT:            DataKey[Boolean] = DataKey("WWW_AUTO_LINK_ELEMENT", false)
  val LINK_TEXT_PRIORITY_OVER_LINK_REF: DataKey[Boolean] = DataKey("LINK_TEXT_PRIORITY_OVER_LINK_REF", false)

  val REFERENCE_PARAGRAPH_PRE_PROCESSOR: DataKey[Boolean] = DataKey("REFERENCE_BLOCK_PRE_PROCESSOR", true)
  val THEMATIC_BREAK_PARSER:             DataKey[Boolean] = DataKey("THEMATIC_BREAK_PARSER", true)
  val THEMATIC_BREAK_RELAXED_START:      DataKey[Boolean] = DataKey("THEMATIC_BREAK_RELAXED_START", true)

  val UNDERSCORE_DELIMITER_PROCESSOR:    DataKey[Boolean] = DataKey("UNDERSCORE_DELIMITER_PROCESSOR", true)
  val BLANK_LINES_IN_AST:                DataKey[Boolean] = SharedDataKeys.BLANK_LINES_IN_AST
  val USE_HARDCODED_LINK_ADDRESS_PARSER: DataKey[Boolean] = DataKey("USE_HARDCODED_LINK_ADDRESS_PARSER", true)

  /** STRONG_WRAPS_EMPHASIS default false, when true makes parsing CommonMark Spec 0.27 compliant */
  val STRONG_WRAPS_EMPHASIS: DataKey[Boolean] = DataKey("STRONG_WRAPS_EMPHASIS", false)

  /** LINKS_ALLOW_MATCHED_PARENTHESES default true, when false makes parsing CommonMark Spec 0.27 compliant */
  val LINKS_ALLOW_MATCHED_PARENTHESES: DataKey[Boolean] = DataKey("LINKS_ALLOW_MATCHED_PARENTHESES", true)

  // the meat of differences in emulation
  val LIST_BLOCK_PARSER:        DataKey[Boolean]                = DataKey("LIST_BLOCK_PARSER", true)
  val PARSER_EMULATION_PROFILE: DataKey[ParserEmulationProfile] = DataKey("PARSER_EMULATION_PROFILE", ParserEmulationProfile.COMMONMARK)

  // deep HTML block parsing
  val HTML_BLOCK_DEEP_PARSER:              DataKey[Boolean] = DataKey("HTML_BLOCK_DEEP_PARSER", false)
  val HTML_BLOCK_DEEP_PARSE_NON_BLOCK:     DataKey[Boolean] = DataKey("HTML_BLOCK_DEEP_PARSE_NON_BLOCK", true)
  val HTML_BLOCK_COMMENT_ONLY_FULL_LINE:   DataKey[Boolean] = DataKey("HTML_BLOCK_COMMENT_ONLY_FULL_LINE", false)
  val HTML_BLOCK_START_ONLY_ON_BLOCK_TAGS: DataKey[Boolean] = DataKey("HTML_BLOCK_START_ONLY_ON_BLOCK_TAGS", HTML_BLOCK_DEEP_PARSER)

  val HTML_BLOCK_TAGS: DataKey[List[String]] = DataKey(
    "HTML_BLOCK_TAGS",
    List(
      "address",
      "article",
      "aside",
      "base",
      "basefont",
      "blockquote",
      "body",
      "caption",
      "center",
      "col",
      "colgroup",
      "dd",
      "details",
      "dialog",
      "dir",
      "div",
      "dl",
      "dt",
      "fieldset",
      "figcaption",
      "figure",
      "footer",
      "form",
      "frame",
      "frameset",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "head",
      "header",
      "hr",
      "html",
      "iframe",
      "legend",
      "li",
      "link",
      "main",
      "math",
      "menu",
      "menuitem",
      "meta",
      "nav",
      "noframes",
      "ol",
      "optgroup",
      "option",
      "p",
      "param",
      "section",
      "source",
      "summary",
      "table",
      "tbody",
      "td",
      "tfoot",
      "th",
      "thead",
      "title",
      "tr",
      "track",
      "ul"
    )
  )

  /** Blank line interrupts HTML block when not in raw tag, otherwise only when closed */
  val HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS: DataKey[Boolean] = DataKey("HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS", true)

  /** Open tags must be contained on one line */
  val HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE: DataKey[Boolean] = DataKey("HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE", false)

  /** Other markdown elements can interrupt a closed block without an intervening blank line */
  val HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED: DataKey[Boolean] = DataKey("HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED", false)

  /** Blank line interrupts partially open tag */
  val HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG: DataKey[Boolean] = DataKey("HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG", true)

  /** Indented code can interrupt HTML block */
  val HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS: DataKey[Boolean] = DataKey("HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS", false)

  /** Name spaces are allowed in HTML elements, default false for backward compatibility */
  val HTML_ALLOW_NAME_SPACE: DataKey[Boolean] = DataKey("HTML_ALLOW_NAME_SPACE", false)

  /** Used by formatter for translation parsing */
  val TRANSLATION_HTML_BLOCK_TAG_PATTERN:  DataKey[String] = SharedDataKeys.TRANSLATION_HTML_BLOCK_TAG_PATTERN
  val TRANSLATION_HTML_INLINE_TAG_PATTERN: DataKey[String] = SharedDataKeys.TRANSLATION_HTML_INLINE_TAG_PATTERN
  val TRANSLATION_AUTOLINK_TAG_PATTERN:    DataKey[String] = SharedDataKeys.TRANSLATION_AUTOLINK_TAG_PATTERN

  val LISTS_CODE_INDENT:                   DataKey[Int]           = DataKey("LISTS_CODE_INDENT", 4)
  val LISTS_ITEM_INDENT:                   DataKey[Int]           = DataKey("LISTS_ITEM_INDENT", 4)
  val LISTS_NEW_ITEM_CODE_INDENT:          DataKey[Int]           = DataKey("LISTS_NEW_ITEM_CODE_INDENT", 4)
  val LISTS_ITEM_MARKER_SPACE:             DataKey[Boolean]       = DataKey("LISTS_ITEM_MARKER_SPACE", false)
  val LISTS_ITEM_MARKER_SUFFIXES:          DataKey[Array[String]] = DataKey("LISTS_ITEM_MARKER_SUFFIXES", Array.empty[String])
  val LISTS_NUMBERED_ITEM_MARKER_SUFFIXED: DataKey[Boolean]       = DataKey("LISTS_NUMBERED_ITEM_MARKER_SUFFIXED", true)

  val LISTS_AUTO_LOOSE:                                        DataKey[Boolean] = DataKey("LISTS_AUTO_LOOSE", true)
  val LISTS_AUTO_LOOSE_ONE_LEVEL_LISTS:                        DataKey[Boolean] = DataKey("LISTS_AUTO_LOOSE_ONE_LEVEL_LISTS", false)
  val LISTS_LOOSE_WHEN_PREV_HAS_TRAILING_BLANK_LINE:           DataKey[Boolean] = DataKey("LISTS_LOOSE_WHEN_PREV_HAS_TRAILING_BLANK_LINE", false)
  val LISTS_LOOSE_WHEN_LAST_ITEM_PREV_HAS_TRAILING_BLANK_LINE: DataKey[Boolean] = DataKey("LISTS_LOOSE_WHEN_LAST_ITEM_PREV_HAS_TRAILING_BLANK_LINE", false)
  val LISTS_LOOSE_WHEN_HAS_NON_LIST_CHILDREN:                  DataKey[Boolean] = DataKey("LISTS_LOOSE_WHEN_HAS_NON_LIST_CHILDREN", false)
  val LISTS_LOOSE_WHEN_BLANK_LINE_FOLLOWS_ITEM_PARAGRAPH:      DataKey[Boolean] = DataKey("LISTS_LOOSE_WHEN_BLANK_LINE_FOLLOWS_ITEM_PARAGRAPH", false)
  val LISTS_LOOSE_WHEN_HAS_LOOSE_SUB_ITEM:                     DataKey[Boolean] = DataKey("LISTS_LOOSE_WHEN_HAS_LOOSE_SUB_ITEM", false)
  val LISTS_LOOSE_WHEN_HAS_TRAILING_BLANK_LINE:                DataKey[Boolean] = DataKey("LISTS_LOOSE_WHEN_HAS_TRAILING_BLANK_LINE", true)
  val LISTS_LOOSE_WHEN_CONTAINS_BLANK_LINE:                    DataKey[Boolean] = DataKey("LISTS_LOOSE_WHEN_CONTAINS_BLANK_LINE", false)
  val LISTS_DELIMITER_MISMATCH_TO_NEW_LIST:                    DataKey[Boolean] = DataKey("LISTS_DELIMITER_MISMATCH_TO_NEW_LIST", true)
  val LISTS_END_ON_DOUBLE_BLANK:                               DataKey[Boolean] = DataKey("LISTS_END_ON_DOUBLE_BLANK", false)
  val LISTS_ITEM_TYPE_MISMATCH_TO_NEW_LIST:                    DataKey[Boolean] = DataKey("LISTS_ITEM_TYPE_MISMATCH_TO_NEW_LIST", true)
  val LISTS_ITEM_TYPE_MISMATCH_TO_SUB_LIST:                    DataKey[Boolean] = DataKey("LISTS_ITEM_TYPE_MISMATCH_TO_SUB_LIST", false)
  val LISTS_ORDERED_ITEM_DOT_ONLY:                             DataKey[Boolean] = DataKey("LISTS_ORDERED_ITEM_DOT_ONLY", false)
  val LISTS_ORDERED_LIST_MANUAL_START:                         DataKey[Boolean] = DataKey("LISTS_ORDERED_LIST_MANUAL_START", true)
  val LISTS_ITEM_CONTENT_AFTER_SUFFIX:                         DataKey[Boolean] = DataKey("LISTS_ITEM_CONTENT_AFTER_SUFFIX", false)

  // List Item paragraph interruption capabilities
  val LISTS_BULLET_ITEM_INTERRUPTS_PARAGRAPH:          DataKey[Boolean] = DataKey("LISTS_BULLET_ITEM_INTERRUPTS_PARAGRAPH", true)
  val LISTS_ORDERED_ITEM_INTERRUPTS_PARAGRAPH:         DataKey[Boolean] = DataKey("LISTS_ORDERED_ITEM_INTERRUPTS_PARAGRAPH", true)
  val LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH: DataKey[Boolean] = DataKey("LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH", false)

  val LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_PARAGRAPH:          DataKey[Boolean] = DataKey("LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_PARAGRAPH", false)
  val LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_PARAGRAPH:         DataKey[Boolean] = DataKey("LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_PARAGRAPH", false)
  val LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH: DataKey[Boolean] = DataKey("LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH", false)

  val LISTS_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH:          DataKey[Boolean] = DataKey("LISTS_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH", true)
  val LISTS_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH:         DataKey[Boolean] = DataKey("LISTS_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH", true)
  val LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH: DataKey[Boolean] = DataKey("LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH", true)

  val LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH:          DataKey[Boolean] = DataKey("LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH", true)
  val LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH:         DataKey[Boolean] = DataKey("LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH", true)
  val LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH: DataKey[Boolean] = DataKey("LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH", true)

  val LISTS_EMPTY_BULLET_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH:          DataKey[Boolean] = DataKey("LISTS_EMPTY_BULLET_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH", false)
  val LISTS_EMPTY_ORDERED_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH:         DataKey[Boolean] = DataKey("LISTS_EMPTY_ORDERED_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH", false)
  val LISTS_EMPTY_ORDERED_NON_ONE_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH: DataKey[Boolean] = DataKey("LISTS_EMPTY_ORDERED_NON_ONE_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH", false)
  val LISTS_ITEM_PREFIX_CHARS:                                        DataKey[String]  = DataKey("LISTS_ITEM_PREFIX_CHARS", "+*-")

  // these are set by the parser for the loaded extensions
  val SPECIAL_LEAD_IN_HANDLERS: DataKey[List[SpecialLeadInHandler]] = DataKey("SPECIAL_LEAD_IN_HANDLERS", List.empty)

  // separate setting for CODE_BLOCK_INDENT
  val CODE_BLOCK_INDENT: DataKey[Int] = DataKey("CODE_BLOCK_INDENT", LISTS_ITEM_INDENT)

  /** Create a new builder for configuring a [[Parser]]. */
  def builder(): Builder = Builder()

  def builder(options: DataHolder): Builder = Builder(options)

  def transferReferences[T <: Node](destination: NodeRepository[T], included: NodeRepository[T], onlyIfUndefined: Boolean): Boolean =
    NodeRepository.transferReferences(destination, included, onlyIfUndefined, Nullable.empty)

  /** Add extension(s) to the extension list.
    */
  def addExtensions(options: MutableDataHolder, extensions: Extension*): MutableDataHolder = {
    val extensionIterable = Parser.EXTENSIONS.get(options)
    val extensionList     = ArrayBuffer.from(extensions)
    extensionList.addAll(extensionIterable.asScala)
    options.set(Parser.EXTENSIONS, extensionList.asJavaCollection)
    options
  }

  /** Remove extension(s) of given class from the extension list.
    */
  def removeExtensions(options: MutableDataHolder, extensions: Class[?]*): MutableDataHolder = {
    val extensionIterable = Parser.EXTENSIONS.get(options)
    val extensionSet      = extensions.toSet
    val filtered          = extensionIterable.asScala.filterNot { ext =>
      extensionSet.exists(_.isInstance(ext))
    }
    options.set(Parser.EXTENSIONS, filtered.asJavaCollection)
    options
  }

  /** Builder for configuring a [[Parser]].
    */
  class Builder private[Parser] (options: Nullable[DataHolder]) extends BuilderBase[Builder](options) {

    val blockParserFactories:           ArrayBuffer[CustomBlockParserFactory]     = ArrayBuffer.empty
    val delimiterProcessors:            ArrayBuffer[DelimiterProcessor]           = ArrayBuffer.empty
    val postProcessorFactories:         ArrayBuffer[PostProcessorFactory]         = ArrayBuffer.empty
    val paragraphPreProcessorFactories: ArrayBuffer[ParagraphPreProcessorFactory] = ArrayBuffer.empty
    val blockPreProcessorFactories:     ArrayBuffer[BlockPreProcessorFactory]     = ArrayBuffer.empty
    val linkRefProcessors:              ArrayBuffer[LinkRefProcessorFactory]      = ArrayBuffer.empty
    val inlineParserExtensionFactories: ArrayBuffer[InlineParserExtensionFactory] = ArrayBuffer.empty
    var inlineParserFactory:            Nullable[InlineParserFactory]             = Nullable.empty
    val specialLeadInHandlers:          ArrayBuffer[SpecialLeadInHandler]         = ArrayBuffer.empty

    loadExtensions()

    def this(opts: DataHolder) =
      this(Nullable(opts))

    def this() =
      this(Nullable.empty[DataHolder])

    /** @return the configured [[Parser]] */
    def build(): Parser = Parser(this)

    override protected def removeApiPoint(apiPoint: AnyRef): Unit =
      apiPoint match {
        case f: CustomBlockParserFactory     => blockParserFactories -= f
        case d: DelimiterProcessor           => delimiterProcessors -= d
        case p: PostProcessorFactory         => postProcessorFactories -= p
        case p: ParagraphPreProcessorFactory => paragraphPreProcessorFactories -= p
        case b: BlockPreProcessorFactory     => blockPreProcessorFactories -= b
        case l: LinkRefProcessorFactory      => linkRefProcessors -= l
        case s: SpecialLeadInHandler         => specialLeadInHandlers -= s
        case i: InlineParserExtensionFactory => inlineParserExtensionFactories -= i
        case _: InlineParserFactory          => inlineParserFactory = Nullable.empty
        case _ => throw IllegalStateException(s"Unknown data point type: ${apiPoint.getClass.getName}")
      }

    override protected def preloadExtension(extension: Extension): Unit =
      extension match {
        case pe: ParserExtension => pe.parserOptions(this)
        case _ =>
      }

    override protected def loadExtension(extension: Extension): Boolean =
      extension match {
        case pe: ParserExtension =>
          pe.extend(this)
          true
        case _ => false
      }

    /** Adds a custom block parser factory.
      *
      * Note that custom factories are applied ''before'' the built-in factories. This is so that extensions can change how some syntax is parsed that would otherwise be handled by built-in factories.
      */
    def customBlockParserFactory(blockParserFactory: CustomBlockParserFactory): Builder = {
      blockParserFactories.addOne(blockParserFactory)
      addExtensionApiPoint(blockParserFactory)
      this
    }

    def customInlineParserExtensionFactory(factory: InlineParserExtensionFactory): Builder = {
      inlineParserExtensionFactories.addOne(factory)
      addExtensionApiPoint(factory)
      this
    }

    def customInlineParserFactory(factory: InlineParserFactory): Builder = {
      if (inlineParserFactory.isDefined) {
        throw IllegalStateException(s"custom inline parser factory is already set to ${inlineParserFactory.get.getClass.getName}")
      }
      inlineParserFactory = Nullable(factory)
      addExtensionApiPoint(factory)
      this
    }

    def customDelimiterProcessor(delimiterProcessor: DelimiterProcessor): Builder = {
      delimiterProcessors.addOne(delimiterProcessor)
      addExtensionApiPoint(delimiterProcessor)
      this
    }

    def postProcessorFactory(postProcessorFactory: PostProcessorFactory): Builder = {
      postProcessorFactories.addOne(postProcessorFactory)
      addExtensionApiPoint(postProcessorFactory)
      this
    }

    def paragraphPreProcessorFactory(factory: ParagraphPreProcessorFactory): Builder = {
      paragraphPreProcessorFactories.addOne(factory)
      addExtensionApiPoint(factory)
      this
    }

    def blockPreProcessorFactory(factory: BlockPreProcessorFactory): Builder = {
      blockPreProcessorFactories.addOne(factory)
      addExtensionApiPoint(factory)
      this
    }

    def linkRefProcessorFactory(factory: LinkRefProcessorFactory): Builder = {
      linkRefProcessors.addOne(factory)
      addExtensionApiPoint(factory)
      this
    }

    def specialLeadInHandler(handler: SpecialLeadInHandler): Builder = {
      specialLeadInHandlers.addOne(handler)
      addExtensionApiPoint(handler)
      this
    }
  }

  /** Extension for [[Parser]].
    *
    * Implementations of this interface should be done by all Extensions that extend the core parser.
    */
  trait ParserExtension extends Extension {

    /** This method is called first on all extensions so that they can adjust the options that must be common to all extensions.
      *
      * @param options
      *   option set that will be used for the builder
      */
    def parserOptions(options: MutableDataHolder): Unit

    /** This method is called on all extensions so that they can register their custom processors.
      *
      * @param parserBuilder
      *   parser builder with which to register extensions
      */
    def extend(parserBuilder: Builder): Unit
  }

  /** Should be implemented by all extensions that create a node repository or other references in the document.
    */
  trait ReferenceHoldingExtension extends Extension {

    /** This method is called to transfer references from included document to the source document.
      *
      * @param document
      *   destination document for references
      * @param included
      *   source document for references
      * @return
      *   true if there were references to transfer
      */
    def transferReferences(document: MutableDataHolder, included: DataHolder): Boolean
  }
}
