/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Stands where JUnit 4's `org.junit.rules.ExpectedException` and `org.junit.Rule` stood in flexmark's
 * suites (ssg-md/port/test.conf redirects both types here): neither exists in the JUnit runtimes
 * of Scala.js and Scala Native.
 *
 * MUnit applies no rules, so this object is never consulted by a runner — exactly as the JUnit one
 * was not. What a test ARMS inside its own body the engine already turns into a check around that
 * body; this type is what the `@Rule` field and an arming made outside a test body compile against.
 */
package ssg
package md
package test
package rules

final class ExpectedException private () {

  private var armed: List[Throwable => Boolean] = Nil

  def expect(throwable: Class[? <: Throwable]): Unit =
    armed = armed :+ ((t: Throwable) => throwable.isInstance(t))

  def expectMessage(substring: String): Unit =
    armed = armed :+ ((t: Throwable) => Option(t.getMessage).exists(_.contains(substring)))

  /** What JUnit's rule would have asked of a thrown exception: every armed expectation holds. */
  def isSatisfiedBy(t: Throwable): Boolean = armed.forall(_.apply(t))

  def isAnyExceptionExpected: Boolean = armed.nonEmpty
}

object ExpectedException {

  def none(): ExpectedException = new ExpectedException()
}

/** The marker `@Rule` was; it carries nothing. */
final class Rule extends scala.annotation.StaticAnnotation
