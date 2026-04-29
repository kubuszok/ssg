/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/TwoWayHashMap.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/TwoWayHashMap.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection

import ssg.md.Nullable

class TwoWayHashMap[F, S] {
  private val fToSMap: java.util.HashMap[F, S] = new java.util.HashMap[F, S]()
  private val sToFMap: java.util.HashMap[S, F] = new java.util.HashMap[S, F]()

  def add(f: Nullable[F], s: Nullable[S]): Unit = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — keys/values may be null
    val fRaw = f.orNull
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — keys/values may be null
    val sRaw = s.orNull
    fToSMap.put(fRaw, sRaw)
    sToFMap.put(sRaw, fRaw)
  }

  def getSecond(f: Nullable[F]): Nullable[S] = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — key may be null
    val fRaw = f.orNull
    Nullable(fToSMap.get(fRaw))
  }

  def getFirst(s: Nullable[S]): Nullable[F] = {
    @annotation.nowarn("msg=deprecated") // Java HashMap interop — key may be null
    val sRaw = s.orNull
    Nullable(sToFMap.get(sRaw))
  }
}
