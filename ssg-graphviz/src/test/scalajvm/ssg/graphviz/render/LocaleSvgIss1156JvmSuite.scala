/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1156: locale-dependent number formatting leaks into the
 * SVG rendered by ssg-graphviz, via the shared graphs-commons formatting
 * infrastructure. Same bug class as resolved ISS-1153 (katex Units.makeEm):
 * the Scala `f"...%.Nf"` interpolator compiles to `String.format`, which on
 * the JVM uses `Locale.getDefault` — on a JVM whose default locale uses a
 * comma decimal separator (de_DE, pl_PL, ...) it emits `1,5` instead of
 * `1.5`.
 *
 * Offending sites reached by this render path (none live in ssg-graphviz
 * itself — they are the shared graphs-commons infrastructure):
 *   - ssg-graphs-commons/src/main/scala/ssg/graphs/commons/svg/PathData.scala:206
 *     — `private def fmt` uses `f"$rounded%.2f"`. Graphviz edges are built
 *     through ssg.graphs.commons.render.Curves.linear (DotRenderer.scala:423),
 *     which serializes coordinates via PathData.fmt into path `d`
 *     attributes; fractional edge endpoints become e.g. "1,50".
 *   - ssg-graphs-commons/src/main/scala/ssg/graphs/commons/util/FormatUtil.scala:24
 *     — `f"$rounded%.4f"` in the scientific-notation branch of
 *     `formatNumber`, used throughout DotRenderer (viewBox, transforms,
 *     polygon points: DotRenderer.scala:145,168,280-315).
 *
 * Because "," is a legitimate coordinate separator inside SVG path data, a
 * comma DECIMAL cannot be told apart from a coordinate separator by regex —
 * the corrupted output is syntactically valid path data with twice the
 * coordinates, silently wrong. The end-to-end assertion is therefore
 * locale-INDEPENDENCE: the SVG rendered under Locale.GERMANY must be
 * byte-identical to the SVG rendered under Locale.US (the layout pipeline
 * has no randomness — see the determinism control below).
 *
 * Expected values (cited per C11): the graphs-commons infrastructure ports/
 * replaces Mermaid JS code — PathData replaces d3-path and
 * FormatUtil.roundNumber ports original-src/mermaid/packages/mermaid/src/utils.ts:329-332
 * — and JS number stringification (ECMA-262 `Number::toString`/`toFixed`)
 * ALWAYS uses "." as the decimal separator, so the original infrastructure
 * is locale-independent by construction. SVG 1.1 §4.2 (<number>) and the
 * path-data grammar (§8.3.9) admit only "." as a decimal separator.
 *
 * JVM-only suite: `Locale.setDefault` is a JVM facility; the f-interpolator
 * only consults a default locale on the JVM.
 */
package ssg
package graphviz
package render

import java.util.Locale

import munit.FunSuite

class LocaleSvgIss1156JvmSuite extends FunSuite {

  // Neato (spring) layout, as used by GraphvizRendererSuite — force-directed
  // positions are fractional, so edge endpoints exercise PathData.fmt's
  // fractional branch.
  private val config: GraphvizConfig = GraphvizConfig(engine = LayoutEngine.Neato)

  private val dotInput = "digraph { A -> B; B -> C; C -> A }"

  // A fractional dot-decimal number inside a path d="..." attribute.
  private val dotDecimalInPathRe = """ d="[^"]*[0-9]\.[0-9]""".r

  private var savedLocale: Locale = Locale.getDefault

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    savedLocale = Locale.getDefault
    // GERMANY uses a comma decimal separator: f"${1.5}%.2f" → "1,50".
    Locale.setDefault(Locale.GERMANY)
  }

  override def afterEach(context: AfterEach): Unit = {
    // Restore unconditionally so a failure never leaks the locale into
    // neighbouring suites.
    Locale.setDefault(savedLocale)
    super.afterEach(context)
  }

  test("ISS-1156 red: rendered SVG is identical under Locale.GERMANY and Locale.US") {
    val svgDe = Graphviz.render(dotInput, config)
    Locale.setDefault(Locale.US)
    val svgUs = Graphviz.render(dotInput, config)
    assert(
      dotDecimalInPathRe.findFirstIn(svgUs).isDefined,
      s"sanity: the US render must contain fractional path coordinates so the " +
        s"comparison exercises PathData.fmt's fractional branch; svg: $svgUs"
    )
    assertEquals(svgDe, svgUs, "render output must not depend on the JVM default locale")
  }

  test("ISS-1156 red: under Locale.GERMANY path data still contains dot-decimal coordinates") {
    // PathData.fmt's own documented intent (PathData.scala:194: up to 2
    // decimals, trailing zeros stripped, '.'-only — see the class doc
    // example at PathData.scala:29-36) and SVG 1.1 §4.2: fractional
    // coordinates must keep their dot decimal regardless of locale. Under
    // the bug, f"%.2f" turns EVERY fractional coordinate's '.' into ',',
    // so no dot-decimal survives in the path data.
    val svg = Graphviz.render(dotInput, config)
    assert(
      dotDecimalInPathRe.findFirstIn(svg).isDefined,
      s"expected fractional dot-decimal coordinates in path data under Locale.GERMANY; svg: $svg"
    )
  }

  test("control: under Locale.US two renders are byte-identical (determinism)") {
    // Pins that the DE/US comparison above is sound: the pipeline is
    // deterministic, so any difference between locales is caused by the
    // locale alone.
    Locale.setDefault(Locale.US)
    val first  = Graphviz.render(dotInput, config)
    val second = Graphviz.render(dotInput, config)
    assert(
      dotDecimalInPathRe.findFirstIn(first).isDefined,
      s"sanity: the US render must contain fractional path coordinates; svg: $first"
    )
    assertEquals(first, second, "rendering must be deterministic under a fixed locale")
  }
}
