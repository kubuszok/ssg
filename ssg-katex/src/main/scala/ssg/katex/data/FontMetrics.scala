/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file contains metrics regarding fonts and individual symbols. The sigma
 * and xi variables, as well as the metricMap map contain data extracted from
 * TeX, TeX font metrics, and the TTF files. These data are then exposed via the
 * `metrics` variable and the getCharacterMetrics function.
 *
 * Original source: katex src/fontMetrics.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

import ssg.commons.Nullable

/** Character metrics for a single glyph: depth, height, italic correction, skew (kern from the character to the corresponding \skewchar), and width.
  */
final case class CharacterMetrics(
  depth:  Double,
  height: Double,
  italic: Double,
  skew:   Double,
  width:  Double
)

/** Font metrics for a given size. Contains all sigma/xi parameters plus cssEmPerMu. The fields are stored in a map for dynamic access.
  */
final class FontMetrics private (private val values: Map[String, Double]) {
  def cssEmPerMu:           Double = values("cssEmPerMu")
  def slant:                Double = values("slant")
  def space:                Double = values("space")
  def stretch:              Double = values("stretch")
  def shrink:               Double = values("shrink")
  def xHeight:              Double = values("xHeight")
  def quad:                 Double = values("quad")
  def extraSpace:           Double = values("extraSpace")
  def num1:                 Double = values("num1")
  def num2:                 Double = values("num2")
  def num3:                 Double = values("num3")
  def denom1:               Double = values("denom1")
  def denom2:               Double = values("denom2")
  def sup1:                 Double = values("sup1")
  def sup2:                 Double = values("sup2")
  def sup3:                 Double = values("sup3")
  def sub1:                 Double = values("sub1")
  def sub2:                 Double = values("sub2")
  def supDrop:              Double = values("supDrop")
  def subDrop:              Double = values("subDrop")
  def delim1:               Double = values("delim1")
  def delim2:               Double = values("delim2")
  def axisHeight:           Double = values("axisHeight")
  def defaultRuleThickness: Double = values("defaultRuleThickness")
  def bigOpSpacing1:        Double = values("bigOpSpacing1")
  def bigOpSpacing2:        Double = values("bigOpSpacing2")
  def bigOpSpacing3:        Double = values("bigOpSpacing3")
  def bigOpSpacing4:        Double = values("bigOpSpacing4")
  def bigOpSpacing5:        Double = values("bigOpSpacing5")
  def sqrtRuleThickness:    Double = values("sqrtRuleThickness")
  def ptPerEm:              Double = values("ptPerEm")
  def doubleRuleSep:        Double = values("doubleRuleSep")
  def arrayRuleWidth:       Double = values("arrayRuleWidth")
  def fboxsep:              Double = values("fboxsep")
  def fboxrule:             Double = values("fboxrule")

  /** Dynamic property access by name. */
  def apply(key: String): Double = values(key)

  def get(key: String): Option[Double] = values.get(key)
}

object FontMetrics {

  // In TeX, there are actually three sets of dimensions, one for each of
  // textstyle (size index 5 and higher: >=9pt), scriptstyle (size index 3 and 4:
  // 7-8pt), and scriptscriptstyle (size index 1 and 2: 5-6pt).  These are
  // provided in the arrays below, in that order.
  //
  // The font metrics are stored in fonts cmsy10, cmsy7, and cmsy5 respectively.
  // This was determined by running the following script:
  //
  //     latex -interaction=nonstopmode \
  //     '\documentclass{article}\usepackage{amsmath}\begin{document}' \
  //     '$a$ \expandafter\show\the\textfont2' \
  //     '\expandafter\show\the\scriptfont2' \
  //     '\expandafter\show\the\scriptscriptfont2' \
  //     '\stop'
  //
  // The metrics themselves were retrieved using the following commands:
  //
  //     tftopl cmsy10
  //     tftopl cmsy7
  //     tftopl cmsy5
  //
  // The output of each of these commands is quite lengthy.  The only part we
  // care about is the FONTDIMEN section. Each value is measured in EMs.
  private val sigmasAndXis: Map[String, (Double, Double, Double)] = Map(
    "slant" -> (0.250, 0.250, 0.250), // sigma1
    "space" -> (0.000, 0.000, 0.000), // sigma2
    "stretch" -> (0.000, 0.000, 0.000), // sigma3
    "shrink" -> (0.000, 0.000, 0.000), // sigma4
    "xHeight" -> (0.431, 0.431, 0.431), // sigma5
    "quad" -> (1.000, 1.171, 1.472), // sigma6
    "extraSpace" -> (0.000, 0.000, 0.000), // sigma7
    "num1" -> (0.677, 0.732, 0.925), // sigma8
    "num2" -> (0.394, 0.384, 0.387), // sigma9
    "num3" -> (0.444, 0.471, 0.504), // sigma10
    "denom1" -> (0.686, 0.752, 1.025), // sigma11
    "denom2" -> (0.345, 0.344, 0.532), // sigma12
    "sup1" -> (0.413, 0.503, 0.504), // sigma13
    "sup2" -> (0.363, 0.431, 0.404), // sigma14
    "sup3" -> (0.289, 0.286, 0.294), // sigma15
    "sub1" -> (0.150, 0.143, 0.200), // sigma16
    "sub2" -> (0.247, 0.286, 0.400), // sigma17
    "supDrop" -> (0.386, 0.353, 0.494), // sigma18
    "subDrop" -> (0.050, 0.071, 0.100), // sigma19
    "delim1" -> (2.390, 1.700, 1.980), // sigma20
    "delim2" -> (1.010, 1.157, 1.420), // sigma21
    "axisHeight" -> (0.250, 0.250, 0.250), // sigma22

    // These font metrics are extracted from TeX by using tftopl on cmex10.tfm;
    // they correspond to the font parameters of the extension fonts (family 3).
    // See the TeXbook, page 441. In AMSTeX, the extension fonts scale; to
    // match cmex7, we'd use cmex7.tfm values for script and scriptscript
    // values.
    "defaultRuleThickness" -> (0.04, 0.049, 0.049), // xi8; cmex7: 0.049
    "bigOpSpacing1" -> (0.111, 0.111, 0.111), // xi9
    "bigOpSpacing2" -> (0.166, 0.166, 0.166), // xi10
    "bigOpSpacing3" -> (0.2, 0.2, 0.2), // xi11
    "bigOpSpacing4" -> (0.6, 0.611, 0.611), // xi12; cmex7: 0.611
    "bigOpSpacing5" -> (0.1, 0.143, 0.143), // xi13; cmex7: 0.143

    // The \sqrt rule width is taken from the height of the surd character.
    // Since we use the same font at all sizes, this thickness doesn't scale.
    "sqrtRuleThickness" -> (0.04, 0.04, 0.04),

    // This value determines how large a pt is, for metrics which are defined
    // in terms of pts.
    // This value is also used in katex.scss; if you change it make sure the
    // values match.
    "ptPerEm" -> (10.0, 10.0, 10.0),

    // The space between adjacent `|` columns in an array definition. From
    // `\showthe\doublerulesep` in LaTeX. Equals 2.0 / ptPerEm.
    "doubleRuleSep" -> (0.2, 0.2, 0.2),

    // The width of separator lines in {array} environments. From
    // `\showthe\arrayrulewidth` in LaTeX. Equals 0.4 / ptPerEm.
    "arrayRuleWidth" -> (0.04, 0.04, 0.04),

    // Two values from LaTeX source2e:
    "fboxsep" -> (0.3, 0.3, 0.3), //        3 pt / ptPerEm
    "fboxrule" -> (0.04, 0.04, 0.04) // 0.4 pt / ptPerEm
  )

  // This map contains a mapping from font name and character code to character
  // metrics, including height, depth, italic correction, and skew (kern from the
  // character to the corresponding \skewchar)
  // This map is generated via `make metrics`. It should not be changed manually.

  // These are very rough approximations.  We default to Times New Roman which
  // should have Latin-1 and Cyrillic characters, but may not depending on the
  // operating system.  The metrics do not account for extra height from the
  // accents.  In the case of Cyrillic characters which have both ascenders and
  // descenders we prefer approximations with ascenders, primarily to prevent
  // the fraction bar or root line from intersecting the glyph.
  // TODO(kevinb) allow union of multiple glyph metrics for better accuracy.
  private val extraCharacterMap: Map[Char, Char] = Map(
    // Latin-1
    'Å' -> 'A',
    'Ð' -> 'D',
    'Þ' -> 'o',
    'å' -> 'a',
    'ð' -> 'd',
    'þ' -> 'o',
    // Cyrillic
    'А' -> 'A',
    'Б' -> 'B',
    'В' -> 'B',
    'Г' -> 'F',
    'Д' -> 'A',
    'Е' -> 'E',
    'Ж' -> 'K',
    'З' -> '3',
    'И' -> 'N',
    'Й' -> 'N',
    'К' -> 'K',
    'Л' -> 'N',
    'М' -> 'M',
    'Н' -> 'H',
    'О' -> 'O',
    'П' -> 'N',
    'Р' -> 'P',
    'С' -> 'C',
    'Т' -> 'T',
    'У' -> 'y',
    'Ф' -> 'O',
    'Х' -> 'X',
    'Ц' -> 'U',
    'Ч' -> 'h',
    'Ш' -> 'W',
    'Щ' -> 'W',
    'Ъ' -> 'B',
    'Ы' -> 'X',
    'Ь' -> 'B',
    'Э' -> '3',
    'Ю' -> 'X',
    'Я' -> 'R',
    'а' -> 'a',
    'б' -> 'b',
    'в' -> 'a',
    'г' -> 'r',
    'д' -> 'y',
    'е' -> 'e',
    'ж' -> 'm',
    'з' -> 'e',
    'и' -> 'n',
    'й' -> 'n',
    'к' -> 'n',
    'л' -> 'n',
    'м' -> 'm',
    'н' -> 'n',
    'о' -> 'o',
    'п' -> 'n',
    'р' -> 'p',
    'с' -> 'c',
    'т' -> 'o',
    'у' -> 'y',
    'ф' -> 'b',
    'х' -> 'x',
    'ц' -> 'n',
    'ч' -> 'n',
    'ш' -> 'w',
    'щ' -> 'w',
    'ъ' -> 'a',
    'ы' -> 'm',
    'ь' -> 'a',
    'э' -> 'e',
    'ю' -> 'm',
    'я' -> 'r'
  )

  /** The mutable metric map — allows font metrics to be added/overridden. */
  private val metricMap: scala.collection.mutable.Map[String, Map[Int, Array[Double]]] = {
    val m = scala.collection.mutable.Map.empty[String, Map[Int, Array[Double]]]
    FontMetricsData.metricMap.foreach { case (fontName, charMap) =>
      m(fontName) = charMap
    }
    m
  }

  /** This function adds new font metrics to default metricMap. It can also override existing metrics.
    */
  def setFontMetrics(fontName: String, metrics: Map[Int, Array[Double]]): Unit =
    metricMap(fontName) = metrics

  /** This function is a convenience function for looking up information in the metricMap table. It takes a character as a string, and a font.
    *
    * Note: the `width` property may be undefined if fontMetricsData wasn't built using `Make extended_metrics`.
    */
  def getCharacterMetrics(
    character: String,
    font:      String,
    mode:      Mode
  ): Nullable[CharacterMetrics] = {
    val fontMap = metricMap.getOrElse(font, throw new ParseError(s"Font metrics not found for font: $font."))

    var ch      = character.charAt(0).toInt
    var metrics = fontMap.get(ch)
    if (metrics.isEmpty && extraCharacterMap.contains(character.charAt(0))) {
      ch = extraCharacterMap(character.charAt(0)).toInt
      metrics = fontMap.get(ch)
    }

    if (metrics.isEmpty && mode == Mode.Text) {
      // We don't typically have font metrics for Asian scripts.
      // But since we support them in text mode, we need to return
      // some sort of metrics.
      // So if the character is in a script we support but we
      // don't have metrics for it, just use the metrics for
      // the Latin capital letter M. This is close enough because
      // we (currently) only care about the height of the glyph
      // not its width.
      if (UnicodeScripts.supportedCodepoint(ch)) {
        metrics = fontMap.get(77) // 77 is the charcode for 'M'
      }
    }

    metrics match {
      case Some(m) =>
        // In the original JS, out-of-bounds array access returns undefined
        // (which becomes 0/NaN). Guard with lift for user-provided metrics.
        Nullable(
          CharacterMetrics(
            depth = m(0),
            height = m(1),
            italic = if (m.length > 2) m(2) else 0.0,
            skew = if (m.length > 3) m(3) else 0.0,
            width = if (m.length > 4) m(4) else 0.0
          )
        )
      case None =>
        Nullable.Null
    }
  }

  private val fontMetricsBySizeIndex: Array[Nullable[FontMetrics]] =
    Array(Nullable.Null, Nullable.Null, Nullable.Null)

  /** Get the font metrics for a given size.
    */
  def getGlobalMetrics(size: Int): FontMetrics = {
    val sizeIndex: Int =
      if (size >= 5) 0
      else if (size >= 3) 1
      else 2

    fontMetricsBySizeIndex(sizeIndex) match {
      case cached if cached.isDefined => cached.get
      case _                          =>
        val builder = scala.collection.mutable.Map[String, Double](
          "cssEmPerMu" -> sigmasAndXis("quad").productElement(sizeIndex).asInstanceOf[Double] / 18.0
        )
        sigmasAndXis.foreach { case (key, triple) =>
          builder(key) = triple.productElement(sizeIndex).asInstanceOf[Double]
        }
        val metrics = new FontMetrics(builder.toMap)
        fontMetricsBySizeIndex(sizeIndex) = Nullable(metrics)
        metrics
    }
  }
}
