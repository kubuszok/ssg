/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListItemPlacement.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListItemPlacement.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gfm
package tasklist

enum TaskListItemPlacement {
  case AS_IS, INCOMPLETE_FIRST, INCOMPLETE_NESTED_FIRST, COMPLETE_TO_NON_TASK, COMPLETE_NESTED_TO_NON_TASK
}
