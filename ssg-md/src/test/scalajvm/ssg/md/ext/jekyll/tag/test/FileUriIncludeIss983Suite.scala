/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-983 regression: a Jekyll {% include %} tag whose link resolves to a filesystem
 * file:/ URI must embed the file's actual on-disk content. This exercises the default
 * FileUriContentResolver.Factory fallback wired into IncludeNodePostProcessor (mirroring
 * flexmark-ext-jekyll-tag/.../IncludeNodePostProcessor.java:57-65) together with the
 * ported FileUriContentResolver
 * (flexmark/src/main/java/com/vladsch/flexmark/html/renderer/FileUriContentResolver.java).
 *
 * Before the fix this is impossible: FileUriContentResolver did not exist and no content
 * resolver was registered, so the include resolved to no content and rendered as an empty
 * jekyll tag span. JVM-only because it writes a real temp file. */
package ssg
package md
package ext
package jekyll
package tag
package test

import ssg.md.Nullable
import ssg.md.ext.jekyll.tag.JekyllTagExtension
import ssg.md.html.{ HtmlRenderer, LinkResolver, LinkResolverFactory }
import ssg.md.html.renderer.{ LinkResolverBasicContext, LinkStatus, ResolvedLink }
import ssg.md.parser.Parser
import ssg.md.util.ast.Node
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.{ ArrayList, Collections, List as JList }

import scala.language.implicitConversions

final class FileUriIncludeIss983Suite extends munit.FunSuite {

  // A link resolver that marks any file:/ url as VALID (mirrors the CustomLinkResolverSample
  // DocxLinkResolver branch `url.startsWith("file:/")` -> withStatus(VALID).withUrl(url)).
  final private class FileLinkResolver extends LinkResolver {
    override def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink = {
      val url = link.url
      if (url.startsWith("file:/")) link.withStatus(LinkStatus.VALID).withUrl(url)
      else link
    }
  }

  final private class FileLinkResolverFactory extends LinkResolverFactory {
    override def afterDependents:                          Nullable[Set[Class[?]]] = Nullable.empty
    override def beforeDependents:                         Nullable[Set[Class[?]]] = Nullable.empty
    override def affectsGlobalScope:                       Boolean                 = false
    override def apply(context: LinkResolverBasicContext): LinkResolver            = new FileLinkResolver()
  }

  test("ISS-983: include resolves a filesystem file:/ path to the file content") {
    // write a real file on disk
    val tmpDir   = Files.createTempDirectory("iss983-include")
    val included = tmpDir.resolve("snippet.md")
    val body     = "# Included Heading\n\nIncluded body paragraph.\n"
    Files.write(included, body.getBytes(StandardCharsets.UTF_8))

    val factories: JList[LinkResolverFactory] = new ArrayList[LinkResolverFactory]()
    factories.add(new FileLinkResolverFactory())

    val options: DataHolder = new MutableDataSet()
      .set(Parser.EXTENSIONS, Collections.singleton(JekyllTagExtension.create()))
      .set(JekyllTagExtension.EMBED_INCLUDED_CONTENT, true)
      .set(JekyllTagExtension.LINK_RESOLVER_FACTORIES, factories)
      // NOTE: CONTENT_RESOLVER_FACTORIES is deliberately left empty so the default
      // FileUriContentResolver.Factory fallback path is exercised.
      .toImmutable

    val parser   = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(Nullable(options)).build()

    val source   = "before\n\n{% include file:" + included.toAbsolutePath.toString + " %}\n\nafter\n"
    val document = parser.parse(source)
    val html     = renderer.render(document)

    // the on-disk file's content must have been embedded and rendered
    assert(html.contains("Included Heading"), s"expected included heading in output, got:\n$html")
    assert(html.contains("Included body paragraph."), s"expected included body in output, got:\n$html")

    Files.deleteIfExists(included)
    Files.deleteIfExists(tmpDir)
  }

  test("ISS-983: include of a missing file:/ path embeds no content") {
    val factories: JList[LinkResolverFactory] = new ArrayList[LinkResolverFactory]()
    factories.add(new FileLinkResolverFactory())

    val options: DataHolder = new MutableDataSet()
      .set(Parser.EXTENSIONS, Collections.singleton(JekyllTagExtension.create()))
      .set(JekyllTagExtension.EMBED_INCLUDED_CONTENT, true)
      .set(JekyllTagExtension.LINK_RESOLVER_FACTORIES, factories)
      .toImmutable

    val parser   = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(Nullable(options)).build()

    val source   = "before\n\n{% include file:/no/such/iss983-missing-file.md %}\n\nafter\n"
    val document = parser.parse(source)
    val html     = renderer.render(document)

    assert(!html.contains("Included Heading"), s"missing file must not embed content, got:\n$html")
  }
}
