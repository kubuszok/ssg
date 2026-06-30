/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs filler factory (getFiller + the module-level filler cache) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/filler.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS module function `getFiller` + the module-level `const fillers` map ->
 *     `object Filler` with a `private val fillers: mutable.Map[String, PatternFiller]`.
 *   Idiom (`o.fillStyle || 'hachure'`): `o.fillStyle` is a required `String` in
 *     `ResolvedOptions`; an empty string is JS-falsy, so `|| 'hachure'` -> "hachure" when
 *     the style is empty. Reproduced via `if (o.fillStyle.nonEmpty) o.fillStyle else
 *     "hachure"`.
 *   Idiom (mutable cache quirk, critical concern #4, PORTED VERBATIM — NOT "fixed"):
 *     the cache is keyed by the fill-style NAME ONLY, so the FIRST `helper` passed for a
 *     given style is RETAINED for the lifetime of the process; later `getFiller` calls
 *     with a DIFFERENT `helper` but the SAME style return the originally-cached filler
 *     (bound to the first helper). This is a genuine roughjs statefulness quirk and is
 *     reproduced exactly. The redundant inner `if (!fillers[fillerName])` guards (already
 *     gated by the outer `if (!fillers[fillerName])`) are preserved verbatim. For an
 *     unknown style the `default` arm reassigns `fillerName = "hachure"` and caches/reads
 *     under "hachure" (the unknown name is never stored). Thread-safety is not a roughjs
 *     concern; the single-threaded semantics are matched.
 *   Idiom (`switch` with `case 'hachure': default:` fall-through): the two arms have
 *     identical bodies; ported as a `case "hachure"` plus a `case _` with the same body.
 *   Idiom (control flow): no `return`; the trailing `fillers(fillerName)` is the result.
 */
package ssg
package graphs
package commons
package rough
package fillers

import scala.collection.mutable

/** roughjs filler factory (port of `filler.ts`). */
object Filler {

  // The module-level filler cache, keyed by fill-style name only (see the migration
  // notes: the first helper for a given style is retained). Ported verbatim.
  private val fillers: mutable.Map[String, PatternFiller] = mutable.Map.empty

  /** Port of `getFiller(o, helper)`: return the (cached) `PatternFiller` for the requested fill style.
    */
  def getFiller(o: ResolvedOptions, helper: RenderHelper): PatternFiller = {
    var fillerName: String = if (o.fillStyle.nonEmpty) o.fillStyle else "hachure"
    if (!fillers.contains(fillerName)) {
      fillerName match {
        case "zigzag" =>
          if (!fillers.contains(fillerName)) {
            fillers(fillerName) = ZigZagFiller(helper)
          }
        case "cross-hatch" =>
          if (!fillers.contains(fillerName)) {
            fillers(fillerName) = HatchFiller(helper)
          }
        case "dots" =>
          if (!fillers.contains(fillerName)) {
            fillers(fillerName) = DotFiller(helper)
          }
        case "dashed" =>
          if (!fillers.contains(fillerName)) {
            fillers(fillerName) = DashedFiller(helper)
          }
        case "zigzag-line" =>
          if (!fillers.contains(fillerName)) {
            fillers(fillerName) = ZigZagLineFiller(helper)
          }
        case "hachure" =>
          fillerName = "hachure"
          if (!fillers.contains(fillerName)) {
            fillers(fillerName) = HachureFiller(helper)
          }
        case _ =>
          fillerName = "hachure"
          if (!fillers.contains(fillerName)) {
            fillers(fillerName) = HachureFiller(helper)
          }
      }
    }
    fillers(fillerName)
  }
}
