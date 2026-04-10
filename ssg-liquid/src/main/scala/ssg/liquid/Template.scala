/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/Template.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: Uses hand-written lexer/parser instead of ANTLR
 *   Idiom: Simplified API — parse returns Template, render returns String
 */
package ssg
package liquid

import ssg.liquid.nodes.BlockNode
import ssg.liquid.parser.Inspectable

import java.nio.file.Path
import java.util.{ ArrayList, HashMap, LinkedHashMap, List => JList, Map => JMap }

/** A parsed Liquid template, ready for rendering with variables.
  *
  * Created via `TemplateParser.parse()` or `Template.parse()`.
  */
final class Template(
  val templateParser:  TemplateParser,
  private val root:    BlockNode,
  val templateSize:    Long = 0L,
  val sourceLocation:  Option[Path] = None
) {

  private var templateContext: TemplateContext = scala.compiletime.uninitialized
  private var contextHolder:   Template.ContextHolder = scala.compiletime.uninitialized

  /** Sets a ContextHolder for accessing the rendering context externally. */
  def withContextHolder(holder: Template.ContextHolder): Template = {
    this.contextHolder = holder
    this
  }

  /** Returns the list of exceptions encountered during the last render. */
  def errors(): JList[Exception] =
    if (templateContext == null) new ArrayList[Exception]()
    else templateContext.errors()

  /** Renders this template with the given variables and returns the result as a String. */
  def render(variables: JMap[String, Any]): String =
    String.valueOf(renderToObject(variables))

  /** Renders this template with no variables. */
  def render(): String =
    render(new HashMap[String, Any]())

  /** Renders this template with an Inspectable object. */
  def render(obj: Inspectable): String =
    renderToObject(obj).toString

  /** Renders this template with an Inspectable object, returning the raw result. */
  def renderToObject(obj: Inspectable): Any = {
    val evaluated = templateParser.evaluate(obj)
    val map       = evaluated.toLiquid()
    renderToObject(map)
  }

  /** Renders this template with no variables, returning the raw result. */
  def renderToObject(): Any =
    renderToObject(new HashMap[String, Any]())

  /** Renders this template and returns the raw result object.
    *
    * Enforces `limitMaxTemplateSizeBytes` (pre-render) and `limitMaxRenderTimeMillis` (elapsed time after render).
    */
  def renderToObject(variables: JMap[String, Any]): Any = {
    if (templateSize > templateParser.limitMaxTemplateSizeBytes) {
      throw new RuntimeException(s"template exceeds the max of ${templateParser.limitMaxTemplateSizeBytes} bytes")
    }

    val evaluatedVars: JMap[String, Any] = templateParser.evaluateMode match {
      case TemplateParser.EvaluateMode.EAGER =>
        val evaluated = new LinkedHashMap[String, Any]()
        val iter      = variables.entrySet().iterator()
        while (iter.hasNext) {
          val entry = iter.next()
          val value = entry.getValue
          val ls    = templateParser.evaluate(value)
          evaluated.put(entry.getKey, ls.toLiquid())
        }
        evaluated
      case _ =>
        // LAZY: pass variables through as-is (converted on demand during rendering)
        variables
    }

    this.templateContext = newRootContext(evaluatedVars)
    setRootFolderRegistry(templateContext, sourceLocation)

    if (this.contextHolder != null) {
      contextHolder.setContext(templateContext)
    }

    val startTime = System.currentTimeMillis()
    val rendered  = root.render(templateContext)

    // Check render time limit (cross-platform: post-hoc elapsed check)
    if (templateParser.isRenderTimeLimited) {
      val elapsed = System.currentTimeMillis() - startTime
      if (elapsed > templateParser.limitMaxRenderTimeMillis) {
        throw new RuntimeException(
          s"exceeded the max amount of time (${templateParser.limitMaxRenderTimeMillis} ms.), actual: $elapsed ms."
        )
      }
    }

    templateParser.renderTransformer.transformObject(templateContext, rendered)
  }

  /** Renders with an existing parent context (used by include tags). */
  def renderToObjectUnguarded(variables: JMap[String, Any], parentContext: TemplateContext, isInclude: Boolean): Any = {
    val context = if (isInclude) {
      parentContext.newChildContext(new LinkedHashMap[String, Any](variables))
    } else {
      newRootContext(new LinkedHashMap[String, Any](variables))
    }

    setRootFolderRegistry(context, sourceLocation)

    if (this.contextHolder != null) {
      contextHolder.setContext(context)
    }

    root.render(context)
  }

  /** Renders without guards — no size or time checks. */
  def renderUnguarded(variables: JMap[String, Any]): String =
    renderToObjectUnguarded(variables).toString

  /** Renders without guards, returning the raw result. */
  def renderToObjectUnguarded(variables: JMap[String, Any]): Any =
    renderToObjectUnguarded(variables, null, true)

  private def newRootContext(variables: JMap[String, Any]): TemplateContext = {
    val context = new TemplateContext(templateParser, variables)
    val configurator = templateParser.environmentMapConfigurator
    if (configurator != null) {
      configurator.accept(context.getEnvironmentMap.asInstanceOf[JMap[String, AnyRef]])
    }
    context
  }

  private def setRootFolderRegistry(context: TemplateContext, location: Option[Path]): Unit = {
    location.foreach { loc =>
      val registry: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_ROOT_FOLDER)
      registry.putIfAbsent(TemplateContext.REGISTRY_ROOT_FOLDER, loc.getParent)
    }
  }
}

object Template {

  /** Parses a Liquid template string with the default parser. */
  def parse(input: String): Template =
    TemplateParser.DEFAULT.parse(input)

  /** Sometimes the custom insertions needs to return some extra-data, that is not renderable. Best way to allow this and keeping existing simplicity(when the result is a string) is: provide holder
    * with container for that data. Best container is current templateContext, and it is set into this holder during creation.
    */
  class ContextHolder {
    private var context: TemplateContext = scala.compiletime.uninitialized

    private[liquid] def setContext(ctx: TemplateContext): Unit =
      context = ctx

    def getContext: TemplateContext = context
  }
}
