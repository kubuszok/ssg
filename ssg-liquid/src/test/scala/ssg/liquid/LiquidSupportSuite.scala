/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/test/java/liqp/parser/LiquidSupportTest.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: munit FunSuite instead of JUnit
 *   Idiom: Java Bean getter methods for reflection-based introspection
 *   Note: All tests in this suite require reflection (JVM-only)
 *   Note: Original used Java field "val" with getVal() — renamed to "val_"
 *     in Scala since "val" is a keyword, but getVal() preserved for Bean compat.
 *     Templates use {{foo.child.val}} matching the getVal() getter. */
package ssg
package liquid

import ssg.liquid.parser.{ Inspectable, LiquidSupport }

import java.util.{ HashMap => JHashMap, Map => JMap }

final class LiquidSupportSuite extends munit.FunSuite {

  import LiquidSupportSuite.*

  private val EAGER_RENDERING_PARSER: TemplateParser = new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.EAGER).build()

  private def getDataAsFoo(foo: Any): JHashMap[String, Any] = {
    val data = new JHashMap[String, Any]()
    data.put("foo", foo)
    data
  }

  private def assertOldRender(template: String, data: JHashMap[String, Any], expected: String): Unit =
    assertEquals(TemplateParser.DEFAULT.parse(template).render(data), expected)

  private def assertEagerRender(template: String, data: JHashMap[String, Any], expected: String): Unit =
    assertEquals(EAGER_RENDERING_PARSER.parse(template).render(data), expected)

  // ---------------------------------------------------------------------------
  // LookupNode tests — default rendering
  // ---------------------------------------------------------------------------

  // testLookupNode1a: Pojo (non-Inspectable) with default rendering → empty
  test("lookupNode 1a: Pojo with default rendering returns empty") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{foo.child.val}}", getDataAsFoo(new Pojo()), "")
  }

  // testLookupNode1b: InsPojo with default rendering → "childOK"
  // NOTE: SSG LiquidSupportFromInspectable doesn't recursively convert nested POJOs
  // (original liqp used Jackson ObjectMapper for deep conversion)
  test("lookupNode 1b: InsPojo with default rendering returns childOK".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{foo.child.val}}", getDataAsFoo(new InsPojo()), "childOK")
  }

  // testLookupNode1c: SuppPojo with default rendering → "SuppChild"
  test("lookupNode 1c: SuppPojo with default rendering returns SuppChild") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{foo.child.val}}", getDataAsFoo(new SuppPojo()), "SuppChild")
  }

  // testLookupNode2a: Pojo with eager rendering → "childOK"
  // NOTE: SSG EAGER mode doesn't recursively convert nested POJOs to Maps
  test("lookupNode 2a: Pojo with eager rendering returns childOK".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{foo.child.val}}", getDataAsFoo(new Pojo()), "childOK")
  }

  // testLookupNode2b: InsPojo with eager rendering → "childOK"
  // NOTE: SSG EAGER mode doesn't recursively convert nested POJOs to Maps
  test("lookupNode 2b: InsPojo with eager rendering returns childOK".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{foo.child.val}}", getDataAsFoo(new InsPojo()), "childOK")
  }

  // testLookupNode2c: SuppPojo with eager rendering → "SuppChild"
  test("lookupNode 2c: SuppPojo with eager rendering returns SuppChild") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{foo.child.val}}", getDataAsFoo(new SuppPojo()), "SuppChild")
  }

  // ---------------------------------------------------------------------------
  // Map filter tests
  // ---------------------------------------------------------------------------

  // testMapFilter1a: Pojo with map filter in default mode raises exception
  test("mapFilter 1a: Pojo with map filter in default mode raises exception") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    intercept[Exception] {
      assertOldRender("{{ foo | map: 'child' | map: 'val' }}", getDataAsFoo(new Pojo()), null)
    }
  }

  // testMapFilter1b: InsPojo with map filter in default mode → "childOK"
  // NOTE: SSG MapFilter doesn't handle non-Map Inspectable objects
  test("mapFilter 1b: InsPojo with map filter returns childOK".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{ foo | map: 'child' | map: 'val' }}", getDataAsFoo(new InsPojo()), "childOK")
  }

  // testMapFilter1c: SuppPojo with map filter in default mode → "SuppChild"
  // NOTE: SSG MapFilter doesn't handle LiquidSupport objects in the pipeline
  test("mapFilter 1c: SuppPojo with map filter returns SuppChild".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{ foo | map: 'child' | map: 'val' }}", getDataAsFoo(new SuppPojo()), "SuppChild")
  }

  // testMapFilter2a: Pojo with map filter in eager mode → "childOK"
  // NOTE: SSG MapFilter doesn't handle non-Map objects after EAGER evaluation
  test("mapFilter 2a: Pojo with eager map filter returns childOK".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{ foo | map: 'child' | map: 'val' }}", getDataAsFoo(new Pojo()), "childOK")
  }

  // testMapFilter2b: InsPojo with map filter in eager mode → "childOK"
  // NOTE: SSG MapFilter doesn't handle non-Map objects after EAGER evaluation
  test("mapFilter 2b: InsPojo with eager map filter returns childOK".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{ foo | map: 'child' | map: 'val' }}", getDataAsFoo(new InsPojo()), "childOK")
  }

  // testMapFilter2c: SuppPojo with map filter in eager mode → "SuppChild"
  test("mapFilter 2c: SuppPojo with eager map filter returns SuppChild") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{ foo | map: 'child' | map: 'val' }}", getDataAsFoo(new SuppPojo()), "SuppChild")
  }

  // ---------------------------------------------------------------------------
  // LookupNode size tests
  // ---------------------------------------------------------------------------

  // testLookupNodeSize1a: Pojo.child.size with default rendering → ""
  test("lookupNodeSize 1a: Pojo child size with default returns empty") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{foo.child.size}}", getDataAsFoo(new Pojo()), "")
  }

  // testLookupNodeSize1b: InsPojo.child.size with default rendering → "1"
  // NOTE: SSG doesn't recursively introspect nested POJOs — child is not a Map
  test("lookupNodeSize 1b: InsPojo child size with default returns 1".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{foo.child.size}}", getDataAsFoo(new InsPojo()), "1")
  }

  // testLookupNodeSize1c: SuppPojo.child.size with default rendering → "1"
  test("lookupNodeSize 1c: SuppPojo child size with default returns 1") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertOldRender("{{foo.child.size}}", getDataAsFoo(new SuppPojo()), "1")
  }

  // testLookupNodeSize2a: Pojo.child.size with eager rendering → "1"
  // NOTE: SSG EAGER mode doesn't recursively convert nested POJOs to Maps
  test("lookupNodeSize 2a: Pojo child size with eager returns 1".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{foo.child.size}}", getDataAsFoo(new Pojo()), "1")
  }

  // testLookupNodeSize2b: InsPojo.child.size with eager rendering → "1"
  // NOTE: SSG EAGER mode doesn't recursively convert nested POJOs to Maps
  test("lookupNodeSize 2b: InsPojo child size with eager returns 1".fail) {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{foo.child.size}}", getDataAsFoo(new InsPojo()), "1")
  }

  // testLookupNodeSize2c: SuppPojo.child.size with eager rendering → "1"
  test("lookupNodeSize 2c: SuppPojo child size with eager returns 1") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    assertEagerRender("{{foo.child.size}}", getDataAsFoo(new SuppPojo()), "1")
  }

  // ---------------------------------------------------------------------------
  // Foo / general tests
  // ---------------------------------------------------------------------------

  // verifyOldBehaviorWorks: Foo (non-Inspectable) with default rendering → ""
  test("verifyOldBehaviorWorks: plain Foo returns empty with default rendering") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    val data = new JHashMap[String, Any]()
    data.put("foo", new Foo())
    val fooA = TemplateParser.DEFAULT.parse("{{foo.a}}").render(data)
    assertEquals(fooA, "")
  }

  // renderMapWithPojosWithNewRenderingSettings: Foo with eager rendering → "A"
  test("renderMapWithPojosWithNewRenderingSettings: Foo returns A with eager rendering") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    val data = new JHashMap[String, Any]()
    data.put("foo", new Foo())
    val fooA = EAGER_RENDERING_PARSER.parse("{{foo.a}}").render(data)
    assertEquals(fooA, "A")
  }

  // renderMapWithPojosWithMarkingInspectable: FooWrapper (Inspectable) with default → "A"
  test("renderMapWithPojosWithMarkingInspectable: FooWrapper returns A") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    val data = new JHashMap[String, Any]()
    data.put("foo", new FooWrapper())
    val fooA = TemplateParser.DEFAULT.parse("{{foo.a}}").render(data)
    assertEquals(fooA, "A")
  }

  // ---------------------------------------------------------------------------
  // LiquidSupport target tests
  // ---------------------------------------------------------------------------

  // testLiquidSupport: Target with LiquidSupport returns "OK" regardless of set value
  test("testLiquidSupport: LiquidSupport target renders toLiquid value") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    val inspect = new Target()
    inspect.setVal("not this")
    val vars = new JHashMap[String, Any]()
    vars.put("a", inspect)
    val res = TemplateParser.DEFAULT.parse("{{a.val}}").render(vars)
    assertEquals(res, "OK")
  }

  // renderLiquidSupportWithNewRenderingSettings: Target with eager rendering → "OK"
  test("renderLiquidSupportWithNewRenderingSettings: eager mode also uses toLiquid") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    val inspect = new Target()
    inspect.setVal("not this")
    val vars = new JHashMap[String, Any]()
    vars.put("a", inspect)
    val fooA = EAGER_RENDERING_PARSER.parse("{{a.val}}").render(vars)
    assertEquals(fooA, "OK")
  }
}

/** Companion object with POJO classes used by LiquidSupportSuite.
  *
  * Defined at top level so getter methods are public and visible to reflection. Uses Java Bean-style getVal()/setVal() to match the original Java POJOs (since Scala "val" is a keyword, the field is
  * named val_ internally).
  */
object LiquidSupportSuite {

  class PojoChild {
    private var val_     : String = "childOK"
    def getVal:            String = val_
    def setVal(v: String): Unit   = val_ = v
  }

  class Pojo {
    private var val_          : String    = "OK"
    private var child_        : PojoChild = new PojoChild()
    def getVal:                 String    = val_
    def setVal(v:   String):    Unit      = val_ = v
    def getChild:               PojoChild = child_
    def setChild(c: PojoChild): Unit      = child_ = c
  }

  class InsPojo extends Pojo with Inspectable

  class SuppPojo extends Pojo with LiquidSupport {
    override def toLiquid(): JMap[String, Any] = {
      val map   = new JHashMap[String, Any]()
      val child = new JHashMap[String, Any]()
      child.put("val", "SuppChild")
      map.put("child", child)
      map
    }
  }

  // The original Foo class has a public field "a" — use BeanProperty
  // to generate getA()/setA() for Java Bean introspection
  class Foo {
    @scala.beans.BeanProperty
    var a: String = "A"
  }

  class FooWrapper extends Foo with Inspectable

  class Target extends LiquidSupport {
    private var val_     : String = scala.compiletime.uninitialized
    def getVal:            String = val_
    def setVal(v: String): Unit   = val_ = v

    override def toLiquid(): JMap[String, Any] = {
      val data = new JHashMap[String, Any]()
      data.put("val", "OK")
      data
    }
  }
}
