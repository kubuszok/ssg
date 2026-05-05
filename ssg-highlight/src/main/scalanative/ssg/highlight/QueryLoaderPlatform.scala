/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

object QueryLoaderPlatform {

  def loadHighlightQuery(queryDir: String): Option[String] = {
    val path   = s"queries/$queryDir/highlights.scm"
    val stream = classOf[SyntaxHighlighter].getResourceAsStream("/" + path)
    if (stream == null) None
    else
      try {
        val bytes = stream.readAllBytes()
        Some(new String(bytes, "UTF-8"))
      } finally
        stream.close()
  }
}
