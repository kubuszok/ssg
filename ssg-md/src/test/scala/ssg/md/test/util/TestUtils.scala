/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/TestUtils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec._
import ssg.md.util.ast.Node
import ssg.md.util.data._
import ssg.md.util.misc.{CharPredicate, DelimitedBuilder, Extension, Pair}
import ssg.md.util.sequence.{BasedSequence, RichSequence, SegmentedSequence, SequenceUtils}
import ssg.md.util.sequence.builder.SequenceBuilder

import java.{util => ju}
import java.util.function.BiFunction
import scala.language.implicitConversions

object TestUtils {

  val MARKUP_CARET_CHAR: Char = '\u2999'      // ⦙
  val MARKUP_SELECTION_START_CHAR: Char = '\u27E6' // ⟦
  val MARKUP_SELECTION_END_CHAR: Char = '\u27E7'   // ⟧
  val MARKUP_CARET: String = Character.toString(MARKUP_CARET_CHAR)
  val MARKUP_SELECTION_START: String = Character.toString(MARKUP_SELECTION_START_CHAR)
  val MARKUP_SELECTION_END: String = Character.toString(MARKUP_SELECTION_END_CHAR)
  val CARET_PREDICATE: CharPredicate = CharPredicate.anyOf(MARKUP_CARET_CHAR)
  val MARKUP_PREDICATE: CharPredicate = CharPredicate.anyOf(MARKUP_CARET_CHAR, MARKUP_SELECTION_START_CHAR, MARKUP_SELECTION_END_CHAR)
  val EMPTY_OFFSETS: Array[Int] = new Array[Int](0)

  val DISABLED_OPTION_PREFIX_CHAR: Char = '-'
  val DISABLED_OPTION_PREFIX: String = String.valueOf(DISABLED_OPTION_PREFIX_CHAR)

  val EMBED_TIMED_OPTION_NAME: String = "EMBED_TIMED"
  val FAIL_OPTION_NAME: String = "FAIL"
  val FILE_EOL_OPTION_NAME: String = "FILE_EOL"
  val IGNORE_OPTION_NAME: String = "IGNORE"
  val NO_FILE_EOL_OPTION_NAME: String = "NO_FILE_EOL"
  val TIMED_ITERATIONS_OPTION_NAME: String = "TIMED_ITERATIONS"
  val TIMED_OPTION_NAME: String = "TIMED"

  val EMBED_TIMED: DataKey[Boolean] = new DataKey[Boolean](TIMED_OPTION_NAME, false)
  val FAIL: DataKey[Boolean] = new DataKey[Boolean](FAIL_OPTION_NAME, false)
  val IGNORE: DataKey[Boolean] = new DataKey[Boolean](IGNORE_OPTION_NAME, false)
  val NO_FILE_EOL: DataKey[Boolean] = new DataKey[Boolean](NO_FILE_EOL_OPTION_NAME, true)
  val TIMED: DataKey[Boolean] = new DataKey[Boolean](TIMED_OPTION_NAME, false)
  val TIMED_ITERATIONS: DataKey[Int] = new DataKey[Int](TIMED_ITERATIONS_OPTION_NAME, 100)

  val TIMED_FORMAT_STRING: String = "Timing %s: parse %.3f ms, render %.3f ms, total %.3f\n"

  val INCLUDED_DOCUMENT: DataKey[String] = new DataKey[String]("INCLUDED_DOCUMENT", "")
  val SOURCE_PREFIX: DataKey[String] = new DataKey[String]("SOURCE_PREFIX", "")
  val SOURCE_SUFFIX: DataKey[String] = new DataKey[String]("SOURCE_SUFFIX", "")
  val SOURCE_INDENT: DataKey[String] = new DataKey[String]("SOURCE_INDENT", "")

  val NO_FILE_EOL_FALSE: DataHolder = new MutableDataSet().set(NO_FILE_EOL, false).toImmutable
  val UNLOAD_EXTENSIONS: DataKey[ju.Collection[Class[? <: Extension]]] = LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS
  val LOAD_EXTENSIONS: DataKey[ju.Collection[Extension]] = LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS
  private val EMPTY_OPTIONS: DataHolder = new DataSet()
  val CUSTOM_OPTION: DataKey[BiFunction[String, String, DataHolder]] =
    new DataKey[BiFunction[String, String, DataHolder]]("CUSTOM_OPTION", ((_: String, _: String) => EMPTY_OPTIONS): BiFunction[String, String, DataHolder])
  val FILE_PROTOCOL: String = ResourceUrlResolver.FILE_PROTOCOL

  def processOption(optionsMap: ju.Map[String, ? <: DataHolder], option: String): Nullable[DataHolder] = {
    var dataHolder: Nullable[DataHolder] = Nullable.empty
    if (!option.startsWith(DISABLED_OPTION_PREFIX)) {
      dataHolder = Nullable(optionsMap.get(option))
      var customOption = option
      var params: Nullable[String] = Nullable.empty

      if (dataHolder.isEmpty) {
        // see if parameterized option
        val exampleOption = ExampleOption.of(option)
        if (exampleOption.isCustom) {
          // parameterized, see if there is a handler defined for it
          customOption = exampleOption.getOptionName
          params = exampleOption.getCustomParams
          dataHolder = Nullable(optionsMap.get(customOption))
        }
      }

      // if custom option is set then delegate to it
      if (dataHolder.exists(_.contains(CUSTOM_OPTION))) {
        val customHandler = CUSTOM_OPTION.get(dataHolder.get)
        @annotation.nowarn("msg=deprecated") // orNull needed for Java BiFunction interop
        val paramsOrNull = params.orNull
        dataHolder = Nullable(customHandler.apply(customOption, paramsOrNull))
      }
    }
    dataHolder
  }

  def buildOptionsMap[T](options: Array[String], factory: BiFunction[ExampleOption, Integer, T]): ju.HashMap[String, T] = {
    val hashMap = new ju.HashMap[String, T]()
    var i = 0
    for (option <- options) {
      hashMap.put(option, factory.apply(ExampleOption.of(option), i))
      i += 1
    }
    hashMap
  }

  /**
   * Build options map, optionally ensuring all built-ins are present
   *
   * @param ensureAllBuiltInPresent if true, throws IllegalStateException if some built-in options are missing
   * @param options                 array of object arrays, each row represents option values
   *                                with first element ([0]) of each row being an option string.
   *                                Each row is passed to factory to allow creating custom options.
   * @param factory                 factory creating a type from ExampleOption and given row of parameters
   * @tparam T                      type of value in the map
   * @return constructed hash map of option name
   */
  def buildOptionsMap[T](ensureAllBuiltInPresent: Boolean, options: Array[Array[AnyRef]], factory: BiFunction[ExampleOption, Array[AnyRef], T]): ju.HashMap[String, T] = {
    val hashMap = new ju.HashMap[String, T]()
    val builtInSet = new ju.HashSet[String](ExampleOption.getBuiltInOptions.keySet())

    for (optionData <- options) {
      assert(optionData(0).isInstanceOf[String])
      val option = optionData(0).asInstanceOf[String]

      val exampleOption = ExampleOption.of(option)
      hashMap.put(option, factory.apply(exampleOption, optionData))
      if (exampleOption.isBuiltIn && exampleOption.isValid && !(exampleOption.isCustom || exampleOption.isDisabled)) {
        builtInSet.remove(exampleOption.getOptionName)
      }
    }

    if (ensureAllBuiltInPresent && !builtInSet.isEmpty) {
      val sb = new DelimitedBuilder(",\n    ")
      sb.append("    ")
      val iter = builtInSet.iterator()
      while (iter.hasNext) {
        sb.append(iter.next()).mark()
      }

      throw new IllegalStateException("Not all built-in options present. Missing:\n" + sb.toString())
    }
    hashMap
  }

  def addSpecSection(headingLine: String, headingText: String, sectionHeadings: Array[Nullable[String]]): Pair[String, Int] = {
    assert(sectionHeadings.length == 7)
    val lastSectionLevel = Math.max(1, Math.min(6, RichSequence.of(headingLine).countLeading(CharPredicate.HASH)))
    sectionHeadings(lastSectionLevel) = Nullable(headingText)
    val iMax = 7
    var i = lastSectionLevel + 1
    while (i < iMax) {
      sectionHeadings(i) = Nullable.empty
      i += 1
    }

    val sb = new StringBuilder()
    var sep = ""
    var level = 0
    for (heading <- sectionHeadings) {
      if (heading.isDefined && level > 1) {
        sb.append(sep).append(heading.get)
        sep = " - "
        if (level == lastSectionLevel) {
          // break equivalent - just stop appending
          level = sectionHeadings.length // force loop to end
        }
      }
      level += 1
    }

    val section = sb.toString()
    val result = if (section.isEmpty) headingText else section
    Pair.of(result, lastSectionLevel)
  }

  /**
   * process comma separated list of option sets and combine them for final set to use
   *
   * @param example         spec example instance for which options are being processed
   * @param optionSets      comma separate list of option set names
   * @param optionsProvider function to take a string option name and provide settings based on it
   * @return combined set from applying these options together
   */
  def getOptions(example: SpecExample, optionSets: Nullable[String], optionsProvider: String => Nullable[DataHolder]): Nullable[DataHolder] = {
    if (optionSets.isEmpty) {
      Nullable.empty
    } else {
      val optionNames = optionSets.get.replace('\u00A0', ' ').split(",")
      var options: Nullable[DataHolder] = Nullable.empty
      for (optionName <- optionNames) {
        val option = optionName.trim
        if (option.nonEmpty && !option.startsWith("-")) {
          option match {
            case IGNORE_OPTION_NAME =>
              throwIgnoredOption(example, optionSets.get, option)
            case FAIL_OPTION_NAME =>
              options = Nullable(addOption(options, FAIL, true))
            case NO_FILE_EOL_OPTION_NAME =>
              options = Nullable(addOption(options, NO_FILE_EOL, true))
            case FILE_EOL_OPTION_NAME =>
              options = Nullable(addOption(options, NO_FILE_EOL, false))
            case TIMED_OPTION_NAME =>
              options = Nullable(addOption(options, TIMED, true))
            case EMBED_TIMED_OPTION_NAME =>
              options = Nullable(addOption(options, EMBED_TIMED, true))
            case _ =>
              if (options.isEmpty) {
                options = optionsProvider(option)

                if (options.isEmpty) {
                  throwIllegalStateException(example, option)
                }

                options = Nullable(options.get.toImmutable)
              } else {
                val dataSet = optionsProvider(option)

                if (dataSet.isDefined) {
                  // CAUTION: have to only aggregate actions here
                  options = Nullable(DataSet.aggregateActions(options.get.toImmutable, dataSet.get))
                } else {
                  throwIllegalStateException(example, option)
                }
              }

              if (options.exists(o => o.contains(IGNORE) && IGNORE.get(o))) {
                throwIgnoredOption(example, optionSets.get, option)
              }
          }
        }
      }
      options.map(_.toImmutable)
    }
  }

  def addOption[T](options: Nullable[DataHolder], key: DataKey[T], value: T): MutableDataSet = {
    options.fold(new MutableDataSet().set(key, value))(o => new MutableDataSet(o).set(key, value))
  }

  def throwIllegalStateException(example: SpecExample, option: String): Unit = {
    throw new IllegalStateException("Option " + option + " is not implemented in the RenderingTestCase subclass\n" + example.getFileUrlWithLineNumber(-1))
  }

  def throwIgnoredOption(example: SpecExample, optionSets: String, option: String): Unit = {
    // JUnit 4: AssumptionViolatedException — will need adaptation to munit later
    throw new RuntimeException("Ignored: example(" + example.section.getOrElse("") + ": " + example.exampleNumber + ") options(" + optionSets + ") is using " + option + " option\n" + example.getFileUrlWithLineNumber(-1))
  }

  def ast(node: Node): String = {
    new AstCollectingVisitor().collectAndGetAstText(node)
  }

  def stripIndent(input: BasedSequence, sourceIndent: CharSequence): BasedSequence = {
    var result = input
    if (sourceIndent.length() != 0) {
      // strip out indent to test how segmented input parses
      val segments = new ju.ArrayList[BasedSequence]()
      var lastPos = 0
      val length = input.length

      while (lastPos < length) {
        val pos = input.indexOf(sourceIndent, lastPos)
        val end = if (pos == -1) length else pos

        if (lastPos < end && (pos <= 0 || input.charAt(pos - 1) == '\n')) {
          segments.add(input.subSequence(lastPos, end))
        }
        lastPos = end + sourceIndent.length()
      }

      result = SegmentedSequence.create(input, segments)
    }
    result
  }

  def addSpecExample(includeExampleStart: Boolean, source: String, html: String, ast: Nullable[String], optionsSet: Nullable[String]): String = {
    val sb = new StringBuilder()
    addSpecExample(includeExampleStart, sb, source, html, ast, optionsSet, false, Nullable.empty[String], 0)
    sb.toString()
  }

  def addSpecExample(includeExampleStart: Boolean, sb: StringBuilder, source: String, html: String, ast: Nullable[String], optionsSet: Nullable[String], includeExampleCoords: Boolean, section: Nullable[String], number: Int): Unit = {
    addSpecExample(includeExampleStart, true, sb, source, html, ast, optionsSet, includeExampleCoords, section, number)
  }

  def addSpecExample(includeExampleStart: Boolean, toVisibleSpecText: Boolean, sb: StringBuilder, source: String, html: String, ast: Nullable[String], optionsSet: Nullable[String], includeExampleCoords: Boolean, section: Nullable[String], number: Int): Unit = {
    addSpecExample(false, includeExampleStart, toVisibleSpecText, sb, source, html, ast, optionsSet, includeExampleCoords, section, number)
  }

  def addSpecExample(useTestExample: Boolean, includeExampleStart: Boolean, toVisibleSpecText: Boolean, sb: StringBuilder, source: String, html: String, ast: Nullable[String], optionsSet: Nullable[String], includeExampleCoords: Boolean, section: Nullable[String], number: Int): Unit = {
    addSpecExample(
      if (useTestExample) SpecReader.EXAMPLE_TEST_BREAK else SpecReader.EXAMPLE_BREAK,
      if (useTestExample) SpecReader.SECTION_TEST_BREAK else SpecReader.SECTION_BREAK,
      includeExampleStart,
      toVisibleSpecText,
      sb.underlying,
      source,
      html,
      ast.map(s => s: CharSequence),
      optionsSet.map(s => s: CharSequence),
      includeExampleCoords,
      section.map(s => s: CharSequence),
      number
    )
  }

  def addSpecExample(
      exampleBreak: CharSequence,
      sectionBreak: CharSequence,
      includeExampleStart: Boolean,
      toVisibleSpecText: Boolean,
      out: Appendable,
      source: CharSequence,
      html: CharSequence,
      ast: Nullable[CharSequence],
      optionsSet: Nullable[CharSequence],
      includeExampleCoords: Boolean,
      section: Nullable[CharSequence],
      number: Int
  ): Unit = {
    addSpecExample(
      exampleBreak,
      sectionBreak,
      sectionBreak,
      exampleBreak,
      includeExampleStart,
      toVisibleSpecText,
      out,
      source,
      html,
      ast,
      optionsSet,
      includeExampleCoords,
      section,
      Integer.toString(number),
      SpecReader.EXAMPLE_KEYWORD,
      SpecReader.OPTIONS_KEYWORD
    )
  }

  def addSpecExample(
      exampleBreakOpen: CharSequence,
      htmlBreak: CharSequence,
      astBreak: CharSequence,
      exampleBreakClose: CharSequence,
      includeExampleStart: Boolean,
      toVisibleSpecText: Boolean,
      out: Appendable,
      source: CharSequence,
      html: CharSequence,
      ast: Nullable[CharSequence],
      optionsSet: Nullable[CharSequence],
      includeExampleCoords: Boolean,
      section: Nullable[CharSequence],
      number: CharSequence,
      exampleKeyword: CharSequence,
      optionsKeyword: CharSequence
  ): Unit = {
    // include source so that diff can be used to update spec
    try {
      if (includeExampleStart) {
        out.append(exampleBreakOpen).append(' ').append(exampleKeyword)
        if (includeExampleCoords) {
          if (optionsSet.exists(o => !SequenceUtils.isBlank(o))) {
            out.append("(").append(section.fold("")(s => SequenceUtils.trim(s))).append(": ").append(number).append(")")
          } else {
            out.append(" ").append(section.fold("")(s => SequenceUtils.trim(s))).append(": ").append(number)
          }
        }
        if (optionsSet.exists(o => !SequenceUtils.isBlank(o))) {
          out.append(' ').append(optionsKeyword).append("(").append(optionsSet.get).append(")")
        }
        out.append("\n")
      }

      // FIX: When multi-sections are implemented need a way to specify per section visibleSpecText
      if (toVisibleSpecText) {
        if (!SequenceUtils.isEmpty(source)) {
          out.append(TestUtils.toVisibleSpecText(source))
          if (!SequenceUtils.endsWithEOL(source)) out.append("\n")
        }

        out.append(htmlBreak)
        if (!SequenceUtils.endsWithEOL(htmlBreak)) out.append("\n")

        if (html != null && !SequenceUtils.isEmpty(html)) {
          out.append(TestUtils.toVisibleSpecText(html))
          if (!SequenceUtils.endsWithEOL(html)) out.append("\n")
        }
      } else {
        if (!SequenceUtils.isEmpty(source)) {
          out.append(source)
          if (!SequenceUtils.endsWithEOL(source)) out.append("\n")
        }

        out.append(htmlBreak)
        if (!SequenceUtils.endsWithEOL(htmlBreak)) out.append("\n")

        if (html != null && !SequenceUtils.isEmpty(html)) {
          out.append(html)
          if (!SequenceUtils.endsWithEOL(html)) out.append("\n")
        }
      }
      ast.foreach { a =>
        if (a ne BasedSequence.NULL) {
          out.append(astBreak)
          if (!SequenceUtils.endsWithEOL(htmlBreak)) out.append("\n")

          if (!SequenceUtils.isEmpty(a)) {
            out.append(a)
            if (!SequenceUtils.endsWithEOL(a)) out.append("\n")
          }
        }
      }
      out.append(exampleBreakClose)
      if (!SequenceUtils.endsWithEOL(exampleBreakClose)) out.append("\n")
    } catch {
      case e: java.io.IOException =>
        e.printStackTrace()
    }
  }

  /**
   * @param s text to convert to visible chars
   * @return spec test special chars converted to visible
   */
  @deprecated("use toVisibleSpecText", "0.1.0")
  def showTabs(s: String): String = toVisibleSpecText(s)

  /**
   * @param s text to convert to visible chars
   * @return spec test special chars converted to visible
   */
  def toVisibleSpecText(s: String): String = {
    if (s == null) "" else toVisibleSpecText(s: CharSequence).toString
  }

  /**
   * @param s text to convert to visible chars
   * @return spec test special chars converted to visible
   */
  def toVisibleSpecText(s: CharSequence): CharSequence = {
    if (s == null) {
      ""
    } else {
      // Tabs are shown as "rightwards arrow" for easier comparison and IntelliJ dummy identifier as 23ae, CR 23ce, LS to U+27A5
      val sequence = BasedSequence.of(s)
      sequence
        .replace("\u2192", "&#2192;").replace("\t", "\u2192")
        .replace("\u23ae", "&#23ae;").replace("\u001f", "\u23ae")
        .replace("\u23ce", "&#23ce;").replace("\r", "\u23ce")
        .replace("\u27a5", "&#27a5;").replace(SequenceUtils.LINE_SEP, "\u27a5")
    }
  }

  /**
   * @param s text to convert to from visible chars to normal
   * @return spec test special visible chars converted to normal
   */
  @deprecated("use fromVisibleSpecText", "0.1.0")
  def unShowTabs(s: String): String = fromVisibleSpecText(s)

  /**
   * @param s text to convert to from visible chars to normal
   * @return spec test special visible chars converted to normal
   */
  def fromVisibleSpecText(s: String): String = {
    if (s == null) "" else fromVisibleSpecText(s: CharSequence).toString
  }

  /**
   * @param s text to convert to from visible chars to normal
   * @return spec test special visible chars converted to normal
   */
  def fromVisibleSpecText(s: CharSequence): CharSequence = {
    if (s == null) {
      ""
    } else {
      val sequence = BasedSequence.of(s)
      sequence
        .replace("\u27a5", SequenceUtils.LINE_SEP).replace("&#27a5;", "\u27a5")
        .replace("\u23ce", "\r").replace("&#23ce;", "\u23ce")
        .replace("\u23ae", "\u001f").replace("&#23ae;", "\u23ae")
        .replace("\u2192", "\t").replace("&#2192;", "\u2192")
    }
  }

  def trimTrailingEOL(parseSource: String): String = {
    if (parseSource.nonEmpty && parseSource.charAt(parseSource.length - 1) == '\n') {
      // if previous line is blank, then no point in removing this EOL, just leave it
      val pos = parseSource.lastIndexOf('\n', parseSource.length - 2)
      if (pos == -1 || parseSource.substring(pos + 1).trim.nonEmpty) {
        parseSource.substring(0, parseSource.length - 1)
      } else {
        parseSource
      }
    } else {
      parseSource
    }
  }

  def getFormattedTimingInfo(iterations: Int, start: Long, parse: Long, render: Long): String = {
    getFormattedTimingInfo("", 0, iterations, start, parse, render)
  }

  def getFormattedTimingInfo(section: String, exampleNumber: Int, iterations: Int, start: Long, parse: Long, render: Long): String = {
    String.format(
      TIMED_FORMAT_STRING,
      getFormattedSection(section, exampleNumber),
      ((parse - start) / 1000000.0 / iterations).asInstanceOf[AnyRef],
      ((render - parse) / 1000000.0 / iterations).asInstanceOf[AnyRef],
      ((render - start) / 1000000.0 / iterations).asInstanceOf[AnyRef]
    )
  }

  def getFormattedSection(section: String, exampleNumber: Int): String = {
    if (section == null || section.isEmpty) "" else section.trim + ": " + exampleNumber
  }

  def getResolvedSpecResourcePath(testClassName: String, resourcePath: String): String = {
    if (resourcePath.startsWith("/")) {
      resourcePath
    } else {
      val classPath = "/" + testClassName.replace('.', '/')
      val parentDir = classPath.substring(0, classPath.lastIndexOf('/'))
      parentDir + "/" + resourcePath
    }
  }

  def getAbsoluteSpecResourcePath(testClassPath: String, resourceRootPath: String, resourcePath: String): String = {
    if (resourcePath.startsWith("/")) {
      val root = if (resourceRootPath.endsWith("/")) resourceRootPath else resourceRootPath + "/"
      root + resourcePath.substring(1)
    } else {
      val parentDir = testClassPath.substring(0, testClassPath.lastIndexOf('/'))
      parentDir + "/" + resourcePath
    }
  }

  def getSpecResourceFileUrl(resourceClass: Class[?], resourcePath: String): String = {
    if (resourcePath.isEmpty) {
      throw new IllegalStateException("Empty resource paths not supported")
    } else {
      val resolvedResourcePath = getResolvedSpecResourcePath(resourceClass.getName, resourcePath)
      // Verify the resource exists via getResourceAsStream (cross-platform)
      val stream = resourceClass.getResourceAsStream(resolvedResourcePath)
      assert(stream != null, "Resource path: '" + resolvedResourcePath + "' not found.") // Java interop: getResourceAsStream returns null when not found
      stream.close()
      "file:" + resolvedResourcePath
    }
  }

  def getTestData(location: ResourceLocation): ju.ArrayList[Array[AnyRef]] = {
    val specReader = SpecReader.createAndReadExamples(location, true)
    val examples = specReader.getExamples
    val data = new ju.ArrayList[Array[AnyRef]]()

    // NULL example runs full spec test
    data.add(Array[AnyRef](SpecExample.NULL.withResourceLocation(location)))

    val iter = examples.iterator()
    while (iter.hasNext) {
      data.add(Array[AnyRef](iter.next()))
    }
    data
  }

  def getUrlWithLineNumber(fileUrl: String, lineNumber: Int): String = {
    if (lineNumber > 0) fileUrl + ":" + (lineNumber + 1) else fileUrl
  }

  def combineDefaultOptions(defaultOptions: Nullable[Array[DataHolder]]): Nullable[DataHolder] = {
    defaultOptions.flatMap { opts =>
      var combinedOptions: Nullable[DataHolder] = Nullable.empty
      for (options <- opts) {
        combinedOptions = Nullable(DataSet.aggregate(combinedOptions, Nullable(options)))
      }
      combinedOptions.map(_.toImmutable)
    }
  }

  def optionsMaps(other: Nullable[ju.Map[String, ? <: DataHolder]], overrides: Nullable[ju.Map[String, ? <: DataHolder]]): Nullable[ju.Map[String, ? <: DataHolder]] = {
    if (other.isDefined && overrides.isDefined) {
      val map = new ju.HashMap[String, DataHolder](other.get)
      map.putAll(overrides.get)
      Nullable(map)
    } else if (other.isDefined) {
      other
    } else {
      overrides
    }
  }

  def dataHolders(other: Nullable[DataHolder], overrides: Nullable[Array[DataHolder]]): Nullable[Array[DataHolder]] = {
    if (other.isEmpty) {
      overrides
    } else if (overrides.isEmpty || overrides.exists(_.isEmpty)) {
      Nullable(Array[DataHolder](other.get))
    } else {
      val ov = overrides.get
      val holders = new Array[DataHolder](ov.length + 1)
      System.arraycopy(ov, 0, holders, 1, ov.length)
      holders(0) = other.get
      Nullable(holders)
    }
  }

  def getTestResourceRootDirectoryForModule(resourceClass: Class[?], moduleRootPackage: String): String = {
    import ssg.md.util.misc.Utils._
    val fileUrl = getSpecResourceFileUrl(resourceClass, wrapWith(moduleRootPackage, "/", ".txt"))
    removePrefix(removeSuffix(fileUrl, suffixWith(moduleRootPackage, ".txt")), FILE_PROTOCOL)
  }

  def getRootDirectoryForModule(resourceClass: Class[?], moduleDirectoryName: String): String = {
    import ssg.md.util.misc.Utils._
    // get project root from our class file url path
    var fileUrl = SpecExample.ofCaller(0, resourceClass, "", "", Nullable("")).fileUrl
    val pos = fileUrl.indexOf(wrapWith(moduleDirectoryName, '/'))
    if (pos != -1) {
      fileUrl = fileUrl.substring(0, pos)
    }
    fileUrl = fileUrl.substring(FILE_PROTOCOL.length)
    fileUrl
  }

  // handle custom string options
  def customStringOption(option: String, params: Nullable[String], resolver: String => DataHolder): DataHolder = {
    params.fold(resolver(null)) { p => // Java interop: resolver may expect null
      val text = p
        .replace("\\\\", "\\")
        .replace("\\]", "]")
        .replace("\\t", "\t")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\b", "\b")
      resolver(text)
    }
  }

  def customIntOption(option: String, params: Nullable[String], resolver: Int => DataHolder): DataHolder = {
    var value = -1
    params.foreach { p =>
      if (!p.matches("\\d*")) {
        throw new IllegalStateException("'" + option + "' option requires a numeric or empty (for default) argument")
      }
      value = Integer.parseInt(p)
    }

    resolver(value)
  }

  def insertCaretMarkup(sequence: BasedSequence, offsets: Array[Int]): SequenceBuilder = {
    val builder: SequenceBuilder = sequence.getBuilder[SequenceBuilder]
    java.util.Arrays.sort(offsets)

    val length = sequence.length
    var lastOffset = 0
    for (offset <- offsets) {
      val useOffset = Math.min(length, offset)

      if (useOffset > lastOffset) {
        sequence.subSequence(lastOffset, useOffset).addSegments(builder.segmentBuilder)
      }
      if (useOffset == offset) builder.append("\u2999")
      lastOffset = useOffset
    }

    val offset = sequence.length
    if (offset > lastOffset) {
      sequence.subSequence(lastOffset, offset).addSegments(builder.segmentBuilder)
    }

    builder
  }

  def extractMarkup(input: BasedSequence): Pair[BasedSequence, Array[Int]] = {
    val markup = input.countOfAny(MARKUP_PREDICATE)

    if (markup > 0) {
      val carets = input.countOfAny(CARET_PREDICATE)
      val offsets = new Array[Int](carets)

      val selections = markup - carets
      assert(selections % 2 == 0)

      val indents = selections / 2

      val starts = new Array[Int](indents)
      val ends = new Array[Int](indents)

      var lastPos = input.length
      var c = carets
      var m = markup
      var i = indents

      var toWrap = input.toString
      var endIndent = -1

      while (lastPos >= 0) {
        val pos = input.lastIndexOfAny(MARKUP_PREDICATE, lastPos)
        if (pos == -1) {
          lastPos = -1 // exit loop
        } else {
          val ch = input.charAt(pos)
          m -= 1
          ch match {
            case MARKUP_CARET_CHAR =>
              c -= 1
              offsets(c) = pos - m // reduce by number of markups ahead

            case MARKUP_SELECTION_START_CHAR =>
              assert(endIndent != -1)
              i -= 1
              starts(i) = pos - m // reduce by number of markups ahead
              ends(i) = endIndent // reduce by number of markups ahead
              endIndent = -1

            case MARKUP_SELECTION_END_CHAR =>
              assert(endIndent == -1)
              endIndent = pos - m // reduce by number of markups ahead

            case _ =>
              throw new IllegalStateException("Unexpected predicate match")
          }

          toWrap = toWrap.substring(0, pos) + toWrap.substring(pos + 1)
          lastPos = pos - 1
        }
      }

      assert(endIndent == -1)
      assert(c == 0, "Unused caret pos: " + c)
      assert(i == 0, "Unused indent pos: " + i)

      val sequence = BasedSequence.of(toWrap)

      // now we delete the indents to simulate prefix removal
      val builder: SequenceBuilder = sequence.getBuilder[SequenceBuilder]
      val jMax = starts.length
      var lastOffset = 0
      var j = 0
      while (j < jMax) {
        val start = starts(j)
        val end = ends(j)

        if (start > lastOffset) {
          sequence.subSequence(lastOffset, start).addSegments(builder.segmentBuilder)
        }
        lastOffset = end
        j += 1
      }

      val offset = sequence.length
      if (offset > lastOffset) {
        sequence.subSequence(lastOffset, offset).addSegments(builder.segmentBuilder)
      }

      Pair.of(builder.toSequence, offsets)
    } else {
      Pair.of(input, EMPTY_OFFSETS)
    }
  }

  val BANNER_PADDING: String = "------------------------------------------------------------------------"
  val BANNER_LENGTH: Int = BANNER_PADDING.length

  def bannerText(message: String): String = {
    val leftPadding = 4 //(BANNER_LENGTH - message.length - 2) >> 4
    val rightPadding = BANNER_LENGTH - message.length - 2 - leftPadding
    BANNER_PADDING.substring(0, leftPadding) + " " + message + " " + BANNER_PADDING.substring(0, rightPadding) + "\n"
  }

  def appendBanner(out: StringBuilder, banner: String): Unit = {
    appendBanner(out, banner, true)
  }

  def appendBanner(out: StringBuilder, banner: String, addBlankLine: Boolean): Unit = {
    if (out.length > 0 && addBlankLine) {
      out.append("\n")
    }

    out.append(banner)
  }

  def appendBannerIfNeeded(out: StringBuilder, banner: String): Unit = {
    if (out.length > 0) {
      out.append("\n")
      out.append(banner)
    }
  }
}
