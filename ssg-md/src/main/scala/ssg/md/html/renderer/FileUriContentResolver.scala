/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/FileUriContentResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/FileUriContentResolver.java
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 *
 * Migration notes:
 * - Renames: UriContentResolver/UriContentResolverFactory live in ssg.md.html; LastDependent in
 *   ssg.md.util.dependency. FileUtil.getFileContentBytesWithExceptions + java.io.File.isFile/exists
 *   are routed through the cross-platform ssg.md.util.misc.PlatformFiles facade (JVM/Native use
 *   java.nio.file.Files; JS uses Node fs) so the resolver stays platform-agnostic in the shared
 *   scala/ source dir.
 * - Convention: Factory.getAfterDependents/getBeforeDependents/affectsGlobalScope/apply are the
 *   Scala Dependent / (LinkResolverBasicContext => UriContentResolver) members; null -> Nullable.empty.
 * - Idiom: the Java IOException catch printed the stack trace and fell through to returning the
 *   unchanged content; that exact behavior is preserved (printStackTrace, return content).
 */
package ssg
package md
package html
package renderer

import ssg.md.Nullable
import ssg.md.html.{ UriContentResolver, UriContentResolverFactory }
import ssg.md.util.ast.Node
import ssg.md.util.dependency.LastDependent
import ssg.md.util.misc.PlatformFiles

import java.io.{ File, IOException }

import scala.util.boundary
import scala.util.boundary.break

class FileUriContentResolver(context: LinkResolverBasicContext) extends UriContentResolver {

  override def resolveContent(node: Node, context: LinkResolverBasicContext, content: ResolvedContent): ResolvedContent =
    boundary[ResolvedContent] {
      val resolvedLink = content.resolvedLink

      if (resolvedLink.status == LinkStatus.VALID) {
        // have the file
        val url = resolvedLink.url
        if (url.startsWith("file:/")) {
          // handle Windows and OSX/Unix URI
          val substring =
            if (url.startsWith("file://")) url.substring("file://".length)
            else if (File.separatorChar == '\\') url.substring("file:/".length)
            else url.substring("file:".length)
          if (PlatformFiles.isExistingFile(substring)) {
            // need to read and parse the file
            try
              break(content.withContent(Nullable(PlatformFiles.readAllBytes(substring))).withStatus(LinkStatus.VALID))
            catch {
              case e: IOException =>
                e.printStackTrace()
            }
          }
        }
      }
      content
    }
}

object FileUriContentResolver {

  class Factory extends UriContentResolverFactory {

    override def afterDependents: Nullable[Set[Class[?]]] =
      // ensure that default file uri resolver is the last one in the list
      Nullable(Set[Class[?]](classOf[LastDependent]))

    override def beforeDependents: Nullable[Set[Class[?]]] =
      Nullable.empty

    override def affectsGlobalScope: Boolean =
      false

    override def apply(context: LinkResolverBasicContext): UriContentResolver =
      new FileUriContentResolver(context)
  }
}
