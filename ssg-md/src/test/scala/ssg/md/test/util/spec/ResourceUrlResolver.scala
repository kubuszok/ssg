/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/ResourceUrlResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util
package spec

/** Used to resolve test resource URL from copy in test location to URL of source file
  */
trait ResourceUrlResolver extends (String => String) {

  override def apply(externalForm: String): String
}

object ResourceUrlResolver {

  val FILE_PROTOCOL: String = "file://"

  def isFileProtocol(externalForm: String): Boolean =
    externalForm.startsWith("file:/")

  def hasProtocol(externalForm: String): Boolean = {
    val pos = externalForm.indexOf(":")
    // allow windows drive letter to not be treated as protocol
    pos > 1
  }

  def removeProtocol(externalForm: String): String = {
    val pos = externalForm.indexOf(':')
    if (pos > 0) {
      externalForm.substring(pos + 1)
    } else {
      externalForm
    }
  }
}
