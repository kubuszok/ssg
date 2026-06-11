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
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/Template.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid

import ssg.commons.io.FilePath
import ssg.data.DataView
import ssg.liquid.nodes.BlockNode

import java.util.{ ArrayList, HashMap, LinkedHashMap, List => JList, Map => JMap }

/** A parsed Liquid template, ready for rendering with variables.
  *
  * Created via `TemplateParser.parse()` or `Template.parse()`.
  */
final class Template(
  val templateParser: TemplateParser,
  private val root:   BlockNode,
  val templateSize:   Long = 0L,
  val sourceLocation: Option[FilePath] = None
) {

  private var templateContext: TemplateContext        = scala.compiletime.uninitialized
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
  def render(variables: JMap[String, DataView]): String =
    renderToObject(variables).toString

  /** Renders this template with no variables. */
  def render(): String =
    render(new HashMap[String, DataView]())

  /** Renders this template with no variables, returning the raw result. */
  def renderToObject(): DataView =
    renderToObject(new HashMap[String, DataView]())

  /** Renders this template and returns the raw result object.
    *
    * Enforces `limitMaxTemplateSizeBytes` (pre-render) and `limitMaxRenderTimeMillis` (elapsed time after render).
    */
  def renderToObject(variables: JMap[String, DataView]): DataView = {
    if (templateSize > templateParser.limitMaxTemplateSizeBytes) {
      throw new RuntimeException(s"template exceeds the max of ${templateParser.limitMaxTemplateSizeBytes} bytes")
    }

    // In DataView mode, EAGER evaluation is a no-op — DataView values pass through as-is
    val evaluatedVars: JMap[String, DataView] = variables

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

  /** Renders with an existing parent context (used by include tags and `where_exp`).
    *
    * Mirrors liqp `Template.renderToObjectUnguarded` (Template.java:347-380): scoping is decided solely by whether `parentContext` is non-null — a non-null parent ALWAYS yields
    * `parent.newChildContext(variables)` (Template.java:358-362), so the child resolves variables local-first and falls through to the enclosing scope (TemplateContext.java:104-120).
    * `doClearThreadLocal` is the thread-local cleanup flag (BasicTypesSupport.clearReferences, Template.java:350-352); in this DataView-mode port there is no thread-local reference table, so it is a
    * no-op kept for signature parity with the upstream call sites.
    */
  def renderToObjectUnguarded(variables: JMap[String, DataView], parentContext: TemplateContext, doClearThreadLocal: Boolean): DataView = {
    val _       = doClearThreadLocal
    val context = if (parentContext != null) {
      parentContext.newChildContext(new LinkedHashMap[String, DataView](variables))
    } else {
      newRootContext(new LinkedHashMap[String, DataView](variables))
    }

    setRootFolderRegistry(context, sourceLocation)

    if (this.contextHolder != null) {
      contextHolder.setContext(context)
    }

    root.render(context)
  }

  /** Renders without guards — no size or time checks. */
  def renderUnguarded(variables: JMap[String, DataView]): String =
    renderToObjectUnguarded(variables).toString

  /** Renders without guards, returning the raw result. */
  def renderToObjectUnguarded(variables: JMap[String, DataView]): DataView =
    renderToObjectUnguarded(variables, null, true)

  private def newRootContext(variables: JMap[String, DataView]): TemplateContext = {
    val context      = new TemplateContext(templateParser, variables)
    val configurator = templateParser.environmentMapConfigurator
    if (configurator != null) {
      // The configurator expects JMap[String, AnyRef] — but now we use JMap[String, DataView].
      // We pass the environment map as-is; the configurator must work with DataView values.
      configurator.accept(context.getEnvironmentMap.asInstanceOf[JMap[String, AnyRef]])
    }
    context
  }

  private def setRootFolderRegistry(context: TemplateContext, location: Option[FilePath]): Unit =
    location.foreach { loc =>
      val registry: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_ROOT_FOLDER)
      loc.parent.foreach { parent =>
        registry.putIfAbsent(TemplateContext.REGISTRY_ROOT_FOLDER, parent)
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
