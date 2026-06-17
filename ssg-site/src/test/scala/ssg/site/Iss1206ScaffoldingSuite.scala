/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scaffolding smoke test for the ssg-site module (ISS-1206).
 * Proves the module builds and tests on all 3 platforms (JVM/JS/Native),
 * and that the kindlings-yaml dependency is on the classpath.
 */
package ssg
package site

import hearth.kindlings.yamlderivation.KindlingsYamlDecoder
import org.virtuslab.yaml.Node

class Iss1206ScaffoldingSuite extends munit.FunSuite {

  test("ssg-site module compiles and kindlings-yaml is on the classpath") {
    // Prove that the kindlings-yaml-derivation dependency resolves by referencing
    // a concrete type from the library. KindlingsYamlDecoder is the primary
    // decoder trait; Node is the YAML AST type from the underlying virtuslab-yaml
    // engine that kindlings-yaml wraps.
    val decoderCompanion: KindlingsYamlDecoder.type = KindlingsYamlDecoder
    assert(decoderCompanion != null, "KindlingsYamlDecoder companion must be reachable")

    // Verify the Node ADT is loadable (proves the transitive yaml dependency too).
    val nodeClass: Class[?] = classOf[Node]
    assert(nodeClass.getName.contains("Node"), "org.virtuslab.yaml.Node must be loadable")
  }

  test("ssg-site depends on ssg-commons (FileOps reachable)") {
    // Prove the ssg-commons dependency is wired by referencing its FileOps object.
    val fileOps: ssg.commons.io.FileOps.type = ssg.commons.io.FileOps
    assert(fileOps != null, "ssg.commons.io.FileOps must be reachable")
  }

  test("ssg-site depends on ssg-md (Parser reachable)") {
    // Prove the ssg-md dependency is wired.
    val parserCompanion: ssg.md.parser.Parser.type = ssg.md.parser.Parser
    assert(parserCompanion != null, "ssg.md.parser.Parser must be reachable")
  }

  test("ssg-site depends on ssg-liquid (TemplateParser reachable)") {
    // Prove the ssg-liquid dependency is wired.
    val tpClass: Class[?] = classOf[ssg.liquid.TemplateParser]
    assert(tpClass.getName.contains("TemplateParser"), "ssg.liquid.TemplateParser must be reachable")
  }

  test("ssg-site depends on ssg-data-commons (DataView reachable)") {
    // Prove the ssg-data-commons dependency is wired.
    val dvClass: Class[?] = classOf[ssg.data.DataView]
    assert(dvClass.getName.contains("DataView"), "ssg.data.DataView must be reachable")
  }

  test("ssg-site module identity") {
    // Prove the ssg-site main source is compiled and accessible.
    assertEquals(SitePipeline.moduleId, "ssg-site")
  }
}
