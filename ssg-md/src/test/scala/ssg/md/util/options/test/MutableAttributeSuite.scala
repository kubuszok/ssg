/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package options
package test

import ssg.md.util.html.MutableAttributeImpl

final class MutableAttributeSuite extends munit.FunSuite {

  test("testBasic") {
    val attribute = MutableAttributeImpl.of("name", "value1", ' ')
    assertEquals(attribute.name, "name", "no name change")

    assertEquals(attribute.containsValue("value1"), true, "contains a simple value")

    val attribute1 = attribute.copy().setValue("value2")
    assertEquals(attribute1.value, "value1 value2", "add a new value")
    assertEquals(attribute1.equals(attribute), false, "non-equality")
    assertEquals(attribute1.name, "name", "no name change")

    val attribute2 = attribute.copy().removeValue("value2")
    assertEquals(attribute.value, "value1", "remove non-existent value")
    assertEquals(attribute2, attribute, "remove non-existent value, no new attribute")
    assertEquals(attribute2.equals(attribute), true, "equality")
    assertEquals(attribute2.name, "name", "no name change")

    val attribute3 = attribute.copy().replaceValue("value2")
    assertEquals(attribute3.value, "value2", "replace value")
    assertEquals(attribute3.name, "name", "no name change")

    val attribute4 = attribute1.setValue("value1")
    assertEquals(attribute4.value, "value1 value2", "add existing value")
    assertEquals(attribute4, attribute1, "add existing value, no new attribute")
    assertEquals(attribute4.name, "name", "no name change")

    val attribute5 = attribute1.setValue("value1")
    assertEquals(attribute5.value, "value1 value2", "add existing value")
    assertEquals(attribute5, attribute1, "add existing value, no new attribute")
    assertEquals(attribute5.name, "name", "no name change")

    val attribute6 = attribute1.copy().setValue("value2")
    assertEquals(attribute6.value, "value1 value2", "add existing value")
    assertEquals(attribute6, attribute1, "add existing value, no new attribute")
    assertEquals(attribute6.name, "name", "no name change")

    val attribute7 = attribute1.copy().setValue("value3")
    assertEquals(attribute7.value, "value1 value2 value3", "add existing value")
    assertEquals(attribute7.name, "name", "no name change")

    val attribute8 = attribute7.copy().removeValue("value2")
    assertEquals(attribute8.value, "value1 value3", "remove middle value")
    assertEquals(attribute8.equals(attribute7), false, "non-equality")
    assertEquals(attribute8.name, "name", "no name change")

    val attribute9 = attribute3.copy().replaceValue("value2")
    assertEquals(attribute9.value, "value2", "replace value")
    assertEquals(attribute9, attribute3, "replace same value, no new attribute")
    assertEquals(attribute9.name, "name", "no name change")
  }

  test("test_Style") {
    val attribute = MutableAttributeImpl.of("style", "")

    attribute.setValue("color:#white")
    assertEquals(attribute.value, "color:#white", "add value")

    attribute.setValue("background:#black")
    assertEquals(attribute.value, "color:#white;background:#black", "add value")

    attribute.setValue("font-family:monospaced;color:#green")
    assertEquals(attribute.value, "color:#green;background:#black;font-family:monospaced", "add and change multiple values")

    attribute.setValue("font-family")
    assertEquals(attribute.value, "color:#green;background:#black", "remove value")

    attribute.removeValue("color;background")
    assertEquals(attribute.value, "", "remove values")
  }
}
