/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/statement.dart,
 *              lib/src/ast/sass/statement/parent.dart,
 *              lib/src/ast/sass/statement/at_root_rule.dart,
 *              lib/src/ast/sass/statement/at_rule.dart,
 *              lib/src/ast/sass/statement/callable_declaration.dart,
 *              lib/src/ast/sass/statement/content_block.dart,
 *              lib/src/ast/sass/statement/content_rule.dart,
 *              lib/src/ast/sass/statement/debug_rule.dart,
 *              lib/src/ast/sass/statement/declaration.dart,
 *              lib/src/ast/sass/statement/each_rule.dart,
 *              lib/src/ast/sass/statement/error_rule.dart,
 *              lib/src/ast/sass/statement/extend_rule.dart,
 *              lib/src/ast/sass/statement/for_rule.dart,
 *              lib/src/ast/sass/statement/forward_rule.dart,
 *              lib/src/ast/sass/statement/function_rule.dart,
 *              lib/src/ast/sass/statement/if_rule.dart,
 *              lib/src/ast/sass/statement/import_rule.dart,
 *              lib/src/ast/sass/statement/include_rule.dart,
 *              lib/src/ast/sass/statement/loud_comment.dart,
 *              lib/src/ast/sass/statement/media_rule.dart,
 *              lib/src/ast/sass/statement/mixin_rule.dart,
 *              lib/src/ast/sass/statement/return_rule.dart,
 *              lib/src/ast/sass/statement/silent_comment.dart,
 *              lib/src/ast/sass/statement/style_rule.dart,
 *              lib/src/ast/sass/statement/stylesheet.dart,
 *              lib/src/ast/sass/statement/supports_rule.dart,
 *              lib/src/ast/sass/statement/use_rule.dart,
 *              lib/src/ast/sass/statement/variable_declaration.dart,
 *              lib/src/ast/sass/statement/warn_rule.dart,
 *              lib/src/ast/sass/statement/while_rule.dart
 * Original: Copyright (c) 2016-2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: statement.dart + 29 subtype files -> Statement.scala
 *   Convention: Dart abstract class + final subclasses -> Scala abstract class + final classes
 *   Idiom: ParentStatement has type parameter for nullable children list;
 *          StatementVisitor as forward-reference trait;
 *          hasDeclarations computed eagerly from children
 */
package ssg
package sass
package ast
package sass

import java.net.URI

import ssg.sass.{ Deprecation, Nullable }
import ssg.sass.Nullable.*
import ssg.sass.util.{ FileSpan, initialIdentifier, initialQuoted, withoutInitialAtRule, withoutNamespace }

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

// ===========================================================================
// StatementVisitor — forward reference trait
// ===========================================================================

/** Visitor interface for [Statement] nodes. */
trait StatementVisitor[T] {
  def visitAtRootRule(node:          AtRootRule):          T
  def visitAtRule(node:              AtRule):              T
  def visitContentBlock(node:        ContentBlock):        T
  def visitContentRule(node:         ContentRule):         T
  def visitDebugRule(node:           DebugRule):           T
  def visitDeclaration(node:         Declaration):         T
  def visitEachRule(node:            EachRule):            T
  def visitErrorRule(node:           ErrorRule):           T
  def visitExtendRule(node:          ExtendRule):          T
  def visitForRule(node:             ForRule):             T
  def visitForwardRule(node:         ForwardRule):         T
  def visitFunctionRule(node:        FunctionRule):        T
  def visitIfRule(node:              IfRule):              T
  def visitImportRule(node:          ImportRule):          T
  def visitIncludeRule(node:         IncludeRule):         T
  def visitLoudComment(node:         LoudComment):         T
  def visitMediaRule(node:           MediaRule):           T
  def visitMixinRule(node:           MixinRule):           T
  def visitReturnRule(node:          ReturnRule):          T
  def visitSilentComment(node:       SilentComment):       T
  def visitStyleRule(node:           StyleRule):           T
  def visitStylesheet(node:          Stylesheet):          T
  def visitSupportsRule(node:        SupportsRule):        T
  def visitUseRule(node:             UseRule):             T
  def visitVariableDeclaration(node: VariableDeclaration): T
  def visitWarnRule(node:            WarnRule):            T
  def visitWhileRule(node:           WhileRule):           T
}

// ===========================================================================
// Statement — base class
// ===========================================================================

/** A statement in a Sass syntax tree. */
abstract class Statement extends SassNode {

  /** Calls the appropriate visit method on [visitor]. */
  def accept[T](visitor: StatementVisitor[T]): T
}

// ===========================================================================
// ParentStatement — statement with children
// ===========================================================================

/** A [Statement] that can have child statements.
  *
  * @param children
  *   the child statements, or empty if this statement has no body
  */
abstract class ParentStatement(
  val children: Nullable[List[Statement]]
) extends Statement {

  /** Whether any of [children] is a variable, function, or mixin declaration, or a dynamic import rule.
    */
  val hasDeclarations: Boolean = children.fold(false) { kids =>
    kids.exists {
      case _:  VariableDeclaration => true
      case _:  FunctionRule        => true
      case _:  MixinRule           => true
      case ir: ImportRule          => ir.imports.exists(_.isInstanceOf[DynamicImport])
      case _ => false
    }
  }
}

// ===========================================================================
// CallableDeclaration — abstract base for FunctionRule and MixinRule
// ===========================================================================

/** An abstract class for callables (functions or mixins) declared in user code.
  *
  * @param originalName
  *   the callable's original name
  * @param parameters
  *   the declared parameters
  * @param children
  *   the body statements
  * @param span
  *   the source span
  * @param comment
  *   the comment immediately preceding this declaration
  */
abstract class CallableDeclaration(
  val originalName: String,
  val parameters:   ParameterList,
  childStatements:  List[Statement],
  val span:         FileSpan,
  val comment:      Nullable[SilentComment] = Nullable.empty
) extends ParentStatement(Nullable(childStatements)) {

  /** The name of this callable, with underscores converted to hyphens. */
  val name: String = originalName.replace('_', '-')

  /** Convenience accessor for children as a non-nullable list. */
  def childrenList: List[Statement] = children.get
}

// ===========================================================================
// Individual statement types
// ===========================================================================

// ---------------------------------------------------------------------------
// AtRootRule
// ---------------------------------------------------------------------------

/** An `@at-root` rule. This moves its contents "up" the tree through parent nodes.
  *
  * @param query
  *   the query specifying which statements to move through
  * @param span
  *   the source span
  */
final class AtRootRule(
  childStatements: List[Statement],
  val span:        FileSpan,
  val query:       Nullable[Interpolation] = Nullable.empty
) extends ParentStatement(Nullable(childStatements)) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitAtRootRule(this)

  override def toString: String = {
    val buffer = new StringBuilder("@at-root ")
    query.foreach(q => buffer.append(s"$q "))
    buffer.append(s"{${children.get.mkString(" ")}}")
    buffer.toString()
  }
}

// ---------------------------------------------------------------------------
// AtRule
// ---------------------------------------------------------------------------

/** An unknown at-rule.
  *
  * @param name
  *   the name of this rule
  * @param span
  *   the source span
  * @param value
  *   the value of this rule
  */
final class AtRule(
  val name:        Interpolation,
  val span:        FileSpan,
  val value:       Nullable[Interpolation] = Nullable.empty,
  childStatements: Nullable[List[Statement]] = Nullable.empty
) extends ParentStatement(childStatements) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitAtRule(this)

  override def toString: String = {
    val buffer = new StringBuilder(s"@$name")
    value.foreach(v => buffer.append(s" $v"))
    children.fold {
      buffer.append(";")
    } { kids =>
      buffer.append(s" {${kids.mkString(" ")}}")
    }
    buffer.toString()
  }
}

// ---------------------------------------------------------------------------
// ContentBlock
// ---------------------------------------------------------------------------

/** An anonymous block of code that's invoked for a [ContentRule].
  *
  * @param parameters
  *   the parameters
  * @param span
  *   the source span
  */
final class ContentBlock(
  parameters:      ParameterList,
  childStatements: List[Statement],
  span:            FileSpan
) extends CallableDeclaration("@content", parameters, childStatements, span) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitContentBlock(this)

  override def toString: String =
    (if (parameters.isEmpty) "" else s" using ($parameters)") +
      s" {${childrenList.mkString(" ")}}"
}

// ---------------------------------------------------------------------------
// ContentRule
// ---------------------------------------------------------------------------

/** A `@content` rule. Used in a mixin to include statement-level content passed by the caller.
  *
  * @param arguments
  *   the arguments passed to this `@content` rule
  * @param span
  *   the source span
  */
final class ContentRule(
  val arguments: ArgumentList,
  val span:      FileSpan
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitContentRule(this)

  override def toString: String =
    if (arguments.isEmpty) "@content;"
    else s"@content($arguments);"
}

// ---------------------------------------------------------------------------
// DebugRule
// ---------------------------------------------------------------------------

/** A `@debug` rule. Prints a Sass value for debugging purposes.
  *
  * @param expression
  *   the expression to print
  * @param span
  *   the source span
  */
final class DebugRule(
  val expression: Expression,
  val span:       FileSpan
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitDebugRule(this)

  override def toString: String = s"@debug $expression;"
}

// ---------------------------------------------------------------------------
// Declaration
// ---------------------------------------------------------------------------

/** A declaration (that is, a `name: value` pair).
  *
  * @param name
  *   the name of this declaration
  * @param span
  *   the source span
  * @param value
  *   the value of this declaration
  * @param parsedAsSassScript
  *   whether the value was parsed as SassScript
  */
final class Declaration private (
  val name:               Interpolation,
  val span:               FileSpan,
  val value:              Nullable[Expression],
  val parsedAsSassScript: Boolean,
  val isImportant:        Boolean,
  childStatements:        Nullable[List[Statement]]
) extends ParentStatement(childStatements) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitDeclaration(this)

  override def toString: String = {
    val buffer = new StringBuilder()
    buffer.append(name)
    buffer.append(':')
    value.foreach { v =>
      if (parsedAsSassScript) buffer.append(' ')
      buffer.append(v)
    }
    if (isImportant) buffer.append(" !important")
    children.fold {
      buffer.append(";")
    } { kids =>
      buffer.append(s" {${kids.mkString(" ")}}")
    }
    buffer.toString()
  }
}

object Declaration {

  /** Creates a declaration with no children. */
  def apply(
    name:        Interpolation,
    value:       Expression,
    span:        FileSpan,
    isImportant: Boolean = false
  ): Declaration =
    new Declaration(name, span, Nullable(value), parsedAsSassScript = true, isImportant, Nullable.empty)

  /** Creates a declaration with no children whose value is not parsed as SassScript. */
  def notSassScript(name: Interpolation, value: StringExpression, span: FileSpan): Declaration =
    new Declaration(name, span, Nullable(value), parsedAsSassScript = false, isImportant = false, Nullable.empty)

  /** Creates a declaration with children. */
  def nested(
    name:     Interpolation,
    children: List[Statement],
    span:     FileSpan,
    value:    Nullable[Expression] = Nullable.empty
  ): Declaration =
    new Declaration(name, span, value, parsedAsSassScript = true, isImportant = false, Nullable(children))
}

// ---------------------------------------------------------------------------
// EachRule
// ---------------------------------------------------------------------------

/** An `@each` rule. Iterates over values in a list or map.
  *
  * @param variables
  *   the variables assigned for each iteration
  * @param list
  *   the expression whose value this iterates through
  * @param span
  *   the source span
  */
final class EachRule(
  val variables:   List[String],
  val list:        Expression,
  childStatements: List[Statement],
  val span:        FileSpan
) extends ParentStatement(Nullable(childStatements)) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitEachRule(this)

  override def toString: String =
    s"@each ${variables.map(v => s"$$$v").mkString(", ")} in " +
      s"$list {${children.get.mkString(" ")}}"
}

// ---------------------------------------------------------------------------
// ErrorRule
// ---------------------------------------------------------------------------

/** An `@error` rule. Emits an error and stops execution.
  *
  * @param expression
  *   the expression to evaluate for the error message
  * @param span
  *   the source span
  */
final class ErrorRule(
  val expression: Expression,
  val span:       FileSpan
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitErrorRule(this)

  override def toString: String = s"@error $expression;"
}

// ---------------------------------------------------------------------------
// ExtendRule
// ---------------------------------------------------------------------------

/** An `@extend` rule. Gives one selector all the styling of another.
  *
  * @param selector
  *   the interpolation for the selector that will be extended
  * @param span
  *   the source span
  * @param isOptional
  *   whether this is an optional extension
  */
final class ExtendRule(
  val selector:   Interpolation,
  val span:       FileSpan,
  val isOptional: Boolean = false
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitExtendRule(this)

  override def toString: String =
    s"@extend $selector${if (isOptional) " !optional" else ""};"
}

// ---------------------------------------------------------------------------
// ForRule
// ---------------------------------------------------------------------------

/** A `@for` rule. Iterates a set number of times.
  *
  * @param variable
  *   the name of the variable that will contain the index
  * @param from
  *   the expression for the start index
  * @param to
  *   the expression for the end index
  * @param span
  *   the source span
  * @param isExclusive
  *   whether [to] is exclusive
  */
final class ForRule(
  val variable:    String,
  val from:        Expression,
  val to:          Expression,
  childStatements: List[Statement],
  val span:        FileSpan,
  val isExclusive: Boolean = true
) extends ParentStatement(Nullable(childStatements)) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitForRule(this)

  override def toString: String =
    s"@for $$$variable from $from ${if (isExclusive) "to" else "through"} $to " +
      s"{${children.get.mkString(" ")}}"
}

// ---------------------------------------------------------------------------
// ForwardRule
// ---------------------------------------------------------------------------

/** A `@forward` rule.
  *
  * @param url
  *   the URI of the module to forward
  * @param span
  *   the source span
  * @param prefix
  *   the prefix to add to member names, or empty
  * @param shownMixinsAndFunctions
  *   set of shown mixin/function names, or empty
  * @param shownVariables
  *   set of shown variable names, or empty
  * @param hiddenMixinsAndFunctions
  *   set of hidden mixin/function names, or empty
  * @param hiddenVariables
  *   set of hidden variable names, or empty
  * @param configuration
  *   variable assignments to configure loaded modules
  */
final class ForwardRule(
  val url:                      URI,
  val span:                     FileSpan,
  val prefix:                   Nullable[String] = Nullable.empty,
  val shownMixinsAndFunctions:  Nullable[Set[String]] = Nullable.empty,
  val shownVariables:           Nullable[Set[String]] = Nullable.empty,
  val hiddenMixinsAndFunctions: Nullable[Set[String]] = Nullable.empty,
  val hiddenVariables:          Nullable[Set[String]] = Nullable.empty,
  val configuration:            List[ConfiguredVariable] = Nil
) extends Statement
    with SassDependency {

  def urlSpan: FileSpan = span.withoutInitialAtRule().initialQuoted()

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitForwardRule(this)

  override def toString: String = {
    val buffer = new StringBuilder(
      s"@forward ${StringExpression.quoteText(url.toString)}"
    )
    shownMixinsAndFunctions.foreach { shown =>
      buffer.append(" show ")
      buffer.append(_memberList(shown, shownVariables.get))
    }
    hiddenMixinsAndFunctions.foreach { hidden =>
      if (hidden.nonEmpty) {
        buffer.append(" hide ")
        buffer.append(_memberList(hidden, hiddenVariables.get))
      }
    }
    prefix.foreach(p => buffer.append(s" as $p*"))
    if (configuration.nonEmpty) {
      buffer.append(s" with (${configuration.mkString(", ")})")
    }
    buffer.append(";")
    buffer.toString()
  }

  private def _memberList(
    mixinsAndFunctions: Set[String],
    variables:          Set[String]
  ): String =
    (mixinsAndFunctions.toList ++ variables.map(n => s"$$$n")).mkString(", ")
}

// ---------------------------------------------------------------------------
// FunctionRule
// ---------------------------------------------------------------------------

/** A function declaration. Declares a function invoked using normal CSS function syntax.
  *
  * @param originalName
  *   the function name (original underscores)
  * @param parameters
  *   the declared parameters
  * @param span
  *   the source span
  * @param comment
  *   the comment immediately preceding this declaration
  */
final class FunctionRule(
  originalName:    String,
  parameters:      ParameterList,
  childStatements: List[Statement],
  span:            FileSpan,
  comment:         Nullable[SilentComment] = Nullable.empty
) extends CallableDeclaration(originalName, parameters, childStatements, span, comment)
    with SassDeclaration {

  def nameSpan: FileSpan =
    span.withoutInitialAtRule().initialIdentifier()

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitFunctionRule(this)

  override def toString: String =
    s"@function $name($parameters) {${childrenList.mkString(" ")}}"
}

// ---------------------------------------------------------------------------
// IfRule + IfClause + ElseClause
// ---------------------------------------------------------------------------

/** The superclass of `@if` and `@else` clauses. */
sealed abstract class IfRuleClause(childStatements: List[Statement]) {

  /** The statements to evaluate if this clause matches. */
  val children: List[Statement] = childStatements

  /** Whether any of [children] is a variable, function, or mixin declaration. */
  val hasDeclarations: Boolean = children.exists {
    case _:  VariableDeclaration => true
    case _:  FunctionRule        => true
    case _:  MixinRule           => true
    case ir: ImportRule          => ir.imports.exists(_.isInstanceOf[DynamicImport])
    case _ => false
  }
}

/** An `@if` or `@else if` clause in an `@if` rule.
  *
  * @param expression
  *   the expression to evaluate
  */
final class IfClause(
  val expression:  Expression,
  childStatements: List[Statement]
) extends IfRuleClause(childStatements) {

  override def toString: String =
    s"@if $expression {${children.mkString(" ")}}"
}

/** An `@else` clause in an `@if` rule. */
final class ElseClause(
  childStatements: List[Statement]
) extends IfRuleClause(childStatements) {

  override def toString: String =
    s"@else {${children.mkString(" ")}}"
}

/** An `@if` rule. Conditionally executes a block of code.
  *
  * @param clauses
  *   the `@if` and `@else if` clauses
  * @param span
  *   the source span
  * @param lastClause
  *   the final `@else` clause, or empty
  */
final class IfRule(
  val clauses:    List[IfClause],
  val span:       FileSpan,
  val lastClause: Nullable[ElseClause] = Nullable.empty
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitIfRule(this)

  override def toString: String = {
    val result = clauses.zipWithIndex
      .map { case (clause, index) =>
        s"@${if (index == 0) "if" else "else if"} ${clause.expression} " +
          s"{${clause.children.mkString(" ")}}"
      }
      .mkString(" ")
    lastClause.fold(result)(ec => s"$result $ec")
  }
}

// ---------------------------------------------------------------------------
// ImportRule
// ---------------------------------------------------------------------------

/** An `@import` rule.
  *
  * @param imports
  *   the imports imported by this statement
  * @param span
  *   the source span
  */
final class ImportRule(
  val imports: List[Import],
  val span:    FileSpan
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitImportRule(this)

  override def toString: String = s"@import ${imports.mkString(", ")};"
}

// ---------------------------------------------------------------------------
// IncludeRule
// ---------------------------------------------------------------------------

/** A mixin invocation.
  *
  * @param originalName
  *   the original name of the mixin being invoked
  * @param arguments
  *   the arguments to pass to the mixin
  * @param span
  *   the source span
  * @param namespace
  *   the namespace of the mixin, or empty
  * @param content
  *   the content block, or empty
  */
final class IncludeRule(
  val originalName: String,
  val arguments:    ArgumentList,
  val span:         FileSpan,
  val namespace:    Nullable[String] = Nullable.empty,
  val content:      Nullable[ContentBlock] = Nullable.empty
) extends Statement
    with CallableInvocation
    with SassReference {

  /** The name with underscores converted to hyphens. */
  val name: String = originalName.replace('_', '-')

  def nameSpan: FileSpan = {
    var startSpan =
      if (span.text.startsWith("+")) span.subspan(1).trimLeft()
      else span.withoutInitialAtRule()
    namespace.foreach { _ =>
      startSpan = startSpan.withoutNamespace()
    }
    startSpan.initialIdentifier()
  }

  def namespaceSpan: Nullable[FileSpan] =
    if (namespace.isEmpty) Nullable.empty
    else {
      val startSpan =
        if (span.text.startsWith("+")) span.subspan(1).trimLeft()
        else span.withoutInitialAtRule()
      Nullable(startSpan.initialIdentifier())
    }

  /** Returns this include's span, without its content block. */
  def spanWithoutContent: FileSpan =
    if (content.isEmpty) span
    else span.file.span(span.start.offset, arguments.span.end.offset).trim()

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitIncludeRule(this)

  override def toString: String = {
    val buffer = new StringBuilder("@include ")
    namespace.foreach(ns => buffer.append(s"$ns."))
    buffer.append(name)
    if (!arguments.isEmpty) buffer.append(s"($arguments)")
    content.fold(buffer.append(";"))(c => buffer.append(s" $c"))
    buffer.toString()
  }
}

// ---------------------------------------------------------------------------
// LoudComment
// ---------------------------------------------------------------------------

/** A loud CSS-style comment.
  *
  * @param text
  *   the interpolated text of this comment, including comment characters
  */
final class LoudComment(
  val text: Interpolation
) extends Statement {

  def span: FileSpan = text.span

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitLoudComment(this)

  override def toString: String = text.toString
}

// ---------------------------------------------------------------------------
// MediaRule
// ---------------------------------------------------------------------------

/** A `@media` rule.
  *
  * @param query
  *   the query that determines on which platforms the styles are in effect
  * @param span
  *   the source span
  */
final class MediaRule(
  val query:       Interpolation,
  childStatements: List[Statement],
  val span:        FileSpan
) extends ParentStatement(Nullable(childStatements)) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitMediaRule(this)

  override def toString: String =
    s"@media $query {${children.get.mkString(" ")}}"
}

// ---------------------------------------------------------------------------
// MixinRule
// ---------------------------------------------------------------------------

/** A mixin declaration. Declares a mixin invoked using `@include`.
  *
  * @param originalName
  *   the mixin name (original underscores)
  * @param parameters
  *   the declared parameters
  * @param span
  *   the source span
  * @param comment
  *   the comment immediately preceding this declaration
  */
final class MixinRule(
  originalName:    String,
  parameters:      ParameterList,
  childStatements: List[Statement],
  span:            FileSpan,
  comment:         Nullable[SilentComment] = Nullable.empty
) extends CallableDeclaration(originalName, parameters, childStatements, span, comment)
    with SassDeclaration {

  /** Whether the mixin contains a `@content` rule. Computed lazily. */
  lazy val hasContent: Boolean = _hasContentInChildren(childrenList)

  def nameSpan: FileSpan = {
    val startSpan =
      if (span.text.startsWith("=")) span.subspan(1).trimLeft()
      else span.withoutInitialAtRule()
    startSpan.initialIdentifier()
  }

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitMixinRule(this)

  override def toString: String = {
    val buffer = new StringBuilder(s"@mixin $name")
    if (!parameters.isEmpty) buffer.append(s"($parameters)")
    buffer.append(s" {${childrenList.mkString(" ")}}")
    buffer.toString()
  }

  /** Recursively checks whether any child is a [ContentRule].
    *
    * dart-sass uses a full `StatementSearchVisitor` for this. We replicate that behaviour by pattern-matching all statement types that carry nested children — in particular [[IfRule]], whose clauses
    * are *not* [[ParentStatement]] subtypes and would otherwise be missed.
    */
  private def _hasContentInChildren(stmts: List[Statement]): Boolean =
    stmts.exists {
      case _:  ContentRule => true
      case ir: IfRule      =>
        ir.clauses.exists(c => _hasContentInChildren(c.children)) ||
        ir.lastClause.fold(false)(ec => _hasContentInChildren(ec.children))
      case ps: ParentStatement =>
        ps.children.fold(false)(_hasContentInChildren)
      case _ => false
    }
}

// ---------------------------------------------------------------------------
// ReturnRule
// ---------------------------------------------------------------------------

/** A `@return` rule. Exits from the current function body with a return value.
  *
  * @param expression
  *   the value to return from this function
  * @param span
  *   the source span
  */
final class ReturnRule(
  val expression: Expression,
  val span:       FileSpan
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitReturnRule(this)

  override def toString: String = s"@return $expression;"
}

// ---------------------------------------------------------------------------
// SilentComment
// ---------------------------------------------------------------------------

/** A silent Sass-style comment.
  *
  * @param text
  *   the text of this comment, including comment characters
  * @param span
  *   the source span
  */
final class SilentComment(
  val text: String,
  val span: FileSpan
) extends Statement {

  /** The subset of lines marked as documentation comments (beginning with `///`). Returns empty when there is no documentation comment.
    */
  def docComment: Nullable[String] = {
    val buffer = new StringBuilder()
    val lines  = text.split("\n")
    var i      = 0
    while (i < lines.length) {
      val trimmed = lines(i).trim()
      if (trimmed.startsWith("///")) {
        val rest    = trimmed.substring(3)
        val content = if (rest.startsWith(" ")) rest.substring(1) else rest
        buffer.append(content)
        buffer.append('\n')
      }
      i += 1
    }
    val result = buffer.toString().stripTrailing()
    if (result.nonEmpty) Nullable(result) else Nullable.empty
  }

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitSilentComment(this)

  override def toString: String = text
}

// ---------------------------------------------------------------------------
// StyleRule
// ---------------------------------------------------------------------------

/** A style rule. Applies style declarations to elements that match a given selector.
  *
  * @param selector
  *   the selector (unparsed interpolation), or empty
  * @param parsedSelector
  *   the pre-parsed selector, or empty
  * @param span
  *   the source span
  */
final class StyleRule private (
  val selector:       Nullable[Interpolation],
  val parsedSelector: Nullable[InterpolatedSelectorList],
  childStatements:    List[Statement],
  val span:           FileSpan
) extends ParentStatement(Nullable(childStatements)) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitStyleRule(this)

  override def toString: String = {
    val sel = selector.fold(parsedSelector.get.toString)(_.toString)
    s"$sel {${children.get.mkString(" ")}}"
  }
}

object StyleRule {

  /** Constructs a style rule with an unparsed selector. */
  def apply(
    selector: Interpolation,
    children: List[Statement],
    span:     FileSpan
  ): StyleRule =
    new StyleRule(Nullable(selector), Nullable.empty, children, span)

  /** Constructs a style rule with a pre-parsed selector. */
  def withParsedSelector(
    parsedSelector: InterpolatedSelectorList,
    children:       List[Statement],
    span:           FileSpan
  ): StyleRule =
    new StyleRule(Nullable.empty, Nullable(parsedSelector), children, span)
}

// ---------------------------------------------------------------------------
// Stylesheet
// ---------------------------------------------------------------------------

/** A Sass stylesheet. This is the root Sass node. It contains top-level statements.
  *
  * @param span
  *   the source span
  * @param plainCss
  *   whether this was parsed from a plain CSS stylesheet
  * @param parseTimeWarnings
  *   warnings discovered while parsing
  * @param globalVariables
  *   normalized global variable names -> definition spans
  */
final class Stylesheet(
  childStatements:       List[Statement],
  val span:              FileSpan,
  val plainCss:          Boolean = false,
  val parseTimeWarnings: List[ParseTimeWarning] = Nil,
  val globalVariables:   Map[String, FileSpan] = Map.empty
) extends ParentStatement(Nullable(childStatements)) {

  /** All the `@use` rules that appear in this stylesheet. */
  val uses: List[UseRule] = _collectUses(children.get)

  /** All the `@forward` rules that appear in this stylesheet. */
  val forwards: List[ForwardRule] = _collectForwards(children.get)

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitStylesheet(this)

  override def toString: String = children.get.mkString(" ")

  private def _collectUses(stmts: List[Statement]): List[UseRule] = boundary {
    val buffer = scala.collection.mutable.ListBuffer[UseRule]()
    for (child <- stmts)
      child match {
        case u: UseRule => buffer += u
        case _: ForwardRule | _: SilentComment | _: LoudComment | _: VariableDeclaration => // allowed between @use/@forward
        case _                                                                           => break(buffer.toList)
      }
    buffer.toList
  }

  private def _collectForwards(stmts: List[Statement]): List[ForwardRule] = boundary {
    val buffer = scala.collection.mutable.ListBuffer[ForwardRule]()
    for (child <- stmts)
      child match {
        case f: ForwardRule => buffer += f
        case _: UseRule | _: SilentComment | _: LoudComment | _: VariableDeclaration => // allowed between @use/@forward
        case _                                                                       => break(buffer.toList)
      }
    buffer.toList
  }
}

/** Record type for a warning discovered while parsing a stylesheet. */
final case class ParseTimeWarning(
  deprecation: Nullable[Deprecation],
  span:        FileSpan,
  message:     String
)

// ---------------------------------------------------------------------------
// SupportsRule
// ---------------------------------------------------------------------------

/** A `@supports` rule.
  *
  * @param condition
  *   the condition that selects what browsers this rule targets
  * @param span
  *   the source span
  */
final class SupportsRule(
  val condition:   SupportsCondition,
  childStatements: List[Statement],
  val span:        FileSpan
) extends ParentStatement(Nullable(childStatements)) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitSupportsRule(this)

  override def toString: String =
    s"@supports $condition {${children.get.mkString(" ")}}"
}

// ---------------------------------------------------------------------------
// UseRule
// ---------------------------------------------------------------------------

/** A `@use` rule.
  *
  * @param url
  *   the URI of the module to use
  * @param namespace
  *   the namespace for members, or empty for no namespace
  * @param span
  *   the source span
  * @param configuration
  *   variable assignments to configure the loaded module
  */
final class UseRule(
  val url:           URI,
  val namespace:     Nullable[String],
  val span:          FileSpan,
  val configuration: List[ConfiguredVariable] = Nil
) extends Statement
    with SassDependency {

  // Validate: guarded variables not allowed in @use
  for (variable <- configuration)
    require(
      !variable.isGuarded,
      s"configured variable can't be guarded in a @use rule: $variable"
    )

  def urlSpan: FileSpan = span.withoutInitialAtRule().initialQuoted()

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitUseRule(this)

  override def toString: String = {
    val buffer = new StringBuilder(
      s"@use ${StringExpression.quoteText(url.toString)}"
    )
    val basename =
      if (url.getPath == null || url.getPath.isEmpty) ""
      else {
        val segments = url.getPath.split('/')
        if (segments.isEmpty) "" else segments.last
      }
    val dot       = basename.indexOf(".")
    val defaultNs = basename.substring(0, if (dot == -1) basename.length else dot)
    namespace.fold {
      buffer.append(" as *")
    } { ns =>
      if (ns != defaultNs) buffer.append(s" as $ns")
    }
    if (configuration.nonEmpty) {
      buffer.append(s" with (${configuration.mkString(", ")})")
    }
    buffer.append(";")
    buffer.toString()
  }
}

// ---------------------------------------------------------------------------
// VariableDeclaration
// ---------------------------------------------------------------------------

/** A variable declaration. Defines or sets a variable.
  *
  * @param name
  *   the name of the variable, with underscores converted to hyphens
  * @param expression
  *   the value the variable is being assigned to
  * @param span
  *   the source span
  * @param namespace
  *   the namespace of the variable, or empty
  * @param isGuarded
  *   whether this is a guarded assignment
  * @param isGlobal
  *   whether this is a global assignment
  * @param comment
  *   the comment immediately preceding this declaration
  */
final class VariableDeclaration(
  val name:       String,
  val expression: Expression,
  val span:       FileSpan,
  val namespace:  Nullable[String] = Nullable.empty,
  val isGuarded:  Boolean = false,
  val isGlobal:   Boolean = false,
  var comment:    Nullable[SilentComment] = Nullable.empty
) extends Statement
    with SassDeclaration {
  require(
    namespace.isEmpty || !isGlobal,
    "Other modules' members can't be defined with !global."
  )

  def nameSpan: FileSpan = {
    var s = span
    namespace.foreach { _ =>
      s = s.withoutNamespace()
    }
    s.initialIdentifier(includeLeading = 1)
  }

  def namespaceSpan: Nullable[FileSpan] =
    if (namespace.isEmpty) Nullable.empty
    else Nullable(span.initialIdentifier())

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitVariableDeclaration(this)

  override def toString: String = {
    val buffer = new StringBuilder()
    namespace.foreach(ns => buffer.append(s"$ns."))
    buffer.append(s"$$$name: $expression;")
    buffer.toString()
  }
}

// ---------------------------------------------------------------------------
// WarnRule
// ---------------------------------------------------------------------------

/** A `@warn` rule. Prints a Sass value to warn the user of something.
  *
  * @param expression
  *   the expression to print
  * @param span
  *   the source span
  */
final class WarnRule(
  val expression: Expression,
  val span:       FileSpan
) extends Statement {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitWarnRule(this)

  override def toString: String = s"@warn $expression;"
}

// ---------------------------------------------------------------------------
// WhileRule
// ---------------------------------------------------------------------------

/** A `@while` rule. Repeatedly executes a block of code as long as a statement evaluates to `true`.
  *
  * @param condition
  *   the condition that determines whether the block executes
  * @param span
  *   the source span
  */
final class WhileRule(
  val condition:   Expression,
  childStatements: List[Statement],
  val span:        FileSpan
) extends ParentStatement(Nullable(childStatements)) {

  def accept[T](visitor: StatementVisitor[T]): T =
    visitor.visitWhileRule(this)

  override def toString: String =
    s"@while $condition {${children.get.mkString(" ")}}"
}
