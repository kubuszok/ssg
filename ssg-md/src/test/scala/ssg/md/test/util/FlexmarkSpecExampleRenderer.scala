/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/FlexmarkSpecExampleRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.ast.{ Document, IParse, IRender, Node }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

class FlexmarkSpecExampleRenderer(
  example:              SpecExample,
  exampleOptions:       Nullable[DataHolder],
  private var myParser: IParse,
  private var myRender: IRender,
  includeExampleCoord:  Boolean
) extends SpecExampleRendererBase(example, exampleOptions, includeExampleCoord) {

  def this(example: SpecExample, options: Nullable[DataHolder], parser: IParse, render: IRender) =
    this(example, options, parser, render, true)

  private var myIncludedDocument: Nullable[Node] = Nullable.empty
  private var myDocument:         Nullable[Node] = Nullable.empty

  override def includeDocument(includedText: String): Unit = {
    // flexmark parser specific
    myIncludedDocument = Nullable.empty

    if (includedText.nonEmpty) {
      // need to parse and transfer references
      myIncludedDocument = Nullable(parser.parse(includedText))
      adjustParserForInclusion()
    }
  }

  protected def includedDocument: Node = {
    assert(myIncludedDocument.isDefined)
    myIncludedDocument.get
  }

  override def parse(input: CharSequence): Unit =
    myDocument = Nullable(parser.parse(BasedSequence.of(input)))

  override def finalizeDocument(): Unit = {
    assert(myDocument.isDefined)

    if (myIncludedDocument.isDefined) {
      adjustParserForInclusion()
    }
  }

  protected def adjustParserForInclusion(): Unit =
    (myDocument.get, myIncludedDocument.get) match {
      case (doc: Document, inc: Document) =>
        parser.transferReferences(doc, inc, Nullable.empty)
      case _ => // not both Documents, skip
    }

  def document: Node = {
    assert(myDocument.isDefined)
    myDocument.get
  }

  /** Override to customize
    *
    * @return
    *   HTML string, will be cached after document is finalized to allow for timing collection iterations,
    */
  override protected def renderHtml(): String = {
    assert(myDocument.isDefined)
    renderer.render(myDocument.get)
  }

  /** Override to customize
    *
    * @return
    *   AST string, will be cached after document is finalized to allow for timing collection iterations,
    */
  override protected def renderAst(): String = {
    assert(myDocument.isDefined)
    TestUtils.ast(myDocument.get)
  }

  override def finalizeRender(): Unit =
    super.finalizeRender()

  def parser: IParse = myParser

  def parser_=(parser: IParse): Unit =
    myParser = parser

  def renderer: IRender = myRender

  def renderer_=(render: IRender): Unit =
    myRender = render
}
