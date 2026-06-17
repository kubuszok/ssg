/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

import lowlevel.Nullable

import java.time.temporal.TemporalAccessor

import scala.collection.immutable.VectorMap

final class DataView(
  thunk: => Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | TemporalAccessor | Vector[DataView] | VectorMap[String, DataView]
) {

  lazy val view: Null | Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | TemporalAccessor | Vector[DataView] | VectorMap[
    String,
    DataView
  ] = thunk

  final def isNull: Boolean = view == null

  final def asBoolean: Nullable[Boolean] = view match {
    case b: Boolean => Nullable(b)
    case _ => Nullable.empty
  }

  final def asShort: Nullable[Short] = view match {
    case s: Short => Nullable(s)
    case _ => Nullable.empty
  }

  final def asInt: Nullable[Int] = view match {
    case i: Int => Nullable(i)
    case _ => Nullable.empty
  }

  final def asLong: Nullable[Long] = view match {
    case l: Long => Nullable(l)
    case _ => Nullable.empty
  }

  final def asFloat: Nullable[Float] = view match {
    case f: Float => Nullable(f)
    case _ => Nullable.empty
  }

  final def asDouble: Nullable[Double] = view match {
    case d: Double => Nullable(d)
    case _ => Nullable.empty
  }

  final def asString: Nullable[String] = view match {
    case s: String => Nullable(s)
    case _ => Nullable.empty
  }

  final def asBigDecimal: Nullable[java.math.BigDecimal] = view match {
    case bd: java.math.BigDecimal => Nullable(bd)
    case _ => Nullable.empty
  }

  final def asTemporal: Nullable[TemporalAccessor] = view match {
    case ta: TemporalAccessor => Nullable(ta)
    case _ => Nullable.empty
  }

  final def asVector: Nullable[Vector[DataView]] = view match {
    case v: Vector[?] => Nullable(v.asInstanceOf[Vector[DataView]])
    case _ => Nullable.empty
  }

  final def asMap: Nullable[VectorMap[String, DataView]] = view match {
    case m: VectorMap[?, ?] => Nullable(m.asInstanceOf[VectorMap[String, DataView]])
    case _ => Nullable.empty
  }

  override def toString: String =
    if (isNull) ""
    else String.valueOf(view)
}

object DataView {

  // --- Value union type alias ---

  type Value = Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | TemporalAccessor | Vector[DataView] | VectorMap[String, DataView]

  // --- Nil (absent/missing) ---

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  val nil: DataView = {
    val n: Null | Value = null
    new DataView(n.asInstanceOf[Value])
  }

  // --- Sentinels (identity-checked with eq, render as empty) ---

  val BREAK:    DataView = new DataView(new String("").asInstanceOf[Value])
  val CONTINUE: DataView = new DataView(new String("").asInstanceOf[Value])
  val EMPTY:    DataView = new DataView(new String("").asInstanceOf[Value])
  val BLANK:    DataView = new DataView(new String("").asInstanceOf[Value])

  // --- Primary constructor ---

  def apply(value: => Value): DataView =
    new DataView(value)

  // --- Typed factory methods (avoid union type ascription at call sites) ---

  def from(value: Boolean):                     DataView = new DataView(value)
  def from(value: Short):                       DataView = new DataView(value)
  def from(value: Int):                         DataView = new DataView(value)
  def from(value: Long):                        DataView = new DataView(value)
  def from(value: Float):                       DataView = new DataView(value)
  def from(value: Double):                      DataView = new DataView(value)
  def from(value: String):                      DataView = if (value == null) nil else new DataView(value)
  def from(value: java.math.BigDecimal):        DataView = if (value == null) nil else new DataView(value)
  def from(value: TemporalAccessor):            DataView = if (value == null) nil else new DataView(value)
  def from(value: Vector[DataView]):            DataView = new DataView(value)
  def from(value: VectorMap[String, DataView]): DataView = new DataView(value)

  // --- Deep merge ---

  /** Recursively merges an `overlay` DataView onto a `base` DataView, with the overlay's present keys winning.
    *
    * This is a faithful port of the "present-keys-win, recurse-when-both-maps" semantics of lodash `merge` (used by mermaid's `cleanAndMerge`, `utils.ts:858-860` — `merge({}, defaultData, data)`) and
    * mermaid's `assignWithDepth` (`assignWithDepth.ts`). The rules are:
    *
    *   - When BOTH sides are maps, merge key-by-key: every key present in `overlay` is merged onto the corresponding `base` key (recursing when both values are maps); a key present only in `base` is
    *     preserved unchanged (present-keys-win — an absent overlay key keeps base's value).
    *   - When the sides are not both maps (scalar-over-map, map-over-scalar, scalar-over-scalar), the overlay value replaces the base value.
    *
    * Unlike lodash `merge` this does not special-case arrays element-wise; an overlay vector replaces the base vector wholesale (matching the non-map "overlay replaces" branch), which is sufficient
    * for the config-overlay use site where arrays are leaf values.
    *
    * @param base
    *   the lower-precedence DataView (e.g. the existing config)
    * @param overlay
    *   the higher-precedence DataView (e.g. author-supplied config); its present keys win
    * @return
    *   the merged DataView
    */
  def deepMerge(base: DataView, overlay: DataView): DataView =
    (base.asMap.toOption, overlay.asMap.toOption) match {
      case (Some(baseMap), Some(overlayMap)) =>
        // Both maps: start from base, then fold every overlay key on top.
        val merged = overlayMap.foldLeft(baseMap) { case (acc, (key, overlayValue)) =>
          acc.get(key) match {
            case Some(baseValue) => acc.updated(key, deepMerge(baseValue, overlayValue))
            case scala.None      => acc.updated(key, overlayValue)
          }
        }
        DataView.from(merged)
      case _ =>
        // Not both maps: overlay replaces base.
        overlay
    }
}
