/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Structured lexical/syntactic failure for the Graphviz DOT scanner and parser.
 */
package ssg
package graphviz
package parse

/** A DOT lexical or syntactic failure carrying a structured 1-based `line`/`col`.
  *
  * ssg-graphviz is SSG-native (no port-fidelity constraint on its internals), so — per docs/architecture/error-contracts.md §2.8 — the scanner and parser throw this instead of a bare
  * `IllegalArgumentException` at their failure sites. It subclasses `IllegalArgumentException` and reuses the exact same message text, so existing `catch`es and message-text assertions keyed on
  * `IllegalArgumentException` keep working; the added `line`/`col` fields let the `Graphviz.parseResult`/`renderResult` facades map the position into `ssg.commons.SourcePosition` without parsing the
  * message. `line`/`col` are 1-based (the `Token` convention, DotScanner.scala:28-29).
  *
  * This file is SSG-native (no original source).
  */
final class DotParseException(message: String, val line: Int, val col: Int) extends IllegalArgumentException(message)
