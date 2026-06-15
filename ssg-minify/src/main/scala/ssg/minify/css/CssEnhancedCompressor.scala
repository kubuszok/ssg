/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: cssminify2 lib/cssminify2/enhanced.rb
 * Original: Copyright (c) DigitalSparky (cssminify2 gem)
 * Original license: MIT
 *
 * Enhanced CSS Compression Features (Optional)
 *
 * This file provides optional enhanced features for CSS compression
 * while maintaining 100% backward compatibility with the original API.
 *
 * Usage:
 *   // Original API (unchanged)
 *   CssMinifier.minify(css)
 *
 *   // Enhanced API (new, optional)
 *   CssEnhancedCompressor.compress(css, config)
 *   new CssEnhancedCompressor(config).compress(css)
 *
 * Migration notes:
 *   Renames: CSSminify2Enhanced::Compressor → ssg.minify.css.CssEnhancedCompressor;
 *     CssCompressor.compress(css, linebreakpos) (the gem's base compressor) →
 *     CssMinifier.minify(css, options) (SSG's base CSS minifier — enhanced.rb:81
 *     layers enhanced passes on top of base CssCompressor output, so this
 *     compressor layers on top of CssMinifier.minify exactly the same way);
 *     CSSminify2Enhanced.{compress,compress_with_stats} (enhanced.rb:773,785) →
 *     companion-object methods; cssminify2.rb:50-82 compress_enhanced/
 *     compress_with_stats dispatch → companion-object methods on this object.
 *   Convention: Ruby Hash statistics → CssEnhancedStatistics holder; the
 *     `linebreakpos` integer is carried through but the base SSG minifier does
 *     not break lines, so it is passed for parity only.
 *   Idiom: Ruby `gsub(pattern) { block }` with `$1`/`$2` backreferences IN THE
 *     PATTERN (e.g. \1\s+\1) is not portable to re2 (Native) / JS, so each such
 *     pattern matches the repeated values as separate capture groups and the
 *     equality test that the backreference encoded is performed in Scala code.
 *     `next`/`return` inside blocks → boundary/break.
 *
 * Covenant: full-port
 * Covenant-ruby-reference: lib/cssminify2/enhanced.rb
 * Covenant-verified: 2026-06-15
 */
package ssg
package minify
package css

import lowlevel.Nullable

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break
import scala.util.matching.Regex

// Error class for enhanced features
final class EnhancedCompressionError(message: String, val originalError: Nullable[Throwable]) extends RuntimeException(message) {
  def this(message: String) = this(message, Nullable.empty)
}

// Error class for malformed CSS
final class MalformedCSSError(message: String, val cssErrors: List[String]) extends RuntimeException(message) {
  def this(message: String) = this(message, Nil)
}

// Mutable statistics holder mirroring the Ruby @statistics Hash.
final class CssEnhancedStatistics {
  var originalSize:        Int     = 0
  var compressedSize:      Int     = 0
  var compressionRatio:    Double  = 0.0
  var selectorsMerged:     Int     = 0
  var propertiesOptimized: Int     = 0
  var colorsConverted:     Int     = 0
  var fallbackUsed:        Boolean = false
}

// Enhanced compressor with new features
final class CssEnhancedCompressor(val config: CssEnhancedConfig) {

  import CssEnhancedCompressor.*

  def this() = this(new CssEnhancedConfig())

  val statistics: CssEnhancedStatistics = new CssEnhancedStatistics()

  def compress(css: String, linebreakpos: Int = 5000): String = {
    statistics.originalSize = css.length

    try {
      // Validate CSS structure before processing
      if (config.strictErrorHandling) validateCssStructure(css)

      // Start with the original compression to maintain compatibility
      var result = baseCompress(css, linebreakpos)

      // Apply enhanced optimizations if enabled
      if (anyEnhancementsEnabled) {
        result = applyEnhancedOptimizationsSafely(result)
      }

      statistics.compressedSize = result.length
      statistics.compressionRatio = calculateCompressionRatio

      result
    } catch {
      case e: Throwable =>
        if (config.strictErrorHandling) {
          throw new EnhancedCompressionError(s"Enhanced compression failed: ${e.getMessage}", Nullable(e))
        } else {
          // Graceful fallback to original compressor
          val fallbackResult = safeFallbackCompression(css, linebreakpos)
          statistics.compressedSize = fallbackResult.length
          statistics.compressionRatio = calculateCompressionRatio
          statistics.fallbackUsed = true
          fallbackResult
        }
    }
  }

  // The base compressor: enhanced.rb calls CssCompressor.compress(css, linebreakpos);
  // SSG's equivalent is CssMinifier.minify. linebreakpos is accepted for API parity
  // (the base SSG minifier does not break long lines).
  private def baseCompress(css: String, linebreakpos: Int): String = {
    val _ = linebreakpos
    CssMinifier.minify(css)
  }

  private def anyEnhancementsEnabled: Boolean =
    config.mergeDuplicateSelectors ||
      config.optimizeShorthandProperties ||
      config.advancedColorOptimization ||
      config.compressCssVariables

  // Direct (unsafe) optimization pipeline, kept faithful to enhanced.rb:116-124
  // (apply_enhanced_optimizations). The original defines this private method but
  // never calls it — compress uses apply_enhanced_optimizations_safely instead.
  // Ported for completeness with the source; @nowarn mirrors that it is unused there too.
  @scala.annotation.nowarn("msg=unused private member")
  private def applyEnhancedOptimizations(cssIn: String): String = {
    var css = cssIn
    if (config.mergeDuplicateSelectors) css = mergeDuplicateSelectors(css)
    if (config.optimizeShorthandProperties) css = optimizeShorthandProperties(css)
    if (config.optimizeShorthandProperties) css = enhanceZeroValueOptimization(css)
    if (config.optimizeShorthandProperties) css = optimizeModernLayoutProperties(css)
    if (config.compressCssVariables) css = compressCssVariables(css)
    if (config.advancedColorOptimization) css = advancedColorOptimization(css)
    css
  }

  private def applyEnhancedOptimizationsSafely(cssIn: String): String = {
    var css = cssIn
    // Apply optimizations with individual error handling
    val optimizations: List[(String, Boolean)] = List(
      ("merge_duplicate_selectors", config.mergeDuplicateSelectors),
      ("optimize_shorthand_properties", config.optimizeShorthandProperties),
      ("enhance_zero_value_optimization", config.optimizeShorthandProperties),
      ("optimize_modern_layout_properties", config.optimizeShorthandProperties),
      ("compress_css_variables", config.compressCssVariables),
      ("advanced_color_optimization", config.advancedColorOptimization)
    )

    optimizations.foreach { case (methodName, enabled) =>
      if (enabled) {
        try
          css = dispatch(methodName, css)
        catch {
          case e: Throwable =>
            if (config.strictErrorHandling) {
              throw e
            } else {
              // Log error but continue with other optimizations
              warn(s"Warning: $methodName optimization failed: ${e.getMessage}")
            }
        }
      }
    }

    css
  }

  // Mirrors Ruby's `send(method_name, css)` dynamic dispatch (enhanced.rb:141).
  private def dispatch(methodName: String, css: String): String =
    methodName match {
      case "merge_duplicate_selectors"         => mergeDuplicateSelectors(css)
      case "optimize_shorthand_properties"     => optimizeShorthandProperties(css)
      case "enhance_zero_value_optimization"   => enhanceZeroValueOptimization(css)
      case "optimize_modern_layout_properties" => optimizeModernLayoutProperties(css)
      case "compress_css_variables"            => compressCssVariables(css)
      case "advanced_color_optimization"       => advancedColorOptimization(css)
      case other                               => throw new EnhancedCompressionError(s"unknown optimization: $other")
    }

  private def validateCssStructure(css: String): Unit = {
    // Basic CSS validation to catch major structural issues
    val errors = mutable.ListBuffer[String]()

    // Check for balanced braces
    val openBraces  = css.count(_ == '{')
    val closeBraces = css.count(_ == '}')
    if (openBraces != closeBraces) {
      errors += s"Unbalanced braces: $openBraces opening vs $closeBraces closing"
    }

    // Check for balanced quotes
    val doubleQuotes = css.count(_ == '"')
    val singleQuotes = css.count(_ == '\'')
    if (doubleQuotes % 2 != 0) {
      errors += "Unmatched double quotes"
    }
    if (singleQuotes % 2 != 0) {
      errors += "Unmatched single quotes"
    }

    // Check for valid CSS structure patterns
    if (NestedBraces.findFirstIn(css).isDefined) { // Nested braces outside of media queries/keyframes
      if (AtRuleNestable.findFirstIn(css).isEmpty) {
        errors += "Potentially invalid nested braces"
      }
    }

    // Check for common syntax errors
    if (MissingSemicolon.findFirstIn(css).isDefined && AtSign.findFirstIn(css).isEmpty) {
      errors += "Missing semicolon before closing brace"
    }

    if (errors.nonEmpty) {
      throw new MalformedCSSError(s"CSS validation failed: ${errors.mkString(", ")}")
    }
  }

  private def safeFallbackCompression(css: String, linebreakpos: Int): String =
    // Safe fallback with lightweight error handling
    try
      baseCompress(css, linebreakpos)
    catch {
      case e: Throwable =>
        // Last resort: basic whitespace compression
        warn(s"Warning: Fallback to basic compression due to: ${e.getMessage}")
        basicCompressionFallback(css)
    }

  private def basicCompressionFallback(css: String): String =
    // Ultra-safe basic compression as last resort
    css
      .replaceAll("(?s)/\\*.*?\\*/", "") // Remove comments
      .replaceAll("\\s+", " ") // Compress whitespace
      .replaceAll("\\s*\\{\\s*", "{") // Clean braces
      .replaceAll("\\s*\\}\\s*", "}")
      .replaceAll("\\s*;\\s*", ";") // Clean semicolons
      .replaceAll(":\\s+", ":") // Clean colons
      .trim

  // ---------------------------------------------------------------------------
  // merge_duplicate_selectors (enhanced.rb:216)
  // ---------------------------------------------------------------------------

  private def mergeDuplicateSelectors(cssIn: String): String = {
    // Advanced duplicate selector merging with proper CSS parsing
    // Handles media queries, keyframes, and preserves cascade order

    val selectorsMerged = 0

    var css = cssIn
    // Split CSS into blocks (rules, at-rules, etc.)
    css = MediaBlock.replaceAllIn(css, m => Regex.quoteReplacement(processMediaBlock(m.matched)))

    // Process regular CSS rules outside of media queries
    css = mergeRegularSelectors(css)

    statistics.selectorsMerged = selectorsMerged
    css
  }

  private def processMediaBlock(mediaBlock: String): String =
    // Extract media query and content
    MediaQueryContent.findFirstMatchIn(mediaBlock) match {
      case Some(m) =>
        val mediaQuery = m.group(1).trim
        val content    = m.group(2)

        // Merge selectors within this media query
        val mergedContent = mergeRegularSelectors(content)

        s"@media$mediaQuery{$mergedContent}"
      case None =>
        mediaBlock
    }

  private def mergeRegularSelectors(css: String): String = {
    // Parse CSS rules more carefully
    val rules       = parseCssRules(css)
    val mergedRules = mergeParsedRules(rules)
    rebuildCssFromRules(mergedRules)
  }

  // Mutable rule record mirroring the Ruby Hash {selector, original_selector, declarations, position}.
  final private class CssRule(
    val selector:         String,
    val originalSelector: String,
    var declarations:     String,
    val position:         Int
  ) {
    def dup: CssRule = new CssRule(selector, originalSelector, declarations, position)
  }

  private def parseCssRules(css: String): List[CssRule] = {
    val rules      = mutable.ListBuffer[CssRule]()
    var currentPos = 0

    boundary {
      while (currentPos < css.length) {
        // Find next rule
        val ruleMatch = RuleMatch.findFirstMatchIn(css.substring(currentPos)) match {
          case Some(m) => m
          case None    => break()
        }

        val selector     = ruleMatch.group(1).trim
        val declarations = ruleMatch.group(2).trim
        val position     = currentPos + ruleMatch.start

        // Skip if this looks like an at-rule we shouldn't merge
        if (AtRuleSkip.findFirstIn(selector).isEmpty) {
          rules += new CssRule(
            selector = normalizeSelector(selector),
            originalSelector = selector,
            declarations = declarations,
            position = position
          )
        }

        currentPos = currentPos + ruleMatch.end
      }
    }

    rules.toList
  }

  private def normalizeSelector(selector: String): String =
    // Normalize selector for comparison (remove extra whitespace, etc.)
    WhitespaceRun.replaceAllIn(selector, " ").trim

  private def mergeParsedRules(rules: List[CssRule]): List[CssRule] = {
    // Group rules by selector, maintaining order
    val selectorGroups = mutable.HashMap[String, CssRule]()
    val mergedRules    = mutable.ListBuffer[CssRule]()

    rules.foreach { rule =>
      val selector = rule.selector

      selectorGroups.get(selector) match {
        case Some(existingRule) =>
          // Merge with existing rule
          existingRule.declarations = mergeDeclarations(
            existingRule.declarations,
            rule.declarations
          )
          statistics.selectorsMerged += 1
        case None =>
          // First occurrence of this selector
          val newRule = rule.dup
          selectorGroups(selector) = newRule
          mergedRules += newRule
      }
    }

    mergedRules.toList
  }

  private def mergeDeclarations(existingDeclarations: String, newDeclarations: String): String = {
    // Parse declarations and merge, with later declarations overriding earlier ones
    val existingProps = parseDeclarations(existingDeclarations)
    val newProps      = parseDeclarations(newDeclarations)

    // Merge properties, with new_props taking precedence (preserve insertion order:
    // existing keys keep their slot, new keys append — matches Ruby Hash#merge).
    val mergedProps = mutable.LinkedHashMap[String, String]()
    existingProps.foreach { case (k, v) => mergedProps(k) = v }
    newProps.foreach { case (k, v) => mergedProps(k) = v }

    // Rebuild declaration string
    mergedProps.map { case (prop, value) => s"$prop:$value" }.mkString(";")
  }

  private def parseDeclarations(declarations: String): mutable.LinkedHashMap[String, String] = {
    val properties = mutable.LinkedHashMap[String, String]()
    if (declarations.isEmpty) {
      properties
    } else {
      declarations.split(";", -1).foreach { declaration =>
        DeclarationMatch.findFirstMatchIn(declaration).foreach { m =>
          val property = m.group(1).trim
          val value    = m.group(2).trim
          properties(property) = value
        }
      }
      properties
    }
  }

  private def rebuildCssFromRules(rules: List[CssRule]): String =
    rules
      .map { rule =>
        val declarations = if (rule.declarations.isEmpty) "" else rule.declarations
        s"${rule.originalSelector}{$declarations}"
      }
      .mkString("")

  // ---------------------------------------------------------------------------
  // optimize_shorthand_properties (enhanced.rb:355)
  // ---------------------------------------------------------------------------

  private def optimizeShorthandProperties(cssIn: String): String = {
    val originalLength = cssIn.length

    var css = cssIn
    // Advanced margin/padding optimization with units flexibility
    css = optimizeBoxModelProperties(css, "margin")
    css = optimizeBoxModelProperties(css, "padding")

    // Background shorthand optimizations
    css = optimizeBackgroundProperties(css)

    // Border shorthand optimizations
    css = optimizeBorderProperties(css)

    // Font shorthand optimizations
    css = optimizeFontProperties(css)

    // List-style optimizations
    css = optimizeListProperties(css)

    statistics.propertiesOptimized += ((originalLength - css.length) / 10).toInt // Rough estimate
    css
  }

  // (?:px|em|rem|%|vh|vw|pt|pc|in|cm|mm|ex|ch|vmin|vmax|0)
  private val UnitPattern = "(?:px|em|rem|%|vh|vw|pt|pc|in|cm|mm|ex|ch|vmin|vmax|0)"

  private def optimizeBoxModelProperties(cssIn: String, property: String): String = {
    var css     = cssIn
    val propEsc = Regex.quote(property)
    val value   = s"([+-]?\\d*\\.?\\d+$UnitPattern)"

    // Backreferences (\1, \2) are not portable to re2 (Native) / JS, so we capture
    // each value as a separate group and verify the equalities the backreferences
    // encoded (g1==g2==g3==g4, etc.) in Scala code.

    // Four identical values: margin: 10px 10px 10px 10px → margin: 10px  (enhanced.rb:385)
    val fourValues = new Regex(s"(?i)$propEsc:\\s*$value\\s+$value\\s+$value\\s+$value")
    css = fourValues.replaceAllIn(
      css,
      m => {
        val v1 = m.group(1)
        val v2 = m.group(2)
        val v3 = m.group(3)
        val v4 = m.group(4)
        if (v1 == v2 && v2 == v3 && v3 == v4) Regex.quoteReplacement(s"$property:$v1")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // Vertical/horizontal pairs: margin: 10px 20px 10px 20px → margin: 10px 20px  (enhanced.rb:388)
    val vhPairs = new Regex(s"(?i)$propEsc:\\s*$value\\s+$value\\s+$value\\s+$value")
    css = vhPairs.replaceAllIn(
      css,
      m => {
        val v1 = m.group(1)
        val v2 = m.group(2)
        val v3 = m.group(3)
        val v4 = m.group(4)
        if (v1 == v3 && v2 == v4) Regex.quoteReplacement(s"$property:$v1 $v2")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // Three values where first and third are same: margin: 10px 20px 10px → margin: 10px 20px  (enhanced.rb:391)
    val threeValues = new Regex(s"(?i)$propEsc:\\s*$value\\s+$value\\s+$value")
    css = threeValues.replaceAllIn(
      css,
      m => {
        val v1 = m.group(1)
        val v2 = m.group(2)
        val v3 = m.group(3)
        if (v1 == v3) Regex.quoteReplacement(s"$property:$v1 $v2")
        else Regex.quoteReplacement(m.matched)
      }
    )

    css
  }

  private def optimizeBackgroundProperties(cssIn: String): String = {
    var css = cssIn
    // background: none repeat scroll 0 0 color → background: color
    css = BackgroundNoneRepeat.replaceAllIn(css, m => Regex.quoteReplacement(s"background:${m.group(1)}"))

    // background-position: 0 center → background-position: 0
    css = BackgroundPositionCenter.replaceAllIn(css, _ => "background-position:0")

    // background-repeat: repeat repeat → background-repeat: repeat
    css = BackgroundRepeatRepeat.replaceAllIn(css, _ => "background-repeat:repeat")

    css
  }

  private def optimizeBorderProperties(cssIn: String): String = {
    var css = cssIn
    // Backreferences (\1, \2) PARTICIPATE IN MATCH SELECTION here: the trailing
    // `border-*:\s*\N` forces the engine to backtrack so that the captured token (\1
    // for width, \2 for style/color) binds to the value that ALSO equals the trailing
    // longhand value. We cannot use a free re-capture (re2/JS forbid backrefs anyway),
    // because without the backref constraint the lazy g1/g3 bind g2 to the FIRST style/
    // color token rather than the one matching the longhand. Instead we capture the full
    // border shorthand value plus the longhand value, then emulate the backref-bound
    // binding in Scala: locate the token the way Ruby's lazy quantifiers would (earliest
    // qualifying token, since g1 is lazy ⇒ as short as possible) and, when it matches the
    // longhand value, drop the redundant longhand declaration (reconstruction
    // `border:#{$1} #{$2}#{$3}` always equals the original shorthand value).
    //
    // Ruby applies the `/i` flag to the WHOLE pattern, which makes the backref \N match
    // case-INSENSITIVELY (verified against the CSSminify2 oracle: `1PX`/`1px`,
    // `Solid`/`solid`, `Red`/`red` all collapse). So the token comparison is
    // case-insensitive for all three.

    // Remove redundant border-width when already specified in border shorthand  (enhanced.rb:411)
    // border: (\d+\w*) ([^;}]+); border-width: \1
    // Group1 is the FIRST `\d+\w*` token (pinned before `\s+`); \1 binds it to border-width.
    val borderWidth = new Regex("(?i)border:\\s*(\\d+\\w*)\\s+([^;}]+);\\s*border-width:\\s*(\\d+\\w*)")
    css = borderWidth.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        val bw = m.group(3)
        if (g1.equalsIgnoreCase(bw)) Regex.quoteReplacement(s"border:$g1 $g2")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // Remove redundant border-style when already specified  (enhanced.rb:414)
    // border: ([^;}]*?) (solid|dashed|dotted|double)([^;}]*?); border-style: \2
    // Capture leading whitespace INTO the shorthand group (Ruby's `border:\s*([^;}]*?)`
    // can leave the post-colon space for the `\s+` before group2, so a token immediately
    // after `border:` still counts as whitespace-preceded). Reconstruction `border:#{$1}`
    // reproduces the original value verbatim.
    val borderStyle = new Regex("(?i)border:(\\s*[^;}]+);\\s*border-style:\\s*(solid|dashed|dotted|double)")
    css = borderStyle.replaceAllIn(
      css,
      m => {
        val shorthand = m.group(1)
        val bs        = m.group(2)
        // Emulate \2: bind group2 to the earliest whitespace-preceded style token that
        // equals (case-insensitively) the border-style value; if found, the redundant
        // declaration collapses (reconstruction equals the original shorthand).
        if (borderTokenBackrefMatches(shorthand, bs)) Regex.quoteReplacement(s"border:$shorthand")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // Remove redundant border-color when already specified  (enhanced.rb:417)
    // border: ([^;}]*?) (#[0-9a-f]{3,6}|[a-z]+)([^;}]*?); border-color: \2
    val borderColor = new Regex("(?i)border:(\\s*[^;}]+);\\s*border-color:\\s*(#[0-9a-f]{3,6}|[a-z]+)")
    css = borderColor.replaceAllIn(
      css,
      m => {
        val shorthand = m.group(1)
        val bc        = m.group(2)
        // Emulate \2: bind group2 to the earliest whitespace-preceded color token that
        // equals (case-insensitively) the border-color value; if found, the redundant
        // declaration collapses (reconstruction equals the original shorthand).
        if (borderTokenBackrefMatches(shorthand, bc)) Regex.quoteReplacement(s"border:$shorthand")
        else Regex.quoteReplacement(m.matched)
      }
    )

    css
  }

  // Emulate Ruby's backref-constrained binding for border-style/border-color:
  //   border:\s*([^;}]*?)\s+(<tokenPat>)([^;}]*?);\s*border-(style|color):\s*\2
  // Ruby's lazy g1/g3 make group2 bind to the EARLIEST WHITESPACE-PRECEDED occurrence of
  // a <tokenPat> run that equals the longhand value (the trailing \2 forces the equality;
  // group2 need not be a full whitespace-delimited token — for color `[a-z]+` it may be a
  // prefix, with g3 absorbing the remainder, e.g. ` darkred` binds g2=`dark` when the
  // longhand is `dark`). The reconstruction `border:#{$1} #{$2}#{$3}` always equals the
  // original shorthand value, so a successful binding simply drops the redundant longhand.
  // We therefore test: does `longhand` (which already matches <tokenPat>, having been
  // captured by it) occur in the shorthand immediately preceded by whitespace? The `/i`
  // flag makes \2 case-INSENSITIVE, so the occurrence test is case-insensitive too.
  private def borderTokenBackrefMatches(shorthand: String, longhand: String): Boolean = {
    // `longhand` was captured by the calling regex's <tokenPat>, so it already matches the
    // token shape; we only need a whitespace-preceded occurrence of it within the shorthand.
    val n     = longhand.length
    val sh    = shorthand
    var i     = 1
    var found = false
    while (i + n <= sh.length && !found) {
      if (sh.charAt(i - 1).isWhitespace && sh.regionMatches(true, i, longhand, 0, n)) found = true
      i += 1
    }
    found
  }

  private def optimizeFontProperties(cssIn: String): String = {
    var css = cssIn
    // Font weight optimizations
    css = FontWeightNormal.replaceAllIn(css, _ => "font-weight:400")
    css = FontWeightBold.replaceAllIn(css, _ => "font-weight:700")

    // Font style optimizations
    css = FontStyleNormal.replaceAllIn(css, _ => "font-style:normal") // Normalize case

    css
  }

  private def optimizeListProperties(cssIn: String): String = {
    var css = cssIn
    // list-style: none inside → list-style: none (inside is default for none)
    css = ListStyleNoneInside.replaceAllIn(css, _ => "list-style:none")

    css
  }

  // ---------------------------------------------------------------------------
  // enhance_zero_value_optimization (enhanced.rb:440)
  // ---------------------------------------------------------------------------

  private def enhanceZeroValueOptimization(cssIn: String): String = {
    // Advanced zero value and unit optimizations beyond basic YUI compressor
    val originalLength = cssIn.length

    var css = cssIn
    // Remove unnecessary zeros in decimal values
    css = optimizeDecimalZeros(css)

    // Optimize calc() expressions with zeros
    css = optimizeCalcZeros(css)

    // Advanced unit optimizations
    css = optimizeModernUnits(css)

    // Transform property optimizations
    css = optimizeTransformZeros(css)

    // Advanced background position optimizations
    css = optimizePositionZeros(css)

    // Box-shadow and text-shadow optimizations
    css = optimizeShadowZeros(css)

    // Update statistics
    val charsSaved = originalLength - css.length
    statistics.propertiesOptimized += (charsSaved / 3).toInt // Rough estimate

    css
  }

  private def optimizeDecimalZeros(cssIn: String): String = {
    var css = cssIn
    // Ruby uses negative lookahead (?!\d) which re2/JS handle differently; we capture
    // the trailing non-digit boundary char (group 2 / group 3) and re-emit it so we
    // do not consume real content. (\D|$) is empty at end-of-input, so the appended
    // boundary char is "" there.
    css = DecimalTrailingZerosInt.replaceAllIn(css, m => Regex.quoteReplacement(s"${m.group(1)}${m.group(2)}")) // 1.0 → 1
    css = LeadingZerosDecimal.replaceAllIn(css, m => Regex.quoteReplacement(s".${m.group(1)}")) // 0.5 → .5
    css = DecimalTrailingZerosFrac.replaceAllIn( // 1.500 → 1.5
      css,
      m => {
        val out = s"${m.group(1)}.${m.group(2)}".replaceAll("\\.$", "")
        Regex.quoteReplacement(s"$out${m.group(3)}")
      }
    )
    css
  }

  private def optimizeCalcZeros(cssIn: String): String =
    // Optimize calc() expressions with zeros
    CalcExpr.replaceAllIn(
      cssIn,
      m => {
        var calcExpr = m.matched
        // Simplify addition/subtraction with zero
        calcExpr = calcExpr.replaceAll("\\+\\s*0\\w*", "") // + 0px → nothing
        calcExpr = calcExpr.replaceAll("0\\w*\\s*\\+", "") // 0px + → nothing
        calcExpr = calcExpr.replaceAll("-\\s*0\\w*", "") // - 0px → nothing
        calcExpr = calcExpr.replaceAll("\\*\\s*1(?:\\.\\d*)?", "") // * 1 → nothing
        calcExpr = calcExpr.replaceAll("1(?:\\.\\d*)?\\s*\\*", "") // 1 * → nothing

        // Clean up extra spaces
        Regex.quoteReplacement(calcExpr.replaceAll("\\s+", " ").trim)
      }
    )

  private def optimizeModernUnits(cssIn: String): String =
    // Optimize newer CSS units where possible
    // 0(ch|rem|em|vw|vh|vmin|vmax|fr) not followed by a word char → 0
    ModernZeroUnit.replaceAllIn(
      cssIn,
      m => {
        // group(1) is the trailing non-word boundary char captured in place of the
        // negative lookahead (?!\w); re-emit it. Empty at end-of-input.
        val after = m.group(1)
        Regex.quoteReplacement("0" + after)
      }
    )

  private def optimizeTransformZeros(cssIn: String): String = {
    var css = cssIn
    // Transform property optimizations
    css = css.replaceAll("translate\\(0,\\s*0\\)", "translate(0)") // translate(0, 0) → translate(0)
    css = css.replaceAll("translate3d\\(0,\\s*0,\\s*0\\)", "translate3d(0)") // translate3d(0,0,0) → translate3d(0)
    css = css.replaceAll("scale\\(1,\\s*1\\)", "scale(1)") // scale(1, 1) → scale(1)
    css = css.replaceAll("rotate\\(0(?:deg|rad|turn)?\\)", "") // rotate(0deg) → remove
    css = css.replaceAll("skew\\(0,\\s*0\\)", "") // skew(0, 0) → remove

    css
  }

  private def optimizePositionZeros(cssIn: String): String = {
    var css = cssIn
    // Advanced background-position and object-position optimizations
    css = css.replaceAll("background-position:\\s*0\\s+0", "background-position:0")
    css = css.replaceAll("object-position:\\s*0\\s+0", "object-position:0")
    css = css.replaceAll("transform-origin:\\s*0\\s+0", "transform-origin:0")

    css
  }

  private def optimizeShadowZeros(cssIn: String): String = {
    var css = cssIn
    // Optimize box-shadow and text-shadow with zeros
    css = ShadowZeroThree.replaceAllIn(css, m => Regex.quoteReplacement(s"${m.group(1)}:0 0 ${m.group(2)}"))
    css = ShadowZeroTwo.replaceAllIn(css, m => Regex.quoteReplacement(s"${m.group(1)}:0 ${m.group(2)}"))
    css = ShadowZeroAll.replaceAllIn(css, m => Regex.quoteReplacement(s"${m.group(1)}:0${m.group(2)}"))

    css
  }

  // ---------------------------------------------------------------------------
  // optimize_modern_layout_properties (enhanced.rb:532)
  // ---------------------------------------------------------------------------

  private def optimizeModernLayoutProperties(cssIn: String): String = {
    // Advanced CSS Grid and Flexbox optimizations
    val originalLength = cssIn.length

    var css = cssIn
    css = optimizeFlexboxProperties(css)
    css = optimizeGridProperties(css)
    css = optimizeAlignmentProperties(css)
    css = optimizeGapProperties(css)

    // Update statistics
    val charsSaved = originalLength - css.length
    statistics.propertiesOptimized += (charsSaved / 4).toInt // Rough estimate

    css
  }

  private def optimizeFlexboxProperties(cssIn: String): String = {
    var css = cssIn
    // Flex shorthand optimizations
    css = css.replaceAll("(?i)flex:\\s*1\\s+1\\s+auto", "flex:1") // flex: 1 1 auto → flex: 1
    css = css.replaceAll("(?i)flex:\\s*0\\s+0\\s+auto", "flex:none") // flex: 0 0 auto → flex: none
    css = css.replaceAll("(?i)flex:\\s*0\\s+1\\s+auto", "flex:auto") // flex: 0 1 auto → flex: auto

    // flex: (\d+) \1 0 → flex: \1  (enhanced.rb:553, backref → compare in code)
    val flexNN0 = new Regex("(?i)flex:\\s*(\\d+)\\s+(\\d+)\\s+0")
    css = flexNN0.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        if (g1 == g2) Regex.quoteReplacement(s"flex:$g1")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // Flex-direction optimizations
    css = css.replaceAll("(?i)flex-direction:\\s*row", "flex-direction:row") // Normalize case

    // Justify-content optimizations (use shorter values when supported)
    css = css.replaceAll("(?i)justify-content:\\s*flex-start", "justify-content:start")
    css = css.replaceAll("(?i)justify-content:\\s*flex-end", "justify-content:end")
    css = css.replaceAll("(?i)align-items:\\s*flex-start", "align-items:start")
    css = css.replaceAll("(?i)align-items:\\s*flex-end", "align-items:end")
    css = css.replaceAll("(?i)align-self:\\s*flex-start", "align-self:start")
    css = css.replaceAll("(?i)align-self:\\s*flex-end", "align-self:end")

    css
  }

  private def optimizeGridProperties(cssIn: String): String = {
    var css = cssIn
    // Grid shorthand optimizations
    css = GridTemplateColumnsRepeat.replaceAllIn(css, m => Regex.quoteReplacement(s"grid-template-columns:repeat(${m.group(1)},1fr)"))

    // Grid-area optimizations
    css = GridArea.replaceAllIn(
      css,
      m => {
        val rowStart = m.group(1)
        val colStart = m.group(2)
        val rowEnd   = m.group(3)
        val colEnd   = m.group(4)

        // Optimize common patterns
        // NOTE: faithful to enhanced.rb:578 —
        //   if row_start == row_end.to_i - 1 && col_start == col_end.to_i - 1
        // Ruby compares the String row_start (e.g. "1") against the Integer
        // (row_end.to_i - 1). A String is never == to an Integer in Ruby, so this
        // condition is always false and the single-cell collapse never fires.
        // Preserved exactly: compare the captured String against the computed
        // Integer (cross-type, always false), so only the else branch runs.
        val rowSingle = (rowStart: Any) == (rowEnd.toInt - 1)
        val colSingle = (colStart: Any) == (colEnd.toInt - 1)
        if (rowSingle && colSingle) {
          // Single cell: grid-area: 1 / 1 / 2 / 2 → grid-area: 1 / 1
          Regex.quoteReplacement(s"grid-area:$rowStart/$colStart")
        } else {
          Regex.quoteReplacement(s"grid-area:$rowStart/$colStart/$rowEnd/$colEnd")
        }
      }
    )

    // Grid-template optimizations
    css = css.replaceAll("(?i)grid-template:\\s*none\\s*/\\s*none", "grid-template:none")

    // Grid-auto-flow optimizations
    css = css.replaceAll("(?i)grid-auto-flow:\\s*row", "grid-auto-flow:row") // Default, can sometimes be omitted

    css
  }

  private def optimizeAlignmentProperties(cssIn: String): String = {
    var css = cssIn
    // Place-items and place-content shortcuts
    // Backreferences (\1) → capture in code, but here \1 PARTICIPATES IN MATCH
    // SELECTION: in Ruby `...justify-items:\s*\1`, the \1 consumes EXACTLY group1's
    // text and the regex then ENDS — the rest of the justify value (and any trailing
    // `}`) is LEFT UNCONSUMED. group1 is pinned before the `;` and cannot backtrack,
    // so it is always the full greedy align value. We emulate \1 as a PREFIX match of
    // the justify value (group2): collapse when group2 startsWith group1, emit
    // `place-*:group1` followed by the leftover tail `group2.drop(group1.length)` so the
    // unconsumed remainder (including the closing brace) is preserved exactly as Ruby
    // leaves it. group2 is bounded by `[^;}]+` so it stops at `;` or `}` just like the
    // text Ruby's `\1` could consume before the pattern ends. Under Ruby's `/i` flag the
    // backref \1 is CASE-INSENSITIVE, so the prefix test is case-insensitive too; the
    // emitted prefix is group1 (original case, as Ruby's #{$1}) and the leftover tail is
    // taken from group2's original text after the prefix length.

    // align-items: (X); justify-items: \1 → place-items: \1  (enhanced.rb:597)
    val placeItems = new Regex("(?i)align-items:\\s*([^;]+);\\s*justify-items:\\s*([^;}]+)")
    css = placeItems.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        if (g2.regionMatches(true, 0, g1, 0, g1.length)) Regex.quoteReplacement(s"place-items:$g1${g2.drop(g1.length)}")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // align-content: (X); justify-content: \1 → place-content: \1  (enhanced.rb:598)
    val placeContent = new Regex("(?i)align-content:\\s*([^;]+);\\s*justify-content:\\s*([^;}]+)")
    css = placeContent.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        if (g2.regionMatches(true, 0, g1, 0, g1.length)) Regex.quoteReplacement(s"place-content:$g1${g2.drop(g1.length)}")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // align-self: (X); justify-self: \1 → place-self: \1  (enhanced.rb:599)
    val placeSelf = new Regex("(?i)align-self:\\s*([^;]+);\\s*justify-self:\\s*([^;}]+)")
    css = placeSelf.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        if (g2.regionMatches(true, 0, g1, 0, g1.length)) Regex.quoteReplacement(s"place-self:$g1${g2.drop(g1.length)}")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // Center shorthand
    css = css.replaceAll("(?i)place-items:\\s*center\\s+center", "place-items:center")
    css = css.replaceAll("(?i)place-content:\\s*center\\s+center", "place-content:center")

    css
  }

  private def optimizeGapProperties(cssIn: String): String = {
    var css = cssIn
    // Gap property optimizations
    // Backreferences (\1) → capture twice and compare in code.

    // grid-gap: (10px) \1 → grid-gap: 10px  (enhanced.rb:610)
    val gridGapPair = new Regex("(?i)grid-gap:\\s*(\\d+\\w*)\\s+(\\d+\\w*)")
    css = gridGapPair.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        if (g1 == g2) Regex.quoteReplacement(s"grid-gap:$g1")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // gap: (10px) \1 → gap: 10px  (enhanced.rb:611)
    val gapPair = new Regex("(?i)gap:\\s*(\\d+\\w*)\\s+(\\d+\\w*)")
    css = gapPair.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        if (g1 == g2) Regex.quoteReplacement(s"gap:$g1")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // row-gap: (10px); column-gap: \1 → gap: 10px  (enhanced.rb:612)
    val rowColGap = new Regex("(?i)row-gap:\\s*(\\d+\\w*);\\s*column-gap:\\s*(\\d+\\w*)")
    css = rowColGap.replaceAllIn(
      css,
      m => {
        val g1 = m.group(1)
        val g2 = m.group(2)
        if (g1 == g2) Regex.quoteReplacement(s"gap:$g1")
        else Regex.quoteReplacement(m.matched)
      }
    )

    // Use gap instead of grid-gap (modern syntax)
    css = css.replaceAll("(?i)grid-gap:", "gap:") // grid-gap → gap (shorter and modern)
    css = css.replaceAll("(?i)grid-row-gap:", "row-gap:") // grid-row-gap → row-gap
    css = css.replaceAll("(?i)grid-column-gap:", "column-gap:") // grid-column-gap → column-gap

    css
  }

  // ---------------------------------------------------------------------------
  // compress_css_variables (enhanced.rb:622)
  // ---------------------------------------------------------------------------

  // Mutable per-variable analysis record mirroring the Ruby Hash.
  final private class VariableInfo(var value: String) {
    var declarations:     Int              = 0
    var usages:           Int              = 0
    var totalValueLength: Int              = 0
    var fallback:         Nullable[String] = Nullable.empty
  }

  private def compressCssVariables(cssIn: String): String = {
    // Advanced CSS custom property optimization
    val originalLength = cssIn.length

    var css = cssIn
    // Parse variable declarations and usage
    val variableData = analyzeCssVariables(css)

    // Apply optimizations based on analysis
    css = inlineSingleUseVariables(css, variableData)
    css = removeUnusedVariables(css, variableData)
    css = optimizeVariableNames(css, variableData)

    // Update statistics
    val charsSaved = originalLength - css.length
    statistics.propertiesOptimized += (charsSaved / 5).toInt // Rough estimate

    css
  }

  private def analyzeCssVariables(css: String): mutable.LinkedHashMap[String, VariableInfo] = {
    val variables = mutable.LinkedHashMap[String, VariableInfo]()

    // Find all variable declarations with their values
    VarDeclaration.findAllMatchIn(css).foreach { m =>
      val varName  = m.group(1)
      val varValue = m.group(2)
      val info     = variables.getOrElseUpdate(varName, new VariableInfo(varValue.trim))
      info.declarations += 1
      info.totalValueLength += varValue.length
    }

    // Count variable usages
    VarUsage.findAllMatchIn(css).foreach { m =>
      val varName  = m.group(1)
      val fallback = Nullable(m.group(2)) // group(2) is null when the optional fallback is absent
      variables.get(varName).foreach { info =>
        info.usages += 1
        info.fallback = fallback.map(_.trim)
      }
    }

    variables
  }

  private def inlineSingleUseVariables(cssIn: String, variableData: mutable.LinkedHashMap[String, VariableInfo]): String = {
    var css = cssIn
    // Inline variables that are used only once or twice and have short values
    val variablesToInline = variableData.filter { case (_, data) =>
      data.usages <= 2 &&
      data.declarations == 1 &&
      data.value.length <= 20 && // Only inline short values
      !data.value.contains("calc(") && // Don't inline complex calc expressions
      !data.value.contains("var(") // Don't inline variables that reference other variables
    }

    variablesToInline.foreach { case (varName, data) =>
      val value = data.value

      // Replace var() usages with the actual value
      // var(<escaped-var-name>(?:,[^)]*)?) → value
      val varUse = new Regex(s"var\\(${Regex.quote(varName)}(?:,[^)]*)?\\)")
      css = varUse.replaceAllIn(css, _ => Regex.quoteReplacement(value))

      // Remove the variable declaration
      // <escaped-var-name>:\s*<escaped-value>;?  → ''
      val varDecl = new Regex(s"${Regex.quote(varName)}:\\s*${Regex.quote(value)};?")
      css = varDecl.replaceAllIn(css, _ => "")
    }

    css
  }

  private def removeUnusedVariables(cssIn: String, variableData: mutable.LinkedHashMap[String, VariableInfo]): String = {
    var css = cssIn
    // Remove variables that are declared but never used
    val unusedVariables = variableData.filter { case (_, data) => data.usages == 0 }

    unusedVariables.foreach { case (varName, _) =>
      // Remove unused variable declarations
      // <escaped-var-name>:\s*[^;]+;?  → ''
      val varDecl = new Regex(s"${Regex.quote(varName)}:\\s*[^;]+;?")
      css = varDecl.replaceAllIn(css, _ => "")
    }

    css
  }

  private def optimizeVariableNames(cssIn: String, variableData: mutable.LinkedHashMap[String, VariableInfo]): String = {
    var css = cssIn
    // For frequently used variables with long names, consider shorter aliases
    // This is more conservative - only optimize very long names that are used frequently

    val frequentLongVariables = variableData.filter { case (varName, data) =>
      data.usages >= 3 && varName.length > 15
    }.toList

    frequentLongVariables.zipWithIndex.foreach { case ((varName, _), index) =>
      boundary {
        // Create a shorter name (be careful not to conflict with existing names)
        val shortName = s"--v${index + 1}"

        // Make sure the short name doesn't already exist
        if (css.contains(shortName)) break()

        // Replace all occurrences of the long variable name
        css = css.replace(varName, shortName)
      }
    }

    css
  }

  // ---------------------------------------------------------------------------
  // advanced_color_optimization (enhanced.rb:724)
  // ---------------------------------------------------------------------------

  private def advancedColorOptimization(cssIn: String): String = {
    var css = cssIn
    // More aggressive color optimization beyond basic YUI compressor
    val colorCountBefore = ColorCount.findAllMatchIn(css).size

    // Add HSL to RGB conversion
    css = HslColor.replaceAllIn(
      css,
      m => {
        val h   = m.group(1).toInt
        val s   = m.group(2).toInt / 100.0
        val l   = m.group(3).toInt / 100.0
        val rgb = hslToRgb(h, s, l)
        Regex.quoteReplacement(s"rgb(${rgb.mkString(",")})")
      }
    )

    val colorCountAfter = ColorCount.findAllMatchIn(css).size
    statistics.colorsConverted += colorCountBefore - colorCountAfter

    css
  }

  private def hslToRgb(hIn: Int, s: Double, l: Double): List[Int] = {
    val h = hIn / 360.0

    val (r, g, b) =
      if (s == 0) {
        (l, l, l) // achromatic
      } else {
        def hue2rgb(p: Double, q: Double, tIn: Double): Double =
          boundary[Double] {
            var t = tIn
            if (t < 0) t += 1
            if (t > 1) t -= 1
            if (t < 1.0 / 6) break(p + (q - p) * 6 * t)
            if (t < 1.0 / 2) break(q)
            if (t < 2.0 / 3) break(p + (q - p) * (2.0 / 3 - t) * 6)
            p
          }

        val q  = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p  = 2 * l - q
        val rr = hue2rgb(p, q, h + 1.0 / 3)
        val gg = hue2rgb(p, q, h)
        val bb = hue2rgb(p, q, h - 1.0 / 3)
        (rr, gg, bb)
      }

    List(rubyRound(r * 255), rubyRound(g * 255), rubyRound(b * 255))
  }

  // Ruby Float#round uses round-half-up (away from zero for .5); for the
  // non-negative channel values here that is round-half-up.
  private def rubyRound(d: Double): Int =
    Math.floor(d + 0.5).toInt

  private def calculateCompressionRatio: Double =
    if (statistics.originalSize == 0) {
      0.0
    } else {
      (statistics.originalSize - statistics.compressedSize).toDouble / statistics.originalSize * 100
    }

  // Ruby `warn` writes to stderr; preserved as a hook (no-op aside from the
  // graceful-degradation semantics, matching the source which only logs).
  private def warn(message: String): Unit = {
    val _ = message
    ()
  }
}

object CssEnhancedCompressor {

  // -- Regex constants (shared, compiled once) --

  // validate_css_structure
  private val NestedBraces     = "\\{[^{}]*\\{".r
  private val AtRuleNestable   = "@(?:media|supports|keyframes|container)".r
  private val MissingSemicolon = "[^;{}]\\s*\\}".r
  private val AtSign           = "@".r

  // merge_duplicate_selectors
  // @media[^{]*\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}  (DOTALL via (?s))
  private val MediaBlock        = "(?s)@media[^{]*\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}".r
  private val MediaQueryContent = "(?s)@media([^{]*)\\{(.*)\\}".r
  private val RuleMatch         = "(?s)([^{}]+)\\{([^{}]*)\\}".r
  private val AtRuleSkip        = "^@(?:keyframes|font-face|page|supports|document)".r
  private val WhitespaceRun     = "\\s+".r
  private val DeclarationMatch  = "^\\s*([^:]+):\\s*(.+)\\s*$".r

  // optimize_background_properties
  private val BackgroundNoneRepeat     = "(?i)background:\\s*none\\s+repeat\\s+scroll\\s+0\\s+0\\s+([^;}]+)".r
  private val BackgroundPositionCenter = "(?i)background-position:\\s*0\\s+(?:center|50%)".r
  private val BackgroundRepeatRepeat   = "(?i)background-repeat:\\s*repeat\\s+repeat".r

  // optimize_font_properties
  private val FontWeightNormal = "(?i)font-weight:\\s*normal".r
  private val FontWeightBold   = "(?i)font-weight:\\s*bold".r
  private val FontStyleNormal  = "(?i)font-style:\\s*normal".r

  // optimize_list_properties
  private val ListStyleNoneInside = "(?i)list-style:\\s*none\\s+inside".r

  // optimize_decimal_zeros
  // (\d)\.0+(?!\d) — trailing-zeros-after-int with negative lookahead.
  // Lookahead is unsupported on re2; match a trailing non-digit (or end) and
  // re-emit it instead. We capture the leading digit and the following char.
  private val DecimalTrailingZerosInt = "(\\d)\\.0+(\\D|$)".r
  private val LeadingZerosDecimal     = "0+\\.(\\d+)".r
  // (\d+)\.0*(\d*?)0+(?!\d) — see optimizeDecimalZerosFrac note.
  private val DecimalTrailingZerosFrac = "(\\d+)\\.0*(\\d*?)0+(\\D|$)".r

  // optimize_calc_zeros — calc\([^)]*\)
  private val CalcExpr = "calc\\([^)]*\\)".r

  // optimize_modern_units
  // 0(?:ch|rem|em|vw|vh|vmin|vmax|fr)(?!\w) → 0 ; lookahead → trailing capture.
  private val ModernZeroUnit = "0(?:ch|rem|em|vw|vh|vmin|vmax|fr)(\\W|$)".r

  // optimize_shadow_zeros
  private val ShadowZeroThree = "(box-shadow|text-shadow):\\s*0\\s+0\\s+0\\s+([^;,}]+)".r
  private val ShadowZeroTwo   = "(box-shadow|text-shadow):\\s*0\\s+0\\s+([^;,}]+)".r
  private val ShadowZeroAll   = "(box-shadow|text-shadow):\\s*0\\s+0\\s+0\\s*(;|\\})".r

  // optimize_grid_properties
  private val GridTemplateColumnsRepeat = "(?i)grid-template-columns:\\s*repeat\\((\\d+),\\s*1fr\\)".r
  private val GridArea                  = "(?i)grid-area:\\s*(\\d+)\\s*/\\s*(\\d+)\\s*/\\s*(\\d+)\\s*/\\s*(\\d+)".r

  // compress_css_variables
  private val VarDeclaration = "(--[\\w-]+):\\s*([^;]+)".r
  private val VarUsage       = "var\\((--[\\w-]+)(?:,([^)]*))?\\)".r

  // advanced_color_optimization
  private val ColorCount = "(?i)#[0-9a-f]{3,6}|rgb\\([^)]+\\)".r
  private val HslColor   = "(?i)hsl\\(\\s*(\\d+)\\s*,\\s*(\\d+)%\\s*,\\s*(\\d+)%\\s*\\)".r

  // Convenience class methods for enhanced compression (enhanced.rb:773)
  def compress(css: String, options: Map[String, Any]): String =
    if (options.nonEmpty) {
      val config       = configFromOptions(options)
      val linebreakpos = options.get("linebreakpos") match {
        case Some(i: Int) => i
        case _            => 5000
      }
      new CssEnhancedCompressor(config).compress(css, linebreakpos)
    } else {
      // Fallback to original API for backward compatibility
      // (options is empty here; the Integer-options Ruby branch is handled by the Int overload below)
      CssMinifier.minify(css)
    }

  def compress(css: String): String =
    compress(css, Map.empty[String, Any])

  // Ruby allows compress(css, <Integer linebreakpos>) for backward compatibility.
  def compress(css: String, linebreakpos: Int): String =
    // Fallback to original API for backward compatibility
    CssMinifier.minify(css)

  // Convenience entry taking a fully-built config (used by the jekyll wrapper path).
  def compress(css: String, config: CssEnhancedConfig): String =
    new CssEnhancedCompressor(config).compress(css)

  def compressWithStats(css: String, options: Map[String, Any]): CssCompressionResult = {
    val config = configFromOptions(options)
    config.statisticsEnabled = true

    val compressor   = new CssEnhancedCompressor(config)
    val linebreakpos = options.get("linebreakpos") match {
      case Some(i: Int) => i
      case _            => 5000
    }
    val result = compressor.compress(css, linebreakpos)

    CssCompressionResult(compressedCss = result, statistics = compressor.statistics)
  }

  def compressWithStats(css: String): CssCompressionResult =
    compressWithStats(css, Map.empty[String, Any])

  // Builds a Configuration from an options map, mirroring enhanced.rb:776 —
  // `options.each { |key, value| config.send("#{key}=", value) if config.respond_to?("#{key}=") }`.
  private def configFromOptions(options: Map[String, Any]): CssEnhancedConfig = {
    val config = new CssEnhancedConfig()
    options.foreach { case (key, value) =>
      (key, value) match {
        case ("merge_duplicate_selectors", b: Boolean)     => config.mergeDuplicateSelectors = b
        case ("optimize_shorthand_properties", b: Boolean) => config.optimizeShorthandProperties = b
        case ("advanced_color_optimization", b: Boolean)   => config.advancedColorOptimization = b
        case ("preserve_ie_hacks", b: Boolean)             => config.preserveIeHacks = b
        case ("compress_css_variables", b: Boolean)        => config.compressCssVariables = b
        case ("strict_error_handling", b: Boolean)         => config.strictErrorHandling = b
        case ("generate_source_map", b: Boolean)           => config.generateSourceMap = b
        case ("statistics_enabled", b: Boolean)            => config.statisticsEnabled = b
        case _                                             => () // respond_to? false → ignored
      }
    }
    config
  }
}

// Result of compress_with_stats (enhanced.rb:793) — {compressed_css, statistics}.
final case class CssCompressionResult(
  compressedCss: String,
  statistics:    CssEnhancedStatistics
)
