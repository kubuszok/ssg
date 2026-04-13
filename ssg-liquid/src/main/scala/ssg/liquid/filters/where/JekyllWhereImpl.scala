/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/JekyllWhereImpl.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.where → ssg.liquid.filters.where
 *   Convention: Faithful port of Jekyll where semantics
 *   Idiom: comparePropertyVsTarget handles nil/empty/blank/string/array
 *   Idiom: parseSortInput coerces numeric-looking strings to Double
 *
 * Based on:
 * https://github.com/jekyll/jekyll/blob/master/lib/jekyll/filters.rb
 * https://github.com/jekyll/jekyll/blob/master/test/test_filters.rb
 */
package ssg
package liquid
package filters
package where

import ssg.liquid.nodes.AtomNode

import java.util.{ ArrayList, Collection => JCollection, Map => JMap }

import scala.util.boundary
import scala.util.boundary.break

/** Jekyll-style where filter implementation.
  *
  * Filter an array of objects — property is required, value is required. Handles nil/empty/blank targets, numeric coercion, and collection-property matching.
  */
class JekyllWhereImpl(
  templateContext: TemplateContext,
  helper:          PropertyResolverHelper
) extends WhereImpl(templateContext, helper) {

  // compare_property_vs_target(property, target)
  /*
    # `where` filter helper
    #
    def compare_property_vs_target(property, target)
      case target
      when NilClass
        return true if property.nil?
      when Liquid::Expression::MethodLiteral # `empty` or `blank`
        target = target.to_s
        return true if property == target || Array(property).join == target
      else
        target = target.to_s
        if property.is_a? String
          return true if property == target
        else
          Array(property).each do |prop|
            return true if prop.to_s == target
          end
        end
      end

      false
    end
   */

  // item_property(item, property)
  /*
    def item_property(item, property)
      @item_property_cache ||= {}
      @item_property_cache[property] ||= {}
      @item_property_cache[property][item] ||= begin
        if item.respond_to?(:to_liquid)
          property.to_s.split(".").reduce(item.to_liquid) do |subvalue, attribute|
            parse_sort_input(subvalue[attribute])
          end
        elsif item.respond_to?(:data)
          parse_sort_input(item.data[property.to_s])
        else
          parse_sort_input(item[property.to_s])
        end
      end
    end
   */

  override def apply(input: Any, params: Array[Any]): Any =
    if (params.length < 1) {
      input
    } else {
      val property = params(0)
      if (isFalsy(property, context)) {
        input
      } else {
        var value: Any = null
        if (params.length > 1) {
          value = params(1)
        }
        if (value != null) {
          if (value.getClass.isArray) {
            input
          } else if (value.isInstanceOf[JMap[?, ?]]) {
            input
          } else {
            filterInput(input, property, value)
          }
        } else {
          filterInput(input, property, value)
        }
      }
    }

  private def filterInput(input: Any, property: Any, value: Any): Any =
    if (input == null) {
      ""
    } else {
      var effective: Any = input
      if (!effective.isInstanceOf[JCollection[?]] && !effective.isInstanceOf[JMap[?, ?]] && !isArray(effective)) {
        effective
      } else {
        if (effective.isInstanceOf[JMap[?, ?]]) {
          effective = effective.asInstanceOf[JMap[?, ?]].values()
        }

        if (effective.getClass.isArray) {
          effective = arrayToArrayList(effective.asInstanceOf[Array[Any]])
        }

        val inputColl = effective.asInstanceOf[JCollection[?]]
        val res       = new ArrayList[Any]()
        val iter      = inputColl.iterator()
        while (iter.hasNext) {
          val item         = iter.next()
          val itemProperty = getItemProperty(item, property)
          if (comparePropertyVsTarget(itemProperty, value)) {
            res.add(item)
          }
        }
        res.toArray
      }
    }

  private def comparePropertyVsTarget(itemProperty: Any, target: Any): Boolean =
    if (target == null) {
      itemProperty == null
    } else if (AtomNode.isEmpty(target) || AtomNode.isBlank(target)) {
      "".equals(itemProperty) || "".equals(joinedArray(itemProperty))
    } else {
      val strTarget = asString(target, context)
      if (isString(itemProperty)) {
        strTarget.equals(itemProperty)
      } else {
        boundary {
          val objects = asArray(itemProperty, context)
          var i       = 0
          while (i < objects.length) {
            if (asString(objects(i), context).equals(strTarget)) break(true)
            i += 1
          }
          false
        }
      }
    }

  private def joinedArray(itemProperty: Any): String = {
    // version of Array(property).join
    val objects =
      if (itemProperty.isInstanceOf[JMap[?, ?]]) mapAsArray(itemProperty.asInstanceOf[JMap[?, ?]])
      else asArray(itemProperty, context)
    asString(objects, context)
  }

  private def getItemProperty(e: Any, property: Any): Any = {
    val adapter = resolverHelper.findFor(e)
    if (adapter != null) {
      parseSortInput(adapter.getItemProperty(context, e, property))
    } else {
      parseSortInput(e.asInstanceOf[JMap[?, ?]].get(property))
    }
  }

  // return numeric values as numbers for proper sorting
  private def parseSortInput(property: Any): Any =
    property match {
      case s: String =>
        try
          java.lang.Double.parseDouble(s): Any
        catch {
          case _: Exception => property
        }
      case _ => property
    }

  private def arrayToArrayList(array: Array[Any]): ArrayList[Any] = {
    val list = new ArrayList[Any]()
    var i    = 0
    while (i < array.length) {
      list.add(array(i))
      i += 1
    }
    list
  }
}
