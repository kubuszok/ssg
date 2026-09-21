/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the jackson-databind bean serialisation reached from
 *   src/main/java/liqp/parser/LiquidSupport.java's objectToMap, which liqp uses for Inspectable
 *   values and for its EAGER evaluate mode
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the JVM row, where java's answer is available and is therefore the answer.
 *   Convention: what jackson's default bean serialisation read — public no-argument getters
 *     (getX, and isX at a boolean) and public fields. liqp declares no jackson property
 *     annotation anywhere in its main or test sources, so none is honoured here.
 *   Idiom: properties are ordered by NAME. Jackson's own order is reflection order, which java
 *     does not specify, so a stable order is the closest thing to it that can be reproduced.
 */
package ssg
package liquid
package json

import java.lang.reflect.{ Field, Method, Modifier }
import java.util.{ LinkedHashMap => JLinkedHashMap, Map => JMap }

/** Reads an object's properties the way jackson read them. */
object Beans {

  /** The value's public getters and public fields, by property name. Values are raw — the caller
    * puts each into JSON's own shape.
    *
    * A value with NO readable property answers the empty map, which is what liqp asks for by turning
    * jackson's fail-on-empty-beans off.
    */
  def toMap(value: Object): JMap[String, Object] = {
    val cls = value.getClass
    val out = new JLinkedHashMap[String, Object]()
    (getters(cls).map(m => propertyName(m.getName) -> read(m, value)) ++
      fields(cls).map(f => f.getName -> read(f, value)))
      .foldLeft(Map.empty[String, Object]) { case (acc, (name, v)) => if (acc.contains(name)) acc else acc + (name -> v) }
      .toList
      .sortBy(_._1)
      .foreach((name, v) => out.put(name, v))
    out
  }

  /** `getX()` and `isX()` at a boolean, public, no arguments, a value returned, and not one of
    * `java.lang.Object`'s own — `getClass` is the one that would otherwise qualify.
    */
  private def getters(cls: Class[?]): List[Method] =
    cls.getMethods.toList.filter { m =>
      Modifier.isPublic(m.getModifiers) &&
      !Modifier.isStatic(m.getModifiers) &&
      m.getParameterCount == 0 &&
      m.getReturnType != classOf[Unit] &&
      m.getReturnType.getName != "void" &&
      (m.getDeclaringClass ne classOf[Object]) &&
      (isGetName(m.getName) || isIsName(m.getName, m.getReturnType))
    }

  private def fields(cls: Class[?]): List[Field] =
    cls.getFields.toList.filter(f => Modifier.isPublic(f.getModifiers) && !Modifier.isStatic(f.getModifiers))

  private def isGetName(name: String): Boolean =
    name.length > 3 && name.startsWith("get") && name != "getClass" && Character.isUpperCase(name.charAt(3))

  private def isIsName(name: String, returns: Class[?]): Boolean =
    name.length > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2)) &&
      (returns == classOf[Boolean] || returns == classOf[java.lang.Boolean] || returns.getName == "boolean")

  private def propertyName(accessor: String): String = {
    val bare = if (accessor.startsWith("get")) accessor.substring(3) else accessor.substring(2)
    // java bean decapitalisation: an initial run of capitals is a name, not a word
    if (bare.length > 1 && Character.isUpperCase(bare.charAt(1))) bare
    else bare.substring(0, 1).toLowerCase(java.util.Locale.ENGLISH) + bare.substring(1)
  }

  /** A member declared public on a class that is not itself public needs the access check turned
    * off, which is what jackson does too; a liqp test's POJO is a package-private nested class or
    * an anonymous one every time.
    */
  private def read(m: Method, on: Object): Object = {
    m.setAccessible(true)
    m.invoke(on)
  }

  private def read(f: Field, on: Object): Object = {
    f.setAccessible(true)
    f.get(on)
  }
}
