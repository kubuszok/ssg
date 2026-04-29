/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: com.vladsch.flexmark.ext.attributes.internal → ssg.md.ext.attributes.internal
 *   Convention: Scala 3, Nullable[A], no return
 *   Idiom: match instead of switch, boundary/break for early exit
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesNodeFormatter.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.Nullable
import ssg.md.ast.{ AnchorRefTarget, Heading }
import ssg.md.ast.util.AnchorRefTargetBlockVisitor
import ssg.md.formatter.*
import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.data.{ DataHolder, DataKey, NotNullValueSupplier }
import ssg.md.util.format.options.DiscretionaryText
import ssg.md.util.html.{ Attribute, MutableAttributes }
import ssg.md.util.sequence.{ BasedSequence, PrefixedSubSequence }

import scala.compiletime.uninitialized

import java.{ util => ju }
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class AttributesNodeFormatter(options: DataHolder) extends PhasedNodeFormatter, ExplicitAttributeIdProvider {

  import AttributesNodeFormatter.*

  private var attributeTranslationMap:      ju.Map[String, String]  = uninitialized // set during renderDocument
  private var attributeTranslatedMap:       ju.Map[String, String]  = uninitialized // set during renderDocument
  private var attributeOriginalIdMap:       ju.Map[String, String]  = uninitialized // set during renderDocument
  private var attributeUniquificationIdMap: ju.Map[String, String]  = uninitialized // set during renderDocument
  private var attributeOriginalId:          Int                     = 0
  private val formatOptions:                AttributesFormatOptions = new AttributesFormatOptions(options)

  override def getNodeClasses: Nullable[Set[Class[?]]] = Nullable.empty

  override def getFormattingPhases: Nullable[Set[FormattingPhase]] =
    Nullable(Set(FormattingPhase.COLLECT))

  override def addExplicitId(node: Node, id: Nullable[String], context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (id.isDefined && node.isInstanceOf[Heading]) {
      // if our id != generated id we add explicit attributes if none are found already
      if (context.getRenderPurpose == RenderPurpose.TRANSLATED) {
        if (hasNoIdAttribute(node) && attributeUniquificationIdMap != null) { // @nowarn - may be uninitialized
          val idStr    = id.get
          val uniqueId = attributeUniquificationIdMap.getOrDefault(idStr, idStr)
          if (uniqueId != idStr) {
            markdown.append(" {.")
            markdown.append(uniqueId)
            markdown.append("}")
          }
        }
      }
    }

  private def hasNoIdAttribute(node: Node): Boolean = {
    var haveIdAttribute = false
    boundary {
      val childIt = node.children.iterator()
      while (childIt.hasNext) {
        val child = childIt.next()
        if (child.isInstanceOf[AttributesNode]) {
          val attrIt = child.children.iterator()
          while (attrIt.hasNext) {
            val attr = attrIt.next()
            if (attr.isInstanceOf[AttributeNode] && attr.asInstanceOf[AttributeNode].isId) {
              haveIdAttribute = true
              break()
            }
          }
          if (haveIdAttribute) break()
        }
      }
    }
    !haveIdAttribute
  }

  override def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit = {
    // reset storage for attribute keys and attributes map
    if (context.isTransformingText) {
      context.getTranslationStore.set(ATTRIBUTE_TRANSLATION_ID, Integer.valueOf(0))
      attributeOriginalId = 0

      if (phase == FormattingPhase.COLLECT) {
        // NOTE: clear processed attributes set
        context.getDocument.remove(PROCESSED_ATTRIBUTES)

        if (context.getRenderPurpose == RenderPurpose.TRANSLATION_SPANS) {
          context.getTranslationStore.set(ATTRIBUTE_TRANSLATION_MAP, new ju.HashMap[String, String]())
          context.getTranslationStore.set(ATTRIBUTE_TRANSLATED_MAP, new ju.HashMap[String, String]())
          context.getTranslationStore.set(ATTRIBUTE_ORIGINAL_ID_MAP, new ju.HashMap[String, String]())

          val mergeContext = context.getMergeContext
          if (mergeContext.isDefined) {
            // make ids unique if there is a list of documents
            val mergedUniquified = new ju.HashSet[String]()

            mergeContext.get.forEachPrecedingDocument(
              Nullable(document),
              new MergeContextConsumer {
                override def accept(docContext: TranslationContext, doc: Document, index: Int): Unit = {
                  val attributes          = AttributesExtension.NODE_ATTRIBUTES.get(Nullable(doc: DataHolder))
                  val idUniquificationMap = ATTRIBUTE_UNIQUIFICATION_ID_MAP.get(Nullable(docContext.getTranslationStore: DataHolder))

                  for {
                    attributesNodes <- attributes.values().asScala
                    attributesNode <- attributesNodes.asScala
                  } {
                    val it = attributesNode.children.iterator()
                    while (it.hasNext) {
                      val childNode = it.next()
                      if (childNode.isInstanceOf[AttributeNode]) {
                        val attributeNode = childNode.asInstanceOf[AttributeNode]
                        if (attributeNode.isId) {
                          val key    = attributeNode.value.toString
                          val newKey = idUniquificationMap.getOrDefault(key, key)
                          if (!mergedUniquified.contains(newKey)) {
                            mergedUniquified.add(newKey)
                          }
                        }
                      }
                    }
                  }

                  // add heading ids to contained ids
                  val generator = context.getIdGenerator
                  if (generator.isDefined) {
                    new AnchorRefTargetBlockVisitor() {
                      override protected def visit(refTarget: AnchorRefTarget): Unit = {
                        val refNode = refTarget.asInstanceOf[Node]
                        if (hasNoIdAttribute(refNode)) {
                          var key = generator.get.getId(refNode)
                          if (key.isEmpty) {
                            val text = refTarget.anchorRefText
                            key = generator.get.getId(text)
                            key.foreach { k => refTarget.anchorRefId = k }
                          }
                          key.foreach { k =>
                            val newKey = idUniquificationMap.getOrDefault(k, k)
                            if (!mergedUniquified.contains(newKey)) {
                              mergedUniquified.add(newKey)
                            }
                          }
                        }
                      }
                    }.visit(document.asInstanceOf[Node])
                  }
                }
              }
            )

            // now make ours unique
            val attributes                = AttributesExtension.NODE_ATTRIBUTES.get(Nullable(document: DataHolder))
            val categoryUniquificationMap = ATTRIBUTE_UNIQUIFICATION_CATEGORY_MAP.get(Nullable(context.getTranslationStore: DataHolder))
            val idMap                     = new ju.HashMap[String, String]()

            for {
              attributesNodes <- attributes.values().asScala
              attributesNode <- attributesNodes.asScala
            } {
              val it = attributesNode.children.iterator()
              while (it.hasNext) {
                val childNode = it.next()
                if (childNode.isInstanceOf[AttributeNode]) {
                  val attributeNode = childNode.asInstanceOf[AttributeNode]
                  if (attributeNode.isId) {
                    // this one needs to be unique
                    val valueChars = attributeNode.value
                    val key        = valueChars.toString
                    var useKey     = key

                    val pos = valueChars.indexOf(':')
                    if (pos != -1) {
                      val category       = valueChars.subSequence(0, pos).toString
                      val catId          = valueChars.subSequence(pos + 1).toString
                      val uniqueCategory = categoryUniquificationMap.getOrDefault(category, category)
                      useKey = s"$uniqueCategory:$catId"
                    }

                    var i      = 0
                    var newKey = useKey
                    while (mergedUniquified.contains(newKey)) {
                      i += 1
                      newKey = s"$useKey$i"
                    }

                    if (i > 0 || newKey != key) {
                      idMap.put(key, newKey)
                    }
                  }
                }
              }
            }

            // add heading ids to contained ids
            val generator = context.getIdGenerator
            if (generator.isDefined) {
              new AnchorRefTargetBlockVisitor() {
                override protected def visit(refTarget: AnchorRefTarget): Unit = {
                  val refNode = refTarget.asInstanceOf[Node]
                  if (hasNoIdAttribute(refNode)) {
                    var key = generator.get.getId(refNode)
                    if (key.isEmpty) {
                      val text = refTarget.anchorRefText
                      key = generator.get.getId(text)
                      key.foreach { k => refTarget.anchorRefId = k }
                    }
                    key.foreach { k =>
                      var i      = 0
                      var newKey = k
                      while (mergedUniquified.contains(newKey)) {
                        i += 1
                        newKey = s"$k$i"
                      }
                      if (i > 0 || newKey != k) {
                        idMap.put(k, newKey)
                      }
                    }
                  }
                }
              }.visit(document.asInstanceOf[Node])
            }

            if (!idMap.isEmpty) {
              context.getTranslationStore.set(ATTRIBUTE_UNIQUIFICATION_ID_MAP, idMap.asInstanceOf[ju.Map[String, String]])
            }
          }
        }
      }
    }

    attributeUniquificationIdMap = ATTRIBUTE_UNIQUIFICATION_ID_MAP.get(Nullable(context.getTranslationStore: DataHolder))
    attributeTranslationMap = ATTRIBUTE_TRANSLATION_MAP.get(Nullable(context.getTranslationStore: DataHolder))
    attributeTranslatedMap = ATTRIBUTE_TRANSLATED_MAP.get(Nullable(context.getTranslationStore: DataHolder))
    attributeOriginalIdMap = ATTRIBUTE_ORIGINAL_ID_MAP.get(Nullable(context.getTranslationStore: DataHolder))
  }

  // only registered if assignTextAttributes is enabled
  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[AttributesNode](classOf[AttributesNode], (node, ctx, md) => render(node, ctx, md)),
        new NodeFormattingHandler[AttributesDelimiter](classOf[AttributesDelimiter], (node, ctx, md) => render(node, ctx, md))
      )
    )

  private def getEncodedOriginalId(attribute: String, context: NodeFormatterContext): String =
    context.getRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS =>
        attributeOriginalId += 1
        val encodedAttribute = "#" + String.format(context.getFormatterOptions.translationIdFormat, attributeOriginalId: Integer)
        attributeOriginalIdMap.put(encodedAttribute, attribute)
        encodedAttribute

      case RenderPurpose.TRANSLATED_SPANS =>
        attributeOriginalId += 1
        "#" + String.format(context.getFormatterOptions.translationIdFormat, attributeOriginalId: Integer)

      case RenderPurpose.TRANSLATED =>
        attributeOriginalId += 1
        val id = attributeOriginalIdMap.get(attribute) // @nowarn - may be null from Java Map.get
        if (attributeUniquificationIdMap != null) { // @nowarn - may be uninitialized
          attributeUniquificationIdMap.getOrDefault(id, id)
        } else {
          id
        }

      case _ => // FORMAT
        attribute
    }

  private def render(node: AttributesNode, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    node.previous.foreach { prev =>
      if (!prev.chars.isContinuedBy(node.chars) && !prev.chars.endsWith(" ") && !node.chars.startsWith(" ")) {
        markdown.append(' ')
      }
    }

    if (context.isTransformingText) {
      renderTranslating(node, context, markdown)
    } else {
      renderFormatting(node, context, markdown)
    }

    node.next.foreach { nxt =>
      if (!nxt.isInstanceOf[AttributesNode] && !node.chars.isContinuedBy(nxt.chars) && !node.chars.endsWith(" ") && !nxt.chars.startsWith(" ")) {
        markdown.append(' ')
      }
    }
  }

  private def renderTranslating(node: AttributesNode, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    var firstChild = true
    val it         = node.children.iterator()
    while (it.hasNext) {
      val child         = it.next()
      val attributeNode = child.asInstanceOf[AttributeNode]
      if (!firstChild) markdown.append(' ')

      if (attributeNode.isId) {
        // encode as X:N if has :, otherwise as non-translating id
        val valueChars = attributeNode.value
        val pos        = valueChars.indexOf(':')
        if (pos == -1) {
          var encodedOriginal = getEncodedOriginalId(attributeNode.chars.toString, context)
          if (context.getRenderPurpose == RenderPurpose.TRANSLATED) {
            if (!attributeUniquificationIdMap.isEmpty) {
              val idOnly = encodedOriginal.substring(1)
              encodedOriginal = "#" + attributeUniquificationIdMap.getOrDefault(idOnly, idOnly)
            }
          }
          markdown.append(encodedOriginal)
        } else {
          val category = valueChars.subSequence(0, pos).toString
          val catId    = valueChars.subSequence(pos + 1).toString
          val encoded  = getEncodedIdAttribute(category, catId, context, markdown, attributeTranslationMap, attributeTranslatedMap)
          context.getRenderPurpose match {
            case RenderPurpose.TRANSLATION_SPANS | RenderPurpose.TRANSLATED_SPANS =>
              val encodedAttribute = "#" + encoded
              attributeOriginalIdMap.put(encodedAttribute, attributeNode.chars.toString)
              markdown.append('#').append(encoded)

            case RenderPurpose.TRANSLATED =>
              var encodedOriginal = attributeOriginalIdMap.get("#" + valueChars.toString) // @nowarn - may be null
              if (attributeUniquificationIdMap != null) { // @nowarn
                if (!attributeUniquificationIdMap.isEmpty) {
                  val idOnly = encodedOriginal.substring(1)
                  encodedOriginal = "#" + attributeUniquificationIdMap.getOrDefault(idOnly, idOnly)
                }
              }
              if (encodedOriginal != null) { // @nowarn - may be null from Java Map
                markdown.append(encodedOriginal)
              } else {
                markdown.append(attributeNode.chars.toString)
              }

            case _ => // FORMAT
              markdown.append(attributeNode.chars)
          }
        }
      } else {
        // encode the whole thing as a class
        markdown.appendNonTranslating(".", attributeNode.chars, "")
      }
      firstChild = false
    }
    markdown.append(node.closingMarker)
  }

  private def renderFormatting(node: AttributesNode, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    val processedNodes = PROCESSED_ATTRIBUTES.get(Nullable(context.getDocument: DataHolder))
    if (processedNodes.contains(node)) {
      // already processed as part of a combine-consecutive group
    } else {
      val chars       = node.chars
      val openMarker  = node.openingMarker
      val closeMarker = node.closingMarker
      var spaceAfterOpenMarker: BasedSequence =
        if (chars.safeBaseCharAt(openMarker.endOffset) == ' ') chars.baseSubSequence(openMarker.endOffset, openMarker.endOffset + 1)
        else BasedSequence.NULL
      var spaceBeforeCloseMarker: BasedSequence =
        if (chars.safeBaseCharAt(closeMarker.startOffset - 1) == ' ') chars.baseSubSequence(closeMarker.startOffset - 1, closeMarker.startOffset)
        else BasedSequence.NULL

      formatOptions.attributesSpaces match {
        case DiscretionaryText.AS_IS => // keep as is
        case DiscretionaryText.ADD   =>
          spaceAfterOpenMarker = BasedSequence.SPACE
          spaceBeforeCloseMarker = BasedSequence.SPACE
        case DiscretionaryText.REMOVE =>
          spaceAfterOpenMarker = BasedSequence.NULL
          spaceBeforeCloseMarker = BasedSequence.NULL
      }

      markdown.append(node.openingMarker)
      markdown.append(spaceAfterOpenMarker)
      val valueQuotes = formatOptions.attributeValueQuotes

      var firstChild     = true
      val attributeNodes = new ju.LinkedHashMap[String, AttributeNode]()

      if (formatOptions.combineConsecutive) {
        // see if there are attributes applicable to the same owner as this node
        val nodeAttributeRepository = AttributesExtension.NODE_ATTRIBUTES.get(Nullable(context.getDocument: DataHolder))
        boundary {
          for (entry <- nodeAttributeRepository.entrySet().asScala)
            if (entry.getValue.contains(node)) {
              // have our list
              for (attributesNode <- entry.getValue.asScala) {
                processedNodes.add(attributesNode)
                val childIt = attributesNode.children.iterator()
                while (childIt.hasNext) {
                  val attrChild = childIt.next()
                  val attrNode  = attrChild.asInstanceOf[AttributeNode]
                  attributeNodes.put(attrNode.name.toString, combineAttributes(attributeNodes, attrNode))
                }
              }
              break()
            }
        }
      }

      if (attributeNodes.isEmpty) {
        val childIt = node.children.iterator()
        while (childIt.hasNext) {
          val child    = childIt.next()
          val attrNode = child.asInstanceOf[AttributeNode]
          attributeNodes.put(attrNode.name.toString, combineAttributes(attributeNodes, attrNode))
        }
      }

      val childNodes: ju.Collection[AttributeNode] =
        if (formatOptions.sort) {
          val entries = new ju.ArrayList[ju.Map.Entry[String, AttributeNode]](attributeNodes.entrySet())
          entries.sort((o1, o2) =>
            if (o1.getValue.isId) -1
            else if (o2.getValue.isId) 1
            else if (o1.getValue.isClass) -1
            else if (o2.getValue.isClass) 1
            else o1.getValue.name.compareTo(o2.getValue.name)
          )
          val nodes = new ju.ArrayList[AttributeNode](entries.size())
          for (entry <- entries.asScala)
            nodes.add(entry.getValue)
          nodes
        } else {
          attributeNodes.values()
        }

      for (child <- childNodes.asScala) {
        val attributeNode = child
        if (!firstChild) markdown.append(' ')

        val attrChars = attributeNode.chars
        var name      = attributeNode.name
        val value     = attributeNode.value
        var sep       = attributeNode.attributeSeparator

        var spaceBeforeSep: BasedSequence =
          if (attrChars.safeBaseCharAt(sep.startOffset - 1) == ' ') attrChars.baseSubSequence(sep.startOffset - 1, sep.startOffset)
          else BasedSequence.NULL
        var spaceAfterSep: BasedSequence =
          if (attrChars.safeBaseCharAt(sep.endOffset) == ' ') attrChars.baseSubSequence(sep.endOffset, sep.endOffset + 1)
          else BasedSequence.NULL

        formatOptions.attributeEqualSpace match {
          case DiscretionaryText.AS_IS => // keep as is
          case DiscretionaryText.ADD   =>
            spaceBeforeSep = BasedSequence.SPACE
            spaceAfterSep = BasedSequence.SPACE
          case DiscretionaryText.REMOVE =>
            spaceBeforeSep = BasedSequence.NULL
            spaceAfterSep = BasedSequence.NULL
        }

        var quote     = if (attributeNode.isImplicitName) "" else valueQuotes.quotesFor(value, attributeNode.openingMarker)
        val needQuote = AttributeValueQuotes.NO_QUOTES_DOUBLE_PREFERRED.quotesFor(value, "")

        if (attributeNode.isId) {
          (if (needQuote.isEmpty) formatOptions.attributeId else AttributeImplicitName.EXPLICIT_PREFERRED) match {
            case AttributeImplicitName.AS_IS              => // keep as is
            case AttributeImplicitName.IMPLICIT_PREFERRED =>
              if (!attributeNode.isImplicitName) {
                name = PrefixedSubSequence.prefixOf("#", name.getEmptyPrefix)
                sep = BasedSequence.NULL
                quote = ""
              }
            case AttributeImplicitName.EXPLICIT_PREFERRED =>
              if (attributeNode.isImplicitName) {
                name = PrefixedSubSequence.prefixOf("id", name.getEmptyPrefix)
                sep = PrefixedSubSequence.prefixOf("=", name.getEmptySuffix)
                if (quote.isEmpty) {
                  quote = valueQuotes.quotesFor(value, attributeNode.openingMarker)
                  if (quote.isEmpty) quote = needQuote
                }
              }
            case unexpected => throw new IllegalStateException(s"Unexpected value: $unexpected")
          }
        } else if (attributeNode.isClass) {
          (if (needQuote.isEmpty) formatOptions.attributeClass else AttributeImplicitName.EXPLICIT_PREFERRED) match {
            case AttributeImplicitName.AS_IS              => // keep as is
            case AttributeImplicitName.IMPLICIT_PREFERRED =>
              if (!attributeNode.isImplicitName) {
                name = PrefixedSubSequence.prefixOf(".", name.getEmptyPrefix)
                sep = BasedSequence.NULL
                quote = ""
              }
            case AttributeImplicitName.EXPLICIT_PREFERRED =>
              if (attributeNode.isImplicitName) {
                name = PrefixedSubSequence.prefixOf("class", name.getEmptyPrefix)
                sep = PrefixedSubSequence.prefixOf("=", name.getEmptySuffix)
                if (quote.isEmpty) {
                  quote = valueQuotes.quotesFor(value, attributeNode.openingMarker)
                  if (quote.isEmpty) quote = needQuote
                }
              }
            case unexpected => throw new IllegalStateException(s"Unexpected value: $unexpected")
          }
        }

        markdown.append(name)
        if (!sep.isEmpty) markdown.append(spaceBeforeSep).append(sep).append(spaceAfterSep)

        if (!quote.isEmpty) {
          val replaceQuote = if (quote == "'") "&apos;" else if (quote == "\"") "&quot;" else ""
          markdown.append(quote)
          markdown.append(value.replace(quote, replaceQuote))
          markdown.append(quote)
        } else {
          markdown.append(value)
        }

        firstChild = false
      }

      markdown.append(spaceBeforeCloseMarker)
      markdown.append(node.closingMarker)
    }
  }
}

object AttributesNodeFormatter {

  val ATTRIBUTE_TRANSLATION_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]](
    "ATTRIBUTE_TRANSLATION_MAP",
    new NotNullValueSupplier[ju.Map[String, String]] { def get: ju.Map[String, String] = new ju.HashMap[String, String]() }
  )
  val ATTRIBUTE_TRANSLATED_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]](
    "ATTRIBUTE_TRANSLATED_MAP",
    new NotNullValueSupplier[ju.Map[String, String]] { def get: ju.Map[String, String] = new ju.HashMap[String, String]() }
  )
  val ATTRIBUTE_ORIGINAL_ID_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]](
    "ATTRIBUTE_ORIGINAL_ID_MAP",
    new NotNullValueSupplier[ju.Map[String, String]] { def get: ju.Map[String, String] = new ju.HashMap[String, String]() }
  )
  val PROCESSED_ATTRIBUTES: DataKey[ju.Set[Node]] = new DataKey[ju.Set[Node]](
    "PROCESSED_ATTRIBUTES",
    new NotNullValueSupplier[ju.Set[Node]] { def get: ju.Set[Node] = new ju.HashSet[Node]() }
  )

  // need to have this one available in core formatter
  val ATTRIBUTE_UNIQUIFICATION_ID_MAP: DataKey[ju.Map[String, String]] = Formatter.ATTRIBUTE_UNIQUIFICATION_ID_MAP

  val ATTRIBUTE_UNIQUIFICATION_CATEGORY_MAP: DataKey[ju.Map[String, String]] = new DataKey[ju.Map[String, String]](
    "ATTRIBUTE_UNIQUIFICATION_CATEGORY_MAP",
    new NotNullValueSupplier[ju.Map[String, String]] { def get: ju.Map[String, String] = new ju.HashMap[String, String]() }
  )
  val ATTRIBUTE_TRANSLATION_ID: DataKey[Integer] = new DataKey[Integer]("ATTRIBUTE_TRANSLATION_ID", Integer.valueOf(0))

  def getEncodedIdAttribute(category: String, categoryId: String, context: NodeFormatterContext, markdown: MarkdownWriter): String = {
    val attributeTranslationMap = ATTRIBUTE_TRANSLATION_MAP.get(Nullable(context.getTranslationStore: DataHolder))
    val attributeTranslatedMap  = ATTRIBUTE_TRANSLATED_MAP.get(Nullable(context.getTranslationStore: DataHolder))
    val id                      = getEncodedIdAttribute(category, categoryId, context, markdown, attributeTranslationMap, attributeTranslatedMap)

    if (context.getRenderPurpose == RenderPurpose.TRANSLATED) {
      val idUniquificationMap = ATTRIBUTE_UNIQUIFICATION_ID_MAP.get(Nullable(context.getTranslationStore: DataHolder))
      if (!idUniquificationMap.isEmpty) {
        idUniquificationMap.getOrDefault(id, id)
      } else {
        id
      }
    } else {
      id
    }
  }

  private def getEncodedIdAttribute(
    category:                String,
    categoryId:              String,
    context:                 NodeFormatterContext,
    markdown:                MarkdownWriter,
    attributeTranslationMap: ju.Map[String, String],
    attributeTranslatedMap:  ju.Map[String, String]
  ): String = {
    var encodedCategory = category
    var encodedId       = categoryId
    var placeholderId: Int = ATTRIBUTE_TRANSLATION_ID.get(Nullable(context.getTranslationStore: DataHolder))

    context.getRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS =>
        if (!attributeTranslationMap.containsKey(category)) {
          placeholderId += 1
          encodedCategory = String.format(context.getFormatterOptions.translationIdFormat, placeholderId: Integer)
          attributeTranslationMap.put(category, encodedCategory)
          attributeTranslatedMap.put(encodedCategory, category)
        } else {
          encodedCategory = attributeTranslationMap.get(category) // @nowarn - known present from containsKey check
        }

        if (categoryId != null && !attributeTranslationMap.containsKey(categoryId)) { // @nowarn - Java interop: categoryId may be null
          placeholderId += 1
          encodedId = String.format(context.getFormatterOptions.translationIdFormat, placeholderId: Integer)
          attributeTranslationMap.put(categoryId, encodedId)
          attributeTranslatedMap.put(encodedId, categoryId)
        } else {
          encodedId = attributeTranslationMap.get(categoryId) // @nowarn - may be null
        }

      case RenderPurpose.TRANSLATED_SPANS =>
      // return encoded non-translating text — use category/categoryId as-is

      case RenderPurpose.TRANSLATED =>
        encodedCategory = attributeTranslatedMap.get(category) // @nowarn - may be null from Java Map.get
        if (categoryId != null) { // @nowarn - Java interop
          encodedId = attributeTranslatedMap.get(categoryId) // @nowarn
        }

      case _ => // FORMAT — use category/categoryId as-is
    }

    context.getTranslationStore.set(ATTRIBUTE_TRANSLATION_ID, Integer.valueOf(placeholderId))

    if (encodedId == null) { // @nowarn - may be null from Java Map.get
      encodedCategory
    } else {
      encodedCategory + ':' + encodedId
    }
  }

  private[internal] def combineAttributes(attributeNodes: ju.LinkedHashMap[String, AttributeNode], attributeNode: AttributeNode): AttributeNode =
    if (attributeNode.isId) {
      attributeNodes.remove("id")
      attributeNodes.remove("#")
      attributeNode
    } else if (attributeNode.isClass) {
      var newNode  = attributeNode
      val removed1 = attributeNodes.remove(Attribute.CLASS_ATTR) // @nowarn - may be null from Java Map.remove
      val removed2 = attributeNodes.remove(".") // @nowarn
      if (removed1 != null || removed2 != null) { // @nowarn - null check for Java Map returns
        val attributes = new MutableAttributes()
        if (removed1 != null) attributes.addValue(Attribute.CLASS_ATTR, removed1.value) // @nowarn
        if (removed2 != null) attributes.addValue(Attribute.CLASS_ATTR, removed2.value) // @nowarn
        val value = attributes.getValue(Attribute.CLASS_ATTR)
        if (!attributeNode.value.equals(value)) {
          val newValue = PrefixedSubSequence.prefixOf(value + " ", attributeNode.value)
          newNode = new AttributeNode(attributeNode.name, attributeNode.attributeSeparator, attributeNode.openingMarker, newValue, attributeNode.closingMarker)
        }
      }
      newNode
    } else if (attributeNode.name.equals(Attribute.STYLE_ATTR)) {
      var newNode  = attributeNode
      val removed1 = attributeNodes.remove(Attribute.STYLE_ATTR) // @nowarn
      if (removed1 != null) { // @nowarn
        val attributes = new MutableAttributes()
        attributes.addValue(Attribute.STYLE_ATTR, removed1.value)
        val value = attributes.getValue(Attribute.STYLE_ATTR)
        if (!attributeNode.value.equals(value)) {
          val newValue = PrefixedSubSequence.prefixOf(value + ";", attributeNode.value)
          newNode = new AttributeNode(attributeNode.name, attributeNode.attributeSeparator, attributeNode.openingMarker, newValue, attributeNode.closingMarker)
        }
      }
      newNode
    } else {
      attributeNode
    }

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new AttributesNodeFormatter(options)
  }
}
