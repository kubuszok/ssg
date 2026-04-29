/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/SpecExample.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package spec

import ssg.md.Nullable
import ssg.md.util.misc.Utils

import java.util.concurrent.{ ConcurrentHashMap, ConcurrentMap }
import scala.language.implicitConversions

final class SpecExample private (
  val resourceLocation: ResourceLocation,
  val lineNumber:       Int,
  val optionsSet:       Nullable[String],
  val section:          Nullable[String],
  val exampleNumber:    Int,
  val source:           String,
  val html:             String,
  val ast:              Nullable[String],
  val comment:          Nullable[String],
  val isNull:           Boolean
) {

  def this(
    resourceLocation: ResourceLocation,
    lineNumber:       Int,
    optionsSet:       Nullable[String],
    section:          Nullable[String],
    exampleNumber:    Int,
    source:           String,
    html:             String,
    ast:              Nullable[String],
    comment:          Nullable[String]
  ) =
    this(resourceLocation, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, false)

  def isFullSpecExample: Boolean =
    (this ne SpecExample.NULL) && isNull &&
      !java.util.Objects.equals(this.resourceLocation, SpecExample.NULL.resourceLocation) &&
      java.util.Objects.equals(this.optionsSet, SpecExample.NULL.optionsSet) &&
      java.util.Objects.equals(this.section, SpecExample.NULL.section) &&
      this.exampleNumber == SpecExample.NULL.exampleNumber &&
      java.util.Objects.equals(this.source, SpecExample.NULL.source) &&
      java.util.Objects.equals(this.html, SpecExample.NULL.html) &&
      java.util.Objects.equals(this.ast, SpecExample.NULL.ast) &&
      java.util.Objects.equals(this.comment, SpecExample.NULL.comment)

  def isSpecExample: Boolean = isNotNull && !isFullSpecExample

  def isNotNull: Boolean = !isNull

  def fileUrlWithLineNumber: String = getFileUrlWithLineNumber(0)

  def getFileUrlWithLineNumber(lineOffset: Int): String =
    resourceLocation.getFileUrl(Utils.minLimit(lineNumber + lineOffset, 0))

  def fileUrl: String = resourceLocation.fileUrl

  def hasComment: Boolean = comment.exists(c => c.nonEmpty)

  // @formatter:off
  def withResourceLocation(location: ResourceLocation): SpecExample = new SpecExample(location, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, isNull)
  def withOptionsSet(optionsSet: Nullable[String]): SpecExample = new SpecExample(resourceLocation, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, isNull)
  def withSection(section: Nullable[String]): SpecExample = new SpecExample(resourceLocation, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, isNull)
  def withExampleNumber(exampleNumber: Int): SpecExample = new SpecExample(resourceLocation, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, isNull)
  def withSource(source: String): SpecExample = new SpecExample(resourceLocation, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, isNull)
  def withHtml(html: String): SpecExample = new SpecExample(resourceLocation, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, isNull)
  def withAst(ast: Nullable[String]): SpecExample = new SpecExample(resourceLocation, lineNumber, optionsSet, section, exampleNumber, source, html, ast, comment, isNull)
  // @formatter:on

  override def toString: String =
    if (this.isFullSpecExample) {
      "Full Spec"
    } else if (this eq SpecExample.NULL) {
      "NULL"
    } else {
      "" + section.getOrElse("") + ": " + exampleNumber
    }
}

object SpecExample {

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  val NULL: SpecExample = new SpecExample(
    ResourceLocation.NULL,
    0,
    Nullable.empty,
    "",
    0,
    "",
    "",
    Nullable.empty,
    Nullable.empty,
    true
  )

  private val classMap: ConcurrentMap[String, String] = new ConcurrentHashMap[String, String]()

  /** Returns parent directory of a path string, or empty string if no parent */
  private def parentPath(path: String): String = {
    val pos = path.lastIndexOf('/')
    if (pos > 0) path.substring(0, pos) else ""
  }

  def ofCaller(callNesting: Int, resourceClass: Class[?], source: String, html: String, ast: Nullable[String]): SpecExample = {
    val trace         = Thread.currentThread().getStackTrace
    val traceElement  = trace(callNesting + 2)
    var javaClassFile = classMap.get(resourceClass.getName)
    if (javaClassFile == null) { // Java interop: ConcurrentHashMap.get returns null when key absent
      val fileName = traceElement.getFileName
      // need path to class, so fake it with class resource file
      val javaFilePath   = resourceClass.getName.replace('.', '/')
      var javaParentPath = parentPath("/" + javaFilePath)
      val javaPath       = javaParentPath + "/" + fileName
      var prefix:       String = null // Java interop: set if resource found
      var resourcePath: String = null // Java interop: set if resource found
      var found = false
      while (!found && javaParentPath.nonEmpty) {
        val absolutePath = Utils.removeSuffix(javaParentPath, "/")
        resourcePath = Utils.removePrefix(absolutePath, '/').replace('/', '.') + ".txt"
        val stream = resourceClass.getResourceAsStream("/" + resourcePath)
        if (stream != null) { // Java interop: getResourceAsStream returns null when not found
          prefix = Utils.getResourceAsString(resourceClass, "/" + resourcePath).trim()
          stream.close()
          found = true
        } else {
          javaParentPath = parentPath(javaParentPath)
        }
      }
      if (!found) {
        throw new IllegalStateException(
          "Class mapping file not found for Class " + resourceClass +
            " add file under test resources with package for name and .txt extension"
        )
      }
      val fileUrl = "file:/" + resourcePath
      javaClassFile = fileUrl.replaceFirst(
        "/resources((?:/[^/]*?)*)/" + resourcePath,
        Utils.prefixWith(Utils.removeSuffix(prefix, '/'), '/') + "$1" + javaPath
      )
      classMap.put(resourceClass.getName, javaClassFile)
    }

    val location = new ResourceLocation(resourceClass, "", javaClassFile)
    new SpecExample(
      location,
      traceElement.getLineNumber - 1,
      Nullable.empty,
      Nullable(traceElement.getMethodName),
      0,
      source,
      html,
      ast,
      Nullable("")
    )
  }
}
