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
 *   Convention: Skeleton — public API surface only, TODOs for scope logic
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions
import ssg.sass.ast.AstNode
import ssg.sass.ast.sass.ContentBlock
import ssg.sass.value.Value

/** The lexical environment in which Sass code is evaluated.
  *
  * Tracks variables, functions, mixins, modules and their scopes. TODO: full scope chain, module imports, configuration, forwards.
  */
final class Environment() {

  private val variables:     mutable.Map[String, Value]                = mutable.Map.empty
  private val variableNodes: mutable.Map[String, AstNode]              = mutable.Map.empty
  private val functions:     mutable.Map[String, Callable]             = mutable.Map.empty
  private val mixins:        mutable.Map[String, Callable]             = mutable.Map.empty
  private val scopes:        mutable.Stack[mutable.Map[String, Value]] = mutable.Stack.empty
  private val namespaces:    mutable.Map[String, Environment]          = mutable.Map.empty
  // Names of variables declared with `!global` (used by `global()`).
  private val globalVarNames: mutable.Set[String] = mutable.Set.empty

  /** Registers a namespaced module environment under [name]. */
  def addNamespace(name: String, module: Environment): Unit =
    namespaces(name) = module

  /** Looks up a variable in a namespaced module, or `Nullable.empty`. */
  def getNamespacedVariable(namespace: String, name: String): Nullable[Value] =
    namespaces.get(namespace) match {
      case Some(env)  => env.getVariable(name)
      case scala.None => Nullable.empty
    }

  /** Looks up a function in a namespaced module, or `Nullable.empty`. */
  def getNamespacedFunction(namespace: String, name: String): Nullable[Callable] =
    namespaces.get(namespace) match {
      case Some(env)  => env.getFunction(name)
      case scala.None => Nullable.empty
    }

  /** Iterates over all variable name/value pairs in this environment. */
  def variableEntries: Iterator[(String, Value)] = variables.iterator

  /** Iterates over all function callables in this environment. */
  def functionValues: Iterator[Callable] = functions.valuesIterator

  /** Iterates over all mixin callables in this environment. */
  def mixinValues: Iterator[Callable] = mixins.valuesIterator

  /** Returns the value of the variable named [name], or `Nullable.empty` if none exists.
    */
  def getVariable(name: String): Nullable[Value] =
    variables.get(name) match {
      case Some(v)    => v
      case scala.None => Nullable.empty
    }

  /** Sets the variable named [name] to [value]. */
  def setVariable(name: String, value: Value, nodeWithSpan: Nullable[AstNode] = Nullable.empty): Unit = {
    variables(name) = value
    nodeWithSpan.foreach(n => variableNodes(name) = n)
  }

  /** Sets the variable named [name] to [value] and marks it as `!global` so it survives `global()` snapshots.
    */
  def setGlobalVariable(name: String, value: Value, nodeWithSpan: Nullable[AstNode] = Nullable.empty): Unit = {
    setVariable(name, value, nodeWithSpan)
    val _ = globalVarNames.add(name)
  }

  /** Returns whether a variable named [name] exists. */
  def variableExists(name: String): Boolean = variables.contains(name)

  /** Returns whether a function named [name] exists in this environment. */
  def functionExists(name: String): Boolean = functions.contains(name)

  /** Returns whether a mixin named [name] exists in this environment. */
  def mixinExists(name: String): Boolean = mixins.contains(name)

  /** Returns the namespaced module environment registered under [name], if any. */
  def getNamespace(name: String): Nullable[Environment] =
    namespaces.get(name) match {
      case Some(env)  => Nullable(env)
      case scala.None => Nullable.empty
    }

  /** Returns the function callable with the given [name], or `Nullable.empty`. */
  def getFunction(name: String): Nullable[Callable] =
    functions.get(name) match {
      case Some(c)    => c
      case scala.None => Nullable.empty
    }

  /** Sets a function in this environment. */
  def setFunction(callable: Callable): Unit =
    functions(callable.name) = callable

  /** Returns the mixin callable with the given [name], or `Nullable.empty`. */
  def getMixin(name: String): Nullable[Callable] =
    mixins.get(name) match {
      case Some(c)    => c
      case scala.None => Nullable.empty
    }

  /** Sets a mixin in this environment. */
  def setMixin(callable: Callable): Unit =
    mixins(callable.name) = callable

  /** Runs [callback] within a new lexical scope. */
  def withinScope[T](callback: () => T): T = {
    scopes.push(mutable.Map.empty)
    try callback()
    finally { val _ = scopes.pop() }
  }

  /** Runs [body] in a fully isolated scope: saves a snapshot of variables, functions, and mixins, runs the body, then restores. Used when invoking user-defined callables where parameter bindings must
    * not leak out.
    */
  def withSnapshot[T](body: => T): T = {
    val savedVars    = variables.clone()
    val savedNodes   = variableNodes.clone()
    val savedFns     = functions.clone()
    val savedMix     = mixins.clone()
    val savedContent = _content
    try body
    finally {
      variables.clear(); variables ++= savedVars
      variableNodes.clear(); variableNodes ++= savedNodes
      functions.clear(); functions ++= savedFns
      mixins.clear(); mixins ++= savedMix
      _content = savedContent
    }
  }

  // --- Content block ---------------------------------------------------------

  private var _content: Nullable[ContentBlock] = Nullable.empty

  /** The currently-active `@content` block, if any. */
  def content: Nullable[ContentBlock] = _content

  /** Sets the currently-active `@content` block. */
  def content_=(block: Nullable[ContentBlock]): Unit =
    _content = block

  /** Creates a closure — a snapshot of the current environment that can be used to evaluate callbacks later.
    *
    * Variables, functions, and mixins are cloned so subsequent mutations to this environment don't leak into the closure (and vice versa).
    */
  def closure(): Environment = {
    val snap = new Environment()
    snap.variables ++= variables
    snap.variableNodes ++= variableNodes
    snap.functions ++= functions
    snap.mixins ++= mixins
    snap.namespaces ++= namespaces
    snap.globalVarNames ++= globalVarNames
    snap._content = _content
    snap
  }

  /** Creates a new global-only environment containing the built-in functions and any variables that were declared with `!global` in this environment.
    */
  def global(): Environment = {
    val g = Environment.withBuiltins()
    for (name <- globalVarNames)
      variables.get(name).foreach(v => g.setGlobalVariable(name, v))
    g
  }
}

object Environment {

  def apply(): Environment = new Environment()

  /** Creates a new environment pre-populated with all global built-in functions (math, string, list, map, meta).
    */
  def withBuiltins(): Environment = {
    val env = new Environment()
    for (fn <- ssg.sass.functions.Functions.global)
      env.setFunction(fn)
    env
  }
}
