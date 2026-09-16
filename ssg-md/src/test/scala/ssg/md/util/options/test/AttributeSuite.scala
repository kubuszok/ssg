/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package options
package test

import ssg.md.util.html.AttributeImpl

final class AttributeSuite extends munit.FunSuite {

  test("testBasic") {
    val attribute = AttributeImpl.of("name", "value1", ' ')
    assertEquals(attribute.getName(), "name", "no name change")

    assertEquals(attribute.containsValue("value1"), true, "contains a simple value")

    val attribute1 = attribute.setValue("value2")
    assertEquals(attribute1.getValue(), "value1 value2", "add a new value")
    assertEquals(attribute1.equals(attribute), false, "non-equality")
    assertEquals(attribute1.getName(), "name", "no name change")

    val attribute2 = attribute.removeValue("value2")
    assertEquals(attribute.getValue(), "value1", "remove non-existent value")
    assertEquals(attribute2, attribute, "remove non-existent value, no new attribute")
    assertEquals(attribute2.equals(attribute), true, "equality")
    assertEquals(attribute2.getName(), "name", "no name change")

    val attribute3 = attribute.replaceValue("value2")
    assertEquals(attribute3.getValue(), "value2", "replace value")
    assertEquals(attribute3.getName(), "name", "no name change")

    val attribute4 = attribute1.setValue("value1")
    assertEquals(attribute4.getValue(), "value1 value2", "add existing value")
    assertEquals(attribute4, attribute1, "add existing value, no new attribute")
    assertEquals(attribute4.getName(), "name", "no name change")

    val attribute5 = attribute1.setValue("value1")
    assertEquals(attribute5.getValue(), "value1 value2", "add existing value")
    assertEquals(attribute5, attribute1, "add existing value, no new attribute")
    assertEquals(attribute5.getName(), "name", "no name change")

    val attribute6 = attribute1.setValue("value2")
    assertEquals(attribute6.getValue(), "value1 value2", "add existing value")
    assertEquals(attribute6, attribute1, "add existing value, no new attribute")
    assertEquals(attribute6.getName(), "name", "no name change")

    val attribute7 = attribute1.setValue("value3")
    assertEquals(attribute7.getValue(), "value1 value2 value3", "add existing value")
    assertEquals(attribute7.getName(), "name", "no name change")

    val attribute8 = attribute7.removeValue("value2")
    assertEquals(attribute8.getValue(), "value1 value3", "remove middle value")
    assertEquals(attribute8.equals(attribute7), false, "non-equality")
    assertEquals(attribute8.getName(), "name", "no name change")

    val attribute9 = attribute3.replaceValue("value2")
    assertEquals(attribute9.getValue(), "value2", "replace value")
    assertEquals(attribute9, attribute3, "replace same value, no new attribute")
    assertEquals(attribute9.getName(), "name", "no name change")
  }
}
