/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: what the youtube extension asks of `java.net.URL` — the host and the file part of a link —
 * answered by java's own `URL`, refusals included. The generated code is re-pointed here
 * (ssg-md/port/ext.conf) because Scala.js and Scala Native have no `URL`; they answer over `URI`. */
package ssg
package md
package util
package misc

/** @throws java.net.MalformedURLException as `new URL(spec)` does */
final class Url(spec: String) {

  // JDK 20 deprecates the constructor in favour of `URI.toURL`, which parses differently; flexmark's call is this one
  @scala.annotation.nowarn("cat=deprecation")
  private val url: java.net.URL = new java.net.URL(spec)

  def getHost(): String = url.getHost()

  def getFile(): String = url.getFile()
}
