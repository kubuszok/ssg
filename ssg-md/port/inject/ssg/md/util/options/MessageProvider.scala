/*
 * Ported from flexmark-java - https://github.com/vsch/flexmark-java
 * Original source: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/MessageProvider.java
 * Original license: BSD-2-Clause, Copyright (c) 2015-2016 Atlassian Pty Ltd, 2016-2018 Vladimir Schneider
 *
 * Migration notes:
 *   Origin: `DEFAULT` formats through `java.text.MessageFormat`, which Scala.js and Scala Native do not
 *     have. The one call goes through `ssg.md.util.misc.Messages`, which is `MessageFormat` on the JVM
 *     and a `{n}` replacement elsewhere; the interface and `DEFAULT`'s guard are java's.
 */
package ssg
package md
package util
package options

trait MessageProvider {
  def message(key: String, defaultText: String, params: Array[Object]): String
}

object MessageProvider {

  val DEFAULT: MessageProvider = (key: String, defaultText: String, params: Array[Object]) =>
    if (params.length > 0 && defaultText.indexOf('{') >= 0) ssg.md.util.misc.Messages.format(defaultText, params) else defaultText
}
