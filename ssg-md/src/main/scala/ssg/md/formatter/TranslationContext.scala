/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslationContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/TranslationContext.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package formatter

import ssg.md.html.renderer.HtmlIdGenerator
import ssg.md.util.ast.Node
import ssg.md.util.data.MutableDataHolder

trait TranslationContext {
  def getIdGenerator: Nullable[HtmlIdGenerator]

  /** Get the reason this format rendering is being performed
    *
    * @return
    *   RenderPurpose for current rendering
    */
  def getRenderPurpose: RenderPurpose

  /** Get MutableDataHolder for storing this translation run values across render purpose phases
    */
  def getTranslationStore: MutableDataHolder

  /** Returns false if special translation functions are no-ops
    *
    * @return
    *   true if need to call translation related functions
    */
  def isTransformingText: Boolean

  /** Transform non-translating text
    */
  def transformNonTranslating(prefix: Nullable[CharSequence], nonTranslatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence

  /** @param postProcessor
    *   id post processor for TRANSLATED purpose
    * @param scope
    *   code to which the post processor applies
    */
  def postProcessNonTranslating(postProcessor: String => CharSequence, scope: Runnable): Unit

  /** @param postProcessor
    *   id post processor for TRANSLATED purpose
    * @param scope
    *   code to which the post processor applies
    */
  def postProcessNonTranslating[T](postProcessor: String => CharSequence, scope: () => T): T

  /** @return
    *   true if non-translating post processor is set
    */
  def isPostProcessingNonTranslating: Boolean

  /** Transform translating text but which is contextually isolated
    */
  def transformTranslating(prefix: Nullable[CharSequence], translatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence

  /** Transform anchor reference
    */
  def transformAnchorRef(pageRef: CharSequence, anchorRef: CharSequence): CharSequence

  /** Separate translation span
    */
  def translatingSpan(render: TranslatingSpanRender): Unit

  /** Separate non-translation span
    */
  def nonTranslatingSpan(render: TranslatingSpanRender): Unit

  /** Separate translation span which is also a ref target
    */
  def translatingRefTargetSpan(target: Nullable[Node], render: TranslatingSpanRender): Unit

  /** Temporarily change the format for placeholders
    */
  def customPlaceholderFormat(generator: TranslationPlaceholderGenerator, render: TranslatingSpanRender): Unit

  def getMergeContext: Nullable[MergeContext]
}
