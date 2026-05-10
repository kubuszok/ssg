/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: khroma (npm package used by mermaid for color manipulation)
 * Original: Copyright (c) Fabio Spampinato
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: CSS named color lookup map — all 148 CSS Level 4 named colors
 *   Idiom: Immutable Map keyed by lowercase name
 *   Renames: khroma → ssg.mermaid.color
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package color

import ssg.commons.Nullable

/** CSS named color lookup.
  *
  * Contains all 148 CSS Level 4 named colors as defined in the CSS Color Module Level 4 specification. Each entry maps a lowercase color name to its [[RgbaColor]] value.
  *
  * @see
  *   [[https://www.w3.org/TR/css-color-4/#named-colors CSS Color Module Level 4]]
  */
object NamedColors {

  /** Looks up a named CSS color (case-insensitive).
    *
    * @param name
    *   the color name (e.g. "red", "cornflowerblue")
    * @return
    *   the color, or empty if the name is not recognized
    */
  def get(name: String): Nullable[RgbaColor] =
    colors.get(name.toLowerCase) match {
      case Some(c) => Nullable(c)
      case None    => Nullable.empty
    }

  /** Returns true if the given name is a recognized CSS color name. */
  def contains(name: String): Boolean =
    colors.contains(name.toLowerCase)

  /** The complete map of CSS named colors.
    *
    * All 148 CSS Level 4 named colors, keyed by lowercase name.
    */
  // format: off
  private val colors: Map[String, RgbaColor] = Map(
    "aliceblue"            -> RgbaColor(240, 248, 255),
    "antiquewhite"         -> RgbaColor(250, 235, 215),
    "aqua"                 -> RgbaColor(0, 255, 255),
    "aquamarine"           -> RgbaColor(127, 255, 212),
    "azure"                -> RgbaColor(240, 255, 255),
    "beige"                -> RgbaColor(245, 245, 220),
    "bisque"               -> RgbaColor(255, 228, 196),
    "black"                -> RgbaColor(0, 0, 0),
    "blanchedalmond"       -> RgbaColor(255, 235, 205),
    "blue"                 -> RgbaColor(0, 0, 255),
    "blueviolet"           -> RgbaColor(138, 43, 226),
    "brown"                -> RgbaColor(165, 42, 42),
    "burlywood"            -> RgbaColor(222, 184, 135),
    "cadetblue"            -> RgbaColor(95, 158, 160),
    "chartreuse"           -> RgbaColor(127, 255, 0),
    "chocolate"            -> RgbaColor(210, 105, 30),
    "coral"                -> RgbaColor(255, 127, 80),
    "cornflowerblue"       -> RgbaColor(100, 149, 237),
    "cornsilk"             -> RgbaColor(255, 248, 220),
    "crimson"              -> RgbaColor(220, 20, 60),
    "cyan"                 -> RgbaColor(0, 255, 255),
    "darkblue"             -> RgbaColor(0, 0, 139),
    "darkcyan"             -> RgbaColor(0, 139, 139),
    "darkgoldenrod"        -> RgbaColor(184, 134, 11),
    "darkgray"             -> RgbaColor(169, 169, 169),
    "darkgreen"            -> RgbaColor(0, 100, 0),
    "darkgrey"             -> RgbaColor(169, 169, 169),
    "darkkhaki"            -> RgbaColor(189, 183, 107),
    "darkmagenta"          -> RgbaColor(139, 0, 139),
    "darkolivegreen"       -> RgbaColor(85, 107, 47),
    "darkorange"           -> RgbaColor(255, 140, 0),
    "darkorchid"           -> RgbaColor(153, 50, 204),
    "darkred"              -> RgbaColor(139, 0, 0),
    "darksalmon"           -> RgbaColor(233, 150, 122),
    "darkseagreen"         -> RgbaColor(143, 188, 143),
    "darkslateblue"        -> RgbaColor(72, 61, 139),
    "darkslategray"        -> RgbaColor(47, 79, 79),
    "darkslategrey"        -> RgbaColor(47, 79, 79),
    "darkturquoise"        -> RgbaColor(0, 206, 209),
    "darkviolet"           -> RgbaColor(148, 0, 211),
    "deeppink"             -> RgbaColor(255, 20, 147),
    "deepskyblue"          -> RgbaColor(0, 191, 255),
    "dimgray"              -> RgbaColor(105, 105, 105),
    "dimgrey"              -> RgbaColor(105, 105, 105),
    "dodgerblue"           -> RgbaColor(30, 144, 255),
    "firebrick"            -> RgbaColor(178, 34, 34),
    "floralwhite"          -> RgbaColor(255, 250, 240),
    "forestgreen"          -> RgbaColor(34, 139, 34),
    "fuchsia"              -> RgbaColor(255, 0, 255),
    "gainsboro"            -> RgbaColor(220, 220, 220),
    "ghostwhite"           -> RgbaColor(248, 248, 255),
    "gold"                 -> RgbaColor(255, 215, 0),
    "goldenrod"            -> RgbaColor(218, 165, 32),
    "gray"                 -> RgbaColor(128, 128, 128),
    "green"                -> RgbaColor(0, 128, 0),
    "greenyellow"          -> RgbaColor(173, 255, 47),
    "grey"                 -> RgbaColor(128, 128, 128),
    "honeydew"             -> RgbaColor(240, 255, 240),
    "hotpink"              -> RgbaColor(255, 105, 180),
    "indianred"            -> RgbaColor(205, 92, 92),
    "indigo"               -> RgbaColor(75, 0, 130),
    "ivory"                -> RgbaColor(255, 255, 240),
    "khaki"                -> RgbaColor(240, 230, 140),
    "lavender"             -> RgbaColor(230, 230, 250),
    "lavenderblush"        -> RgbaColor(255, 240, 245),
    "lawngreen"            -> RgbaColor(124, 252, 0),
    "lemonchiffon"         -> RgbaColor(255, 250, 205),
    "lightblue"            -> RgbaColor(173, 216, 230),
    "lightcoral"           -> RgbaColor(240, 128, 128),
    "lightcyan"            -> RgbaColor(224, 255, 255),
    "lightgoldenrodyellow" -> RgbaColor(250, 250, 210),
    "lightgray"            -> RgbaColor(211, 211, 211),
    "lightgreen"           -> RgbaColor(144, 238, 144),
    "lightgrey"            -> RgbaColor(211, 211, 211),
    "lightpink"            -> RgbaColor(255, 182, 193),
    "lightsalmon"          -> RgbaColor(255, 160, 122),
    "lightseagreen"        -> RgbaColor(32, 178, 170),
    "lightskyblue"         -> RgbaColor(135, 206, 250),
    "lightslategray"       -> RgbaColor(119, 136, 153),
    "lightslategrey"       -> RgbaColor(119, 136, 153),
    "lightsteelblue"       -> RgbaColor(176, 196, 222),
    "lightyellow"          -> RgbaColor(255, 255, 224),
    "lime"                 -> RgbaColor(0, 255, 0),
    "limegreen"            -> RgbaColor(50, 205, 50),
    "linen"                -> RgbaColor(250, 240, 230),
    "magenta"              -> RgbaColor(255, 0, 255),
    "maroon"               -> RgbaColor(128, 0, 0),
    "mediumaquamarine"     -> RgbaColor(102, 205, 170),
    "mediumblue"           -> RgbaColor(0, 0, 205),
    "mediumorchid"         -> RgbaColor(186, 85, 211),
    "mediumpurple"         -> RgbaColor(147, 112, 219),
    "mediumseagreen"       -> RgbaColor(60, 179, 113),
    "mediumslateblue"      -> RgbaColor(123, 104, 238),
    "mediumspringgreen"    -> RgbaColor(0, 250, 154),
    "mediumturquoise"      -> RgbaColor(72, 209, 204),
    "mediumvioletred"      -> RgbaColor(199, 21, 133),
    "midnightblue"         -> RgbaColor(25, 25, 112),
    "mintcream"            -> RgbaColor(245, 255, 250),
    "mistyrose"            -> RgbaColor(255, 228, 225),
    "moccasin"             -> RgbaColor(255, 228, 181),
    "navajowhite"          -> RgbaColor(255, 222, 173),
    "navy"                 -> RgbaColor(0, 0, 128),
    "oldlace"              -> RgbaColor(253, 245, 230),
    "olive"                -> RgbaColor(128, 128, 0),
    "olivedrab"            -> RgbaColor(107, 142, 35),
    "orange"               -> RgbaColor(255, 165, 0),
    "orangered"            -> RgbaColor(255, 69, 0),
    "orchid"               -> RgbaColor(218, 112, 214),
    "palegoldenrod"        -> RgbaColor(238, 232, 170),
    "palegreen"            -> RgbaColor(152, 251, 152),
    "paleturquoise"        -> RgbaColor(175, 238, 238),
    "palevioletred"        -> RgbaColor(219, 112, 147),
    "papayawhip"           -> RgbaColor(255, 239, 213),
    "peachpuff"            -> RgbaColor(255, 218, 185),
    "peru"                 -> RgbaColor(205, 133, 63),
    "pink"                 -> RgbaColor(255, 192, 203),
    "plum"                 -> RgbaColor(221, 160, 221),
    "powderblue"           -> RgbaColor(176, 224, 230),
    "purple"               -> RgbaColor(128, 0, 128),
    "rebeccapurple"        -> RgbaColor(102, 51, 153),
    "red"                  -> RgbaColor(255, 0, 0),
    "rosybrown"            -> RgbaColor(188, 143, 143),
    "royalblue"            -> RgbaColor(65, 105, 225),
    "saddlebrown"          -> RgbaColor(139, 69, 19),
    "salmon"               -> RgbaColor(250, 128, 114),
    "sandybrown"           -> RgbaColor(244, 164, 96),
    "seagreen"             -> RgbaColor(46, 139, 87),
    "seashell"             -> RgbaColor(255, 245, 238),
    "sienna"               -> RgbaColor(160, 82, 45),
    "silver"               -> RgbaColor(192, 192, 192),
    "skyblue"              -> RgbaColor(135, 206, 235),
    "slateblue"            -> RgbaColor(106, 90, 205),
    "slategray"            -> RgbaColor(112, 128, 144),
    "slategrey"            -> RgbaColor(112, 128, 144),
    "snow"                 -> RgbaColor(255, 250, 250),
    "springgreen"          -> RgbaColor(0, 255, 127),
    "steelblue"            -> RgbaColor(70, 130, 180),
    "tan"                  -> RgbaColor(210, 180, 140),
    "teal"                 -> RgbaColor(0, 128, 128),
    "thistle"              -> RgbaColor(216, 191, 216),
    "tomato"               -> RgbaColor(255, 99, 71),
    "turquoise"            -> RgbaColor(64, 224, 208),
    "violet"               -> RgbaColor(238, 130, 238),
    "wheat"                -> RgbaColor(245, 222, 179),
    "white"                -> RgbaColor(255, 255, 255),
    "whitesmoke"           -> RgbaColor(245, 245, 245),
    "yellow"               -> RgbaColor(255, 255, 0),
    "yellowgreen"          -> RgbaColor(154, 205, 50)
  )
  // format: on
}
