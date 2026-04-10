/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/TemplateParser.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: Builder pattern preserved
 *   Idiom: Enums as Scala 3 enums extending java.lang.Enum
 *   Breaking: Default flavor changed from LIQP to JEKYLL — SSG targets
 *     Jekyll-compatible sites. Users porting from raw liqp who relied on
 *     LIQP flavor semantics should explicitly set .withFlavor(Flavor.LIQP).
 */
package ssg
package liquid

import ssg.liquid.antlr.NameResolver
import ssg.liquid.filters.{ Filter, Filters }
import ssg.liquid.filters.date.{ BasicDateParser, DateParser }
import ssg.liquid.parser.{ Flavor, LiquidSupport }

import java.nio.file.Path
import java.time.ZoneId
import java.util.{ ArrayList, Locale, Map => JMap }
import java.util.function.Consumer

/** The main entry point for configuring and creating Liquid templates.
  *
  * Use `TemplateParser.Builder` to configure and build a parser instance.
  */
final class TemplateParser(
  val flavor:                     Flavor,
  val stripSpacesAroundTags:      Boolean,
  val stripSingleLine:            Boolean,
  val insertions:                 Insertions,
  val filters:                    Filters,
  val evaluateInOutputTag:        Boolean,
  val strictTypedExpressions:     Boolean,
  val errorMode:                  TemplateParser.ErrorMode,
  val liquidStyleInclude:         Boolean,
  val liquidStyleWhere:           Boolean,
  val strictVariables:            Boolean,
  val showExceptionsFromInclude:  Boolean,
  val evaluateMode:               TemplateParser.EvaluateMode,
  val locale:                     Locale,
  val renderTransformer:          RenderTransformer,
  val nameResolver:               NameResolver,
  val limitMaxIterations:         Int,
  val limitMaxSizeRenderedString: Int,
  val limitMaxRenderTimeMillis:   Long,
  val limitMaxTemplateSizeBytes:  Long,
  val defaultTimeZone:            ZoneId,
  val dateParser:                 BasicDateParser,
  val environmentMapConfigurator: Consumer[JMap[String, AnyRef]]
) {

  def isRenderTimeLimited: Boolean = limitMaxRenderTimeMillis != Long.MaxValue

  /** Parses a Liquid template from a file path, recording sourceLocation for include_relative. */
  def parse(path: Path): Template = {
    val input = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8)
    parseWithLocation(input, Some(path))
  }

  /** Parses a Liquid template from a File. */
  def parse(file: java.io.File): Template =
    parse(file.toPath)

  /** Parses a Liquid template from an InputStream. */
  def parse(stream: java.io.InputStream): Template =
    parse(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))

  /** Parses a Liquid template from a Reader. */
  def parse(reader: java.io.Reader): Template = {
    val sb = new StringBuilder()
    val buf = new Array[Char](8192)
    var n = reader.read(buf)
    while (n != -1) {
      sb.appendAll(buf, 0, n)
      n = reader.read(buf)
    }
    parse(sb.toString)
  }

  /** Parses a Liquid template string. */
  def parse(input: String): Template =
    parseWithLocation(input, None)

  private def parseWithLocation(input: String, location: Option[Path]): Template = {
    val lexer = new parser.LiquidLexer(
      input,
      stripSpacesAroundTags,
      stripSingleLine,
      insertions.blockNames,
      insertions.tagNames
    )
    val tokens       = lexer.tokenize()
    val liquidParser = new parser.LiquidParser(
      tokens,
      insertions,
      filters,
      liquidStyleInclude,
      evaluateInOutputTag,
      errorMode
    )
    val root = liquidParser.parse()
    new Template(this, root, input.length.toLong, location)
  }

  /** Evaluates an object, converting Inspectable objects to LiquidSupport. */
  def evaluate(variable: Any): LiquidSupport =
    variable match {
      case ls: LiquidSupport => ls
      case _ =>
        new LiquidSupport {
          override def toLiquid(): JMap[String, Any] =
            new java.util.HashMap[String, Any]()
        }
    }
}

object TemplateParser {

  val DEFAULT_LOCALE: Locale = Locale.ENGLISH

  /** Default flavor for SSG is JEKYLL (unlike liqp which defaults to LIQP). */
  val DEFAULT_FLAVOR: Flavor = Flavor.JEKYLL

  /** Default parser with JEKYLL flavor. */
  lazy val DEFAULT: TemplateParser = new Builder().withFlavor(DEFAULT_FLAVOR).build()

  /** Default parser with JEKYLL flavor (convenience alias). */
  lazy val DEFAULT_JEKYLL: TemplateParser = DEFAULT

  /** Equivalent of Ruby's Liquid::Template.error_mode */
  enum ErrorMode extends java.lang.Enum[ErrorMode] {
    case STRICT, WARN, LAX
  }

  /** Evaluation mode for template variables. */
  enum EvaluateMode extends java.lang.Enum[EvaluateMode] {
    case LAZY, EAGER
  }

  /** Builder for TemplateParser — fluent API for configuration. */
  class Builder() {
    private var _flavor:                     Flavor               = scala.compiletime.uninitialized
    private var _stripSpacesAroundTags:      Boolean              = false
    private var _stripSingleLine:            Boolean              = false
    private val _insertions:                 ArrayList[Insertion] = new ArrayList[Insertion]()
    private val _filters:                    ArrayList[Filter]    = new ArrayList[Filter]()
    private var _evaluateInOutputTag:        java.lang.Boolean    = scala.compiletime.uninitialized
    private var _strictTypedExpressions:     java.lang.Boolean    = scala.compiletime.uninitialized
    private var _errorMode:                  ErrorMode            = scala.compiletime.uninitialized
    private var _liquidStyleInclude:         java.lang.Boolean    = scala.compiletime.uninitialized
    private var _liquidStyleWhere:           java.lang.Boolean    = scala.compiletime.uninitialized
    private var _strictVariables:            Boolean              = false
    private var _showExceptionsFromInclude:  Boolean              = true
    private var _evaluateMode:               EvaluateMode         = EvaluateMode.LAZY
    private var _locale:                     Locale               = DEFAULT_LOCALE
    private var _renderTransformer:          RenderTransformer    = scala.compiletime.uninitialized
    private var _snippetsFolderName:         String               = scala.compiletime.uninitialized
    private var _nameResolver:               NameResolver         = scala.compiletime.uninitialized
    private var _limitMaxIterations:         Int                  = Int.MaxValue
    private var _limitMaxSizeRenderedString: Int                  = Int.MaxValue
    private var _limitMaxRenderTimeMillis:   Long                 = Long.MaxValue
    private var _limitMaxTemplateSizeBytes:  Long                 = Long.MaxValue
    private var _defaultTimeZone:            ZoneId                       = scala.compiletime.uninitialized
    private var _dateParser:                 BasicDateParser              = scala.compiletime.uninitialized
    private var _environmentMapConfigurator: Consumer[JMap[String, AnyRef]] = scala.compiletime.uninitialized

    /** Creates a Builder from an existing TemplateParser (copy settings). */
    def this(parser: TemplateParser) = {
      this()
      _flavor = parser.flavor
      _stripSpacesAroundTags = parser.stripSpacesAroundTags
      _stripSingleLine = parser.stripSingleLine
      _insertions.addAll(parser.insertions.values())
      _filters.addAll(parser.filters.values())
      _strictVariables = parser.strictVariables
      _evaluateMode = parser.evaluateMode
      _locale = parser.locale
      _renderTransformer = parser.renderTransformer
      _showExceptionsFromInclude = parser.showExceptionsFromInclude
      _limitMaxIterations = parser.limitMaxIterations
      _limitMaxSizeRenderedString = parser.limitMaxSizeRenderedString
      _limitMaxRenderTimeMillis = parser.limitMaxRenderTimeMillis
      _limitMaxTemplateSizeBytes = parser.limitMaxTemplateSizeBytes
      _evaluateInOutputTag = parser.evaluateInOutputTag
      _strictTypedExpressions = parser.strictTypedExpressions
      _liquidStyleInclude = parser.liquidStyleInclude
      _liquidStyleWhere = parser.liquidStyleWhere
      _errorMode = parser.errorMode
      _nameResolver = parser.nameResolver
      _defaultTimeZone = parser.defaultTimeZone
      _dateParser = parser.dateParser
      _environmentMapConfigurator = parser.environmentMapConfigurator
    }

    def withFlavor(flavor: Flavor):                                    Builder = { _flavor = flavor; this }
    def withStripSpaceAroundTags(strip: Boolean, singleLine: Boolean): Builder = {
      if (singleLine && !strip) {
        throw new IllegalStateException("stripSpacesAroundTags must be true if stripSingleLine is true")
      }
      _stripSpacesAroundTags = strip; _stripSingleLine = singleLine; this
    }
    def withStripSpaceAroundTags(strip:  Boolean):           Builder = withStripSpaceAroundTags(strip, false)
    def withBlock(block:                 blocks.Block):      Builder = { _insertions.add(block); this }
    def withTag(tag:                     tags.Tag):          Builder = { _insertions.add(tag); this }
    def withFilter(filter:               Filter):            Builder = { _filters.add(filter); this }
    def withEvaluateInOutputTag(v:       Boolean):           Builder = { _evaluateInOutputTag = v; this }
    def withStrictTypedExpressions(v:    Boolean):           Builder = { _strictTypedExpressions = v; this }
    def withLiquidStyleInclude(v:        Boolean):           Builder = { _liquidStyleInclude = v; this }
    def withLiquidStyleWhere(v:         Boolean):           Builder = { _liquidStyleWhere = v; this }
    def withStrictVariables(v:           Boolean):           Builder = { _strictVariables = v; this }
    def withShowExceptionsFromInclude(v: Boolean):           Builder = { _showExceptionsFromInclude = v; this }
    def withEvaluateMode(mode:           EvaluateMode):      Builder = { _evaluateMode = mode; this }
    def withRenderTransformer(rt:        RenderTransformer): Builder = { _renderTransformer = rt; this }
    def withLocale(locale:               Locale):            Builder = { _locale = locale; this }
    def withSnippetsFolderName(name:     String):            Builder = { _snippetsFolderName = name; this }
    def withNameResolver(nr:             NameResolver):      Builder = { _nameResolver = nr; this }
    def withMaxIterations(max:           Int):               Builder = { _limitMaxIterations = max; this }
    def withMaxSizeRenderedString(max:   Int):               Builder = { _limitMaxSizeRenderedString = max; this }
    def withMaxRenderTimeMillis(max:     Long):              Builder = { _limitMaxRenderTimeMillis = max; this }
    def withMaxTemplateSizeBytes(max:    Long):              Builder = { _limitMaxTemplateSizeBytes = max; this }
    def withErrorMode(mode:              ErrorMode):         Builder = { _errorMode = mode; this }
    def withDefaultTimeZone(tz:          ZoneId):            Builder = { _defaultTimeZone = tz; this }
    def withDateParser(dp:               BasicDateParser):   Builder = { _dateParser = dp; this }
    def withEnvironmentMapConfigurator(c: Consumer[JMap[String, AnyRef]]): Builder = { _environmentMapConfigurator = c; this }

    def build(): TemplateParser = {
      val fl = if (_flavor != null) _flavor else DEFAULT_FLAVOR

      val evaluateInOutputTag =
        if (_evaluateInOutputTag != null) _evaluateInOutputTag.booleanValue()
        else fl.evaluateInOutputTag

      val strictTypedExpressions =
        if (_strictTypedExpressions != null) _strictTypedExpressions.booleanValue()
        else fl.strictTypedExpressions

      val liquidStyleInclude =
        if (_liquidStyleInclude != null) _liquidStyleInclude.booleanValue()
        else fl.liquidStyleInclude

      val liquidStyleWhere =
        if (_liquidStyleWhere != null) _liquidStyleWhere.booleanValue()
        else fl.liquidStyleWhere

      val errorMode =
        if (_errorMode != null) _errorMode
        else fl.errorMode

      val allInsertions = fl.getInsertions.mergeWith(Insertions.of(_insertions))
      val finalFilters  = fl.getFilters.mergeWith(_filters)

      val snippetsFolderName =
        if (_snippetsFolderName != null) _snippetsFolderName
        else fl.snippetsFolderName

      val nameResolver =
        if (_nameResolver != null) _nameResolver
        else NameResolver.Default(snippetsFolderName)

      val renderTransformer =
        if (_renderTransformer != null) _renderTransformer
        else RenderTransformerDefaultImpl

      val defaultTimeZone =
        if (_defaultTimeZone != null) _defaultTimeZone
        else ZoneId.systemDefault()

      val dateParser =
        if (_dateParser != null) _dateParser
        else new DateParser()

      new TemplateParser(
        flavor = fl,
        stripSpacesAroundTags = _stripSpacesAroundTags,
        stripSingleLine = _stripSingleLine,
        insertions = allInsertions,
        filters = finalFilters,
        evaluateInOutputTag = evaluateInOutputTag,
        strictTypedExpressions = strictTypedExpressions,
        errorMode = errorMode,
        liquidStyleInclude = liquidStyleInclude,
        liquidStyleWhere = liquidStyleWhere,
        strictVariables = _strictVariables,
        showExceptionsFromInclude = _showExceptionsFromInclude,
        evaluateMode = _evaluateMode,
        locale = _locale,
        renderTransformer = renderTransformer,
        nameResolver = nameResolver,
        limitMaxIterations = _limitMaxIterations,
        limitMaxSizeRenderedString = _limitMaxSizeRenderedString,
        limitMaxRenderTimeMillis = _limitMaxRenderTimeMillis,
        limitMaxTemplateSizeBytes = _limitMaxTemplateSizeBytes,
        defaultTimeZone = defaultTimeZone,
        dateParser = dateParser,
        environmentMapConfigurator = _environmentMapConfigurator
      )
    }
  }
}
