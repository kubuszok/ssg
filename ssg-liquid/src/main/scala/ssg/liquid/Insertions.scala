/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/Insertions.java
 * Original: Copyright (c) Christian Kohlschütter
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Idiom: Immutable map of Insertion instances
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/Insertions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid

import ssg.liquid.blocks._
import ssg.liquid.tags._

import java.util
import java.util.{ Collection => JCollection, Collections, HashMap, Map => JMap, Set => JSet }

/** An immutable map of Insertions (tags and blocks). */
final class Insertions private (private val map: JMap[String, Insertion]) {

  private val _blockNames: JSet[String] = {
    val set = new util.HashSet[String]()
    val it  = map.entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      if (entry.getValue.isInstanceOf[blocks.Block]) {
        set.add(entry.getKey)
      }
    }
    Collections.unmodifiableSet(set)
  }

  private val _tagNames: JSet[String] = {
    val set = new util.HashSet[String]()
    val it  = map.entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      if (!entry.getValue.isInstanceOf[blocks.Block]) {
        set.add(entry.getKey)
      }
    }
    Collections.unmodifiableSet(set)
  }

  def blockNames: JSet[String] = _blockNames

  def tagNames: JSet[String] = _tagNames

  /** Returns a new Insertions that combines this instance with the other. Other takes precedence. */
  def mergeWith(other: Insertions): Insertions =
    if ((other eq this) || other.map.isEmpty) {
      this
    } else if (this.map.isEmpty) {
      other
    } else {
      val newMap = new HashMap[String, Insertion](map)
      newMap.putAll(other.map)
      new Insertions(newMap)
    }

  /** Returns the Insertion registered under the given name, or null. */
  def get(name: String): Insertion = map.get(name)

  /** Returns an unmodifiable collection of the stored Insertions. */
  def values(): JCollection[Insertion] = Collections.unmodifiableCollection(map.values())

  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: Insertions => other.map == map
    case _ => false
  }

  override def toString: String =
    if (map.isEmpty) getClass.getName + ".EMPTY"
    else super.toString + map
}

object Insertions {

  val EMPTY: Insertions = new Insertions(Collections.emptyMap())

  /** Creates an Insertions instance from a collection of Insertion objects. */
  def of(insertions: JCollection[Insertion]): Insertions =
    if (insertions.isEmpty) {
      EMPTY
    } else {
      val map = new HashMap[String, Insertion]()
      val it  = insertions.iterator()
      while (it.hasNext) {
        val ins = it.next()
        map.put(ins.name, ins)
      }
      new Insertions(map)
    }

  /** Creates an Insertions instance from vararg Insertion objects. */
  def of(insertions: Insertion*): Insertions =
    of(util.Arrays.asList(insertions*))

  /** Creates an Insertions instance from a map. */
  def of(insertions: JMap[String, Insertion]): Insertions =
    if (insertions.isEmpty) EMPTY
    else new Insertions(insertions)

  /** The standard insertions. */
  val STANDARD_INSERTIONS: Insertions = Insertions.of(
    new Assign(),
    new tags.Break(),
    new Capture(),
    new Case(),
    new Comment(),
    new tags.Continue(),
    new Cycle(),
    new Decrement(),
    new For(),
    new If(),
    new Ifchanged(),
    new Include(),
    new Increment(),
    new Raw(),
    new Tablerow(),
    new Unless()
  )

  /** Jekyll insertions — adds include_relative to standard insertions. */
  val JEKYLL_INSERTIONS: Insertions = STANDARD_INSERTIONS.mergeWith(
    Insertions.of(
      new IncludeRelative()
    )
  )
}
