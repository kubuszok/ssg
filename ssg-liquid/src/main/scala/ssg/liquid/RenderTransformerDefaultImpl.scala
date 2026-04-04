/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/RenderTransformerDefaultImpl.java
 * Original: Copyright (c) Christian Kohlschütter
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Idiom: Anonymous class → Scala inner class
 */
package ssg
package liquid

/** The default RenderTransformer.
  *
  * Objects are converted to CharSequence, and appended to a StringBuilder where necessary. The resulting object is always transformed to String.
  */
object RenderTransformerDefaultImpl extends RenderTransformer {

  override def newObjectAppender(context: TemplateContext, estimatedNumberOfAppends: Int): RenderTransformer.ObjectAppender.Controller =
    new DefaultController(context)

  override def transformObject(context: TemplateContext, obj: Any): Any =
    String.valueOf(obj)

  final private class DefaultController(context: TemplateContext) extends RenderTransformer.ObjectAppender.Controller {
    private var result: CharSequence  = ""
    private var state:  Int           = 0 // 0 = initial, 1 = single, 2 = builder
    private var sb:     StringBuilder = scala.compiletime.uninitialized

    override def getResult: Any = {
      checkLength()
      RenderTransformerDefaultImpl.transformObject(context, result)
    }

    override def append(obj: Any): Unit =
      state match {
        case 0 =>
          // First append: just store the value
          result = obj match {
            case cs: CharSequence => cs
            case other => String.valueOf(other)
          }
          state = 1
        case 1 =>
          // Second append: create StringBuilder
          sb = new StringBuilder()
          sb.append(result)
          sb.append(obj)
          result = sb
          state = 2
          checkLength()
        case _ =>
          // Subsequent appends: use existing StringBuilder
          sb.append(obj)
          checkLength()
      }

    private def checkLength(): Unit = {
      val maxLen = context.parser.limitMaxSizeRenderedString
      if (result.length() > maxLen) {
        throw new RuntimeException("rendered string exceeds " + maxLen)
      }
    }
  }
}
