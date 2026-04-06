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
import ssg.sass.ast.selector.{ ComplexSelector, ComplexSelectorComponent, CompoundSelector, PlaceholderSelector, SelectorList, SimpleSelector }
import ssg.sass.util.{ Box, ModifiableBox }

import scala.collection.mutable

/** Tracks style rules and extensions, computing the final selectors after `@extend` rules are applied.
  *
  * This is the public API surface of the extend subsystem. The full extend algorithm (selector unification, second law of extend) is deferred to Phase 10 alongside the evaluator.
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
    * Extends [selector] using any registered extensions, then returns a modifiable [Box] containing the resulting list.
    */
  def addSelector(
    selector:     SelectorList,
    mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Box[SelectorList]

  /** Adds an extension to this store.
    *
    * `extender` is the selector for the style rule in which the extension is defined, and `target` is the selector passed to `@extend`.
    */
  def addExtension(
    extender:     SelectorList,
    target:       SimpleSelector,
    extend:       ExtendRule,
    mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Unit

  /** Adds existing extensions from [extenders] into this store. */
  def addExtensions(extenders: Iterable[ExtensionStore]): Unit

  /** Returns a copy of this extension store paired with a map from the selectors in the old store to their copies in the new one.
    */
  def cloneStore(): (ExtensionStore, Map[SelectorList, Box[SelectorList]])

  /** All the extensions this store contains, indexed by extender. */
  def extensionsByExtender: Map[SimpleSelector, List[Extension]]
}

object ExtensionStore {

  /** Returns a new empty, mutable extension store. */
  def apply(): ExtensionStore = new MutableExtensionStore(ExtendMode.Normal)

  /** Returns a new empty, mutable extension store with a specific extend mode (used by `selector-extend()`, `selector-replace()`).
    */
  def apply(mode: ExtendMode): ExtensionStore = new MutableExtensionStore(mode)

  /** The singleton empty extension store. */
  val empty: ExtensionStore = EmptyExtensionStore
}

/** An [ExtensionStore] that contains no extensions and can have no extensions added.
  */
object EmptyExtensionStore extends ExtensionStore {
  override def isEmpty: Boolean = true

  override def simpleSelectors: Set[SimpleSelector] = Set.empty

  override def extensionsWhereTarget(
    callback: SimpleSelector => Boolean
  ): Iterable[Extension] = Nil

  override def addSelector(
    selector:     SelectorList,
    mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Box[SelectorList] =
    throw new UnsupportedOperationException(
      "addSelector() can't be called for a const ExtensionStore."
    )

  override def addExtension(
    extender:     SelectorList,
    target:       SimpleSelector,
    extend:       ExtendRule,
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
  * Phase 7 ships only the public API surface; all selector-rewriting logic is TODO: Phase 10.
  */
final class MutableExtensionStore(val mode: ExtendMode) extends ExtensionStore {

  /** A map from all simple selectors in the stylesheet to the selector lists that contain them.
    */
  private val selectors: mutable.Map[SimpleSelector, mutable.Set[ModifiableBox[SelectorList]]] =
    mutable.Map.empty

  /** A map from all extended simple selectors to the sources of those extensions.
    */
  private val extensions: mutable.Map[SimpleSelector, mutable.Map[SelectorList, Extension]] =
    mutable.Map.empty

  /** A map from all simple selectors in extenders to the extensions that those extenders define.
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
  ): Iterable[Extension] =
    // TODO: Phase 10 — flatten MergedExtensions via unmerge()
    for {
      (target, sources) <- extensions
      if callback(target)
      extension <- sources.values
    } yield extension

  override def addSelector(
    selector:     SelectorList,
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
    extender:     SelectorList,
    target:       SimpleSelector,
    extend:       ExtendRule,
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

  /** Registers every simple selector in [list] against the given modifiable box so that later extensions can rewrite it.
    */
  private def registerSelector(
    list: SelectorList,
    box:  ModifiableBox[SelectorList]
  ): Unit = {
    // TODO: Phase 10 — recursively walk compound/complex selectors and index
    //   each simple selector. For now, just store the top-level list so that
    //   `simpleSelectors` reports something meaningful once extend runs.
    val _ = (list, box)
  }

  // ---------------------------------------------------------------------------
  // AST-based extension API
  //
  // Minimal implementation covering the common cases needed by the evaluator:
  //   * `.foo { ... } .bar { @extend .foo; }` — the extender selector is
  //     appended to the target's rule.
  //   * Placeholder targets (`%base`) — placeholder rules are stripped from
  //     output by `_applyExtends`.
  //   * Compound extend (`.a.b` + `@extend .a` from `.x` -> `.x.b`).
  //   * Multiple extenders for a single target.
  //
  // Full selector unification, the second law of extend and media-context
  // checks remain TODOs.
  // ---------------------------------------------------------------------------

  /** AST extension map: target simple selector -> list of extender complex selectors.
    */
  private val astExtensions: mutable.LinkedHashMap[SimpleSelector, mutable.ListBuffer[ComplexSelector]] =
    mutable.LinkedHashMap.empty

  /** Records an AST-level extension: `extender { @extend target }`.
    *
    * This is the entry point used by the evaluator. `optional` is accepted but not yet validated.
    */
  def addExtensionAst(
    extender: ComplexSelector,
    target:   SimpleSelector,
    optional: Boolean
  ): Unit = {
    val _      = optional
    val buffer = astExtensions.getOrElseUpdate(target, mutable.ListBuffer.empty)
    if (!buffer.contains(extender)) buffer += extender
  }

  /** Returns a new [SelectorList] with all applicable extensions applied to [list].
    *
    * For each complex selector in the list, emits the original selector plus one additional complex selector for every extender whose target appears in any of its compound components.
    */
  def extendList(list: SelectorList): SelectorList = {
    if (astExtensions.isEmpty) return list
    val out = mutable.ListBuffer.empty[ComplexSelector]
    for (complex <- list.components) {
      val extended = extendComplex(complex)
      for (c <- extended)
        if (!out.contains(c)) out += c
    }
    if (out.isEmpty) list
    else new SelectorList(out.toList, list.span)
  }

  /** Expands a single complex selector into itself plus any extension-generated variants.
    *
    * Implements the "second law of extend": a generated selector is only emitted if its specificity is greater than or equal to the specificity of the original complex selector being extended. This
    * preserves the user's intent that an `@extend`-generated selector never selects fewer elements than its source.
    */
  private def extendComplex(complex: ComplexSelector): List[ComplexSelector] = {
    val results = mutable.ListBuffer.empty[ComplexSelector]
    results += complex
    val originalSpecificity = complex.specificity
    var i                   = 0
    while (i < complex.components.length) {
      val component = complex.components(i)
      val compound  = component.selector
      // For each target in the store, check whether this compound contains it.
      for ((target, extenders) <- astExtensions)
        if (compound.components.contains(target)) {
          for (extender <- extenders) {
            val merged = substituteInComplex(complex, i, target, extender)
            // Second law of extend: drop generated selectors whose
            // specificity is lower than the original complex selector's.
            if (merged.specificity >= originalSpecificity && !results.contains(merged))
              results += merged
          }
        }
      i += 1
    }
    results.toList
  }

  /** Produces a new complex selector based on [complex] where the component at [componentIndex] has its [target] simple selector replaced by the simples drawn from [extender]'s last compound, with
    * any leading components of [extender] prepended as additional complex components.
    */
  private def substituteInComplex(
    complex:        ComplexSelector,
    componentIndex: Int,
    target:         SimpleSelector,
    extender:       ComplexSelector
  ): ComplexSelector = {
    val origComponent = complex.components(componentIndex)
    val origCompound  = origComponent.selector
    // Merge target-stripped original simples with the extender's last compound.
    val extLast           = extender.components.last
    val extLastSimples    = extLast.selector.components
    val origWithoutTarget = origCompound.components.filterNot(_ == target)
    val mergedSimples     = mergeSimples(extLastSimples, origWithoutTarget)
    val mergedCompound    = new CompoundSelector(mergedSimples, origCompound.span)

    val newComponent = new ComplexSelectorComponent(
      mergedCompound,
      origComponent.combinators,
      origComponent.span
    )

    // Prepend any leading components of the extender (e.g. for `.a .b` extender).
    val extLeading    = extender.components.init
    val newComponents =
      complex.components.take(componentIndex) ++ extLeading ++ (newComponent :: complex.components.drop(componentIndex + 1))

    new ComplexSelector(
      complex.leadingCombinators ++ extender.leadingCombinators,
      newComponents,
      complex.span,
      lineBreak = complex.lineBreak
    )
  }

  /** Merges two simple-selector lists, preserving order and avoiding duplicates.
    */
  private def mergeSimples(
    first:  List[SimpleSelector],
    second: List[SimpleSelector]
  ): List[SimpleSelector] = {
    val out = mutable.ListBuffer.empty[SimpleSelector]
    for (s <- first) if (!out.contains(s)) out += s
    for (s <- second) if (!out.contains(s)) out += s
    out.toList
  }

  /** Whether the store contains any AST-level extensions. */
  def hasAstExtensions: Boolean = astExtensions.nonEmpty

  /** Returns the set of target simple selectors currently registered. Used by
    * the evaluator's `!optional` check to detect unmatched extend targets.
    */
  def astTargets: Iterable[SimpleSelector] = astExtensions.keys
}

/** Returns true if the given complex selector is composed solely of placeholder selectors (and so should be stripped from CSS output).
  */
object ExtendUtils {
  def isPlaceholderOnly(complex: ComplexSelector): Boolean =
    complex.components.nonEmpty &&
      complex.components.forall { component =>
        component.selector.components.nonEmpty &&
        component.selector.components.forall(_.isInstanceOf[PlaceholderSelector])
      }
}
