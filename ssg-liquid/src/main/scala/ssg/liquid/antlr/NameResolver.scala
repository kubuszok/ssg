/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/antlr/NameResolver.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.antlr → ssg.liquid.antlr
 *   Convention: Java interface → Scala trait
 *   Idiom: Returns String content instead of ANTLR CharStream
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/antlr/NameResolver.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package antlr

/** Resolves template names to their content.
  *
  * This is the extension point for different SSG include conventions (e.g., Jekyll's `_includes/`, Cobalt's partials, custom layouts).
  */
trait NameResolver {

  /** Resolves a template name to its content string.
    *
    * @param name
    *   the template name (e.g., "header.html")
    * @return
    *   the template content and optional source location info
    */
  def resolve(name: String): NameResolver.ResolvedSource
}

object NameResolver {

  /** Resolved template source with content and optional location. */
  final case class ResolvedSource(content: String, sourceName: String)

  /** Default NameResolver that always throws — templates must provide their own resolver.
    *
    * On JVM, use LocalFSNameResolver for file-system-based resolution.
    */
  final class Default(val snippetsFolderName: String) extends NameResolver {

    override def resolve(name: String): ResolvedSource =
      throw new UnsupportedOperationException(
        s"No NameResolver configured. Cannot resolve template: $name. " +
          "Set a NameResolver via TemplateParser.Builder.withNameResolver()"
      )
  }

  object Default {
    def apply(snippetsFolderName: String): Default = new Default(snippetsFolderName)
  }

  /** In-memory NameResolver for testing and embedded use. */
  final class InMemory(private val templates: java.util.Map[String, String]) extends NameResolver {

    override def resolve(name: String): ResolvedSource = {
      val content = templates.get(name)
      if (content == null) {
        throw new RuntimeException(s"Template not found: $name")
      }
      ResolvedSource(content, name)
    }
  }
}
