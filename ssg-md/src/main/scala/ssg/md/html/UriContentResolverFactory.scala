/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/UriContentResolverFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

import ssg.md.html.renderer.LinkResolverBasicContext
import ssg.md.util.dependency.Dependent

trait UriContentResolverFactory extends (LinkResolverBasicContext => UriContentResolver), Dependent {
  override def afterDependents:                          Nullable[Set[Class[?]]]
  override def beforeDependents:                         Nullable[Set[Class[?]]]
  override def affectsGlobalScope:                       Boolean
  override def apply(context: LinkResolverBasicContext): UriContentResolver
}
