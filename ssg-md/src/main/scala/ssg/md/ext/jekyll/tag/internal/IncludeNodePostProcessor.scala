/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/IncludeNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package tag
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.html.*
import ssg.md.html.renderer.*
import ssg.md.parser.Parser
import ssg.md.parser.block.{NodePostProcessor, NodePostProcessorFactory}
import ssg.md.util.ast.{Document, Node, NodeTracker}
import ssg.md.util.data.DataHolder

import java.util.{ArrayList, HashMap, List as JList, Map as JMap}

import scala.language.implicitConversions
import ssg.md.util.dependency.FirstDependent


class IncludeNodePostProcessor(val document: Document) extends NodePostProcessor {

  val includedDocuments: HashMap[JekyllTag, String] = new HashMap[JekyllTag, String]()
  val resolvedLinks: HashMap[String, ResolvedLink] = new HashMap[String, ResolvedLink]()
  val parser: Parser = Parser.builder(document).build()

  val context: LinkResolverBasicContext = new LinkResolverBasicContext {
    override def getOptions: DataHolder = document
    override def getDocument: Document = IncludeNodePostProcessor.this.document
  }

  // TODO: DependencyResolver.resolveFlatDependencies expects scala.List not java.util.List
  // These need proper conversion when DependencyResolver is fully ported.
  val linkResolvers: JList[LinkResolver] = {
    val factories = JekyllTagExtension.LINK_RESOLVER_FACTORIES.get(document)
    val resolvers = new ArrayList[LinkResolver](factories.size())
    factories.forEach { factory =>
      resolvers.add(factory.apply(context))
    }
    resolvers
  }

  val contentResolvers: JList[UriContentResolver] = {
    val resolverFactories = JekyllTagExtension.CONTENT_RESOLVER_FACTORIES.get(document)
    // TODO: FileUriContentResolver not yet ported - using configured factories only
    val resolvers = new ArrayList[UriContentResolver](resolverFactories.size())
    resolverFactories.forEach { factory =>
      resolvers.add(factory.apply(context))
    }
    resolvers
  }

  private val embedIncludedContent: Boolean = JekyllTagExtension.EMBED_INCLUDED_CONTENT.get(document)
  private val includedHtml: Nullable[JMap[String, String]] = Nullable(JekyllTagExtension.INCLUDED_HTML.get(document))

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  override def process(state: NodeTracker, node: Node): Unit = {
    if (node.isInstanceOf[JekyllTag] && !includedDocuments.containsKey(node)) {
      val jekyllTag = node.asInstanceOf[JekyllTag]
      if (embedIncludedContent && jekyllTag.tag.equals("include")) {
        // see if can find file
        val parameters = jekyllTag.parameters
        val rawUrl = parameters.unescape()
        var fileContent: Nullable[String] = Nullable.empty

        if (includedHtml.exists(_.containsKey(rawUrl))) {
          fileContent = Nullable(includedHtml.get.get(rawUrl))
        } else {
          var resolvedLink = resolvedLinks.get(rawUrl)

          if (resolvedLink == null) { // @nowarn - Java interop: HashMap.get returns null
            resolvedLink = new ResolvedLink(LinkType.LINK, rawUrl)
            val iter = linkResolvers.iterator()
            while (iter.hasNext) {
              val linkResolver = iter.next()
              resolvedLink = linkResolver.resolveLink(node, context, resolvedLink)
              if (resolvedLink.status != LinkStatus.UNKNOWN) {
                // break equivalent via while guard
              }
            }
            resolvedLinks.put(rawUrl, resolvedLink)
          }

          if (resolvedLink.status == LinkStatus.VALID) {
            var resolvedContent = new ResolvedContent(resolvedLink, LinkStatus.UNKNOWN, Nullable.empty)
            val iter = contentResolvers.iterator()
            while (iter.hasNext) {
              val contentResolver = iter.next()
              resolvedContent = contentResolver.resolveContent(node, context, resolvedContent)
              if (resolvedContent.status != LinkStatus.UNKNOWN) {
                // break equivalent
              }
            }

            if (resolvedContent.status == LinkStatus.VALID) {
              resolvedContent.content.foreach { bytes =>
                fileContent = Nullable(new String(bytes, "UTF-8"))
              }
            }
          }
        }

        fileContent.foreach { content =>
          if (content.nonEmpty) {
            includedDocuments.put(jekyllTag, content)

            val includedDoc = parser.parse(content)
            parser.transferReferences(document, includedDoc, Nullable.empty)

            if (includedDoc.contains(Parser.REFERENCES)) {
              // NOTE: if included doc has reference definitions then we need to re-evaluate ones which are missing
              document.set(HtmlRenderer.RECHECK_UNDEFINED_REFERENCES, true)
            }

            // insert children of included documents into jekyll tag node
            var child = includedDoc.firstChild

            // NOTE: if this is an inline include tag and there is only one child and it is a Paragraph then we unwrap the paragraph
            if (jekyllTag.parent.isDefined && !jekyllTag.parent.get.isInstanceOf[JekyllTagBlock]) {
              if (child.isDefined && child.get.isInstanceOf[Paragraph] && child.get.next.isEmpty) {
                child = child.get.firstChild
              }
            }

            while (child.isDefined) {
              val nextChild = child.get.next
              node.appendChild(child.get)
              state.nodeAddedWithDescendants(child.get)
              child = nextChild
            }
          }
        }
      }
    }
  }
}

object IncludeNodePostProcessor {

  class Factory extends NodePostProcessorFactory(false) {
    addNodes(classOf[JekyllTag])

    override def beforeDependents: Nullable[Set[Class[?]]] = {
      // NOTE: add this as the first node post processor
      Nullable(Set[Class[?]](classOf[FirstDependent]))
    }

    override def apply(document: Document): NodePostProcessor = new IncludeNodePostProcessor(document)
  }
}
