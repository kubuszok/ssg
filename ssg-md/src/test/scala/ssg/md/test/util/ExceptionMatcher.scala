/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/ExceptionMatcher.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause
 *
 * NOTE: Hamcrest BaseMatcher dependency removed. This is a standalone implementation that provides matching without the org.hamcrest dependency. */
package ssg
package md
package test
package util

import java.util.regex.Pattern

/** Matches exceptions by class prefix and message pattern. Replaces the original Hamcrest-based ExceptionMatcher.
  */
final class ExceptionMatcher private (
  private val prefix:  String,
  private val pattern: Pattern,
  private val message: String
) {

  def matches(o: Any): Boolean =
    o match {
      case re: RuntimeException =>
        if (re.toString.startsWith(prefix + ": ")) {
          pattern.matcher(re.toString.substring(prefix.length + ": ".length)).matches()
        } else {
          false
        }
      case t: Throwable =>
        if (t.toString.startsWith(prefix)) {
          val input = if (t.getCause == null) t.toString else t.getCause.toString
          pattern.matcher(input).matches()
        } else {
          false
        }
      case _ => false
    }

  def description: String = prefix + ": " + message
}

object ExceptionMatcher {

  def `match`(throwable: Class[? <: Throwable], text: String): ExceptionMatcher =
    new ExceptionMatcher(throwable.getName, Pattern.compile(Pattern.quote(text)), text)

  def matchPrefix(throwable: Class[? <: Throwable], text: String): ExceptionMatcher =
    new ExceptionMatcher(throwable.getName, Pattern.compile(Pattern.quote(text) + "(?s:.*)"), text)

  def matchRegEx(throwable: Class[? <: Throwable], regEx: String): ExceptionMatcher =
    new ExceptionMatcher(throwable.getName, Pattern.compile(regEx), regEx)
}
