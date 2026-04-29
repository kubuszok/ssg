/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** Tests ported from liqp's filters/Absolute_UrlTest.java — 14 tests. */
final class AbsoluteUrlFilterSuite extends munit.FunSuite {

  // site.baseurl and site.config.url
  private def getData(siteUrl: Any, baseurl: String): JHashMap[String, Any] = {
    val siteMap = new JHashMap[String, Any]()
    siteMap.put("baseurl", baseurl)
    val config: JHashMap[String, Any] = new JHashMap[String, Any]()
    config.put("url", siteUrl)
    siteMap.put("config", config)
    val result = new JHashMap[String, Any]()
    result.put("site", siteMap)
    result
  }

  private val jekyllParser: TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()

  /*
   * should "produce an absolute URL from a page URL"
   */
  test("absolute_url: produce an absolute URL from a page URL") {
    val template = jekyllParser.parse("{{ '/about/my_favorite_page/' | absolute_url }}")
    val data     = getData("http://example.com", "base")
    assertEquals(template.render(data), "http://example.com/base/about/my_favorite_page/")
  }

  /*
   * should "ensure the leading slash"
   */
  test("absolute_url: ensure the leading slash") {
    val template = jekyllParser.parse("{{ 'about/my_favorite_page/' | absolute_url }}")
    val data     = getData("http://example.com", "/base")
    assertEquals(template.render(data), "http://example.com/base/about/my_favorite_page/")
  }

  /*
   * should "ensure the leading slash for the baseurl"
   */
  test("absolute_url: ensure the leading slash for the baseurl") {
    val template = jekyllParser.parse("{{ 'about/my_favorite_page/' | absolute_url }}")
    val data     = getData("http://example.com", "base")
    assertEquals(template.render(data), "http://example.com/base/about/my_favorite_page/")
  }

  /*
   * should "be ok with a blank but present 'url'"
   */
  test("absolute_url: be ok with a blank but present url") {
    val template = jekyllParser.parse("{{ 'about/my_favorite_page/' | absolute_url }}")
    val data     = getData("", "base")
    assertEquals(template.render(data), "/base/about/my_favorite_page/")
  }

  /*
   * should "be ok with a nil 'url'"
   */
  test("absolute_url: be ok with a nil url") {
    val template = jekyllParser.parse("{{ 'about/my_favorite_page/' | absolute_url }}")
    val data     = getData(null, "base")
    assertEquals(template.render(data), "/base/about/my_favorite_page/")
  }

  /*
   * should "be ok with a nil 'baseurl'"
   */
  test("absolute_url: be ok with a nil baseurl") {
    val template = jekyllParser.parse("{{ 'about/my_favorite_page/' | absolute_url }}")
    val data     = getData("http://example.com", null)
    assertEquals(template.render(data), "http://example.com/about/my_favorite_page/")
  }

  /*
   * should "not prepend a forward slash if input is empty"
   */
  test("absolute_url: not prepend forward slash if input is empty") {
    val template = jekyllParser.parse("{{ '' | absolute_url }}")
    val data     = getData("http://example.com", "/base")
    assertEquals(template.render(data), "http://example.com/base")
  }

  /*
   * should "not append a forward slash if input is '/'"
   */
  test("absolute_url: not append forward slash if input is slash") {
    val template = jekyllParser.parse("{{ '/' | absolute_url }}")
    val data     = getData("http://example.com", "/base")
    assertEquals(template.render(data), "http://example.com/base/")
  }

  /*
   * should "not append a forward slash if input is '/' and nil 'baseurl'"
   */
  test("absolute_url: not append forward slash if input is slash and nil baseurl") {
    val template = jekyllParser.parse("{{ '/' | absolute_url }}")
    val data     = getData("http://example.com", null)
    assertEquals(template.render(data), "http://example.com/")
  }

  /*
   * should "not append a forward slash if both input and baseurl are simply '/'"
   */
  test("absolute_url: not append forward slash if both input and baseurl are simply slash") {
    val template = jekyllParser.parse("{{ '/' | absolute_url }}")
    val data     = getData("http://example.com", "/")
    assertEquals(template.render(data), "http://example.com/")
  }

  /*
   * should "normalize international URLs"
   */
  test("absolute_url: normalize international URLs".fail) {
    val template = jekyllParser.parse("{{ '' | absolute_url }}")
    val data     = getData("http://\u00fcmlaut.example.org/", null)
    assertEquals(template.render(data), "http://xn--mlaut-jva.example.org/")
  }

  test("absolute_url: normalize international URLs in path".fail) {
    val template = jekyllParser.parse("{{ '\u00fc' | absolute_url }}")
    val data     = getData("http://\u00fcmlaut.example.org/", null)
    assertEquals(template.render(data), "http://xn--mlaut-jva.example.org/%C3%BC")
  }

  /*
   * should "not modify an absolute URL"
   */
  test("absolute_url: not modify an absolute URL") {
    val template = jekyllParser.parse("{{ 'http://example.com/' | absolute_url }}")
    val data     = getData("http://\u00fcmlaut.example.org/", null)
    assertEquals(template.render(data), "http://example.com/")
  }

  /*
   * should "transform the input URL to a string"
   */
  test("absolute_url: transform input URL to string") {
    assume(PlatformCompat.supportsReflection, "Requires toString() dispatch via reflection (JVM-only)")
    val template = jekyllParser.parse("{{ '/my-page.html' | absolute_url }}")
    val data = getData(new Object() {
      override def toString: String = "http://example.org"
    }, null)
    assertEquals(template.render(data), "http://example.org/my-page.html")
  }
}
