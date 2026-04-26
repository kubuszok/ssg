/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/where/LiquidWhereImpl.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters.where → ssg.liquid.filters.where
 *   Convention: Faithful port of Liquid (Shopify) where semantics
 *   Idiom: Original uses Jackson ObjectMapper for JSON equality;
 *     port uses Objects.equals + string fallback (no Jackson dependency)
 *
 * Filter the elements of an array to those with a certain property value.
 * By default the target is any truthy value.
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/where/LiquidWhereImpl.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters
package where

import java.lang.reflect.{ Array => JArray }
import java.util.{ ArrayList, Collection => JCollection, List => JList, Map => JMap, Objects }

/** Liquid (Shopify) style where filter implementation.
  *
  * Differs from Jekyll: supports 1-param (truthy check) and 2-param (equality), flattens arrays, wraps maps in arrays.
  */
class LiquidWhereImpl(
  templateContext: TemplateContext,
  helper:          PropertyResolverHelper
) extends WhereImpl(templateContext, helper) {

  /*
    # Filter the elements of an array to those with a certain property value.
    # By default the target is any truthy value.
    def where(input, property, target_value = nil)
      ary = InputIterator.new(input)

      if ary.empty?
        []
      elsif ary.first.respond_to?(:[]) && target_value.nil?
        begin
          ary.select { |item| item[property] }
        rescue TypeError
          raise_property_error(property)
        end
      elsif ary.first.respond_to?(:[])
        begin
          ary.select { |item| item[property] == target_value }
        rescue TypeError
          raise_property_error(property)
        end
      end
    end
   */

  override def apply(input: Any, params: Array[Any]): Any = {
    val objects = toArray(input)
    if (objects.length == 0) {
      objects
    } else {
      val res = new ArrayList[Any]()
      var i   = 0
      while (i < objects.length) {
        if (objectHasPropertyValue(objects(i), params)) {
          res.add(objects(i))
        }
        i += 1
      }
      res.toArray
    }
  }

  private def objectHasPropertyValue(el: Any, params: Array[Any]): Boolean = {
    val rawProperty = params(0)
    val property    = asString(rawProperty, context)
    val resolver    = resolverHelper.findFor(el)
    val node: Any =
      if (resolver != null) {
        resolver.getItemProperty(context, el, rawProperty)
      } else {
        el match {
          case map: JMap[?, ?] =>
            if (!map.containsKey(property)) {
              // property not found — cannot match
              // original uses ObjectMapper.convertValue(el, Map.class) which would
              // also return null for missing keys
              null
            } else {
              map.get(property)
            }
          case _ =>
            // try evaluate as Inspectable
            val evaluated = context.parser.evaluate(el)
            val liquidMap = evaluated.toLiquid()
            if (!liquidMap.containsKey(property)) null
            else liquidMap.get(property)
        }
      }

    if (params.length == 1) {
      asBoolean(node)
    } else {
      // params.length == 2
      val value = params(1)
      // Compare values — original uses Jackson JsonNode equality;
      // we use Objects.equals with string fallback
      Objects.equals(node, value) || Objects.equals(
        if (node != null) node.toString else null,
        if (value != null) value.toString else null
      )
    }
  }

  private def toArray(in: Any): Array[Any] =
    if (in == null) {
      Array.empty[Any]
    } else if (in.getClass.isArray) {
      flatten(in).toArray
    } else if (in.isInstanceOf[JMap[?, ?]]) {
      // map can be also a collection, but we treat it as hash
      Array[Any](in)
    } else if (in.isInstanceOf[JCollection[?]]) {
      flatten(in).toArray
    } else {
      Array[Any](in)
    }

  private def flatten(obj: Any): JList[Any] = {
    val l = new ArrayList[Any]()
    if (obj == null) {
      l
    } else if (obj.getClass.isArray) {
      var i = 0
      while (i < JArray.getLength(obj)) {
        l.addAll(flatten(JArray.get(obj, i)))
        i += 1
      }
      l
    } else if (obj.isInstanceOf[JCollection[?]]) {
      val iter = obj.asInstanceOf[JCollection[?]].iterator()
      while (iter.hasNext)
        l.addAll(flatten(iter.next()))
      l
    } else {
      l.add(obj)
      l
    }
  }
}
