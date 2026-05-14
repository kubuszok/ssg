/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/PropertyResolverAdapter.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.where → ssg.liquid.filters.where
 *   Convention: Java interface → Scala trait
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/where/PropertyResolverAdapter.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters
package where

import ssg.data.DataView

trait PropertyResolverAdapter {
  def getItemProperty(context: TemplateContext, input: DataView, property: DataView): DataView
  def support(target:          DataView):                                             Boolean
}
