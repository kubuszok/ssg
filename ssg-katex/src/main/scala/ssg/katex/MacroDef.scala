/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Macro definition types and global macro registry for KaTeX.
 *
 * Defines MacroDefinition, MacroExpansion, MacroArg, MacroContextInterface,
 * and the global _macros registry.
 *
 * Original source: katex src/defineMacro.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: defineMacro -> MacroDef.defineMacro
 *   Convention: TypeScript union type -> sealed trait hierarchy
 *   Idiom: Record<string, MacroDefinition> -> mutable.Map
 */
package ssg
package katex

import scala.collection.mutable

import ssg.commons.Nullable

/**
 * Provides context to macros defined by functions. Implemented by
 * MacroExpander.
 */
trait MacroContextInterface {
  def mode: Mode

  /**
   * Object mapping macros to their expansions.
   */
  def macros: Namespace[MacroDefinition]

  /**
   * Returns the topmost token on the stack, without expanding it.
   * Similar in behavior to TeX's `\futurelet`.
   */
  def future(): Token

  /**
   * Remove and return the next unexpanded token.
   */
  def popToken(): Token

  /**
   * Consume all following space tokens, without expansion.
   */
  def consumeSpaces(): Unit

  /**
   * Expand the next token only once if possible.
   */
  def expandOnce(expandableOnly: Boolean = false): Int | Boolean

  /**
   * Expand the next token only once (if possible), and return the resulting
   * top token on the stack (without removing anything from the stack).
   * Similar in behavior to TeX's `\expandafter\futurelet`.
   */
  def expandAfterFuture(): Token

  /**
   * Recursively expand first token, then return first non-expandable token.
   */
  def expandNextToken(): Token

  /**
   * Fully expand the given macro name and return the resulting list of
   * tokens, or return `Nullable.Null` if no such macro is defined.
   */
  def expandMacro(name: String): Nullable[Array[Token]]

  /**
   * Fully expand the given macro name and return the result as a string,
   * or return `Nullable.Null` if no such macro is defined.
   */
  def expandMacroAsText(name: String): Nullable[String]

  /**
   * Fully expand the given token stream and return the resulting list of
   * tokens.  Note that the input tokens are in reverse order, but the
   * output tokens are in forward order.
   */
  def expandTokens(tokens: Array[Token]): Array[Token]

  /**
   * Consume an argument from the token stream, and return the resulting array
   * of tokens and start/end token.
   */
  def consumeArg(delims: Nullable[Array[String]] = Nullable.Null): MacroArg

  /**
   * Consume the specified number of arguments from the token stream,
   * and return the resulting array of arguments.
   */
  def consumeArgs(numArgs: Int): Array[Array[Token]]

  /**
   * Determine whether a command is currently "defined" (has some
   * functionality), meaning that it's a macro (in the current group),
   * a function, a symbol, or one of the special commands listed in
   * `implicitCommands`.
   */
  def isDefined(name: String): Boolean

  /**
   * Determine whether a command is expandable.
   */
  def isExpandable(name: String): Boolean
}

/**
 * A consumed argument: the tokens and the start/end tokens.
 */
final case class MacroArg(
    tokens: Array[Token],
    start: Token,
    end: Token
)

/** Macro tokens (in reverse order). */
final case class MacroExpansion(
    tokens: Array[Token],
    numArgs: Int,
    delimiters: Nullable[Array[Array[String]]] = Nullable.Null,
    unexpandable: Boolean = false // used in \let
)

/**
 * A macro definition: either a string, a MacroExpansion, or a function
 * that takes a MacroContextInterface and returns a string or MacroExpansion.
 */
sealed trait MacroDefinition

object MacroDefinition {
  final case class StringDef(value: String) extends MacroDefinition
  final case class ExpansionDef(value: MacroExpansion) extends MacroDefinition
  final case class FunctionDef(value: MacroContextInterface => String | MacroExpansion) extends MacroDefinition

  // Convenience implicit conversions
  given Conversion[String, MacroDefinition] = StringDef(_)
  given Conversion[MacroExpansion, MacroDefinition] = ExpansionDef(_)
  given Conversion[MacroContextInterface => String | MacroExpansion, MacroDefinition] = FunctionDef(_)
}

type MacroMap = mutable.Map[String, MacroDefinition]

object MacroDef {

  /**
   * All registered global/built-in macros.
   * `macros.js` exports this same dictionary again and makes it public.
   * `Parser.js` requires this dictionary via `macros.js`.
   */
  val _macros: MacroMap = mutable.Map.empty

  // This function might one day accept an additional argument and do more things.
  def defineMacro(name: String, body: MacroDefinition): Unit = {
    _macros(name) = body
  }
}
