/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/IncludeRelative.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 *   Convention: Resolves includes relative to the current file's root folder
 *   Idiom: Overrides detectSource to resolve via context.getRootFolder()
 *   Audited: 2026-04-10 — ISS-102 fixed: uses getRootFolder() for path resolution
 */
package ssg
package liquid
package tags

import ssg.liquid.antlr.NameResolver

import java.nio.file.{ Files, Paths }

/** Jekyll-style include_relative tag.
  *
  * Resolves templates relative to the current file location (from context root folder), unlike the standard include tag which uses the configured NameResolver.
  */
class IncludeRelative extends Include("include_relative") {

  /** Resolves the include source relative to the current file's root folder.
    *
    * Uses `context.getRootFolder()` to determine the base path. Falls back to the current working directory if the root folder is not set.
    */
  override protected def detectSource(context: TemplateContext, includeResource: String): NameResolver.ResolvedSource = {
    var rootPath = context.getRootFolder
    if (rootPath == null) {
      rootPath = Paths.get(".").toAbsolutePath
    }
    val includePath = rootPath.resolve(includeResource)
    val content     = new String(Files.readAllBytes(includePath), java.nio.charset.StandardCharsets.UTF_8)
    NameResolver.ResolvedSource(content, includePath.toString)
  }
}
