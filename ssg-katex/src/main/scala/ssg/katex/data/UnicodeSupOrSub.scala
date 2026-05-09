/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Helpers for Parser handling of Unicode (sub|super)script characters.
 *
 * Original source: katex src/unicodeSupOrSub.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

import scala.util.matching.Regex

/**
 * Helpers for Parser handling of Unicode (sub|super)script characters.
 */
object UnicodeSupOrSub {

  val unicodeSubRegEx: Regex =
    "^[₊₋₌₍₎₀₁₂₃₄₅₆₇₈₉ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓᵦᵧᵨᵩᵪ]".r

  val uSubsAndSups: Map[String, String] = Map(
    "₊" -> "+",
    "₋" -> "-",
    "₌" -> "=",
    "₍" -> "(",
    "₎" -> ")",
    "₀" -> "0",
    "₁" -> "1",
    "₂" -> "2",
    "₃" -> "3",
    "₄" -> "4",
    "₅" -> "5",
    "₆" -> "6",
    "₇" -> "7",
    "₈" -> "8",
    "₉" -> "9",
    "ₐ" -> "a",
    "ₑ" -> "e",
    "ₕ" -> "h",
    "ᵢ" -> "i",
    "ⱼ" -> "j",
    "ₖ" -> "k",
    "ₗ" -> "l",
    "ₘ" -> "m",
    "ₙ" -> "n",
    "ₒ" -> "o",
    "ₚ" -> "p",
    "ᵣ" -> "r",
    "ₛ" -> "s",
    "ₜ" -> "t",
    "ᵤ" -> "u",
    "ᵥ" -> "v",
    "ₓ" -> "x",
    "ᵦ" -> "β",
    "ᵧ" -> "γ",
    "ᵨ" -> "ρ",
    "ᵩ" -> "ϕ",
    "ᵪ" -> "χ",
    "⁺" -> "+",
    "⁻" -> "-",
    "⁼" -> "=",
    "⁽" -> "(",
    "⁾" -> ")",
    "⁰" -> "0",
    "¹" -> "1",
    "²" -> "2",
    "³" -> "3",
    "⁴" -> "4",
    "⁵" -> "5",
    "⁶" -> "6",
    "⁷" -> "7",
    "⁸" -> "8",
    "⁹" -> "9",
    "ᴬ" -> "A",
    "ᴮ" -> "B",
    "ᴰ" -> "D",
    "ᴱ" -> "E",
    "ᴳ" -> "G",
    "ᴴ" -> "H",
    "ᴵ" -> "I",
    "ᴶ" -> "J",
    "ᴷ" -> "K",
    "ᴸ" -> "L",
    "ᴹ" -> "M",
    "ᴺ" -> "N",
    "ᴼ" -> "O",
    "ᴾ" -> "P",
    "ᴿ" -> "R",
    "ᵀ" -> "T",
    "ᵁ" -> "U",
    "ⱽ" -> "V",
    "ᵂ" -> "W",
    "ᵃ" -> "a",
    "ᵇ" -> "b",
    "ᶜ" -> "c",
    "ᵈ" -> "d",
    "ᵉ" -> "e",
    "ᶠ" -> "f",
    "ᵍ" -> "g",
    "ʰ" -> "h",
    "ⁱ" -> "i",
    "ʲ" -> "j",
    "ᵏ" -> "k",
    "ˡ" -> "l",
    "ᵐ" -> "m",
    "ⁿ" -> "n",
    "ᵒ" -> "o",
    "ᵖ" -> "p",
    "ʳ" -> "r",
    "ˢ" -> "s",
    "ᵗ" -> "t",
    "ᵘ" -> "u",
    "ᵛ" -> "v",
    "ʷ" -> "w",
    "ˣ" -> "x",
    "ʸ" -> "y",
    "ᶻ" -> "z",
    "ᵝ" -> "β",
    "ᵞ" -> "γ",
    "ᵟ" -> "δ",
    "ᵠ" -> "ϕ",
    "ᵡ" -> "χ",
    "ᶿ" -> "θ"
  )
}
