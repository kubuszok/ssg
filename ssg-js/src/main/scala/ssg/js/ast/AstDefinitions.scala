/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Variable/constant declaration and import/export AST nodes.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*
 *   Convention: ArrayBuffer for definitions/imported_names/exported_names
 *   Idiom: T | Null for nullable fields
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer

// ---------------------------------------------------------------------------
// Variable definitions
// ---------------------------------------------------------------------------

/** Base class for variable definitions and `using`. */
trait AstDefinitionsLike extends AstStatement {
  var definitions: ArrayBuffer[AstNode] = ArrayBuffer.empty
}

/** Helper: walk definitions. */
private trait AstDefinitionsLikeWalk extends AstDefinitionsLike {
  override protected def walkChildren(visitor: TreeWalker): Unit = {
    var i = 0
    while (i < definitions.size) {
      definitions(i).walk(visitor)
      i += 1
    }
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = definitions.size
    while ({ i -= 1; i >= 0 }) push(definitions(i))
  }
}

/** Base class for `var`, `let`, or `const` nodes. */
trait AstDefinitions extends AstDefinitionsLike

/** A `var` statement. */
class AstVar extends AstNode with AstDefinitions with AstDefinitionsLikeWalk {
  def nodeType: String = "Var"
}

/** A `let` statement. */
class AstLet extends AstNode with AstDefinitions with AstDefinitionsLikeWalk {
  def nodeType: String = "Let"
}

/** A `const` statement. */
class AstConst extends AstNode with AstDefinitions with AstDefinitionsLikeWalk {
  def nodeType: String = "Const"
}

/** A `using` statement. */
class AstUsing extends AstNode with AstDefinitionsLike with AstDefinitionsLikeWalk {
  var isAwait: Boolean = false

  def nodeType: String = "Using"
}

// ---------------------------------------------------------------------------
// Variable definition (name = value pair)
// ---------------------------------------------------------------------------

/** A name=value pair in a variable definition or `using`. */
trait AstVarDefLike extends AstNode {
  var name:  AstNode | Null = null
  var value: AstNode | Null = null
}

/** Helper: walk name and value. */
private trait AstVarDefLikeWalk extends AstVarDefLike {
  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (name != null) name.nn.walk(visitor)
    if (value != null) value.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (value != null) push(value.nn)
    if (name != null) push(name.nn)
  }
}

/** A variable declaration; only appears in an AstDefinitions node. */
class AstVarDef extends AstNode with AstVarDefLike with AstVarDefLikeWalk {
  def nodeType: String = "VarDef"
}

/** Like VarDef but specific to AstUsing. */
class AstUsingDef extends AstNode with AstVarDefLike with AstVarDefLikeWalk {
  def nodeType: String = "UsingDef"
}

// ---------------------------------------------------------------------------
// Import / Export name mapping
// ---------------------------------------------------------------------------

/** The part of the export/import that declares names from a module. */
class AstNameMapping extends AstNode {
  var foreignName: AstNode | Null = null
  var name:        AstNode | Null = null

  def nodeType: String = "NameMapping"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (foreignName != null) foreignName.nn.walk(visitor)
    if (name != null) name.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (name != null) push(name.nn)
    if (foreignName != null) push(foreignName.nn)
  }
}

// ---------------------------------------------------------------------------
// Import
// ---------------------------------------------------------------------------

/** An `import` statement. */
class AstImport extends AstNode with AstStatement {
  var importedName:  AstNode | Null              = null
  var importedNames: ArrayBuffer[AstNode] | Null = null
  var moduleName:    AstNode | Null              = null
  var attributes:    AstNode | Null              = null

  def nodeType: String = "Import"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (importedName != null) importedName.nn.walk(visitor)
    val names = importedNames
    if (names != null) {
      var i = 0
      while (i < names.nn.size) {
        names.nn(i).walk(visitor)
        i += 1
      }
    }
    if (moduleName != null) moduleName.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (moduleName != null) push(moduleName.nn)
    val names = importedNames
    if (names != null) {
      var i = names.nn.size
      while ({ i -= 1; i >= 0 }) push(names.nn(i))
    }
    if (importedName != null) push(importedName.nn)
  }
}

/** A reference to import.meta. */
class AstImportMeta extends AstNode {
  def nodeType: String = "ImportMeta"
}

// ---------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------

/** An `export` statement. */
class AstExport extends AstNode with AstStatement {
  var exportedDefinition: AstNode | Null              = null
  var exportedValue:      AstNode | Null              = null
  var isDefault:          Boolean                     = false
  var exportedNames:      ArrayBuffer[AstNode] | Null = null
  var moduleName:         AstNode | Null              = null
  var attributes:         AstNode | Null              = null

  def nodeType: String = "Export"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (exportedDefinition != null) exportedDefinition.nn.walk(visitor)
    if (exportedValue != null) exportedValue.nn.walk(visitor)
    val names = exportedNames
    if (names != null) {
      var i = 0
      while (i < names.nn.size) {
        names.nn(i).walk(visitor)
        i += 1
      }
    }
    if (moduleName != null) moduleName.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (moduleName != null) push(moduleName.nn)
    val names = exportedNames
    if (names != null) {
      var i = names.nn.size
      while ({ i -= 1; i >= 0 }) push(names.nn(i))
    }
    if (exportedValue != null) push(exportedValue.nn)
    if (exportedDefinition != null) push(exportedDefinition.nn)
  }
}
