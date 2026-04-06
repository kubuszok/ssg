/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/extend/extension_store.dart, lib/src/extend/empty_extension_store.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: extension_store.dart -> ExtensionStore.scala
 *   Convention: trait ExtensionStore with mutable default implementation and
 *               EmptyExtensionStore singleton; Box[SelectorList] from ssg.sass.util
 *   Idiom: Phase 7 skeleton only — complex selector-unification and
 *          second-law-of-extend algorithms deferred to Phase 10 evaluator work
 *          TODO: Phase 10 — port the full addExtensions / extendList /
 *          extendComplex / extendCompound pipeline from extension_store.dart
 */
package ssg
package sass
package extend

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.ast.css.CssMediaQuery
import ssg.sass.ast.sass.ExtendRule
import ssg.sass.ast.selector.{SelectorList, SimpleSelector}
import ssg.sass.util.{Box, ModifiableBox}

import scala.collection.mutable

/** Tracks style rules and extensions, computing the final selectors after
 * `@extend` rules are applied.
 *
 * This is the public API surface of the extend subsystem. The full extend
 * algorithm (selector unification, second law of extend) is deferred to
 * Phase 10 alongside the evaluator.
 */
trait ExtensionStore {

  /** Whether there are any extensions. */
  def isEmpty: Boolean

  /** All the simple selectors that are targets of extensions. */
  def simpleSelectors: Set[SimpleSelector]

  /** Returns all the extensions whose targets match [callback]. */
  def extensionsWhereTarget(callback: SimpleSelector => Boolean): Iterable[Extension]

  /** Adds [selector] to this store.
   *
   * Extends [selector] using any registered extensions, then returns a
   * modifiable [Box] containing the resulting list.
   */
  def addSelector(
      selector: SelectorList,
      mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Box[SelectorList]

  /** Adds an extension to this store.
   *
   * `extender` is the selector for the style rule in which the extension
   * is defined, and `target` is the selector passed to `@extend`.
   */
  def addExtension(
      extender: SelectorList,
      target: SimpleSelector,
      extend: ExtendRule,
      mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Unit

  /** Adds existing extensions from [extenders] into this store. */
  def addExtensions(extenders: Iterable[ExtensionStore]): Unit

  /** Returns a copy of this extension store paired with a map from the
   * selectors in the old store to their copies in the new one.
   */
  def cloneStore(): (ExtensionStore, Map[SelectorList, Box[SelectorList]])

  /** All the extensions this store contains, indexed by extender. */
  def extensionsByExtender: Map[SimpleSelector, List[Extension]]
}

object ExtensionStore {
  /** Returns a new empty, mutable extension store. */
  def apply(): ExtensionStore = new MutableExtensionStore(ExtendMode.Normal)

  /** Returns a new empty, mutable extension store with a specific extend
   * mode (used by `selector-extend()`, `selector-replace()`).
   */
  def apply(mode: ExtendMode): ExtensionStore = new MutableExtensionStore(mode)

  /** The singleton empty extension store. */
  val empty: ExtensionStore = EmptyExtensionStore
}

/** An [ExtensionStore] that contains no extensions and can have no
 * extensions added.
 */
object EmptyExtensionStore extends ExtensionStore {
  override def isEmpty: Boolean = true

  override def simpleSelectors: Set[SimpleSelector] = Set.empty

  override def extensionsWhereTarget(
      callback: SimpleSelector => Boolean
  ): Iterable[Extension] = Nil

  override def addSelector(
      selector: SelectorList,
      mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Box[SelectorList] =
    throw new UnsupportedOperationException(
      "addSelector() can't be called for a const ExtensionStore."
    )

  override def addExtension(
      extender: SelectorList,
      target: SimpleSelector,
      extend: ExtendRule,
      mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Unit =
    throw new UnsupportedOperationException(
      "addExtension() can't be called for a const ExtensionStore."
    )

  override def addExtensions(extenders: Iterable[ExtensionStore]): Unit =
    throw new UnsupportedOperationException(
      "addExtensions() can't be called for a const ExtensionStore."
    )

  override def cloneStore(): (ExtensionStore, Map[SelectorList, Box[SelectorList]]) =
    (EmptyExtensionStore, Map.empty)

  override def extensionsByExtender: Map[SimpleSelector, List[Extension]] = Map.empty
}

/** Default mutable [ExtensionStore] implementation.
 *
 * Phase 7 ships only the public API surface; all selector-rewriting logic
 * is TODO: Phase 10.
 */
final class MutableExtensionStore(val mode: ExtendMode) extends ExtensionStore {

  /** A map from all simple selectors in the stylesheet to the selector lists
   * that contain them.
   */
  private val selectors: mutable.Map[SimpleSelector, mutable.Set[ModifiableBox[SelectorList]]] =
    mutable.Map.empty

  /** A map from all extended simple selectors to the sources of those
   * extensions.
   */
  private val extensions: mutable.Map[SimpleSelector, mutable.Map[SelectorList, Extension]] =
    mutable.Map.empty

  /** A map from all simple selectors in extenders to the extensions that
   * those extenders define.
   */
  private val extensionsByExtenderMut: mutable.Map[SimpleSelector, mutable.ListBuffer[Extension]] =
    mutable.Map.empty

  /** A map from CSS selectors to the media query contexts they're defined in. */
  private val mediaContexts: mutable.Map[ModifiableBox[SelectorList], List[CssMediaQuery]] =
    mutable.Map.empty

  override def isEmpty: Boolean = extensions.isEmpty

  override def simpleSelectors: Set[SimpleSelector] = selectors.keySet.toSet

  override def extensionsByExtender: Map[SimpleSelector, List[Extension]] =
    extensionsByExtenderMut.view.mapValues(_.toList).toMap

  override def extensionsWhereTarget(
      callback: SimpleSelector => Boolean
  ): Iterable[Extension] = {
    // TODO: Phase 10 — flatten MergedExtensions via unmerge()
    for {
      (target, sources) <- extensions
      if callback(target)
      extension <- sources.values
    } yield extension
  }

  override def addSelector(
      selector: SelectorList,
      mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Box[SelectorList] = {
    // TODO: Phase 10 — run the selector through existing extensions
    //   (currently stored verbatim; selector unification deferred to
    //   evaluator integration).
    val modifiable = new ModifiableBox[SelectorList](selector)
    mediaContext.foreach(ctx => mediaContexts(modifiable) = ctx)
    registerSelector(selector, modifiable)
    modifiable.seal()
  }

  override def addExtension(
      extender: SelectorList,
      target: SimpleSelector,
      extend: ExtendRule,
      mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Unit = {
    // TODO: Phase 10 — apply the new extension to any already-registered
    //   selectors and re-index the extension graph.
    val _ = (extender, target, extend, mediaContext)
  }

  override def addExtensions(extenders: Iterable[ExtensionStore]): Unit = {
    // TODO: Phase 10 — merge extensions from [extenders] into this store,
    //   unifying media contexts and rewriting existing selectors.
    val _ = extenders
  }

  override def cloneStore(): (ExtensionStore, Map[SelectorList, Box[SelectorList]]) = {
    // TODO: Phase 10 — deep-copy selectors/extensions, return mapping
    val newStore = new MutableExtensionStore(mode)
    (newStore, Map.empty)
  }

  /** Registers every simple selector in [list] against the given modifiable
   * box so that later extensions can rewrite it.
   */
  private def registerSelector(
      list: SelectorList,
      box: ModifiableBox[SelectorList]
  ): Unit = {
    // TODO: Phase 10 — recursively walk compound/complex selectors and index
    //   each simple selector. For now, just store the top-level list so that
    //   `simpleSelectors` reports something meaningful once extend runs.
    val _ = (list, box)
  }
}
