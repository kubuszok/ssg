/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/HtmlRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

import ssg.md.html.renderer.*
import ssg.md.util.ast.{ Document, IRender, Node }
import ssg.md.util.build.BuilderBase
import ssg.md.util.data.*
import ssg.md.util.dependency.DependencyResolver
import ssg.md.util.format.{ TrackedOffset, TrackedOffsetUtils }
import ssg.md.util.html.{ Attributes, MutableAttributes }
import ssg.md.util.misc.{ Extension, Pair }
import ssg.md.util.sequence.{ Escaping, LineAppendable, TagRange }

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

/** Renders a tree of nodes to HTML.
  *
  * Start with the [[HtmlRenderer.builder]] method to configure the renderer. Example:
  * {{{
  * val renderer = HtmlRenderer.builder().escapeHtml(true).build()
  * renderer.render(node)
  * }}}
  */
class HtmlRenderer private[html] (builder: HtmlRenderer.Builder) extends IRender {

  private val _options: DataHolder           = builder.toImmutable
  override val options: Nullable[DataHolder] = Nullable(_options)
  val htmlOptions:      HtmlRendererOptions  = new HtmlRendererOptions(_options)

  private val htmlIdGeneratorFactory: Nullable[HeaderIdGeneratorFactory] = builder.htmlIdGeneratorFactory

  // resolve renderer dependencies
  val nodeRendererFactories: List[DelegatingNodeRendererFactoryWrapper] = {
    val nodeRenderers = mutable.ArrayBuffer[DelegatingNodeRendererFactoryWrapper]()
    val renderersList = nodeRenderers.toList

    var i = builder.nodeRendererFactories.size - 1
    while (i >= 0) {
      val nodeRendererFactory = builder.nodeRendererFactories(i)
      nodeRenderers += new DelegatingNodeRendererFactoryWrapper(Nullable(renderersList), nodeRendererFactory)
      i -= 1
    }

    // Add as last. This means clients can override the rendering of core nodes if they want by default
    val coreFactory = new CoreNodeRenderer.Factory()
    nodeRenderers += new DelegatingNodeRendererFactoryWrapper(Nullable(renderersList), coreFactory)

    DependencyResolver.resolveFlatDependencies(nodeRenderers.toList, Nullable.empty, Nullable((d: DelegatingNodeRendererFactoryWrapper) => d.factory.getClass))
  }

  val attributeProviderFactories: List[AttributeProviderFactory] = {
    // HACK: but for now works
    val addEmbedded = !builder.attributeProviderFactories.contains(EmbeddedAttributeProvider.Factory.getClass)
    val values      = mutable.ArrayBuffer[AttributeProviderFactory](builder.attributeProviderFactories.values.toSeq*)
    if (addEmbedded && HtmlRenderer.EMBEDDED_ATTRIBUTE_PROVIDER.get(Nullable(_options))) {
      // add it first so the rest can override it if needed
      values.insert(0, EmbeddedAttributeProvider.Factory)
    }
    DependencyResolver.resolveFlatDependencies(values.toList, Nullable.empty, Nullable.empty)
  }

  val linkResolverFactories: List[LinkResolverFactory] =
    DependencyResolver.resolveFlatDependencies(builder.linkResolverFactories.toList, Nullable.empty, Nullable.empty)

  def getOptions: DataHolder = _options

  /** Render a node to the appendable
    *
    * @param node
    *   node to render
    * @param output
    *   appendable to use for the output
    */
  def render(node: Node, output: Appendable): Unit =
    render(node, output, htmlOptions.maxTrailingBlankLines)

  /** Render a node to the appendable
    */
  def render(node: Node, output: Appendable, maxTrailingBlankLines: Int): Unit = {
    val htmlWriter = new HtmlWriter(
      Nullable(output),
      htmlOptions.indentSize,
      htmlOptions.formatFlags,
      !htmlOptions.htmlBlockOpenTagEol,
      !htmlOptions.htmlBlockCloseTagEol
    )
    val renderer = new HtmlRenderer.MainNodeRenderer(this, _options, htmlWriter, node.document)
    if (renderer.htmlIdGenerator != HtmlIdGenerator.NULL && !node.isInstanceOf[Document]) {
      renderer.htmlIdGenerator.generateIds(node.document)
    }

    renderer.render(node)
    htmlWriter.appendToSilently(output, htmlOptions.maxBlankLines, maxTrailingBlankLines)

    // resolve any unresolved tracked offsets that are outside elements which resolve their own
    TrackedOffsetUtils.resolveTrackedOffsets(
      node.chars,
      htmlWriter,
      HtmlRenderer.TRACKED_OFFSETS.get(Nullable(renderer.getDocument)).asJava,
      maxTrailingBlankLines,
      SharedDataKeys.RUNNING_TESTS.get(Nullable(_options))
    )
    renderer.dispose()
  }

  /** Render the tree of nodes to HTML.
    *
    * @param node
    *   the root node
    * @return
    *   the rendered HTML.
    */
  override def render(node: Node): String = {
    val sb = new java.lang.StringBuilder()
    render(node, sb)
    sb.toString
  }
}

object HtmlRenderer {
  val SOFT_BREAK:                                 DataKey[String]                          = new DataKey[String]("SOFT_BREAK", "\n")
  val HARD_BREAK:                                 DataKey[String]                          = new DataKey[String]("HARD_BREAK", "<br />\n")
  val STRONG_EMPHASIS_STYLE_HTML_OPEN:            NullableDataKey[String]                  = new NullableDataKey[String]("STRONG_EMPHASIS_STYLE_HTML_OPEN")
  val STRONG_EMPHASIS_STYLE_HTML_CLOSE:           NullableDataKey[String]                  = new NullableDataKey[String]("STRONG_EMPHASIS_STYLE_HTML_CLOSE")
  val EMPHASIS_STYLE_HTML_OPEN:                   NullableDataKey[String]                  = new NullableDataKey[String]("EMPHASIS_STYLE_HTML_OPEN")
  val EMPHASIS_STYLE_HTML_CLOSE:                  NullableDataKey[String]                  = new NullableDataKey[String]("EMPHASIS_STYLE_HTML_CLOSE")
  val CODE_STYLE_HTML_OPEN:                       NullableDataKey[String]                  = new NullableDataKey[String]("CODE_STYLE_HTML_OPEN")
  val CODE_STYLE_HTML_CLOSE:                      NullableDataKey[String]                  = new NullableDataKey[String]("CODE_STYLE_HTML_CLOSE")
  val INLINE_CODE_SPLICE_CLASS:                   NullableDataKey[String]                  = new NullableDataKey[String]("INLINE_CODE_SPLICE_CLASS")
  val PERCENT_ENCODE_URLS:                        DataKey[Boolean]                         = SharedDataKeys.PERCENT_ENCODE_URLS
  val INDENT_SIZE:                                DataKey[Int]                             = SharedDataKeys.INDENT_SIZE
  val ESCAPE_HTML:                                DataKey[Boolean]                         = new DataKey[Boolean]("ESCAPE_HTML", false)
  val ESCAPE_HTML_BLOCKS:                         DataKey[Boolean]                         = new DataKey[Boolean]("ESCAPE_HTML_BLOCKS", ESCAPE_HTML)
  val ESCAPE_HTML_COMMENT_BLOCKS:                 DataKey[Boolean]                         = new DataKey[Boolean]("ESCAPE_HTML_COMMENT_BLOCKS", ESCAPE_HTML_BLOCKS)
  val ESCAPE_INLINE_HTML:                         DataKey[Boolean]                         = new DataKey[Boolean]("ESCAPE_HTML_BLOCKS", ESCAPE_HTML)
  val ESCAPE_INLINE_HTML_COMMENTS:                DataKey[Boolean]                         = new DataKey[Boolean]("ESCAPE_INLINE_HTML_COMMENTS", ESCAPE_INLINE_HTML)
  val SUPPRESS_HTML:                              DataKey[Boolean]                         = new DataKey[Boolean]("SUPPRESS_HTML", false)
  val SUPPRESS_HTML_BLOCKS:                       DataKey[Boolean]                         = new DataKey[Boolean]("SUPPRESS_HTML_BLOCKS", SUPPRESS_HTML)
  val SUPPRESS_HTML_COMMENT_BLOCKS:               DataKey[Boolean]                         = new DataKey[Boolean]("SUPPRESS_HTML_COMMENT_BLOCKS", SUPPRESS_HTML_BLOCKS)
  val SUPPRESS_INLINE_HTML:                       DataKey[Boolean]                         = new DataKey[Boolean]("SUPPRESS_INLINE_HTML", SUPPRESS_HTML)
  val SUPPRESS_INLINE_HTML_COMMENTS:              DataKey[Boolean]                         = new DataKey[Boolean]("SUPPRESS_INLINE_HTML_COMMENTS", SUPPRESS_INLINE_HTML)
  val SOURCE_WRAP_HTML:                           DataKey[Boolean]                         = new DataKey[Boolean]("SOURCE_WRAP_HTML", false)
  val SOURCE_WRAP_HTML_BLOCKS:                    DataKey[Boolean]                         = new DataKey[Boolean]("SOURCE_WRAP_HTML_BLOCKS", SOURCE_WRAP_HTML)
  val HEADER_ID_GENERATOR_RESOLVE_DUPES:          DataKey[Boolean]                         = SharedDataKeys.HEADER_ID_GENERATOR_RESOLVE_DUPES
  val HEADER_ID_GENERATOR_TO_DASH_CHARS:          DataKey[String]                          = SharedDataKeys.HEADER_ID_GENERATOR_TO_DASH_CHARS
  val HEADER_ID_GENERATOR_NON_DASH_CHARS:         DataKey[String]                          = SharedDataKeys.HEADER_ID_GENERATOR_NON_DASH_CHARS
  val HEADER_ID_GENERATOR_NO_DUPED_DASHES:        DataKey[Boolean]                         = SharedDataKeys.HEADER_ID_GENERATOR_NO_DUPED_DASHES
  val HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE: DataKey[Boolean]                         = SharedDataKeys.HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE
  val HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES:     DataKey[Boolean]                         = SharedDataKeys.HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES
  val HEADER_ID_REF_TEXT_TRIM_TRAILING_SPACES:    DataKey[Boolean]                         = SharedDataKeys.HEADER_ID_REF_TEXT_TRIM_TRAILING_SPACES
  val HEADER_ID_ADD_EMOJI_SHORTCUT:               DataKey[Boolean]                         = SharedDataKeys.HEADER_ID_ADD_EMOJI_SHORTCUT
  val RENDER_HEADER_ID:                           DataKey[Boolean]                         = SharedDataKeys.RENDER_HEADER_ID
  val GENERATE_HEADER_ID:                         DataKey[Boolean]                         = SharedDataKeys.GENERATE_HEADER_ID
  val DO_NOT_RENDER_LINKS:                        DataKey[Boolean]                         = SharedDataKeys.DO_NOT_RENDER_LINKS
  val FENCED_CODE_LANGUAGE_CLASS_PREFIX:          DataKey[String]                          = new DataKey[String]("FENCED_CODE_LANGUAGE_CLASS_PREFIX", "language-")
  val FENCED_CODE_LANGUAGE_CLASS_MAP:             DataKey[mutable.HashMap[String, String]] = new DataKey[mutable.HashMap[String, String]](
    "FENCED_CODE_LANGUAGE_CLASS_MAP",
    new NotNullValueSupplier[mutable.HashMap[String, String]] { def get: mutable.HashMap[String, String] = mutable.HashMap.empty }
  )
  val FENCED_CODE_NO_LANGUAGE_CLASS:   DataKey[String]                   = new DataKey[String]("FENCED_CODE_NO_LANGUAGE_CLASS", "")
  val FENCED_CODE_LANGUAGE_DELIMITERS: DataKey[String]                   = new DataKey[String]("FENCED_CODE_LANGUAGE_DELIMITERS", " \t")
  val SOURCE_POSITION_ATTRIBUTE:       DataKey[String]                   = new DataKey[String]("SOURCE_POSITION_ATTRIBUTE", "")
  val SOURCE_POSITION_PARAGRAPH_LINES: DataKey[Boolean]                  = new DataKey[Boolean]("SOURCE_POSITION_PARAGRAPH_LINES", false)
  val TYPE:                            DataKey[String]                   = new DataKey[String]("TYPE", "HTML")
  val TAG_RANGES:                      DataKey[util.ArrayList[TagRange]] = new DataKey[util.ArrayList[TagRange]](
    "TAG_RANGES",
    new NotNullValueSupplier[util.ArrayList[TagRange]] { def get: util.ArrayList[TagRange] = new util.ArrayList[TagRange]() }
  )

  val RECHECK_UNDEFINED_REFERENCES: DataKey[Boolean] = new DataKey[Boolean]("RECHECK_UNDEFINED_REFERENCES", false)
  val OBFUSCATE_EMAIL:              DataKey[Boolean] = new DataKey[Boolean]("OBFUSCATE_EMAIL", false)
  val OBFUSCATE_EMAIL_RANDOM:       DataKey[Boolean] = new DataKey[Boolean]("OBFUSCATE_EMAIL_RANDOM", true)
  val HTML_BLOCK_OPEN_TAG_EOL:      DataKey[Boolean] = new DataKey[Boolean]("HTML_BLOCK_OPEN_TAG_EOL", true)
  val HTML_BLOCK_CLOSE_TAG_EOL:     DataKey[Boolean] = new DataKey[Boolean]("HTML_BLOCK_CLOSE_TAG_EOL", true)
  val UNESCAPE_HTML_ENTITIES:       DataKey[Boolean] = new DataKey[Boolean]("UNESCAPE_HTML_ENTITIES", true)
  val AUTOLINK_WWW_PREFIX:          DataKey[String]  = new DataKey[String]("AUTOLINK_WWW_PREFIX", "http://")

  // regex for suppressed link prefixes
  val SUPPRESSED_LINKS:            DataKey[String]  = new DataKey[String]("SUPPRESSED_LINKS", "javascript:.*")
  val NO_P_TAGS_USE_BR:            DataKey[Boolean] = new DataKey[Boolean]("NO_P_TAGS_USE_BR", false)
  val EMBEDDED_ATTRIBUTE_PROVIDER: DataKey[Boolean] = new DataKey[Boolean]("EMBEDDED_ATTRIBUTE_PROVIDER", true)

  /** output control for FormattingAppendable, see [[LineAppendable.setOptions]]
    */
  val FORMAT_FLAGS:             DataKey[Int] = new DataKey[Int]("RENDERER_FORMAT_FLAGS", LineAppendable.F_TRIM_LEADING_WHITESPACE)
  val MAX_TRAILING_BLANK_LINES: DataKey[Int] = SharedDataKeys.RENDERER_MAX_TRAILING_BLANK_LINES
  val MAX_BLANK_LINES:          DataKey[Int] = SharedDataKeys.RENDERER_MAX_BLANK_LINES

  // Use LineFormattingAppendable values instead
  @deprecated("Use LineAppendable.F_CONVERT_TABS", "0.60")
  val CONVERT_TABS: Int = LineAppendable.F_CONVERT_TABS
  @deprecated("Use LineAppendable.F_COLLAPSE_WHITESPACE", "0.60")
  val COLLAPSE_WHITESPACE: Int = LineAppendable.F_COLLAPSE_WHITESPACE
  @deprecated("Use LineAppendable.F_TRIM_TRAILING_WHITESPACE", "0.60")
  val SUPPRESS_TRAILING_WHITESPACE: Int = LineAppendable.F_TRIM_TRAILING_WHITESPACE
  @deprecated("Use LineAppendable.F_PASS_THROUGH", "0.60")
  val PASS_THROUGH: Int = LineAppendable.F_PASS_THROUGH
  @deprecated("Use LineAppendable.F_FORMAT_ALL", "0.60")
  val FORMAT_ALL: Int = LineAppendable.F_FORMAT_ALL

  /** Stores pairs of equivalent renderer types to allow extensions to resolve types not known to them
    *
    * Pair contains: rendererType, equivalentType
    */
  val RENDERER_TYPE_EQUIVALENCE: DataKey[List[Pair[String, String]]] =
    new DataKey[List[Pair[String, String]]]("RENDERER_TYPE_EQUIVALENCE", List.empty)

  // Use LineFormattingAppendable values instead
  @deprecated("Use LineAppendable.F_CONVERT_TABS", "0.60")
  val FORMAT_CONVERT_TABS: Int = LineAppendable.F_CONVERT_TABS
  @deprecated("Use LineAppendable.F_COLLAPSE_WHITESPACE", "0.60")
  val FORMAT_COLLAPSE_WHITESPACE: Int = LineAppendable.F_COLLAPSE_WHITESPACE
  @deprecated("Use LineAppendable.F_TRIM_TRAILING_WHITESPACE", "0.60")
  val FORMAT_SUPPRESS_TRAILING_WHITESPACE: Int = LineAppendable.F_TRIM_TRAILING_WHITESPACE
  @deprecated("Use LineAppendable.F_FORMAT_ALL", "0.60")
  val FORMAT_ALL_OPTIONS: Int = LineAppendable.F_FORMAT_ALL

  // Experimental, not tested
  val TRACKED_OFFSETS: DataKey[List[TrackedOffset]] =
    new DataKey[List[TrackedOffset]]("TRACKED_OFFSETS", List.empty)

  /** Create a new builder for configuring an [[HtmlRenderer]].
    *
    * @return
    *   a builder
    */
  def builder(): Builder = new Builder()

  /** Create a new builder for configuring an [[HtmlRenderer]].
    *
    * @param options
    *   initialization options
    * @return
    *   a builder
    */
  def builder(options: Nullable[DataHolder]): Builder = new Builder(options)

  def isCompatibleRendererType(options: MutableDataHolder, supportedRendererType: String): Boolean = {
    val rendererType = HtmlRenderer.TYPE.get(Nullable(options))
    isCompatibleRendererType(options, rendererType, supportedRendererType)
  }

  def isCompatibleRendererType(options: MutableDataHolder, rendererType: String, supportedRendererType: String): Boolean =
    if (rendererType == supportedRendererType) {
      true
    } else {
      val equivalence = RENDERER_TYPE_EQUIVALENCE.get(Nullable(options))
      equivalence.exists(pair =>
        pair.first.isDefined && pair.second.isDefined &&
          rendererType == pair.first.get && supportedRendererType == pair.second.get
      )
    }

  def addRenderTypeEquivalence(options: MutableDataHolder, rendererType: String, supportedRendererType: String): MutableDataHolder = {
    if (!isCompatibleRendererType(options, rendererType, supportedRendererType)) {
      val equivalence    = RENDERER_TYPE_EQUIVALENCE.get(Nullable(options))
      val newEquivalence = equivalence :+ new Pair(Nullable(rendererType), Nullable(supportedRendererType))
      options.set(RENDERER_TYPE_EQUIVALENCE, newEquivalence)
    }
    options
  }

  /** Builder for configuring an [[HtmlRenderer]]. See methods for default configuration.
    */
  class Builder(options: Nullable[DataHolder]) extends BuilderBase[Builder](options), RendererBuilder {
    var attributeProviderFactories: mutable.LinkedHashMap[Class[?], AttributeProviderFactory] = mutable.LinkedHashMap.empty
    var nodeRendererFactories:      mutable.ArrayBuffer[NodeRendererFactory]                  = mutable.ArrayBuffer.empty
    var linkResolverFactories:      mutable.ArrayBuffer[LinkResolverFactory]                  = mutable.ArrayBuffer.empty
    var htmlIdGeneratorFactory:     Nullable[HeaderIdGeneratorFactory]                        = Nullable.empty

    def this() =
      this(Nullable.empty)

    if (options.isDefined) {
      loadExtensions()
    }

    override protected def removeApiPoint(apiPoint: AnyRef): Unit =
      apiPoint match {
        case _: AttributeProviderFactory => attributeProviderFactories.remove(apiPoint.getClass)
        case _: NodeRendererFactory      => nodeRendererFactories -= apiPoint.asInstanceOf[NodeRendererFactory]
        case _: LinkResolverFactory      => linkResolverFactories -= apiPoint.asInstanceOf[LinkResolverFactory]
        case _: HeaderIdGeneratorFactory => htmlIdGeneratorFactory = Nullable.empty
        case _ =>
          throw new IllegalStateException("Unknown data point type: " + apiPoint.getClass.getName)
      }

    override protected def preloadExtension(extension: Extension): Unit =
      extension match {
        case ext: HtmlRendererExtension => ext.rendererOptions(this)
        case ext: RendererExtension     => ext.rendererOptions(this)
        case _ => ()
      }

    override protected def loadExtension(extension: Extension): Boolean =
      extension match {
        case ext: HtmlRendererExtension =>
          ext.extend(this, TYPE.get(Nullable(this)))
          true
        case ext: RendererExtension =>
          ext.extend(this, TYPE.get(Nullable(this)))
          true
        case _ => false
      }

    /** @return
      *   the configured [[HtmlRenderer]]
      */
    def build(): HtmlRenderer = new HtmlRenderer(this)

    /** The HTML to use for rendering a softbreak, defaults to `"\n"`.
      */
    def softBreak(softBreak: String): Builder = {
      this.set(SOFT_BREAK, softBreak)
      this
    }

    /** The size of the indent to use for hierarchical elements, default 0.
      */
    def indentSize(indentSize: Int): Builder = {
      this.set(INDENT_SIZE, indentSize)
      this
    }

    /** Whether HtmlInline and HtmlBlock should be escaped, defaults to false.
      */
    def escapeHtml(escapeHtml: Boolean): Builder = {
      this.set(ESCAPE_HTML, escapeHtml)
      this
    }

    def isRendererType(supportedRendererType: String): Boolean = {
      val rendererType = HtmlRenderer.TYPE.get(Nullable(this))
      HtmlRenderer.isCompatibleRendererType(this, rendererType, supportedRendererType)
    }

    /** Whether URLs of link or images should be percent-encoded, defaults to false.
      */
    def percentEncodeUrls(percentEncodeUrls: Boolean): Builder = {
      this.set(PERCENT_ENCODE_URLS, percentEncodeUrls)
      this
    }

    /** Add an attribute provider for adding/changing HTML attributes to the rendered tags.
      */
    override def attributeProviderFactory(attributeProviderFactory: AttributeProviderFactory): Builder = {
      this.attributeProviderFactories.put(attributeProviderFactory.getClass, attributeProviderFactory)
      addExtensionApiPoint(attributeProviderFactory)
      this
    }

    /** Add a factory for instantiating a node renderer (done when rendering).
      */
    def nodeRendererFactory(nodeRendererFactory: NodeRendererFactory): Builder = {
      this.nodeRendererFactories += nodeRendererFactory
      addExtensionApiPoint(nodeRendererFactory)
      this
    }

    /** Add a factory for resolving links.
      */
    override def linkResolverFactory(linkResolverFactory: LinkResolverFactory): Builder = {
      this.linkResolverFactories += linkResolverFactory
      addExtensionApiPoint(linkResolverFactory)
      this
    }

    /** Add a factory for resolving URI to content
      */
    override def contentResolverFactory(contentResolverFactory: UriContentResolverFactory): Builder =
      throw new IllegalStateException("Not implemented")

    /** Add a factory for generating the header id attribute from the header's text
      */
    override def htmlIdGeneratorFactory(htmlIdGeneratorFactory: HeaderIdGeneratorFactory): Builder = {
      if (this.htmlIdGeneratorFactory.isDefined) {
        throw new IllegalStateException("custom header id factory is already set to " + htmlIdGeneratorFactory.getClass.getName)
      }
      this.htmlIdGeneratorFactory = Nullable(htmlIdGeneratorFactory)
      addExtensionApiPoint(htmlIdGeneratorFactory)
      this
    }
  }

  /** Extension for [[HtmlRenderer]].
    */
  trait HtmlRendererExtension extends Extension {

    /** This method is called first on all extensions so that they can adjust the options.
      */
    def rendererOptions(options: MutableDataHolder): Unit

    /** Called to give each extension to register extension points that it contains
      */
    def extend(htmlRendererBuilder: Builder, rendererType: String): Unit
  }

  private[html] class MainNodeRenderer(
    renderer:   HtmlRenderer,
    options:    DataHolder,
    htmlWriter: HtmlWriter,
    document:   Document
  ) extends NodeRendererSubContext(htmlWriter)
      with NodeRendererContext
      with Disposable {

    private var _document:       Nullable[Document]                                               = Nullable(document)
    private var _options:        Nullable[DataHolder]                                             = Nullable(new ScopedDataSet(Nullable(document), Nullable(options)))
    private var renderers:       Nullable[mutable.HashMap[Class[?], NodeRenderingHandlerWrapper]] = Nullable(mutable.HashMap.empty)
    private var phasedRenderers: Nullable[mutable.ArrayBuffer[PhasedNodeRenderer]]                = Nullable(mutable.ArrayBuffer.empty)
    private var myLinkResolvers: Nullable[Array[LinkResolver]]                                    = Nullable(new Array[LinkResolver](renderer.linkResolverFactories.size))
    private var renderingPhases: Nullable[mutable.HashSet[RenderingPhase]]                        = Nullable(mutable.HashSet.empty)
    private var phase:           RenderingPhase                                                   = RenderingPhase.BODY
    var htmlIdGenerator:         HtmlIdGenerator                                                  = renderer.htmlIdGeneratorFactory.fold(
      if (!renderer.htmlOptions.generateHeaderIds) HtmlIdGenerator.NULL
      else new HeaderIdGenerator.Factory().create(this)
    )(f => f.create(this))
    private var resolvedLinkMap:    Nullable[mutable.HashMap[LinkType, mutable.HashMap[String, ResolvedLink]]] = Nullable(mutable.HashMap.empty)
    private var attributeProviders: Nullable[Array[AttributeProvider]]                                         = Nullable(new Array[AttributeProvider](renderer.attributeProviderFactories.size))

    doNotRenderLinksNesting = if (renderer.htmlOptions.doNotRenderLinksInDocument) 0 else 1

    htmlWriter.setContext(this)

    // Initialize node renderers
    {
      var i = renderer.nodeRendererFactories.size - 1
      while (i >= 0) {
        val nodeRendererFactory = renderer.nodeRendererFactories(i)
        val nodeRenderer        = nodeRendererFactory.apply(this.getOptions)
        val renderingHandlers   = nodeRenderer.getNodeRenderingHandlers

        assert(renderingHandlers.isDefined)
        renderingHandlers.foreach { handlers =>
          for (nodeType <- handlers) {
            // Overwrite existing renderer
            val handlerWrapper = new NodeRenderingHandlerWrapper(nodeType, renderers.get.get(nodeType.nodeType).fold(Nullable.empty[NodeRenderingHandlerWrapper])(Nullable(_)))
            renderers.get.put(nodeType.nodeType, handlerWrapper)
          }
        }

        nodeRenderer match {
          case phasedRenderer: PhasedNodeRenderer =>
            val phases = phasedRenderer.getRenderingPhases
            assert(phases.isDefined)
            phases.foreach { p =>
              renderingPhases.get ++= p
              phasedRenderers.get += phasedRenderer
            }
          case _ => ()
        }
        i -= 1
      }

      var j = 0
      while (j < renderer.linkResolverFactories.size) {
        myLinkResolvers.get(j) = renderer.linkResolverFactories(j).apply(this)
        j += 1
      }

      var k = 0
      while (k < renderer.attributeProviderFactories.size) {
        attributeProviders.get(k) = renderer.attributeProviderFactories(k).apply(this)
        k += 1
      }
    }

    override def dispose(): Unit = {
      _document = Nullable.empty
      renderers = Nullable.empty
      phasedRenderers = Nullable.empty

      myLinkResolvers.foreach { resolvers =>
        for (linkResolver <- resolvers)
          linkResolver match {
            case d: Disposable => d.dispose()
            case _ => ()
          }
      }
      myLinkResolvers = Nullable.empty

      renderingPhases = Nullable.empty
      _options = Nullable.empty

      htmlIdGenerator match {
        case d: Disposable => d.dispose()
        case _ => ()
      }
      resolvedLinkMap = Nullable.empty

      attributeProviders.foreach { providers =>
        for (attributeProvider <- providers)
          attributeProvider match {
            case d: Disposable => d.dispose()
            case _ => ()
          }
      }
      attributeProviders = Nullable.empty
    }

    override def getCurrentNode: Node = renderingNode.get

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    override def resolveLink(linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes], urlEncode: Nullable[Boolean]): ResolvedLink = {
      val resolvedLinks = resolvedLinkMap.get.getOrElseUpdate(linkType, mutable.HashMap.empty)

      val urlSeq = String.valueOf(url)
      resolvedLinks.get(urlSeq) match {
        case Some(existing) => existing
        case scala.None     =>
          var resolvedLink = new ResolvedLink(linkType, urlSeq, attributes)

          if (urlSeq.nonEmpty) {
            val currentNode = getCurrentNode
            val resolvers   = myLinkResolvers.get
            var i           = 0
            var done        = false
            while (i < resolvers.length && !done) {
              resolvedLink = resolvers(i).resolveLink(currentNode, this, resolvedLink)
              if (resolvedLink.status != LinkStatus.UNKNOWN) done = true
              i += 1
            }

            val shouldEncode = urlEncode.fold(renderer.htmlOptions.percentEncodeUrls)(_.booleanValue())
            if (shouldEncode) {
              resolvedLink = resolvedLink.withUrl(Escaping.percentEncodeUrl(resolvedLink.url))
            }
          }

          resolvedLinks.put(urlSeq, resolvedLink)
          resolvedLink
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    override def getNodeId(node: Node): Nullable[String] = {
      var id: Nullable[String] = htmlIdGenerator.getId(node)
      if (renderer.attributeProviderFactories.nonEmpty) {
        val attributes = new MutableAttributes()
        id.foreach(idVal => attributes.replaceValue("id", idVal))
        attributeProviders.foreach { providers =>
          for (attributeProvider <- providers)
            attributeProvider.setAttributes(renderingNode.get, AttributablePart.ID, attributes)
        }
        val idVal = attributes.getValue("id") // @nowarn - getValue may return Java-null
        id = if (idVal == null) Nullable.empty else Nullable(idVal)
      }
      id
    }

    override def getOptions: DataHolder = _options.get

    override def getHtmlOptions: HtmlRendererOptions = renderer.htmlOptions

    override def getDocument: Document = _document.get

    override def getRenderingPhase: RenderingPhase = phase

    override def encodeUrl(url: CharSequence): String =
      if (renderer.htmlOptions.percentEncodeUrls) {
        Escaping.percentEncodeUrl(url)
      } else {
        String.valueOf(url)
      }

    override def extendRenderingNodeAttributes(part: AttributablePart, attributes: Nullable[Attributes]): MutableAttributes = {
      val attr = attributes.fold(new MutableAttributes())(_.toMutable)
      attributeProviders.foreach { providers =>
        for (attributeProvider <- providers)
          attributeProvider.setAttributes(renderingNode.get, part, attr)
      }
      attr
    }

    override def extendRenderingNodeAttributes(node: Node, part: AttributablePart, attributes: Nullable[Attributes]): MutableAttributes = {
      val attr = attributes.fold(new MutableAttributes())(_.toMutable)
      attributeProviders.foreach { providers =>
        for (attributeProvider <- providers)
          attributeProvider.setAttributes(node, part, attr)
      }
      attr
    }

    override def render(node: Node): Unit =
      renderNode(node, this)

    override def delegateRender(): Unit =
      renderByPreviousHandler(this)

    private[html] def renderByPreviousHandler(subContext: NodeRendererSubContext): Unit =
      if (subContext.renderingNode.isDefined) {
        subContext.renderingHandlerWrapper.flatMap(_.myPreviousRenderingHandler).foreach { nodeRenderer =>
          val oldNode                    = subContext.renderingNode
          val oldDoNotRenderLinksNesting = subContext.doNotRenderLinksNesting
          val prevWrapper                = subContext.renderingHandlerWrapper
          try {
            subContext.renderingHandlerWrapper = Nullable(nodeRenderer)
            nodeRenderer.myRenderingHandler.render(oldNode.get, subContext, subContext.htmlWriter)
          } finally {
            subContext.renderingNode = oldNode
            subContext.doNotRenderLinksNesting = oldDoNotRenderLinksNesting
            subContext.renderingHandlerWrapper = prevWrapper
          }
        }
      } else {
        throw new IllegalStateException("renderingByPreviousHandler called outside node rendering code")
      }

    override def getSubContext(inheritIndent: Boolean): NodeRendererContext = {
      val newHtmlWriter = new HtmlWriter(getHtmlWriter, inheritIndent)
      newHtmlWriter.setContext(this)
      new SubNodeRenderer(this, newHtmlWriter, false)
    }

    override def getDelegatedSubContext(inheritIndent: Boolean): NodeRendererContext = {
      val newHtmlWriter = new HtmlWriter(getHtmlWriter, inheritIndent)
      newHtmlWriter.setContext(this)
      new SubNodeRenderer(this, newHtmlWriter, true)
    }

    private[html] def renderNode(node: Node, subContext: NodeRendererSubContext): Unit =
      if (node.isInstanceOf[Document]) {
        // here we render multiple phases
        val oldDoNotRenderLinksNesting      = subContext.doNotRenderLinksNesting
        val documentDoNotRenderLinksNesting = if (getHtmlOptions.doNotRenderLinksInDocument) 1 else 0
        this.htmlIdGenerator.generateIds(_document.get)

        for (currentPhase <- RenderingPhase.values)
          if (currentPhase != RenderingPhase.BODY && !renderingPhases.get.contains(currentPhase)) {
            // skip this phase
          } else {
            this.phase = currentPhase

            // go through all renderers that want this phase
            phasedRenderers.foreach { phased =>
              for (phasedRenderer <- phased) {
                val phases = phasedRenderer.getRenderingPhases
                if (phases.isDefined && phases.get.contains(currentPhase)) {
                  subContext.doNotRenderLinksNesting = documentDoNotRenderLinksNesting
                  subContext.renderingNode = Nullable(node)
                  phasedRenderer.renderDocument(subContext, subContext.htmlWriter, node.asInstanceOf[Document], currentPhase)
                  subContext.renderingNode = Nullable.empty
                  subContext.doNotRenderLinksNesting = oldDoNotRenderLinksNesting
                }
              }
            }

            if (getRenderingPhase == RenderingPhase.BODY) {
              renderers.get.get(node.getClass).foreach { nodeRenderer =>
                subContext.doNotRenderLinksNesting = documentDoNotRenderLinksNesting
                val prevWrapper = subContext.renderingHandlerWrapper
                try {
                  subContext.renderingNode = Nullable(node)
                  subContext.renderingHandlerWrapper = Nullable(nodeRenderer)
                  nodeRenderer.myRenderingHandler.render(node, subContext, subContext.htmlWriter)
                } finally {
                  subContext.renderingHandlerWrapper = prevWrapper
                  subContext.renderingNode = Nullable.empty
                  subContext.doNotRenderLinksNesting = oldDoNotRenderLinksNesting
                }
              }
            }
          }
      } else {
        renderers.get.get(node.getClass).foreach { nodeRenderer =>
          val oldNode                    = this.renderingNode
          val oldDoNotRenderLinksNesting = subContext.doNotRenderLinksNesting
          val prevWrapper                = subContext.renderingHandlerWrapper
          try {
            subContext.renderingNode = Nullable(node)
            subContext.renderingHandlerWrapper = Nullable(nodeRenderer)
            nodeRenderer.myRenderingHandler.render(node, subContext, subContext.htmlWriter)
          } finally {
            subContext.renderingNode = oldNode
            subContext.doNotRenderLinksNesting = oldDoNotRenderLinksNesting
            subContext.renderingHandlerWrapper = prevWrapper
          }
        }
      }

    override def renderChildren(parent: Node): Unit =
      renderChildrenNode(parent, this)

    private[html] def renderChildrenNode(parent: Node, subContext: NodeRendererSubContext): Unit = {
      var node = parent.firstChild
      while (node.isDefined) {
        val next = node.get.next
        renderNode(node.get, subContext)
        node = next
      }
    }
  }

  private class SubNodeRenderer(
    mainNodeRenderer:      MainNodeRenderer,
    htmlWriter:            HtmlWriter,
    inheritCurrentHandler: Boolean
  ) extends NodeRendererSubContext(htmlWriter)
      with NodeRendererContext {

    doNotRenderLinksNesting = if (mainNodeRenderer.getHtmlOptions.doNotRenderLinksInDocument) 1 else 0
    if (inheritCurrentHandler) {
      renderingNode = mainNodeRenderer.renderingNode
      renderingHandlerWrapper = mainNodeRenderer.renderingHandlerWrapper
    }

    override def getNodeId(node: Node):         Nullable[String]    = mainNodeRenderer.getNodeId(node)
    override def getOptions:                    DataHolder          = mainNodeRenderer.getOptions
    override def getHtmlOptions:                HtmlRendererOptions = mainNodeRenderer.getHtmlOptions
    override def getDocument:                   Document            = mainNodeRenderer.getDocument
    override def getRenderingPhase:             RenderingPhase      = mainNodeRenderer.getRenderingPhase
    override def encodeUrl(url:  CharSequence): String              = mainNodeRenderer.encodeUrl(url)

    override def extendRenderingNodeAttributes(part: AttributablePart, attributes: Nullable[Attributes]): MutableAttributes =
      mainNodeRenderer.extendRenderingNodeAttributes(part, attributes)

    override def extendRenderingNodeAttributes(node: Node, part: AttributablePart, attributes: Nullable[Attributes]): MutableAttributes =
      mainNodeRenderer.extendRenderingNodeAttributes(node, part, attributes)

    override def render(node: Node): Unit = mainNodeRenderer.renderNode(node, this)
    override def delegateRender():   Unit = mainNodeRenderer.renderByPreviousHandler(this)
    override def getCurrentNode:     Node = mainNodeRenderer.getCurrentNode

    override def resolveLink(linkType: LinkType, url: CharSequence, urlEncode: Nullable[Boolean]): ResolvedLink =
      mainNodeRenderer.resolveLink(linkType, url, urlEncode)

    override def resolveLink(linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes], urlEncode: Nullable[Boolean]): ResolvedLink =
      mainNodeRenderer.resolveLink(linkType, url, attributes, urlEncode)

    override def getSubContext(inheritIndent: Boolean): NodeRendererContext = {
      val newHtmlWriter = new HtmlWriter(this.htmlWriter, inheritIndent)
      newHtmlWriter.setContext(this)
      new SubNodeRenderer(mainNodeRenderer, newHtmlWriter, false)
    }

    override def getDelegatedSubContext(inheritIndent: Boolean): NodeRendererContext = {
      val newHtmlWriter = new HtmlWriter(this.htmlWriter, inheritIndent)
      newHtmlWriter.setContext(this)
      new SubNodeRenderer(mainNodeRenderer, newHtmlWriter, true)
    }

    override def renderChildren(parent: Node): Unit = mainNodeRenderer.renderChildrenNode(parent, this)

    override def getHtmlWriter: HtmlWriter = htmlWriter

    override def isDoNotRenderLinks:                          Boolean = super.isDoNotRenderLinks
    override def doNotRenderLinks(doNotRenderLinks: Boolean): Unit    = super.doNotRenderLinks(doNotRenderLinks)
    override def doNotRenderLinks():                          Unit    = super.doNotRenderLinks()
    override def doRenderLinks():                             Unit    = super.doRenderLinks()
  }
}
