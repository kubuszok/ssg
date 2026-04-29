/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Symbol definition — represents a variable/function definition in scope analysis.
 *
 * Original source: terser lib/scope.js (SymbolDef class, lines 127-194)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: SymbolDef (unchanged), snake_case -> camelCase
 *   Convention: Mutable var fields, Any for polymorphic fields (fixed, singleUse)
 *   Idiom: Companion object holds next_id counter, boundary/break for early return
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/scope.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 88493d7ca0d708389f5f78f541c4fb48e71d9fe2
 */
package ssg
package js
package scope

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*

/** Represents a variable/function definition in scope analysis.
  *
  * Each SymbolDef tracks a single named binding: its original declarations, references, and metadata used by the mangler and compressor.
  */
class SymbolDef(
  scopeArg:  AstScope,
  origSym:   AstSymbol,
  initValue: AstNode | Null = null
) {

  /** The name of this symbol (as declared in source). */
  var name: String = origSym.name

  /** Original declaration symbols (there may be multiple for `var` re-declarations). */
  var orig: ArrayBuffer[AstSymbol] = ArrayBuffer(origSym)

  /** Initializer node, if any. */
  var init: AstNode | Null = initValue

  /** The scope in which this symbol is defined. */
  var scope: AstScope = scopeArg

  /** All references (AstSymbolRef) pointing to this definition. */
  var references: ArrayBuffer[AstSymbolRef] = ArrayBuffer.empty

  /** Whether this is a global (undeclared at top level). */
  var global: Boolean = false

  /** Export bitmask: 0 = not exported, MASK_EXPORT_DONT_MANGLE = exported (don't mangle), MASK_EXPORT_WANT_MANGLE = default export (may mangle).
    */
  var exportFlag: Int = 0

  /** The mangled (shortened) name, or null if unmangled. */
  var mangledName: String | Null = null

  /** Whether this symbol is undeclared (referenced but never defined). */
  var undeclared: Boolean = false

  /** Unique identifier for this definition. */
  var id: Int = {
    val nextId = SymbolDef.nextId
    SymbolDef.nextId += 1
    nextId
  }

  /** Whether this def is chained to another via catch scope. */
  var chained: Boolean = false

  /** Whether this symbol is accessed directly (e.g., via `eval`). */
  var directAccess: Boolean = false

  /** Nesting depth at which the value escapes scope. */
  var escaped: Int = 0

  /** Count of recursive references. */
  var recursiveRefs: Int = 0

  /** Number of assignments to this symbol. */
  var assignments: Int = 0

  /** Number of times this symbol's value was replaced by the compressor. */
  var replaced: Int = 0

  /** Whether this symbol is used exactly once (can be Boolean or "m" for mangled). */
  var singleUse: Any = false

  /** The fixed value of this symbol: Boolean (false = not fixed), AstNode, or a () => AstNode thunk. */
  var fixed: Any = false

  /** Number of times this definition was eliminated. */
  var eliminated: Int = 0

  /** Replacement value for the compressor, or null if none. */
  var shouldReplace: Any | Null = null

  /** Get the fixed value: if `fixed` is a thunk function, call it; otherwise return it directly.
    */
  def fixedValue: AstNode | Null | Boolean =
    fixed match {
      case false => false
      case n: AstNode      => n
      case f: Function0[?] => f().asInstanceOf[AstNode] // @nowarn — thunk interop, type erased at runtime
      case _ => false
    }

  /** Check if this symbol cannot be mangled under the given options. */
  def unmangleable(options: ManglerOptions): Boolean =
    boundary[Boolean] {
      // Function defs with keep_fnames
      if (
        ScopeAnalysis.functionDefs != null &&
        ScopeAnalysis.functionDefs.nn.contains(id) &&
        keepName(options.keepFnames, orig(0).name)
      ) {
        break(true)
      }

      (global && !options.toplevel) ||
      (exportFlag & ScopeAnalysis.MaskExportDontMangle) != 0 ||
      undeclared ||
      (!options.eval && scope.pinned) ||
      ((orig(0).isInstanceOf[AstSymbolLambda] || orig(0).isInstanceOf[AstSymbolDefun]) &&
        keepName(options.keepFnames, orig(0).name)) ||
      orig(0).isInstanceOf[AstSymbolMethod] ||
      ((orig(0).isInstanceOf[AstSymbolClass] || orig(0).isInstanceOf[AstSymbolDefClass]) &&
        keepName(options.keepClassnames, orig(0).name))
    }

  /** Mangle this symbol's name using the given options. */
  def mangle(options: ManglerOptions): Unit = {
    val cache = options.cache
    if (global && cache != null && cache.nn.props.contains(name)) {
      mangledName = cache.nn.props(name)
    } else if (mangledName == null && !unmangleable(options)) {
      var s: AstScope = scope
      val sym = orig(0)
      if (options.ie8 && sym.isInstanceOf[AstSymbolLambda]) {
        s.parentScope match {
          case ps: AstScope => s = ps
          case null =>
        }
      }
      val redefinition = SymbolDef.redefinedCatchDef(this)
      mangledName =
        if (redefinition != null) {
          val rd = redefinition.nn
          if (rd.mangledName != null) rd.mangledName else rd.name
        } else {
          Mangler.nextMangled(s, options, this)
        }
      if (global && cache != null) {
        cache.nn.props(name) = mangledName.nn
      }
    }
  }

  /** Check if a name should be kept based on a keep pattern (Boolean, Regex, or null). */
  private def keepName(keep: Any, nameToCheck: String): Boolean =
    keep match {
      case true         => true
      case false | null => false
      case r: scala.util.matching.Regex => r.findFirstIn(nameToCheck).isDefined
      case _ => false
    }
}

object SymbolDef {

  /** Global counter for unique SymbolDef IDs. */
  var nextId: Int = 1

  /** Reset the ID counter (useful for tests). */
  def resetIds(): Unit =
    nextId = 1

  /** Look up a redefined catch variable definition in the containing function scope.
    *
    * When a `catch` variable is defined in a block scope, it may shadow a variable from the enclosing function scope. This function finds that outer definition for proper mangling.
    */
  def redefinedCatchDef(d: SymbolDef): SymbolDef | Null =
    if (d.orig(0).isInstanceOf[AstSymbolCatch] && d.scope.isBlockScope) {
      d.scope.getDefunScope.variables.get(d.name) match {
        case Some(v) => v.asInstanceOf[SymbolDef]
        case None    => null
      }
    } else {
      null
    }
}
