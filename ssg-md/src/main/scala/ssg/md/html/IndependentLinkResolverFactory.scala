/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/IndependentLinkResolverFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

abstract class IndependentLinkResolverFactory extends LinkResolverFactory {
  override def afterDependents:    Nullable[Set[Class[?]]] = Nullable.empty
  override def beforeDependents:   Nullable[Set[Class[?]]] = Nullable.empty
  override def affectsGlobalScope: Boolean                 = false
}
