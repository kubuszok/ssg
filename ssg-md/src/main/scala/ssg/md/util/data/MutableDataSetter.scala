/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableDataSetter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/MutableDataSetter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package data

trait MutableDataSetter {
  def setIn(dataHolder: MutableDataHolder): MutableDataHolder
}
