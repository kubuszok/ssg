/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Filters.java
 * Original: Copyright (c) Christian Kohlschütter
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Idiom: Immutable map of Filter instances
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Filters.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import java.util
import java.util.{ Collection => JCollection, Collections, HashMap, List => JList, Map => JMap }

/** An immutable map of Filters. */
final class Filters private (private val map: JMap[String, Filter]) {

  /** Returns the filter registered under the given name, or null. */
  def get(name: String): Filter = map.get(name)

  /** Returns an unmodifiable collection of all Filters. */
  def values(): JCollection[Filter] = Collections.unmodifiableCollection(map.values())

  /** Returns the filters as an unmodifiable Map. */
  def getMap: JMap[String, Filter] = map

  /** Returns a new Filters instance that combines this with the other (other takes precedence). */
  def mergeWith(other: Filters): Filters =
    if ((other eq this) || other.map.isEmpty) {
      this
    } else if (this.map.isEmpty) {
      other
    } else {
      val newMap = new HashMap[String, Filter](map)
      newMap.putAll(other.map)
      new Filters(newMap)
    }

  /** Returns a new Filters instance that combines this with the given filter list. */
  def mergeWith(other: JList[Filter]): Filters =
    if (other.isEmpty) {
      this
    } else if (this.map.isEmpty) {
      Filters.of(other)
    } else {
      val newMap = new HashMap[String, Filter](map)
      newMap.putAll(Filters.of(other).map)
      new Filters(newMap)
    }

  override def hashCode(): Int = map.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: Filters => other.map == map
    case _ => false
  }

  override def toString: String =
    if (map.isEmpty) getClass.getName + ".EMPTY"
    else super.toString + map
}

object Filters {

  val EMPTY: Filters = new Filters(Collections.emptyMap())

  /** Creates a Filters instance from a collection of Filter objects. */
  def of(filters: JCollection[Filter]): Filters =
    if (filters.isEmpty) {
      EMPTY
    } else {
      val map = new HashMap[String, Filter]()
      val it  = filters.iterator()
      while (it.hasNext) {
        val f = it.next()
        map.put(f.name, f)
      }
      new Filters(Collections.unmodifiableMap(map))
    }

  /** Creates a Filters instance from vararg Filter objects. */
  def of(filters: Filter*): Filters =
    of(util.Arrays.asList(filters*))

  /** Creates a Filters instance from a map. */
  def of(filters: JMap[String, Filter]): Filters =
    if (filters.isEmpty) EMPTY
    else new Filters(Collections.unmodifiableMap(new HashMap[String, Filter](filters)))

  /** Common filters (Shopify Liquid standard). */
  val COMMON_FILTERS: Filters = Filters.of(
    new Abs(),
    new Absolute_Url(),
    new Append(),
    new At_Least(),
    new At_Most(),
    new Capitalize(),
    new Ceil(),
    new Compact(),
    new Concat(),
    new Date(),
    new Default(),
    new Divided_By(),
    new Downcase(),
    new Escape(),
    new Escape_Once(),
    new First(),
    new Floor(),
    new H(),
    new Join(),
    new Json(),
    new Last(),
    new Lstrip(),
    new MapFilter(),
    new Minus(),
    new Modulo(),
    new Newline_To_Br(),
    new Plus(),
    new Prepend(),
    new Remove(),
    new Remove_First(),
    new Replace(),
    new Replace_First(),
    new Reverse(),
    new Round(),
    new Rstrip(),
    new Size(),
    new Slice(),
    new Sort(),
    new Sort_Natural(),
    new Split(),
    new Strip(),
    new Strip_HTML(),
    new Strip_Newlines(),
    new Times(),
    new Truncate(),
    new Truncatewords(),
    new Uniq(),
    new Upcase(),
    new Url_Decode(),
    new Url_Encode(),
    new Where()
  )

  /** Jekyll extra filters. */
  val JEKYLL_EXTRA_FILTERS: Filters = Filters.of(
    new Normalize_Whitespace(),
    new Pop(),
    new Push(),
    new Relative_Url(),
    new Shift(),
    new Unshift(),
    new Where_Exp()
  )

  /** Default filters (same as COMMON_FILTERS). */
  val DEFAULT_FILTERS: Filters = COMMON_FILTERS

  /** Jekyll filters (common + Jekyll extras). */
  val JEKYLL_FILTERS: Filters = COMMON_FILTERS.mergeWith(JEKYLL_EXTRA_FILTERS)
}
