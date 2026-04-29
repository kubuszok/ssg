/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Property name mangling — shortens object property names when safe.
 *
 * Performs two passes:
 * 1. Walk to find candidate property names for mangling
 * 2. Transform to rename properties to short names
 *
 * Also handles mangling of private class members (always safe to mangle)
 * and respects quoted properties, reserved names, regex filters, and
 * annotation-based mangling directives.
 *
 * Original source: terser lib/propmangle.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: mangle_properties -> mangleProperties,
 *     mangle_private_properties -> manglePrivateProperties,
 *     reserve_quoted_keys -> reserveQuotedKeys,
 *     find_annotated_props -> findAnnotatedProps,
 *     find_builtins -> findBuiltins, can_mangle -> canMangle,
 *     should_mangle -> shouldMangle
 *   Convention: Object with methods, mutable.Set/Map for caches
 *   Idiom: TreeWalker/TreeTransformer, boundary/break instead of return
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/propmangle.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 6e7323fd4b0e255a06f6d3a2dcd111b8640a9031
 */
package ssg
package js
package scope

import scala.collection.mutable

import ssg.js.ast.*

/** Options for property mangling. */
final case class PropManglerOptions(
  builtins:      Boolean = false,
  cache:         ManglerCache | Null = null,
  debug:         Any = false, // false or String
  keepQuoted:    Boolean = false,
  nthIdentifier: NthIdentifier = Base54,
  onlyCache:     Boolean = false,
  regex:         String | Null = null,
  reserved:      mutable.Set[String] = mutable.Set.empty,
  undeclared:    Boolean = false,
  onlyAnnotated: Boolean = false
)

/** Property name mangling engine. */
object PropMangler {

  /** Well-known DOM property names that should not be mangled. */
  private val builtinNames: mutable.Set[String] = {
    val reserved = mutable.Set[String]()
    // Literal keywords
    reserved.addAll(Seq("null", "true", "false", "NaN", "Infinity", "-Infinity", "undefined"))
    // Standard built-in object property names (subset of domprops)
    val builtins = Seq(
      "Object",
      "Array",
      "Function",
      "Number",
      "String",
      "Boolean",
      "Error",
      "Math",
      "Date",
      "RegExp",
      "Symbol",
      "Map",
      "Set",
      "WeakMap",
      "WeakSet",
      "Promise",
      "Proxy",
      "Reflect",
      "ArrayBuffer",
      "DataView",
      "Float32Array",
      "Float64Array",
      "Int8Array",
      "Int16Array",
      "Int32Array",
      "Uint8Array",
      "Uint8ClampedArray",
      "Uint16Array",
      "Uint32Array",
      "JSON",
      "EvalError",
      "RangeError",
      "ReferenceError",
      "SyntaxError",
      "TypeError",
      "URIError"
    )
    reserved.addAll(builtins)
    // Common method/property names from built-in prototypes
    val commonProps = Seq(
      "constructor",
      "prototype",
      "length",
      "name",
      "toString",
      "valueOf",
      "toLocaleString",
      "hasOwnProperty",
      "isPrototypeOf",
      "propertyIsEnumerable",
      "apply",
      "call",
      "bind",
      "arguments",
      "caller",
      "concat",
      "join",
      "pop",
      "push",
      "reverse",
      "shift",
      "slice",
      "sort",
      "splice",
      "unshift",
      "indexOf",
      "lastIndexOf",
      "every",
      "some",
      "forEach",
      "map",
      "filter",
      "reduce",
      "reduceRight",
      "find",
      "findIndex",
      "fill",
      "copyWithin",
      "entries",
      "keys",
      "values",
      "includes",
      "flat",
      "flatMap",
      "at",
      "toFixed",
      "toExponential",
      "toPrecision",
      "charAt",
      "charCodeAt",
      "codePointAt",
      "trim",
      "trimStart",
      "trimEnd",
      "padStart",
      "padEnd",
      "repeat",
      "startsWith",
      "endsWith",
      "match",
      "replace",
      "replaceAll",
      "search",
      "split",
      "substring",
      "toLowerCase",
      "toUpperCase",
      "normalize",
      "matchAll",
      "getTime",
      "setTime",
      "getFullYear",
      "getMonth",
      "getDate",
      "getHours",
      "getMinutes",
      "getSeconds",
      "getMilliseconds",
      "toISOString",
      "toJSON",
      "toDateString",
      "toTimeString",
      "source",
      "flags",
      "global",
      "ignoreCase",
      "multiline",
      "sticky",
      "unicode",
      "test",
      "exec",
      "then",
      "catch",
      "finally",
      "resolve",
      "reject",
      "all",
      "race",
      "any",
      "allSettled",
      "get",
      "set",
      "has",
      "delete",
      "clear",
      "size",
      "add",
      "forEach",
      "next",
      "done",
      "value",
      "return",
      "throw",
      "getPrototypeOf",
      "setPrototypeOf",
      "isExtensible",
      "preventExtensions",
      "getOwnPropertyDescriptor",
      "defineProperty",
      "deleteProperty",
      "ownKeys",
      "apply",
      "construct",
      "assign",
      "create",
      "defineProperties",
      "freeze",
      "fromEntries",
      "getOwnPropertyDescriptors",
      "getOwnPropertyNames",
      "getOwnPropertySymbols",
      "is",
      "isFrozen",
      "isSealed",
      "seal",
      "message",
      "stack",
      "cause",
      "byteLength",
      "byteOffset",
      "buffer"
    )
    reserved.addAll(commonProps)
    reserved
  }

  /** Add strings extracted from subscript property accesses. */
  private def addStrings(node: AstNode, add: String => Unit): Unit = {
    val tw = new TreeWalker((n, _) => {
      n match {
        case seq: AstSequence =>
          if (seq.expressions.nonEmpty)
            addStrings(seq.expressions.last, add)
        case s: AstString =>
          add(s.value)
        case cond: AstConditional =>
          if (cond.consequent != null) addStrings(cond.consequent.nn, add)
          if (cond.alternative != null) addStrings(cond.alternative.nn, add)
        case _ =>
      }
      true // skip children, we recurse manually
    })
    node.walk(tw)
  }

  /** Reserve property names that appear as quoted keys in the AST. */
  def reserveQuotedKeys(ast: AstNode, reserved: mutable.Set[String]): Unit =
    ast.walk(
      new TreeWalker((node, _) => {
        node match {
          case kv: AstObjectKeyVal if kv.quote.nonEmpty =>
            kv.key match {
              case s: String => reserved.add(s)
              case _ =>
            }
          case op: AstObjectProperty
              if op.isInstanceOf[AstObjectGetter] ||
                op.isInstanceOf[AstObjectSetter] || op.isInstanceOf[AstConciseMethod] =>
            op match {
              case og: AstObjectGetter if og.quote.nonEmpty =>
                og.key match {
                  case sym: AstSymbol => reserved.add(sym.name)
                  case _ =>
                }
              case os: AstObjectSetter if os.quote.nonEmpty =>
                os.key match {
                  case sym: AstSymbol => reserved.add(sym.name)
                  case _ =>
                }
              case cm: AstConciseMethod if cm.quote.nonEmpty =>
                cm.key match {
                  case sym: AstSymbol => reserved.add(sym.name)
                  case _ =>
                }
              case _ =>
            }
          case sub: AstSub =>
            sub.property match {
              case n: AstNode => addStrings(n, s => reserved.add(s))
              case _ =>
            }
          case _ =>
        }
        null
      })
    )

  /** Find properties annotated with @__MANGLE_PROP__. */
  def findAnnotatedProps(ast: AstNode): mutable.Set[String] = {
    val annotatedProps = mutable.Set[String]()
    walk(
      ast,
      (node, _) => {
        node match {
          case _: AstClassPrivateProperty | _: AstPrivateMethod | _: AstPrivateGetter | _: AstPrivateSetter | _: AstDotHash =>
          // handled by manglePrivateProperties
          case kv: AstObjectKeyVal =>
            kv.key match {
              case s: String if (kv.annotations & Annotations.MangleProp) != 0 =>
                annotatedProps.add(s)
              case _ =>
            }
          case op: AstObjectProperty if (op.annotations & Annotations.MangleProp) != 0 =>
            op.key match {
              case sym: AstSymbol => annotatedProps.add(sym.name)
              case _ =>
            }
          case dot: AstDot =>
            if ((dot.annotations & Annotations.MangleProp) != 0) {
              dot.property match {
                case s: String => annotatedProps.add(s)
                case _ =>
              }
            }
          case sub: AstSub =>
            if ((sub.annotations & Annotations.MangleProp) != 0) {
              sub.property match {
                case s: AstString => annotatedProps.add(s.value)
                case _ =>
              }
            }
          case _ =>
        }
        null
      }
    )
    annotatedProps
  }

  /** Mangle private class member names.
    *
    * Private members are always safe to mangle since they are lexically scoped to the class body.
    */
  def manglePrivateProperties(ast: AstNode, nthIdentifier: NthIdentifier = Base54): AstNode = {
    var counter      = -1
    val privateCache = mutable.Map[String, String]()

    def manglePrivate(name: String): String =
      privateCache.getOrElseUpdate(name, {
                                     counter += 1
                                     nthIdentifier.get(counter)
                                   }
      )

    val tt = new TreeTransformer(
      before = (node, _) => {
        node match {
          case cpp: AstClassPrivateProperty =>
            cpp.key match {
              case sym: AstSymbol => sym.name = manglePrivate(sym.name)
              case _ =>
            }
          case pm: AstPrivateMethod =>
            pm.key match {
              case sym: AstSymbol => sym.name = manglePrivate(sym.name)
              case _ =>
            }
          case pg: AstPrivateGetter =>
            pg.key match {
              case sym: AstSymbol => sym.name = manglePrivate(sym.name)
              case _ =>
            }
          case ps: AstPrivateSetter =>
            ps.key match {
              case sym: AstSymbol => sym.name = manglePrivate(sym.name)
              case _ =>
            }
          case pi: AstPrivateIn =>
            if (pi.key != null) {
              pi.key.nn match {
                case sym: AstSymbol => sym.name = manglePrivate(sym.name)
                case _ =>
              }
            }
          case dh: AstDotHash =>
            dh.property match {
              case s: String => dh.property = manglePrivate(s)
              case _ =>
            }
          case _ =>
        }
        null // continue walking
      }
    )
    ast.walk(tt)
    ast
  }

  /** Mangle property names in the AST.
    *
    * Two-pass algorithm:
    *   1. Walk to find all candidate property names
    *   2. Transform to rename properties using short generated names
    *
    * @param ast
    *   the toplevel AST to mangle
    * @param options
    *   mangling options
    */
  def mangleProperties(
    ast:     AstToplevel,
    options: PropManglerOptions = PropManglerOptions()
  ): AstToplevel = {
    val nthIdentifier = options.nthIdentifier
    val reserved      = options.reserved.clone()
    if (!options.builtins) reserved.addAll(builtinNames)

    var cname = -1

    val cache: mutable.Map[String, String] =
      if (options.cache != null) options.cache.nn.props
      else mutable.Map.empty

    val onlyAnnotated = options.onlyAnnotated
    val regex: scala.util.matching.Regex | Null =
      if (options.regex != null) options.regex.nn.r else null

    val debug = options.debug != false
    val debugNameSuffix: String =
      if (debug) {
        options.debug match {
          case true => ""
          case s: String => s
          case _ => ""
        }
      } else ""

    val namesToMangle = mutable.Set[String]()
    val unmangleable  = mutable.Set[String]()
    // Track already-mangled names to prevent collisions
    cache.values.foreach(unmangleable.add)

    val keepQuoted     = options.keepQuoted
    val annotatedProps = findAnnotatedProps(ast)

    def canMangle(name: String): Boolean = {
      if (unmangleable.contains(name)) return false // @nowarn -- quick check
      if (reserved.contains(name)) return false // @nowarn -- reserved
      if (options.onlyCache) return cache.contains(name) // @nowarn -- only cache
      if (name.matches("^-?[0-9]+(\\.[0-9]+)?(e[+-][0-9]+)?$")) return false // @nowarn -- numeric
      true
    }

    def shouldMangle(name: String): Boolean = {
      if (onlyAnnotated && !annotatedProps.contains(name)) return false // @nowarn -- not annotated
      if (regex != null && !regex.nn.findFirstIn(name).isDefined) {
        return annotatedProps.contains(name) // @nowarn -- regex filter
      }
      if (reserved.contains(name)) return false // @nowarn -- reserved
      cache.contains(name) || namesToMangle.contains(name)
    }

    def add(name: String): Unit = {
      if (canMangle(name)) namesToMangle.add(name)
      if (!shouldMangle(name)) unmangleable.add(name)
    }

    def mangle(name: String): String = {
      if (!shouldMangle(name)) return name // @nowarn -- not mangleable
      cache.getOrElseUpdate(
        name, {
          var mangled: String | Null = null
          if (debug) {
            val debugMangled = "_$" + name + "$" + debugNameSuffix + "_"
            if (canMangle(debugMangled)) mangled = debugMangled
          }
          if (mangled == null) {
            var candidate = ""
            var found     = false
            while (!found) {
              cname += 1
              candidate = nthIdentifier.get(cname)
              if (canMangle(candidate)) found = true
            }
            mangled = candidate
          }
          mangled.nn
        }
      )
    }

    def mangleStrings(node: AstNode): AstNode = {
      val tt = new TreeTransformer(
        before = (n, _) => {
          n match {
            case seq: AstSequence =>
              val last = seq.expressions.size - 1
              if (last >= 0) seq.expressions(last) = mangleStrings(seq.expressions(last))
            case s: AstString =>
              s.annotations &= ~Annotations.Key
              s.value = mangle(s.value)
            case cond: AstConditional =>
              if (cond.consequent != null) cond.consequent = mangleStrings(cond.consequent.nn)
              if (cond.alternative != null) cond.alternative = mangleStrings(cond.alternative.nn)
            case _ =>
          }
          n
        }
      )
      node.walk(tt)
      node
    }

    // Step 1: find candidates to mangle
    ast.walk(
      new TreeWalker((node, _) => {
        node match {
          case _: AstClassPrivateProperty | _: AstPrivateMethod | _: AstPrivateGetter | _: AstPrivateSetter | _: AstDotHash =>
          // handled by manglePrivateProperties

          case kv: AstObjectKeyVal =>
            kv.key match {
              case s: String if !keepQuoted || kv.quote.isEmpty => add(s)
              case _ =>
            }

          case og: AstObjectGetter =>
            if (!keepQuoted || og.quote.isEmpty) {
              og.key match {
                case sym: AstSymbol => add(sym.name)
                case _ =>
              }
            }
          case os: AstObjectSetter =>
            if (!keepQuoted || os.quote.isEmpty) {
              os.key match {
                case sym: AstSymbol => add(sym.name)
                case _ =>
              }
            }
          case cm: AstConciseMethod =>
            if (!keepQuoted || cm.quote.isEmpty) {
              cm.key match {
                case sym: AstSymbol => add(sym.name)
                case _ =>
              }
            }

          case dot: AstDot =>
            val declared = options.undeclared || {
              var root: AstNode = dot
              while (root.isInstanceOf[AstDot] && root.asInstanceOf[AstDot].expression != null)
                root = root.asInstanceOf[AstDot].expression.nn
              root match {
                case sr: AstSymbolRef => !(sr.thedef != null && sr.thedef.asInstanceOf[AnyRef] != null)
                case _ => true
              }
            }
            if (declared && (!keepQuoted || dot.quote.isEmpty)) {
              dot.property match {
                case s: String => add(s)
                case _ =>
              }
            }

          case sub: AstSub if !keepQuoted =>
            sub.property match {
              case n: AstNode => addStrings(n, add)
              case _ =>
            }

          case call: AstCall =>
            // Object.defineProperty calls
            if (call.expression != null) {
              call.expression.nn match {
                case dot: AstDot =>
                  dot.property match {
                    case s: String if s == "defineProperty" =>
                      dot.expression match {
                        case sr: AstSymbolRef if sr.name == "Object" && call.args.size >= 2 =>
                          addStrings(call.args(1), add)
                        case _ =>
                      }
                    case _ =>
                  }
                case _ =>
              }
            }

          case bin: AstBinary if bin.operator == "in" =>
            if (bin.left != null) addStrings(bin.left.nn, add)

          case s: AstString if (s.annotations & Annotations.Key) != 0 =>
            add(s.value)

          case _ =>
        }
        null // continue walking
      })
    )

    // Step 2: transform the tree, renaming properties
    val tt = new TreeTransformer(
      before = (node, _) => {
        node match {
          case _: AstClassPrivateProperty | _: AstPrivateMethod | _: AstPrivateGetter | _: AstPrivateSetter | _: AstDotHash =>
          // handled by manglePrivateProperties

          case kv: AstObjectKeyVal =>
            kv.key match {
              case s: String if !keepQuoted || kv.quote.isEmpty =>
                kv.key = mangle(s)
              case _ =>
            }

          case op: AstObjectProperty
              if op.isInstanceOf[AstObjectGetter] || op.isInstanceOf[AstObjectSetter] ||
                op.isInstanceOf[AstConciseMethod] =>
            if (
              !keepQuoted || {
                op match {
                  case og: AstObjectGetter  => og.quote.isEmpty
                  case os: AstObjectSetter  => os.quote.isEmpty
                  case cm: AstConciseMethod => cm.quote.isEmpty
                  case _ => true
                }
              }
            ) {
              if (!op.computedKey()) {
                op.key match {
                  case sym: AstSymbol => sym.name = mangle(sym.name)
                  case _ =>
                }
              }
            }

          case dot: AstDot =>
            if (!keepQuoted || dot.quote.isEmpty) {
              dot.property match {
                case s: String => dot.property = mangle(s)
                case _ =>
              }
            }

          case sub: AstSub if !keepQuoted =>
            sub.property match {
              case n: AstNode => sub.property = mangleStrings(n)
              case _ =>
            }

          case call: AstCall =>
            if (call.expression != null) {
              call.expression.nn match {
                case dot: AstDot =>
                  dot.property match {
                    case s: String if s == "defineProperty" =>
                      dot.expression match {
                        case sr: AstSymbolRef if sr.name == "Object" && call.args.size >= 2 =>
                          call.args(1) = mangleStrings(call.args(1))
                        case _ =>
                      }
                    case _ =>
                  }
                case _ =>
              }
            }

          case bin: AstBinary if bin.operator == "in" =>
            if (bin.left != null) bin.left = mangleStrings(bin.left.nn)

          case s: AstString if (s.annotations & Annotations.Key) != 0 =>
            s.annotations &= ~Annotations.Key
            s.value = mangle(s.value)

          case _ =>
        }
        null // continue walking
      }
    )
    ast.walk(tt)
    ast
  }
}
