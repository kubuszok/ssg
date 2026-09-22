/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: there is no `java.net.URL` here, so the host and file part of a link are read off
 * `java.net.URI`, which exists, with `URL`'s two refusals restated — an unparseable spec and a spec
 * with no scheme both raise `MalformedURLException`, as `new URL(spec)` does. The JVM row is java's
 * own `URL`. The hand-written port of the youtube extension read the same two parts off a `URI`. */
package ssg
package md
package util
package misc

/** @throws java.net.MalformedURLException where `new URL(spec)` would */
final class Url(spec: String) {

  private val uri: java.net.URI =
    try
      new java.net.URI(spec)
    catch {
      case e: java.net.URISyntaxException => throw new java.net.MalformedURLException(e.getMessage)
    }

  if (uri.getScheme == null) { // @nowarn — java interop: URI answers null for an absent component
    throw new java.net.MalformedURLException("no protocol: " + spec)
  }

  /** java's `URL.getHost`: the empty string when the link names no host */
  def getHost(): String = Option(uri.getHost).getOrElse("")

  /** java's `URL.getFile`: the raw path, then `?` and the raw query when there is one */
  def getFile(): String = Option(uri.getRawPath).getOrElse("") + Option(uri.getRawQuery).fold("")("?" + _)
}
