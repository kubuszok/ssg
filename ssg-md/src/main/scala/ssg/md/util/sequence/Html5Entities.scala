/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/Html5Entities.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import java.io.{ BufferedReader, InputStreamReader }
import java.nio.charset.StandardCharsets
import java.util as ju
import java.util.regex.Pattern

/** HTML5 entity parser. Loads from entities.properties resource. Static methods.
  */
object Html5Entities {

  private val ENTITY_PATH:                String                 = "/ssg/md/util/sequence/entities.properties"
  private val NAMED_CHARACTER_REFERENCES: ju.Map[String, String] = readEntities()
  private val NUMERIC_PATTERN:            Pattern                = Pattern.compile("^&#[Xx]?")

  def entityToString(input: String): String = {
    val matcher = NUMERIC_PATTERN.matcher(input)

    if (matcher.find()) {
      val base = if (matcher.end() == 2) 10 else 16
      try {
        val codePoint = Integer.parseInt(input.substring(matcher.end(), input.length - 1), base)
        if (codePoint == 0) {
          "\uFFFD"
        } else {
          new String(Character.toChars(codePoint))
        }
      } catch {
        case _: IllegalArgumentException => "\uFFFD"
      }
    } else {
      val name = input.substring(1, input.length - 1)
      val s    = NAMED_CHARACTER_REFERENCES.get(name)
      if (s != null) s else input // @nowarn — Map.get returns null at Java interop boundary
    }
  }

  def entityToSequence(input: BasedSequence): BasedSequence = {
    val matcher = NUMERIC_PATTERN.matcher(input)
    val baseSeq = input.subSequence(0, 0)

    if (matcher.find()) {
      val base = if (matcher.end() == 2) 10 else 16
      try {
        val codePoint = Integer.parseInt(input.subSequence(matcher.end(), input.length() - 1).toString, base)
        if (codePoint == 0) {
          PrefixedSubSequence.prefixOf("\uFFFD", baseSeq)
        } else {
          PrefixedSubSequence.prefixOf(ju.Arrays.toString(Character.toChars(codePoint)), baseSeq)
        }
      } catch {
        case _: IllegalArgumentException =>
          PrefixedSubSequence.prefixOf("\uFFFD", baseSeq)
      }
    } else {
      val name = input.subSequence(1, input.length() - 1).toString
      val s    = NAMED_CHARACTER_REFERENCES.get(name)
      if (s != null) { // @nowarn — Map.get returns null at Java interop boundary
        PrefixedSubSequence.prefixOf(s, baseSeq)
      } else {
        input
      }
    }
  }

  private def readEntities(): ju.Map[String, String] = {
    val entities = new ju.HashMap[String, String]()
    val stream   = ssg.md.util.misc.PlatformResources.getResourceAsStream(Html5Entities.getClass, ENTITY_PATH).getOrElse {
      throw new IllegalStateException("Could not load " + ENTITY_PATH)
    }
    val charset  = StandardCharsets.UTF_8
    try {
      val streamReader   = new InputStreamReader(stream, charset)
      val bufferedReader = new BufferedReader(streamReader)
      var line           = bufferedReader.readLine()
      while (line != null) { // @nowarn — null check at Java interop boundary (readLine returns null at EOF)
        if (line.length > 0) {
          val equal = line.indexOf("=")
          val key   = line.substring(0, equal)
          val value = line.substring(equal + 1)
          entities.put(key, value)
        }
        line = bufferedReader.readLine()
      }
    } catch {
      case e: java.io.IOException =>
        throw new IllegalStateException("Failed reading data for HTML named character references", e)
    }
    entities.put("NewLine", "\n")
    entities
  }

}
