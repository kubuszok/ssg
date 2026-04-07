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
 *   Convention: Scope chain + closures + !global + semi-global tracking ported
 *               from dart-sass. Module/forward/namespace machinery remains a
 *               simplified shim (the deferred 40%).
 */
package ssg
package sass

import scala.annotation.nowarn
import scala.collection.mutable
import scala.language.implicitConversions
import ssg.sass.ast.AstNode
import ssg.sass.ast.sass.ContentBlock
import ssg.sass.value.Value

/** The lexical environment in which Sass code is evaluated.
  *
  * Tracks variables via a scope chain (innermost scope at the tail of the list, global scope at the head — matching dart-sass `_variables[0]` is global). Functions and mixins are stored flat for now;
  * only variables use the chain.
  */
final class Environment private (
  // variables[0] is global; last element is innermost/local scope.
  private val _variables:       mutable.ArrayBuffer[mutable.Map[String, Value]],
  private val _variableNodes:   mutable.ArrayBuffer[mutable.Map[String, AstNode]],
  private val _variableIndices: mutable.Map[String, Int],
  // Flat function / mixin tables (module machinery deferred).
  private val functions:  mutable.Map[String, Callable],
  private val mixins:     mutable.Map[String, Callable],
  private val namespaces: mutable.Map[String, Environment],
  // Names declared with `!global`, used by `global()` snapshots.
  private val globalVarNames:     mutable.Set[String],
  private var _content:           Nullable[ContentBlock],
  private var _inSemiGlobalScope: Boolean
) {

  // --- Stage 4A: module-system storage fields (unused until 4B–4E) ----------
  // Mirror dart-sass `Environment` field layout. Initialized empty; legacy
  // `namespaces: Map[String, Environment]` continues to back lookups for now.
  private val _modules:                mutable.Map[String, EnvironmentModule]  = mutable.Map.empty
  private val _namespaceNodes:         mutable.Map[String, AstNode]            = mutable.Map.empty
  private val _globalModules:          mutable.Map[EnvironmentModule, AstNode] = mutable.LinkedHashMap.empty
  @nowarn("msg=unused private member") private val _importedModules:        mutable.Map[EnvironmentModule, AstNode] = mutable.LinkedHashMap.empty
  @nowarn("msg=unused private member") private val _forwardedModules:       mutable.Map[EnvironmentModule, AstNode] = mutable.LinkedHashMap.empty
  @nowarn("msg=unused private member") private val _nestedForwardedModules: mutable.ArrayBuffer[mutable.ArrayBuffer[EnvironmentModule]] = mutable.ArrayBuffer.empty
  private val _allModules:             mutable.ArrayBuffer[EnvironmentModule]  = mutable.ArrayBuffer.empty
  @nowarn("msg=unused private member") private val _configurableVariables:  mutable.Set[String]                     = mutable.Set.empty


  def this() = this(
    _variables = mutable.ArrayBuffer(mutable.Map.empty[String, Value]),
    _variableNodes = mutable.ArrayBuffer(mutable.Map.empty[String, AstNode]),
    _variableIndices = mutable.Map.empty[String, Int],
    functions = mutable.Map.empty[String, Callable],
    mixins = mutable.Map.empty[String, Callable],
    namespaces = mutable.Map.empty[String, Environment],
    globalVarNames = mutable.Set.empty[String],
    _content = Nullable.empty,
    _inSemiGlobalScope = true
  )

  /** True when this environment is at the root (only the global scope exists). */
  def atRoot: Boolean = _variables.length == 1

  // --- Namespace shims (deferred module port) --------------------------------

  def addNamespace(name: String, module: Environment): Unit = {
    assertNoConflicts(name)
    namespaces(name) = module
    _modules(name) = EnvironmentModule(module)
    _allModules += _modules(name)
  }

  /** Registers [module] under [namespace]. If [namespace] is empty, the module becomes a global (unnamespaced) import visible to all member lookups.
    *
    * Port of dart-sass `Environment.addModule`. Currently accepts already-wrapped [[EnvironmentModule]]s for integration with Stage 4E.
    */
  def addModule(module: EnvironmentModule, nodeWithSpan: AstNode, namespace: Nullable[String] = Nullable.empty): Unit = {
    namespace.toOption match {
      case Some(ns) =>
        assertNoConflicts(ns)
        _modules(ns) = module
        _namespaceNodes(ns) = nodeWithSpan
        namespaces(ns) = module.env
      case None =>
        _globalModules(module) = nodeWithSpan
    }
    _allModules += module
  }

  /** Looks up a module registered under [namespace], checking both the new `_modules` map and the legacy `namespaces` map. */
  private def _getModule(namespace: String): Nullable[EnvironmentModule] =
    _modules.get(namespace) match {
      case Some(m) => Nullable(m)
      case None    =>
        namespaces.get(namespace) match {
          case Some(env) => Nullable(EnvironmentModule(env))
          case None      => Nullable.empty
        }
    }

  /** Raises a clean error if [namespace] is already in use. */
  private def assertNoConflicts(namespace: String): Unit = {
    if (_modules.contains(namespace) || namespaces.contains(namespace)) {
      throw new IllegalStateException(s"There's already a module with namespace \"$namespace\".")
    }
  }

  def getNamespacedVariable(namespace: String, name: String): Nullable[Value] =
    _getModule(namespace).toOption match {
      case Some(m) => m.env.getVariable(name)
      case None    => Nullable.empty
    }

  def getNamespacedFunction(namespace: String, name: String): Nullable[Callable] =
    _getModule(namespace).toOption match {
      case Some(m) => m.env.getFunction(name)
      case None    => Nullable.empty
    }

  def getNamespacedMixin(namespace: String, name: String): Nullable[Callable] =
    _getModule(namespace).toOption match {
      case Some(m) => m.env.getMixin(name)
      case None    => Nullable.empty
    }

  def getNamespace(name: String): Nullable[Environment] =
    namespaces.get(name) match {
      case Some(env)  => Nullable(env)
      case scala.None => Nullable.empty
    }

  // --- Iteration helpers -----------------------------------------------------

  /** Iterates over all variable name/value pairs across all scopes (innermost wins on duplicates). */
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

  def functionValues: Iterator[Callable] = functions.valuesIterator
  def mixinValues:    Iterator[Callable] = mixins.valuesIterator

  // --- Variable lookup -------------------------------------------------------

  /** Returns the index of the innermost scope containing [name], or -1. */
  private def _variableIndex(name: String): Int = {
    var i = _variables.length - 1
    while (i >= 0) {
      if (_variables(i).contains(name)) return i
      i -= 1
    }
    -1
  }

  /** Returns the value of the variable named [name], walking scopes innermost-out. */
  def getVariable(name: String): Nullable[Value] = {
    val cached = _variableIndices.get(name)
    val idx    = cached match {
      case Some(i) if i < _variables.length && _variables(i).contains(name) => i
      case _                                                                =>
        val found = _variableIndex(name)
        if (found >= 0) _variableIndices(name) = found
        found
    }
    if (idx < 0) Nullable.empty
    else
      _variables(idx).get(name) match {
        case Some(v) => v
        case None    => Nullable.empty
      }
  }

  /** Sets the variable named [name] to [value].
    *
    * When `global` is true, writes to the global scope (index 0). Otherwise, if a scope already holds `name`, overwrites that scope (dart-sass semantics: assignments in a non-semi-global scope to a
    * variable that only exists at the global scope create a new local binding instead). If no scope holds it, writes to the innermost scope.
    */
  def setVariable(
    name:         String,
    value:        Value,
    nodeWithSpan: Nullable[AstNode] = Nullable.empty,
    global:       Boolean = false
  ): Unit = {
    if (global || atRoot) {
      _variables(0)(name) = value
      nodeWithSpan.foreach(n => _variableNodes(0)(name) = n)
      _variableIndices.getOrElseUpdate(name, 0)
      return
    }
    var index =
      if (_variableIndices.contains(name)) _variableIndices(name)
      else {
        val found    = _variableIndex(name)
        val resolved = if (found < 0) _variables.length - 1 else found
        _variableIndices(name) = resolved
        resolved
      }
    // If we only have it at the global scope and we're not in a semi-global
    // scope, shadow it locally instead of writing through.
    if (!_inSemiGlobalScope && index == 0 && !atRoot) {
      index = _variables.length - 1
      _variableIndices(name) = index
    }
    _variables(index)(name) = value
    nodeWithSpan.foreach(n => _variableNodes(index)(name) = n)
  }

  /** Convenience for the common `!global` assignment path. */
  def setGlobalVariable(name: String, value: Value, nodeWithSpan: Nullable[AstNode] = Nullable.empty): Unit = {
    setVariable(name, value, nodeWithSpan, global = true)
    val _ = globalVarNames.add(name)
  }

  /** Writes to the innermost scope unconditionally, shadowing any outer binding. */
  def setLocalVariable(name: String, value: Value, nodeWithSpan: Nullable[AstNode] = Nullable.empty): Unit = {
    val index = _variables.length - 1
    _variables(index)(name) = value
    nodeWithSpan.foreach(n => _variableNodes(index)(name) = n)
    _variableIndices(name) = index
  }

  def variableExists(name: String): Boolean = _variableIndex(name) >= 0

  def globalVariableExists(name: String): Boolean = _variables(0).contains(name)

  // --- Functions / mixins (flat, module-free) --------------------------------

  def functionExists(name: String): Boolean = functions.contains(name)
  def mixinExists(name:    String): Boolean = mixins.contains(name)

  def getFunction(name: String): Nullable[Callable] =
    functions.get(name) match {
      case Some(c)    => c
      case scala.None => Nullable.empty
    }

  def setFunction(callable: Callable): Unit =
    functions(callable.name) = callable

  def getMixin(name: String): Nullable[Callable] =
    mixins.get(name) match {
      case Some(c)    => c
      case scala.None => Nullable.empty
    }

  def setMixin(callable: Callable): Unit =
    mixins(callable.name) = callable

  // --- Scope management ------------------------------------------------------

  /** Runs [body] in a new lexical scope. If [semiGlobal] is true and we're already in a semi-global scope, the new scope can assign to globals without `!global`. Matches dart-sass `scope()`
    * propagation.
    */
  def withinScope[T](semiGlobal: Boolean)(body: => T): T = {
    val effective = semiGlobal && _inSemiGlobalScope
    val wasSemi   = _inSemiGlobalScope
    _inSemiGlobalScope = effective
    _variables += mutable.Map.empty
    _variableNodes += mutable.Map.empty
    try body
    finally {
      _inSemiGlobalScope = wasSemi
      val popped = _variables.remove(_variables.length - 1)
      val _      = _variableNodes.remove(_variableNodes.length - 1)
      for (name <- popped.keys) {
        val _ = _variableIndices.remove(name)
      }
    }
  }

  /** Legacy shim: pre-port API — non-semi-global nested scope. */
  def withinScope[T](callback: () => T): T = withinScope(semiGlobal = false)(callback())

  /** Shorthand for a semi-global scope (e.g. `@if`/`@for`/`@each`/`@while`). */
  def withinSemiGlobalScope[T](body: => T): T = withinScope(semiGlobal = true)(body)

  /** Runs [body] in a fully isolated scope: saves a snapshot of variables, functions, and mixins, runs the body, then restores.
    *
    * Kept for callers that invoke user-defined callables and need to prevent parameter bindings from leaking.
    */
  def withSnapshot[T](body: => T): T = {
    val savedVars    = _variables.map(_.clone()).toBuffer
    val savedNodes   = _variableNodes.map(_.clone()).toBuffer
    val savedIndices = _variableIndices.clone()
    val savedFns     = functions.clone()
    val savedMix     = mixins.clone()
    val savedContent = _content
    try body
    finally {
      _variables.clear(); _variables ++= savedVars
      _variableNodes.clear(); _variableNodes ++= savedNodes
      _variableIndices.clear(); _variableIndices ++= savedIndices
      functions.clear(); functions ++= savedFns
      mixins.clear(); mixins ++= savedMix
      _content = savedContent
    }
  }

  // --- Content block ---------------------------------------------------------

  def content:                                  Nullable[ContentBlock] = _content
  def content_=(block: Nullable[ContentBlock]): Unit                   = _content = block

  // --- Closures --------------------------------------------------------------

  /** Creates a closure: a new Environment whose scope chain is a shallow copy of the current scope chain. Subsequent scope pushes/pops in this Environment won't affect the closure, but existing
    * visible scopes remain shared so later assignments within them are observed.
    *
    * Matches dart-sass semantics for `closure()`.
    */
  def closure(): Environment = new Environment(
    _variables = _variables.clone(),
    _variableNodes = _variableNodes.clone(),
    _variableIndices = mutable.Map.empty,
    functions = functions,
    mixins = mixins,
    namespaces = namespaces,
    globalVarNames = globalVarNames,
    _content = _content,
    _inSemiGlobalScope = false
  )

  /** Creates a public-facing copy of this environment that hides private members.
    *
    * A member whose name starts with `-` or `_` is considered private to the defining module and is omitted. This only considers the global scope.
    */
  def publicView(): Environment = {
    val out = new Environment()
    for ((n, v) <- _variables(0) if !Environment.isPrivate(n)) {
      out._variables(0)(n) = v
      _variableNodes(0).get(n).foreach(node => out._variableNodes(0)(n) = node)
    }
    for ((n, c) <- functions if !Environment.isPrivate(n))
      out.functions(n) = c
    for ((n, c) <- mixins if !Environment.isPrivate(n))
      out.mixins(n) = c
    for ((n, e) <- namespaces)
      out.namespaces(n) = e
    out
  }

  /** Creates a new global-only environment containing the built-in functions and any variables that were declared with `!global` in this environment.
    */
  def global(): Environment = {
    val g = Environment.withBuiltins()
    for (name <- globalVarNames)
      _variables(0).get(name).foreach(v => g.setGlobalVariable(name, v))
    g
  }
}

object Environment {

  def apply(): Environment = new Environment()

  /** Whether [name] is a Sass-private member name (leading `-` or `_`). */
  def isPrivate(name: String): Boolean =
    name.nonEmpty && { val c = name.charAt(0); c == '-' || c == '_' }

  /** Creates a new environment pre-populated with all global built-in functions. */
  def withBuiltins(): Environment = {
    val env = new Environment()
    for (fn <- ssg.sass.functions.Functions.global)
      env.setFunction(fn)
    env
  }
}
