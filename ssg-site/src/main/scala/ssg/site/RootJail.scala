/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Root-jail utilities for the site pipeline (ISS-1211, ISS-1020).
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 6 "Root-jail
 * decision (ties to ISS-1020)" for design.
 *
 * All path resolution in the pipeline — include resolution, layout file
 * lookup, and output path derivation — is jailed to the configured
 * source root (inputs) or destination root (outputs). After
 * resolve(...).normalize.toAbsolute, the resolved absolute path string
 * must start with the jailed root's absolute path + separator (or equal
 * it); if it escapes, the resolution is rejected via a BuildDiagnostic
 * and the build continues.
 *
 * The separator-boundary check prevents false positives where a path
 * like /srcfoo matches root /src — we require that the resolved path
 * either equals the root exactly or is followed by "/".
 *
 * Root-jail + hardening: ISS-1211.
 */
package ssg
package site

import ssg.commons.io.FilePath
import ssg.liquid.antlr.NameResolver

/** Root-jail utilities: checks that resolved paths stay under a jailed root.
  *
  * The jail predicate is: `resolvedAbs.pathString.startsWith(rootAbs.pathString + "/") || resolvedAbs.pathString == rootAbs.pathString` — i.e. the resolved absolute path is either the root itself or
  * a descendant of it (with a separator boundary to avoid matching `/srcfoo` against root `/src`).
  *
  * Sound on all 3 platforms: JVM, JS, and Native all use POSIX "/" as the path separator for `pathString` (JVM wraps java.nio which uses OS-aware separators but Unix-targets use "/"; JS uses Node.js
  * POSIX flavor explicitly; Native delegates to java.nio).
  */
object RootJail {

  /** The path separator used in `pathString` on all platforms.
    *
    * All three platforms (JVM on Unix, Scala.js with Node POSIX, Scala Native via java.nio) produce "/" as the separator in `FilePath.pathString`.
    */
  private val Separator: String = "/"

  /** Checks whether a resolved absolute path stays under the given jailed root.
    *
    * @param resolvedAbs
    *   the resolved path, already normalized and made absolute
    * @param rootAbs
    *   the jail root, already normalized and made absolute
    * @return
    *   true if the resolved path is the root itself or a descendant of it (with separator boundary)
    */
  def isUnderRoot(resolvedAbs: FilePath, rootAbs: FilePath): Boolean = {
    val resolvedStr = resolvedAbs.pathString
    val rootStr     = rootAbs.pathString
    // Either the path equals the root exactly, or is a descendant
    // (starts with root + separator). The separator boundary check
    // prevents false positives like /srcfoo matching root /src.
    resolvedStr == rootStr || resolvedStr.startsWith(rootStr + Separator)
  }

  /** A jailing [[NameResolver]] wrapper that checks resolved include paths stay under the source root.
    *
    * Wraps a delegate `NameResolver` (typically `LocalFSNameResolver`). **Before** delegating, this wrapper pre-computes the resolved path and verifies it stays under the jailed root. This
    * pre-checking is critical: the delegate may call `FileOps.readString`, which would read a file outside the jail before the post-check could reject it. By pre-checking the path, the wrapper
    * prevents the file from being read at all.
    *
    * The pre-check reproduces `LocalFSNameResolver`'s resolution logic: absolute names are used directly; relative names are resolved under `includesRoot` with a `.liquid` default extension if the
    * name has no dot. This coupling to the delegate's logic is intentional — the jail must know the resolved path before the delegate reads the file.
    *
    * When `include_relative` lands (ISS-1214), it will use a different base dir but the same jail check — the `JailedNameResolver` wrapper is the natural integration point.
    *
    * @param delegate
    *   the underlying NameResolver to wrap (e.g. `LocalFSNameResolver`)
    * @param includesRootAbs
    *   the absolute normalized includes directory (e.g. `<source>/_includes/`)
    * @param sourceRootAbs
    *   the absolute normalized source root to jail against
    */
  final class JailedNameResolver(
    private val delegate:        NameResolver,
    private val includesRootAbs: FilePath,
    private val sourceRootAbs:   FilePath
  ) extends NameResolver {

    /** Default extension appended when the name has no dot, matching `LocalFSNameResolver.DEFAULT_EXTENSION`.
      */
    private val DefaultExtension: String = ".liquid"

    override def resolve(name: String): NameResolver.ResolvedSource = {
      // Pre-compute the resolved path the same way LocalFSNameResolver does,
      // then check the jail BEFORE delegating (so the file is never read if
      // it escapes the root).
      val preResolvedPath: FilePath = {
        val directPath = FilePath.of(name)
        if (directPath.isAbsolute) {
          directPath.toAbsolute.normalize
        } else {
          val extension    = if (name.indexOf('.') > 0) "" else DefaultExtension
          val resolvedName = name + extension
          includesRootAbs.resolve(resolvedName).toAbsolute.normalize
        }
      }

      if (!isUnderRoot(preResolvedPath, sourceRootAbs)) {
        throw new RootJailViolationException(
          preResolvedPath,
          sourceRootAbs,
          s"Include path '${name}' resolves to '${preResolvedPath.pathString}' which is outside the source root '${sourceRootAbs.pathString}'"
        )
      }

      // Path is within the jail; delegate to the actual resolver.
      delegate.resolve(name)
    }
  }

  /** Thrown when a resolved path escapes the jailed root.
    *
    * Caught by the pipeline and converted to a `BuildDiagnostic` with the appropriate stage and severity Error.
    */
  final class RootJailViolationException(
    val resolvedPath: FilePath,
    val jailRoot:     FilePath,
    message:          String
  ) extends RuntimeException(message)

  /** Checks that a layout file path stays under the source root.
    *
    * @param layoutPath
    *   the resolved layout file path
    * @param sourceRootAbs
    *   the absolute normalized source root
    * @throws RootJailViolationException
    *   if the path escapes the root
    */
  def checkLayoutPath(layoutPath: FilePath, sourceRootAbs: FilePath): Unit = {
    val resolvedAbs = layoutPath.toAbsolute.normalize
    if (!isUnderRoot(resolvedAbs, sourceRootAbs)) {
      throw new RootJailViolationException(
        resolvedAbs,
        sourceRootAbs,
        s"Layout path '${resolvedAbs.pathString}' is outside the source root '${sourceRootAbs.pathString}'"
      )
    }
  }

  /** Checks that an output file path stays under the destination root.
    *
    * @param outputPath
    *   the resolved output file path
    * @param destRootAbs
    *   the absolute normalized destination root
    * @throws RootJailViolationException
    *   if the path escapes the root
    */
  def checkOutputPath(outputPath: FilePath, destRootAbs: FilePath): Unit = {
    val resolvedAbs = outputPath.toAbsolute.normalize
    if (!isUnderRoot(resolvedAbs, destRootAbs)) {
      throw new RootJailViolationException(
        resolvedAbs,
        destRootAbs,
        s"Output path '${resolvedAbs.pathString}' is outside the destination root '${destRootAbs.pathString}'"
      )
    }
  }
}
