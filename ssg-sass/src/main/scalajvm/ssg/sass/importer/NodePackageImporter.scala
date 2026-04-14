/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/node_package.dart
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: node_package.dart -> NodePackageImporter.scala (JVM-only)
 *   Convention: Uses ssg-commons FileOps/FilePath for cross-platform I/O.
 *   Idiom: Walks upward from `entryPoint` to find a `node_modules/<pkg>`
 *          directory and resolves `pkg:` URLs through it. Uses a small
 *          hand-written JSON scanner to extract `sass` / `style` / `main`
 *          string fields from `package.json` (no full JSON parser
 *          dependency).
 */
package ssg
package sass
package importer

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** A Node-style `pkg:` URL importer.
  *
  * Given a root path, walks upward to find an enclosing `node_modules` directory. URLs of the form `pkg:name[/rest]` resolve to `node_modules/name/...`. Scoped packages (`pkg:@scope/name[/rest]`) are
  * supported.
  *
  *   - `pkg:name` (no rest): reads `node_modules/name/package.json` and uses the first of `sass`, `style`, or `main` fields as the entry point.
  *   - `pkg:name/rest`: resolves `rest` against `node_modules/name/` using the standard SCSS file resolution rules (partials, `.scss`/`.sass`/ `.css`, `_index.scss`).
  *
  * JVM-only.
  */
final class NodePackageImporter(val entryPoint: String) extends Importer {

  private val Prefix = "pkg:"
  private val rootPath: FilePath = FilePath.of(entryPoint).toAbsolute.normalize

  /** Find the nearest `node_modules/<pkg>` directory walking upward from the entry point. Returns `Nullable.empty` if not found.
    */
  private def findPackageRoot(pkg: String): Nullable[FilePath] = {
    val start: Nullable[FilePath] =
      if (FileOps.isDirectory(rootPath)) Nullable(rootPath)
      else Nullable.fromOption(rootPath.parent)
    var dir:    Nullable[FilePath] = start
    var result: Nullable[FilePath] = Nullable.empty
    while (result.isEmpty && dir.isDefined) {
      val d         = dir.get
      val candidate = d.resolve("node_modules").resolve(pkg)
      if (FileOps.isDirectory(candidate)) {
        result = Nullable(candidate.normalize)
      }
      dir = Nullable.fromOption(d.parent)
    }
    result
  }

  /** Tiny extractor for a top-level string field in a package.json file. Returns the first string value associated with `key` at any nesting depth (which, for the limited keys we look up, is fine —
    * `sass`, `style`, `main` are conventionally top-level). Returns `Nullable.empty` if not found.
    */
  private def readStringField(json: String, key: String): Nullable[String] = {
    val needle = "\"" + key + "\""
    var idx    = json.indexOf(needle)
    var result: Nullable[String] = Nullable.empty
    while (result.isEmpty && idx >= 0) {
      // Skip past the key and find the colon.
      var i = idx + needle.length
      while (i < json.length && json.charAt(i).isWhitespace) i += 1
      if (i < json.length && json.charAt(i) == ':') {
        i += 1
        while (i < json.length && json.charAt(i).isWhitespace) i += 1
        if (i < json.length && json.charAt(i) == '"') {
          // Read string contents, honoring backslash escapes.
          val sb = new StringBuilder
          i += 1
          var done = false
          while (!done && i < json.length) {
            val c = json.charAt(i)
            if (c == '\\' && i + 1 < json.length) {
              val n = json.charAt(i + 1)
              n match {
                case '"'  => sb.append('"')
                case '\\' => sb.append('\\')
                case '/'  => sb.append('/')
                case 'n'  => sb.append('\n')
                case 't'  => sb.append('\t')
                case 'r'  => sb.append('\r')
                case _    => sb.append(n)
              }
              i += 2
            } else if (c == '"') {
              done = true
              i += 1
            } else {
              sb.append(c)
              i += 1
            }
          }
          result = Nullable(sb.toString)
        }
      }
      if (result.isEmpty) {
        idx = json.indexOf(needle, idx + needle.length)
      }
    }
    result
  }

  /** Read the entry-point file path for a package: try `sass`, then `style`, then `main`. Returns the resolved path if the package.json exists.
    */
  private def readEntryPoint(pkgRoot: FilePath): Nullable[String] = {
    val pj = pkgRoot.resolve("package.json")
    if (!FileOps.isRegularFile(pj)) Nullable.empty
    else {
      try {
        val json  = FileOps.readString(pj)
        val field =
          readStringField(json, "sass").orElse(readStringField(json, "style")).orElse(readStringField(json, "main"))
        field
      } catch {
        case _: Throwable => Nullable.empty
      }
    }
  }

  /** Parse `pkg:name[/rest]`, supporting scoped `@scope/name`. Returns `(packageName, rest)`.
    */
  private def parsePkgUrl(url: String): Nullable[(String, String)] =
    if (!url.startsWith(Prefix)) Nullable.empty
    else {
      val rest = url.substring(Prefix.length)
      if (rest.isEmpty) Nullable.empty
      else if (rest.startsWith("@")) {
        // Scoped: @scope/name[/rest]
        val firstSlash = rest.indexOf('/')
        if (firstSlash < 0) Nullable.empty
        else {
          val secondSlash = rest.indexOf('/', firstSlash + 1)
          if (secondSlash < 0) Nullable((rest, ""))
          else Nullable((rest.substring(0, secondSlash), rest.substring(secondSlash + 1)))
        }
      } else {
        val slash = rest.indexOf('/')
        if (slash < 0) Nullable((rest, ""))
        else Nullable((rest.substring(0, slash), rest.substring(slash + 1)))
      }
    }

  def canonicalize(url: String): Nullable[String] = {
    val parsed = parsePkgUrl(url)
    if (parsed.isEmpty) Nullable.empty
    else {
      val (pkg, rest) = parsed.get
      val pkgRoot     = findPackageRoot(pkg)
      if (pkgRoot.isEmpty) Nullable.empty
      else {
        val root          = pkgRoot.get
        val effectiveRest =
          if (rest.nonEmpty) Nullable(rest)
          else readEntryPoint(root)
        if (effectiveRest.isEmpty) {
          // Fall back to looking for an index file at the package root.
          val fs = new FilesystemImporter(root.pathString)
          fs.canonicalize("")
        } else {
          val fs = new FilesystemImporter(root.pathString)
          fs.canonicalize(effectiveRest.get)
        }
      }
    }
  }

  def load(url: String): Nullable[ImporterResult] =
    try {
      val path: FilePath = {
        val uri = java.net.URI.create(url)
        if (uri.getScheme == "file") FilePath.of(uri.getPath) else FilePath.of(url)
      }
      if (!FileOps.exists(path) || !FileOps.isRegularFile(path)) {
        Nullable.empty
      } else {
        val contents = FileOps.readString(path)
        val pathStr  = path.pathString
        val syntax   =
          if (pathStr.endsWith(".sass")) Syntax.Sass
          else if (pathStr.endsWith(".css")) Syntax.Css
          else Syntax.Scss
        Nullable(ImporterResult(contents, syntax))
      }
    } catch {
      case _: Throwable => Nullable.empty
    }

  override def toString: String = s"NodePackageImporter($entryPoint)"
}
