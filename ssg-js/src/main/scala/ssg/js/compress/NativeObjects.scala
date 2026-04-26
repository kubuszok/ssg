/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Metadata about pure native JavaScript methods, functions, and values.
 *
 * These lookup tables are used by the `unsafe` compressor option, which assumes
 * that native built-in methods exist and have their standard behavior.
 *
 * Original source: terser lib/compress/native-objects.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: make_nested_lookup -> nested Map[String, Set[String]]
 *   Convention: Immutable Map/Set instead of JS predicate functions
 *   Idiom: Shared objectMethods list factored into val
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/compress/native-objects.js
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package compress

/** Static lookup tables for JavaScript built-in objects.
  *
  * Used by the compressor to determine which property accesses, method calls, and static function calls are known to be side-effect free.
  */
object NativeObjects {

  // -- Common methods present on all Object instances --
  private val objectMethods: Set[String] = Set(
    "constructor",
    "toString",
    "valueOf"
  )

  // -----------------------------------------------------------------------
  // Pure property access globals
  // -----------------------------------------------------------------------

  /** Objects which are safe to access without throwing or causing a side effect. Usually we'd check the `unsafe` option first but these are way too common for that.
    */
  val purePropAccessGlobals: Set[String] = Set(
    "Number",
    "String",
    "Array",
    "Object",
    "Function",
    "Promise"
  )

  // -----------------------------------------------------------------------
  // Pure native instance methods (called on a value)
  // -----------------------------------------------------------------------

  /** Map from global type name to set of method names that are pure (no side effects) when called as instance methods.
    */
  val pureNativeMethods: Map[String, Set[String]] = Map(
    "Array" -> (Set(
      "at",
      "flat",
      "includes",
      "indexOf",
      "join",
      "lastIndexOf",
      "slice"
    ) ++ objectMethods),
    "Boolean" -> objectMethods,
    "Function" -> objectMethods,
    "Number" -> (Set(
      "toExponential",
      "toFixed",
      "toPrecision"
    ) ++ objectMethods),
    "Object" -> objectMethods,
    "RegExp" -> (Set(
      "test"
    ) ++ objectMethods),
    "String" -> (Set(
      "at",
      "charAt",
      "charCodeAt",
      "charPointAt",
      "concat",
      "endsWith",
      "fromCharCode",
      "fromCodePoint",
      "includes",
      "indexOf",
      "italics",
      "lastIndexOf",
      "localeCompare",
      "match",
      "matchAll",
      "normalize",
      "padStart",
      "padEnd",
      "repeat",
      "replace",
      "replaceAll",
      "search",
      "slice",
      "split",
      "startsWith",
      "substr",
      "substring",
      "toLocaleLowerCase",
      "toLocaleUpperCase",
      "toLowerCase",
      "toUpperCase",
      "trim",
      "trimEnd",
      "trimStart"
    ) ++ objectMethods)
  )

  // -----------------------------------------------------------------------
  // Pure native static functions (called on the constructor)
  // -----------------------------------------------------------------------

  /** Map from global type name to set of static function names that are pure (no side effects) when called.
    */
  val pureNativeFns: Map[String, Set[String]] = Map(
    "Array" -> Set(
      "isArray"
    ),
    "Math" -> Set(
      "abs",
      "acos",
      "asin",
      "atan",
      "ceil",
      "cos",
      "exp",
      "floor",
      "log",
      "round",
      "sin",
      "sqrt",
      "tan",
      "atan2",
      "pow",
      "max",
      "min"
    ),
    "Number" -> Set(
      "isFinite",
      "isNaN"
    ),
    "Object" -> Set(
      "create",
      "getOwnPropertyDescriptor",
      "getOwnPropertyNames",
      "getPrototypeOf",
      "isExtensible",
      "isFrozen",
      "isSealed",
      "hasOwn",
      "keys"
    ),
    "String" -> Set(
      "fromCharCode"
    )
  )

  // -----------------------------------------------------------------------
  // Pure native values (constant properties on globals)
  // -----------------------------------------------------------------------

  /** Map from global type name to set of property names that are known numeric constants.
    */
  val pureNativeValues: Map[String, Set[String]] = Map(
    "Math" -> Set(
      "E",
      "LN10",
      "LN2",
      "LOG2E",
      "LOG10E",
      "PI",
      "SQRT1_2",
      "SQRT2"
    ),
    "Number" -> Set(
      "MAX_VALUE",
      "MIN_VALUE",
      "NaN",
      "NEGATIVE_INFINITY",
      "POSITIVE_INFINITY"
    )
  )

  // -----------------------------------------------------------------------
  // Lookup helpers
  // -----------------------------------------------------------------------

  /** Check if `globalName.methodName` is a pure native instance method. */
  def isPureNativeMethod(globalName: String, methodName: String): Boolean = {
    val methods = pureNativeMethods.getOrElse(globalName, null) // @nowarn -- Map lookup
    methods != null && methods.contains(methodName)
  }

  /** Check if `globalName.fnName` is a pure native static function. */
  def isPureNativeFn(globalName: String, fnName: String): Boolean = {
    val fns = pureNativeFns.getOrElse(globalName, null) // @nowarn -- Map lookup
    fns != null && fns.contains(fnName)
  }

  /** Check if `globalName.valueName` is a pure native constant value. */
  def isPureNativeValue(globalName: String, valueName: String): Boolean = {
    val values = pureNativeValues.getOrElse(globalName, null) // @nowarn -- Map lookup
    values != null && values.contains(valueName)
  }
}
