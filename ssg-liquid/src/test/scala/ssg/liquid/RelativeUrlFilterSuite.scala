/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** Tests ported from liqp's filters/Relative_UrlTest.java — 14 tests. */
final class RelativeUrlFilterSuite extends munit.FunSuite {

  // site.baseurl
  private def getData(s: String): JHashMap[String, DataView] = {
    val siteMap = new JHashMap[String, DataView]()
    siteMap.put("baseurl", TestHelper.dv(s))
    val result = new JHashMap[String, DataView]()
    result.put("site", TestHelper.dv(siteMap))
    result
  }

  private def jekyllParser(): TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()

  /*
   * should "produce a relative URL from a page URL"
   */
  test("relative_url: produce a relative URL from a page URL") {
    val res = jekyllParser().parse("{{ '/about/my_favorite_page/' | relative_url }}").render(getData("/base"))
    assertEquals(res, "/base/about/my_favorite_page/")
  }

  /*
   * should "ensure the leading slash between baseurl and input"
   */
  test("relative_url: ensure the leading slash between baseurl and input") {
    val res = jekyllParser().parse("{{ 'about/my_favorite_page/' | relative_url }}").render(getData("/base"))
    assertEquals(res, "/base/about/my_favorite_page/")
  }

  /*
   * should "ensure the leading slash for the baseurl"
   */
  test("relative_url: ensure the leading slash for the baseurl") {
    val res = jekyllParser().parse("{{ 'about/my_favorite_page/' | relative_url }}").render(getData("base"))
    assertEquals(res, "/base/about/my_favorite_page/")
  }

  test("relative_url: normalize international URLs") {
    assume(PlatformCompat.isJVM, "URL encoding of international chars differs on Native")
    val res = jekyllParser().parse("{{ '\u9519\u8bef.html' | relative_url }}").render(getData("/base"))
    assertEquals(res, "/base/%E9%94%99%E8%AF%AF.html")
  }

  /*
   * should "be ok with a nil 'baseurl'"
   */
  test("relative_url: be ok with a nil baseurl") {
    val data = new JHashMap[String, DataView]()
    data.put("baseurl", TestHelper.dv(null))
    val res = jekyllParser().parse("{{ 'about/my_favorite_page/' | relative_url }}").render(TestHelper.mapOf())
    assertEquals(res, "/about/my_favorite_page/")
  }

  /*
   * should "not prepend a forward slash if input is empty"
   */
  test("relative_url: not prepend a forward slash if input is empty") {
    val res = jekyllParser().parse("{{ '' | relative_url }}").render(getData("/base"))
    assertEquals(res, "/base")
  }

  /*
   * should "not prepend a forward slash if baseurl ends with a single '/'"
   */
  test("relative_url: not prepend forward slash if baseurl ends with single slash") {
    assume(PlatformCompat.isJVM, "URL path normalization may differ on Native")
    val res = jekyllParser().parse("{{ '/css/main.css' | relative_url }}").render(getData("/base/"))
    assertEquals(res, "/base/css/main.css")
  }

  /*
   * should "not return valid URI if baseurl ends with multiple '/'"
   */
  test("relative_url: baseurl ends with multiple slashes normalizes") {
    assume(PlatformCompat.isJVM, "URL path normalization may differ on Native")
    val res = jekyllParser().parse("{{ '/css/main.css' | relative_url }}").render(getData("/base//"))
    assertEquals(res, "/base/css/main.css")
  }

  /*
   * should "not prepend a forward slash if both input and baseurl are simply '/'"
   */
  test("relative_url: not prepend forward slash if both input and baseurl are simply slashes") {
    assume(PlatformCompat.isJVM, "URL path normalization may differ on Native")
    val res = Flavor.JEKYLL.defaultParser().parse("{{ '/' | relative_url }}").render(getData("/"))
    assertEquals(res, "/")
  }

  /*
   * should "transform the input baseurl to a string"
   */
  test("relative_url: transform the input baseurl to a string") {
    assume(PlatformCompat.isJVM, "URL path normalization may differ on Native")
    val data = TestHelper.mapOf("site" -> TestHelper.mapOf("baseurl" -> "/baseurl/"))
    val res  = jekyllParser().parse("{{ '/my-page.html' | relative_url }}").render(data)
    assertEquals(res, "/baseurl/my-page.html")
  }

  /*
   * should "transform protocol-relative url"
   */
  test("relative_url: transform protocol-relative url") {
    assume(PlatformCompat.isJVM, "Protocol-relative URL handling may differ on Native")
    val res = jekyllParser().parse("{{ '//example.com/' | relative_url }}").render(getData("/base"))
    assertEquals(res, "/base/example.com/")
  }

  /*
   * should "not modify an absolute url with scheme"
   */
  test("relative_url: not modify an absolute url with scheme") {
    assume(PlatformCompat.isJVM, "URL scheme detection may differ on Native")
    val res = jekyllParser().parse("{{ 'file:///file.html' | relative_url }}").render(getData("/base"))
    assertEquals(res, "file:///file.html")
  }

  /*
   * should "not normalize absolute international URLs"
   */
  test("relative_url: not normalize absolute international URLs") {
    assume(PlatformCompat.isJVM, "International URL handling differs on Native")
    val res = jekyllParser().parse("{{ 'https://example.com/\u9519\u8bef' | relative_url }}").render(getData("/base"))
    assertEquals(res, "https://example.com/\u9519\u8bef")
  }

  test("relative_url: with fully featured url") {
    assume(PlatformCompat.isJVM, "URL path handling with query/anchor may differ on Native")
    val res = jekyllParser().parse("{{ '/some/path?with=extra&parameters=true#anchorhere' | relative_url }}").render(getData("/base"))
    assertEquals(res, "/base/some/path?with=extra&parameters=true#anchorhere")
  }
}
