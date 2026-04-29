/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package options
package test

import ssg.md.util.html.MutableAttributes

final class AttributesSuite extends munit.FunSuite {

  test("testBasic") {
    val attributes = new MutableAttributes()

    assertEquals(attributes.contains("class"), false, "empty no attributes")
    assertEquals(attributes.containsValue("class", "class1"), false, "empty no values")

    attributes.addValue("class", "class1")
    assertEquals(attributes.getValue("class"), "class1", "add value")
    assertEquals(attributes.contains("class"), true, "contains added attribute")
    assertEquals(attributes.containsValue("class", "class1"), true, "contains added value")

    attributes.addValue("class", "class2")
    assertEquals(attributes.getValue("class"), "class1 class2", "add value")
    assertEquals(attributes.contains("class"), true, "contains added attribute")
    assertEquals(attributes.containsValue("class", "class1"), true, "contains old value")
    assertEquals(attributes.containsValue("class", "class2"), true, "contains added value")

    attributes.addValue("class", "class3")
    assertEquals(attributes.getValue("class"), "class1 class2 class3", "add value")
    assertEquals(attributes.contains("class"), true, "contains added attribute")
    assertEquals(attributes.containsValue("class", "class1"), true, "contains old value")
    assertEquals(attributes.containsValue("class", "class2"), true, "contains added value")
    assertEquals(attributes.containsValue("class", "class3"), true, "contains added value")

    attributes.removeValue("class", "class2")
    assertEquals(attributes.getValue("class"), "class1 class3", "removed value")
    assertEquals(attributes.contains("class"), true, "contains removed value attribute")
    assertEquals(attributes.containsValue("class", "class1"), true, "contains old value")
    assertEquals(attributes.containsValue("class", "class2"), false, "does not contain removed value")
    assertEquals(attributes.containsValue("class", "class3"), true, "contains old value")

    attributes.removeValue("class", "class3")
    assertEquals(attributes.getValue("class"), "class1", "removed value")
    assertEquals(attributes.contains("class"), true, "contains removed value attribute")
    assertEquals(attributes.containsValue("class", "class1"), true, "contains old value")
    assertEquals(attributes.containsValue("class", "class2"), false, "does not contain removed value")
    assertEquals(attributes.containsValue("class", "class3"), false, "does not contain removed value")

    attributes.removeValue("class", "class1")
    assertEquals(attributes.getValue("class"), "", "removed value")
    assertEquals(attributes.contains("class"), true, "contains removed value attribute")
    assertEquals(attributes.containsValue("class", "class1"), false, "does not contain removed value")
    assertEquals(attributes.containsValue("class", "class2"), false, "does not contain removed value")
    assertEquals(attributes.containsValue("class", "class3"), false, "does not contain removed value")

    attributes.replaceValue("class", "class1 class2 class3")
    assertEquals(attributes.getValue("class"), "class1 class2 class3", "replaced value")
    assertEquals(attributes.contains("class"), true, "contains value attribute")
    assertEquals(attributes.containsValue("class", "class1"), true, "contains added values")
    assertEquals(attributes.containsValue("class", "class2"), true, "contains added values")
    assertEquals(attributes.containsValue("class", "class3"), true, "contains added values")

    attributes.addValue("id", "id1")
    assertEquals(attributes.getValue("id"), "id1", "add value")
    assertEquals(attributes.contains("id"), true, "contains added attribute")
    assertEquals(attributes.containsValue("id", "id1"), true, "contains added value")
  }
}
