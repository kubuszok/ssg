/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Literal/constant AST nodes: strings, numbers, booleans, atoms.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*
 *   Convention: RegExp value stored as RegExpValue(source, flags)
 *   Idiom: Atom nodes are leaf nodes with fixed values
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/ast.js
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package ast

/** Value holder for regular expressions since Scala has no native RegExp literal. */
final case class RegExpValue(source: String, flags: String)

// ---------------------------------------------------------------------------
// Base constant
// ---------------------------------------------------------------------------

/** Base class for all constants. */
trait AstConstant extends AstNode

// ---------------------------------------------------------------------------
// String / Number / BigInt / RegExp
// ---------------------------------------------------------------------------

/** A string literal. */
class AstString extends AstNode with AstConstant {
  var value:       String = ""
  var quote:       String = ""
  var annotations: Int    = 0

  def nodeType: String = "String"
}

/** A number literal. */
class AstNumber extends AstNode with AstConstant {
  var value: Double = 0.0
  var raw:   String = ""

  def nodeType: String = "Number"
}

/** A big int literal. */
class AstBigInt extends AstNode with AstConstant {
  var value: String = ""
  var raw:   String = ""

  def nodeType: String = "BigInt"
}

/** A regexp literal. */
class AstRegExp extends AstNode with AstConstant {
  var value: RegExpValue = RegExpValue("", "")

  def nodeType: String = "RegExp"
}

// ---------------------------------------------------------------------------
// Atoms
// ---------------------------------------------------------------------------

/** Base class for atoms (null, NaN, undefined, Infinity, booleans, holes). */
trait AstAtom extends AstConstant

/** The `null` atom. */
class AstNull extends AstNode with AstAtom {
  def nodeType: String = "Null"
}

/** The `NaN` value. */
class AstNaN extends AstNode with AstAtom {
  def nodeType: String = "NaN"
}

/** The `undefined` value. */
class AstUndefined extends AstNode with AstAtom {
  def nodeType: String = "Undefined"
}

/** The `Infinity` value. */
class AstInfinity extends AstNode with AstAtom {
  def nodeType: String = "Infinity"
}

/** A hole in an array. */
class AstHole extends AstNode with AstAtom {
  def nodeType: String = "Hole"
}

/** Base class for booleans. */
trait AstBoolean extends AstAtom

/** The `true` atom. */
class AstTrue extends AstNode with AstBoolean {
  def nodeType: String = "True"
}

/** The `false` atom. */
class AstFalse extends AstNode with AstBoolean {
  def nodeType: String = "False"
}
