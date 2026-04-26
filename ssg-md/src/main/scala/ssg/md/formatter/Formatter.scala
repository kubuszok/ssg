/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/Formatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/Formatter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package formatter

import ssg.md.formatter.internal.{ CoreNodeFormatter, FormatControlProcessor, MergeContextImpl, MergeLinkResolver, TranslationHandlerImpl }
import ssg.md.html.renderer.{ HeaderIdGenerator, HeaderIdGeneratorFactory, HtmlIdGenerator, HtmlIdGeneratorFactory, LinkStatus, LinkType, ResolvedLink }
import ssg.md.html.{ LinkResolver, LinkResolverFactory }
import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.util.ast.*
import ssg.md.util.collection.SubClassingBag
import ssg.md.util.data.*
import ssg.md.util.dependency.DependencyResolver
import ssg.md.util.format.*
import ssg.md.util.format.options.*
import ssg.md.util.html.Attributes
import ssg.md.util.misc.{ CharPredicate, Extension }
import ssg.md.util.sequence.{ BasedSequence, LineAppendable, SequenceUtils }
import ssg.md.util.sequence.builder.{ ISequenceBuilder, SequenceBuilder }

import java.util.Collection
import scala.collection.mutable
import scala.language.implicitConversions

/** Renders a tree of nodes to Markdown.
  *
  * Start with the [[Formatter.builder]] method to configure the renderer. Example:
  * {{{
  * val formatter = Formatter.builder().build()
  * formatter.render(node)
  * }}}
  */
class Formatter private (builder: Formatter.Builder) extends IRender {

  override val options: Nullable[DataHolder] = Nullable(builder.toImmutable)

  private val idGeneratorFactory: HeaderIdGeneratorFactory =
    if (builder.htmlIdGeneratorFactory.isDefined) builder.htmlIdGeneratorFactory.get
    else new HeaderIdGenerator.Factory()

  private val linkResolverFactories: List[LinkResolverFactory] = {
    import scala.jdk.CollectionConverters.*
    DependencyResolver.resolveFlatDependencies(builder.linkResolverFactories.asScala.toList, Nullable.empty, Nullable.empty)
  }

  private val nodeFormatterFactories: List[NodeFormatterFactory] = {
    import scala.jdk.CollectionConverters.*
    Formatter.calculateNodeFormatterFactories(builder.nodeFormatterFactories.asScala.toList)
  }

  def getTranslationHandler(translationHandlerFactory: TranslationHandlerFactory, idGenFactory: HtmlIdGeneratorFactory): TranslationHandler =
    translationHandlerFactory.create(options.get, idGenFactory)

  def getTranslationHandler(idGenFactory: HtmlIdGeneratorFactory): TranslationHandler =
    new TranslationHandlerImpl(options.get, idGenFactory)

  def getTranslationHandler: TranslationHandler =
    new TranslationHandlerImpl(options.get, idGeneratorFactory)

  override def render(node: Node, output: Appendable): Unit =
    render(node, output, Formatter.MAX_TRAILING_BLANK_LINES.get(options))

  def render(node: Node, output: Appendable, maxTrailingBlankLines: Int): Unit = {
    // NOTE: output to MarkdownWriter is only used to get builder if output is LineAppendable or ISequenceBuilder
    val markdown = new MarkdownWriter(Nullable(output), Formatter.FORMAT_FLAGS.get(options))
    val renderer = new Formatter.MainNodeFormatter(this, options.get, markdown, node.document, Nullable.empty)
    renderer.render(node)
    markdown.appendToSilently(output, Formatter.MAX_BLANK_LINES.get(options), maxTrailingBlankLines)

    // resolve any unresolved tracked offsets that are outside elements which resolve their own
    var sequence = node.document.chars
    if (output.isInstanceOf[SequenceBuilder] && (node.document.chars ne renderer.trackedSequence)) {
      // have to use alternate builder sequence for tracked offset resolution
      sequence = output.asInstanceOf[SequenceBuilder].toSequence(renderer.trackedSequence)
    }

    TrackedOffsetUtils.resolveTrackedOffsets(
      sequence,
      markdown,
      renderer.trackedOffsets.getUnresolvedOffsets,
      maxTrailingBlankLines,
      SharedDataKeys.RUNNING_TESTS.get(options)
    )
  }

  override def render(document: Node): String = {
    val sb = new java.lang.StringBuilder()
    render(document, sb)
    sb.toString
  }

  def translationRender(document: Node, output: Appendable, translationHandler: TranslationHandler, renderPurpose: RenderPurpose): Unit =
    translationRender(document, output, Formatter.MAX_TRAILING_BLANK_LINES.get(options), translationHandler, renderPurpose)

  def translationRender(document: Node, translationHandler: TranslationHandler, renderPurpose: RenderPurpose): String = {
    val sb = new java.lang.StringBuilder()
    translationRender(document, sb, translationHandler, renderPurpose)
    sb.toString
  }

  def translationRender(document: Node, output: Appendable, maxTrailingBlankLines: Int, translationHandler: TranslationHandler, renderPurpose: RenderPurpose): Unit = {
    translationHandler.setRenderPurpose(renderPurpose)
    val renderer = new Formatter.MainNodeFormatter(
      this,
      options.get,
      new MarkdownWriter(Formatter.FORMAT_FLAGS.get(options) & ~LineAppendable.F_TRIM_LEADING_WHITESPACE),
      document.document,
      Nullable(translationHandler)
    )
    renderer.render(document)
    renderer.flushTo(output, Formatter.MAX_BLANK_LINES.get(options), maxTrailingBlankLines)
  }

  def mergeRender(documents: Array[Document], output: Appendable): Unit =
    mergeRender(documents, output, Formatter.MAX_TRAILING_BLANK_LINES.get(options))

  def mergeRender(documents: java.util.List[Document], output: Appendable): Unit =
    mergeRender(documents.toArray(Formatter.EMPTY_DOCUMENTS), output)

  def mergeRender(documents: Array[Document], maxTrailingBlankLines: Int): String = {
    val sb = new java.lang.StringBuilder()
    mergeRender(documents, sb, maxTrailingBlankLines)
    sb.toString
  }

  def mergeRender(documents: java.util.List[Document], maxTrailingBlankLines: Int): String =
    mergeRender(documents.toArray(Formatter.EMPTY_DOCUMENTS), maxTrailingBlankLines)

  def mergeRender(documents: java.util.List[Document], output: Appendable, maxTrailingBlankLines: Int): Unit =
    mergeRender(documents.toArray(Formatter.EMPTY_DOCUMENTS), output, maxTrailingBlankLines)

  def mergeRender(documents: Array[Document], output: Appendable, maxTrailingBlankLines: Int): Unit = {
    val mergeOptions = new MutableDataSet(options.get)
    mergeOptions.set(Parser.HTML_FOR_TRANSLATOR, true)

    val translationHandlers      = new Array[TranslationHandler](documents.length)
    val translationHandlersTexts = new Array[java.util.List[String]](documents.length)

    val iMax = documents.length
    for (i <- 0 until iMax)
      translationHandlers(i) = getTranslationHandler(idGeneratorFactory)

    val mergeContext  = new MergeContextImpl(documents, translationHandlers)
    val formatFlags   = Formatter.FORMAT_FLAGS.get(options)
    val maxBlankLines = Formatter.MAX_BLANK_LINES.get(options)

    mergeContext.forEachPrecedingDocument(
      Nullable.empty,
      new MergeContextConsumer {
        override def accept(context: TranslationContext, document: Document, index: Int): Unit = {
          val handler = context.asInstanceOf[TranslationHandler]
          handler.setRenderPurpose(RenderPurpose.TRANSLATION_SPANS)
          val renderer = new Formatter.MainNodeFormatter(Formatter.this, mergeOptions, new MarkdownWriter(formatFlags), document, Nullable(handler))
          renderer.render(document)
          translationHandlersTexts(index) = new java.util.ArrayList[String]()
          handler.getTranslatingTexts.foreach(t => translationHandlersTexts(index).add(t))
        }
      }
    )

    val translatedDocuments = new Array[Document](documents.length)

    mergeContext.forEachPrecedingDocument(
      Nullable.empty,
      new MergeContextConsumer {
        override def accept(context: TranslationContext, document: Document, index: Int): Unit = {
          val handler = context.asInstanceOf[TranslationHandler]
          handler.setRenderPurpose(RenderPurpose.TRANSLATED_SPANS)
          val textsScala = (0 until translationHandlersTexts(index).size()).map(i => translationHandlersTexts(index).get(i): CharSequence).toList
          handler.setTranslatedTexts(textsScala)

          val renderer = new Formatter.MainNodeFormatter(Formatter.this, mergeOptions, new MarkdownWriter(formatFlags), document, Nullable(handler))
          renderer.render(document)
          val sb = new java.lang.StringBuilder()
          renderer.flushTo(sb, maxBlankLines, maxTrailingBlankLines)

          translatedDocuments(index) = Parser.builder(mergeOptions: DataHolder).build().parse(sb.toString)
        }
      }
    )

    mergeContext.documents = translatedDocuments

    mergeContext.forEachPrecedingDocument(
      Nullable.empty,
      new MergeContextConsumer {
        override def accept(context: TranslationContext, document: Document, index: Int): Unit = {
          val handler = context.asInstanceOf[TranslationHandler]
          handler.setRenderPurpose(RenderPurpose.TRANSLATED)

          val markdownWriter = new MarkdownWriter(formatFlags)
          val renderer       = new Formatter.MainNodeFormatter(Formatter.this, mergeOptions, markdownWriter, document, Nullable(handler))
          renderer.render(document)
          markdownWriter.blankLine()
          renderer.flushTo(output, maxBlankLines, maxTrailingBlankLines)
        }
      }
    )
  }
}

object Formatter {
  val EMPTY_DOCUMENTS: Array[Document] = Array.empty

  /** output control for FormattingAppendable, see [[LineAppendable.setOptions]]
    */
  val FORMAT_FLAGS: DataKey[Int] = new DataKey[Int]("FORMAT_FLAGS", LineAppendable.F_TRIM_LEADING_WHITESPACE | LineAppendable.F_TRIM_LEADING_EOL)

  @deprecated("Use LineAppendable.F_CONVERT_TABS", "0.60")
  val FORMAT_CONVERT_TABS: Int = LineAppendable.F_CONVERT_TABS
  @deprecated("Use LineAppendable.F_COLLAPSE_WHITESPACE", "0.60")
  val FORMAT_COLLAPSE_WHITESPACE: Int = LineAppendable.F_COLLAPSE_WHITESPACE
  @deprecated("Use LineAppendable.F_TRIM_TRAILING_WHITESPACE", "0.60")
  val FORMAT_SUPPRESS_TRAILING_WHITESPACE: Int = LineAppendable.F_TRIM_TRAILING_WHITESPACE
  @deprecated("Use LineAppendable.F_FORMAT_ALL", "0.60")
  val FORMAT_ALL_OPTIONS: Int = LineAppendable.F_FORMAT_ALL

  val GENERATE_HEADER_ID:       DataKey[Boolean] = new DataKey[Boolean]("GENERATE_HEADER_ID", false)
  val MAX_BLANK_LINES:          DataKey[Int]     = SharedDataKeys.FORMATTER_MAX_BLANK_LINES
  val MAX_TRAILING_BLANK_LINES: DataKey[Int]     = SharedDataKeys.FORMATTER_MAX_TRAILING_BLANK_LINES
  val RIGHT_MARGIN:             DataKey[Int]     = new DataKey[Int]("RIGHT_MARGIN", 0)

  val APPLY_SPECIAL_LEAD_IN_HANDLERS: DataKey[Boolean] = SharedDataKeys.APPLY_SPECIAL_LEAD_IN_HANDLERS
  val ESCAPE_SPECIAL_CHARS:           DataKey[Boolean] = SharedDataKeys.ESCAPE_SPECIAL_CHARS
  val ESCAPE_NUMBERED_LEAD_IN:        DataKey[Boolean] = SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN
  val UNESCAPE_SPECIAL_CHARS:         DataKey[Boolean] = SharedDataKeys.UNESCAPE_SPECIAL_CHARS

  val SPACE_AFTER_ATX_MARKER:           DataKey[DiscretionaryText]               = new DataKey[DiscretionaryText]("SPACE_AFTER_ATX_MARKER", DiscretionaryText.ADD)
  val SETEXT_HEADING_EQUALIZE_MARKER:   DataKey[Boolean]                         = new DataKey[Boolean]("SETEXT_HEADING_EQUALIZE_MARKER", true)
  val ATX_HEADING_TRAILING_MARKER:      DataKey[EqualizeTrailingMarker]          = new DataKey[EqualizeTrailingMarker]("ATX_HEADING_TRAILING_MARKER", EqualizeTrailingMarker.AS_IS)
  val HEADING_STYLE:                    DataKey[HeadingStyle]                    = new DataKey[HeadingStyle]("HEADING_STYLE", HeadingStyle.AS_IS)
  val THEMATIC_BREAK:                   NullableDataKey[String]                  = new NullableDataKey[String]("THEMATIC_BREAK")
  val BLOCK_QUOTE_BLANK_LINES:          DataKey[Boolean]                         = SharedDataKeys.BLOCK_QUOTE_BLANK_LINES
  val BLOCK_QUOTE_MARKERS:              DataKey[BlockQuoteMarker]                = new DataKey[BlockQuoteMarker]("BLOCK_QUOTE_MARKERS", BlockQuoteMarker.ADD_COMPACT_WITH_SPACE)
  val INDENTED_CODE_MINIMIZE_INDENT:    DataKey[Boolean]                         = new DataKey[Boolean]("INDENTED_CODE_MINIMIZE_INDENT", true)
  val FENCED_CODE_MINIMIZE_INDENT:      DataKey[Boolean]                         = new DataKey[Boolean]("FENCED_CODE_MINIMIZE_INDENT", true)
  val FENCED_CODE_MATCH_CLOSING_MARKER: DataKey[Boolean]                         = new DataKey[Boolean]("FENCED_CODE_MATCH_CLOSING_MARKER", true)
  val FENCED_CODE_SPACE_BEFORE_INFO:    DataKey[Boolean]                         = new DataKey[Boolean]("FENCED_CODE_SPACE_BEFORE_INFO", false)
  val FENCED_CODE_MARKER_LENGTH:        DataKey[Int]                             = new DataKey[Int]("FENCED_CODE_MARKER_LENGTH", 3)
  val FENCED_CODE_MARKER_TYPE:          DataKey[CodeFenceMarker]                 = new DataKey[CodeFenceMarker]("FENCED_CODE_MARKER_TYPE", CodeFenceMarker.ANY)
  val LIST_ADD_BLANK_LINE_BEFORE:       DataKey[Boolean]                         = new DataKey[Boolean]("LIST_ADD_BLANK_LINE_BEFORE", false)
  val LIST_RENUMBER_ITEMS:              DataKey[Boolean]                         = new DataKey[Boolean]("LIST_RENUMBER_ITEMS", true)
  val LIST_REMOVE_EMPTY_ITEMS:          DataKey[Boolean]                         = new DataKey[Boolean]("LIST_REMOVE_EMPTY_ITEMS", false)
  val LIST_ALIGN_NUMERIC:               DataKey[ElementAlignment]                = new DataKey[ElementAlignment]("LIST_ALIGN_NUMERIC", ElementAlignment.NONE)
  val LIST_RESET_FIRST_ITEM_NUMBER:     DataKey[Boolean]                         = new DataKey[Boolean]("LIST_RESET_FIRST_ITEM_NUMBER", false)
  val LIST_BULLET_MARKER:               DataKey[ListBulletMarker]                = new DataKey[ListBulletMarker]("LIST_BULLET_MARKER", ListBulletMarker.ANY)
  val LIST_NUMBERED_MARKER:             DataKey[ListNumberedMarker]              = new DataKey[ListNumberedMarker]("LIST_NUMBERED_MARKER", ListNumberedMarker.ANY)
  val LIST_SPACING:                     DataKey[ListSpacing]                     = new DataKey[ListSpacing]("LIST_SPACING", ListSpacing.AS_IS)
  val LISTS_ITEM_CONTENT_AFTER_SUFFIX:  DataKey[Boolean]                         = new DataKey[Boolean]("LISTS_ITEM_CONTENT_AFTER_SUFFIX", false)
  val REFERENCE_PLACEMENT:              DataKey[ElementPlacement]                = new DataKey[ElementPlacement]("REFERENCE_PLACEMENT", ElementPlacement.AS_IS)
  val REFERENCE_SORT:                   DataKey[ElementPlacementSort]            = new DataKey[ElementPlacementSort]("REFERENCE_SORT", ElementPlacementSort.AS_IS)
  val KEEP_IMAGE_LINKS_AT_START:        DataKey[Boolean]                         = new DataKey[Boolean]("KEEP_IMAGE_LINKS_AT_START", false)
  val KEEP_EXPLICIT_LINKS_AT_START:     DataKey[Boolean]                         = new DataKey[Boolean]("KEEP_EXPLICIT_LINKS_AT_START", false)
  val OPTIMIZED_INLINE_RENDERING:       DataKey[Boolean]                         = new DataKey[Boolean]("OPTIMIZED_INLINE_RENDERING", false)
  val FORMAT_CHAR_WIDTH_PROVIDER:       DataKey[CharWidthProvider]               = TableFormatOptions.FORMAT_CHAR_WIDTH_PROVIDER
  val KEEP_HARD_LINE_BREAKS:            DataKey[Boolean]                         = new DataKey[Boolean]("KEEP_HARD_LINE_BREAKS", true)
  val KEEP_SOFT_LINE_BREAKS:            DataKey[Boolean]                         = new DataKey[Boolean]("KEEP_SOFT_LINE_BREAKS", true)
  val FORMATTER_ON_TAG:                 DataKey[String]                          = new DataKey[String]("FORMATTER_ON_TAG", "@formatter:on")
  val FORMATTER_OFF_TAG:                DataKey[String]                          = new DataKey[String]("FORMATTER_OFF_TAG", "@formatter:off")
  val FORMATTER_TAGS_ENABLED:           DataKey[Boolean]                         = new DataKey[Boolean]("FORMATTER_TAGS_ENABLED", false)
  val FORMATTER_TAGS_ACCEPT_REGEXP:     DataKey[Boolean]                         = new DataKey[Boolean]("FORMATTER_TAGS_ACCEPT_REGEXP", false)
  val LINK_MARKER_COMMENT_PATTERN:      NullableDataKey[java.util.regex.Pattern] = new NullableDataKey[java.util.regex.Pattern]("FORMATTER_TAGS_ACCEPT_REGEXP")

  val APPEND_TRANSFERRED_REFERENCES: DataKey[Boolean] = new DataKey[Boolean]("APPEND_TRANSFERRED_REFERENCES", false)

  // used during translation
  val UNIQUIFICATION_MAP: DataKey[java.util.Map[String, String]] = new DataKey[java.util.Map[String, String]](
    "REFERENCES_UNIQUIFICATION_MAP",
    new NotNullValueSupplier[java.util.Map[String, String]] { def get: java.util.Map[String, String] = new java.util.HashMap[String, String]() }
  )
  val ATTRIBUTE_UNIQUIFICATION_ID_MAP: DataKey[java.util.Map[String, String]] = new DataKey[java.util.Map[String, String]](
    "ATTRIBUTE_UNIQUIFICATION_ID_MAP",
    new NotNullValueSupplier[java.util.Map[String, String]] { def get: java.util.Map[String, String] = new java.util.HashMap[String, String]() }
  )

  // used for translation phases of rendering
  val TRANSLATION_ID_FORMAT:          DataKey[String] = new DataKey[String]("TRANSLATION_ID_FORMAT", "_%d_")
  val TRANSLATION_HTML_BLOCK_PREFIX:  DataKey[String] = new DataKey[String]("TRANSLATION_HTML_BLOCK_PREFIX", "__")
  val TRANSLATION_HTML_INLINE_PREFIX: DataKey[String] = new DataKey[String]("TRANSLATION_HTML_INLINE_PREFIX", "_")
  val TRANSLATION_AUTOLINK_PREFIX:    DataKey[String] = new DataKey[String]("TRANSLATION_AUTOLINK_PREFIX", "___")
  // Cross-platform: original Java regex used \p{IsAlphabetic} Unicode property which
  // is unavailable on Scala.js and Scala Native. Replaced with [a-zA-Z] for ASCII
  // alphabetic check, which is sufficient for the translation exclusion feature.
  // Original: "^[^\\p{IsAlphabetic}]*$"
  // Revert to original if/when Scala.js and Scala Native add full java.util.regex support.
  val TRANSLATION_EXCLUDE_PATTERN:         DataKey[String] = new DataKey[String]("TRANSLATION_EXCLUDE_PATTERN", "^[^a-zA-Z]*$")
  val TRANSLATION_HTML_BLOCK_TAG_PATTERN:  DataKey[String] = SharedDataKeys.TRANSLATION_HTML_BLOCK_TAG_PATTERN
  val TRANSLATION_HTML_INLINE_TAG_PATTERN: DataKey[String] = SharedDataKeys.TRANSLATION_HTML_INLINE_TAG_PATTERN

  // link resolver info for doc relative and doc root urls
  val DOC_RELATIVE_URL:      DataKey[String]  = new DataKey[String]("DOC_RELATIVE_URL", "")
  val DOC_ROOT_URL:          DataKey[String]  = new DataKey[String]("DOC_ROOT_URL", "")
  val DEFAULT_LINK_RESOLVER: DataKey[Boolean] = new DataKey[Boolean]("DEFAULT_LINK_RESOLVER", false)

  // formatter family override
  val FORMATTER_EMULATION_PROFILE: DataKey[ParserEmulationProfile] = new DataKey[ParserEmulationProfile]("FORMATTER_EMULATION_PROFILE", Parser.PARSER_EMULATION_PROFILE)

  // tracked offsets
  val TRACKED_OFFSETS:        DataKey[List[TrackedOffset]] = new DataKey[List[TrackedOffset]]("TRACKED_OFFSETS", List.empty)
  val TRACKED_SEQUENCE:       DataKey[BasedSequence]       = new DataKey[BasedSequence]("TRACKED_SEQUENCE", BasedSequence.NULL)
  val RESTORE_TRACKED_SPACES: DataKey[Boolean]             = new DataKey[Boolean]("RESTORE_END_SPACES", false)
  val DOCUMENT_FIRST_PREFIX:  DataKey[CharSequence]        = new DataKey[CharSequence]("DOCUMENT_FIRST_PREFIX", BasedSequence.NULL)
  val DOCUMENT_PREFIX:        DataKey[CharSequence]        = new DataKey[CharSequence]("DOCUMENT_PREFIX", BasedSequence.NULL)

  @deprecated("Use SETEXT_HEADING_EQUALIZE_MARKER", "0.62")
  val SETEXT_HEADER_EQUALIZE_MARKER: DataKey[Boolean] = SETEXT_HEADING_EQUALIZE_MARKER
  @deprecated("Use ATX_HEADING_TRAILING_MARKER", "0.62")
  val ATX_HEADER_TRAILING_MARKER: DataKey[EqualizeTrailingMarker] = ATX_HEADING_TRAILING_MARKER

  def builder(): Builder = new Builder()

  def builder(options: Nullable[DataHolder]): Builder = new Builder(options)

  private def calculateNodeFormatterFactories(formatterFactories: List[NodeFormatterFactory]): List[NodeFormatterFactory] = {
    // By having the custom factories come first, extensions are able to change behavior of core syntax.
    val list = formatterFactories :+ new CoreNodeFormatter.Factory()
    DependencyResolver.resolveFlatDependencies(list, Nullable.empty, Nullable.empty)
  }

  val NULL_ITERABLE: Iterable[Node] = Iterable.empty[Node]

  /** Builder for configuring a [[Formatter]].
    */
  class Builder(options: Nullable[DataHolder]) extends ssg.md.util.build.BuilderBase[Builder](options) {

    def this() =
      this(Nullable.empty)

    if (options.isDefined) {
      loadExtensions()
    }

    private[formatter] val nodeFormatterFactories: java.util.List[NodeFormatterFactory] = new java.util.ArrayList[NodeFormatterFactory]()
    private[formatter] val linkResolverFactories:  java.util.List[LinkResolverFactory]  = new java.util.ArrayList[LinkResolverFactory]()
    private[formatter] var htmlIdGeneratorFactory: Nullable[HeaderIdGeneratorFactory]   = Nullable.empty

    override protected def removeApiPoint(apiPoint: AnyRef): Unit =
      apiPoint match {
        case f: NodeFormatterFactory     => nodeFormatterFactories.remove(f)
        case f: LinkResolverFactory      => linkResolverFactories.remove(f)
        case _: HeaderIdGeneratorFactory => htmlIdGeneratorFactory = Nullable.empty
        case _ => throw new IllegalStateException("Unknown data point type: " + apiPoint.getClass.getName)
      }

    override protected def preloadExtension(extension: Extension): Unit =
      extension match {
        case fe: FormatterExtension => fe.rendererOptions(this)
        case _ => ()
      }

    override protected def loadExtension(extension: Extension): Boolean =
      extension match {
        case fe: FormatterExtension =>
          fe.extend(this, "")
          true
        case _ => false
      }

    def nodeFormatterFactory(factory: NodeFormatterFactory): Builder = {
      nodeFormatterFactories.add(factory)
      this
    }

    def linkResolverFactory(factory: LinkResolverFactory): Builder = {
      linkResolverFactories.add(factory)
      addExtensionApiPoint(factory)
      this
    }

    def htmlIdGeneratorFactory(factory: HeaderIdGeneratorFactory): Builder = {
      if (htmlIdGeneratorFactory.isDefined) {
        throw new IllegalStateException("custom header id factory is already set to " + factory.getClass.getName)
      }
      htmlIdGeneratorFactory = Nullable(factory)
      addExtensionApiPoint(factory)
      this
    }

    def build(): Formatter = new Formatter(this)
  }

  /** Extension for [[Formatter]].
    */
  trait FormatterExtension extends Extension {
    def rendererOptions(options: MutableDataHolder):             Unit
    def extend(formatterBuilder: Builder, rendererType: String): Unit
  }

  /** The main node formatter that handles document rendering and dispatching to specific node formatters.
    */
  private[formatter] class MainNodeFormatter(
    formatter:          Formatter,
    opts:               DataHolder,
    out:                MarkdownWriter,
    doc:                Document,
    translationHandler: Nullable[TranslationHandler]
  ) extends NodeFormatterSubContext(out) {

    private val document:               Document                                                            = doc
    private val renderers:              mutable.HashMap[Class[?], mutable.Buffer[NodeFormattingHandler[?]]] = mutable.HashMap.empty
    private var collectedNodes:         Nullable[SubClassingBag[Node]]                                      = Nullable.empty
    private val phasedFormatters:       mutable.Buffer[PhasedNodeFormatter]                                 = mutable.ArrayBuffer.empty
    private val renderingPhases:        mutable.Set[FormattingPhase]                                        = mutable.HashSet.empty
    private val myOptions:              DataHolder                                                          = new ScopedDataSet(document, opts)
    private val isFormatControlEnabled: Boolean                                                             = FORMATTER_TAGS_ENABLED.get(Nullable(myOptions))
    private var phase:                  FormattingPhase                                                     = FormattingPhase.DOCUMENT
    private val linkResolvers:          Array[LinkResolver]                                                 = {
      val defaultLinkResolver = DEFAULT_LINK_RESOLVER.get(Nullable(myOptions))
      val arr                 = new Array[LinkResolver](formatter.linkResolverFactories.size + (if (defaultLinkResolver) 1 else 0))
      for (i <- formatter.linkResolverFactories.indices)
        arr(i) = formatter.linkResolverFactories(i).apply(this)
      if (defaultLinkResolver) {
        arr(formatter.linkResolverFactories.size) = new MergeLinkResolver.Factory().apply(this)
      }
      arr
    }
    private val resolvedLinkMap:               mutable.HashMap[LinkType, mutable.HashMap[String, ResolvedLink]] = mutable.HashMap.empty
    private var myExplicitAttributeIdProvider: Nullable[ExplicitAttributeIdProvider]                            = Nullable.empty
    private val idGenerator:                   Nullable[HtmlIdGenerator]                                        =
      if (GENERATE_HEADER_ID.get(Nullable(myOptions)))
        Nullable(formatter.idGeneratorFactory.create(this))
      else Nullable.empty
    private var controlProcessor:        Nullable[FormatControlProcessor] = Nullable.empty
    private val blockQuoteLikePredicate: CharPredicate                    = {
      val sb               = new java.lang.StringBuilder()
      val collectNodeTypes = new java.util.HashSet[Class[?]]()
      val factories        = formatter.nodeFormatterFactories
      for (i <- factories.indices.reverse) {
        val nodeFormatter = factories(i).create(myOptions)

        if (nodeFormatter.isInstanceOf[ExplicitAttributeIdProvider]) {
          myExplicitAttributeIdProvider = Nullable(nodeFormatter.asInstanceOf[ExplicitAttributeIdProvider])
        }

        val blockLikePrefixChar = nodeFormatter.getBlockQuoteLikePrefixChar
        if (blockLikePrefixChar != SequenceUtils.NUL) {
          sb.append(blockLikePrefixChar)
        }

        val formattingHandlers = nodeFormatter.getNodeFormattingHandlers
        formattingHandlers.foreach { handlers =>
          for (handler <- handlers) {
            val rendererList = renderers.getOrElseUpdate(handler.nodeType, mutable.ArrayBuffer.empty)
            rendererList.prepend(handler)
          }
        }

        // get nodes of interest
        val nodeClasses = nodeFormatter.getNodeClasses
        nodeClasses.foreach { classes =>
          for (cls <- classes) collectNodeTypes.add(cls)
        }

        nodeFormatter match {
          case pf: PhasedNodeFormatter =>
            val phases = pf.getFormattingPhases
            phases.foreach { p =>
              if (p.isEmpty) throw new IllegalStateException("PhasedNodeFormatter with empty Phases")
              renderingPhases ++= p
              phasedFormatters += pf
            }
          case _ => ()
        }
      }

      // collect nodes of interest from document
      if (!collectNodeTypes.isEmpty) {
        val collectingVisitor = new NodeCollectingVisitor(collectNodeTypes)
        collectingVisitor.collect(document)
        collectedNodes = Nullable(collectingVisitor.getSubClassingBag)
      }

      CharPredicate.anyOf(sb.toString)
    }
    private val blockQuoteLikeChars: BasedSequence = BasedSequence.of(blockQuoteLikePredicate.toString)

    val restoreTrackedSpaces:     Boolean       = RESTORE_TRACKED_SPACES.get(Nullable(myOptions))
    private val _trackedSequence: BasedSequence = {
      val seq = TRACKED_SEQUENCE.get(Nullable(myOptions))
      if (seq.isEmpty) document.chars else seq
    }
    val trackedSequence: BasedSequence     = _trackedSequence
    val trackedOffsets:  TrackedOffsetList = {
      val offsets = TRACKED_OFFSETS.get(Nullable(myOptions))
      if (offsets.isEmpty) TrackedOffsetList.EMPTY_LIST
      else {
        import scala.jdk.CollectionConverters.*
        TrackedOffsetList.create(_trackedSequence, offsets.asJava)
      }
    }
    val formatterOptions: FormatterOptions = new FormatterOptions(myOptions)

    out.setContext(this)

    idGenerator.foreach(_.generateIds(document))

    override def encodeUrl(url: CharSequence): String = String.valueOf(url)

    override def resolveLink(linkType: LinkType, url: CharSequence, urlEncode: Nullable[Boolean]): ResolvedLink =
      resolveLink(linkType, url, Nullable.empty, urlEncode)

    override def resolveLink(linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes], urlEncode: Nullable[Boolean]): ResolvedLink =
      resolveLinkInContext(this, linkType, url, attributes)

    private def resolveLinkInContext(context: NodeFormatterSubContext, linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes]): ResolvedLink = {
      val resolvedLinks = resolvedLinkMap.getOrElseUpdate(linkType, mutable.HashMap.empty)
      val urlSeq        = String.valueOf(url)
      resolvedLinks.getOrElseUpdate(
        urlSeq, {
          var resolved = new ResolvedLink(linkType, urlSeq, attributes)
          if (urlSeq.nonEmpty) {
            val currentNode = context.renderingNode
            import scala.util.boundary
            import scala.util.boundary.break
            boundary {
              for (linkResolver <- linkResolvers) {
                resolved = linkResolver.resolveLink(currentNode.get, this, resolved)
                if (resolved.status != LinkStatus.UNKNOWN) {
                  break()
                }
              }
            }
          }
          resolved
        }
      )
    }

    override def addExplicitId(node: Node, id: Nullable[String], context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
      id.foreach { idStr =>
        myExplicitAttributeIdProvider.foreach(_.addExplicitId(node, Nullable(idStr), context, markdown))
      }

    override def getRenderPurpose: RenderPurpose =
      translationHandler.fold(RenderPurpose.FORMAT)(_.getRenderPurpose)

    override def isTransformingText: Boolean =
      translationHandler.exists(_.isTransformingText)

    override def transformNonTranslating(prefix: Nullable[CharSequence], nonTranslatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence =
      translationHandler.fold(nonTranslatingText)(_.transformNonTranslating(prefix, nonTranslatingText, suffix, suffix2))

    override def transformTranslating(prefix: Nullable[CharSequence], translatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence =
      translationHandler.fold(translatingText)(_.transformTranslating(prefix, translatingText, suffix, suffix2))

    override def transformAnchorRef(pageRef: CharSequence, anchorRef: CharSequence): CharSequence =
      translationHandler.fold(anchorRef)(_.transformAnchorRef(pageRef, anchorRef))

    override def postProcessNonTranslating(postProcessor: String => CharSequence, scope: Runnable): Unit =
      translationHandler.fold(scope.run())(_.postProcessNonTranslating(postProcessor, scope))

    override def postProcessNonTranslating[T](postProcessor: String => CharSequence, scope: () => T): T =
      translationHandler.fold(scope())(_.postProcessNonTranslating(postProcessor, scope))

    override def isPostProcessingNonTranslating: Boolean =
      translationHandler.exists(_.isPostProcessingNonTranslating)

    override def getMergeContext: Nullable[MergeContext] =
      translationHandler.flatMap(_.getMergeContext)

    override def getIdGenerator: Nullable[HtmlIdGenerator] =
      translationHandler.flatMap(_.getIdGenerator).orElse(idGenerator)

    override def translatingSpan(render: TranslatingSpanRender): Unit =
      translationHandler.fold(render.render(this, markdown))(_.translatingSpan(render))

    override def nonTranslatingSpan(render: TranslatingSpanRender): Unit =
      translationHandler.fold(render.render(this, markdown))(_.nonTranslatingSpan(render))

    override def translatingRefTargetSpan(target: Nullable[Node], render: TranslatingSpanRender): Unit =
      translationHandler.fold(render.render(this, markdown))(_.translatingRefTargetSpan(target, render))

    override def getTranslationStore: MutableDataHolder =
      translationHandler.fold(document: MutableDataHolder)(_.getTranslationStore)

    override def customPlaceholderFormat(generator: TranslationPlaceholderGenerator, render: TranslatingSpanRender): Unit =
      translationHandler.fold(render.render(this, markdown))(_.customPlaceholderFormat(generator, render))

    override def getCurrentNode: Node = renderingNode.get

    override def getOptions: DataHolder = myOptions

    override def getFormatterOptions: FormatterOptions = formatterOptions

    override def getDocument: Document = document

    override def getBlockQuoteLikePrefixPredicate: CharPredicate = blockQuoteLikePredicate

    override def getBlockQuoteLikePrefixChars: BasedSequence = blockQuoteLikeChars

    override def getTrackedOffsets: TrackedOffsetList = trackedOffsets

    override def isRestoreTrackedSpaces: Boolean = restoreTrackedSpaces

    override def getTrackedSequence: BasedSequence = _trackedSequence

    override def getFormattingPhase: FormattingPhase = phase

    override def render(node: Node): Unit = renderNode(node, this)

    override def nodesOfType(classes: Array[Class[?]]): Iterable[? <: Node] =
      collectedNodes.fold(NULL_ITERABLE)(bag => bag.itemsOfType(classOf[Node], classes).asInstanceOf[Iterable[Node]])

    override def nodesOfType(classes: Collection[Class[?]]): Iterable[? <: Node] =
      collectedNodes.fold(NULL_ITERABLE)(bag => bag.itemsOfType(classOf[Node], classes).asInstanceOf[Iterable[Node]])

    override def reversedNodesOfType(classes: Array[Class[?]]): Iterable[? <: Node] =
      collectedNodes.fold(NULL_ITERABLE)(bag => bag.reversedItemsOfType(classOf[Node], classes).asInstanceOf[Iterable[Node]])

    override def reversedNodesOfType(classes: Collection[Class[?]]): Iterable[? <: Node] =
      collectedNodes.fold(NULL_ITERABLE)(bag => bag.reversedItemsOfType(classOf[Node], classes).asInstanceOf[Iterable[Node]])

    override def getSubContext(): NodeFormatterContext =
      getSubContextRaw(Nullable.empty, markdown.getBuilder)

    override def getSubContext(options: Nullable[DataHolder]): NodeFormatterContext =
      getSubContextRaw(options, markdown.getBuilder)

    override def getSubContext(options: Nullable[DataHolder], builder: ISequenceBuilder[?, ?]): NodeFormatterContext =
      getSubContextRaw(options, builder)

    private def getSubContextRaw(options: Nullable[DataHolder], builder: ISequenceBuilder[?, ?]): NodeFormatterContext = {
      val writer = new MarkdownWriter(Nullable(builder), getMarkdown.getOptions)
      writer.setContext(this)
      new SubNodeFormatter(this, writer, options)
    }

    private[formatter] def renderNode(node: Node, subContext: NodeFormatterSubContext): Unit =
      if (node.isInstanceOf[Document]) {
        // here we render multiple phases
        translationHandler.foreach { handler =>
          handler.beginRendering(node.asInstanceOf[Document], subContext, subContext.getMarkdown)
        }

        for (p <- FormattingPhase.values)
          if (p != FormattingPhase.DOCUMENT && !renderingPhases.contains(p)) {
            // skip
          } else {
            this.phase = p
            if (this.phase == FormattingPhase.DOCUMENT) {
              // pre-indent document
              subContext.getMarkdown
                .pushPrefix()
                .setPrefix(DOCUMENT_FIRST_PREFIX.get(Nullable(node.asInstanceOf[Document])), false)
                .setPrefix(DOCUMENT_PREFIX.get(Nullable(node.asInstanceOf[Document])), true)

              val nodeRendererList = renderers.get(node.getClass)
              nodeRendererList.foreach { nrl =>
                subContext.rendererList = Nullable(nrl.toList)
                subContext.rendererIndex = 0
                subContext.renderingNode = Nullable(node)
                nrl(0).render(node, subContext, subContext.getMarkdown)
                subContext.renderingNode = Nullable.empty
                subContext.rendererList = Nullable.empty
                subContext.rendererIndex = -1
              }

              subContext.getMarkdown.popPrefix()
            } else {
              // go through all renderers that want this phase
              for (phasedFormatter <- phasedFormatters) {
                val phases = phasedFormatter.getFormattingPhases
                if (phases.exists(_.contains(p))) {
                  subContext.renderingNode = Nullable(node)
                  phasedFormatter.renderDocument(subContext, subContext.getMarkdown, node.asInstanceOf[Document], p)
                  subContext.renderingNode = Nullable.empty
                }
              }
            }
          }
      } else {
        if (isFormatControlEnabled) {
          if (controlProcessor.isEmpty) {
            val cp = new FormatControlProcessor(document, myOptions)
            cp.initializeFrom(node)
            controlProcessor = Nullable(cp)
          } else {
            controlProcessor.get.processFormatControl(node)
          }
        }

        if (isFormatControlEnabled && controlProcessor.exists(_.isFormattingOff)) {
          if (node.isInstanceOf[BlankLine]) subContext.getMarkdown.blankLine()
          else subContext.getMarkdown.append(node.chars)
        } else {
          var nodeRendererList = renderers.get(node.getClass)

          if (nodeRendererList.isEmpty) {
            nodeRendererList = renderers.get(classOf[Node])
          }

          nodeRendererList match {
            case Some(nrl) =>
              val oldRendererList  = subContext.rendererList
              val oldRendererIndex = subContext.rendererIndex
              val oldRenderingNode = subContext.renderingNode

              subContext.rendererList = Nullable(nrl.toList)
              subContext.rendererIndex = 0
              subContext.renderingNode = Nullable(node)
              nrl(0).render(node, subContext, subContext.getMarkdown)
              subContext.renderingNode = oldRenderingNode
              subContext.rendererList = oldRendererList
              subContext.rendererIndex = oldRendererIndex

            case None =>
              // default behavior is controlled by generic Node.class that is implemented in CoreNodeFormatter
              throw new IllegalStateException("Core Node Formatter should implement generic Node renderer")
          }
        }
      }

    override def renderChildren(parent: Node): Unit = renderChildrenNode(parent, this)

    override def delegateRender(): Unit = delegateRenderInContext(this)

    private def delegateRenderInContext(subContext: NodeFormatterSubContext): Unit = {
      if (subContext.getFormattingPhase != FormattingPhase.DOCUMENT) {
        throw new IllegalStateException("Delegate rendering only supported in document rendering phase")
      }

      if (subContext.rendererList.isEmpty || subContext.renderingNode.isEmpty) {
        throw new IllegalStateException("Delegate rendering can only be called from node render handler")
      }

      val node                 = subContext.renderingNode.get
      val oldRendererList      = subContext.rendererList
      var currentRendererList  = oldRendererList.get
      val oldRendererIndex     = subContext.rendererIndex
      var currentRendererIndex = oldRendererIndex + 1

      if (currentRendererIndex >= currentRendererList.size) {
        if (node.isInstanceOf[Document]) {
          // no default needed, just ignore
        } else {
          // see if there is a default node renderer list
          val nodeRendererList = renderers.get(classOf[Node])
          nodeRendererList match {
            case None =>
              throw new IllegalStateException("Core Node Formatter should implement generic Node renderer")
            case Some(nrl) =>
              if (oldRendererList.exists(_ eq nrl.toList)) {
                throw new IllegalStateException("Core Node Formatter should not delegate generic Node renderer")
              }
              currentRendererList = nrl.toList
              currentRendererIndex = 0
          }
        }
      }

      if (!node.isInstanceOf[Document] || currentRendererIndex < currentRendererList.size) {
        subContext.rendererList = Nullable(currentRendererList)
        subContext.rendererIndex = currentRendererIndex
        currentRendererList(currentRendererIndex).render(node, subContext, subContext.getMarkdown)
        subContext.rendererIndex = oldRendererIndex
        subContext.rendererList = oldRendererList
      }
    }

    private def renderChildrenNode(parent: Node, subContext: NodeFormatterSubContext): Unit = {
      var node = parent.firstChild
      while (node.isDefined) {
        val next = node.get.next
        renderNode(node.get, subContext)
        node = next
      }
    }

    /** Sub-context formatter that delegates most operations to the main formatter.
      */
    private class SubNodeFormatter(
      mainNodeRenderer: MainNodeFormatter,
      out:              MarkdownWriter,
      subOptions:       Nullable[DataHolder]
    ) extends NodeFormatterSubContext(out),
          NodeFormatterContext {

      private val mySubOptions: DataHolder =
        if (subOptions.isEmpty || subOptions.exists(_ eq mainNodeRenderer.getOptions)) mainNodeRenderer.getOptions
        else new ScopedDataSet(mainNodeRenderer.getOptions, subOptions.get)
      private val mySubFormatterOptions: FormatterOptions = new FormatterOptions(mySubOptions)

      override def getTranslationStore:                                MutableDataHolder   = mainNodeRenderer.getTranslationStore
      override def nodesOfType(classes:         Array[Class[?]]):      Iterable[? <: Node] = mainNodeRenderer.nodesOfType(classes)
      override def nodesOfType(classes:         Collection[Class[?]]): Iterable[? <: Node] = mainNodeRenderer.nodesOfType(classes)
      override def reversedNodesOfType(classes: Array[Class[?]]):      Iterable[? <: Node] = mainNodeRenderer.reversedNodesOfType(classes)
      override def reversedNodesOfType(classes: Collection[Class[?]]): Iterable[? <: Node] = mainNodeRenderer.reversedNodesOfType(classes)
      override def getOptions:                                         DataHolder          = mySubOptions
      override def getFormatterOptions:                                FormatterOptions    = mySubFormatterOptions
      override def getDocument:                                        Document            = mainNodeRenderer.getDocument
      override def getBlockQuoteLikePrefixPredicate:                   CharPredicate       = mainNodeRenderer.getBlockQuoteLikePrefixPredicate
      override def getBlockQuoteLikePrefixChars:                       BasedSequence       = mainNodeRenderer.getBlockQuoteLikePrefixChars

      /** Sub-context does not have offset tracking */
      override def getTrackedOffsets:                                                             TrackedOffsetList    = TrackedOffsetList.EMPTY_LIST
      override def isRestoreTrackedSpaces:                                                        Boolean              = false
      override def getTrackedSequence:                                                            BasedSequence        = mainNodeRenderer.getTrackedSequence
      override def getFormattingPhase:                                                            FormattingPhase      = mainNodeRenderer.getFormattingPhase
      override def render(node:           Node):                                                  Unit                 = mainNodeRenderer.renderNode(node, this)
      override def getCurrentNode:                                                                Node                 = this.renderingNode.get
      override def delegateRender():                                                              Unit                 = mainNodeRenderer.delegateRenderInContext(this)
      override def getSubContext():                                                               NodeFormatterContext = getSubContext(Nullable.empty, markdown.getBuilder)
      override def getSubContext(options: Nullable[DataHolder]):                                  NodeFormatterContext = getSubContext(options, markdown.getBuilder)
      override def getSubContext(options: Nullable[DataHolder], builder: ISequenceBuilder[?, ?]): NodeFormatterContext = {
        val htmlWriter = new MarkdownWriter(Nullable(builder), this.markdown.getOptions)
        htmlWriter.setContext(this)
        val subOpts = if (options.isEmpty || options.exists(_ eq mySubOptions)) mySubOptions else new ScopedDataSet(mySubOptions, options.get)
        new SubNodeFormatter(mainNodeRenderer, htmlWriter, Nullable(subOpts))
      }
      override def renderChildren(parent: Node): Unit           = mainNodeRenderer.renderChildrenNode(parent, this)
      override def getMarkdown:                  MarkdownWriter = markdown
      override def getRenderPurpose:             RenderPurpose  = mainNodeRenderer.getRenderPurpose
      override def isTransformingText:           Boolean        = mainNodeRenderer.isTransformingText
      override def transformNonTranslating(prefix: Nullable[CharSequence], nonTranslatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence =
        mainNodeRenderer.transformNonTranslating(prefix, nonTranslatingText, suffix, suffix2)
      override def transformTranslating(prefix: Nullable[CharSequence], translatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence =
        mainNodeRenderer.transformTranslating(prefix, translatingText, suffix, suffix2)
      override def transformAnchorRef(pageRef:        CharSequence, anchorRef:                 CharSequence):          CharSequence = mainNodeRenderer.transformAnchorRef(pageRef, anchorRef)
      override def translatingSpan(render:            TranslatingSpanRender):                                          Unit         = mainNodeRenderer.translatingSpan(render)
      override def nonTranslatingSpan(render:         TranslatingSpanRender):                                          Unit         = mainNodeRenderer.nonTranslatingSpan(render)
      override def translatingRefTargetSpan(target:   Nullable[Node], render:                  TranslatingSpanRender): Unit         = mainNodeRenderer.translatingRefTargetSpan(target, render)
      override def customPlaceholderFormat(generator: TranslationPlaceholderGenerator, render: TranslatingSpanRender): Unit         = mainNodeRenderer.customPlaceholderFormat(generator, render)
      override def encodeUrl(url:                     CharSequence):                                                   String       = mainNodeRenderer.encodeUrl(url)
      override def resolveLink(linkType: LinkType, url: CharSequence, urlEncode: Nullable[Boolean]): ResolvedLink = mainNodeRenderer.resolveLinkInContext(this, linkType, url, Nullable.empty)
      override def resolveLink(linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes], urlEncode: Nullable[Boolean]): ResolvedLink =
        mainNodeRenderer.resolveLinkInContext(this, linkType, url, attributes)
      override def postProcessNonTranslating(postProcessor:    String => CharSequence, scope: Runnable): Unit                   = mainNodeRenderer.postProcessNonTranslating(postProcessor, scope)
      override def postProcessNonTranslating[T](postProcessor: String => CharSequence, scope: () => T):  T                      = mainNodeRenderer.postProcessNonTranslating(postProcessor, scope)
      override def isPostProcessingNonTranslating:                                                       Boolean                = mainNodeRenderer.isPostProcessingNonTranslating
      override def getMergeContext:                                                                      Nullable[MergeContext] = mainNodeRenderer.getMergeContext
      override def addExplicitId(node: Node, id: Nullable[String], context: NodeFormatterContext, markdown: MarkdownWriter): Unit = mainNodeRenderer.addExplicitId(node, id, context, markdown)
      override def getIdGenerator: Nullable[HtmlIdGenerator] = mainNodeRenderer.getIdGenerator
    }
  }
}
