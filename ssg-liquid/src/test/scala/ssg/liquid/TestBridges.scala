/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Bridge types for test code that was written against the hand-ported liqp API
 * but now runs against Baltic Porter generated code.
 */
package ssg
package liquid

import ssg.liquid.antlr.{CharStreamWithLocation, NameResolver}

import java.nio.file.Paths

/** Extensions on generated classes to restore hand-ported API surface used by tests. */
object TestBridges {

  /** In-memory NameResolver for testing — replaces the hand-ported NameResolver.InMemory.
    * The generated NameResolver trait returns CharStreamWithLocation (ANTLR-based).
    */
  final class InMemoryNameResolver(templates: java.util.Map[String, String]) extends NameResolver {
    override def resolve(name: String): CharStreamWithLocation = {
      val content = templates.get(name)
      if (content == null)
        throw new RuntimeException(s"Template not found: $name")
      new CharStreamWithLocation(
        org.antlr.v4.runtime.CharStreams.fromString(content, name),
        Paths.get(name)
      )
    }
  }
}
