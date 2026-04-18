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
 *   Idiom: T008 — faithful port of the dart-sass _extendList / _extendComplex /
 *          _extendCompound / _extendSimple / _extendPseudo pipeline plus the
 *          addExtension / addExtensions / addSelector / cloneStore / _registerSelector
 *          / _extendExistingExtensions / _extendExistingSelectors / _trim /
 *          _unifyExtenders / _sourceSpecificityFor / _simpleSelectors public and
 *          private methods. The ad-hoc AST-based extend system (addExtensionAst /
 *          extendList / extendComplex / weaveExtension / substituteInComplex) is
 *          retained as backward-compat adapters until EvaluateVisitor is rewired
 *          to use the dart addExtension + addSelector API.
 */
package ssg
package sass
package extend

import ssg.sass.{ Nullable, SassException, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.ast.css.CssMediaQuery
import ssg.sass.ast.sass.ExtendRule
import ssg.sass.ast.selector.{ ComplexSelector, ComplexSelectorComponent, CompoundSelector, PseudoSelector, PlaceholderSelector, SelectorList, SimpleSelector }
import ssg.sass.util.{ Box, FileSpan, ModifiableBox }

import scala.collection.mutable
import scala.language.implicitConversions

/** Tracks style rules and extensions, computing the final selectors after `@extend` rules are applied.
  *
  * This is the public API surface of the extend subsystem.
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

  /** Extends [selector] with [source] extender and [targets] extendees.
    *
    * This works as though `source { @extend target }` were written in the stylesheet.
    */
  def extend(
    selector: SelectorList,
    source:   SelectorList,
    targets:  SelectorList,
    span:     FileSpan
  ): SelectorList =
    _extendOrReplace(selector, source, targets, ExtendMode.AllTargets, span)

  /** Returns a copy of [selector] with [targets] replaced by [source]. */
  def replace(
    selector: SelectorList,
    source:   SelectorList,
    targets:  SelectorList,
    span:     FileSpan
  ): SelectorList =
    _extendOrReplace(selector, source, targets, ExtendMode.Replace, span)

  private def _extendOrReplace(
    selector: SelectorList,
    source:   SelectorList,
    targets:  SelectorList,
    mode:     ExtendMode,
    span:     FileSpan
  ): SelectorList = {
    val store = new MutableExtensionStore(mode)
    if (!selector.isInvisible) store._originals ++= selector.components

    var result = selector
    for (complex <- targets.components) {
      val compound = complex.singleCompound
      if (compound.isEmpty)
        throw SassScriptException(s"Can't extend complex selector $complex.")
      val extsForCompound = mutable.Map.empty[SimpleSelector, mutable.Map[ComplexSelector, Extension]]
      for (simple <- compound.get.components) {
        val inner = mutable.Map.empty[ComplexSelector, Extension]
        for (c <- source.components)
          inner(c) = Extension(c, simple, span, optional = true)
        extsForCompound(simple) = inner
      }
      result = store._extendList(result, extsForCompound.toMap.view.mapValues(_.toMap).toMap)
    }
    result
  }
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
  * Faithful port of dart-sass `ExtensionStore` class in
  * lib/src/extend/extension_store.dart. Contains the full
  * _extendList / _extendComplex / _extendCompound / _extendSimple /
  * _extendPseudo pipeline, the _trim / _unifyExtenders helpers, the
  * addExtension / addSelector / addExtensions / cloneStore public
  * API, and the _extendExistingExtensions / _extendExistingSelectors
  * graph-update operations.
  */
final class MutableExtensionStore(val mode: ExtendMode) extends ExtensionStore {

  // ---------------------------------------------------------------------------
  // State (matches dart-sass field-for-field)
  // ---------------------------------------------------------------------------

  /** A map from all simple selectors in the stylesheet to the selector lists
    * that contain them.
    */
  private val _selectors: mutable.Map[SimpleSelector, mutable.Set[ModifiableBox[SelectorList]]] =
    mutable.Map.empty

  /** A map from all extended simple selectors to the sources of those extensions.
    * Keyed by (target → (extender complex → Extension)).
    */
  private val _extensions: mutable.Map[SimpleSelector, mutable.Map[ComplexSelector, Extension]] =
    mutable.Map.empty

  /** A map from all simple selectors in extenders to the extensions that those extenders define.
    */
  private val _extensionsByExtender: mutable.Map[SimpleSelector, mutable.ListBuffer[Extension]] =
    mutable.Map.empty

  /** A map from CSS selectors to the media query contexts they're defined in. */
  private val _mediaContexts: mutable.Map[ModifiableBox[SelectorList], List[CssMediaQuery]] =
    mutable.Map.empty

  /** A map from SimpleSelectors to the specificity of their source selectors. */
  private[extend] val _sourceSpecificity: mutable.Map[SimpleSelector, Int] =
    mutable.Map.empty

  /** The set of ComplexSelectors that were originally part of their component
    * SelectorLists, as opposed to being added by @extend.
    */
  private[extend] val _originals: mutable.Set[ComplexSelector] =
    mutable.Set.empty

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  override def isEmpty: Boolean = _extensions.isEmpty

  override def simpleSelectors: Set[SimpleSelector] = _selectors.keySet.toSet

  override def extensionsByExtender: Map[SimpleSelector, List[Extension]] =
    _extensionsByExtender.view.mapValues(_.toList).toMap

  override def extensionsWhereTarget(
    callback: SimpleSelector => Boolean
  ): Iterable[Extension] = {
    val out = mutable.ListBuffer.empty[Extension]
    for ((target, sources) <- _extensions if callback(target))
      for (ext <- sources.values) ext match {
        case m: MergedExtension =>
          out ++= m.unmerge().filterNot(_.isOptional)
        case e if !e.isOptional =>
          out += e
        case _ => ()
      }
    out
  }

  override def addSelector(
    selector:     SelectorList,
    mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Box[SelectorList] = {
    val original = selector
    if (!original.isInvisible) _originals ++= original.components

    var result = selector
    if (_extensions.nonEmpty) {
      try {
        result = _extendList(
          original,
          _extensions.toMap.view.mapValues(_.toMap).toMap,
          mediaContext.toOption
        )
      } catch {
        case error: SassException =>
          throw new SassException(
            s"From ${error.span.message("")}\n${error.sassMessage}",
            error.span
          )
      }
    }

    val modifiable = new ModifiableBox[SelectorList](result)
    mediaContext.foreach(ctx => _mediaContexts(modifiable) = ctx)
    _registerSelector(result, modifiable)
    modifiable.seal()
  }

  override def addExtension(
    extender:     SelectorList,
    target:       SimpleSelector,
    extend:       ExtendRule,
    mediaContext: Nullable[List[CssMediaQuery]] = Nullable.empty
  ): Unit = {
    val existingSelectors   = _selectors.get(target)
    val existingExtensions  = _extensionsByExtender.get(target)

    var newExtensions: mutable.Map[ComplexSelector, Extension] = null
    val sources = _extensions.getOrElseUpdate(target, mutable.Map.empty)

    for (complex <- extender.components) {
      if (!complex.isUseless) {
        val ext = Extension(
          complex,
          target,
          extend.span,
          mediaContext = mediaContext,
          optional = extend.isOptional
        )

        sources.get(complex) match {
          case Some(existing) =>
            sources(complex) = MergedExtension.merge(existing, ext)
          case None =>
            sources(complex) = ext
            for (simple <- _simpleSelectors(complex)) {
              _extensionsByExtender.getOrElseUpdate(simple, mutable.ListBuffer.empty) += ext
              _sourceSpecificity.getOrElseUpdate(simple, complex.specificity)
            }
            if (existingSelectors.isDefined || existingExtensions.isDefined) {
              if (newExtensions == null) newExtensions = mutable.Map.empty
              newExtensions(complex) = ext
            }
        }
      }
    }

    if (newExtensions == null) return

    val newExtByTarget: mutable.Map[SimpleSelector, Map[ComplexSelector, Extension]] =
      mutable.Map(target -> newExtensions.toMap)

    if (existingExtensions.isDefined) {
      val additional = _extendExistingExtensions(
        existingExtensions.get.toList,
        newExtByTarget.toMap
      )
      additional.foreach { add =>
        for ((k, v) <- add) {
          val existing = newExtByTarget.getOrElse(k, Map.empty)
          newExtByTarget(k) = existing ++ v
        }
      }
    }

    if (existingSelectors.isDefined) {
      _extendExistingSelectors(existingSelectors.get.toSet, newExtByTarget.toMap)
    }
  }

  override def addExtensions(extenders: Iterable[ExtensionStore]): Unit = {
    var extensionsToExtend:  mutable.ListBuffer[Extension]                                    = null
    var selectorsToExtend:   mutable.Set[ModifiableBox[SelectorList]]                         = null
    var newExtensions:       mutable.Map[SimpleSelector, mutable.Map[ComplexSelector, Extension]] = null

    for (store <- extenders) {
      if (!store.isEmpty) {
        val mutable_ = store.asInstanceOf[MutableExtensionStore]
        _sourceSpecificity ++= mutable_._sourceSpecificity
        for ((target, newSources) <- mutable_._extensions) {
          target match {
            case p: PlaceholderSelector if Environment.isPrivate(p.name) => ()
            case _ =>
              val extensionsForTarget = _extensionsByExtender.get(target)
              if (extensionsForTarget.isDefined) {
                if (extensionsToExtend == null) extensionsToExtend = mutable.ListBuffer.empty
                extensionsToExtend ++= extensionsForTarget.get
              }
              val selectorsForTarget = _selectors.get(target)
              if (selectorsForTarget.isDefined) {
                if (selectorsToExtend == null) selectorsToExtend = mutable.Set.empty
                selectorsToExtend ++= selectorsForTarget.get
              }
              _extensions.get(target) match {
                case Some(existingSources) =>
                  for ((extenderKey, ext) <- newSources) {
                    val merged = existingSources.get(extenderKey) match {
                      case Some(existing) => MergedExtension.merge(existing, ext)
                      case None           => ext
                    }
                    existingSources(extenderKey) = merged
                    if (extensionsForTarget.isDefined || selectorsForTarget.isDefined) {
                      if (newExtensions == null) newExtensions = mutable.Map.empty
                      newExtensions.getOrElseUpdate(target, mutable.Map.empty)(extenderKey) = merged
                    }
                  }
                case None =>
                  _extensions(target) = mutable.Map.from(newSources)
                  if (extensionsForTarget.isDefined || selectorsForTarget.isDefined) {
                    if (newExtensions == null) newExtensions = mutable.Map.empty
                    newExtensions(target) = mutable.Map.from(newSources)
                  }
              }
          }
        }
      }
    }

    if (newExtensions != null) {
      val newExtsImmutable = newExtensions.toMap.view.mapValues(_.toMap).toMap
      if (extensionsToExtend != null)
        _extendExistingExtensions(extensionsToExtend.toList, newExtsImmutable)
      if (selectorsToExtend != null)
        _extendExistingSelectors(selectorsToExtend.toSet, newExtsImmutable)
    }
  }

  override def cloneStore(): (ExtensionStore, Map[SelectorList, Box[SelectorList]]) = {
    val newSelectors      = mutable.Map.empty[SimpleSelector, mutable.Set[ModifiableBox[SelectorList]]]
    val newMediaContexts  = mutable.Map.empty[ModifiableBox[SelectorList], List[CssMediaQuery]]
    val oldToNew          = mutable.Map.empty[SelectorList, Box[SelectorList]]
    val newBoxes          = mutable.Map.empty[ModifiableBox[SelectorList], ModifiableBox[SelectorList]]

    for ((simple, sels) <- _selectors) {
      val newSet = mutable.Set.empty[ModifiableBox[SelectorList]]
      newSelectors(simple) = newSet
      for (sel <- sels) {
        val newSel = newBoxes.getOrElseUpdate(sel, new ModifiableBox(sel.value))
        newSet += newSel
        oldToNew(sel.value) = newSel.seal()
        _mediaContexts.get(sel).foreach(mc => newMediaContexts(newSel) = mc)
      }
    }

    val newStore = new MutableExtensionStore(mode)
    newStore._selectors ++= newSelectors
    for ((k, v) <- _extensions) newStore._extensions(k) = mutable.Map.from(v)
    for ((k, v) <- _extensionsByExtender) newStore._extensionsByExtender(k) = mutable.ListBuffer.from(v)
    newStore._mediaContexts ++= newMediaContexts
    newStore._sourceSpecificity ++= _sourceSpecificity
    newStore._originals ++= _originals
    (newStore, oldToNew.toMap)
  }

  // ---------------------------------------------------------------------------
  // Private: selector registration
  // ---------------------------------------------------------------------------

  private def _registerSelector(
    list:     SelectorList,
    selector: ModifiableBox[SelectorList]
  ): Unit =
    for (complex <- list.components)
      for (component <- complex.components)
        for (simple <- component.selector.components) {
          _selectors.getOrElseUpdate(simple, mutable.Set.empty) += selector
          simple match {
            case p: PseudoSelector if p.selector.isDefined =>
              _registerSelector(p.selector.get, selector)
            case _ => ()
          }
        }

  // ---------------------------------------------------------------------------
  // Private: _simpleSelectors
  // ---------------------------------------------------------------------------

  private def _simpleSelectors(complex: ComplexSelector): List[SimpleSelector] = {
    val out = mutable.ListBuffer.empty[SimpleSelector]
    for (component <- complex.components)
      for (simple <- component.selector.components) {
        out += simple
        simple match {
          case p: PseudoSelector if p.selector.isDefined =>
            for (inner <- p.selector.get.components)
              out ++= _simpleSelectors(inner)
          case _ => ()
        }
      }
    out.toList
  }

  // ---------------------------------------------------------------------------
  // Private: extend graph update
  // ---------------------------------------------------------------------------

  private def _extendExistingExtensions(
    extensions:    List[Extension],
    newExtensions: Map[SimpleSelector, Map[ComplexSelector, Extension]]
  ): Option[Map[SimpleSelector, Map[ComplexSelector, Extension]]] = {
    var additional: mutable.Map[SimpleSelector, mutable.Map[ComplexSelector, Extension]] = null

    for (ext <- extensions) {
      val sources = _extensions(ext.target)
      val selectors = try {
        _extendComplex(ext.extender.selector, newExtensions, ext.mediaContext.toOption)
      } catch {
        case error: SassException =>
          throw error.withAdditionalSpan(
            ext.extender.selector.span,
            "target selector"
          )
      }
      if (selectors == null || selectors.isEmpty) ()
      else {
        val skip = if (selectors.head == ext.extender.selector) 1 else 0
        for (complex <- selectors.drop(skip)) {
          val withExtender = ext.withExtender(complex)
          sources.get(complex) match {
            case Some(existing) =>
              sources(complex) = MergedExtension.merge(existing, withExtender)
            case None =>
              sources(complex) = withExtender
              for (component <- complex.components)
                for (simple <- component.selector.components)
                  _extensionsByExtender.getOrElseUpdate(simple, mutable.ListBuffer.empty) += withExtender
              if (newExtensions.contains(ext.target)) {
                if (additional == null) additional = mutable.Map.empty
                additional.getOrElseUpdate(ext.target, mutable.Map.empty)(complex) = withExtender
              }
          }
        }
      }
    }

    if (additional == null) None
    else Some(additional.toMap.view.mapValues(_.toMap).toMap)
  }

  private def _extendExistingSelectors(
    selectors:     Set[ModifiableBox[SelectorList]],
    newExtensions: Map[SimpleSelector, Map[ComplexSelector, Extension]]
  ): Unit =
    for (selector <- selectors) {
      val oldValue = selector.value
      try {
        selector.value = _extendList(
          selector.value,
          newExtensions,
          _mediaContexts.get(selector)
        )
      } catch {
        // TODO(nweiz): Make this a MultiSpanSassException.
        case error: SassException =>
          throw new SassException(
            s"From ${selector.value.span.message("")}\n${error.sassMessage}",
            error.span
          )
      }
      if (!(oldValue eq selector.value))
        _registerSelector(selector.value, selector)
    }

  // ---------------------------------------------------------------------------
  // Private: _extendList / _extendComplex / _extendCompound / _extendSimple
  // ---------------------------------------------------------------------------

  private[extend] def _extendList(
    list:              SelectorList,
    extensions:        Map[SimpleSelector, Map[ComplexSelector, Extension]],
    mediaQueryContext: Option[List[CssMediaQuery]] = None
  ): SelectorList = {
    var extended: mutable.ListBuffer[ComplexSelector] = null
    var i = 0
    while (i < list.components.length) {
      val complex = list.components(i)
      val result  = _extendComplex(complex, extensions, mediaQueryContext)
      if (result == null) {
        if (extended != null) extended += complex
      } else {
        if (extended == null)
          extended = if (i == 0) mutable.ListBuffer.empty else mutable.ListBuffer.from(list.components.take(i))
        extended ++= result
      }
      i += 1
    }
    if (extended == null) list
    else new SelectorList(_trim(extended.toList, _originals.contains), list.span)
  }

  private def _extendComplex(
    complex:           ComplexSelector,
    extensions:        Map[SimpleSelector, Map[ComplexSelector, Extension]],
    mediaQueryContext: Option[List[CssMediaQuery]]
  ): List[ComplexSelector] = {
    if (complex.leadingCombinators.length > 1) return null

    var extendedNotExpanded: mutable.ListBuffer[List[ComplexSelector]] = null
    val isOriginal = _originals.contains(complex)

    var i = 0
    while (i < complex.components.length) {
      val component = complex.components(i)
      val extended  = _extendCompound(component, extensions, mediaQueryContext, inOriginal = isOriginal)
      if (extended == null) {
        if (extendedNotExpanded != null)
          extendedNotExpanded += List(
            new ComplexSelector(Nil, List(component), complex.span, lineBreak = complex.lineBreak)
          )
      } else if (extendedNotExpanded != null) {
        extendedNotExpanded += extended
      } else if (i != 0) {
        extendedNotExpanded = mutable.ListBuffer(
          List(new ComplexSelector(
            complex.leadingCombinators,
            complex.components.take(i),
            complex.span,
            lineBreak = complex.lineBreak
          )),
          extended
        )
      } else if (complex.leadingCombinators.isEmpty) {
        extendedNotExpanded = mutable.ListBuffer(extended)
      } else {
        extendedNotExpanded = mutable.ListBuffer(
          extended.collect {
            case nc if nc.leadingCombinators.isEmpty ||
              nc.leadingCombinators == complex.leadingCombinators =>
              new ComplexSelector(
                complex.leadingCombinators,
                nc.components,
                complex.span,
                lineBreak = complex.lineBreak || nc.lineBreak
              )
          }
        )
      }
      i += 1
    }

    if (extendedNotExpanded == null) return null

    var first = true
    ExtendFunctions.paths(extendedNotExpanded.toList).flatMap { path =>
      ExtendFunctions.weave(path, complex.span, forceLineBreak = complex.lineBreak).map { outputComplex =>
        if (first && _originals.contains(complex))
          _originals += outputComplex
        first = false
        outputComplex
      }
    }
  }

  private def _extendCompound(
    component:         ComplexSelectorComponent,
    extensions:        Map[SimpleSelector, Map[ComplexSelector, Extension]],
    mediaQueryContext: Option[List[CssMediaQuery]],
    inOriginal:        Boolean
  ): List[ComplexSelector] = {
    val targetsUsed: mutable.Set[SimpleSelector] =
      if (mode == ExtendMode.Normal || extensions.size < 2) null
      else mutable.Set.empty

    val simples = component.selector.components
    var options: mutable.ListBuffer[List[Extender]] = null

    var i = 0
    while (i < simples.length) {
      val simple   = simples(i)
      val extended = _extendSimple(simple, extensions, mediaQueryContext, targetsUsed)
      if (extended == null) {
        if (options != null) options += List(_extenderForSimple(simple))
      } else {
        if (options == null) {
          options = mutable.ListBuffer.empty
          if (i != 0) {
            options += List(_extenderForCompound(simples.take(i), component.span))
          }
        }
        options ++= extended
      }
      i += 1
    }
    if (options == null) return null
    if (targetsUsed != null && targetsUsed.size != extensions.size) return null

    // Single-option fast path
    if (options.length == 1) {
      val extenders = options.head
      val results = mutable.ListBuffer.empty[ComplexSelector]
      for (extender <- extenders) {
        extender.assertCompatibleMediaContext(
          if (mediaQueryContext.isDefined) Nullable(mediaQueryContext.get) else Nullable.empty
        )
        val complex = extender.selector.withAdditionalCombinators(component.combinators)
        if (!complex.isUseless) results += complex
      }
      if (results.isEmpty) return null
      return results.toList
    }

    // Multi-option: compute cartesian product, unify each path
    val extenderPaths = ExtendFunctions.paths(options.toList)
    val result = mutable.ListBuffer.empty[ComplexSelector]

    if (mode != ExtendMode.Replace) {
      // First path = original compound, reconstructed from the extenders
      val firstPath = extenderPaths.head
      val originalSimples = firstPath.flatMap { extender =>
        extender.selector.components.last.selector.components
      }
      result += new ComplexSelector(
        Nil,
        List(new ComplexSelectorComponent(
          new CompoundSelector(originalSimples, component.selector.span),
          component.combinators,
          component.span
        )),
        component.span
      )
    }

    val skipFirst = if (mode == ExtendMode.Replace) 0 else 1
    for (path <- extenderPaths.drop(skipFirst)) {
      val extended = _unifyExtenders(path, mediaQueryContext, component.span)
      if (extended != null) {
        for (complex <- extended) {
          val withCombinators = complex.withAdditionalCombinators(component.combinators)
          if (!withCombinators.isUseless) result += withCombinators
        }
      }
    }

    val isOriginalFn: ComplexSelector => Boolean =
      if (inOriginal && mode != ExtendMode.Replace && result.nonEmpty) {
        val orig = result.head
        c => c == orig
      } else _ => false

    val trimmed = _trim(result.toList, isOriginalFn)
    if (trimmed.isEmpty) null else trimmed
  }

  private def _extendSimple(
    simple:            SimpleSelector,
    extensions:        Map[SimpleSelector, Map[ComplexSelector, Extension]],
    mediaQueryContext: Option[List[CssMediaQuery]],
    targetsUsed:       mutable.Set[SimpleSelector]
  ): List[List[Extender]] = {
    def withoutPseudo(s: SimpleSelector): List[Extender] = {
      val extsForSimple = extensions.get(s)
      if (extsForSimple.isEmpty) return null
      if (targetsUsed != null) targetsUsed += s
      val out = mutable.ListBuffer.empty[Extender]
      if (mode != ExtendMode.Replace) out += _extenderForSimple(s)
      for (ext <- extsForSimple.get.values) out += ext.extender
      out.toList
    }

    simple match {
      case p: PseudoSelector if p.selector.isDefined =>
        val extended = _extendPseudo(p, extensions, mediaQueryContext)
        if (extended != null) {
          return extended.map { pseudo =>
            val wp = withoutPseudo(pseudo)
            if (wp != null) wp else List(_extenderForSimple(pseudo))
          }
        }
      case _ => ()
    }

    val result = withoutPseudo(simple)
    if (result == null) null else List(result)
  }

  // ---------------------------------------------------------------------------
  // Private: helpers
  // ---------------------------------------------------------------------------

  private def _extenderForSimple(simple: SimpleSelector): Extender =
    new Extender(
      new ComplexSelector(
        Nil,
        List(new ComplexSelectorComponent(
          new CompoundSelector(List(simple), simple.span),
          Nil,
          simple.span
        )),
        simple.span
      ),
      specificityOpt = Nullable(_sourceSpecificity.getOrElse(simple, 0)),
      isOriginal = true
    )

  private def _extenderForCompound(
    simples: List[SimpleSelector],
    span:    FileSpan
  ): Extender = {
    val compound = new CompoundSelector(simples, span)
    new Extender(
      new ComplexSelector(
        Nil,
        List(new ComplexSelectorComponent(compound, Nil, span)),
        span
      ),
      specificityOpt = Nullable(_sourceSpecificityFor(compound)),
      isOriginal = true
    )
  }

  private def _sourceSpecificityFor(compound: CompoundSelector): Int = {
    var spec = 0
    for (simple <- compound.components)
      spec = math.max(spec, _sourceSpecificity.getOrElse(simple, 0))
    spec
  }

  private def _unifyExtenders(
    extenders:         List[Extender],
    mediaQueryContext: Option[List[CssMediaQuery]],
    span:              FileSpan
  ): List[ComplexSelector] = scala.util.boundary {
    val toUnify            = mutable.ListBuffer.empty[ComplexSelector]
    var originals:         mutable.ListBuffer[SimpleSelector] = null
    var originalsLineBreak = false

    for (extender <- extenders) {
      if (extender.isOriginal) {
        if (originals == null) originals = mutable.ListBuffer.empty
        val lastComponent = extender.selector.components.last
        originals ++= lastComponent.selector.components
        originalsLineBreak = originalsLineBreak || extender.selector.lineBreak
      } else if (extender.selector.isUseless) {
        scala.util.boundary.break(null)
      } else {
        toUnify += extender.selector
      }
    }

    if (originals != null) {
      toUnify.prepend(
        new ComplexSelector(
          Nil,
          List(new ComplexSelectorComponent(
            new CompoundSelector(originals.toList, span),
            Nil,
            span
          )),
          span,
          lineBreak = originalsLineBreak
        )
      )
    }

    val complexes = ExtendFunctions.unifyComplex(toUnify.toList, span)
    if (complexes.isEmpty) scala.util.boundary.break(null)

    for (extender <- extenders)
      extender.assertCompatibleMediaContext(
        if (mediaQueryContext.isDefined) Nullable(mediaQueryContext.get) else Nullable.empty
      )

    complexes.get
  }

  private def _trim(
    selectors:  List[ComplexSelector],
    isOriginal: ComplexSelector => Boolean
  ): List[ComplexSelector] = {
    // Avoid quadratic blowup for very large selector lists
    if (selectors.length > 100) return selectors

    val result       = mutable.ListBuffer.empty[ComplexSelector]
    var numOriginals = 0

    var i = selectors.length - 1
    while (i >= 0) {
      val complex1 = selectors(i)
      if (isOriginal(complex1)) {
        // Make sure we don't include duplicate originals, which could happen if
        // a style rule extends a component of its own selector.
        var dup = false
        var j   = 0
        while (!dup && j < numOriginals) {
          if (result(j) == complex1) {
            // rotateSlice(result, 0, j + 1): move result(j) to position 0,
            // shift elements 0..j-1 one position right.
            val saved = result(j)
            var k = j
            while (k > 0) {
              result(k) = result(k - 1)
              k -= 1
            }
            result(0) = saved
            dup = true
          }
          j += 1
        }
        if (!dup) {
          numOriginals += 1
          result.prepend(complex1)
        }
      } else {
        var maxSpecificity = 0
        for (component <- complex1.components)
          maxSpecificity = math.max(maxSpecificity, _sourceSpecificityFor(component.selector))

        val dominated = result.exists { complex2 =>
          complex2.specificity >= maxSpecificity && complex2.isSuperselector(complex1)
        } || selectors.take(i).exists { complex2 =>
          complex2.specificity >= maxSpecificity && complex2.isSuperselector(complex1)
        }

        if (!dominated) result.prepend(complex1)
      }
      i -= 1
    }
    result.toList
  }

  // ---------------------------------------------------------------------------
  // Private: _extendPseudo
  // ---------------------------------------------------------------------------

  private def _extendPseudo(
    pseudo:            PseudoSelector,
    extensions:        Map[SimpleSelector, Map[ComplexSelector, Extension]],
    mediaQueryContext: Option[List[CssMediaQuery]]
  ): List[PseudoSelector] = {
    val selector = pseudo.selector.get

    val extended = _extendList(selector, extensions, mediaQueryContext)
    if (extended eq selector) return null

    var complexes: List[ComplexSelector] = extended.components
    if (pseudo.normalizedName == "not" &&
      !selector.components.exists(_.components.length > 1) &&
      extended.components.exists(_.components.length == 1)) {
      complexes = extended.components.filter(_.components.length <= 1)
    }

    complexes = complexes.flatMap { complex =>
      val innerPseudo = complex.singleCompound.flatMap(_.singleSimple)
      innerPseudo.toOption match {
        case Some(ip: PseudoSelector) if ip.selector.isDefined =>
          val innerSel = ip.selector.get
          pseudo.normalizedName match {
            case "not" =>
              if (!Set("is", "matches", "where").contains(ip.normalizedName)) Nil
              else innerSel.components
            case "is" | "matches" | "where" | "any" | "current" | "nth-child" | "nth-last-child" =>
              if (ip.name != pseudo.name) Nil
              else if (ip.argument != pseudo.argument) Nil
              else innerSel.components
            case "has" | "host" | "host-context" | "slotted" =>
              List(complex)
            case _ =>
              Nil
          }
        case _ => List(complex)
      }
    }

    if (pseudo.normalizedName == "not" && selector.components.length == 1) {
      val result = complexes.map { complex =>
        pseudo.withSelector(new SelectorList(List(complex), selector.span))
      }
      if (result.isEmpty) null else result
    } else {
      List(pseudo.withSelector(new SelectorList(complexes, selector.span)))
    }
  }

  // ---------------------------------------------------------------------------
  // Backward-compat: AST-based extension API
  // (used by EvaluateVisitor until it's rewired to the dart addExtension API)
  // ---------------------------------------------------------------------------

  /** AST extension map: target simple selector -> list of extender complex selectors.
    */
  private val astExtensions: mutable.LinkedHashMap[SimpleSelector, mutable.ListBuffer[ComplexSelector]] =
    mutable.LinkedHashMap.empty

  /** Records an AST-level extension: `extender { @extend target }`.
    */
  def addExtensionAst(
    extender: ComplexSelector,
    target:   SimpleSelector,
    optional: Boolean
  ): Unit = {
    val buffer = astExtensions.getOrElseUpdate(target, mutable.ListBuffer.empty)
    if (!buffer.contains(extender)) buffer += extender
  }

  /** Returns a new [SelectorList] with all applicable extensions applied to [list].
    *
    * Delegates to the faithful dart-sass `_extendList` pipeline by converting the
    * ad-hoc `astExtensions` map into the format the pipeline expects. The ad-hoc
    * map stores `SimpleSelector → List[ComplexSelector]` (extenders); the dart pipeline
    * expects `SimpleSelector → Map[ComplexSelector, Extension]`.
    */
  def extendList(list: SelectorList): SelectorList = {
    if (astExtensions.isEmpty) return list
    // Ensure originals are tracked so _trim doesn't drop them.
    if (!list.isInvisible) _originals ++= list.components
    // Build the extensions map that _extendList expects.
    val extsMap: Map[SimpleSelector, Map[ComplexSelector, Extension]] =
      astExtensions.view.map { case (target, extenders) =>
        val inner = extenders.iterator.map { complex =>
          complex -> Extension(complex, target, list.span, optional = true)
        }.toMap
        target -> inner
      }.toMap
    _extendList(list, extsMap)
  }

  @scala.annotation.nowarn("msg=unused private member")
  private def extendComplex(complex: ComplexSelector): List[ComplexSelector] = {
    val results = mutable.ListBuffer.empty[ComplexSelector]
    results += complex
    val originalSpecificity = complex.specificity
    var i = 0
    while (i < complex.components.length) {
      val component = complex.components(i)
      val compound  = component.selector
      for ((target, extenders) <- astExtensions)
        if (compound.components.contains(target)) {
          for (extender <- extenders) {
            val mergedOpt: List[ComplexSelector] =
              if (i == complex.components.length - 1 && extender.components.length > 1)
                weaveExtension(complex, i, target, extender)
              else
                substituteInComplexUnified(complex, i, target, extender)
            for (merged <- mergedOpt)
              if (merged.specificity >= originalSpecificity && !results.contains(merged))
                results += merged
          }
        }
      i += 1
    }
    results.toList
  }

  private def weaveExtension(
    complex:        ComplexSelector,
    componentIndex: Int,
    target:         SimpleSelector,
    extender:       ComplexSelector
  ): List[ComplexSelector] =
    if (extender.components.isEmpty) Nil
    else weaveExtensionNonEmpty(complex, componentIndex, target, extender)

  private def weaveExtensionNonEmpty(
    complex:        ComplexSelector,
    componentIndex: Int,
    target:         SimpleSelector,
    extender:       ComplexSelector
  ): List[ComplexSelector] = {
    val origComponent     = complex.components(componentIndex)
    val origCompound      = origComponent.selector
    val extLast           = extender.components.last
    val origWithoutTarget = origCompound.components.filterNot(_ == target)
    val mergedCompoundOpt =
      if (origWithoutTarget.isEmpty) Nullable(extLast.selector)
      else
        ExtendFunctions.unifyCompound(
          extLast.selector,
          new CompoundSelector(origWithoutTarget, origCompound.span)
        )
    if (mergedCompoundOpt.isEmpty) Nil
    else weaveExtensionRest(complex, componentIndex, extender, mergedCompoundOpt.get)
  }

  private def weaveExtensionRest(
    complex:        ComplexSelector,
    componentIndex: Int,
    extender:       ComplexSelector,
    mergedCompound: CompoundSelector
  ): List[ComplexSelector] = {
    val origComponent = complex.components(componentIndex)
    val newLast       = new ComplexSelectorComponent(mergedCompound, origComponent.combinators, origComponent.span)
    val prefixComponents = complex.components.take(componentIndex)
    val extLeading       = extender.components.init

    if (prefixComponents.isEmpty) {
      val newComponents = extLeading :+ newLast
      List(new ComplexSelector(
        complex.leadingCombinators ++ extender.leadingCombinators,
        newComponents, complex.span, lineBreak = complex.lineBreak
      ))
    } else {
      val prefixComplex = new ComplexSelector(complex.leadingCombinators, prefixComponents, complex.span, lineBreak = complex.lineBreak)
      val extPrefix     = new ComplexSelector(extender.leadingCombinators, extLeading, extender.span, lineBreak = extender.lineBreak)
      val woven = ExtendFunctions.weave(List(prefixComplex, extPrefix), complex.span)
      woven.map(p => p.withAdditionalComponent(newLast, complex.span))
    }
  }

  private def substituteInComplexUnified(
    complex:        ComplexSelector,
    componentIndex: Int,
    target:         SimpleSelector,
    extender:       ComplexSelector
  ): List[ComplexSelector] =
    if (extender.components.isEmpty) Nil
    else substituteInComplexUnifiedNonEmpty(complex, componentIndex, target, extender)

  private def substituteInComplexUnifiedNonEmpty(
    complex:        ComplexSelector,
    componentIndex: Int,
    target:         SimpleSelector,
    extender:       ComplexSelector
  ): List[ComplexSelector] = {
    val origComponent     = complex.components(componentIndex)
    val origCompound      = origComponent.selector
    val extLast           = extender.components.last
    val origWithoutTarget = origCompound.components.filterNot(_ == target)
    val unified           =
      if (origWithoutTarget.isEmpty) Nullable(extLast.selector)
      else
        ExtendFunctions.unifyCompound(
          extLast.selector,
          new CompoundSelector(origWithoutTarget, origCompound.span)
        )
    if (unified.isEmpty) Nil
    else List(substituteInComplex(complex, componentIndex, target, extender))
  }

  private def substituteInComplex(
    complex:        ComplexSelector,
    componentIndex: Int,
    target:         SimpleSelector,
    extender:       ComplexSelector
  ): ComplexSelector = {
    val origComponent     = complex.components(componentIndex)
    val origCompound      = origComponent.selector
    val extLast           = extender.components.last
    val extLastSimples    = extLast.selector.components
    val origWithoutTarget = origCompound.components.filterNot(_ == target)
    val mergedSimples     = mergeSimples(extLastSimples, origWithoutTarget)
    val mergedCompound    = new CompoundSelector(mergedSimples, origCompound.span)
    val newComponent      = new ComplexSelectorComponent(mergedCompound, origComponent.combinators, origComponent.span)
    val extLeading        = extender.components.init
    val newComponents     = complex.components.take(componentIndex) ++ extLeading ++ (newComponent :: complex.components.drop(componentIndex + 1))
    new ComplexSelector(
      complex.leadingCombinators ++ extender.leadingCombinators,
      newComponents, complex.span, lineBreak = complex.lineBreak
    )
  }

  private def mergeSimples(first: List[SimpleSelector], second: List[SimpleSelector]): List[SimpleSelector] = {
    val out = mutable.ListBuffer.empty[SimpleSelector]
    for (s <- first) if (!out.contains(s)) out += s
    for (s <- second) if (!out.contains(s)) out += s
    out.toList
  }

  /** Whether the store contains any AST-level extensions. */
  def hasAstExtensions: Boolean = astExtensions.nonEmpty

  /** Returns the set of target simple selectors currently registered. */
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
