/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs core types (Options/ResolvedOptions/Op/OpSet/Drawable/PathInfo/Config + the
 * OpType/OpSetType discriminants + SVGNS) — Scala 3 port
 *
 * Original source: roughjs (src/core.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: the `Point` referenced by `OpSet.size` is the roughjs `Point` defined in
 *     this package (`Geometry.scala`); the `Random` referenced by `ResolvedOptions`
 *     is the roughjs `Random` defined in this package (`RoughMath.scala`).
 *   Convention (optional fields): TS optional members (`field?: T`) -> `Option[T]` with
 *     a `None` default, matching the optionality exactly. `number[]` -> `Vector[Double]`,
 *     `number` -> `Double` (except `seed`, see below), `boolean` -> `Boolean`,
 *     `string` -> `String`.
 *   Convention (seed type): `seed?: number` / required `seed: number` -> `Int` — roughjs
 *     seeds are always integral and feed the Int-based `Random` PRNG (see RoughMath).
 *   Convention (interface inheritance): TS `ResolvedOptions extends Options` (which
 *     re-declares ~20 of `Options`' members as REQUIRED, leaves 7 optional, and adds an
 *     optional `randomizer`). Scala `case class`es cannot extend `case class`es, so
 *     `ResolvedOptions` is a STANDALONE `final case class` carrying the flattened field
 *     set: the promoted-to-required members as non-`Option` types, the 7 members that
 *     stay optional (`fill`, `simplification`, `strokeLineDash`, `strokeLineDashOffset`,
 *     `fillLineDash`, `fillLineDashOffset`, `fixedDecimalPlaceDigits`) as `Option[T]`,
 *     and the added `randomizer`.
 *   Idiom (Nullable): the inherited-but-still-optional `randomizer?: Random` ->
 *     `Nullable[Random]` (per the project's no-`null` rule), defaulting to
 *     `Nullable.empty`. The other optional members use `Option[T]` to match upstream's
 *     plain optional-interface-member shape.
 *   Renames (`OpType`/`OpSetType`): TS string-literal union types
 *     `'move' | 'bcurveTo' | 'lineTo'` and `'path' | 'fillPath' | 'fillSketch'` ->
 *     Scala 3 `enum`s carrying an explicit `value: String` that serializes faithfully to
 *     those exact literals (later chips emit these strings). The string is decoupled
 *     from the case name so the emitted literal is asserted independently.
 *   Renames (`type` field): TS `OpSet.type` (a reserved word in Scala) -> the
 *     backticked identifier `` `type` `` to keep the field name verbatim.
 *   Convention (DrawingSurface): TS `DrawingSurface.{width,height}: number |
 *     SVGAnimatedLength`. `SVGAnimatedLength` is a browser-DOM type with no SSG analog
 *     and is only consumed by the DOM/canvas rendering surface (the platform-
 *     inapplicable `canvas.ts`); the union is collapsed to plain `Double` here. The
 *     interface itself is preserved for structural completeness.
 *   Idiom: `interface` -> `final case class`; `const SVGNS` -> a `final val`.
 */
package ssg
package graphs
package commons
package rough

import lowlevel.Nullable

/** The SVG XML namespace. Port of `const SVGNS`. */
final val SVGNS: String = "http://www.w3.org/2000/svg"

/** The op-kind discriminant of an `Op`. Port of `type OpType = 'move' | 'bcurveTo' |
  * 'lineTo'`. `value` is the exact string literal upstream emits.
  */
enum OpType(val value: String) {
  case move     extends OpType("move")
  case bcurveTo extends OpType("bcurveTo")
  case lineTo   extends OpType("lineTo")
}

/** The kind discriminant of an `OpSet`. Port of `type OpSetType = 'path' | 'fillPath' |
  * 'fillSketch'`. `value` is the exact string literal upstream emits.
  */
enum OpSetType(val value: String) {
  case path       extends OpSetType("path")
  case fillPath   extends OpSetType("fillPath")
  case fillSketch extends OpSetType("fillSketch")
}

/** Top-level configuration. Port of `interface Config`. */
final case class Config(options: Option[Options] = None)

/** A drawing surface's dimensions. Port of `interface DrawingSurface` (the
  * `number | SVGAnimatedLength` union collapsed to `Double`; see the migration notes).
  */
final case class DrawingSurface(width: Double, height: Double)

/** The full set of (all-optional) rough drawing options. Port of `interface Options`. */
final case class Options(
  maxRandomnessOffset:     Option[Double] = None,
  roughness:               Option[Double] = None,
  bowing:                  Option[Double] = None,
  stroke:                  Option[String] = None,
  strokeWidth:             Option[Double] = None,
  curveFitting:            Option[Double] = None,
  curveTightness:          Option[Double] = None,
  curveStepCount:          Option[Double] = None,
  fill:                    Option[String] = None,
  fillStyle:               Option[String] = None,
  fillWeight:              Option[Double] = None,
  hachureAngle:            Option[Double] = None,
  hachureGap:              Option[Double] = None,
  simplification:          Option[Double] = None,
  dashOffset:              Option[Double] = None,
  dashGap:                 Option[Double] = None,
  zigzagOffset:            Option[Double] = None,
  seed:                    Option[Int] = None,
  strokeLineDash:          Option[Vector[Double]] = None,
  strokeLineDashOffset:    Option[Double] = None,
  fillLineDash:            Option[Vector[Double]] = None,
  fillLineDashOffset:      Option[Double] = None,
  disableMultiStroke:      Option[Boolean] = None,
  disableMultiStrokeFill:  Option[Boolean] = None,
  preserveVertices:        Option[Boolean] = None,
  fixedDecimalPlaceDigits: Option[Double] = None,
  fillShapeRoughnessGain:  Option[Double] = None
)

/** The fully-resolved drawing options. Port of `interface ResolvedOptions extends
  * Options` — the members upstream promotes to required are non-`Option`; the seven that
  * stay optional remain `Option[T]`; `randomizer` is added as `Nullable[Random]` (see
  * the migration notes on the flattened interface inheritance).
  */
final case class ResolvedOptions(
  maxRandomnessOffset:     Double,
  roughness:               Double,
  bowing:                  Double,
  stroke:                  String,
  strokeWidth:             Double,
  curveFitting:            Double,
  curveTightness:          Double,
  curveStepCount:          Double,
  fillStyle:               String,
  fillWeight:              Double,
  hachureAngle:            Double,
  hachureGap:              Double,
  dashOffset:              Double,
  dashGap:                 Double,
  zigzagOffset:            Double,
  seed:                    Int,
  disableMultiStroke:      Boolean,
  disableMultiStrokeFill:  Boolean,
  preserveVertices:        Boolean,
  fillShapeRoughnessGain:  Double,
  randomizer:              Nullable[Random] = Nullable.empty,
  fill:                    Option[String] = None,
  simplification:          Option[Double] = None,
  strokeLineDash:          Option[Vector[Double]] = None,
  strokeLineDashOffset:    Option[Double] = None,
  fillLineDash:            Option[Vector[Double]] = None,
  fillLineDashOffset:      Option[Double] = None,
  fixedDecimalPlaceDigits: Option[Double] = None
)

/** A single drawing op. Port of `interface Op`. */
final case class Op(op: OpType, data: Vector[Double])

/** A set of ops describing one path/fill. Port of `interface OpSet`. */
final case class OpSet(
  `type`: OpSetType,
  ops:    Vector[Op],
  size:   Option[Point] = None,
  path:   Option[String] = None
)

/** A drawable shape (its op-sets + the options used). Port of `interface Drawable`. */
final case class Drawable(shape: String, options: ResolvedOptions, sets: Vector[OpSet])

/** A renderable path (`d` + paint attributes). Port of `interface PathInfo`. */
final case class PathInfo(
  d:           String,
  stroke:      String,
  strokeWidth: Double,
  fill:        Option[String] = None
)
