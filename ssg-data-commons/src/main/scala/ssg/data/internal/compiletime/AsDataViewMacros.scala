/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data
package internal.compiletime

import hearth.MacroCommonsScala3

import scala.quoted.*

final private[data] class AsDataViewMacros(q: Quotes)
    extends MacroCommonsScala3(using q),
      AsDataViewMacrosImpl

private[data] object AsDataViewMacros {

  def deriveImpl[A: Type](using q: Quotes): Expr[AsDataView[A]] =
    new AsDataViewMacros(q).deriveAsDataView[A]
}
