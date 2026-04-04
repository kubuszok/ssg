/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Url_Encode.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 */
package ssg
package liquid
package filters

import java.net.URLEncoder

class Url_Encode extends Filter {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
    try
      URLEncoder.encode(asString(value, context), "UTF-8")
    catch {
      case _: Exception => value
    }
}
