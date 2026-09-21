/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Bridge types for test code that was written against the hand-ported liqp API
 * but now runs against Baltic Porter generated code.
 */
package ssg
package liquid

import ssg.liquid.antlr.{ CharStreamWithLocation, NameResolver }

import ssg.commons.io.FilePath

/** Extensions on generated classes to restore hand-ported API surface used by tests. */
object TestBridges {

  /** The hand port's `ResolvedSource` accessors, over what the generated resolver answers. */
  extension (source: CharStreamWithLocation) {
    def content:    String = source.text()
    def sourceName: String = source.getSourceName()
  }

  /** In-memory NameResolver for testing — replaces the hand-ported NameResolver.InMemory. The generated NameResolver trait returns CharStreamWithLocation (ANTLR-based).
    */
  final class InMemoryNameResolver(templates: java.util.Map[String, String]) extends NameResolver {
    override def resolve(name: String): CharStreamWithLocation = {
      val stripped =
        if (
          (name.startsWith("'") && name.endsWith("'")) ||
          (name.startsWith("\"") && name.endsWith("\""))
        )
          name.substring(1, name.length - 1)
        else name
      var content = templates.get(stripped)
      if (content == null) content = templates.get(name)
      if (content == null)
        throw new RuntimeException(s"Template not found: $name")
      new CharStreamWithLocation(
        ssg.liquid.antlr.CharSources.fromString(content, stripped),
        FilePath.of(stripped)
      )
    }
  }
}
