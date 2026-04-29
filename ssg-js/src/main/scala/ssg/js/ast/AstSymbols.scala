/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Symbol/name AST nodes for variables, functions, classes, labels, imports,
 * exports, this, super, and new.target.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*
 *   Convention: thedef typed as Any (scope analysis assigns SymbolDef)
 *   Idiom: Mutable var fields for scope analysis
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/ast.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 88493d7ca0d708389f5f78f541c4fb48e71d9fe2
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer
import ssg.js.scope.SymbolDef

// ---------------------------------------------------------------------------
// Base symbol
// ---------------------------------------------------------------------------

/** Base class for all symbols. */
trait AstSymbol extends AstNode {
  var scope: AstScope | Null = null
  var name:  String          = ""

  /** The definition of this symbol (SymbolDef). Typed as Any for assignment compatibility with scope analysis. */
  var thedef: Any | Null = null

  /** Get the SymbolDef for this symbol (typed convenience over thedef). */
  def definition(): SymbolDef | Null = thedef.asInstanceOf[SymbolDef | Null]

  /** Get the fixed value from this symbol's definition, if any. */
  def fixedValue(): AstNode | Null | Boolean =
    definition() match {
      case d: SymbolDef => d.fixedValue
      case null => null
    }

  /** Check if this symbol is unreferenced (no references and scope not pinned). */
  def unreferenced(): Boolean =
    definition() match {
      case d: SymbolDef => d.references.isEmpty && !d.scope.pinned
      case null => false
    }

  /** Check if this symbol is global. */
  def isGlobal: Boolean =
    definition() match {
      case d: SymbolDef => d.global
      case null => false
    }
}

/** A reference to new.target. */
class AstNewTarget extends AstNode {
  def nodeType: String = "NewTarget"
}

// ---------------------------------------------------------------------------
// Declaration symbols
// ---------------------------------------------------------------------------

/** A declaration symbol (symbol in var/const, function name or argument, symbol in catch). */
trait AstSymbolDeclaration extends AstSymbol {
  var init: AstNode | Null = null
}

/** Symbol defining a variable. */
class AstSymbolVar extends AstNode with AstSymbolDeclaration {
  def nodeType: String = "SymbolVar"
}

/** Symbol naming a function argument. */
class AstSymbolFunarg extends AstSymbolVar {
  override def nodeType: String = "SymbolFunarg"
}

/** Base class for block-scoped declaration symbols. */
trait AstSymbolBlockDeclaration extends AstSymbolDeclaration

/** A constant declaration. */
class AstSymbolConst extends AstNode with AstSymbolBlockDeclaration {
  def nodeType: String = "SymbolConst"
}

/** A `using` declaration. */
class AstSymbolUsing extends AstNode with AstSymbolBlockDeclaration {
  def nodeType: String = "SymbolUsing"
}

/** A block-scoped `let` declaration. */
class AstSymbolLet extends AstNode with AstSymbolBlockDeclaration {
  def nodeType: String = "SymbolLet"
}

/** Symbol naming the exception in catch. */
class AstSymbolCatch extends AstNode with AstSymbolBlockDeclaration {
  def nodeType: String = "SymbolCatch"
}

/** Symbol naming a class's name in a class declaration. Lexically scoped to containing scope. */
class AstSymbolDefClass extends AstNode with AstSymbolBlockDeclaration {
  def nodeType: String = "SymbolDefClass"
}

/** Symbol naming a class's name. Lexically scoped to the class. */
class AstSymbolClass extends AstNode with AstSymbolDeclaration {
  def nodeType: String = "SymbolClass"
}

/** Symbol defining a function. */
class AstSymbolDefun extends AstNode with AstSymbolDeclaration {
  def nodeType: String = "SymbolDefun"
}

/** Symbol naming a function expression. */
class AstSymbolLambda extends AstNode with AstSymbolDeclaration {
  def nodeType: String = "SymbolLambda"
}

/** Symbol referring to an imported name. */
class AstSymbolImport extends AstNode with AstSymbolBlockDeclaration {
  def nodeType: String = "SymbolImport"
}

// ---------------------------------------------------------------------------
// Non-declaration symbols
// ---------------------------------------------------------------------------

/** Symbol in an object defining a method. */
class AstSymbolMethod extends AstNode with AstSymbol {
  def nodeType: String = "SymbolMethod"
}

/** Symbol for a class property. */
class AstSymbolClassProperty extends AstNode with AstSymbol {
  def nodeType: String = "SymbolClassProperty"
}

/** A symbol that refers to a private property. */
class AstSymbolPrivateProperty extends AstNode with AstSymbol {
  def nodeType: String = "SymbolPrivateProperty"
}

/** Reference to some symbol (not definition/declaration). */
class AstSymbolRef extends AstNode with AstSymbol {
  def nodeType: String = "SymbolRef"
}

/** Symbol referring to a name to export. */
class AstSymbolExport extends AstSymbolRef {
  var quote: String = ""

  override def nodeType: String = "SymbolExport"
}

/** A symbol exported from this module, used in the other module. */
class AstSymbolExportForeign extends AstNode with AstSymbol {
  var quote: String = ""

  def nodeType: String = "SymbolExportForeign"
}

/** A symbol imported from a module, defined in the other module. */
class AstSymbolImportForeign extends AstNode with AstSymbol {
  var quote: String = ""

  def nodeType: String = "SymbolImportForeign"
}

// ---------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------

/** Symbol naming a label (declaration). */
class AstLabel extends AstNode with AstSymbol {
  var references: ArrayBuffer[AstNode] = ArrayBuffer.empty

  /** Mangled name for label, or null if unmangled. */
  var mangledName: String | Null = null

  def nodeType: String = "Label"

  /** Initialize references list and set thedef to self. */
  def initialize(): Unit = {
    references = ArrayBuffer.empty
    thedef = this
  }
}

/** Reference to a label symbol. */
class AstLabelRef extends AstNode with AstSymbol {
  def nodeType: String = "LabelRef"
}

// ---------------------------------------------------------------------------
// This / Super
// ---------------------------------------------------------------------------

/** The `this` symbol. */
class AstThis extends AstNode with AstSymbol {
  def nodeType: String = "This"
}

/** The `super` symbol. */
class AstSuper extends AstThis {
  override def nodeType: String = "Super"
}
