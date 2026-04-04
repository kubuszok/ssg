/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/IncludeRelative.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 *
 * NOTE: Like Include, full implementation requires Template + NameResolver (Phase 6).
 */
package ssg
package liquid
package tags

/** Jekyll-style include_relative tag. Includes templates relative to the current file location. */
class IncludeRelative extends Include("include_relative")
