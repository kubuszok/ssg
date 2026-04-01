/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/IParse.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.io.IOException
import java.io.Reader

/** Interface to generic parser for RenderingTestCase customizations
  */
trait IParse {

  /** Parse the specified input text into a tree of nodes.
    *
    * Note that this method is thread-safe (a new parser state is used for each invocation).
    *
    * @param input
    *   the text to parse
    * @return
    *   the root node
    */
  def parse(input: BasedSequence): Node

  /** Parse the specified input text into a tree of nodes.
    *
    * Note that this method is thread-safe (a new parser state is used for each invocation).
    *
    * @param input
    *   the text to parse
    * @return
    *   the root node
    */
  def parse(input: String): Node

  /** Parse the specified reader into a tree of nodes. The caller is responsible for closing the reader.
    *
    * Note that this method is thread-safe (a new parser state is used for each invocation).
    *
    * @param input
    *   the reader to parse
    * @return
    *   the root node
    * @throws IOException
    *   when reading throws an exception
    */
  @throws[IOException]
  def parseReader(input: Reader): Node

  /** Get Options for parsing
    *
    * @return
    *   DataHolder for options
    */
  def options: Nullable[DataHolder]

  /** Transfer reference definition between documents
    *
    * @param document
    *   destination document
    * @param included
    *   source document
    * @param onlyIfUndefined
    *   true if only should transfer references not already defined in the destination document, false to transfer all, null to use repository's KEEP_TYPE to make the determination (if KEEP_FIRST then
    *   only transfer if undefined,
    * @return
    *   true if any references were transferred
    */
  def transferReferences(document: Document, included: Document, onlyIfUndefined: Nullable[Boolean]): Boolean
}
