/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

import ssg.commons.Nullable

import scala.collection.immutable.VectorMap

final class DataView(thunk: => Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | Vector[DataView] | VectorMap[String, DataView]) {

  lazy val view: Null | Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | Vector[DataView] | VectorMap[String, DataView] = thunk

  final def isNull: Boolean = view == null

  final def asBoolean: Nullable[Boolean] = view match {
    case b: Boolean => Nullable(b)
    case _          => Nullable.empty
  }

  final def asShort: Nullable[Short] = view match {
    case s: Short => Nullable(s)
    case _        => Nullable.empty
  }

  final def asInt: Nullable[Int] = view match {
    case i: Int => Nullable(i)
    case _      => Nullable.empty
  }

  final def asLong: Nullable[Long] = view match {
    case l: Long => Nullable(l)
    case _       => Nullable.empty
  }

  final def asFloat: Nullable[Float] = view match {
    case f: Float => Nullable(f)
    case _        => Nullable.empty
  }

  final def asDouble: Nullable[Double] = view match {
    case d: Double => Nullable(d)
    case _         => Nullable.empty
  }

  final def asString: Nullable[String] = view match {
    case s: String => Nullable(s)
    case _         => Nullable.empty
  }

  final def asBigDecimal: Nullable[java.math.BigDecimal] = view match {
    case bd: java.math.BigDecimal => Nullable(bd)
    case _                        => Nullable.empty
  }

  final def asVector: Nullable[Vector[DataView]] = view match {
    case v: Vector[?] => Nullable(v.asInstanceOf[Vector[DataView]])
    case _            => Nullable.empty
  }

  final def asMap: Nullable[VectorMap[String, DataView]] = view match {
    case m: VectorMap[?, ?] => Nullable(m.asInstanceOf[VectorMap[String, DataView]])
    case _                  => Nullable.empty
  }
}

object DataView {

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  val nil: DataView = {
    val n: Null | Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | Vector[DataView] | VectorMap[String, DataView] = null
    new DataView(n.asInstanceOf[Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | Vector[DataView] | VectorMap[String, DataView]])
  }

  def apply(value: => Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | Vector[DataView] | VectorMap[String, DataView]): DataView =
    new DataView(value)
}
