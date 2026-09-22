/*
 * Ported from flexmark-java - https://github.com/vsch/flexmark-java
 * Original source: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/ExceptionMatcher.java
 * Original license: BSD-2-Clause, Copyright (c) 2015-2016 Atlassian Pty Ltd, 2016-2018 Vladimir Schneider
 *
 * Migration notes:
 *   Origin: java's class extends hamcrest's BaseMatcher, and hamcrest does not exist on Scala Native.
 *     The generated suites only ever call `matches` (the engine models JUnit's ExpectedException
 *     rule itself and asks the matcher directly), so the hamcrest parent is what is left out.
 *   Convention: `matches` and the three factories are java's, line for line. `describeTo` took a
 *     hamcrest Description and had no other caller; its text is what `toString` answers.
 */
package ssg
package md
package test
package util

import java.util.regex.Pattern

class ExceptionMatcher(throwable: Class[? <: Throwable], val pattern: Pattern, val message: String) {

  val prefix: String = throwable.getName

  def matches(o: Object): Boolean =
    o match {
      case _: RuntimeException =>
        if (o.toString.startsWith(prefix + ": ")) {
          pattern.matcher(o.toString.substring(prefix.length + ": ".length)).matches()
        } else {
          false
        }
      case t: Throwable =>
        if (o.toString.startsWith(prefix)) {
          val input = Option(t.getCause).fold(o.toString)(_.toString)
          pattern.matcher(input).matches()
        } else {
          false
        }
      case _ => false
    }

  override def toString: String = prefix + ": " + message
}

object ExceptionMatcher {

  def `match`(throwable: Class[? <: Throwable], text: String): ExceptionMatcher =
    new ExceptionMatcher(throwable, Pattern.compile(Pattern.quote(text)), text)

  def matchPrefix(throwable: Class[? <: Throwable], text: String): ExceptionMatcher =
    new ExceptionMatcher(throwable, Pattern.compile(Pattern.quote(text) + "(?s:.*)"), text)

  def matchRegEx(throwable: Class[? <: Throwable], regEx: String): ExceptionMatcher =
    new ExceptionMatcher(throwable, Pattern.compile(regEx), regEx)
}
