/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedOrdinalRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedOrdinalRenderer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package enumerated
package reference

import ssg.md.Nullable

trait EnumeratedOrdinalRenderer {

  /** Start of rendering for all renderings
    *
    * @param renderings
    *   renderings which will be rendered
    */
  def startRendering(renderings: Array[EnumeratedReferenceRendering]): Unit

  /** Execute this runnable when empty enum text or link is encountered
    *
    * @param runnable
    *   runnable
    */
  def setEnumOrdinalRunnable(runnable: Nullable[Runnable]): Unit

  /** Return current enum ordinal runnable, used to save previous state
    *
    * @return
    *   current empty enum runnable
    */
  def getEnumOrdinalRunnable: Nullable[Runnable]

  /** Render individual reference format
    *
    * @param referenceOrdinal
    *   ordinal for the reference
    * @param referenceFormat
    *   reference format or null
    * @param defaultText
    *   default text to use if referenceFormat is null or not being used
    * @param needSeparator
    *   true if need to add separator character after output of referenceOrdinal
    *
    * Should set current enum ordinal runnable to output the given referenceOrdinal if referenceFormat is not null the runnable is saved before this call and restored after so there is no need to save
    * its value.
    *
    * NOTE: if referenceFormat is null and the current runnable is not null then it should be run after output of default text and before output of referenceOrdinal, to make sure that parent compound
    * ordinal formats are output.
    */
  def render(referenceOrdinal: Int, referenceFormat: EnumeratedReferenceBlock, defaultText: String, needSeparator: Boolean): Unit

  /** After Rendering is complete
    */
  def endRendering(): Unit
}
