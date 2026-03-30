/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListItemPlacement.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package tasklist

enum TaskListItemPlacement {
  case AS_IS, INCOMPLETE_FIRST, INCOMPLETE_NESTED_FIRST, COMPLETE_TO_NON_TASK, COMPLETE_NESTED_TO_NON_TASK
}
