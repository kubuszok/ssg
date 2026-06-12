/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/utils.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces @braintree/sanitize-url and browser-dependent utilities
 *   Idiom: Pure functions; deterministic ID generation via AtomicInteger or seed
 *   Renames: utils.formatUrl → Utils.sanitizeUrl; N/A for dedent (from TextUtils)
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package util

import java.util.concurrent.atomic.AtomicLong

import scala.util.boundary
import scala.util.boundary.break

/** General utilities for Mermaid diagram rendering.
  *
  * Provides URL sanitization, text dedenting, and unique ID generation for SVG elements.
  */
object Utils {

  /** Counter for generating unique IDs. Thread-safe via AtomicLong. */
  private val idCounter: AtomicLong = new AtomicLong(0L)

  /** Dangerous URL protocol patterns that should be blocked. */
  private val DangerousProtocols = Set(
    "javascript:",
    "data:",
    "vbscript:"
  )

  /** Sanitizes a URL for safe use in SVG attributes.
    *
    * Blocks dangerous protocols (javascript:, data:, vbscript:) and returns the URL unchanged if it uses a safe protocol or is a relative URL.
    *
    * This is a server-side replacement for `@braintree/sanitize-url`.
    *
    * @param url
    *   the URL to sanitize
    * @return
    *   the sanitized URL, or "about:blank" if the URL is dangerous
    */
  def sanitizeUrl(url: String): String = {
    val trimmed = url.trim

    if (trimmed.isEmpty) {
      "about:blank"
    } else {
      // Decode any URL-encoded characters for protocol detection
      val decoded = decodeUrlEntities(trimmed.toLowerCase)

      // Strip whitespace and control characters that might hide protocols
      val cleaned = decoded.replaceAll("[\\s\\x00-\\x1f]", "")

      // Check for dangerous protocols
      val hasDangerousProtocol = DangerousProtocols.exists(proto => cleaned.startsWith(proto))

      if (hasDangerousProtocol) {
        "about:blank"
      } else {
        trimmed
      }
    }
  }

  /** Removes common leading whitespace from all lines.
    *
    * Delegates to [[ssg.mermaid.render.text.TextUtils.dedent]].
    *
    * @param text
    *   indented text
    * @return
    *   text with common leading whitespace removed
    */
  def dedent(text: String): String =
    ssg.mermaid.render.text.TextUtils.dedent(text)

  /** Generates a unique ID string for SVG elements.
    *
    * When `deterministicIds` is true in config, generates predictable IDs based on an incrementing counter with an optional seed prefix. Otherwise, generates IDs based on a timestamp and counter.
    *
    * @return
    *   a unique ID string suitable for use as an SVG element id attribute
    */
  def generateId(): String = {
    val counter = idCounter.getAndIncrement()
    s"id-${counter.toHexString}-${System.nanoTime().toHexString.takeRight(8)}"
  }

  /** Generates a deterministic ID using a seed string and counter.
    *
    * @param seed
    *   a prefix string for the ID
    * @return
    *   a deterministic ID string
    */
  def generateDeterministicId(seed: String): String = {
    val counter = idCounter.getAndIncrement()
    s"$seed-$counter"
  }

  /** Resets the ID counter (useful for deterministic testing). */
  def resetIdCounter(): Unit =
    idCounter.set(0L)

  /** Zero-width space character used by Mermaid for text processing. */
  val ZeroWidthSpace: String = "​"

  /** Rounds a number to the given precision (decimal places).
    *
    * Mirrors the `roundNumber` function from the original utils.ts.
    *
    * @param num
    *   the number to round
    * @param precision
    *   number of decimal places (default 2)
    * @return
    *   the rounded number
    */
  def roundNumber(num: Double, precision: Int = 2): Double = {
    val factor = math.pow(10, precision)
    math.round(num * factor).toDouble / factor
  }

  def formatNumber(value: Double): String =
    if (value == value.toLong.toDouble) {
      value.toLong.toString
    } else {
      // Round to 4 decimals, strip trailing zeros, '.' separator — delegated to
      // the shared graphs-commons formatter so the comma-locale bug is fixed in
      // one place. The previous E-notation branch routed |value| < 1e-3 or >= 1e7
      // through a locale-sensitive `f"$rounded%.4f"`; toFixedTrimmed never emits
      // E-notation and is locale-independent on all platforms (ISS-1156).
      ssg.graphs.commons.util.FormatUtil.toFixedTrimmed(value, 4)
    }

  /** Checks if a substring is present in any element of an array.
    *
    * Mirrors the `isSubstringInArray` function from the original.
    *
    * @param str
    *   the substring to detect
    * @param arr
    *   the array to search
    * @return
    *   the array index containing the substring or -1 if not present
    */
  def isSubstringInArray(str: String, arr: Array[String]): Int =
    boundary[Int] {
      var i = 0
      while (i < arr.length) {
        if (arr(i).contains(str)) {
          break(i)
        }
        i += 1
      }
      -1
    }

  /** Decodes common URL-encoded entities for protocol detection. */
  private def decodeUrlEntities(url: String): String = {
    var result = url
    result = result.replace("%3a", ":")
    result = result.replace("%2f", "/")
    result = result.replace("%20", " ")
    result = result.replace("&colon;", ":")
    result
  }
}
