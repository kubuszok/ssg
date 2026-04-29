/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/ResolvedLink.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/ResolvedLink.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

import ssg.md.Nullable
import ssg.md.util.html.{ Attribute, Attributes, MutableAttributes }

import scala.language.implicitConversions

class ResolvedLink private (
  val linkType:             LinkType,
  val url:                  String,
  val status:               LinkStatus,
  private var myAttributes: Nullable[MutableAttributes]
) {

  def this(linkType: LinkType, url: CharSequence) =
    this(linkType, String.valueOf(url), LinkStatus.UNKNOWN, Nullable.empty)

  def this(linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes]) = {
    this(linkType, String.valueOf(url), LinkStatus.UNKNOWN, Nullable.empty)
    attributes.foreach { attrs =>
      getMutableAttributes().addValues(attrs)
    }
  }

  def this(linkType: LinkType, url: CharSequence, attributes: Nullable[Attributes], status: LinkStatus) = {
    this(linkType, String.valueOf(url), status, Nullable.empty)
    attributes.foreach { attrs =>
      getMutableAttributes().addValues(attrs)
    }
  }

  def getAttributes: Nullable[Attributes] =
    myAttributes.map(_.toImmutable)

  def getNonNullAttributes: Attributes = {
    if (myAttributes.isEmpty) {
      myAttributes = new MutableAttributes()
    }
    myAttributes.get.toImmutable
  }

  def getMutableAttributes(): MutableAttributes = {
    if (myAttributes.isEmpty) {
      myAttributes = new MutableAttributes()
    }
    myAttributes.get
  }

  def withLinkType(linkType: LinkType): ResolvedLink =
    if (linkType eq this.linkType) this
    else new ResolvedLink(linkType, url, status, myAttributes)

  def withStatus(status: LinkStatus): ResolvedLink =
    if (status eq this.status) this
    else new ResolvedLink(linkType, url, status, myAttributes)

  def withUrl(url: CharSequence): ResolvedLink = {
    val useUrl = String.valueOf(url)
    if (this.url == useUrl) this
    else new ResolvedLink(linkType, useUrl, status, myAttributes)
  }

  def getPageRef: String = {
    // parse out the anchor marker and ref
    val pos = url.indexOf('#')
    if (pos < 0) url
    else url.substring(0, pos)
  }

  def getAnchorRef: Nullable[String] = {
    // parse out the anchor marker and ref
    val pos = url.indexOf('#')
    if (pos < 0) Nullable.empty
    else Nullable(url.substring(pos + 1))
  }

  def withTitle(title: Nullable[CharSequence]): ResolvedLink = {
    val haveTitle: Nullable[String] = myAttributes.flatMap(a => Nullable(a.getValue(Attribute.TITLE_ATTR)))
    val titlesEqual = (title.isEmpty && haveTitle.isEmpty) ||
      (haveTitle.isDefined && title.isDefined && haveTitle.get == title.get.toString)
    if (titlesEqual) this
    else {
      val attributes = myAttributes.fold(new MutableAttributes())(a => new MutableAttributes(a))
      if (title.isEmpty) {
        attributes.remove(Attribute.TITLE_ATTR)
        val newAttrs: Nullable[MutableAttributes] =
          if (attributes.isEmpty) Nullable.empty else Nullable(attributes)
        new ResolvedLink(linkType, url, status, newAttrs)
      } else {
        attributes.replaceValue(Attribute.TITLE_ATTR, title.get)
        new ResolvedLink(linkType, url, status, Nullable(attributes))
      }
    }
  }

  def getTitle: Nullable[String] =
    myAttributes.flatMap(a => Nullable(a.getValue(Attribute.TITLE_ATTR)))

  def withTarget(target: Nullable[CharSequence]): ResolvedLink = {
    val haveTarget: Nullable[String] = myAttributes.flatMap(a => Nullable(a.getValue(Attribute.TARGET_ATTR)))
    val targetsEqual = (target.isEmpty && haveTarget.isEmpty) ||
      (haveTarget.isDefined && target.isDefined && haveTarget.get == target.get.toString)
    if (targetsEqual) this
    else {
      val attributes = myAttributes.fold(new MutableAttributes())(a => new MutableAttributes(a))
      if (target.isEmpty) {
        attributes.remove(Attribute.TARGET_ATTR)
        val newAttrs: Nullable[MutableAttributes] =
          if (attributes.isEmpty) Nullable.empty else Nullable(attributes)
        new ResolvedLink(linkType, url, status, newAttrs)
      } else {
        attributes.replaceValue(Attribute.TARGET_ATTR, target.get)
        new ResolvedLink(linkType, url, status, Nullable(attributes))
      }
    }
  }

  def getTarget: Nullable[String] =
    myAttributes.flatMap(a => Nullable(a.getValue(Attribute.TARGET_ATTR)))

  override def equals(o: Any): Boolean = o match {
    case that: ResolvedLink =>
      (this eq that) ||
      (linkType == that.linkType && url == that.url && status == that.status)
    case _ => false
  }

  override def hashCode(): Int = {
    var result = linkType.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + status.hashCode()
    result
  }
}
