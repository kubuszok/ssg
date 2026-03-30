/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/FlatDependencyStage.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package dependency

/** @deprecated
  *   use [[DependencyResolver.resolveFlatDependencies]]
  */
@deprecated("use DependencyResolver.resolveFlatDependencies", "")
class FlatDependencyStage[T](val dependents: List[T])
