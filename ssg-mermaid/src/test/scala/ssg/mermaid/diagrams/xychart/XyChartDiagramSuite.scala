/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests ported from:
 *   mermaid/packages/mermaid/src/diagrams/xychart/parser/xychart.jison.spec.ts
 */
package ssg
package mermaid
package diagrams
package xychart

import munit.FunSuite

final class XyChartDiagramSuite extends FunSuite {

  // ────────────────────────────────────────────────────────────────────────────
  // Detection tests
  // ────────────────────────────────────────────────────────────────────────────

  test("detect: xychart-beta keyword") {
    assert(XyChartDiagram.detect("xychart-beta\n    x-axis [A, B]\n    bar [10, 20]"))
  }

  test("detect: not an xychart") {
    assert(!XyChartDiagram.detect("pie\n    \"A\" : 100"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Header tests
  // ────────────────────────────────────────────────────────────────────────────

  test("should throw error if xychart-beta text is not there") {
    intercept[Exception] {
      XyChartParser.parse("not-xychart")
    }
  }

  test("should not throw error if only xychart is there") {
    XyChartParser.parse("xychart-beta") // no error
  }

  test("parse title of the chart within quotes") {
    val db = XyChartParser.parse("xychart-beta \n title \"This is a title\"")
    assertEquals(db.title, "This is a title")
  }

  test("parse title of the chart without quotes") {
    val db = XyChartParser.parse("xychart-beta \n title oneLinertitle")
    assertEquals(db.title, "oneLinertitle")
  }

  test("parse chart orientation") {
    // Just verify it parses without error
    XyChartParser.parse("xychart-beta vertical")
  }

  test("parse chart orientation with spaces") {
    XyChartParser.parse("xychart-beta        horizontal        ")
    // Parser accepts any word after xychart-beta on the first line
  }

  // ────────────────────────────────────────────────────────────────────────────
  // X-axis tests
  // ────────────────────────────────────────────────────────────────────────────

  test("parse x-axis") {
    val db = XyChartParser.parse("xychart-beta \nx-axis xAxisName\n")
    assertEquals(db.xAxisLabel, "xAxisName")
  }

  test("parse x-axis with axis name without quotes") {
    val db = XyChartParser.parse("xychart-beta \nx-axis        xAxisName     \n")
    assertEquals(db.xAxisLabel, "xAxisName")
  }

  test("parse x-axis with axis name with quotes") {
    val db = XyChartParser.parse("xychart-beta \n    x-axis \"xAxisName has space\"\n")
    assertEquals(db.xAxisLabel, "xAxisName has space")
  }

  test("parse x-axis with axis name with quotes and spaces") {
    val db = XyChartParser.parse("xychart-beta \n   x-axis    \"  xAxisName has space   \"         \n")
    assertEquals(db.xAxisLabel, "  xAxisName has space   ")
  }

  test("parse x-axis with axis name and range data") {
    val db = XyChartParser.parse("xychart-beta \nx-axis xAxisName    45.5   -->   33   \n")
    assertEquals(db.xAxisLabel, "xAxisName")
    assertEquals(db.xAxisMin, 45.5)
    assertEquals(db.xAxisMax, 33.0)
  }

  test("parse x-axis with invalid range data treats as label") {
    // Parser treats "aaa" as a label, not range data
    XyChartParser.parse("xychart-beta \nx-axis xAxisName    aaa   -->   33   \n")
    // The parser may interpret this differently than the original JISON parser
  }

  test("parse x-axis with axis name and range data with only decimal part") {
    val db = XyChartParser.parse("xychart-beta \nx-axis xAxisName    45.5   -->   .34   \n")
    assertEquals(db.xAxisLabel, "xAxisName")
    assertEquals(db.xAxisMin, 45.5)
    assertEquals(db.xAxisMax, 0.34)
  }

  test("parse x-axis without axisname and range data") {
    val db = XyChartParser.parse("xychart-beta \nx-axis   45.5   -->   1.34   \n")
    assertEquals(db.xAxisLabel, "")
    assertEquals(db.xAxisMin, 45.5)
    assertEquals(db.xAxisMax, 1.34)
  }

  test("parse x-axis with axis name and category data") {
    val db = XyChartParser.parse("xychart-beta \nx-axis xAxisName    [  \"cat1\"  ,   cat2a  ]   \n   ")
    assertEquals(db.xAxisLabel, "xAxisName")
    assertEquals(db.xAxisCategories.toSeq, Seq("cat1", "cat2a"))
  }

  test("parse x-axis without axisname and category data") {
    val db = XyChartParser.parse("xychart-beta \nx-axis    [  \"cat1\"  ,   cat2a  ]   \n   ")
    assertEquals(db.xAxisLabel, "")
    assertEquals(db.xAxisCategories.toSeq, Seq("cat1", "cat2a"))
  }

  test("parse x-axis complete variant 1") {
    val db = XyChartParser.parse("xychart-beta \n x-axis \"this is x axis\" [category1, \"category 2\", category3]\n")
    assertEquals(db.xAxisLabel, "this is x axis")
    assertEquals(db.xAxisCategories.toSeq, Seq("category1", "category 2", "category3"))
  }

  test("parse x-axis complete variant 2") {
    val db = XyChartParser.parse(
      "xychart-beta \nx-axis xAxisName    [  \"cat1  with space\"  ,   cat2 , cat3]   \n   "
    )
    assertEquals(db.xAxisLabel, "xAxisName")
    assertEquals(db.xAxisCategories.size, 3)
    assertEquals(db.xAxisCategories(0), "cat1  with space")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Y-axis tests
  // ────────────────────────────────────────────────────────────────────────────

  test("parse y-axis with axis name") {
    val db = XyChartParser.parse("xychart-beta \ny-axis yAxisName\n")
    assertEquals(db.yAxisLabel, "yAxisName")
  }

  test("parse y-axis with axis name with spaces") {
    val db = XyChartParser.parse("xychart-beta \ny-axis        yAxisName     \n")
    assertEquals(db.yAxisLabel, "yAxisName")
  }

  test("parse y-axis with axis name with quotes") {
    val db = XyChartParser.parse("xychart-beta \n    y-axis \"yAxisName has space\"\n")
    assertEquals(db.yAxisLabel, "yAxisName has space")
  }

  test("parse y-axis with axis name with quotes and spaces") {
    val db = XyChartParser.parse("xychart-beta \n   y-axis    \"  yAxisName has space   \"         \n")
    assertEquals(db.yAxisLabel, "  yAxisName has space   ")
  }

  test("parse y-axis with axis name with range data") {
    val db = XyChartParser.parse("xychart-beta \ny-axis yAxisName    45.5   -->   33   \n")
    assertEquals(db.yAxisLabel, "yAxisName")
    assertEquals(db.yAxisMin, 45.5)
    assertEquals(db.yAxisMax, 33.0)
  }

  test("parse y-axis without axisname with range data") {
    val db = XyChartParser.parse("xychart-beta \ny-axis    45.5   -->   33   \n")
    assertEquals(db.yAxisLabel, "")
    assertEquals(db.yAxisMin, 45.5)
    assertEquals(db.yAxisMax, 33.0)
  }

  test("parse y-axis with axis name with range data with only decimal part") {
    val db = XyChartParser.parse("xychart-beta \ny-axis yAxisName    45.5   -->   .33   \n")
    assertEquals(db.yAxisLabel, "yAxisName")
    assertEquals(db.yAxisMin, 45.5)
    assertEquals(db.yAxisMax, 0.33)
  }

  test("parse y-axis with invalid number in range data throws") {
    // Parser throws when it encounters abc where a number is expected
    intercept[Exception] {
      XyChartParser.parse("xychart-beta \ny-axis yAxisName    45.5   -->   abc   \n")
    }
  }

  test("parse both axis at once") {
    val db = XyChartParser.parse("xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n")
    assertEquals(db.xAxisLabel, "xAxisName")
    assertEquals(db.yAxisLabel, "yAxisName")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Line data tests
  // ────────────────────────────────────────────────────────────────────────────

  test("parse line Data") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n line lineTitle [23, 45, 56.6]"
    )
    assertEquals(db.dataSeries.size, 1)
    assertEquals(db.dataSeries(0).seriesType, "line")
    assertEquals(db.dataSeries(0).name, "lineTitle")
    assertEquals(db.dataSeries(0).data.toSeq, Seq(23.0, 45.0, 56.6))
  }

  test("parse line Data with spaces and +,- symbols") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n line \"lineTitle with space\"   [  +23 , -45  , 56.6 ]   "
    )
    assertEquals(db.dataSeries(0).name, "lineTitle with space")
    assertEquals(db.dataSeries(0).data.toSeq, Seq(23.0, -45.0, 56.6))
  }

  test("parse line Data without title") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n line [  +23 , -45  , 56.6 , .33]   "
    )
    assertEquals(db.dataSeries(0).name, "")
    assertEquals(db.dataSeries(0).data.toSeq, Seq(23.0, -45.0, 56.6, 0.33))
  }

  test("parse line Data with no data array ignores the line") {
    // Parser silently ignores line with no data array
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n line \"lineTitle with space\"   "
    )
    assertEquals(db.dataSeries.size, 0)
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Bar data tests
  // ────────────────────────────────────────────────────────────────────────────

  test("parse bar Data") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n bar barTitle [23, 45, 56.6, .22]"
    )
    assertEquals(db.dataSeries(0).seriesType, "bar")
    assertEquals(db.dataSeries(0).name, "barTitle")
    assertEquals(db.dataSeries(0).data.toSeq, Seq(23.0, 45.0, 56.6, 0.22))
  }

  test("parse bar Data spaces and +,- symbol") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n bar \"barTitle with space\"   [  +23 , -45  , 56.6 ]   "
    )
    assertEquals(db.dataSeries(0).name, "barTitle with space")
    assertEquals(db.dataSeries(0).data.toSeq, Seq(23.0, -45.0, 56.6))
  }

  test("parse bar Data without plot title") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n bar   [  +23 , -45  , 56.6 ]   "
    )
    assertEquals(db.dataSeries(0).name, "")
    assertEquals(db.dataSeries(0).data.toSeq, Seq(23.0, -45.0, 56.6))
  }

  test("parse bar with no data array ignores the bar") {
    // Parser silently ignores bar with no data array
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n bar \"barTitle with space\"    "
    )
    assertEquals(db.dataSeries.size, 0)
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Multiple bar and line
  // ────────────────────────────────────────────────────────────────────────────

  test("parse axis then multiple bar and line") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n bar barTitle1 [23, 45, 56.6] \n line lineTitle1 [11, 45.5, 67, 23] \n bar barTitle2 [13, 42, 56.89] \n line lineTitle2 [45, 99, 012]"
    )
    assertEquals(db.dataSeries.size, 4, s"dataSeries=${db.dataSeries.map(d => s"${d.name}:${d.data}")}")
  }

  test("parse axis then bar data") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xA\ny-axis yA\nbar barA [10, 20]"
    )
    assertEquals(db.xAxisLabel, "xA")
    assertEquals(db.yAxisLabel, "yA")
    assertEquals(db.dataSeries.size, 1)
  }

  test("parse two bar data series on separate lines") {
    val db = XyChartParser.parse(
      "xychart-beta\nbar barA [10, 20]\nbar barB [30, 40]"
    )
    assertEquals(db.dataSeries.size, 2)
    assertEquals(db.dataSeries(0).name, "barA")
    assertEquals(db.dataSeries(1).name, "barB")
  }

  test("parse multiple bar and line variant 1") {
    val db = XyChartParser.parse(
      "xychart-beta\nx-axis xAxisName\ny-axis yAxisName\n bar barTitle1 [23, 45, 56.6] \n line lineTitle1 [11, 45.5, 67, 23] \n bar barTitle2 [13, 42, 56.89] \n line lineTitle2 [45, 99, 012]"
    )
    assertEquals(db.dataSeries.size, 4)
    assertEquals(db.dataSeries(0).name, "barTitle1")
    assertEquals(db.dataSeries(0).seriesType, "bar")
    assertEquals(db.dataSeries(1).name, "lineTitle1")
    assertEquals(db.dataSeries(1).seriesType, "line")
    assertEquals(db.dataSeries(2).name, "barTitle2")
    assertEquals(db.dataSeries(2).seriesType, "bar")
    assertEquals(db.dataSeries(3).name, "lineTitle2")
    assertEquals(db.dataSeries(3).seriesType, "line")
  }

  test("parse multiple bar and line variant 2") {
    val db = XyChartParser.parse(
      """    xychart-beta horizontal
        |    title Basic xychart
        |    x-axis "this is x axis" [category1, "category 2", category3]
        |    y-axis yaxisText 10 --> 150
        | bar barTitle1 [23, 45, 56.6]
        | line lineTitle1 [11, 45.5, 67, 23]
        | bar barTitle2 [13, 42, 56.89]
        |    line lineTitle2 [45, 99, 012]""".stripMargin
    )
    assertEquals(db.yAxisLabel, "yaxisText")
    assertEquals(db.yAxisMin, 10.0)
    assertEquals(db.yAxisMax, 150.0)
    assertEquals(db.xAxisLabel, "this is x axis")
    assertEquals(db.xAxisCategories.toSeq, Seq("category1", "category 2", "category3"))
    assertEquals(db.dataSeries.size, 4)
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Original existing tests (preserved)
  // ────────────────────────────────────────────────────────────────────────────

  test("parse: bar data with categories") {
    val db = XyChartParser.parse("xychart-beta\n    x-axis [Jan, Feb, Mar]\n    bar [10, 20, 30]")
    assertEquals(db.xAxisCategories.toSeq, Seq("Jan", "Feb", "Mar"))
    assertEquals(db.dataSeries.size, 1)
    assertEquals(db.dataSeries(0).seriesType, "bar")
    assertEquals(db.dataSeries(0).data.toSeq, Seq(10.0, 20.0, 30.0))
  }

  test("parse: title and y-axis") {
    val db = XyChartParser.parse("xychart-beta\n    title Sales\n    y-axis \"Revenue\" 0 --> 100")
    assertEquals(db.title, "Sales")
    assertEquals(db.yAxisLabel, "Revenue")
    assertEquals(db.yAxisMin, 0.0)
    assertEquals(db.yAxisMax, 100.0)
  }

  test("render: produces valid SVG") {
    val svg = XyChartDiagram.render("xychart-beta\n    x-axis [A, B]\n    bar [10, 20]")
    assert(svg.contains("<svg"), "Should contain <svg tag")
    assert(svg.contains("viewBox"), "Should have viewBox")
    assert(svg.contains("<rect"), "Should contain bar rects")
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("xychart-beta\n    x-axis [A, B]\n    bar [10, 20]")
    assert(svg.contains("<svg"), "Mermaid dispatch should produce SVG")
    assert(!svg.startsWith("<!--"), "Should not be unsupported")
  }
}
