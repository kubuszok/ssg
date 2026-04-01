/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/IParseBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util
package spec

import ssg.md.Nullable
import ssg.md.util.ast.{Document, IParse, Node}
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import java.io.{BufferedReader, IOException, Reader}
import scala.language.implicitConversions

abstract class IParseBase(val options: Nullable[DataHolder]) extends IParse {

  def this() = this(Nullable.empty)

  override def parse(input: String): Node = {
    parse(BasedSequence.of(input))
  }

  override def transferReferences(document: Document, included: Document, onlyIfUndefined: Nullable[Boolean]): Boolean = {
    false
  }

  @throws[IOException]
  override def parseReader(input: Reader): Node = {
    val bufferedReader: BufferedReader = input match {
      case br: BufferedReader => br
      case _ => new BufferedReader(input)
    }

    val file = new StringBuilder()
    val buffer = new Array[Char](16384)

    var charsRead = bufferedReader.read(buffer)
    while (charsRead >= 0) {
      file.appendAll(buffer, 0, charsRead)
      charsRead = bufferedReader.read(buffer)
    }

    val source = BasedSequence.of(file.toString())
    parse(source)
  }
}
