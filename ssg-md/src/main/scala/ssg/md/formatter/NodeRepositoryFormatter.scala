/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/NodeRepositoryFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/formatter/NodeRepositoryFormatter.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package formatter

import ssg.md.html.HtmlRenderer
import ssg.md.util.ast.{ Document, Node, NodeRepository, ReferenceNode, ReferencingNode }
import ssg.md.util.data.{ DataHolder, DataKey, DataSet }
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

abstract class NodeRepositoryFormatter[
  R <: NodeRepository[B],
  B <: Node & ReferenceNode[R, B, N],
  N <: Node & ReferencingNode[R, B]
](options: DataHolder, referenceMapKey: DataKey[java.util.Map[String, String]], uniquificationMapKey: DataKey[java.util.Map[String, String]])
    extends PhasedNodeFormatter {

  protected val referenceRepository:        R                     = getRepository(options)
  protected val referenceList:              java.util.List[B]     = referenceRepository.values()
  protected val unusedReferences:           mutable.HashSet[Node] = mutable.HashSet.empty
  protected val lastReference:              Nullable[B]           = if (referenceList.isEmpty) Nullable.empty else Nullable(referenceList.get(referenceList.size() - 1))
  protected var recheckUndefinedReferences: Boolean               = HtmlRenderer.RECHECK_UNDEFINED_REFERENCES.get(Nullable(options))
  protected var repositoryNodesDone:        Boolean               = false

  private val myComparator: java.util.Comparator[B] = (o1: B, o2: B) => o1.compareTo(o2)

  private var referenceTranslationMap:     Nullable[java.util.Map[String, String]] = Nullable.empty
  protected var referenceUniqificationMap: Nullable[java.util.Map[String, String]] = Nullable.empty

  def getReferenceComparator: java.util.Comparator[B] = myComparator

  def getRepository(options:               DataHolder):                                                 R
  def getReferencePlacement:                                                                            ElementPlacement
  def getReferenceSort:                                                                                 ElementPlacementSort
  protected def renderReferenceBlock(node: B, context: NodeFormatterContext, markdown: MarkdownWriter): Unit

  /** Whether references should be made unique
    *
    * @return
    *   true if yes, false if leave all references as is
    */
  protected def makeReferencesUnique: Boolean = true

  protected def getTranslationReferencePlacement(context: NodeFormatterContext): ElementPlacement =
    if (context.isTransformingText) ElementPlacement.AS_IS
    else getReferencePlacement

  def modifyTransformedReference(transformedReferenceId: String, context: NodeFormatterContext): String =
    transformedReferenceId

  private def renderReferenceBlockUnique(node: B, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.getRenderPurpose == RenderPurpose.TRANSLATED) {
      context.postProcessNonTranslating(
        (id: String) =>
          referenceUniqificationMap.fold(id: CharSequence) { uMap =>
            val uniqueS = uMap.getOrDefault(id, id)
            uniqueS
          },
        new Runnable { def run(): Unit = renderReferenceBlock(node, context, markdown) }
      )
    } else {
      renderReferenceBlock(node, context, markdown)
    }

  protected def transformReferenceId(nodeText: String, context: NodeFormatterContext): String = boundary {
    if (context.isTransformingText) {
      context.getRenderPurpose match {
        case RenderPurpose.TRANSLATION_SPANS | RenderPurpose.TRANSLATED_SPANS =>
          val transformed = referenceTranslationMap
            .flatMap { tMap =>
              val existing = tMap.get(nodeText)
              if (existing != null) Nullable(existing) // @nowarn - Java Map may return null
              else Nullable.empty[String]
            }
            .getOrElse {
              val t = context.transformNonTranslating(Nullable.empty, nodeText, Nullable.empty, Nullable.empty).toString
              referenceTranslationMap.foreach(_.put(nodeText, t))
              t
            }
          modifyTransformedReference(transformed, context)

        case RenderPurpose.TRANSLATED =>
          val untransformed = modifyTransformedReference(nodeText, context)
          val s             = context.transformNonTranslating(Nullable.empty, untransformed, Nullable.empty, Nullable.empty).toString

          // apply uniquification
          // disable otherwise may get double mapping
          if (!context.isPostProcessingNonTranslating) {
            referenceUniqificationMap.foreach { uMap =>
              val uniqueS = uMap.get(s)
              if (uniqueS != null) { // @nowarn - Java Map may return null
                break(uniqueS)
              }
            }
          }
          s

        case _ => nodeText
      }
    } else {
      nodeText
    }
  }

  override def getFormattingPhases: Nullable[Set[FormattingPhase]] =
    Nullable(Set(FormattingPhase.COLLECT, FormattingPhase.DOCUMENT_TOP, FormattingPhase.DOCUMENT_BOTTOM))

  override def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit = {
    // here non-rendered elements can be collected so that they are rendered in another part of the document
    if (context.isTransformingText && referenceMapKey != null) { // @nowarn - null check for safety
      if (context.getRenderPurpose == RenderPurpose.TRANSLATION_SPANS) {
        context.getTranslationStore.set(referenceMapKey, new java.util.HashMap[String, String]())
      }
      referenceTranslationMap = Nullable(referenceMapKey.get(Nullable(context.getTranslationStore)))
    }

    phase match {
      case FormattingPhase.COLLECT =>
        referenceUniqificationMap = Nullable.empty

        if (context.isTransformingText && uniquificationMapKey != null && makeReferencesUnique) { // @nowarn
          if (context.getRenderPurpose == RenderPurpose.TRANSLATION_SPANS) {
            // need to uniquify the ids across documents
            context.getMergeContext.foreach { mergeContext =>
              uniquifyIds(context, markdown, document)
            }
          }

          referenceUniqificationMap = Nullable(uniquificationMapKey.get(Nullable(context.getTranslationStore)))
        }

        if (getTranslationReferencePlacement(context).isChange && getReferenceSort.isUnused) {
          // get all ref nodes and figure out which ones are unused
          val iter = referenceList.iterator()
          while (iter.hasNext)
            unusedReferences.add(iter.next())
          val nodeClasses = getNodeClasses
          nodeClasses.foreach { classes =>
            val nodes = context.nodesOfType(classes.toArray.map(_.asInstanceOf[Class[?]]))
            for (node <- nodes)
              lastReference.foreach { lastRef =>
                val referencingNode = lastRef.referencingNode(node)
                referencingNode.foreach { refNode =>
                  val referenceBlock = refNode.getReferenceNode(referenceRepository)
                  if (referenceBlock != null) { // @nowarn - Java interop: getReferenceNode may return null
                    unusedReferences.remove(referenceBlock)
                  }
                }
              }
          }
        }

      case FormattingPhase.DOCUMENT_TOP =>
        if (getTranslationReferencePlacement(context) == ElementPlacement.DOCUMENT_TOP) {
          formatReferences(context, markdown)
        }

      case FormattingPhase.DOCUMENT_BOTTOM =>
        if (getTranslationReferencePlacement(context) == ElementPlacement.DOCUMENT_BOTTOM) {
          formatReferences(context, markdown)
        }

      case _ => ()
    }
  }

  private def formatReferences(context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    val references = new java.util.ArrayList[B](referenceList)

    val referenceSort = getReferenceSort
    referenceSort match {
      case ElementPlacementSort.AS_IS => ()

      case ElementPlacementSort.SORT =>
        references.sort(getReferenceComparator)

      case ElementPlacementSort.SORT_UNUSED_LAST | ElementPlacementSort.SORT_DELETE_UNUSED | ElementPlacementSort.DELETE_UNUSED =>
        val used    = new java.util.ArrayList[B]()
        val unused  = new java.util.ArrayList[B]()
        val refIter = references.iterator()
        while (refIter.hasNext) {
          val reference = refIter.next()
          if (!unusedReferences.contains(reference)) {
            used.add(reference)
          } else if (!referenceSort.isDeleteUnused) {
            unused.add(reference)
          }
        }

        if (referenceSort.isSort) {
          used.sort(getReferenceComparator)
          if (!referenceSort.isDeleteUnused) {
            unused.sort(getReferenceComparator)
          }
        }

        references.clear()
        references.addAll(used)
        if (!referenceSort.isDeleteUnused) {
          references.addAll(unused)
        }

      case null => // @nowarn - defensive catch-all, faithful port from Java switch
        throw new IllegalStateException("Unexpected value: " + referenceSort)
    }

    markdown.blankLine()
    val iter = references.iterator()
    while (iter.hasNext)
      renderReferenceBlockUnique(iter.next(), context, markdown)
    markdown.blankLine()
    repositoryNodesDone = true
  }

  protected def renderReference(node: B, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (!repositoryNodesDone) {
      getTranslationReferencePlacement(context) match {
        case ElementPlacement.AS_IS =>
          renderReferenceBlockUnique(node, context, markdown)
          if (node.next.isEmpty || node.next.exists(_.getClass != node.getClass)) {
            markdown.blankLine()
          }

        case ElementPlacement.GROUP_WITH_FIRST =>
          // must be the first since we are here
          formatReferences(context, markdown)

        case ElementPlacement.GROUP_WITH_LAST =>
          lastReference.foreach { lastRef =>
            if (node eq lastRef) {
              formatReferences(context, markdown)
            }
          }

        case _ => ()
      }
    }

  /** Compute needed id map to make reference ids unique across documents[] up to entry equal to document
    */
  protected def uniquifyIds(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document): Unit = {
    // collect ids and values to uniquify references up to our document
    val combinedRefs: R = getRepository(new DataSet()) // create an empty repository
    val idMap = new java.util.HashMap[String, String]()

    context.getMergeContext.foreach { mergeContext =>
      mergeContext.forEachPrecedingDocument(
        Nullable(document),
        new MergeContextConsumer {
          override def accept(docContext: TranslationContext, doc: Document, index: Int): Unit = {
            val docRefs           = getRepository(doc)
            val uniquificationMap = uniquificationMapKey.get(Nullable(docContext.getTranslationStore))
            NodeRepository.transferReferences(combinedRefs, docRefs, true, uniquificationMap)
          }
        }
      )
    }

    // now map our ids that clash to unique ids by appending increasing integers to id
    val repository = getRepository(document)
    val entryIter  = repository.entrySet().iterator()
    while (entryIter.hasNext) {
      val entry  = entryIter.next()
      val key    = entry.getKey
      var newKey = key
      var i      = 0

      while (combinedRefs.containsKey(newKey)) {
        i += 1
        newKey = s"$key$i"
      }

      if (i > 0) {
        // have conflict, remap
        idMap.put(key, newKey)
      }
    }

    if (!idMap.isEmpty) {
      // save for later use
      context.getTranslationStore.set(uniquificationMapKey, idMap)
    }
  }
}
