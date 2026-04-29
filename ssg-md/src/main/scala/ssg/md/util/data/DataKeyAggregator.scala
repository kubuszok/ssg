/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataKeyAggregator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/DataKeyAggregator.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package data

import ssg.md.Nullable

/** Interface for aggregating DataKey values from multiple DataHolder instances.
  */
trait DataKeyAggregator {

  /** Combine options by applying aggregate action keys
    *
    * @param combined
    *   set of combined options (by overwriting or combined by prior aggregator)
    * @return
    *   combined and cleaned of aggregate action keys, return MutableDataHolder if it was modified so downstream aggregators re-use the mutable
    */
  def aggregate(combined: DataHolder): DataHolder

  /** Combine aggregate action keys from two sets but do not apply them
    *
    * @param combined
    *   set of combined options (by overwriting or combined by prior aggregator)
    * @param other
    *   set of original uncombined options
    * @param overrides
    *   overriding set of options
    * @return
    *   combined aggregate actions from other and overrides overwritten in combined
    */
  def aggregateActions(combined: DataHolder, other: DataHolder, overrides: DataHolder): DataHolder

  /** Remove any keys which contain aggregation actions and do not represent a state
    *
    * @param combined
    *   combined data holder
    * @return
    *   cleaned of all aggregate action keys
    */
  def clean(combined: DataHolder): DataHolder

  /** return a set of aggregator classes this aggregator should run after
    *
    * @return
    *   keys
    */
  def invokeAfterSet(): Nullable[Set[Class[?]]]

//  /**
//   * return a set of aggregator classes this aggregator should run before
//   *
//   * @return keys
//   */
//  def invokeBeforeSet(): Nullable[Set[Class[?]]]
}
