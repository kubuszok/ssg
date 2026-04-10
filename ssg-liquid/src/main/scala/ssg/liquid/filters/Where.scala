/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Where.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Delegates to JekyllWhereImpl or LiquidWhereImpl based on liquidStyleWhere
 *
 * There are two different implementations of this filter in ruby.
 *
 * One is from shopify/liquid package:
 * https://github.com/Shopify/liquid/blob/master/lib/liquid/standardfilters.rb
 * https://github.com/Shopify/liquid/blob/master/test/integration/standard_filter_test.rb
 *
 * And the other is from jekyll/jekyll package:
 * https://github.com/jekyll/jekyll/blob/master/lib/jekyll/filters.rb
 * https://github.com/jekyll/jekyll/blob/master/test/test_filters.rb
 *
 * The differ between them are in how they work with objects and arrays as second argument.
 * And this is rare usage case, and even more, java is not a ruby so we cannot implement
 * exact behavior for these edge cases.
 */
package ssg
package liquid
package filters

import ssg.liquid.filters.where.{ JekyllWhereImpl, LiquidWhereImpl, PropertyResolverHelper, WhereImpl }

/** Filters an array of objects by a property value.
  *
  * Delegates to JekyllWhereImpl or LiquidWhereImpl based on the `liquidStyleWhere` parser setting.
  */
class Where extends Filter("where") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val delegate: WhereImpl =
      if (context.parser.liquidStyleWhere) {
        checkParams(params, 1, 2)
        new LiquidWhereImpl(context, PropertyResolverHelper.INSTANCE)
      } else {
        checkParams(params, 2)
        new JekyllWhereImpl(context, PropertyResolverHelper.INSTANCE)
      }
    delegate.apply(value, params)
  }
}
