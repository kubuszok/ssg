/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Unshift.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Unshift.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import java.util.ArrayList
import scala.jdk.CollectionConverters._

/** Liquid "unshift" filter — returns a new array with the given item(s) added to the beginning.
  *
  * Jekyll-specific filter for array manipulation.
  */
class Unshift extends Filter {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView =
    if (!isArray(value)) {
      value
    } else if (params.length == 0) {
      value
    } else {
      val valueList = asArray(value, context)
      val paramList = params.toVector
      val combined  = new ArrayList[DataView]()
      paramList.foreach(combined.add)
      valueList.foreach(combined.add)
      DataView.from(combined.asScala.toVector)
    }
}
