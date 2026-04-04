/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Url_Decode.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

import java.net.URLDecoder

class Url_Decode extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    try
      URLDecoder.decode(asString(value, context), "UTF-8")
    catch {
      case _: Exception => value
    }
}
