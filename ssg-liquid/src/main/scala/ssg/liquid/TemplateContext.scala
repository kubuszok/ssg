/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/TemplateContext.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: Registry delegates to root context
 *   Idiom: incrementIterations uses registry like original
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/TemplateContext.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid

import ssg.liquid.exceptions.ExceededMaxIterationsException

import java.util.{ ArrayList, HashMap, LinkedHashMap, List => JList, Map => JMap }

/** Runtime execution context for template rendering.
  *
  * Holds variable scope (parent-child hierarchy for nested contexts), environment map, registries for special state (cycle, ifchanged, for loops), and error tracking.
  */
class TemplateContext(
  val parser:             TemplateParser,
  protected var parent:   TemplateContext,
  private var variables:  JMap[String, Any],
  private val errorsList: JList[Exception]
) {

  // Environment map and registry live on the root context only
  private var environmentMap: JMap[String, Any] = scala.compiletime.uninitialized
  private var registry:       JMap[String, Any] = scala.compiletime.uninitialized

  def this(parser: TemplateParser, variables: JMap[String, Any]) =
    this(parser, null, new LinkedHashMap[String, Any](variables), new ArrayList[Exception]())

  def this(parser: TemplateParser) =
    this(parser, new LinkedHashMap[String, Any]())

  /** Creates a child context for nested scopes. */
  private def this(parentCtx: TemplateContext) =
    this(parentCtx.parser, parentCtx, new LinkedHashMap[String, Any](), parentCtx.errorsList)

  /** Creates a new child context for nested scopes (blocks, loops). */
  def newChildContext(): TemplateContext =
    new TemplateContext(this)

  /** Creates a new child context with initial variables. */
  def newChildContext(variablesForChild: JMap[String, Any]): TemplateContext = {
    val child = new TemplateContext(this)
    child.variables = variablesForChild
    child
  }

  /** Creates a new ObjectAppender.Controller for rendering. */
  def newObjectAppender(estimatedNumberOfAppends: Int): RenderTransformer.ObjectAppender.Controller =
    parser.renderTransformer.newObjectAppender(this, estimatedNumberOfAppends)

  /** Gets a variable value from this context or parent contexts. */
  def get(key: String): Any = {
    val value = variables.get(key)
    if (value != null) {
      value
    } else if (parent != null) {
      parent.get(key)
    } else {
      null
    }
  }

  /** Puts a variable in this context. */
  def put(key: String, value: Any): Any =
    variables.put(key, value)

  /** Puts a variable, optionally in the root context. */
  def put(key: String, value: Any, inRootContext: Boolean): Any =
    if (!inRootContext || parent == null) {
      variables.put(key, value)
    } else {
      parent.put(key, value, inRootContext)
    }

  /** Removes a variable from this context or parent contexts. */
  def remove(key: String): Any =
    if (variables.containsKey(key)) {
      variables.remove(key)
    } else if (parent != null) {
      parent.remove(key)
    } else {
      null
    }

  /** Checks if a variable exists in this context or parent contexts. */
  def containsKey(key: String): Boolean =
    variables.containsKey(key) || (parent != null && parent.containsKey(key))

  /** Returns the variables in this context (copy). */
  def getVariables: JMap[String, Any] = new LinkedHashMap[String, Any](variables)

  /** Returns the environment map (delegates to root context). */
  def getEnvironmentMap: JMap[String, Any] =
    if (parent != null) {
      parent.getEnvironmentMap
    } else {
      if (environmentMap == null) {
        environmentMap = new HashMap[String, Any]()
      }
      environmentMap
    }

  /** Returns a named registry map (delegates to root context, creates if needed). */
  @SuppressWarnings(Array("unchecked"))
  def getRegistry[T](registryName: String): JMap[String, T] =
    if (parent != null) {
      parent.getRegistry(registryName)
    } else {
      if (registry == null) {
        registry = new HashMap[String, Any]()
      }
      if (!registry.containsKey(registryName)) {
        registry.put(registryName, new HashMap[String, Any]())
      }
      registry.get(registryName).asInstanceOf[JMap[String, T]]
    }

  /** Adds an error to the error list (used in WARN mode). */
  def addError(e: Exception): Unit =
    errorsList.add(e)

  /** Returns the collected errors (copy). */
  def errors(): JList[Exception] = new ArrayList[Exception](errorsList)

  /** Returns the error mode from the parser. */
  def getErrorMode: TemplateParser.ErrorMode = parser.errorMode

  /** Increments the iteration counter and checks against the max (uses registry like original). */
  def incrementIterations(): Unit = {
    val iteratorProtector: JMap[String, Integer] = getRegistry(TemplateContext.REGISTRY_ITERATION_PROTECTOR)
    if (!iteratorProtector.containsKey(TemplateContext.REGISTRY_ITERATION_PROTECTOR)) {
      iteratorProtector.put(TemplateContext.REGISTRY_ITERATION_PROTECTOR, Integer.valueOf(0))
    }
    val value = iteratorProtector.get(TemplateContext.REGISTRY_ITERATION_PROTECTOR).intValue() + 1
    iteratorProtector.put(TemplateContext.REGISTRY_ITERATION_PROTECTOR, Integer.valueOf(value))
    if (value > parser.limitMaxIterations) {
      throw new ExceededMaxIterationsException(parser.limitMaxIterations)
    }
  }

  /** Returns the date parser configured on this context's TemplateParser. */
  def getDateParser: filters.date.BasicDateParser =
    parser.dateParser

  /** Returns the root folder path (for include_relative). */
  def getRootFolder: ssg.commons.io.FilePath = {
    val reg: JMap[String, Any] = getRegistry(TemplateContext.REGISTRY_ROOT_FOLDER)
    reg.get(TemplateContext.REGISTRY_ROOT_FOLDER).asInstanceOf[ssg.commons.io.FilePath]
  }
}

object TemplateContext {
  val REGISTRY_FOR:                 String = "for"
  val REGISTRY_FOR_STACK:           String = "for_stack"
  val REGISTRY_CYCLE:               String = "cycle"
  val REGISTRY_IFCHANGED:           String = "ifchanged"
  val REGISTRY_ITERATION_PROTECTOR: String = "iteration_protector"
  val REGISTRY_ROOT_FOLDER:         String = "registry_root_folder"
}
