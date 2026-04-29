/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/Dependent.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/Dependent.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package dependency

import ssg.md.Nullable

trait Dependent {

  /** @return
    *   null or a list of dependents that must be executed before calling this one if any of the blocks in the list affect global state then these will be run on ALL blocks of the document before this
    *   preprocessor is called.
    */
  def afterDependents: Nullable[Set[Class[?]]]

  /** @return
    *   null or a list of dependents that must be executed after calling this one if any of the blocks in the list affect global state then these will be run on ALL blocks of the document before this
    *   preprocessor is called.
    */
  def beforeDependents: Nullable[Set[Class[?]]]

  /** @return
    *   true if this dependent affects the global scope, which means that any that depend on it have to be run after this dependent has run against all elements. Otherwise, the dependent can run on an
    *   element after its dependents have processed an element. parsed.
    */
  def affectsGlobalScope: Boolean
}
