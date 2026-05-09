/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provides a single function for parsing an expression using a Parser
 * TODO(emily): Remove this
 *
 * Original source: katex src/parseTree.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: parseTree -> ParseTree.parseTree
 *   Convention: top-level function -> object method
 *   Idiom: delete from map -> map.remove
 */
package ssg
package katex
package parse

import ssg.commons.Nullable
import ssg.katex.{ParseError, Settings, Token}

/**
 * Provides a single function for parsing an expression using a Parser.
 */
object ParseTree {

  /**
   * Parses an expression using a Parser, then returns the parsed result.
   */
  def parseTree(
      toParse: String,
      settings: Settings
  ): Array[AnyParseNode] = {
    val parser = new Parser(toParse, settings)

    // Blank out any \df@tag to avoid spurious "Duplicate \tag" errors
    parser.gullet.macros.current.remove("\\df@tag")

    var tree = parser.parse()

    // Prevent a color definition from persisting between calls to katex.render().
    parser.gullet.macros.current.remove("\\current@color")
    parser.gullet.macros.current.remove("\\color")

    // If the input used \tag, it will set the \df@tag macro to the tag.
    // In this case, we separately parse the tag and wrap the tree.
    if (parser.gullet.macros.get("\\df@tag").isDefined) {
      if (!settings.displayMode) {
        throw new ParseError("\\tag works only in display equations")
      }
      tree = Array(ParseNodeTag(
        mode = Mode.Text,
        body = tree,
        tag = parser.subparse(Array(new Token("\\df@tag")))
      ))
    }

    tree
  }
}
