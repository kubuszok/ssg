/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesNodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributesNodePostProcessor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.ast.{ AnchorRefTarget, FencedCodeBlock, Paragraph, ParagraphItemContainer, Text, TextBase }
import ssg.md.parser.{ LightInlineParser, LightInlineParserImpl }
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ BlankLine, DoNotAttributeDecorate, Document, Node, NodeTracker }
import ssg.md.util.misc.CharPredicate

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class AttributesNodePostProcessor(document: Document) extends NodePostProcessor {

  private val nodeAttributeRepository: NodeAttributeRepository                   = AttributesExtension.NODE_ATTRIBUTES.get(document)
  private val myOptions:               AttributesOptions                         = new AttributesOptions(document)
  private var myLightInlineParser:     Nullable[LightInlineParser]               = Nullable.empty
  private var myParserExtension:       Nullable[AttributesInlineParserExtension] = Nullable.empty

  def getAttributeOwner(state: NodeTracker, attributesNode: AttributesNode): Nullable[Node] = {
    val previous:       Nullable[Node] = attributesNode.previousAnyNot(classOf[BlankLine], classOf[DoNotAttributeDecorate])
    var attributeOwner: Nullable[Node] = Nullable.empty
    val parent:         Nullable[Node] = attributesNode.parent

    if (previous.isEmpty) {
      // attributes are first
      // if parent is a paragraph
      //      if paragraph parent is a paragraph item container
      //          if paragraph has no previous sibling then
      //              1. attributes go to the paragraph parent's parent
      //          else
      //              if paragraph only contains attributes then
      //                  2. attributes go to paragraph's previous sibling,
      //              else
      //                  3. attributes go to the paragraph
      //      else
      //          if paragraph only contains attributes then
      //              if paragraph has no previous sibling then
      //                  4. attributes go to paragraph's parent
      //              else
      //                  5. attributes go to paragraph's previous sibling,
      //          else
      //              6. attributes go to the paragraph
      // else
      //      7. attributes go to the parent
      if (parent.exists(_.isInstanceOf[Paragraph])) {
        val p = parent.get
        if (p.parent.exists(_.isInstanceOf[ParagraphItemContainer])) {
          val parentPreviousNotBlankLine = p.previousAnyNot(classOf[BlankLine])
          if (parentPreviousNotBlankLine.isEmpty) {
            //              1. attributes go to the paragraph parent's parent
            attributeOwner = p.grandParent
          } else {
            if (attributesNode.nextAnyNot(classOf[AttributesNode], classOf[BlankLine]).isEmpty) {
              //                  2. attributes go to paragraph's previous sibling,
              attributeOwner = parentPreviousNotBlankLine
            } else {
              //                  3. attributes go to the paragraph
              attributeOwner = parent
            }
          }
        } else {
          if (attributesNode.nextAnyNot(classOf[AttributesNode], classOf[BlankLine]).isEmpty) {
            val parentPreviousNotBlankLine = p.previousAnyNot(classOf[BlankLine])
            if (parentPreviousNotBlankLine.isEmpty) {
              //                  4. attributes go to paragraph's parent
              attributeOwner = p.parent
            } else {
              //                  5. attributes go to paragraph's previous sibling,
              attributeOwner = parentPreviousNotBlankLine
            }
          } else {
            //              6. attributes go to the paragraph
            attributeOwner = parent
          }
        }
      } else {
        //      7. attributes go to the parent
        attributeOwner = parent
      }
    } else {
      val prev = previous.get
      if ((!myOptions.assignTextAttributes && (prev.isInstanceOf[Text] || prev.isInstanceOf[TextBase])) || prev.endOffset < attributesNode.startOffset) {
        // either previous is text and no text attributes or not attached to the previous node
        // then attributes go to parent unless overridden by delimited attribute spans

        var effectivePrevious: Node = prev
        if (myOptions.useEmptyImplicitAsSpanDelimiter) {
          // find first previous not delimited by unmatched attribute
          effectivePrevious = AttributesNodePostProcessor.matchDelimitedSpans(state, attributesNode, prev)
        }

        if (effectivePrevious.isInstanceOf[TextBase]) {
          // use delimited span
          attributeOwner = Nullable(effectivePrevious)
        } else if (parent.exists(_.isInstanceOf[Paragraph]) && parent.get.parent.exists(_.isInstanceOf[ParagraphItemContainer])) {
          attributeOwner = parent.get.parent
        } else {
          attributeOwner = parent
        }
      } else {
        // attached, attributes go to previous node, but may need to wrap spans containing DoNotAttributeDecorate in TextBase
        var effectivePrevious: Node = prev
        if (myOptions.wrapNonAttributeText) {
          // find first previous not delimited by attribute
          var first:                 Nullable[Node] = attributesNode.previous
          var lastNonAttributesNode: Nullable[Node] = Nullable.empty
          var hadDoNotDecorate = false

          while (first.isDefined && (first.get.isInstanceOf[Text] || first.get.isInstanceOf[DoNotAttributeDecorate])) {
            if (first.get.isInstanceOf[DoNotAttributeDecorate]) {
              hadDoNotDecorate = true
            }
            lastNonAttributesNode = first
            first = first.get.previous
          }

          if (hadDoNotDecorate && lastNonAttributesNode.isDefined) {
            // need to wrap in text base from first to attribute node
            val textBase = new TextBase()
            AttributesNodePostProcessor.textBaseWrap(state, lastNonAttributesNode.get, attributesNode, textBase)
            effectivePrevious = textBase
          }
        }

        if (myOptions.useEmptyImplicitAsSpanDelimiter) {
          // find first previous not delimited by unmatched attribute
          effectivePrevious = AttributesNodePostProcessor.matchDelimitedSpans(state, attributesNode, effectivePrevious)
        }

        if (effectivePrevious.isInstanceOf[Text]) {
          // insert text base where text was
          val textBase = new TextBase(effectivePrevious.chars)
          effectivePrevious.insertBefore(textBase)
          effectivePrevious.unlink()
          state.nodeRemoved(effectivePrevious)

          textBase.appendChild(effectivePrevious)
          state.nodeAddedWithChildren(textBase)
          attributeOwner = Nullable(textBase)
        } else if (effectivePrevious.isInstanceOf[AttributesDelimiter]) {
          // no owner, attributes go into aether
          attributeOwner = Nullable.empty
        } else {
          if (effectivePrevious.isInstanceOf[AttributesNode]) {
            // we are spliced right up against previous attributes, give our attributes to the owner of previous attributes
            attributeOwner = getAttributeOwner(state, effectivePrevious.asInstanceOf[AttributesNode])
          } else {
            attributeOwner = Nullable(effectivePrevious)
          }
        }
      }
    }
    attributeOwner
  }

  override def process(state: NodeTracker, node: Node): Unit = {
    node match {
      case attributesNode: AttributesNode =>
        // apply to sibling unless the sibling is a Text node then apply it to the parent
        var previous: Nullable[Node] = attributesNode.previous
        var next:     Nullable[Node] = attributesNode.next

        // need to trim end of previous sibling if we are last
        // and to trim start of next sibling if we are first
        if (previous.isEmpty) {
          // we are first, trim start of next sibling
          if (next.isDefined && !next.get.isInstanceOf[AttributesNode]) {
            if (next.get.chars.isBlank()) {
              // remove this node it is all blank
              val tmp = next.get
              next = tmp.next
              tmp.unlink()
              state.nodeRemoved(tmp)
            } else {
              next.get.chars = next.get.chars.trimStart()
            }
          }
        }

        if (next.isEmpty) {
          // we are last, trim end of prev sibling
          if (previous.isDefined && !previous.get.isInstanceOf[AttributesNode]) {
            if (previous.get.chars.isBlank()) {
              // remove this node it is all blank
              val tmp = previous.get
              previous = tmp.previous
              tmp.unlink()
              state.nodeRemoved(tmp)
            } else {
              previous.get.chars = previous.get.chars.trimEnd()
            }
          }
        }

        val attributeOwner = getAttributeOwner(state, attributesNode)
        if (attributeOwner.isDefined) {
          nodeAttributeRepository.put(attributeOwner.get, attributesNode)

          // set the heading id for this node so the correct id will be used
          if (attributeOwner.get.isInstanceOf[AnchorRefTarget]) {
            boundary {
              for (attributeNode <- attributesNode.reversedChildren.iterator().asScala)
                attributeNode match {
                  case an: AttributeNode if an.isId =>
                    attributeOwner.get.asInstanceOf[AnchorRefTarget].anchorRefId = an.value.toString
                    attributeOwner.get.asInstanceOf[AnchorRefTarget].explicitAnchorRefId_=(true)
                    break()
                  case _ => // continue
                }
            }
          }
        }

      case _ => // not an AttributesNode
    }

    node match {
      case fencedCodeBlock: FencedCodeBlock if myOptions.fencedCodeInfoAttributes =>
        // see if has { after the first word
        val info     = fencedCodeBlock.info
        val language = fencedCodeBlock.infoDelimitedByAny(CharPredicate.SPACE_TAB)
        val infoTail = info.subSequence(language.length()).trimStart()

        var pos = infoTail.indexOf('{')
        if (pos >= 0) {
          // have possible attributes
          if (myLightInlineParser.isEmpty) {
            myLightInlineParser = Nullable(new LightInlineParserImpl(node.document))
            myParserExtension = Nullable(new AttributesInlineParserExtension(myLightInlineParser.get))
          }

          val lip = myLightInlineParser.get
          val pe  = myParserExtension.get

          lip.input = infoTail
          lip.index = pos
          val dummyBlock = new AttributesNode()
          lip.block = dummyBlock // dummy to hold possibly parsed attributes

          boundary {
            while (true) {
              val startIndex = lip.index
              val parsed     = pe.parse(lip)

              lip.spnl()
              val index = lip.index + (if (lip.index == startIndex) 1 else 0)
              pos = infoTail.indexOf('{', index)
              if (pos == -1) break()

              if (!parsed || !infoTail.subSequence(index, pos).isBlank()) {
                // ignore any parsed attributes since they are not consecutive
                dummyBlock.removeChildren()
              }

              lip.index = pos
            }
          }

          if (dummyBlock.hasChildren) {
            // attributes added to block, move them as first child of fencedCode
            val firstAttributes = dummyBlock.firstChild.get
            val lastAttributes  = dummyBlock.lastChild.get

            // truncate info to exclude attributes
            fencedCodeBlock.info = fencedCodeBlock.baseSubSequence(info.startOffset, firstAttributes.startOffset)
            fencedCodeBlock.attributes = fencedCodeBlock.baseSubSequence(firstAttributes.startOffset, lastAttributes.endOffset)

            for (attributesNode <- dummyBlock.children.iterator().asScala)
              if (lip.index >= lip.input.length()) {
                if (fencedCodeBlock.hasChildren) {
                  fencedCodeBlock.lastChild.get.insertBefore(attributesNode)
                } else {
                  fencedCodeBlock.appendChild(attributesNode)
                }

                // set attributes owner
                nodeAttributeRepository.put(fencedCodeBlock, attributesNode.asInstanceOf[AttributesNode])
              }
          }
        }

      case _ => // not a FencedCodeBlock or fencedCodeInfoAttributes is false
    }
  }
}

object AttributesNodePostProcessor {

  def matchDelimitedSpans(state: NodeTracker, attributesNode: AttributesNode, previous: Node): Node = {
    var first:                 Nullable[Node] = attributesNode.previous
    var lastNonAttributesNode: Nullable[Node] = Nullable.empty
    val unmatchedAttributes = ArrayBuffer[Node]()
    var result: Node = previous

    boundary {
      while (first.isDefined) {
        first.get match {
          case _: AttributesDelimiter =>
            if (unmatchedAttributes.nonEmpty) {
              // match it and wrap in text
              val lastNode = unmatchedAttributes.remove(unmatchedAttributes.size - 1)
              lastNonAttributesNode = first.get.next
              if (lastNonAttributesNode.isDefined && (lastNode ne lastNonAttributesNode.get)) {
                val textBase = new TextBase()
                textBaseWrap(state, lastNonAttributesNode.get, lastNode, textBase)
                lastNonAttributesNode = Nullable(textBase)
              } else {
                result = first.get
              }
            } else {
              // unmatched delimiter is our start span
              val textBase = new TextBase()
              lastNonAttributesNode = first.get.next

              if (lastNonAttributesNode.isDefined && (lastNonAttributesNode.get ne attributesNode)) {
                textBaseWrap(state, lastNonAttributesNode.get, attributesNode, textBase)
                result = textBase
              } else {
                result = first.get
              }
              break()
            }

          case _: AttributesNode =>
            unmatchedAttributes.addOne(first.get)

          case _ =>
            lastNonAttributesNode = first
        }

        first = first.get.previous
      }
    }

    if (unmatchedAttributes.nonEmpty) {
      // use the first unmatched as our end of attribute span
      result = unmatchedAttributes(0)
      val previousNext = result.next
      if (previousNext.isDefined && (previousNext.get ne attributesNode)) {
        result = previousNext.get
      }
    }

    result
  }

  def textBaseWrap(state: NodeTracker, lastNonAttributesNode: Node, lastNode: Node, textBase: TextBase): Unit = {
    var current = lastNonAttributesNode
    while (current ne lastNode) {
      val nextNode = current.next.get
      current.unlink()
      state.nodeRemoved(current)
      textBase.appendChild(current)
      current = nextNode
    }
    textBase.setCharsFromContent()
    lastNode.insertBefore(textBase)
    state.nodeAddedWithDescendants(textBase)
  }

  class Factory extends NodePostProcessorFactory(false) {
    addNodes(classOf[AttributesNode], classOf[FencedCodeBlock])

    override def apply(document: Document): NodePostProcessor = new AttributesNodePostProcessor(document)
  }
}
