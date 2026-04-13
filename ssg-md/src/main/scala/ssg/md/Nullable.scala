/*
 * Copyright (c) 2025-2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Re-export of Nullable from ssg-commons for backwards compatibility.
 * New code should import from ssg.commons.Nullable directly.
 */
package ssg
package md

/** Re-export of Nullable from ssg-commons. */
type Nullable[A] = ssg.commons.Nullable[A]
val Nullable = ssg.commons.Nullable
