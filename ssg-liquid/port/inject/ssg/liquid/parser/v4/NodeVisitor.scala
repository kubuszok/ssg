/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/parser/v4/NodeVisitor.java
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: java walks an ANTLR parse tree here and builds the LNode AST from it. The
 *     replacement lexer and parser (ssg.liquid.parser) build that AST while they parse, so
 *     there is no second tree to walk and this visitor has nothing left to do.
 *   Convention: java's own constructor arguments and `visit` are kept, so `Template` ports
 *     mechanically; the registries reach the parser through `Template.parse` instead.
 */
package ssg.liquid.parser.v4

/** What is left of java's parse-tree visitor once the parser builds the AST itself. */
final class NodeVisitor(
  val insertions:         ssg.liquid.Insertions,
  val filters:            ssg.liquid.filters.Filters,
  val liquidStyleInclude: Boolean
) {

  /** The AST the parser already built. */
  def visit(tree: ssg.liquid.nodes.LNode): ssg.liquid.nodes.LNode = tree
}
