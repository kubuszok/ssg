/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/NodeContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/NodeContext.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.builder.ISequenceBuilder

trait NodeContext[N, C <: NodeContext[N, C]] {

  /** Creates a child rendering context that can be used to collect rendered html text. The child context inherits everything but the HtmlRenderer and doNotRenderLinksNesting from the parent.
    *
    * @return
    *   a new rendering context with a given appendable for its output
    */
  def getSubContext(): C

  /** Creates a child rendering context that can be used to collect rendered html text. The child context inherits everything but the HtmlRenderer and doNotRenderLinksNesting from the parent.
    *
    * @param options
    *   options to use for the context (only options which do not affect the context construction will be used)
    * @return
    *   a new rendering context with a given appendable for its output
    */
  def getSubContext(options: Nullable[DataHolder]): C

  /** Creates a child rendering context that can be used to collect rendered html text. The child context inherits everything but the HtmlRenderer and doNotRenderLinksNesting from the parent.
    *
    * @param options
    *   options to use for the context (only options which do not affect the context construction will be used)
    * @param builder
    *   sequence builder to user for appended text for tracking original base offsets
    * @return
    *   a new rendering context with a given appendable for its output
    */
  def getSubContext(options: Nullable[DataHolder], builder: ISequenceBuilder[?, ?]): C

  /** @return
    *   the current node being rendered
    */
  def getCurrentNode: N

  /** Get options for the context
    *
    * @return
    *   data holder
    */
  def getOptions: DataHolder
}
