/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/RenderTransformer.java
 * Original: Copyright (c) Christian Kohlschütter
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: Java interfaces → Scala traits
 *   Idiom: FunctionalInterface → SAM trait
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/RenderTransformer.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid

/** Handles the conversion of objects during template rendering.
  *
  * Implementations may optimize how objects are appended/serialized, and when exceptions are thrown if the final result would be too long.
  *
  * The default implementation simply appends strings to a StringBuilder.
  *
  * @see
  *   RenderTransformerDefaultImpl
  */
trait RenderTransformer {

  /** Creates a new ObjectAppender.Controller for the given TemplateContext. */
  def newObjectAppender(context: TemplateContext, estimatedNumberOfAppends: Int): RenderTransformer.ObjectAppender.Controller

  /** Transforms an object to a representation suitable for calling toString() on during the render phase. */
  def transformObject(context: TemplateContext, obj: Any): Any
}

object RenderTransformer {

  /** Something that can append objects. */
  trait ObjectAppender {

    /** Appends the given object. */
    def append(obj: Any): Unit
  }

  object ObjectAppender {

    /** An ObjectAppender that also provides the accumulated result. */
    trait Controller extends ObjectAppender {

      /** The accumulated result of all append() calls. */
      def getResult: Any
    }
  }
}
