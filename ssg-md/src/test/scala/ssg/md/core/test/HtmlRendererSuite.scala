/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../html/HtmlRendererTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.ast._
import ssg.md.html._
import ssg.md.html.renderer._
import ssg.md.parser.Parser
import ssg.md.util.ast.Node
import ssg.md.util.data.{ DataHolder, DataKey, MutableDataSet }
import ssg.md.util.sequence.LineAppendable

import scala.collection.mutable
import scala.language.implicitConversions

final class HtmlRendererSuite extends munit.FunSuite {

  test("htmlAllowingShouldNotEscapeInlineHtml") {
    val rendered = htmlAllowingRenderer().render(parse("paragraph with <span id='foo' class=\"bar\">inline &amp; html</span>"))
    assertEquals(rendered, "<p>paragraph with <span id='foo' class=\"bar\">inline &amp; html</span></p>\n")
  }

  test("htmlAllowingShouldNotEscapeBlockHtml") {
    val rendered = htmlAllowingRenderer().render(parse("<div id='foo' class=\"bar\">block &amp;</div>"))
    assertEquals(rendered, "<div id='foo' class=\"bar\">block &amp;</div>\n")
  }

  test("htmlEscapingShouldEscapeInlineHtml") {
    val rendered = htmlEscapingRenderer().render(parse("paragraph with <span id='foo' class=\"bar\">inline &amp; html</span>"))
    // Note that &amp; is not escaped, as it's a normal text node, not part of the inline HTML.
    assertEquals(rendered, "<p>paragraph with &lt;span id='foo' class=&quot;bar&quot;&gt;inline &amp; html&lt;/span&gt;</p>\n")
  }

  test("htmlEscapingShouldEscapeHtmlBlocks") {
    val rendered = htmlEscapingRenderer().render(parse("<div id='foo' class=\"bar\">block &amp;</div>"))
    assertEquals(rendered, "<p>&lt;div id='foo' class=&quot;bar&quot;&gt;block &amp;amp;&lt;/div&gt;</p>\n")
  }

  test("textEscaping") {
    val rendered = defaultRenderer().render(parse("escaping: & < > \" '"))
    assertEquals(rendered, "<p>escaping: &amp; &lt; &gt; &quot; '</p>\n")
  }

  test("percentEncodeUrlDisabled") {
    assertEquals(defaultRenderer().render(parse("[a](foo&amp;bar)")), "<p><a href=\"foo&amp;bar\">a</a></p>\n")
    assertEquals(defaultRenderer().render(parse("[a](\u00e4)")), "<p><a href=\"\u00e4\">a</a></p>\n")
    assertEquals(defaultRenderer().render(parse("[a](foo%20bar)")), "<p><a href=\"foo%20bar\">a</a></p>\n")
  }

  test("percentEncodeUrl") {
    // Entities are escaped anyway
    assertEquals(percentEncodingRenderer().render(parse("[a](foo&amp;bar)")), "<p><a href=\"foo&amp;bar\">a</a></p>\n")
    // Existing encoding is preserved
    assertEquals(percentEncodingRenderer().render(parse("[a](foo%20bar)")), "<p><a href=\"foo%20bar\">a</a></p>\n")
    assertEquals(percentEncodingRenderer().render(parse("[a](foo%61)")), "<p><a href=\"foo%61\">a</a></p>\n")
    // Invalid encoding is escaped
    assertEquals(percentEncodingRenderer().render(parse("[a](foo%)")), "<p><a href=\"foo%25\">a</a></p>\n")
    assertEquals(percentEncodingRenderer().render(parse("[a](foo%a)")), "<p><a href=\"foo%25a\">a</a></p>\n")
    assertEquals(percentEncodingRenderer().render(parse("[a](foo%a_)")), "<p><a href=\"foo%25a_\">a</a></p>\n")
    assertEquals(percentEncodingRenderer().render(parse("[a](foo%xx)")), "<p><a href=\"foo%25xx\">a</a></p>\n")
    // Reserved characters are preserved, except for '[' and ']'
    assertEquals(
      percentEncodingRenderer().render(parse("[a](!*'();:@&=+$,/?#[])")),
      "<p><a href=\"!*'();:@&amp;=+$,/?#%5B%5D\">a</a></p>\n"
    )
    // Unreserved characters are preserved
    assertEquals(
      percentEncodingRenderer().render(parse("[a](ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~)")),
      "<p><a href=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~\">a</a></p>\n"
    )
    // Other characters are percent-encoded (LATIN SMALL LETTER A WITH DIAERESIS)
    assertEquals(percentEncodingRenderer().render(parse("[a](\u00e4)")), "<p><a href=\"%C3%A4\">a</a></p>\n")
    // Other characters are percent-encoded (MUSICAL SYMBOL G CLEF, surrogate pair in UTF-16)
    assertEquals(percentEncodingRenderer().render(parse("[a](\uD834\uDD1E)")), "<p><a href=\"%F0%9D%84%9E\">a</a></p>\n")
  }

  test("attributeProviderForCodeBlock") {
    val factory = new IndependentAttributeProviderFactory {
      override def apply(context: LinkResolverContext): AttributeProvider = { (node: Node, part: AttributablePart, attributes: ssg.md.util.html.MutableAttributes) =>
        if (node.isInstanceOf[FencedCodeBlock] && (part eq CoreNodeRenderer.CODE_CONTENT)) {
          val fencedCodeBlock = node.asInstanceOf[FencedCodeBlock]
          // Remove the default attribute for info
          attributes.remove("class")
          // Put info in custom attribute instead
          attributes.replaceValue("data-custom", fencedCodeBlock.info.toString)
        }
      }
    }

    val renderer = HtmlRenderer.builder().attributeProviderFactory(factory).build()
    val rendered = renderer.render(parse("```info\ncontent\n```"))
    assertEquals(rendered, "<pre><code data-custom=\"info\">content\n</code></pre>\n")

    val rendered2 = renderer.render(parse("```evil\"\ncontent\n```"))
    assertEquals(rendered2, "<pre><code data-custom=\"evil&quot;\">content\n</code></pre>\n")
  }

  test("attributeProviderForImage") {
    val factory = new IndependentAttributeProviderFactory {
      override def apply(context: LinkResolverContext): AttributeProvider = { (node: Node, part: AttributablePart, attributes: ssg.md.util.html.MutableAttributes) =>
        if (node.isInstanceOf[Image]) {
          attributes.remove("alt")
          attributes.replaceValue("test", "hey")
        }
      }
    }

    val renderer = HtmlRenderer.builder().attributeProviderFactory(factory).build()
    val rendered = renderer.render(parse("![foo](/url)\n"))
    assertEquals(rendered, "<p><img src=\"/url\" test=\"hey\" /></p>\n")
  }

  test("overrideNodeRender") {
    val nodeRendererFactory: NodeRendererFactory = (options: DataHolder) =>
      new NodeRenderer {
        override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
          val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
          set.add(
            new NodeRenderingHandler[Link](
              classOf[Link],
              new NodeRenderingHandler.CustomNodeRenderer[Link] {
                override def render(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
                  context.getHtmlWriter.text("test")
              }
            )
          )
          Nullable(set.toSet)
        }
      }

    val renderer = HtmlRenderer.builder().nodeRendererFactory(nodeRendererFactory).build()
    val rendered = renderer.render(parse("foo [bar](/url)"))
    assertEquals(rendered, "<p>foo test</p>\n")
  }

  test("overrideInheritNodeRender") {
    val nodeRendererFactory: NodeRendererFactory = (options: DataHolder) =>
      new NodeRenderer {
        override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
          val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
          set.add(
            new NodeRenderingHandler[Link](
              classOf[Link],
              new NodeRenderingHandler.CustomNodeRenderer[Link] {
                override def render(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
                  if (node.text.equals("bar")) {
                    context.getHtmlWriter.text("test")
                  } else {
                    context.delegateRender()
                  }
              }
            )
          )
          Nullable(set.toSet)
        }
      }

    val renderer = HtmlRenderer.builder().nodeRendererFactory(nodeRendererFactory).build()
    var rendered = renderer.render(parse("foo [bar](/url)"))
    assertEquals(rendered, "<p>foo test</p>\n")

    rendered = renderer.render(parse("foo [bars](/url)"))
    assertEquals(rendered, "<p>foo <a href=\"/url\">bars</a></p>\n")
  }

  test("overrideInheritNodeRenderSubContext") {
    val nodeRendererFactory: NodeRendererFactory = (options: DataHolder) =>
      new NodeRenderer {
        override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
          val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
          set.add(
            new NodeRenderingHandler[Link](
              classOf[Link],
              new NodeRenderingHandler.CustomNodeRenderer[Link] {
                override def render(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
                  if (node.text.equals("bar")) {
                    context.getHtmlWriter.text("test")
                  } else {
                    val subContext = context.getDelegatedSubContext(true)
                    if (node.text.equals("raw")) {
                      subContext.doNotRenderLinks()
                    }
                    subContext.delegateRender()
                    val s = subContext.getHtmlWriter.asInstanceOf[LineAppendable].toString(-1, -1)
                    html.raw(s)
                  }
              }
            )
          )
          Nullable(set.toSet)
        }
      }

    val renderer = HtmlRenderer.builder().nodeRendererFactory(nodeRendererFactory).build()
    var rendered = renderer.render(parse("foo [bar](/url)"))
    assertEquals(rendered, "<p>foo test</p>\n")

    rendered = renderer.render(parse("foo [bars](/url)"))
    assertEquals(rendered, "<p>foo <a href=\"/url\">bars</a></p>\n")

    rendered = renderer.render(parse("foo [raw](/url)"))
    assertEquals(rendered, "<p>foo raw</p>\n")

    rendered = renderer.render(parse("[bar](/url) foo [raw](/url) [bars](/url)"))
    assertEquals(rendered, "<p>test foo raw <a href=\"/url\">bars</a></p>\n")
  }

  test("overrideInheritDependentNodeRender") {
    val nodeRendererFactory: NodeRendererFactory = (options: DataHolder) =>
      new NodeRenderer {
        override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
          val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
          set.add(
            new NodeRenderingHandler[Link](
              classOf[Link],
              new NodeRenderingHandler.CustomNodeRenderer[Link] {
                override def render(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
                  if (node.text.equals("bar")) {
                    context.getHtmlWriter.text("test")
                  } else if (node.text.equals("bars")) {
                    context.getHtmlWriter.text("tests")
                  } else {
                    context.delegateRender()
                  }
              }
            )
          )
          Nullable(set.toSet)
        }
      }

    val nodeRendererFactory2: DelegatingNodeRendererFactory = new DelegatingNodeRendererFactory {
      override def apply(options: DataHolder): NodeRenderer = new NodeRenderer {
        override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
          val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
          set.add(
            new NodeRenderingHandler[Link](
              classOf[Link],
              new NodeRenderingHandler.CustomNodeRenderer[Link] {
                override def render(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
                  if (node.text.equals("bar")) {
                    context.getHtmlWriter.text("testing")
                  } else {
                    context.delegateRender()
                  }
              }
            )
          )
          Nullable(set.toSet)
        }
      }

      override def getDelegates: Nullable[Set[Class[?]]] = {
        val set = mutable.HashSet.empty[Class[?]]
        set.add(nodeRendererFactory.getClass)
        Nullable(set.toSet)
      }
    }

    val renderer = HtmlRenderer.builder().nodeRendererFactory(nodeRendererFactory).nodeRendererFactory(nodeRendererFactory2).build()
    var rendered = renderer.render(parse("foo [bar](/url)"))
    assertEquals(rendered, "<p>foo testing</p>\n")

    rendered = renderer.render(parse("foo [bars](/url)"))
    assertEquals(rendered, "<p>foo tests</p>\n")
  }

  test("overrideInheritDependentNodeRenderReversed") {
    val nodeRendererFactory: NodeRendererFactory = (options: DataHolder) =>
      new NodeRenderer {
        override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
          val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
          set.add(
            new NodeRenderingHandler[Link](
              classOf[Link],
              new NodeRenderingHandler.CustomNodeRenderer[Link] {
                override def render(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
                  if (node.text.equals("bar")) {
                    context.getHtmlWriter.text("test")
                  } else if (node.text.equals("bars")) {
                    context.getHtmlWriter.text("tests")
                  } else {
                    context.delegateRender()
                  }
              }
            )
          )
          Nullable(set.toSet)
        }
      }

    val nodeRendererFactory2: DelegatingNodeRendererFactory = new DelegatingNodeRendererFactory {
      override def apply(options: DataHolder): NodeRenderer = new NodeRenderer {
        override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
          val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
          set.add(
            new NodeRenderingHandler[Link](
              classOf[Link],
              new NodeRenderingHandler.CustomNodeRenderer[Link] {
                override def render(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
                  if (node.text.equals("bar")) {
                    context.getHtmlWriter.text("testing")
                  } else {
                    context.delegateRender()
                  }
              }
            )
          )
          Nullable(set.toSet)
        }
      }

      override def getDelegates: Nullable[Set[Class[?]]] = {
        val set = mutable.HashSet.empty[Class[?]]
        set.add(nodeRendererFactory.getClass)
        Nullable(set.toSet)
      }
    }

    // reverse the renderer order
    val renderer = HtmlRenderer.builder().nodeRendererFactory(nodeRendererFactory2).nodeRendererFactory(nodeRendererFactory).build()
    var rendered = renderer.render(parse("foo [bar](/url)"))
    assertEquals(rendered, "<p>foo testing</p>\n")

    rendered = renderer.render(parse("foo [bars](/url)"))
    assertEquals(rendered, "<p>foo tests</p>\n")
  }

  test("orderedListStartZero") {
    assertEquals(defaultRenderer().render(parse("0. Test\n")), "<ol start=\"0\">\n<li>Test</li>\n</ol>\n")
  }

  test("imageAltTextWithSoftLineBreak") {
    assertEquals(
      defaultRenderer().render(parse("![foo\nbar](/url)\n")),
      "<p><img src=\"/url\" alt=\"foo\nbar\" /></p>\n"
    )
  }

  test("imageAltTextWithHardLineBreak") {
    assertEquals(
      defaultRenderer().render(parse("![foo  \nbar](/url)\n")),
      "<p><img src=\"/url\" alt=\"foo\nbar\" /></p>\n"
    )
  }

  test("imageAltTextWithEntities") {
    assertEquals(
      defaultRenderer().render(parse("![foo &auml;](/url)\n")),
      "<p><img src=\"/url\" alt=\"foo \u00e4\" /></p>\n"
    )
  }

  test("withOptions_customLinkResolver") {
    // make sure custom link resolver is preserved when using withOptions() on HTML builder
    val renderer = HtmlRenderer.builder().linkResolverFactory(new CustomRefLinkResolverFactory()).build()
    val rendered = renderer.render(parse("foo [:bar]"))
    assertEquals(rendered, "<p>foo [:bar]</p>\n")
  }

  test("withOptions_linkRefCustomLinkResolver") {
    // make sure custom link resolver is preserved when using withOptions() on HTML builder
    val OPTIONS:  DataHolder = new MutableDataSet().set(CustomLinkResolverImpl.DOC_RELATIVE_URL, "/url").toImmutable
    val OPTIONS1: DataHolder = new MutableDataSet().set(CustomLinkResolverImpl.DOC_RELATIVE_URL, "/url1").toImmutable
    val OPTIONS2: DataHolder = new MutableDataSet().set(CustomLinkResolverImpl.DOC_RELATIVE_URL, "/url2").toImmutable

    val rendererBase = HtmlRenderer.builder(OPTIONS).linkResolverFactory(new CustomLinkResolverFactory()).build()
    val renderer1    = HtmlRenderer.builder(OPTIONS1).linkResolverFactory(new CustomLinkResolverFactory()).build()
    val renderer2    = HtmlRenderer.builder(OPTIONS2).linkResolverFactory(new CustomLinkResolverFactory()).build()

    val rendered  = rendererBase.render(parse("foo [bar](/url)"))
    val rendered1 = renderer1.render(parse("foo [bar](/url)"))
    val rendered2 = renderer2.render(parse("foo [bar](/url)"))

    assertEquals(rendered, "<p>foo <a href=\"www.url.com/url\">bar</a></p>\n")
    assertEquals(rendered1, "<p>foo <a href=\"www.url.com/url1\">bar</a></p>\n")
    assertEquals(rendered2, "<p>foo <a href=\"www.url.com/url2\">bar</a></p>\n")
  }

  // Helper methods

  private def defaultRenderer(): HtmlRenderer =
    HtmlRenderer.builder().build()

  private def htmlAllowingRenderer(): HtmlRenderer =
    HtmlRenderer.builder().escapeHtml(false).build()

  private def htmlEscapingRenderer(): HtmlRenderer =
    HtmlRenderer.builder().escapeHtml(true).build()

  private def percentEncodingRenderer(): HtmlRenderer =
    HtmlRenderer.builder().percentEncodeUrls(true).build()

  private def parse(source: String): Node =
    Parser.builder().build().parse(source)

  // Custom link resolver implementations

  private class CustomLinkResolverImpl(context: LinkResolverBasicContext) extends LinkResolver {
    private val docUrl: String = CustomLinkResolverImpl.DOC_RELATIVE_URL.get(Nullable(context.getOptions))

    override def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink =
      node match {
        case linkNode: Link =>
          if (linkNode.url.equals("/url")) {
            link.withUrl("www.url.com" + docUrl)
          } else {
            link
          }
        case _ => link
      }
  }

  private object CustomLinkResolverImpl {
    val DOC_RELATIVE_URL: DataKey[String] = new DataKey[String]("DOC_RELATIVE_URL", "")
  }

  private class CustomLinkResolverFactory extends IndependentLinkResolverFactory {
    override def apply(context: LinkResolverBasicContext): LinkResolver =
      new CustomLinkResolverImpl(context)
  }

  private class CustomRefLinkResolverFactory extends IndependentLinkResolverFactory {
    // In the original, CustomRefLinkResolverImpl.resolveLink handles LinkRef/ImageRef,
    // but the factory delegates to CustomLinkResolverImpl (matching the original code).
    override def apply(context: LinkResolverBasicContext): LinkResolver =
      new CustomLinkResolverImpl(context)
  }
}
