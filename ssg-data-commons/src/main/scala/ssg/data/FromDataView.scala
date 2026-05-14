/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

import ssg.commons.Nullable

trait FromDataView[A] {

  extension (dv: DataView) def fromDataView: Nullable[A]
}

object FromDataView {

  inline given derived[A]: FromDataView[A] = ${
    internal.compiletime.FromDataViewMacros.deriveImpl[A]
  }
}
