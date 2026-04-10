/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/ResourceLocation.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util
package spec

import ssg.md.test.util.ResourceCompat
import java.io.{ BufferedReader, IOException, InputStream, InputStreamReader }
import java.nio.charset.StandardCharsets

final class ResourceLocation private (
  val resourceClass:        Class[?],
  val resourcePath:         String,
  val fileUrl:              String,
  val resolvedResourcePath: String
) {

  def this(resourceClass: Class[?], resourcePath: String, fileUrl: String) = {
    this(
      resourceClass,
      resourcePath,
      fileUrl,
      TestUtils.getResolvedSpecResourcePath(resourceClass.getName, resourcePath)
    )
  }

  def fileDirectoryUrl: String = {
    // Resource paths always use '/' regardless of platform
    val pos = fileUrl.lastIndexOf('/')
    if (pos > 0) {
      fileUrl.substring(0, pos + 1)
    } else {
      fileUrl
    }
  }

  def getFileUrl(lineNumber: Int): String =
    TestUtils.getUrlWithLineNumber(fileUrl, lineNumber)

  def isNull: Boolean = this eq ResourceLocation.NULL

  def resourceInputStream: InputStream =
    ResourceLocation.getResourceInputStream(this)

  def resourceText: String =
    ResourceLocation.getResourceText(this)

  // @formatter:off
  def withResourceClass(resourceClass: Class[?]): ResourceLocation = new ResourceLocation(resourceClass, resourcePath, fileUrl, resolvedResourcePath)
  def withResourcePath(resourcePath: String): ResourceLocation = new ResourceLocation(resourceClass, resourcePath, fileUrl, resolvedResourcePath)
  def withFileUrl(fileUrl: String): ResourceLocation = new ResourceLocation(resourceClass, resourcePath, fileUrl, resolvedResourcePath)
  def withResolvedResourcePath(resolvedResourcePath: String): ResourceLocation = new ResourceLocation(resourceClass, resourcePath, fileUrl, resolvedResourcePath)
  // @formatter:on

  override def equals(o: Any): Boolean =
    if (this eq o.asInstanceOf[AnyRef]) {
      true
    } else {
      o match {
        case that: ResourceLocation =>
          resourceClass == that.resourceClass &&
          resourcePath == that.resourcePath &&
          fileUrl == that.fileUrl &&
          resolvedResourcePath == that.resolvedResourcePath
        case _ => false
      }
    }

  override def hashCode(): Int = {
    var result = resourceClass.hashCode()
    result = 31 * result + resourcePath.hashCode()
    result = 31 * result + fileUrl.hashCode()
    result = 31 * result + resolvedResourcePath.hashCode()
    result
  }

  override def toString: String =
    s"ResourceLocation { resourceClass=$resourceClass, resourcePath='$resourcePath' }"
}

object ResourceLocation {

  val NULL: ResourceLocation = of(classOf[Object], "", "")

  def of(resourcePath: String): ResourceLocation =
    new ResourceLocation(
      classOf[ComboSpecTestCase],
      resourcePath,
      TestUtils.getSpecResourceFileUrl(classOf[ComboSpecTestCase], resourcePath),
      TestUtils.getResolvedSpecResourcePath(classOf[ComboSpecTestCase].getName, resourcePath)
    )

  def of(resourceClass: Class[?], resourcePath: String): ResourceLocation =
    new ResourceLocation(
      resourceClass,
      resourcePath,
      TestUtils.getSpecResourceFileUrl(resourceClass, resourcePath),
      TestUtils.getResolvedSpecResourcePath(resourceClass.getName, resourcePath)
    )

  def of(resourceClass: Class[?], resourcePath: String, fileUrl: String): ResourceLocation =
    new ResourceLocation(resourceClass, resourcePath, fileUrl)

  def getResourceText(location: ResourceLocation): String = {
    val sb = new StringBuilder()
    try {
      val inputStream  = getResourceInputStream(location)
      val streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)
      val reader       = new BufferedReader(streamReader)
      var line         = reader.readLine()
      while (line != null) {
        sb.append(line)
        sb.append("\n")
        line = reader.readLine()
      }
      reader.close()
      streamReader.close()
      inputStream.close()
      sb.toString()
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
  }

  def getResourceInputStream(location: ResourceLocation): InputStream =
    // Cross-platform: use ResourceCompat instead of Class.getResourceAsStream
    // (getResourceAsStream is not available on Scala.js)
    ResourceCompat.getResourceAsStream(location.resourceClass, location.resolvedResourcePath)
}
