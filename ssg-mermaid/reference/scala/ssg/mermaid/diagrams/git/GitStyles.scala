/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/git/styles.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces template literal CSS generation with string interpolation
 *   Idiom: Pure function from theme variables to CSS string
 *   Renames: getStyles() -> GitStyles.generate()
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package git

import ssg.mermaid.theme.ThemeVariables

/** CSS class generation for git graph diagram elements. */
object GitStyles {

  /** Generates CSS rules for all git graph diagram elements.
    *
    * @param vars
    *   theme variables providing color values
    * @return
    *   CSS rules as a string
    */
  def generate(vars: ThemeVariables): String = {
    val sb = new StringBuilder()

    // Commit nodes
    sb.append(
      s""".commit-node {
         |  stroke: ${vars.nodeBorder};
         |  stroke-width: 2px;
         |}
         |""".stripMargin
    )

    // Commit labels
    sb.append(
      s""".commit-label {
         |  fill: ${vars.commitLabelColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: ${vars.commitLabelFontSize};
         |}
         |""".stripMargin
    )

    // Branch labels
    sb.append(
      s""".branch-label {
         |  fill: ${vars.textColor};
         |  font-family: ${vars.fontFamily};
         |  font-weight: bold;
         |}
         |""".stripMargin
    )

    // Branch lines
    sb.append(""".branch-line {
                |  stroke-width: 2;
                |}
                |""".stripMargin)

    // Tag labels
    sb.append(
      s""".tag-label {
         |  fill: ${vars.tagLabelColor};
         |  font-family: ${vars.fontFamily};
         |  font-size: ${vars.tagLabelFontSize};
         |}
         |""".stripMargin
    )

    // Merge lines
    sb.append(""".merge-line {
                |  stroke-dasharray: 5,3;
                |}
                |""".stripMargin)

    // Branch-specific colors (git0 through git7)
    for (i <- 0 until 8) {
      if (vars.git(i).nonEmpty) {
        sb.append(
          s""".branch-$i {
             |  stroke: ${vars.git(i)};
             |  fill: ${vars.git(i)};
             |}
             |""".stripMargin
        )
      }
      if (vars.gitBranchLabel(i).nonEmpty) {
        sb.append(
          s""".branch-label-$i {
             |  fill: ${vars.gitBranchLabel(i)};
             |}
             |""".stripMargin
        )
      }
    }

    sb.toString
  }
}
