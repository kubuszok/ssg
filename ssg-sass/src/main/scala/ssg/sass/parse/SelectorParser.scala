/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/selector.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: selector.dart -> SelectorParser.scala
 *   Skeleton: parser stubs with the correct public surface.
 */
package ssg
package sass
package parse

import ssg.sass.{InterpolationMap, Nullable}
import ssg.sass.ast.selector.{
  ComplexSelector,
  CompoundSelector,
  SelectorList,
  SimpleSelector
}

/** A parser for CSS/Sass selectors. */
class SelectorParser(
  contents: String,
  url: Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null,
  allowParent: Boolean = true,
  plainCss: Boolean = false
) extends Parser(contents, url, interpolationMap) {

  protected val _allowParent: Boolean = allowParent
  protected val _plainCss: Boolean = plainCss

  def parse(): SelectorList =
    throw new UnsupportedOperationException("SelectorParser.parse: not yet implemented in skeleton")

  def parseComplexSelector(): ComplexSelector =
    throw new UnsupportedOperationException("SelectorParser.parseComplexSelector: not yet implemented in skeleton")

  def parseCompoundSelector(): CompoundSelector =
    throw new UnsupportedOperationException("SelectorParser.parseCompoundSelector: not yet implemented in skeleton")

  def parseSimpleSelector(): SimpleSelector =
    throw new UnsupportedOperationException("SelectorParser.parseSimpleSelector: not yet implemented in skeleton")
}

object SelectorParser {

  /** Pseudo-class selectors that take unadorned selectors as arguments. */
  val selectorPseudoClasses: Set[String] = Set(
    "not",
    "is",
    "matches",
    "where",
    "current",
    "any",
    "has",
    "host",
    "host-context"
  )

  /** Pseudo-element selectors that take unadorned selectors as arguments. */
  val selectorPseudoElements: Set[String] = Set("slotted")
}
