/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/git/gitGraphAst.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: gitGraphAst module functions -> GitDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package git

import ssg.commons.Nullable

import scala.collection.mutable

/** Commit types in the git graph. */
enum CommitType(val label: String) extends java.lang.Enum[CommitType] {
  case Normal extends CommitType("NORMAL")
  case Reverse extends CommitType("REVERSE")
  case Highlight extends CommitType("HIGHLIGHT")
  case Merge extends CommitType("MERGE")
  case CherryPick extends CommitType("CHERRY_PICK")
}

/** A commit in the git graph.
  *
  * @param id
  *   unique commit identifier (hash-like)
  * @param message
  *   commit message (for label display)
  * @param branch
  *   the branch this commit was made on
  * @param parent
  *   parent commit ID (or empty for initial commit)
  * @param secondParent
  *   second parent commit ID (for merge commits)
  * @param commitType
  *   type of commit (normal, merge, cherry-pick, etc.)
  * @param tag
  *   optional tag name
  * @param order
  *   display order
  */
final case class GitCommit(
  id:               String,
  var message:      String = "",
  var branch:       String = "",
  var parent:       Nullable[String] = Nullable.empty,
  var secondParent: Nullable[String] = Nullable.empty,
  var commitType:   CommitType = CommitType.Normal,
  var tag:          Nullable[String] = Nullable.empty,
  var order:        Int = 0
)

/** A branch in the git graph.
  *
  * @param name
  *   branch name
  * @param order
  *   display order
  */
final case class GitBranch(
  name:      String,
  var order: Int = 0
)

/** Mutable database for git graph diagram data.
  *
  * Accumulates commits, branches, and their relationships during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `gitGraphAst.ts`.
  */
final class GitDb {

  val commits:     mutable.LinkedHashMap[String, GitCommit] = mutable.LinkedHashMap.empty
  val branches:    mutable.LinkedHashMap[String, GitBranch] = mutable.LinkedHashMap.empty
  val branchHeads: mutable.LinkedHashMap[String, String]    = mutable.LinkedHashMap.empty

  var currentBranch:  String = "main"
  var direction:      String = "LR"
  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  private var commitCount: Int = 0
  private var branchCount: Int = 0

  /** Initializes the database with a main branch and initial commit. */
  def init(mainBranchName: String = "main"): Unit = {
    currentBranch = mainBranchName
    branches(mainBranchName) = GitBranch(name = mainBranchName, order = 0)

    // Create initial commit
    val initialCommitId = generateCommitId()
    val initialCommit   = GitCommit(
      id = initialCommitId,
      message = "",
      branch = mainBranchName,
      order = commitCount
    )
    commits(initialCommitId) = initialCommit
    branchHeads(mainBranchName) = initialCommitId
  }

  /** Creates a new commit on the current branch.
    *
    * @param message
    *   commit message
    * @param id
    *   optional explicit commit ID
    * @param tag
    *   optional tag
    * @param commitType
    *   type of commit
    */
  def commit(
    message:    String = "",
    id:         Nullable[String] = Nullable.empty,
    tag:        Nullable[String] = Nullable.empty,
    commitType: CommitType = CommitType.Normal
  ): Unit = {
    commitCount += 1
    val commitId = id.getOrElse(generateCommitId())

    val parentId = branchHeads.get(currentBranch)

    val newCommit = GitCommit(
      id = commitId,
      message = if (message.nonEmpty) message else commitId,
      branch = currentBranch,
      parent = parentId match {
        case Some(p) => Nullable(p)
        case None    => Nullable.empty
      },
      commitType = commitType,
      tag = tag,
      order = commitCount
    )

    commits(commitId) = newCommit
    branchHeads(currentBranch) = commitId
  }

  /** Creates a new branch from the current branch.
    *
    * @param name
    *   branch name
    * @param order
    *   display order (-1 for auto)
    */
  def branch(name: String, order: Int = -1): Unit = {
    branchCount += 1
    val branchOrder = if (order >= 0) order else branchCount

    branches(name) = GitBranch(name = name, order = branchOrder)

    // Copy head from current branch
    branchHeads.get(currentBranch).foreach { headId =>
      branchHeads(name) = headId
    }
  }

  /** Switches to an existing branch.
    *
    * @param name
    *   branch name to switch to
    */
  def checkout(name: String): Unit =
    if (branches.contains(name)) {
      currentBranch = name
    }

  /** Merges the specified branch into the current branch.
    *
    * @param otherBranch
    *   the branch to merge from
    * @param message
    *   merge commit message
    * @param id
    *   optional explicit commit ID
    * @param tag
    *   optional tag
    * @param commitType
    *   commit type (usually Merge)
    */
  def merge(
    otherBranch: String,
    message:     String = "",
    id:          Nullable[String] = Nullable.empty,
    tag:         Nullable[String] = Nullable.empty,
    commitType:  CommitType = CommitType.Merge
  ): Unit = {
    commitCount += 1
    val commitId = id.getOrElse(generateCommitId())

    val parentId       = branchHeads.get(currentBranch)
    val secondParentId = branchHeads.get(otherBranch)

    val mergeMsg = if (message.nonEmpty) message else s"Merge $otherBranch into $currentBranch"

    val mergeCommit = GitCommit(
      id = commitId,
      message = mergeMsg,
      branch = currentBranch,
      parent = parentId match {
        case Some(p) => Nullable(p)
        case None    => Nullable.empty
      },
      secondParent = secondParentId match {
        case Some(p) => Nullable(p)
        case None    => Nullable.empty
      },
      commitType = commitType,
      tag = tag,
      order = commitCount
    )

    commits(commitId) = mergeCommit
    branchHeads(currentBranch) = commitId
  }

  /** Cherry-picks a commit to the current branch.
    *
    * @param sourceCommitId
    *   the commit ID to cherry-pick
    * @param tag
    *   optional tag for the cherry-picked commit
    */
  def cherryPick(sourceCommitId: String, tag: Nullable[String] = Nullable.empty): Unit =
    if (commits.contains(sourceCommitId)) {
      commitCount += 1
      val commitId = generateCommitId()

      val parentId = branchHeads.get(currentBranch)

      val cpCommit = GitCommit(
        id = commitId,
        message = s"cherry-pick: $sourceCommitId",
        branch = currentBranch,
        parent = parentId match {
          case Some(p) => Nullable(p)
          case None    => Nullable.empty
        },
        secondParent = Nullable(sourceCommitId),
        commitType = CommitType.CherryPick,
        tag = tag,
        order = commitCount
      )

      commits(commitId) = cpCommit
      branchHeads(currentBranch) = commitId
    }

  /** Generates a short commit ID. */
  private def generateCommitId(): String =
    // Generate a deterministic hash-like ID
    f"${commitCount}%07x"

  /** Returns commits in order. */
  def orderedCommits: Array[GitCommit] =
    commits.values.toArray.sortBy(_.order)

  /** Returns branches in display order. */
  def orderedBranches: Array[GitBranch] =
    branches.values.toArray.sortBy(_.order)

  /** Clears all state for parsing a new diagram. */
  def clear(): Unit = {
    commits.clear()
    branches.clear()
    branchHeads.clear()
    commitCount = 0
    branchCount = 0
    currentBranch = "main"
    direction = "LR"
    title = ""
    accTitle = ""
    accDescription = ""
  }
}
