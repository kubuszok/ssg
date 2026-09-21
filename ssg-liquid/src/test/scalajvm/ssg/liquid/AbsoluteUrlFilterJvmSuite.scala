/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** The one test of liqp's filters/Absolute_UrlTest.java that hands the template an arbitrary object as data.
  *
  * JVM-only: reading an object that is neither a map, a collection nor a scalar goes through bean reflection, which Scala.js and Scala Native do not have. The other 13 tests are in the shared
  * AbsoluteUrlFilterSuite.
  */
final class AbsoluteUrlFilterJvmSuite extends munit.FunSuite {

  // site.baseurl and site.config.url
  private def getData(siteUrl: Any, baseurl: String): JHashMap[String, DataView] = {
    val siteMap = new JHashMap[String, DataView]()
    siteMap.put("baseurl", TestHelper.dv(baseurl))
    val config: JHashMap[String, DataView] = new JHashMap[String, DataView]()
    config.put("url", TestHelper.dv(siteUrl))
    siteMap.put("config", TestHelper.dv(config))
    val result = new JHashMap[String, DataView]()
    result.put("site", TestHelper.dv(siteMap))
    result
  }

  private val jekyllParser: TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()

  /*
   * should "transform the input URL to a string"
   */
  test("absolute_url: transform input URL to string") {
    val template = jekyllParser.parse("{{ '/my-page.html' | absolute_url }}")
    val data     = getData(new Object() {
                         override def toString: String = "http://example.org"
                       },
                       null
    )
    assertEquals(template.render(data), "http://example.org/my-page.html")
  }
}
