/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/public_member_map_view.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: public_member_map_view.dart → PublicMemberMapView.scala
 *   Convention: Filters keys starting with - or _ (Sass private members)
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/util/public_member_map_view.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package util

/** An unmodifiable map view that hides keys starting with - or _ (Sass private members).
  */
final class PublicMemberMapView[V](
  private val inner: Map[String, V]
) extends scala.collection.immutable.AbstractMap[String, V] {

  override def get(key: String): Option[V] =
    if (isPublic(key)) inner.get(key) else None

  override def iterator: Iterator[(String, V)] =
    inner.iterator.filter { case (k, _) => isPublic(k) }

  override def removed(key: String): Map[String, V] =
    iterator.toMap.removed(key)

  override def updated[V1 >: V](key: String, value: V1): Map[String, V1] =
    iterator.toMap.updated(key, value)

  override def size: Int = inner.keys.count(isPublic)

  /** Whether member is a public Sass member (doesn't start with - or _). */
  private def isPublic(member: String): Boolean =
    member.nonEmpty && {
      val start = member.charAt(0).toInt
      start != CharCode.$minus && start != CharCode.$underscore
    }
}
