/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferences.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferences.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package enumerated
package reference

import ssg.md.Nullable
import ssg.md.util.data.DataHolder

import scala.collection.mutable
import scala.language.implicitConversions

class EnumeratedReferences(options: DataHolder) {

  private val referenceRepository: EnumeratedReferenceRepository = EnumeratedReferenceExtension.ENUMERATED_REFERENCES.get(options)
  private val enumerationCounters         = mutable.HashMap.empty[String, Int]
  private val enumeratedReferenceOrdinals = mutable.HashMap.empty[String, Array[Int]]

  def add(text: String): Unit = {
    val typeStr = EnumeratedReferenceRepository.getType(text)

    val types    = typeStr.split(":")
    val ordinals = new Array[Int](types.length)

    // replace all types but the last with ordinal of that type
    val nestedType = new StringBuilder()

    val iMax = types.length
    var i    = 0
    while (i < iMax) {
      val typeText = types(i)
      nestedType.append(typeText)

      val nestedTypeKey = nestedType.toString

      if (i < iMax - 1) {
        val typeOrdinal = enumerationCounters.getOrElse(nestedTypeKey, 0)
        nestedType.append(':').append(typeOrdinal).append(':')
        ordinals(i) = typeOrdinal
      } else {
        // last type gets defined if it does not exist
        val ordinal = if (!enumerationCounters.contains(nestedTypeKey)) {
          enumerationCounters.put(nestedTypeKey, 1)
          1
        } else {
          val o = enumerationCounters(nestedTypeKey) + 1
          enumerationCounters.put(nestedTypeKey, o)
          o
        }
        ordinals(i) = ordinal
      }
      i += 1
    }

    // save the ordinal for this reference and type
    enumeratedReferenceOrdinals.put(text, ordinals)
  }

  def getEnumeratedReferenceOrdinals(text: String): Array[EnumeratedReferenceRendering] = {
    val typeStr = EnumeratedReferenceRepository.getType(text)

    val types      = typeStr.split(":")
    val renderings = new Array[EnumeratedReferenceRendering](types.length)

    val ordinals = enumeratedReferenceOrdinals.getOrElse(text, EnumeratedReferences.EMPTY_ORDINALS)

    val iMax = types.length
    var i    = 0
    while (i < iMax) {
      val typeText        = types(i)
      val referenceFormat = referenceRepository.get(typeText)
      val ordinal         = if (i < ordinals.length) ordinals(i) else 0
      renderings(i) = EnumeratedReferenceRendering(referenceFormat, typeText, ordinal)
      i += 1
    }

    renderings
  }

  def renderReferenceOrdinals(text: String, renderer: EnumeratedOrdinalRenderer): Unit = {
    val renderings = getEnumeratedReferenceOrdinals(text)
    EnumeratedReferences.renderReferenceOrdinals(renderings, renderer)
  }
}

object EnumeratedReferences {
  val EMPTY_TYPE:     String     = ""
  val EMPTY_ORDINALS: Array[Int] = Array.empty[Int]

  def renderReferenceOrdinals(renderings: Array[EnumeratedReferenceRendering], renderer: EnumeratedOrdinalRenderer): Unit = {
    renderer.startRendering(renderings)

    // need to accumulate all compound formats and output on final format's [#]
    val compoundReferences = new java.util.ArrayList[CompoundEnumeratedReferenceRendering]()

    val lastRendering = renderings(renderings.length - 1)

    for (rendering <- renderings) {
      val ordinal     = rendering.referenceOrdinal
      val defaultText = rendering.referenceType

      var needSeparator = false

      if (rendering ne lastRendering) {
        if (rendering.referenceFormat != null) { // @nowarn - referenceFormat may be null from repository.get
          var lastChild = rendering.referenceFormat.lastChild
          while (lastChild.isDefined && !lastChild.get.isInstanceOf[EnumeratedReferenceBase])
            lastChild = lastChild.get.lastChild
          needSeparator = lastChild.isDefined && lastChild.get.isInstanceOf[EnumeratedReferenceBase] && lastChild.get.asInstanceOf[EnumeratedReferenceBase].text.isEmpty
        } else {
          needSeparator = true
        }
      }

      compoundReferences.add(CompoundEnumeratedReferenceRendering(ordinal, rendering.referenceFormat, defaultText, needSeparator))
    }

    val iMax        = compoundReferences.size() - 1
    val wasRunnable = renderer.getEnumOrdinalRunnable

    renderer.setEnumOrdinalRunnable(
      Nullable(
        new Runnable {
          override def run(): Unit = {
            var i = 0
            while (i < iMax) {
              val rendering    = compoundReferences.get(i)
              val wasRunnable1 = renderer.getEnumOrdinalRunnable
              renderer.setEnumOrdinalRunnable(Nullable.empty)
              renderer.render(rendering.ordinal, rendering.referenceFormat, rendering.defaultText, rendering.needSeparator)
              renderer.setEnumOrdinalRunnable(wasRunnable1)
              i += 1
            }
          }
        }
      )
    )

    val rendering = compoundReferences.get(iMax)
    renderer.render(rendering.ordinal, rendering.referenceFormat, rendering.defaultText, rendering.needSeparator)
    renderer.setEnumOrdinalRunnable(wasRunnable)

    renderer.endRendering()
  }
}
