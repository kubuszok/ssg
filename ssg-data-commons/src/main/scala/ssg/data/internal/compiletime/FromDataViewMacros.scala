/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data
package internal.compiletime

import hearth.MacroCommonsScala3

import scala.quoted.*

final private[data] class FromDataViewMacros(q: Quotes) extends MacroCommonsScala3(using q), FromDataViewMacrosImpl

private[data] object FromDataViewMacros {

  def deriveImpl[A: Type](using q: Quotes): Expr[FromDataView[A]] =
    new FromDataViewMacros(q).deriveFromDataView[A]
}
