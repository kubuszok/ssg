/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

final class NestWithinSuite extends munit.FunSuite {

  test("nestWithin with implicitParent=false and no & returns child as-is") {
    val child  = ssg.sass.parse.SelectorParser.tryParse(".child")
    val parent = ssg.sass.parse.SelectorParser.tryParse(".parent")
    assert(child.isDefined, "Failed to parse .child")
    assert(parent.isDefined, "Failed to parse .parent")
    val result = child.get.nestWithin(parent, implicitParent = false)
    assertEquals(result.toString, ".child")
  }

  test("nestWithin with implicitParent=false and & resolves parent") {
    val child  = ssg.sass.parse.SelectorParser.tryParse("&")
    val parent = ssg.sass.parse.SelectorParser.tryParse("foo")
    assert(child.isDefined, "Failed to parse &")
    assert(parent.isDefined, "Failed to parse foo")
    val result = child.get.nestWithin(parent, implicitParent = false)
    assertEquals(result.toString, "foo")
  }

  test("nestWithin with implicitParent=false and .bar & resolves parent") {
    val child  = ssg.sass.parse.SelectorParser.tryParse(".bar &")
    val parent = ssg.sass.parse.SelectorParser.tryParse("foo")
    assert(child.isDefined, "Failed to parse .bar &")
    assert(parent.isDefined, "Failed to parse foo")
    val result = child.get.nestWithin(parent, implicitParent = false)
    assertEquals(result.toString, ".bar foo")
  }
}
