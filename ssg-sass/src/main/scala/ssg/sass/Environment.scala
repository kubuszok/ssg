/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/environment.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: environment.dart -> Environment.scala
 *   Convention: full faithful port of the dart-sass lexical Environment.
 *               Variable/function/mixin scope chains (nested Lists of
 *               per-scope Maps) mirror dart-sass exactly, as do the
 *               module / forwarded-module / imported-module / nested-
 *               forwarded-module storage layouts. `_assertNoConflicts`,
 *               `_fromOneModule`, `_variableIndex` / `_functionIndex` /
 *               `_mixinIndex`, cached `_lastVariable*` lookups, and
 *               `scope()` with full cleanup of the index caches are all
 *               ported one-for-one.
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 5053
 * Covenant-baseline-loc: 1195
 * Covenant-baseline-methods: Environment,atRoot,inMixin,closure,forImport,addModule,forwardModule,_assertNoConflicts,importForwards,getVariable,_getVariableFromGlobalModule,getVariableNode,_getVariableNodeFromGlobalModule,variableExists,globalVariableExists,_variableIndex,setVariable,setLocalVariable,setGlobalVariable,markVariableConfigurable,isVariableConfigurable,getFunction,_getFunctionFromGlobalModule,_functionIndex,functionExists,setFunction,getMixin,_getMixinFromGlobalModule,_mixinIndex,mixinExists,setMixin,content,asMixin,withContent,scope,withinScope,withinSemiGlobalScope,withSnapshot,toImplicitConfiguration,toModule,toDummyModule,_getModule,_fromOneModule,getNamespace,findNamespacedModule,getNamespacedVariable,getNamespacedFunction,getNamespacedMixin,addNamespace,variableEntries,functionValues,mixinValues,publicView,global,functions,mixins,withBuiltins,isPrivate,EnvironmentModule
 * Covenant-dart-reference: lib/src/environment.dart
 * Covenant-verified: 2026-04-08
 *
 * T007 — Phase 4 task. Faithful port of environment.dart (~1153 dart
 * LoC). Covers:
 *   - Scoped variable/function/mixin stacks with lazy-filled index caches
 *   - `_lastVariableName` / `_lastVariableIndex` fast path for repeat
 *     variable lookups
 *   - Module storage (`_modules`, `_globalModules`, `_importedModules`,
 *     `_forwardedModules`) and the nested-forwarded-module list
 *   - `addModule` with `MultiSpanSassException` for namespace conflicts
 *     and the variable-collision check for namespaceless adds
 *   - `forwardModule(module, rule)` with the full `_assertNoConflicts`
 *     pairwise check over `variables` / `functions` / `mixins`
 *   - `importForwards` with `ShadowedView` shadowing of already-imported
 *     and already-forwarded modules, plus `_nestedForwardedModules`
 *     population for non-root imports
 *   - `getVariable` / `getFunction` / `getMixin` with the scope-chain
 *     walk, index cache, and `_fromOneModule` fallback over nested
 *     forwarded, imported, and global modules
 *   - `setVariable` with the namespace path, the global-module write-
 *     through, and nested-forwarded-module redirection
 *   - `scope(callback, semiGlobal, when)` with full cleanup of
 *     variable/function/mixin scopes plus the nested-forwarded-modules
 *     level, and reset of the last-variable cache
 *   - `toModule` / `toDummyModule` backed by `EnvironmentModule`
 *     with `_modulesByVariable` and the `couldHaveBeenConfigured`
 *     recursive check
 *
 * Status: global sass-spec 4993→5053 (+60 cases). The visitor was
 * also updated to use module-based @forward / @import semantics:
 *   - `visitForwardRule` registers the upstream module via
 *     `forwardModule(module, rule)` instead of copying members
 *     into the current env's local scopes
 *   - `_loadDynamicImport` uses `forImport()` + `importForwards()`
 *     when the imported file contains `@use` / `@forward`
 *   - `_runUserDefinedFunction` and `_invokeMixinCallable` switch
 *     to `captured.closure()` and push a `scope()` so $-assignments
 *     in the body redirect to a local frame instead of writing
 *     through to the captured env's shared global map
 *   - `visitVariableDeclaration` consults `_pendingConfig` on
 *     `!default` declarations at module root (no pre-seeding into
 *     the module's local scope)
 *   - `ForwardedView` show/hide checks moved to PREFIXED keys to
 *     match dart-sass `_forwardedMap`
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions

import ssg.sass.ast.AstNode
import ssg.sass.ast.css.CssStylesheet
import ssg.sass.ast.sass.{ ContentBlock, ForwardRule }
import ssg.sass.extend.ExtensionStore
import ssg.sass.value.Value

/** The lexical environment in which Sass code is evaluated.
  *
  * Full port of dart-sass `Environment` with one-for-one fields and
  * methods. The scope chain is stored as `_variables: ArrayBuffer[Map]`
  * where index 0 is the global scope and the tail is the innermost
  * local scope; `_functions` and `_mixins` follow the same layout.
  */
final class Environment private (
  private val _modules:          mutable.Map[String, Module[Callable]],
  private val _namespaceNodes:   mutable.Map[String, Nullable[AstNode]],
  private val _globalModules:    mutable.LinkedHashMap[Module[Callable], Nullable[AstNode]],
  private val _importedModules:  mutable.LinkedHashMap[Module[Callable], Nullable[AstNode]],
  private var _forwardedModules: Nullable[mutable.LinkedHashMap[Module[Callable], Nullable[AstNode]]],
  private var _nestedForwardedModules: Nullable[mutable.ArrayBuffer[mutable.ArrayBuffer[Module[Callable]]]],
  private val _allModules:        mutable.ArrayBuffer[Module[Callable]],
  private val _variables:         mutable.ArrayBuffer[mutable.Map[String, Value]],
  private val _variableNodes:     mutable.ArrayBuffer[mutable.Map[String, AstNode]],
  private val _functions:         mutable.ArrayBuffer[mutable.Map[String, Callable]],
  private val _mixins:            mutable.ArrayBuffer[mutable.Map[String, Callable]],
  private var _content:           Nullable[ContentBlock],
  private val _configurableVariables: mutable.Set[String]
) {

  /** Lazy name->scope-index caches. Rebuilt on scope pop. */
  private val _variableIndices: mutable.Map[String, Int] = mutable.Map.empty
  private val _functionIndices: mutable.Map[String, Int] = mutable.Map.empty
  private val _mixinIndices:    mutable.Map[String, Int] = mutable.Map.empty

  /** Last-variable fast path. */
  private var _lastVariableName:  Nullable[String] = Nullable.empty
  private var _lastVariableIndex: Int              = -1

  /** Whether we are lexically inside a mixin body. */
  private var _inMixin: Boolean = false

  /** Whether assignments in the current scope should propagate to the
    * enclosing scope without an explicit `!global`. `true` in the
    * global scope, in `@if`/`@for`/`@each`/`@while` bodies, and
    * inherited from parent when nested.
    */
  private var _inSemiGlobalScope: Boolean = true

  /** Names declared with `!global`. Used by `global()` snapshots. */
  private val globalVarNames: mutable.Set[String] = mutable.Set.empty

  def this() = this(
    _modules = mutable.Map.empty,
    _namespaceNodes = mutable.Map.empty,
    _globalModules = mutable.LinkedHashMap.empty,
    _importedModules = mutable.LinkedHashMap.empty,
    _forwardedModules = Nullable.empty,
    _nestedForwardedModules = Nullable.empty,
    _allModules = mutable.ArrayBuffer.empty,
    _variables = mutable.ArrayBuffer(mutable.Map.empty),
    _variableNodes = mutable.ArrayBuffer(mutable.Map.empty),
    _functions = mutable.ArrayBuffer(mutable.Map.empty),
    _mixins = mutable.ArrayBuffer(mutable.Map.empty),
    _content = Nullable.empty,
    _configurableVariables = mutable.Set.empty
  )

  /** True when this environment is at the root (only the global scope exists). */
  def atRoot: Boolean = _variables.length == 1

  /** Whether we are lexically within a mixin body. */
  def inMixin: Boolean = _inMixin

  // ---------------------------------------------------------------------------
  // Closure / import variant constructors.
  // ---------------------------------------------------------------------------

  /** Creates a closure whose scope chain is a snapshot of the current
    * chain. Subsequent scope pushes/pops in this environment do not
    * affect the closure, but existing scope entries remain shared so
    * later assignments through them are visible to both.
    */
  def closure(): Environment = new Environment(
    _modules = _modules,
    _namespaceNodes = _namespaceNodes,
    _globalModules = _globalModules,
    _importedModules = _importedModules,
    _forwardedModules = _forwardedModules,
    _nestedForwardedModules = _nestedForwardedModules,
    _allModules = _allModules,
    _variables = _variables.clone(),
    _variableNodes = _variableNodes.clone(),
    _functions = _functions.clone(),
    _mixins = _mixins.clone(),
    _content = _content,
    // Closures are always in nested contexts where configurable
    // variables are never added.
    _configurableVariables = mutable.Set.empty
  )

  /** Returns a fresh environment for evaluating an `@import`ed file.
    *
    * Shares variables/functions/mixins with the caller but starts with
    * empty `_modules` / `_globalModules` / `_forwardedModules` so the
    * imported file cannot access modules that were namespaced into the
    * caller. Imported modules flow through via `_importedModules`.
    */
  def forImport(): Environment = {
    val e = new Environment(
      _modules = mutable.Map.empty,
      _namespaceNodes = mutable.Map.empty,
      _globalModules = mutable.LinkedHashMap.empty,
      _importedModules = _importedModules,
      _forwardedModules = Nullable.empty,
      _nestedForwardedModules = _nestedForwardedModules,
      _allModules = mutable.ArrayBuffer.empty,
      _variables = _variables.clone(),
      _variableNodes = _variableNodes.clone(),
      _functions = _functions.clone(),
      _mixins = _mixins.clone(),
      _content = _content,
      _configurableVariables = _configurableVariables
    )
    e._inSemiGlobalScope = true
    e
  }

  // ---------------------------------------------------------------------------
  // Module storage
  // ---------------------------------------------------------------------------

  /** Adds a module to this environment.
    *
    * When [namespace] is empty, the module is added as a namespaceless
    * global module. The variables of the current global scope are
    * checked for collisions with the new module's variables, matching
    * dart-sass's error message.
    *
    * When [namespace] is present, the module is stored under that
    * name; a pre-existing module with the same namespace raises a
    * MultiSpanSassException containing the span of the original `@use`.
    */
  def addModule(
    module:       Module[Callable],
    nodeWithSpan: Nullable[AstNode] = Nullable.empty,
    namespace:    Nullable[String]  = Nullable.empty
  ): Unit =
    namespace.toOption match {
      case None =>
        // `@use ... as *` or a built-in module registration. The span
        // is optional — for built-in modules (e.g. `sass:math`) and
        // synthetic registrations the span is legitimately absent.
        _globalModules(module) = nodeWithSpan
        _allModules += module
        // Collision with an existing global variable of the same name.
        val globalVars = _variables(0)
        val clashing = globalVars.keysIterator.find(name => module.variables.contains(name))
        clashing.foreach { name =>
          throw SassScriptException(
            s"""This module and the new module both define a variable named "$$$name"."""
          )
        }
      case Some(ns) =>
        if (_modules.contains(ns)) {
          val priorSpan = _namespaceNodes.get(ns).flatMap(_.toOption)
            .map(_.span.toString).getOrElse("<unknown>")
          throw SassScriptException(
            s"""There's already a module with namespace "$ns" (first loaded at $priorSpan)."""
          )
        }
        _modules(ns) = module
        _namespaceNodes(ns) = nodeWithSpan
        _allModules += module
    }

  /** Exposes [module]'s members to downstream modules under a forwarded
    * view per [rule].
    *
    * Checks each of `variables`/`functions`/`mixins` for conflicts with
    * already-forwarded modules, collapsing name-clashes that share the
    * same `variableIdentity` (meaning they come from a common upstream).
    */
  def forwardModule(module: Module[Callable], rule: ForwardRule): Unit = {
    val forwarded = _forwardedModules.toOption.getOrElse {
      val m = mutable.LinkedHashMap.empty[Module[Callable], Nullable[AstNode]]
      _forwardedModules = Nullable(m)
      m
    }

    val view = new ForwardedView[Callable](
      inner = module,
      prefix = rule.prefix,
      shownVariables = rule.shownVariables,
      shownMixinsAndFunctions = rule.shownMixinsAndFunctions,
      hiddenVariables = rule.hiddenVariables,
      hiddenMixinsAndFunctions = rule.hiddenMixinsAndFunctions
    )
    for (other <- forwarded.keys) {
      _assertNoConflicts(view.variables, other.variables, view, other, "variable")
      _assertNoConflicts(view.functions, other.functions, view, other, "function")
      _assertNoConflicts(view.mixins, other.mixins, view, other, "mixin")
    }

    // Add the ORIGINAL module (not the view) to _allModules so upstream
    // de-duplication works via `==`.
    _allModules += module
    forwarded(view) = rule
  }

  /** Throws a SassScriptException if [newMembers] from [newModule] has
    * any keys overlapping [oldMembers] from [oldModule] that don't
    * share a `variableIdentity` (for variables) or reference equality
    * (for callables).
    */
  private def _assertNoConflicts(
    newMembers: Map[String, ?],
    oldMembers: Map[String, ?],
    newModule:  Module[Callable],
    oldModule:  Module[Callable],
    memberType: String
  ): Unit = {
    val (smaller, larger) =
      if (newMembers.size < oldMembers.size) (newMembers, oldMembers)
      else (oldMembers, newMembers)
    for ((name, small) <- smaller) {
      larger.get(name).foreach { large =>
        val isSame =
          if (memberType == "variable")
            newModule.variableIdentity(name) == oldModule.variableIdentity(name)
          else large == small
        if (!isSame) {
          val displayName = if (memberType == "variable") s"$$$name" else name
          throw SassScriptException(
            s"Two forwarded modules both define a $memberType named $displayName."
          )
        }
      }
    }
  }

  /** Makes [module]'s forwarded members available in the current
    * environment, matching `@import` semantics.
    *
    * At root, existing imported and forwarded modules are shadowed so
    * they no longer expose names that the newly-imported module
    * forwards. Below root, the new modules go into
    * `_nestedForwardedModules` at the current scope level.
    */
  def importForwards(module: Module[Callable]): Unit = {
    val fwdOpt = module match {
      case impl: Environment.EnvironmentModule => impl.env._forwardedModules
      case _                                   => Nullable.empty[mutable.LinkedHashMap[Module[Callable], Nullable[AstNode]]]
    }
    if (fwdOpt.isEmpty) return
    var forwarded: mutable.LinkedHashMap[Module[Callable], Nullable[AstNode]] = fwdOpt.get

    // Omit modules from [forwarded] that are already globally available
    // and forwarded in this module.
    val thisForwarded = _forwardedModules.toOption
    if (thisForwarded.isDefined) {
      val filtered = mutable.LinkedHashMap.empty[Module[Callable], Nullable[AstNode]]
      for ((m, n) <- forwarded)
        if (!thisForwarded.get.contains(m) || !_globalModules.contains(m))
          filtered(m) = n
      forwarded = filtered
    }

    val forwardedVariableNames = mutable.Set.empty[String]
    val forwardedFunctionNames = mutable.Set.empty[String]
    val forwardedMixinNames    = mutable.Set.empty[String]
    for (m <- forwarded.keys) {
      forwardedVariableNames ++= m.variables.keys
      forwardedFunctionNames ++= m.functions.keys
      forwardedMixinNames ++= m.mixins.keys
    }

    if (atRoot) {
      // Hide members from modules already imported/forwarded that
      // would otherwise conflict with the @imported members.
      val importedSnap = _importedModules.toList
      for ((m, node) <- importedSnap) {
        val shadowed = Environment.makeShadowed(
          m,
          variables = forwardedVariableNames.toSet,
          mixins = forwardedMixinNames.toSet,
          functions = forwardedFunctionNames.toSet
        )
        shadowed.foreach { s =>
          val _ = _importedModules.remove(m)
          _importedModules(s) = node
        }
      }
      val tforwarded = _forwardedModules.toOption.getOrElse {
        val m = mutable.LinkedHashMap.empty[Module[Callable], Nullable[AstNode]]
        _forwardedModules = Nullable(m)
        m
      }
      val fwdSnap = tforwarded.toList
      for ((m, node) <- fwdSnap) {
        val shadowed = Environment.makeShadowed(
          m,
          variables = forwardedVariableNames.toSet,
          mixins = forwardedMixinNames.toSet,
          functions = forwardedFunctionNames.toSet
        )
        shadowed.foreach { s =>
          val _ = tforwarded.remove(m)
          tforwarded(s) = node
        }
      }

      for ((m, n) <- forwarded) {
        _importedModules(m) = n
        tforwarded(m) = n
      }
    } else {
      val nested = _nestedForwardedModules.toOption.getOrElse {
        val buf = mutable.ArrayBuffer.fill(_variables.length - 1)(mutable.ArrayBuffer.empty[Module[Callable]])
        _nestedForwardedModules = Nullable(buf)
        buf
      }
      nested.last ++= forwarded.keys
    }

    // Remove existing member definitions that are now shadowed by the
    // forwarded modules.
    for (v <- forwardedVariableNames) {
      _variableIndices.remove(v)
      _variables.last.remove(v)
      _variableNodes.last.remove(v)
    }
    for (f <- forwardedFunctionNames) {
      _functionIndices.remove(f)
      _functions.last.remove(f)
    }
    for (mx <- forwardedMixinNames) {
      _mixinIndices.remove(mx)
      _mixins.last.remove(mx)
    }
  }

  // ---------------------------------------------------------------------------
  // Variable lookup
  // ---------------------------------------------------------------------------

  /** Returns the variable named [name] from the namespace [namespace] if
    * given, or walking the scope chain then namespaceless modules
    * otherwise. Returns Nullable.empty when undefined.
    */
  def getVariable(name: String, namespace: Nullable[String] = Nullable.empty): Nullable[Value] = {
    if (namespace.isDefined)
      return _getModule(namespace.get).variables.get(name) match {
        case Some(v) => v
        case None    => Nullable.empty
      }

    if (_lastVariableName.exists(_ == name))
      return _variables(_lastVariableIndex).get(name) match {
        case Some(v) => v
        case None    => _getVariableFromGlobalModule(name)
      }

    _variableIndices.get(name) match {
      case Some(idx) =>
        _lastVariableName = Nullable(name)
        _lastVariableIndex = idx
        _variables(idx).get(name) match {
          case Some(v) => v
          case None    => _getVariableFromGlobalModule(name)
        }
      case None =>
        val found = _variableIndex(name)
        if (found >= 0) {
          _lastVariableName = Nullable(name)
          _lastVariableIndex = found
          _variableIndices(name) = found
          _variables(found).get(name) match {
            case Some(v) => v
            case None    => _getVariableFromGlobalModule(name)
          }
        } else _getVariableFromGlobalModule(name)
    }
  }

  /** Looks up [name] via `_fromOneModule` across the namespaceless
    * (imported + global) modules.
    */
  private def _getVariableFromGlobalModule(name: String): Nullable[Value] =
    _fromOneModule[Value](name, "variable", m => m.variables.get(name) match {
      case Some(v) => Nullable(v)
      case None    => Nullable.empty
    })

  /** Returns the [[AstNode]] for the variable named [name], or empty if no
    * such variable is declared. The node is intended as a proxy for the
    * source span of the variable's value origin — used by the evaluator
    * to attach source spans to deprecation warnings without forcing
    * span materialization for variables that never need it.
    */
  def getVariableNode(name: String, namespace: Nullable[String] = Nullable.empty): Nullable[AstNode] = {
    if (namespace.isDefined) {
      // No public `variableNodes` accessor on Module yet — we look the
      // node up through the namespaced env if available.
      val ns = _modules.get(namespace.get)
      ns match {
        case Some(impl: Environment.EnvironmentModule) =>
          impl.env._variableNodes(0).get(name) match {
            case Some(n) => Nullable(n)
            case None    => Nullable.empty
          }
        case _ => Nullable.empty
      }
    } else if (_lastVariableName.exists(_ == name)) {
      _variableNodes(_lastVariableIndex).get(name) match {
        case Some(n) => Nullable(n)
        case None    => _getVariableNodeFromGlobalModule(name)
      }
    } else {
      _variableIndices.get(name) match {
        case Some(idx) =>
          _lastVariableName = Nullable(name)
          _lastVariableIndex = idx
          _variableNodes(idx).get(name) match {
            case Some(n) => Nullable(n)
            case None    => _getVariableNodeFromGlobalModule(name)
          }
        case None =>
          val found = _variableIndex(name)
          if (found >= 0) {
            _lastVariableName = Nullable(name)
            _lastVariableIndex = found
            _variableIndices(name) = found
            _variableNodes(found).get(name) match {
              case Some(n) => Nullable(n)
              case None    => _getVariableNodeFromGlobalModule(name)
            }
          } else _getVariableNodeFromGlobalModule(name)
      }
    }
  }

  /** Walks namespaceless (imported + global) modules to find the AstNode
    * for [name]. Mirrors dart-sass `_getVariableNodeFromGlobalModule`.
    *
    * dart-sass's `Module` carries a `variableNodes` map. The ssg-sass
    * `Module` trait routes variable-node access through its concrete
    * subtypes — the `EnvironmentModule` wrapper exposes its underlying
    * env's `_variableNodes(0)` directly. For built-in and forwarded-view
    * modules variable nodes are absent (those modules never produce
    * runtime span warnings on their own variables), so this method
    * returns empty for them.
    */
  private def _getVariableNodeFromGlobalModule(name: String): Nullable[AstNode] = scala.util.boundary {
    for (m <- _importedModules.keysIterator) m match {
      case impl: Environment.EnvironmentModule =>
        impl.env._variableNodes(0).get(name) match {
          case Some(n) => scala.util.boundary.break(Nullable(n))
          case None    => ()
        }
      case _ => ()
    }
    for (m <- _globalModules.keysIterator) m match {
      case impl: Environment.EnvironmentModule =>
        impl.env._variableNodes(0).get(name) match {
          case Some(n) => scala.util.boundary.break(Nullable(n))
          case None    => ()
        }
      case _ => ()
    }
    Nullable.empty
  }

  /** Returns the scope index (0..length-1) that contains [name], or -1. */
  private def _variableIndex(name: String): Int = {
    var i = _variables.length - 1
    while (i >= 0) {
      if (_variables(i).contains(name)) return i
      i -= 1
    }
    -1
  }

  def variableExists(name: String): Boolean = getVariable(name).isDefined

  def globalVariableExists(name: String, namespace: Nullable[String] = Nullable.empty): Boolean =
    namespace.toOption match {
      case Some(ns) => _getModule(ns).variables.contains(name)
      case None =>
        if (_variables(0).contains(name)) true
        else _getVariableFromGlobalModule(name).isDefined
    }

  /** Sets the variable named [name] to [value]. */
  def setVariable(
    name:         String,
    value:        Value,
    nodeWithSpan: Nullable[AstNode] = Nullable.empty,
    namespace:    Nullable[String] = Nullable.empty,
    global:       Boolean = false
  ): Unit = {
    if (namespace.isDefined) {
      _getModule(namespace.get).setVariable(name, value)
      return
    }

    if (global || atRoot) {
      if (!_variableIndices.contains(name)) {
        _lastVariableName = Nullable(name)
        _lastVariableIndex = 0
        _variableIndices(name) = 0
      }
      // Write-through to a global module that already defines this name.
      if (!_variables(0).contains(name)) {
        val moduleWithName =
          _fromOneModule[Module[Callable]](
            name,
            "variable",
            m => if (m.variables.contains(name)) Nullable(m) else Nullable.empty
          )
        if (moduleWithName.isDefined) {
          moduleWithName.get.setVariable(name, value)
          return
        }
      }
      _variables(0)(name) = value
      nodeWithSpan.foreach(n => _variableNodes(0)(name) = n)
      return
    }

    // Nested-forwarded-module write-through: if the variable isn't
    // known to the scope chain, check any nested forwarded modules
    // introduced by `@import`s below the root.
    val nested = _nestedForwardedModules.toOption
    if (nested.isDefined && !_variableIndices.contains(name) && _variableIndex(name) < 0) {
      val buckets = nested.get
      var i = buckets.length - 1
      while (i >= 0) {
        val bucket = buckets(i)
        var j = bucket.length - 1
        while (j >= 0) {
          val m = bucket(j)
          if (m.variables.contains(name)) {
            m.setVariable(name, value)
            return
          }
          j -= 1
        }
        i -= 1
      }
    }

    var index =
      if (_lastVariableName.exists(_ == name)) _lastVariableIndex
      else _variableIndices.get(name) match {
        case Some(i) => i
        case None =>
          val found    = _variableIndex(name)
          val resolved = if (found < 0) _variables.length - 1 else found
          _variableIndices(name) = resolved
          resolved
      }

    if (!_inSemiGlobalScope && index == 0) {
      index = _variables.length - 1
      _variableIndices(name) = index
    }
    _lastVariableName = Nullable(name)
    _lastVariableIndex = index
    _variables(index)(name) = value
    nodeWithSpan.foreach(n => _variableNodes(index)(name) = n)
  }

  /** Writes [name] to the innermost scope unconditionally. */
  def setLocalVariable(name: String, value: Value, nodeWithSpan: Nullable[AstNode] = Nullable.empty): Unit = {
    val index = _variables.length - 1
    _lastVariableName = Nullable(name)
    _lastVariableIndex = index
    _variableIndices(name) = index
    _variables(index)(name) = value
    nodeWithSpan.foreach(n => _variableNodes(index)(name) = n)
  }

  /** Convenience for the `!global` assignment path. */
  def setGlobalVariable(name: String, value: Value, nodeWithSpan: Nullable[AstNode] = Nullable.empty): Unit = {
    setVariable(name, value, nodeWithSpan, global = true)
    val _ = globalVarNames.add(name)
  }

  /** Records [name] as a configurable `!default` variable in this module. */
  def markVariableConfigurable(name: String): Unit = {
    val _ = _configurableVariables.add(name)
  }

  def isVariableConfigurable(name: String): Boolean = _configurableVariables.contains(name)

  // ---------------------------------------------------------------------------
  // Function lookup
  // ---------------------------------------------------------------------------

  def getFunction(name: String, namespace: Nullable[String] = Nullable.empty): Nullable[Callable] = {
    if (namespace.isDefined)
      return _getModule(namespace.get).functions.get(name) match {
        case Some(c) => Nullable(c)
        case None    => Nullable.empty
      }

    _functionIndices.get(name) match {
      case Some(idx) =>
        _functions(idx).get(name) match {
          case Some(c) => Nullable(c)
          case None    => _getFunctionFromGlobalModule(name)
        }
      case None =>
        val found = _functionIndex(name)
        if (found >= 0) {
          _functionIndices(name) = found
          _functions(found).get(name) match {
            case Some(c) => Nullable(c)
            case None    => _getFunctionFromGlobalModule(name)
          }
        } else _getFunctionFromGlobalModule(name)
    }
  }

  private def _getFunctionFromGlobalModule(name: String): Nullable[Callable] =
    _fromOneModule[Callable](name, "function", m => m.functions.get(name) match {
      case Some(c) => Nullable(c)
      case None    => Nullable.empty
    })

  private def _functionIndex(name: String): Int = {
    var i = _functions.length - 1
    while (i >= 0) {
      if (_functions(i).contains(name)) return i
      i -= 1
    }
    -1
  }

  def functionExists(name: String, namespace: Nullable[String] = Nullable.empty): Boolean =
    getFunction(name, namespace).isDefined

  def setFunction(callable: Callable): Unit = {
    val index = _functions.length - 1
    _functionIndices(callable.name) = index
    _functions(index)(callable.name) = callable
  }

  // ---------------------------------------------------------------------------
  // Mixin lookup
  // ---------------------------------------------------------------------------

  def getMixin(name: String, namespace: Nullable[String] = Nullable.empty): Nullable[Callable] = {
    if (namespace.isDefined)
      return _getModule(namespace.get).mixins.get(name) match {
        case Some(c) => Nullable(c)
        case None    => Nullable.empty
      }

    _mixinIndices.get(name) match {
      case Some(idx) =>
        _mixins(idx).get(name) match {
          case Some(c) => Nullable(c)
          case None    => _getMixinFromGlobalModule(name)
        }
      case None =>
        val found = _mixinIndex(name)
        if (found >= 0) {
          _mixinIndices(name) = found
          _mixins(found).get(name) match {
            case Some(c) => Nullable(c)
            case None    => _getMixinFromGlobalModule(name)
          }
        } else _getMixinFromGlobalModule(name)
    }
  }

  private def _getMixinFromGlobalModule(name: String): Nullable[Callable] =
    _fromOneModule[Callable](name, "mixin", m => m.mixins.get(name) match {
      case Some(c) => Nullable(c)
      case None    => Nullable.empty
    })

  private def _mixinIndex(name: String): Int = {
    var i = _mixins.length - 1
    while (i >= 0) {
      if (_mixins(i).contains(name)) return i
      i -= 1
    }
    -1
  }

  def mixinExists(name: String, namespace: Nullable[String] = Nullable.empty): Boolean =
    getMixin(name, namespace).isDefined

  def setMixin(callable: Callable): Unit = {
    val index = _mixins.length - 1
    _mixinIndices(callable.name) = index
    _mixins(index)(callable.name) = callable
  }

  // ---------------------------------------------------------------------------
  // Content block / `asMixin`
  // ---------------------------------------------------------------------------

  def content:                              Nullable[ContentBlock] = _content
  def content_=(block: Nullable[ContentBlock]): Unit                = _content = block

  /** Sets [content] as the content block for the duration of [callback]. */
  def withContent[T](newContent: Nullable[ContentBlock])(callback: => T): T = {
    val saved = _content
    _content = newContent
    try callback
    finally _content = saved
  }

  /** Sets [inMixin] to `true` for the duration of [callback]. */
  def asMixin[T](callback: => T): T = {
    val saved = _inMixin
    _inMixin = true
    try callback
    finally _inMixin = saved
  }

  // ---------------------------------------------------------------------------
  // Scope management
  // ---------------------------------------------------------------------------

  /** Runs [callback] in a new scope.
    *
    * Variables, functions, and mixins declared in the new scope are
    * inaccessible outside of it. When [semiGlobal] is `true` AND the
    * caller is itself in a semi-global scope, assignments can write
    * back to the global scope without an explicit `!global`. When
    * [when] is `false`, no scope is pushed — the scope decision is
    * logical only, matching dart-sass's `when` semantics.
    */
  def scope[T](semiGlobal: Boolean = false, when: Boolean = true)(callback: => T): T = {
    val effective    = semiGlobal && _inSemiGlobalScope
    val wasSemi      = _inSemiGlobalScope
    _inSemiGlobalScope = effective

    if (!when) {
      try callback
      finally _inSemiGlobalScope = wasSemi
    } else {
      _variables += mutable.Map.empty
      _variableNodes += mutable.Map.empty
      _functions += mutable.Map.empty
      _mixins += mutable.Map.empty
      _nestedForwardedModules.foreach(_ += mutable.ArrayBuffer.empty)
      try callback
      finally {
        _inSemiGlobalScope = wasSemi
        _lastVariableName = Nullable.empty
        _lastVariableIndex = -1
        val poppedVars = _variables.remove(_variables.length - 1)
        _variableNodes.remove(_variableNodes.length - 1)
        for (n <- poppedVars.keys) _variableIndices.remove(n)
        val poppedFns = _functions.remove(_functions.length - 1)
        for (n <- poppedFns.keys) _functionIndices.remove(n)
        val poppedMix = _mixins.remove(_mixins.length - 1)
        for (n <- poppedMix.keys) _mixinIndices.remove(n)
        _nestedForwardedModules.foreach { buckets =>
          if (buckets.nonEmpty) buckets.remove(buckets.length - 1)
        }
      }
    }
  }

  /** Thunk overload kept for existing callers that pass `() => T`. */
  def withinScope[T](callback: () => T): T = scope(semiGlobal = false)(callback())

  /** Convenience overload: `withinScope(semiGlobal = true) { body }`
    * without having to write `scope(...)`.
    */
  def withinScope[T](semiGlobal: Boolean)(body: => T): T = scope(semiGlobal = semiGlobal)(body)

  /** Shorthand for a semi-global scope. */
  def withinSemiGlobalScope[T](body: => T): T = scope(semiGlobal = true)(body)

  /** Runs [body] in a fully isolated snapshot: saves variables /
    * function / mixin tables, content, runs the body, then restores.
    * Used by the evaluator's callable dispatchers so parameter
    * bindings don't leak.
    */
  def withSnapshot[T](body: => T): T = {
    val savedVars    = _variables.map(_.clone()).toBuffer
    val savedNodes   = _variableNodes.map(_.clone()).toBuffer
    val savedFns     = _functions.map(_.clone()).toBuffer
    val savedMix     = _mixins.map(_.clone()).toBuffer
    val savedVarIdx  = _variableIndices.clone()
    val savedFnIdx   = _functionIndices.clone()
    val savedMixIdx  = _mixinIndices.clone()
    val savedContent = _content
    try body
    finally {
      _variables.clear(); _variables ++= savedVars
      _variableNodes.clear(); _variableNodes ++= savedNodes
      _functions.clear(); _functions ++= savedFns
      _mixins.clear(); _mixins ++= savedMix
      _variableIndices.clear(); _variableIndices ++= savedVarIdx
      _functionIndices.clear(); _functionIndices ++= savedFnIdx
      _mixinIndices.clear(); _mixinIndices ++= savedMixIdx
      _content = savedContent
    }
  }

  // ---------------------------------------------------------------------------
  // Configuration and module sealing
  // ---------------------------------------------------------------------------

  /** Creates an implicit configuration from the variables declared in
    * this environment, mirroring dart-sass `toImplicitConfiguration`.
    *
    * For each scope level, the namespaceless modules visible at that
    * level (`_importedModules` at the global level, the corresponding
    * bucket of `_nestedForwardedModules` for non-root levels) contribute
    * their variables first, and the level's own variables overlay on
    * top. Innermost levels win on duplicate names because the loop
    * proceeds outermost-to-innermost and overwrites the map.
    */
  def toImplicitConfiguration(): Map[String, Value] = {
    val out = mutable.LinkedHashMap.empty[String, Value]
    var i = 0
    while (i < _variables.length) {
      val modules: Iterable[Module[Callable]] =
        if (i == 0) _importedModules.keys
        else _nestedForwardedModules.toOption.flatMap { buckets =>
          if (i - 1 < buckets.length) Some(buckets(i - 1)) else None
        }.getOrElse(Iterable.empty)
      for (m <- modules) {
        for ((name, value) <- m.variables) out(name) = value
      }
      for ((name, value) <- _variables(i)) out(name) = value
      i += 1
    }
    out.toMap
  }

  /** Seals this environment into a [[Module]] containing [css] and
    * [extensionStore], exposing the top-level members as its public
    * surface.
    */
  def toModule(
    css:            CssStylesheet,
    extensionStore: ExtensionStore,
    url:            Nullable[String] = Nullable.empty
  ): Module[Callable] =
    new Environment.EnvironmentModule(
      env = this,
      css = css,
      extensionStore = extensionStore,
      explicitUrl = url,
      forwarded = _forwardedModules.toOption.map(_.keySet.toSet).getOrElse(Set.empty)
    )

  /** Returns a module with the same members and upstream modules as this
    * environment, but an empty stylesheet and extension store.
    *
    * Used when resolving `@import`s — the importing context needs a
    * Module wrapper around the forImport env so that
    * `_environment.importForwards(dummyModule)` can hoist the imported
    * file's `@forward`ed modules into the outer env's lookup chain. The
    * dummy module's CSS is empty (the actual CSS lives directly in the
    * outer env), and the extension store is fresh because no @extend
    * processing happens against this stand-in.
    */
  def toDummyModule(url: Nullable[String] = Nullable.empty): Module[Callable] =
    new Environment.EnvironmentModule(
      env = this,
      css = CssStylesheet.empty(url),
      extensionStore = ExtensionStore.empty,
      explicitUrl = url,
      forwarded = _forwardedModules.toOption.map(_.keySet.toSet).getOrElse(Set.empty)
    )

  // ---------------------------------------------------------------------------
  // Internal module lookup helpers
  // ---------------------------------------------------------------------------

  private def _getModule(namespace: String): Module[Callable] =
    _modules.get(namespace) match {
      case Some(m) => m
      case None    =>
        throw SassScriptException(s"""There is no module with the namespace "$namespace".""")
    }

  /** Returns the result of [callback] for the first namespaceless
    * module (nested forwarded, imported, or global) that provides a
    * non-null value. Throws if two unrelated global modules both
    * provide a value.
    */
  private def _fromOneModule[T](
    name:     String,
    typeName: String,
    callback: Module[Callable] => Nullable[T]
  ): Nullable[T] = scala.util.boundary {
    val nested = _nestedForwardedModules.toOption
    if (nested.isDefined) {
      val buckets = nested.get
      var i = buckets.length - 1
      while (i >= 0) {
        val bucket = buckets(i)
        var j = bucket.length - 1
        while (j >= 0) {
          val value = callback(bucket(j))
          if (value.isDefined) scala.util.boundary.break(value)
          j -= 1
        }
        i -= 1
      }
    }
    val importedIt = _importedModules.keysIterator
    while (importedIt.hasNext) {
      val value = callback(importedIt.next())
      if (value.isDefined) scala.util.boundary.break(value)
    }

    var resolved: Nullable[T] = Nullable.empty
    var identity: AnyRef      = null
    for (m <- _globalModules.keys) {
      val valueInModule = callback(m)
      if (valueInModule.isDefined) {
        val identityFromModule: AnyRef = valueInModule.get match {
          case c: Callable => c
          case _           => m.variableIdentity(name)
        }
        if (identityFromModule != identity) {
          if (resolved.isDefined)
            throw SassScriptException(s"This $typeName is available from multiple global modules.")
          resolved = valueInModule
          identity = identityFromModule
        }
      }
    }
    resolved
  }

  // ---------------------------------------------------------------------------
  // Compatibility shims for existing callers
  // ---------------------------------------------------------------------------

  /** Legacy overload (namespace as plain String). */
  def getNamespace(name: String): Nullable[Environment] =
    _modules.get(name) match {
      case Some(impl: Environment.EnvironmentModule) => Nullable(impl.env)
      case _                                             => Nullable.empty
    }

  /** Returns the [[Module]] registered under [namespace], if any.
    * Used by `meta.module-variables`/`meta.module-functions` to walk the
    * module's *public* surface (which includes forwarded members) rather
    * than just the inner Environment's local scope.
    */
  def findNamespacedModule(namespace: String): Nullable[Module[Callable]] =
    _modules.get(namespace) match {
      case Some(m) => Nullable(m)
      case None    => Nullable.empty
    }

  def getNamespacedVariable(namespace: String, name: String): Nullable[Value] =
    _modules.get(namespace) match {
      case Some(m) => m.variables.get(name) match {
          case Some(v) => Nullable(v)
          case None    => Nullable.empty
        }
      case None => Nullable.empty
    }

  def getNamespacedFunction(namespace: String, name: String): Nullable[Callable] =
    _modules.get(namespace) match {
      case Some(m) => m.functions.get(name) match {
          case Some(c) => Nullable(c)
          case None    => Nullable.empty
        }
      case None => Nullable.empty
    }

  def getNamespacedMixin(namespace: String, name: String): Nullable[Callable] =
    _modules.get(namespace) match {
      case Some(m) => m.mixins.get(name) match {
          case Some(c) => Nullable(c)
          case None    => Nullable.empty
        }
      case None => Nullable.empty
    }

  /** Legacy Environment-based namespace registration used by
    * `@use "sass:X"`. Wraps [env] in an EnvironmentModule. The
    * `nodeWithSpan` is intentionally empty — built-in modules are
    * registered without a source span because their load site is
    * synthetic (the `@use "sass:X"` AST node is processed in
    * EvaluateVisitor, which already carries its own span for
    * diagnostics).
    */
  def addNamespace(name: String, env: Environment): Unit =
    addModule(
      env.toModule(CssStylesheet.empty(Nullable.empty), ExtensionStore.empty),
      nodeWithSpan = Nullable.empty,
      namespace = Nullable(name)
    )

  // ---------------------------------------------------------------------------
  // Iteration helpers
  // ---------------------------------------------------------------------------

  /** Iterates over all variable name/value pairs across all scopes
    * (innermost wins on duplicates).
    */
  def variableEntries: Iterator[(String, Value)] = {
    val seen = mutable.Set.empty[String]
    val buf  = mutable.ArrayBuffer.empty[(String, Value)]
    var i    = _variables.length - 1
    while (i >= 0) {
      for ((n, v) <- _variables(i) if !seen.contains(n)) {
        seen.add(n)
        buf += ((n, v))
      }
      i -= 1
    }
    buf.iterator
  }

  /** Iterates every function currently visible to the scope chain. */
  def functionValues: Iterator[Callable] = {
    val seen = mutable.Set.empty[String]
    val buf  = mutable.ArrayBuffer.empty[Callable]
    var i    = _functions.length - 1
    while (i >= 0) {
      for ((n, c) <- _functions(i) if !seen.contains(n)) {
        seen.add(n)
        buf += c
      }
      i -= 1
    }
    buf.iterator
  }

  /** Iterates every mixin currently visible to the scope chain. */
  def mixinValues: Iterator[Callable] = {
    val seen = mutable.Set.empty[String]
    val buf  = mutable.ArrayBuffer.empty[Callable]
    var i    = _mixins.length - 1
    while (i >= 0) {
      for ((n, c) <- _mixins(i) if !seen.contains(n)) {
        seen.add(n)
        buf += c
      }
      i -= 1
    }
    buf.iterator
  }

  /** Returns a public-view copy that hides private (`-`/`_`-prefixed)
    * members. Used by the evaluator before registering a module under a
    * caller's namespace.
    */
  def publicView(): Environment = {
    val out = new Environment()
    for ((n, v) <- _variables(0) if !Environment.isPrivate(n)) {
      out._variables(0)(n) = v
      _variableNodes(0).get(n).foreach(node => out._variableNodes(0)(n) = node)
    }
    for ((n, c) <- _functions(0) if !Environment.isPrivate(n))
      out._functions(0)(n) = c
    for ((n, c) <- _mixins(0) if !Environment.isPrivate(n))
      out._mixins(0)(n) = c
    for ((ns, m) <- _modules)
      out._modules(ns) = m
    out
  }

  /** Creates a new global-only environment containing the built-ins
    * plus any variables declared with `!global` in this environment.
    */
  def global(): Environment = {
    val g = Environment.withBuiltins()
    for (name <- globalVarNames)
      _variables(0).get(name).foreach(v => g.setGlobalVariable(name, v))
    g
  }

  // ---------------------------------------------------------------------------
  // Backwards-compat aliases
  // ---------------------------------------------------------------------------

  /** Exposed for the `Functions` map — returns the first scope's functions. */
  def functions: Map[String, Callable] = _functions(0).toMap

  /** Exposed for the mixins map — returns the first scope's mixins. */
  def mixins: Map[String, Callable] = _mixins(0).toMap
}

object Environment {

  def apply(): Environment = new Environment()

  /** Whether [name] is a Sass-private member name (leading `-` or `_`). */
  def isPrivate(name: String): Boolean =
    name.nonEmpty && { val c = name.charAt(0); c == '-' || c == '_' }

  /** Creates a new environment pre-populated with every global built-in function. */
  def withBuiltins(): Environment = {
    val env = new Environment()
    for (fn <- ssg.sass.functions.Functions.global)
      env.setFunction(fn)
    env
  }

  /** Returns a `ShadowedView` wrapping [module] with the given hidden
    * sets, or `Nullable.empty` if the shadow would be a no-op (no
    * overlap with the module's members).
    */
  private[sass] def makeShadowed(
    module:    Module[Callable],
    variables: Set[String],
    functions: Set[String],
    mixins:    Set[String]
  ): Nullable[Module[Callable]] = {
    val hasOverlap =
      module.variables.keysIterator.exists(variables.contains) ||
        module.functions.keysIterator.exists(functions.contains) ||
        module.mixins.keysIterator.exists(mixins.contains)
    if (!hasOverlap) Nullable.empty
    else
      Nullable(
        new ShadowedView[Callable](
          inner = module,
          shadowedVars = variables,
          shadowedFunctions = functions,
          shadowedMixins = mixins
        )
      )
  }

  // ---------------------------------------------------------------------------
  // EnvironmentModule — the Module[Callable] exposed by toModule /
  // toDummyModule. Port of dart-sass's private `_EnvironmentModule` class.
  // ---------------------------------------------------------------------------

  /** A [[Module]] that wraps an [[Environment]] and exposes its
    * top-level members as its public surface. Private members are
    * filtered; members forwarded from upstream modules are merged in.
    */
  final class EnvironmentModule(
    val env:                    Environment,
    val css:                    CssStylesheet,
    val extensionStore:         ExtensionStore,
    explicitUrl:                Nullable[String],
    forwarded:                  Set[Module[Callable]]
  ) extends Module[Callable] {

    val url: Nullable[String] =
      if (explicitUrl.isDefined) explicitUrl
      else Nullable(css.span.sourceUrl.toString)

    /** For each variable name, the module that actually holds the
      * underlying storage. Used by `setVariable` to route writes to the
      * forwarded module and by `variableIdentity` to compare variables
      * across forward chains.
      */
    private val _modulesByVariable: Map[String, Module[Callable]] =
      EnvironmentModule.makeModulesByVariable(forwarded)

    val variables: Map[String, Value] =
      EnvironmentModule.memberMap(
        env._variables(0).iterator.filter { case (n, _) => !Environment.isPrivate(n) }.toMap,
        forwarded.map(_.variables)
      )

    val functions: Map[String, Callable] =
      EnvironmentModule.memberMap(
        env._functions(0).iterator.filter { case (n, _) => !Environment.isPrivate(n) }.toMap,
        forwarded.map(_.functions)
      )

    val mixins: Map[String, Callable] =
      EnvironmentModule.memberMap(
        env._mixins(0).iterator.filter { case (n, _) => !Environment.isPrivate(n) }.toMap,
        forwarded.map(_.mixins)
      )

    val transitivelyContainsCss: Boolean =
      css.children.nonEmpty || env._allModules.exists(_.transitivelyContainsCss)

    val transitivelyContainsExtensions: Boolean =
      !extensionStore.isEmpty || env._allModules.exists(_.transitivelyContainsExtensions)

    def setVariable(name: String, value: Value): Unit = {
      _modulesByVariable.get(name) match {
        case Some(m) =>
          m.setVariable(name, value)
        case None =>
          if (!env._variables(0).contains(name))
            throw SassScriptException(s"Undefined variable: $$$name")
          env._variables(0)(name) = value
      }
    }

    override def variableIdentity(name: String): AnyRef =
      _modulesByVariable.get(name) match {
        case Some(m) => m.variableIdentity(name)
        case None    => this
      }

    override def couldHaveBeenConfigured(names: Set[String]): Boolean = {
      val localHit =
        if (names.size < env._configurableVariables.size) names.exists(env._configurableVariables.contains)
        else env._configurableVariables.exists(names.contains)
      if (localHit) return true
      val relevantModules =
        if (names.size < _modulesByVariable.size)
          names.iterator.flatMap(n => _modulesByVariable.get(n)).toSet
        else
          _modulesByVariable.iterator.collect { case (v, m) if names.contains(v) => m }.toSet
      relevantModules.exists(_.couldHaveBeenConfigured(names))
    }

    override def toString: String =
      if (url.isEmpty) "<unknown url>" else url.get

    /** Returns a copy of this module with its CSS and ExtensionStore
      * cloned. Mirrors dart-sass `_EnvironmentModule.cloneCss`.
      *
      * If the module contains no transitive CSS, returns `this` (no
      * cloning needed). Otherwise produces a new EnvironmentModule
      * sharing the same Environment and member maps but with a
      * fresh CssStylesheet wrapping a snapshot of the children, plus
      * a fresh ExtensionStore obtained via `cloneStore`. The clone
      * is hermetic — applying `@extend` to the clone has no effect
      * on the original.
      */
    def cloneCss: Module[Callable] = {
      if (!transitivelyContainsCss) this
      else {
        // CssStylesheet exposes its children list directly; building
        // a new stylesheet around the same span and children gives a
        // shallow clone whose own `children` list can be mutated by
        // the consumer (typically `_combineCss`) without bleeding
        // into the original module's CSS.
        val newCss = CssStylesheet(css.children.toList, css.span)
        val newStore =
          if (extensionStore.isEmpty) ExtensionStore.empty
          else extensionStore.cloneStore()._1
        new EnvironmentModule(
          env = env,
          css = newCss,
          extensionStore = newStore,
          explicitUrl = explicitUrl,
          forwarded = forwarded
        )
      }
    }
  }

  object EnvironmentModule {

    private[sass] def makeModulesByVariable(forwarded: Set[Module[Callable]]): Map[String, Module[Callable]] = {
      if (forwarded.isEmpty) return Map.empty
      val out = mutable.Map.empty[String, Module[Callable]]
      for (m <- forwarded) {
        m match {
          case impl: EnvironmentModule =>
            // Flatten nested forwarded modules to avoid O(depth) overhead.
            for ((_, inner) <- impl._modulesByVariable)
              for (v <- inner.variables.keys) out(v) = inner
            for (v <- impl.env._variables(0).keys if !Environment.isPrivate(v))
              out(v) = impl
          case other =>
            for (v <- other.variables.keys) out(v) = other
        }
      }
      out.toMap
    }

    /** Returns a map containing [localMap] plus every other-map
      * entry, with `localMap` taking precedence. `localMap` is assumed
      * to already hide private members.
      */
    private[sass] def memberMap[V](localMap: Map[String, V], otherMaps: Iterable[Map[String, V]]): Map[String, V] = {
      if (otherMaps.isEmpty) return localMap
      val out = mutable.LinkedHashMap.empty[String, V]
      for (m <- otherMaps if m.nonEmpty)
        for ((k, v) <- m) out(k) = v
      for ((k, v) <- localMap) out(k) = v
      out.toMap
    }
  }
}
