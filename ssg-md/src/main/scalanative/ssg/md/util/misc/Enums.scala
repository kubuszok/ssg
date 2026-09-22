/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: `Enum.getDeclaringClass`, `Class.isEnum` and `Class.getEnumConstants` do not exist here.
 * The first two are restated from `Class.getSuperclass`, exactly as the JDK writes them. The third
 * cannot be derived from a class, so an enum states its own constants (`announce`, added to each
 * enum flexmark keeps in a BitFieldSet by ssg-md/port/main.conf) and `constants` reads them back.
 * An enum nobody announced is refused by name rather than answered with an empty universe. */
package ssg
package md
package util
package misc

object Enums {

  private val announced: java.util.HashMap[Class[?], () => Array[? <: java.lang.Enum[?]]] = new java.util.HashMap()

  def declaringClass[E <: java.lang.Enum[E]](e: E): Class[E] = {
    val cls   = e.getClass
    val zuper = cls.getSuperclass
    (if (zuper eq classOf[java.lang.Enum[?]]) cls else zuper).asInstanceOf[Class[E]]
  }

  def isEnum(cls: Class[?]): Boolean = cls.getSuperclass eq classOf[java.lang.Enum[?]]

  /** Java's contract: a fresh array of the constants in declaration order, null for a class that is not an enum. */
  def constants[T](cls: Class[T]): Array[T] =
    if (!isEnum(cls)) {
      null.asInstanceOf[Array[T]] // java interop boundary: `Class.getEnumConstants` answers null for a non-enum class
    } else {
      Option(announced.get(cls)) match {
        case Some(values) => values().clone().asInstanceOf[Array[T]]
        case None         =>
          throw new IllegalStateException(
            "The constants of " + cls.getName + " are not known: this platform has no enum reflection, so the enum has to call " +
              "ssg.md.util.misc.Enums.announce(classOf[...], () => values) before it is used in a BitFieldSet"
          )
      }
    }

  /** An enum states its constants. The function is kept, not called: it is handed over while the enum is still being initialised. */
  def announce[E <: java.lang.Enum[E]](cls: Class[E], values: () => Array[E]): Unit = {
    val _ = announced.put(cls, values)
  }
}
