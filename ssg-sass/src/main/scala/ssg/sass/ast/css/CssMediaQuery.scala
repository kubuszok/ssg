/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/media_query.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: media_query.dart -> CssMediaQuery.scala
 *   Convention: Dart named constructors -> companion apply methods
 *   Idiom: Dart sealed class hierarchy -> Scala sealed trait + enum + case class
 *   Idiom: equalsIgnoreCase from Utils
 */
package ssg
package sass
package ast
package css

/** A plain CSS media query, as used in `@media` and `@import`. */
final class CssMediaQuery private (
  /** The modifier, probably either "not" or "only". May be absent. */
  val modifier: Option[String],
  /** The media type, for example "screen" or "print". May be absent. */
  val type_ : Option[String],
  /** Whether conditions is a conjunction or a disjunction.
    *
    * If true, this query matches when all conditions are met. If false, this query matches when any condition is met. If false, modifier and type_ will both be None.
    */
  val conjunction: Boolean,
  /** Media conditions, including parentheses. */
  val conditions: List[String]
) {

  /** Whether this media query matches all media types. */
  def matchesAllTypes: Boolean =
    type_.isEmpty || type_.exists(_.equalsIgnoreCase("all"))

  /** Merges this with [other] to return a query that matches the intersection of both inputs.
    */
  def merge(other: CssMediaQuery): MediaQueryMergeResult =
    if (!conjunction || !other.conjunction) {
      MediaQueryMergeResult.Unrepresentable
    } else {
      val ourModifier   = modifier.map(_.toLowerCase)
      val ourType       = type_.map(_.toLowerCase)
      val theirModifier = other.modifier.map(_.toLowerCase)
      val theirType     = other.type_.map(_.toLowerCase)

      if (ourType.isEmpty && theirType.isEmpty) {
        MediaQueryMergeResult.Success(
          CssMediaQuery.condition(
            this.conditions ++ other.conditions,
            conjunction = Some(true)
          )
        )
      } else {
        mergeWithTypes(other, ourModifier, ourType, theirModifier, theirType)
      }
    }

  private def mergeWithTypes(
    other:         CssMediaQuery,
    ourModifier:   Option[String],
    ourType:       Option[String],
    theirModifier: Option[String],
    theirType:     Option[String]
  ): MediaQueryMergeResult = {
    var resultModifier:   Option[String] = None
    var resultType:       Option[String] = None
    var resultConditions: List[String]   = Nil

    if (ourModifier.contains("not") != theirModifier.contains("not")) {
      if (ourType == theirType) {
        val negativeConditions =
          if (ourModifier.contains("not")) this.conditions else other.conditions
        val positiveConditions =
          if (ourModifier.contains("not")) other.conditions else this.conditions

        // If the negative conditions are a subset of the positive conditions, the
        // query is empty. For example, `not screen and (color)` has no
        // intersection with `screen and (color) and (grid)`.
        //
        // However, `not screen and (color)` *does* intersect with `screen and
        // (grid)`, because it means `not (screen and (color))` and so it allows
        // a screen with no color but with a grid.
        if (negativeConditions.forall(positiveConditions.contains)) {
          MediaQueryMergeResult.Empty
        } else {
          MediaQueryMergeResult.Unrepresentable
        }
      } else if (matchesAllTypes || other.matchesAllTypes) {
        MediaQueryMergeResult.Unrepresentable
      } else {
        if (ourModifier.contains("not")) {
          resultModifier = other.modifier
          resultType = other.type_
          resultConditions = other.conditions
        } else {
          resultModifier = this.modifier
          resultType = this.type_
          resultConditions = this.conditions
        }
        MediaQueryMergeResult.Success(
          CssMediaQuery.type_(
            if (resultType.map(_.toLowerCase) == ourType) this.type_ else other.type_,
            modifier =
              if (resultModifier.map(_.toLowerCase) == ourModifier) this.modifier else other.modifier,
            conditions = resultConditions
          )
        )
      }
    } else if (ourModifier.contains("not")) {
      // Both are "not"
      // CSS has no way of representing "neither screen nor print".
      if (ourType != theirType) {
        MediaQueryMergeResult.Unrepresentable
      } else {
        val moreConditions =
          if (this.conditions.length > other.conditions.length) this.conditions
          else other.conditions
        val fewerConditions =
          if (this.conditions.length > other.conditions.length) other.conditions
          else this.conditions

        // If one set of conditions is a superset of the other, use those conditions
        // because they're strictly narrower.
        if (fewerConditions.forall(moreConditions.contains)) {
          MediaQueryMergeResult.Success(
            CssMediaQuery.type_(
              this.type_,
              modifier = this.modifier,
              conditions = moreConditions
            )
          )
        } else {
          // Otherwise, there's no way to represent the intersection.
          MediaQueryMergeResult.Unrepresentable
        }
      }
    } else if (matchesAllTypes) {
      resultModifier = other.modifier
      // Omit the type if either input query did, since that indicates that they
      // aren't targeting a browser that requires "all and".
      resultType =
        if (other.matchesAllTypes && this.type_.isEmpty) None else other.type_
      resultConditions = this.conditions ++ other.conditions
      MediaQueryMergeResult.Success(
        CssMediaQuery.type_(
          resultType,
          modifier = resultModifier,
          conditions = resultConditions
        )
      )
    } else if (other.matchesAllTypes) {
      resultModifier = this.modifier
      resultType = this.type_
      resultConditions = this.conditions ++ other.conditions
      MediaQueryMergeResult.Success(
        CssMediaQuery.type_(
          resultType,
          modifier = resultModifier,
          conditions = resultConditions
        )
      )
    } else if (ourType != theirType) {
      MediaQueryMergeResult.Empty
    } else {
      resultModifier = if (ourModifier.isDefined) ourModifier else theirModifier
      resultType = ourType
      resultConditions = this.conditions ++ other.conditions
      MediaQueryMergeResult.Success(
        CssMediaQuery.type_(
          if (resultType == ourType) this.type_ else other.type_,
          modifier =
            if (resultModifier.map(_.toLowerCase) == ourModifier) this.modifier
            else other.modifier,
          conditions = resultConditions
        )
      )
    }
  }

  override def equals(other: Any): Boolean = other match {
    case that: CssMediaQuery =>
      that.modifier == modifier &&
      that.type_ == type_ &&
      that.conditions == conditions
    case _ => false
  }

  override def hashCode(): Int =
    modifier.hashCode ^ type_.hashCode ^ conditions.hashCode

  override def toString: String = {
    val sb = new StringBuilder()
    modifier.foreach(m => sb.append(s"$m "))
    type_.foreach { t =>
      sb.append(t)
      if (conditions.nonEmpty) sb.append(" and ")
    }
    sb.append(conditions.mkString(if (conjunction) " and " else " or "))
    sb.toString()
  }
}

object CssMediaQuery {

  /** Normalize a feature condition string so `(orientation:landscape)` serializes as `(orientation: landscape)`, matching dart-sass output. Leaves unrecognized shapes (nested parens, no colon) alone. */
  private[sass] def normalizeCondition(cond: String): String = {
    if (!(cond.startsWith("(") && cond.endsWith(")"))) return cond
    val inner = cond.substring(1, cond.length - 1)
    if (inner.contains('(')) return cond
    val colon = inner.indexOf(':')
    if (colon < 0) return cond
    val name  = inner.substring(0, colon).trim
    val value = inner.substring(colon + 1).trim
    if (name.isEmpty || value.isEmpty) return cond
    s"($name: $value)"
  }

  /** Normalize all parenthesized media features in a modifier string. */
  private[sass] def normalizeMediaFeatures(text: String): String = {
    if (!text.contains('(')) return text
    val sb = new StringBuilder()
    var i = 0
    while (i < text.length) {
      if (text.charAt(i) == '(') {
        var depth = 1
        var j = i + 1
        while (j < text.length && depth > 0) {
          val ch = text.charAt(j)
          if (ch == '(') depth += 1
          else if (ch == ')') depth -= 1
          j += 1
        }
        sb.append(normalizeCondition(text.substring(i, j)))
        i = j
      } else {
        sb.append(text.charAt(i))
        i += 1
      }
    }
    sb.toString()
  }

  /** Creates a media query that specifies a type and, optionally, conditions.
    *
    * This always sets conjunction to true.
    */
  def type_(
    type_     : Option[String],
    modifier:   Option[String] = None,
    conditions: List[String] = Nil
  ): CssMediaQuery =
    new CssMediaQuery(
      modifier = modifier,
      type_ = type_,
      conjunction = true,
      conditions = conditions
    )

  /** Creates a media query that matches conditions according to conjunction.
    *
    * The conjunction argument must not be None if conditions is longer than a single element.
    */
  def condition(
    conditions:  List[String],
    conjunction: Option[Boolean] = None
  ): CssMediaQuery = {
    if (conditions.length > 1 && conjunction.isEmpty) {
      throw new IllegalArgumentException(
        "If conditions is longer than one element, conjunction may not be None."
      )
    }
    new CssMediaQuery(
      modifier = None,
      type_ = None,
      conjunction = conjunction.getOrElse(true),
      conditions = conditions
    )
  }
}

/** The interface of possible return values of CssMediaQuery.merge. */
sealed trait MediaQueryMergeResult

object MediaQueryMergeResult {

  /** There are no contexts that match both input queries. */
  case object Empty extends MediaQueryMergeResult

  /** The contexts that match both input queries can't be represented by a Level 3 media query.
    */
  case object Unrepresentable extends MediaQueryMergeResult

  /** A successful merge result containing the merged query. */
  final case class Success(query: CssMediaQuery) extends MediaQueryMergeResult {
    override def toString: String = query.toString
  }
}
