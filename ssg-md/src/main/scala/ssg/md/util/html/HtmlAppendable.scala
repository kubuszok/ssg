/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/HtmlAppendable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/HtmlAppendable.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package html

import ssg.md.Nullable
import ssg.md.util.sequence.LineAppendable

import scala.collection.mutable

/** Used to help with HTML output generation and formatting of HTML
  */
trait HtmlAppendable extends LineAppendable {
  def getAttributes:                                                Nullable[Attributes]
  def setAttributes(attributes: Attributes):                        HtmlAppendable
  def inPre:                                                        Boolean
  def openPre():                                                    HtmlAppendable
  def closePre():                                                   HtmlAppendable
  def raw(s:                    CharSequence):                      HtmlAppendable
  def raw(s:                    CharSequence, count: Int):          HtmlAppendable
  def rawPre(s:                 CharSequence):                      HtmlAppendable
  def rawIndentedPre(s:         CharSequence):                      HtmlAppendable
  def text(s:                   CharSequence):                      HtmlAppendable
  def attr(attrName:            CharSequence, value: CharSequence): HtmlAppendable
  def attr(attribute:           Attribute*):                        HtmlAppendable
  def attr(attributes:          Attributes):                        HtmlAppendable
  def withAttr():                                                   HtmlAppendable

  // tag tracking
  def getOpenTags:                                   mutable.Stack[String]
  def getOpenTagsAfterLast(latestTag: CharSequence): List[String]

  def withCondLineOnChildText(): HtmlAppendable
  def withCondIndent():          HtmlAppendable

  def tagVoid(tagName: CharSequence):                                                              HtmlAppendable
  def tag(tagName:     CharSequence):                                                              HtmlAppendable
  def tag(tagName:     CharSequence, runnable:    Runnable):                                       HtmlAppendable
  def tag(tagName:     CharSequence, voidElement: Boolean):                                        HtmlAppendable
  def tag(tagName:     CharSequence, withIndent:  Boolean, withLine: Boolean, runnable: Runnable): HtmlAppendable

  def tagVoidLine(tagName:   CharSequence):                        HtmlAppendable
  def tagLine(tagName:       CharSequence):                        HtmlAppendable
  def tagLine(tagName:       CharSequence, voidElement: Boolean):  HtmlAppendable
  def tagLine(tagName:       CharSequence, runnable:    Runnable): HtmlAppendable
  def tagIndent(tagName:     CharSequence, runnable:    Runnable): HtmlAppendable
  def tagLineIndent(tagName: CharSequence, runnable:    Runnable): HtmlAppendable
  def closeTag(tagName:      CharSequence):                        HtmlAppendable
}
