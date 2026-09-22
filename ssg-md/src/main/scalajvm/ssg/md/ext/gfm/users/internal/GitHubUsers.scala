/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: a GitHub user mention is matched with flexmark's own pattern, whose lookahead this engine
 * has. Scala Native's RE2 has none and matches the same language in code at the same name. */
package ssg
package md
package ext
package gfm
package users
package internal

object GitHubUsers {

  def matchWithGroups(inlineParser: ssg.md.parser.LightInlineParser): Array[ssg.md.util.sequence.BasedSequence] =
    inlineParser.matchWithGroups(GfmUsersInlineParserExtension.GITHUB_USER)
}
