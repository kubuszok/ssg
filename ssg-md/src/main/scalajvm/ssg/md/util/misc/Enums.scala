/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: the three enum questions flexmark asks of `java.lang.Class` and `java.lang.Enum`, answered by
 * java's own reflection. The generated code reaches this object through the port's call-site
 * substitutions (ssg-md/port/main.conf); Scala.js and Scala Native have no enum reflection and ship
 * a registry at the same name. */
package ssg
package md
package util
package misc

object Enums {

  def declaringClass[E <: java.lang.Enum[E]](e: E): Class[E] = e.getDeclaringClass()

  def isEnum(cls: Class[?]): Boolean = cls.isEnum()

  // the cast only drops scala's reading of java's `T[]` as `Array[Object & T]`
  def constants[T](cls: Class[T]): Array[T] = cls.getEnumConstants().asInstanceOf[Array[T]]

  /** An enum states its constants. Java reads them reflectively, so nothing is kept here. */
  def announce[E <: java.lang.Enum[E]](cls: Class[E], values: () => Array[E]): Unit = ()
}
