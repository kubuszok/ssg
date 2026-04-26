/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/LinkNodeBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/LinkNodeBase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

abstract class LinkNodeBase extends Node {

  var urlOpeningMarker:   BasedSequence = BasedSequence.NULL
  var url:                BasedSequence = BasedSequence.NULL
  var pageRef:            BasedSequence = BasedSequence.NULL
  var anchorMarker:       BasedSequence = BasedSequence.NULL
  var anchorRef:          BasedSequence = BasedSequence.NULL
  var urlClosingMarker:   BasedSequence = BasedSequence.NULL
  var titleOpeningMarker: BasedSequence = BasedSequence.NULL
  var title:              BasedSequence = BasedSequence.NULL
  var titleClosingMarker: BasedSequence = BasedSequence.NULL

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def setTitleChars(titleChars: BasedSequence): Unit =
    if (titleChars != null && (titleChars ne BasedSequence.NULL)) {
      val titleCharsLength = titleChars.length
      titleOpeningMarker = titleChars.subSequence(0, 1)
      title = titleChars.subSequence(1, titleCharsLength - 1)
      titleClosingMarker = titleChars.subSequence(titleCharsLength - 1, titleCharsLength)
    } else {
      titleOpeningMarker = BasedSequence.NULL
      title = BasedSequence.NULL
      titleClosingMarker = BasedSequence.NULL
    }

  def setUrlChars(urlSeq: BasedSequence): Unit =
    if (urlSeq != null && (urlSeq ne BasedSequence.NULL)) {
      // strip off <> wrapping
      if (urlSeq.startsWith("<") && urlSeq.endsWith(">")) {
        urlOpeningMarker = urlSeq.subSequence(0, 1)
        url = urlSeq.subSequence(1, urlSeq.length - 1)
        urlClosingMarker = urlSeq.subSequence(urlSeq.length - 1)
      } else {
        url = urlSeq
      }

      // parse out the anchor marker and ref
      val pos = url.indexOf('#')
      if (pos < 0) {
        pageRef = url
      } else {
        pageRef = url.subSequence(0, pos)
        anchorMarker = url.subSequence(pos, pos + 1)
        anchorRef = url.subSequence(pos + 1)
      }
    } else {
      urlOpeningMarker = BasedSequence.NULL
      url = BasedSequence.NULL
      urlClosingMarker = BasedSequence.NULL
    }
}
