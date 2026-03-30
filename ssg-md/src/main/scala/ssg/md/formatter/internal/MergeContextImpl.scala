/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/internal/MergeContextImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter
package internal

import ssg.md.util.ast.Document

import scala.collection.mutable
import scala.language.implicitConversions

class MergeContextImpl(
  private var myDocuments:           Array[Document],
  private val myTranslationHandlers: Array[TranslationHandler]
) extends MergeContext {

  assert(myDocuments.length == myTranslationHandlers.length)

  private val myTranslationHandlerDocumentMap: mutable.HashMap[TranslationContext, Document] = mutable.HashMap.empty
  updateDocumentMap()
  for (handler <- myTranslationHandlers)
    handler.setMergeContext(this)

  private def updateDocumentMap(): Unit = {
    var i = 0
    while (i < myDocuments.length) {
      myTranslationHandlerDocumentMap.put(myTranslationHandlers(i), myDocuments(i))
      i += 1
    }
  }

  def documents: Array[Document] = myDocuments

  def documents_=(documents: Array[Document]): Unit = {
    assert(documents.length == myTranslationHandlers.length)
    myDocuments = documents
    updateDocumentMap()
  }

  def translationHandlers: Array[TranslationHandler] = myTranslationHandlers

  override def getDocument(context: TranslationContext): Document =
    myTranslationHandlerDocumentMap(context)

  override def forEachPrecedingDocument(document: Nullable[Document], consumer: MergeContextConsumer): Unit = {
    var i = 0
    while (i < myDocuments.length)
      if (document.isDefined && (myDocuments(i) eq document.get)) {
        // stop when we reach the target document
        i = myDocuments.length // break
      } else {
        consumer.accept(myTranslationHandlers(i), myDocuments(i), i)
        i += 1
      }
  }
}
