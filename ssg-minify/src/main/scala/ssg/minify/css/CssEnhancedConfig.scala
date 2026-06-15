/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: cssminify2 lib/cssminify2/enhanced.rb
 * Original: Copyright (c) DigitalSparky (cssminify2 gem)
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: CSSminify2Enhanced::Configuration → ssg.minify.css.CssEnhancedConfig
 *   Convention: Ruby attr_accessor flags → public var fields on a mutable config
 *     (Configuration is mutated after construction by the convenience class
 *     methods, e.g. enhanced.rb:776 `config.send("#{key}=", value)` and the
 *     presets, so a mutable holder is the faithful shape).
 *   Idiom: `Configuration.conservative/aggressive/modern` class methods →
 *     companion-object factory methods.
 *
 * Covenant: full-port
 * Covenant-ruby-reference: lib/cssminify2/enhanced.rb
 * Covenant-verified: 2026-06-15
 */
package ssg
package minify
package css

// Configuration class for enhanced features
final class CssEnhancedConfig {
  // All new features are opt-in by default for compatibility
  var mergeDuplicateSelectors:     Boolean = false
  var optimizeShorthandProperties: Boolean = false
  var advancedColorOptimization:   Boolean = false
  var preserveIeHacks:             Boolean = true
  var compressCssVariables:        Boolean = false
  var strictErrorHandling:         Boolean = false
  var generateSourceMap:           Boolean = false
  var statisticsEnabled:           Boolean = false
}

object CssEnhancedConfig {

  // Preset configurations
  def conservative: CssEnhancedConfig =
    new CssEnhancedConfig() // All features disabled

  def aggressive: CssEnhancedConfig = {
    val config = new CssEnhancedConfig()
    config.mergeDuplicateSelectors = true
    config.optimizeShorthandProperties = true
    config.advancedColorOptimization = true
    config.compressCssVariables = true
    config
  }

  def modern: CssEnhancedConfig = {
    val config = aggressive
    config.generateSourceMap = true
    config.statisticsEnabled = true
    config
  }
}
