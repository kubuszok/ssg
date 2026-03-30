/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/ResourceResolverManager.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util
package spec

import ssg.md.Nullable

import java.io.File
import java.net.URL
import java.{util => ju}
import scala.util.boundary
import scala.util.boundary.break

object ResourceResolverManager {

  /**
   * urlResolvers map test resource location url to source resource url to allow tests to
   * output file URLs which refer to source location, not copies in test location
   */
  private val urlResolvers: ju.ArrayList[String => String] = new ju.ArrayList[String => String]()

  def registerUrlResolver(resolver: String => String): Unit = {
    urlResolvers.add(resolver)
  }

  def adjustedFileUrl(url: URL): String = {
    val externalForm = url.toExternalForm
    var bestProtocolMatch: Nullable[String] = Nullable.empty

    val iter = urlResolvers.iterator()
    boundary {
      while (iter.hasNext) {
        val resolver = iter.next()
        val filePath = resolver.apply(externalForm)
        if (filePath != null) { // resolver returns null to indicate no match
          if (ResourceUrlResolver.hasProtocol(filePath) && bestProtocolMatch.isEmpty) {
            bestProtocolMatch = Nullable(filePath)
          } else {
            val file = new File(filePath)
            if (file.exists()) {
              break(TestUtils.FILE_PROTOCOL + filePath)
            }
          }
        }
      }

      bestProtocolMatch.getOrElse(externalForm)
    }
  }
}
