/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/internal/TranslationHandlerImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter
package internal

import ssg.md.ast.AnchorRefTarget
import ssg.md.html.renderer.{ HtmlIdGenerator, HtmlIdGeneratorFactory }
import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.data.{ DataHolder, MutableDataHolder, MutableDataSet }

import java.util.regex.Pattern
import scala.collection.mutable
import scala.language.implicitConversions

class TranslationHandlerImpl(options: DataHolder, idGeneratorFactory: HtmlIdGeneratorFactory) extends TranslationHandler {

  private val myFormatterOptions: FormatterOptions       = new FormatterOptions(options)
  private val myNonTranslatingTexts: mutable.HashMap[String, String] = mutable.HashMap.empty // map placeholder to non-translating text
  private val myAnchorTexts: mutable.HashMap[String, String]         = mutable.HashMap.empty // map anchor id to non-translating text
  private val myTranslatingTexts: mutable.HashMap[String, String]    = mutable.HashMap.empty // map placeholder to translating original text
  private val myTranslatedTexts: mutable.HashMap[String, String]     = mutable.HashMap.empty // map placeholder to translated text
  private val myTranslatingPlaceholders: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty // list of placeholders to index
  private val myTranslatingSpans: mutable.ArrayBuffer[String]        = mutable.ArrayBuffer.empty
  private val myNonTranslatingSpans: mutable.ArrayBuffer[String]     = mutable.ArrayBuffer.empty
  private val myTranslatedSpans: mutable.ArrayBuffer[String]         = mutable.ArrayBuffer.empty
  private val myPlaceHolderMarkerPattern: Pattern                    = Pattern.compile(myFormatterOptions.translationExcludePattern)
  private val myTranslationStore: MutableDataSet                     = new MutableDataSet()

  private val myOriginalRefTargets: mutable.HashMap[String, Int]     = mutable.HashMap.empty // map ref target id to translation index
  private val myTranslatedRefTargets: mutable.HashMap[Int, String]   = mutable.HashMap.empty // map translation index to translated ref target id
  @annotation.nowarn("msg=unused private member") // faithful port: maps used in full translation pipeline
  private val myOriginalAnchors: mutable.HashMap[String, String]     = mutable.HashMap.empty // map placeholder id to original ref id
  @annotation.nowarn("msg=unused private member") // faithful port: maps used in full translation pipeline
  private val myTranslatedAnchors: mutable.HashMap[String, String]   = mutable.HashMap.empty // map placeholder id to translated ref target id

  private var myPlaceholderId: Int                      = 0
  private var myAnchorId: Int                           = 0
  private var myTranslatingSpanId: Int                  = 0
  private var myNonTranslatingSpanId: Int               = 0
  private var myRenderPurpose: RenderPurpose            = RenderPurpose.FORMAT
  private var myWriter: Nullable[MarkdownWriter]        = Nullable.empty
  private var myIdGenerator: Nullable[HtmlIdGenerator]  = Nullable.empty
  private var myPlaceholderGenerator: Nullable[TranslationPlaceholderGenerator] = Nullable.empty
  private var myNonTranslatingPostProcessor: Nullable[String => CharSequence]   = Nullable.empty
  private var myMergeContext: Nullable[MergeContext]     = Nullable.empty

  override def beginRendering(node: Document, context: NodeFormatterContext, out: MarkdownWriter): Unit = {
    // collect anchor ref ids
    myWriter = Nullable(out)
    myIdGenerator = Nullable(idGeneratorFactory.create())
    myIdGenerator.foreach(_.generateIds(node))
  }

  private def isNotBlank(csq: CharSequence): Boolean = {
    val iMax = csq.length
    var i = 0
    while (i < iMax) {
      if (!Character.isWhitespace(csq.charAt(i))) return true
      i += 1
    }
    false
  }

  override def getTranslatingTexts: List[String] = {
    myTranslatingPlaceholders.clear()
    val translatingSnippets = mutable.ArrayBuffer.empty[String]
    val repeatedTranslatingIndices = mutable.HashMap.empty[String, Int]

    // collect all the translating snippets first
    for ((key, value) <- myTranslatingTexts) {
      if (isNotBlank(value) && !myPlaceHolderMarkerPattern.matcher(value).matches()) {
        // see if it is repeating
        if (!repeatedTranslatingIndices.contains(value)) {
          // new, index
          repeatedTranslatingIndices.put(value, translatingSnippets.size)
          translatingSnippets += value
          myTranslatingPlaceholders += key
        }
      }
    }

    for (text <- myTranslatingSpans) {
      if (isNotBlank(text) && !myPlaceHolderMarkerPattern.matcher(text).matches()) {
        translatingSnippets += text
      }
    }

    translatingSnippets.toList
  }

  override def setTranslatedTexts(translatedTexts: List[? <: CharSequence]): Unit = {
    myTranslatedTexts.clear()
    myTranslatedTexts ++= myTranslatingTexts
    myTranslatedSpans.clear()

    // collect all the translating snippets first
    var i = 0
    val iMax = translatedTexts.size
    val placeholderSize = myTranslatingPlaceholders.size
    val repeatedTranslatingIndices = mutable.HashMap.empty[String, Int]

    for ((key, value) <- myTranslatingTexts) {
      if (isNotBlank(value) && !myPlaceHolderMarkerPattern.matcher(value).matches()) {
        repeatedTranslatingIndices.get(value) match {
          case None =>
            if (i >= placeholderSize) {
              // break equivalent - just stop processing
            } else {
              // new, index
              repeatedTranslatingIndices.put(value, i)
              myTranslatedTexts.put(key, translatedTexts(i).toString)
              i += 1
            }
          case Some(index) =>
            myTranslatedTexts.put(key, translatedTexts(index).toString)
        }
      }
    }

    for (text <- myTranslatingSpans) {
      if (isNotBlank(text) && !myPlaceHolderMarkerPattern.matcher(text).matches()) {
        if (i < iMax) {
          myTranslatedSpans += translatedTexts(i).toString
          i += 1
        }
      } else {
        // add original blank sequence
        myTranslatedSpans += text
      }
    }
  }

  override def setRenderPurpose(renderPurpose: RenderPurpose): Unit = {
    myAnchorId = 0
    myTranslatingSpanId = 0
    myPlaceholderId = 0
    myRenderPurpose = renderPurpose
    myNonTranslatingSpanId = 0
  }

  override def getRenderPurpose: RenderPurpose = myRenderPurpose

  override def isTransformingText: Boolean = myRenderPurpose != RenderPurpose.FORMAT

  override def transformAnchorRef(pageRef: CharSequence, anchorRef: CharSequence): CharSequence = {
    myRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS =>
        myAnchorId += 1
        val replacedTextId = String.format(myFormatterOptions.translationIdFormat, myAnchorId: java.lang.Integer)
        myAnchorTexts.put(replacedTextId, anchorRef.toString)
        replacedTextId

      case RenderPurpose.TRANSLATED_SPANS =>
        myAnchorId += 1
        String.format(myFormatterOptions.translationIdFormat, myAnchorId: java.lang.Integer)

      case RenderPurpose.TRANSLATED =>
        myAnchorId += 1
        val anchorIdText = String.format(myFormatterOptions.translationIdFormat, myAnchorId: java.lang.Integer)
        val resolvedPageRef = myNonTranslatingTexts.get(pageRef.toString)

        resolvedPageRef match {
          case Some(ref) if ref.isEmpty =>
            // self reference, add it to the list
            myAnchorTexts.get(anchorIdText) match {
              case Some(refId) =>
                // original ref id for the heading we should have them all
                myOriginalRefTargets.get(refId) match {
                  case Some(spanIndex) =>
                    // have the index to translatingSpans
                    myTranslatedRefTargets.get(spanIndex) match {
                      case Some(translatedRefId) => return translatedRefId
                      case None                  => ()
                    }
                  case None => ()
                }
                return refId
              case None => ()
            }
            anchorRef

          case Some(_) =>
            myAnchorTexts.get(anchorIdText) match {
              case Some(resolvedAnchorRef) => resolvedAnchorRef
              case None                    => anchorRef
            }

          case None => anchorRef
        }

      case RenderPurpose.FORMAT | _ =>
        anchorRef
    }
  }

  override def customPlaceholderFormat(generator: TranslationPlaceholderGenerator, render: TranslatingSpanRender): Unit = {
    if (myRenderPurpose != RenderPurpose.TRANSLATED_SPANS) {
      val savedGenerator = myPlaceholderGenerator
      myPlaceholderGenerator = Nullable(generator)
      render.render(myWriter.get.getContext, myWriter.get)
      myPlaceholderGenerator = savedGenerator
    }
  }

  override def translatingSpan(render: TranslatingSpanRender): Unit =
    translatingRefTargetSpan(Nullable.empty, render)

  private def renderInSubContext(render: TranslatingSpanRender, copyToMain: Boolean): String = {
    val savedMarkdown = myWriter.get
    val subContext = myWriter.get.getContext.getSubContext()
    val writer = subContext.getMarkdown
    myWriter = Nullable(writer)

    render.render(subContext, writer)

    // trim off eol added by toString(0)
    val spanText = writer.toString(2, -1)

    myWriter = Nullable(savedMarkdown)
    if (copyToMain) {
      myWriter.get.append(spanText)
    }
    spanText
  }

  override def translatingRefTargetSpan(target: Nullable[Node], render: TranslatingSpanRender): Unit = {
    myRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS =>
        val spanText = renderInSubContext(render, copyToMain = true)
        target.foreach { t =>
          if (!t.isInstanceOf[AnchorRefTarget] || !t.asInstanceOf[AnchorRefTarget].isExplicitAnchorRefId) {
            val id = myIdGenerator.get.getId(t).get
            myOriginalRefTargets.put(id, myTranslatingSpans.size)
          }
        }
        myTranslatingSpans += spanText

      case RenderPurpose.TRANSLATED_SPANS =>
        // we output translated text instead of render
        renderInSubContext(render, copyToMain = false)

        val translated = myTranslatedSpans(myTranslatingSpanId)

        target.foreach { t =>
          if (!t.isInstanceOf[AnchorRefTarget] || !t.asInstanceOf[AnchorRefTarget].isExplicitAnchorRefId) {
            // only if does not have an explicit id then map to translated text id
            val id = myIdGenerator.get.getId(translated).get
            myTranslatedRefTargets.put(myTranslatingSpanId, id)
          }
        }

        myTranslatingSpanId += 1

        myWriter.get.append(translated)

      case RenderPurpose.TRANSLATED =>
        target.foreach { t =>
          if (!t.isInstanceOf[AnchorRefTarget] || !t.asInstanceOf[AnchorRefTarget].isExplicitAnchorRefId) {
            // only if does not have an explicit id then map to translated text id
            val id = myIdGenerator.get.getId(t).get
            myTranslatedRefTargets.put(myTranslatingSpanId, id)
          }
        }

        myTranslatingSpanId += 1
        renderInSubContext(render, copyToMain = true)

      case RenderPurpose.FORMAT | _ =>
        render.render(myWriter.get.getContext, myWriter.get)
    }
  }

  override def nonTranslatingSpan(render: TranslatingSpanRender): Unit = {
    myRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS =>
        val spanText = renderInSubContext(render, copyToMain = false)

        myNonTranslatingSpans += spanText
        myNonTranslatingSpanId += 1

        val replacedTextId = getPlaceholderId(myFormatterOptions.translationIdFormat, myNonTranslatingSpanId, Nullable.empty, Nullable.empty, Nullable.empty)
        myWriter.get.append(replacedTextId)

      case RenderPurpose.TRANSLATED_SPANS =>
        // we output translated text instead of render
        renderInSubContext(render, copyToMain = false)

        val translated = myNonTranslatingSpans(myNonTranslatingSpanId)
        myNonTranslatingSpanId += 1
        myWriter.get.append(translated)

      case RenderPurpose.TRANSLATED =>
        // we output translated text instead of render
        renderInSubContext(render, copyToMain = true)
        myNonTranslatingSpanId += 1

      case RenderPurpose.FORMAT | _ =>
        render.render(myWriter.get.getContext, myWriter.get)
    }
  }

  def getPlaceholderId(format: String, placeholderId: Int, prefix: Nullable[CharSequence], suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): String = {
    val replacedTextId = myPlaceholderGenerator.fold(String.format(format, placeholderId: java.lang.Integer))(_.getPlaceholder(placeholderId))
    if (prefix.isEmpty && suffix.isEmpty && suffix2.isEmpty) replacedTextId
    else TranslationHandlerImpl.addPrefixSuffix(replacedTextId, prefix, suffix, suffix2)
  }

  override def postProcessNonTranslating(postProcessor: String => CharSequence, scope: Runnable): Unit = {
    val savedValue = myNonTranslatingPostProcessor
    try {
      myNonTranslatingPostProcessor = Nullable(postProcessor)
      scope.run()
    } finally {
      myNonTranslatingPostProcessor = savedValue
    }
  }

  override def postProcessNonTranslating[T](postProcessor: String => CharSequence, scope: () => T): T = {
    val savedValue = myNonTranslatingPostProcessor
    try {
      myNonTranslatingPostProcessor = Nullable(postProcessor)
      scope()
    } finally {
      myNonTranslatingPostProcessor = savedValue
    }
  }

  override def isPostProcessingNonTranslating: Boolean = myNonTranslatingPostProcessor.isDefined

  override def transformNonTranslating(prefix: Nullable[CharSequence], nonTranslatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence = {
    // need to transfer trailing EOLs to id
    val trimmedEOL: CharSequence = suffix2.getOrElse {
      val basedSequence = ssg.md.util.sequence.BasedSequence.of(nonTranslatingText)
      basedSequence.trimmedEOL()
    }

    myRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS =>
        myPlaceholderId += 1
        val replacedTextId = getPlaceholderId(myFormatterOptions.translationIdFormat, myPlaceholderId, prefix, suffix, Nullable(trimmedEOL))
        val useReplacedTextId = myNonTranslatingPostProcessor.fold(replacedTextId)(pp => pp(replacedTextId).toString)
        myNonTranslatingTexts.put(useReplacedTextId, nonTranslatingText.toString)
        useReplacedTextId

      case RenderPurpose.TRANSLATED_SPANS =>
        myPlaceholderId += 1
        val placeholderId = getPlaceholderId(myFormatterOptions.translationIdFormat, myPlaceholderId, prefix, suffix, Nullable(trimmedEOL))
        myNonTranslatingPostProcessor.fold(placeholderId: CharSequence)(pp => pp(placeholderId))

      case RenderPurpose.TRANSLATED =>
        if (nonTranslatingText.length > 0) {
          val text = myNonTranslatingTexts.getOrElse(nonTranslatingText.toString, "")
          myNonTranslatingPostProcessor.fold(text: CharSequence)(pp => pp(text))
        } else {
          nonTranslatingText
        }

      case RenderPurpose.FORMAT | _ =>
        nonTranslatingText
    }
  }

  override def transformTranslating(prefix: Nullable[CharSequence], translatingText: CharSequence, suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): CharSequence = {
    myRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS =>
        myPlaceholderId += 1
        val replacedTextId = getPlaceholderId(myFormatterOptions.translationIdFormat, myPlaceholderId, prefix, suffix, suffix2)
        myTranslatingTexts.put(replacedTextId, translatingText.toString)
        replacedTextId

      case RenderPurpose.TRANSLATED_SPANS =>
        myPlaceholderId += 1
        getPlaceholderId(myFormatterOptions.translationIdFormat, myPlaceholderId, prefix, suffix, suffix2)

      case RenderPurpose.TRANSLATED =>
        val replacedText = if (prefix.isEmpty && suffix.isEmpty && suffix2.isEmpty) translatingText else TranslationHandlerImpl.addPrefixSuffix(translatingText, prefix, suffix, suffix2)
        myTranslatedTexts.get(replacedText.toString) match {
          case Some(text) if prefix.isDefined || suffix.isDefined || suffix2.isDefined =>
            TranslationHandlerImpl.addPrefixSuffix(text, prefix, suffix, suffix2)
          case Some(text) => text
          case None       => translatingText
        }

      case RenderPurpose.FORMAT | _ =>
        translatingText
    }
  }

  override def setMergeContext(context: MergeContext): Unit =
    myMergeContext = Nullable(context)

  override def getIdGenerator: Nullable[HtmlIdGenerator] = myIdGenerator

  override def getTranslationStore: MutableDataHolder = myTranslationStore

  override def getMergeContext: Nullable[MergeContext] = myMergeContext
}

object TranslationHandlerImpl {
  def addPrefixSuffix(placeholderId: CharSequence, prefix: Nullable[CharSequence], suffix: Nullable[CharSequence], suffix2: Nullable[CharSequence]): String = {
    if (prefix.isEmpty && suffix.isEmpty && suffix2.isEmpty) placeholderId.toString
    else {
      val sb = new StringBuilder()
      prefix.foreach(sb.append)
      sb.append(placeholderId)
      suffix.foreach(sb.append)
      suffix2.foreach(sb.append)
      sb.toString
    }
  }
}
