/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: there is no org.nibor.autolink here, so links are found by the regular expressions the
 * hand-written port of this extension used. The JVM row wraps nibor's own extractor. */
package ssg
package md
package ext
package autolink
package internal

object PlatformLinkExtractor {

  def create(types: java.util.Set[LinkType]): LinkExtractor = new RegexLinkExtractor(types)
}
