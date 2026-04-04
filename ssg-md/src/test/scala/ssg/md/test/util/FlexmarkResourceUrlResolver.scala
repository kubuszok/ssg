/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/FlexmarkResourceUrlResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.test.util.spec.{ ResourceResolverManager, ResourceUrlResolver }

import scala.language.implicitConversions

object FlexmarkResourceUrlResolver {

  def registerUrlResolvers(): Unit = {
    ResourceResolverManager.registerUrlResolver(new TargetTestResourceUrlResolver())
    ResourceResolverManager.registerUrlResolver(new BuildTestResourceUrlResolver())
    ResourceResolverManager.registerUrlResolver(new OutTestResourcesUrlResolver())
  }

  private class TargetTestResourceUrlResolver extends ResourceUrlResolver {

    override def apply(externalForm: String): String =
      if (ResourceUrlResolver.isFileProtocol(externalForm)) {
        val noFileProtocol = ResourceUrlResolver.removeProtocol(externalForm)

        if (noFileProtocol.contains(TargetTestResourceUrlResolver.TEST_RESOURCES)) {
          noFileProtocol.replaceFirst(
            TargetTestResourceUrlResolver.TEST_RESOURCES,
            TargetTestResourceUrlResolver.SRC_TEST_RESOURCES
          )
        } else {
          null // Java interop: resolver returns null to indicate no match
        }
      } else {
        null // Java interop: resolver returns null to indicate no match
      }
  }

  private object TargetTestResourceUrlResolver {
    val TEST_RESOURCES:     String = "/target/test-classes/"
    val SRC_TEST_RESOURCES: String = "/src/test/resources/"
  }

  private class BuildTestResourceUrlResolver extends ResourceUrlResolver {

    override def apply(externalForm: String): String =
      if (ResourceUrlResolver.isFileProtocol(externalForm)) {
        val noFileProtocol = ResourceUrlResolver.removeProtocol(externalForm)

        if (noFileProtocol.contains(BuildTestResourceUrlResolver.TEST_RESOURCES)) {
          noFileProtocol.replaceFirst(
            BuildTestResourceUrlResolver.TEST_RESOURCES,
            BuildTestResourceUrlResolver.SRC_TEST_RESOURCES
          )
        } else {
          null // Java interop: resolver returns null to indicate no match
        }
      } else {
        null // Java interop: resolver returns null to indicate no match
      }
  }

  private object BuildTestResourceUrlResolver {
    val TEST_RESOURCES:     String = "/build/resources/test/"
    val SRC_TEST_RESOURCES: String = "/src/test/resources/"
  }

  private class OutTestResourcesUrlResolver extends ResourceUrlResolver {

    override def apply(externalForm: String): String =
      if (ResourceUrlResolver.isFileProtocol(externalForm)) {
        val noFileProtocol = ResourceUrlResolver.removeProtocol(externalForm)

        val pos = noFileProtocol.indexOf(OutTestResourcesUrlResolver.OUT_TEST)
        if (pos > 0) {
          val pathPos = noFileProtocol.indexOf("/", pos + OutTestResourcesUrlResolver.OUT_TEST.length)
          if (pathPos > 0) {
            noFileProtocol.substring(0, pos) + "/" +
              noFileProtocol.substring(pos + OutTestResourcesUrlResolver.OUT_TEST.length, pathPos) +
              OutTestResourcesUrlResolver.SRC_TEST_RESOURCES +
              noFileProtocol.substring(pathPos + 1)
          } else {
            null // Java interop: resolver returns null to indicate no match
          }
        } else {
          null // Java interop: resolver returns null to indicate no match
        }
      } else {
        null // Java interop: resolver returns null to indicate no match
      }
  }

  private object OutTestResourcesUrlResolver {
    val OUT_TEST:           String = "/out/test/"
    val SRC_TEST_RESOURCES: String = "/src/test/resources/"
  }
}
