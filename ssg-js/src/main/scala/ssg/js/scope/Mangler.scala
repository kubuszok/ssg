/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Name mangling — renames all variables to shortest possible names for
 * minification, using a base-54/64 encoding scheme.
 *
 * Original source: terser lib/scope.js (mangle_names, base54, lines 696-1063)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: mangle_names -> mangleNames, base54 -> Base54, snake_case -> camelCase
 *   Convention: object methods, boundary/break for labeled continues
 *   Idiom: ArrayBuffer for to_mangle list, mutable.Set for mangled_names
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/scope.js (mangle_names, base54, lines 696-1063)
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package scope

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.parse.Token

/** Options for the name mangler.
  *
  * `keepFnames` and `keepClassnames` can be `Boolean`, `scala.util.matching.Regex`, or `null`.
  */
final case class ManglerOptions(
  eval:           Boolean = false,
  keepFnames:     Any = false,
  keepClassnames: Any = false,
  ie8:            Boolean = false,
  safari10:       Boolean = false,
  module:         Boolean = false,
  reserved:       mutable.Set[String] = mutable.Set.empty,
  toplevel:       Boolean = false,
  cache:          ManglerCache | Null = null,
  nthIdentifier:  NthIdentifier = Base54
)

/** Cache for mangled names across multiple files. */
class ManglerCache {
  var props: mutable.Map[String, String] = mutable.Map.empty
}

/** Interface for generating the Nth identifier name. */
trait NthIdentifier {
  def get(n:        Int):                String
  def reset():                           Unit = {}
  def consider(str: String, delta: Int): Unit = {}
  def sort():                            Unit = {}
}

/** The standard base-54/64 identifier generator.
  *
  * First character is chosen from 54 valid identifier-start characters (a-z, A-Z, $, _). Subsequent characters are chosen from 64 characters (adding 0-9). Characters are sorted by frequency for
  * better compression.
  */
object Base54 extends NthIdentifier {

  private val leading: Array[Char] =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ$_".toCharArray

  private val digits: Array[Char] = "0123456789".toCharArray

  private var chars:     Array[Char]            = Array.empty
  private var frequency: mutable.Map[Char, Int] = mutable.Map.empty

  // Initialize to a usable state
  reset()
  sort()

  override def reset(): Unit = {
    frequency = mutable.Map.empty
    leading.foreach(ch => frequency(ch) = 0)
    digits.foreach(ch => frequency(ch) = 0)
  }

  override def consider(str: String, delta: Int): Unit = {
    var i = str.length
    while ({ i -= 1; i >= 0 }) {
      val ch = str.charAt(i)
      frequency.get(ch) match {
        case Some(f) => frequency(ch) = f + delta
        case None    =>
      }
    }
  }

  private def compare(a: Char, b: Char): Int = {
    val fa = frequency.getOrElse(b, 0)
    val fb = frequency.getOrElse(a, 0)
    fa - fb
  }

  override def sort(): Unit = {
    val sortedLeading = leading.sortWith((a, b) => compare(a, b) < 0)
    val sortedDigits  = digits.sortWith((a, b) => compare(a, b) < 0)
    chars = sortedLeading ++ sortedDigits
  }

  override def get(num: Int): String = {
    val sb   = new StringBuilder
    var n    = num + 1
    var base = 54
    while (n > 0) {
      n -= 1
      sb.append(chars(n % base))
      n = n / base
      base = 64
    }
    sb.toString()
  }
}

/** Name mangling engine. */
object Mangler {

  /** Format and normalize mangler options. */
  def formatOptions(options: ManglerOptions): ManglerOptions = {
    var opts = options
    if (opts.module) {
      opts = opts.copy(toplevel = true)
    }
    // Never mangle "arguments"
    opts.reserved.add("arguments")
    opts
  }

  /** Generate the next available mangled name for a scope. */
  def nextMangled(scope: AstScope, options: ManglerOptions, symbolDef: SymbolDef | Null = null): String = {
    var actualScope = scope

    // If there are block-level function declarations, mangle at the defun scope level
    val blockDefuns = ScopeAnalysis.scopesWithBlockDefuns
    if (blockDefuns != null) {
      val defunScope = scope.getDefunScope
      if (blockDefuns.nn.contains(defunScope)) {
        actualScope = defunScope
      }
    }

    val ext           = actualScope.enclosed
    val nthIdentifier = options.nthIdentifier

    boundary[String] {
      while (true) {
        actualScope.cname += 1
        val m = nthIdentifier.get(actualScope.cname)

        // Skip reserved words
        if (Token.ALL_RESERVED_WORDS.contains(m)) {} // continue
        // Skip names reserved from mangling
        else if (options.reserved.contains(m)) {} // continue
        // Skip unmangleable short names (to avoid collisions when keep_fnames is true)
        else if (ScopeAnalysis.unmangledNames != null && ScopeAnalysis.unmangledNames.nn.contains(m)) {} // continue
        else {
          // Check that the mangled name does not shadow a name from a parent scope
          // that is referenced in this or inner scopes
          var collision = false
          var i         = ext.size
          while ({ i -= 1; i >= 0 } && !collision) {
            val d    = ext(i).asInstanceOf[SymbolDef]
            val name = if (d.mangledName != null) d.mangledName else if (d.unmangleable(options)) d.name else null
            if (name != null && m == name.nn) {
              collision = true
            }
          }
          if (!collision) {
            break(m)
          }
        }
      }
      throw new AssertionError("unreachable") // satisfy compiler
    }
  }

  /** Generate the next mangled name for a toplevel scope (also checks mangled_names set). */
  def nextMangledToplevel(toplevel: AstToplevel, options: ManglerOptions, mangledNames: mutable.Set[String]): String = {
    var name = nextMangled(toplevel, options)
    while (mangledNames.contains(name))
      name = nextMangled(toplevel, options)
    name
  }

  /** Generate the next mangled name for a function expression (avoids shadowing the function's own name). */
  def nextMangledFunction(
    func:    AstFunction,
    options: ManglerOptions,
    d:       SymbolDef
  ): String = {
    // In Safari strict mode, a function expression's argument cannot shadow the function expression's name
    val trickyDef: SymbolDef | Null =
      if (d.orig(0).isInstanceOf[AstSymbolFunarg] && func.name != null) {
        func.name.nn match {
          case sym: AstSymbol => sym.thedef.asInstanceOf[SymbolDef]
          case _ => null
        }
      } else {
        null
      }

    val trickyName: String | Null = trickyDef match {
      case td: SymbolDef => if (td.mangledName != null) td.mangledName else td.name
      case null => null
    }

    boundary[String] {
      while (true) {
        val name = nextMangled(func, options)
        if (trickyName == null || trickyName != name) {
          break(name)
        }
      }
      throw new AssertionError("unreachable")
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

  /** Mangle all names in the given AST.
    *
    * @param ast
    *   the top-level AST node
    * @param options
    *   mangler options
    */
  def mangleNames(ast: AstToplevel, options: ManglerOptions = ManglerOptions()): Unit = {
    val opts          = formatOptions(options)
    val nthIdentifier = opts.nthIdentifier

    // Label mangling counter
    var lname = -1

    // Collect symbols to mangle
    val toMangle = ArrayBuffer.empty[SymbolDef]

    if (opts.keepFnames != null && opts.keepFnames != false) {
      ScopeAnalysis.functionDefs = mutable.Set.empty
    }

    val mangledNames: mutable.Set[String] = mutable.Set.empty
    ScopeAnalysis.unmangledNames = mutable.Set.empty

    val cache = opts.cache
    if (cache != null) {
      ast.globals.foreach { case (_, g) =>
        collect(g.asInstanceOf[SymbolDef], toMangle, opts)
      }
      cache.nn.props.foreach { case (_, mangledName) =>
        mangledNames.add(mangledName)
      }
    }

    var tw: TreeWalker = null.asInstanceOf[TreeWalker] // @nowarn — set immediately below
    tw = new TreeWalker((node: AstNode, descend: () => Unit) =>
      boundary[Any] {
        node match {
          case _: AstLabeledStatement =>
            // lname is incremented when we get to the AstLabel
            val saveNesting = lname
            descend()
            lname = saveNesting
            break(true) // don't descend again in TreeWalker
          case _ =>
        }

        // Track block-level function declarations
        node match {
          case _: AstDefun if !tw.parent().isInstanceOf[AstScope] =>
            if (ScopeAnalysis.scopesWithBlockDefuns == null) {
              ScopeAnalysis.scopesWithBlockDefuns = mutable.Set.empty
            }
            node.asInstanceOf[AstScope].parentScope match {
              case ps: AstScope =>
                ScopeAnalysis.scopesWithBlockDefuns.nn.add(ps.getDefunScope)
              case null =>
            }
          case _ =>
        }

        node match {
          case s: AstScope =>
            s.variables.foreach { case (_, v) =>
              collect(v.asInstanceOf[SymbolDef], toMangle, opts)
            }
            break(()) // continue (don't descend, already handled)
          case _ =>
        }

        if (ScopeAnalysis.isBlockScope(node)) {
          node match {
            case b: AstBlock =>
              if (b.blockScope != null) {
                b.blockScope.nn.variables.foreach { case (_, v) =>
                  collect(v.asInstanceOf[SymbolDef], toMangle, opts)
                }
              }
            case it: AstIterationStatement =>
              if (it.blockScope != null) {
                it.blockScope.nn.variables.foreach { case (_, v) =>
                  collect(v.asInstanceOf[SymbolDef], toMangle, opts)
                }
              }
            case _ =>
          }
          break(()) // continue
        }

        // Track anonymous function expressions assigned to variables with kept names
        val funcDefs = ScopeAnalysis.functionDefs
        node match {
          case vd: AstVarDef if funcDefs != null =>
            vd.value match {
              case lambda: AstLambda if lambda.name == null =>
                vd.name match {
                  case sym: AstSymbol if keepName(opts.keepFnames, sym.name) =>
                    sym.thedef match {
                      case d: SymbolDef => funcDefs.nn.add(d.id)
                      case _ =>
                    }
                  case _ =>
                }
              case _ =>
            }
            break(()) // continue
          case _ =>
        }

        node match {
          case lbl: AstLabel =>
            lname += 1
            var lblName = nthIdentifier.get(lname)
            while (Token.ALL_RESERVED_WORDS.contains(lblName)) {
              lname += 1
              lblName = nthIdentifier.get(lname)
            }
            lbl.mangledName = lblName
            break(true) // don't descend
          case _ =>
        }

        if (!opts.ie8 && !opts.safari10) {
          node match {
            case sym: AstSymbolCatch =>
              sym.thedef match {
                case d: SymbolDef => toMangle.addOne(d)
                case _ =>
              }
              break(()) // continue
            case _ =>
          }
        }

        () // continue walking children
      }
    )

    ast.walk(tw)

    // Collect unmangleable short names to avoid collisions
    if (
      opts.keepFnames != null && opts.keepFnames != false ||
      opts.keepClassnames != null && opts.keepClassnames != false
    ) {
      toMangle.foreach { d =>
        if (d.name.length < 6 && d.unmangleable(opts)) {
          ScopeAnalysis.unmangledNames match {
            case names: mutable.Set[String] => names.add(d.name)
            case null =>
          }
        }
      }
    }

    // Perform the actual mangling
    toMangle.foreach { d =>
      d.mangle(opts)
    }

    // Clean up module-level state
    ScopeAnalysis.functionDefs = null
    ScopeAnalysis.unmangledNames = null
    ScopeAnalysis.scopesWithBlockDefuns = null
  }

  /** Collect a symbol definition for mangling (or record it as unmangleable). */
  private def collect(
    symbol:   SymbolDef,
    toMangle: ArrayBuffer[SymbolDef],
    options:  ManglerOptions
  ): Unit =
    if ((symbol.exportFlag & ScopeAnalysis.MaskExportDontMangle) != 0) {
      ScopeAnalysis.unmangledNames match {
        case names: mutable.Set[String] => names.add(symbol.name)
        case null =>
      }
    } else if (!options.reserved.contains(symbol.name)) {
      toMangle.addOne(symbol)
    }

  /** Find all names that would collide with mangled names. */
  def findCollidingNames(ast: AstToplevel, options: ManglerOptions): mutable.Set[String] = {
    val cache = options.cache
    val avoid = mutable.Set.empty[String]
    options.reserved.foreach(avoid.add)
    ast.globals.foreach { case (_, g) =>
      addDef(g.asInstanceOf[SymbolDef], avoid, options, cache)
    }
    ast.walk(
      new TreeWalker((node: AstNode, _: () => Unit) => {
        node match {
          case s: AstScope =>
            s.variables.foreach { case (_, v) =>
              addDef(v.asInstanceOf[SymbolDef], avoid, options, cache)
            }
          case sym: AstSymbolCatch =>
            sym.thedef match {
              case d: SymbolDef => addDef(d, avoid, options, cache)
              case _ =>
            }
          case _ =>
        }
        ()
      })
    )
    avoid
  }

  private def addDef(
    d:       SymbolDef,
    avoid:   mutable.Set[String],
    options: ManglerOptions,
    cache:   ManglerCache | Null
  ): Unit = {
    var name = d.name
    if (d.global && cache != null) {
      cache.nn.props.get(name) match {
        case Some(cached) => name = cached
        case None         => if (!d.unmangleable(options)) return // @nowarn
      }
    } else if (!d.unmangleable(options)) {
      return // @nowarn
    }
    avoid.add(name)
  }

  /** Expand (un-mangle) all names back to unique readable names. */
  def expandNames(ast: AstToplevel, options: ManglerOptions = ManglerOptions()): Unit = {
    val opts          = formatOptions(options)
    val nthIdentifier = opts.nthIdentifier
    if (nthIdentifier.isInstanceOf[Base54.type]) {
      nthIdentifier.reset()
      nthIdentifier.sort()
    }
    val avoid = findCollidingNames(ast, opts)
    var cname = 0

    def nextName(): String = {
      var name = nthIdentifier.get(cname)
      cname += 1
      while (avoid.contains(name) || Token.ALL_RESERVED_WORDS.contains(name)) {
        name = nthIdentifier.get(cname)
        cname += 1
      }
      name
    }

    def rename(d: SymbolDef): Unit = {
      if (d.global && opts.cache != null) return // @nowarn
      if (d.unmangleable(opts)) return // @nowarn
      if (opts.reserved.contains(d.name)) return // @nowarn
      val redefinition = SymbolDef.redefinedCatchDef(d)
      val name         = if (redefinition != null) redefinition.nn.name else nextName()
      d.name = name
      d.orig.foreach(_.name = name)
      d.references.foreach(_.name = name)
    }

    ast.globals.foreach { case (_, g) => rename(g.asInstanceOf[SymbolDef]) }
    ast.walk(
      new TreeWalker((node: AstNode, _: () => Unit) => {
        node match {
          case s: AstScope =>
            s.variables.foreach { case (_, v) => rename(v.asInstanceOf[SymbolDef]) }
          case sym: AstSymbolCatch =>
            sym.thedef match {
              case d: SymbolDef => rename(d)
              case _ =>
            }
          case _ =>
        }
        ()
      })
    )
  }

  /** Compute character frequency for optimal base54 ordering. */
  def computeCharFrequency(ast: AstToplevel, options: ManglerOptions = ManglerOptions()): Unit = {
    val opts          = formatOptions(options)
    val nthIdentifier = opts.nthIdentifier

    // Only compute if the identifier mangler supports frequency-based sorting
    nthIdentifier match {
      case b54: Base54.type =>
        b54.reset()

        walk(
          ast,
          (node: AstNode, _: ArrayBuffer[AstNode]) => {
            node match {
              case sym: AstSymbol =>
                sym.thedef match {
                  case d: SymbolDef if !d.unmangleable(opts) =>
                    b54.consider(sym.name, -1)
                  case _ =>
                }
              case dot: AstDot =>
                dot.property match {
                  case s: String => b54.consider(s, -1)
                  case _ =>
                }
              case dotHash: AstDotHash =>
                dotHash.property match {
                  case s: String => b54.consider("#" + s, -1)
                  case _ =>
                }
              case _ =>
            }
            () // continue
          }
        )

        b54.sort()

      case _ => // invariant identifier mangler, skip
    }
  }
}
