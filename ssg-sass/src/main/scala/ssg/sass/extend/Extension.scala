/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/extend/extension.dart, lib/src/extend/merged_extension.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: extension.dart -> Extension.scala; merged_extension.dart merged in
 *   Convention: Dart final class -> Scala final class; List<T>? -> Nullable[List[T]]
 *   Idiom: _extension mutable slot kept as var; Extender uses reference-equality
 *          default; assertCompatibleMediaContext preserved
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/extend/extension.dart, lib/src/extend/merged_extension.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package extend

import ssg.sass.{ Nullable, SassException }
import ssg.sass.Nullable.*
import ssg.sass.ast.css.CssMediaQuery
import ssg.sass.ast.selector.{ ComplexSelector, SimpleSelector }
import ssg.sass.util.FileSpan

/** The state of an extension for a given extender.
  *
  * The target of the extension is represented externally, in the map that contains this extender.
  */
class Extension(
  /** The extender (such as `A` in `A {@extend B}`). */
  val extender: Extender,
  /** The selector that's being extended. */
  val target: SimpleSelector,
  /** The span for an `@extend` rule that defined this extension.
    *
    * If any extend rule for this is extension is mandatory, this is guaranteed to be a span for a mandatory rule.
    */
  val span: FileSpan,
  /** The media query context to which this extension is restricted, or `Nullable.empty` if it can apply within any context.
    */
  val mediaContext: Nullable[List[CssMediaQuery]],
  /** Whether this extension is optional. */
  val isOptional: Boolean
) {

  def withExtender(newExtender: ComplexSelector): Extension =
    Extension(newExtender, target, span, mediaContext = mediaContext, optional = isOptional)

  override def toString: String =
    s"$extender {@extend $target${if (isOptional) " !optional" else ""}}"
}

object Extension {

  /** Creates a new extension. */
  def apply(
    extender:     ComplexSelector,
    target:       SimpleSelector,
    span:         FileSpan,
    mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty,
    optional:     Boolean = false
  ): Extension = {
    val ext       = new Extender(extender)
    val extension = new Extension(ext, target, span, mediaContext, optional)
    ext._extension = Nullable(extension)
    extension
  }
}

/** A selector that's extending another selector, such as `A` in `A {@extend B}`.
  */
final class Extender(
  /** The selector in which the `@extend` appeared. */
  val selector:   ComplexSelector,
  specificityOpt: Nullable[Int] = Nullable.empty,
  /** Whether this extender represents a selector that was originally in the document, rather than one defined with `@extend`.
    */
  val isOriginal: Boolean = false
) {

  /** The minimum specificity required for any selector generated from this extender.
    */
  val specificity: Int = specificityOpt.getOrElse(selector.specificity)

  /** The extension that created this [Extender].
    *
    * Not all [Extender]s are created by extensions. Some simply represent the original selectors that exist in the document.
    */
  private[extend] var _extension: Nullable[Extension] = Nullable.empty

  /** Asserts that the [mediaContext] for a selector is compatible with the query context for this extender.
    */
  def assertCompatibleMediaContext(mediaContext: Nullable[List[CssMediaQuery]]): Unit =
    _extension.toOption match {
      case None            => ()
      case Some(extension) =>
        extension.mediaContext.toOption match {
          case None           => ()
          case Some(expected) =>
            mediaContext.toOption match {
              case Some(actual) if expected == actual => ()
              case _                                  =>
                throw new SassException(
                  "You may not @extend selectors across media queries.",
                  extension.span
                )
            }
        }
    }

  override def toString: String = selector.toString
}

/** An [Extension] created by merging two [Extension]s with the same extender and target.
  *
  * This is used when multiple mandatory extensions exist to ensure that both of them are marked as resolved.
  */
final class MergedExtension private (
  /** One of the merged extensions. */
  val left: Extension,
  /** The other merged extension. */
  val right: Extension
) extends Extension(
      new Extender(left.extender.selector),
      left.target,
      left.span,
      mediaContext =
        if (left.mediaContext.toOption.isDefined) left.mediaContext
        else right.mediaContext,
      isOptional = true
    ) {

  /** Returns all leaf-node [Extension]s in the tree of [MergedExtension]s. */
  def unmerge(): Iterator[Extension] = {
    val leftIt = left match {
      case m: MergedExtension => m.unmerge()
      case e => Iterator.single(e)
    }
    val rightIt = right match {
      case m: MergedExtension => m.unmerge()
      case e => Iterator.single(e)
    }
    leftIt ++ rightIt
  }
}

object MergedExtension {

  /** Returns an extension that combines [left] and [right].
    *
    * Throws a [SassException] if [left] and [right] have incompatible media contexts.
    *
    * Throws an [IllegalArgumentException] if [left] and [right] don't have the same extender and target.
    */
  def merge(left: Extension, right: Extension): Extension = {
    if (left.extender.selector != right.extender.selector || left.target != right.target) {
      throw new IllegalArgumentException(s"$left and $right aren't the same extension.")
    }

    (left.mediaContext.toOption, right.mediaContext.toOption) match {
      case (Some(l), Some(r)) if l != r =>
        throw new SassException(
          s"From ${left.span.message("")}\n" +
            "You may not @extend the same selector from within different media queries.",
          right.span
        )
      case _ => ()
    }

    // If one extension is optional and doesn't add a special media context, it
    // doesn't need to be merged.
    if (right.isOptional && right.mediaContext.toOption.isEmpty) left
    else if (left.isOptional && left.mediaContext.toOption.isEmpty) right
    else {
      val merged = new MergedExtension(left, right)
      merged.extender._extension = Nullable(merged)
      merged
    }
  }
}
