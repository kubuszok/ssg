/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

import ssg.data.DataView

import java.util.{ ArrayList, LinkedHashMap, List => JList, Map => JMap }

import scala.collection.immutable.VectorMap

object DataViewBridge {

  def unwrap(dv: DataView): Any =
    if (dv.isNull) null
    else
      dv.view match {
        case m: VectorMap[?, ?] => unwrapMap(m.asInstanceOf[VectorMap[String, DataView]])
        case v: Vector[?]      => unwrapVector(v.asInstanceOf[Vector[DataView]])
        case other              => other
      }

  def unwrapMap(m: VectorMap[String, DataView]): JMap[String, Any] = {
    val result = new LinkedHashMap[String, Any](m.size)
    m.foreach { case (k, v) => result.put(k, unwrap(v)) }
    result
  }

  private def unwrapVector(v: Vector[DataView]): JList[Any] = {
    val result = new ArrayList[Any](v.size)
    v.foreach(dv => result.add(unwrap(dv)))
    result
  }
}
