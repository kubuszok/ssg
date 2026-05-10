/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package git

import munit.FunSuite

final class GitDiagramSuite extends FunSuite {

  test("detect: gitGraph keyword") {
    assert(GitDiagram.detect("gitGraph\n    commit"))
  }

  test("detect: not a git graph") {
    assert(!GitDiagram.detect("graph TD\n    A-->B"))
  }

  test("parse: initial commit exists") {
    val db = GitParser.parse("gitGraph\n    commit")
    assert(db.commits.nonEmpty, "Should have commits")
    // Initial commit + one explicit commit
    assert(db.commits.size >= 2, s"Should have at least 2 commits, got ${db.commits.size}")
  }

  test("parse: branch and checkout") {
    val db = GitParser.parse(
      """gitGraph
        |    commit
        |    branch develop
        |    checkout develop
        |    commit""".stripMargin
    )
    assert(db.branches.contains("main"), "Should have main branch")
    assert(db.branches.contains("develop"), "Should have develop branch")
    assertEquals(db.currentBranch, "develop")
  }

  test("parse: merge") {
    val db = GitParser.parse(
      """gitGraph
        |    commit
        |    branch feature
        |    checkout feature
        |    commit
        |    checkout main
        |    merge feature""".stripMargin
    )
    val mergeCommits = db.commits.values.filter(_.commitType == CommitType.Merge)
    assert(mergeCommits.nonEmpty, "Should have a merge commit")
  }

  test("parse: commit with id and tag") {
    val db = GitParser.parse(
      """gitGraph
        |    commit id: "abc123" tag: "v1.0"
        |    commit""".stripMargin
    )
    assert(db.commits.contains("abc123"), "Should have commit with explicit ID")
    val tagged = db.commits("abc123")
    assertEquals(tagged.tag.getOrElse(""), "v1.0")
  }

  test("parse: commit type highlight") {
    val db = GitParser.parse(
      """gitGraph
        |    commit type: HIGHLIGHT""".stripMargin
    )
    val highlighted = db.commits.values.filter(_.commitType == CommitType.Highlight)
    assert(highlighted.nonEmpty, "Should have a highlighted commit")
  }

  test("parse: ordered commits") {
    val db      = GitParser.parse("gitGraph\n    commit\n    commit\n    commit")
    val ordered = db.orderedCommits
    assert(ordered.length >= 4, s"Should have at least 4 commits (1 initial + 3 explicit)")
    // Verify ordering
    for (i <- 0 until ordered.length - 1)
      assert(ordered(i).order <= ordered(i + 1).order, "Commits should be in order")
  }

  test("render: produces valid SVG") {
    val svg = GitDiagram.render("gitGraph\n    commit\n    commit")
    assert(svg.contains("<svg"), "Should contain <svg tag")
    assert(svg.contains("viewBox"), "Should have viewBox")
  }
}
