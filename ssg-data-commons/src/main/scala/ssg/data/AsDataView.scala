/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

trait AsDataView[A] {

  extension (a: A) def asDataView: DataView
}

object AsDataView {

  inline given derived[A]: AsDataView[A] = ${
    internal.compiletime.AsDataViewMacros.deriveImpl[A]
  }
}
