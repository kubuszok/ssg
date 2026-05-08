/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/node_package.dart Original: Copyright (c) 2024 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: node_package.dart -> NodePackageImporter.scala (JVM-only)
 *   Convention: Uses ssg-commons FileOps/FilePath for cross-platform I/O.
 *   Idiom: Full port of Node resolution algorithm including package.json exports.
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/importer/node_package.dart
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package importer

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.Nullable.*

import scala.language.implicitConversions

private val ValidExtensions = Set(".scss", ".sass", ".css")

final class NodePackageImporter(val entryPoint: String) extends Importer {

  override def isNonCanonicalScheme(scheme: String): Boolean = scheme == "pkg"

  private val _entryPointDirectory: String = FilePath.of(entryPoint).toAbsolute.normalize.pathString

  def canonicalize(url: String): Nullable[String] = {
    if (url.startsWith("file:")) {
      return new FilesystemImporter(".").canonicalize(url)
    }
    if (!url.startsWith("pkg:")) return Nullable.empty

    val path = url.substring(4)
    if (path.isEmpty)
      throw new IllegalArgumentException("A pkg: URL must not have an empty path.")
    if (path.startsWith("/"))
      throw new IllegalArgumentException("A pkg: URL's path must not begin with /.")
    if (url.contains('?') || url.contains('#'))
      throw new IllegalArgumentException("A pkg: URL must not have a query or fragment.")

    val (packageName, subpath) = _packageNameAndSubpath(path)

    if (packageName.startsWith(".") || packageName.contains("\\") || packageName.contains("%") ||
      (packageName.startsWith("@") && !packageName.contains("/"))) {
      return Nullable.empty
    }

    val packageRoot = _resolvePackageRoot(packageName, _entryPointDirectory)
    if (packageRoot == null) return Nullable.empty

    val jsonPath = FilePath.of(packageRoot).resolve("package.json")
    if (!FileOps.isRegularFile(jsonPath)) return Nullable.empty

    val jsonString =
      try FileOps.readString(jsonPath)
      catch { case _: Throwable => return Nullable.empty }
    val manifest =
      try JsonValue.parse(jsonString)
      catch { case e: Exception => throw new IllegalArgumentException(s"Failed to parse $jsonPath for \"pkg:$packageName\": ${e.getMessage}") }
    val manifestMap = manifest match {
      case JsonValue.Obj(m) => m
      case _                => return Nullable.empty
    }

    _resolvePackageExports(packageRoot, subpath, manifestMap, packageName) match {
      case Some(resolved) =>
        val ext = {
          val dot = resolved.lastIndexOf('.')
          if (dot >= 0) resolved.substring(dot) else ""
        }
        if (ValidExtensions.contains(ext)) {
          return Nullable(_toFileUri(resolved))
        } else {
          throw new IllegalArgumentException(
            s"The export for '${subpath.getOrElse("root")}' in '$packageName' resolved to '$resolved', " +
              "which is not a '.scss', '.sass', or '.css' file."
          )
        }
      case None => ()
    }

    if (subpath.isEmpty) {
      _resolvePackageRootValues(packageRoot, manifestMap) match {
        case Some(p) => return Nullable(_toFileUri(p))
        case None    => return Nullable.empty
      }
    }

    val subpathInRoot = FilePath.of(packageRoot).resolve(subpath.get).pathString
    new FilesystemImporter(packageRoot).canonicalize(subpathInRoot)
  }

  def load(url: String): Nullable[ImporterResult] =
    try {
      val path =
        if (url.startsWith("file:")) ssg.commons.io.FilePathPlatform.fromNioPath(java.nio.file.Paths.get(new java.net.URI(url)))
        else FilePath.of(url)
      if (!FileOps.exists(path) || !FileOps.isRegularFile(path)) Nullable.empty
      else {
        val contents = FileOps.readString(path)
        val pathStr  = path.pathString
        val syntax =
          if (pathStr.endsWith(".sass")) Syntax.Sass
          else if (pathStr.endsWith(".css")) Syntax.Css
          else Syntax.Scss
        Nullable(ImporterResult(contents, syntax))
      }
    } catch { case _: Throwable => Nullable.empty }

  override def toString: String = s"NodePackageImporter($entryPoint)"

  // ---------------------------------------------------------------------------
  // Private: resolution algorithm
  // ---------------------------------------------------------------------------

  private def _toFileUri(path: String): String = {
    val normalized = FilePath.of(path).toAbsolute.normalize.pathString
    "file://" + (if (normalized.startsWith("/")) normalized else "/" + normalized)
  }

  private def _packageNameAndSubpath(specifier: String): (String, Option[String]) = {
    val parts = specifier.split('/').toList
    val (name, rest) =
      if (parts.head.startsWith("@") && parts.length >= 2)
        (parts.head + "/" + parts(1), parts.drop(2))
      else
        (parts.head, parts.tail)
    (name, if (rest.isEmpty) None else Some(rest.mkString("/")))
  }

  private def _resolvePackageRoot(packageName: String, baseDirectory: String): String | Null = {
    var dir = baseDirectory
    while (true) {
      val candidate = FilePath.of(dir).resolve("node_modules").resolve(packageName)
      if (FileOps.isDirectory(candidate)) return candidate.pathString
      val parent = FilePath.of(dir).parent
      if (parent.isEmpty) return null
      val parentStr = parent.get.pathString
      if (parentStr == dir) return null
      dir = parentStr
    }
    null
  }

  private def _resolvePackageRootValues(
    packageRoot: String,
    manifest:    Map[String, JsonValue]
  ): Option[String] = {
    manifest.get("sass").flatMap(_.asString).filter(v => ValidExtensions.exists(v.endsWith)).map { v =>
      FilePath.of(packageRoot).resolve(v).pathString
    }.orElse {
      manifest.get("style").flatMap(_.asString).filter(v => ValidExtensions.exists(v.endsWith)).map { v =>
        FilePath.of(packageRoot).resolve(v).pathString
      }
    }.orElse {
      ImporterFileUtils.resolveImportPath(FilePath.of(packageRoot).resolve("index").pathString).toOption
    }
  }

  private def _resolvePackageExports(
    packageRoot: String,
    subpath:     Option[String],
    manifest:    Map[String, JsonValue],
    packageName: String
  ): Option[String] = {
    val exports = manifest.get("exports") match {
      case Some(v) if v != JsonValue.Null => v
      case _                              => return None
    }

    val subpathVariants = _exportsToCheck(subpath, addIndex = false)
    _nodePackageExportsResolve(packageRoot, subpathVariants, exports, subpath, packageName) match {
      case Some(p) => return Some(p)
      case None    => ()
    }

    if (subpath.isDefined && {
          val sp  = subpath.get
          val dot = sp.lastIndexOf('.')
          dot >= 0 && ValidExtensions.contains(sp.substring(dot))
        }) return None

    val indexVariants = _exportsToCheck(subpath, addIndex = true)
    _nodePackageExportsResolve(packageRoot, indexVariants, exports, subpath, packageName)
  }

  private def _nodePackageExportsResolve(
    packageRoot:    String,
    subpathVariants: List[Option[String]],
    exports:        JsonValue,
    subpath:        Option[String],
    packageName:    String
  ): Option[String] = {
    exports match {
      case JsonValue.Obj(map) =>
        val hasPaths      = map.keys.exists(_.startsWith("."))
        val hasConditions  = map.keys.exists(!_.startsWith("."))
        if (hasPaths && hasConditions)
          throw new IllegalArgumentException(
            s"`exports` in $packageName can not have both conditions and paths at the same level.\n" +
              s"Found ${map.keys.map(k => s"\"$k\"").mkString(",")} in " +
              s"${FilePath.of(packageRoot).resolve("package.json").pathString}."
          )
      case _ => ()
    }

    val matches = subpathVariants.flatMap { variant =>
      if (variant.isEmpty) {
        _getMainExport(exports).flatMap(main => _packageTargetResolve(variant, main, packageRoot, None))
      } else {
        exports match {
          case JsonValue.Obj(map) if map.keys.exists(_.startsWith(".")) =>
            val matchKey = "./" + variant.get.replace('\\', '/')
            if (map.contains(matchKey) && !matchKey.contains('*')) {
              map(matchKey) match {
                case JsonValue.Null => None
                case target         => _packageTargetResolve(variant, target, packageRoot, None)
              }
            } else {
              val expansionKeys = map.keys.filter(k => k.count(_ == '*') == 1).toList.sortWith(_compareExpansionKeys)
              expansionKeys.view.flatMap { expansionKey =>
                val Array(patternBase, patternTrailer) = expansionKey.split("\\*", 2)
                if (!matchKey.startsWith(patternBase) || matchKey == patternBase) None
                else if (patternTrailer.isEmpty || (matchKey.endsWith(patternTrailer) && matchKey.length >= expansionKey.length)) {
                  map.get(expansionKey).flatMap {
                    case JsonValue.Null => None
                    case target =>
                      val patternMatch = matchKey.substring(patternBase.length, matchKey.length - patternTrailer.length)
                      _packageTargetResolve(variant, target, packageRoot, Some(patternMatch))
                  }
                } else None
              }.headOption
            }
          case _ => None
        }
      }
    }.distinct

    matches match {
      case List(single) => Some(single)
      case Nil          => None
      case multiple     =>
        throw new IllegalArgumentException(
          s"Unable to determine which of multiple potential resolutions found for ${subpath.getOrElse("root")} in $packageName should be used.\n\nFound:\n${multiple.mkString("\n")}"
        )
    }
  }

  private def _compareExpansionKeys(keyA: String, keyB: String): Boolean = {
    val baseLengthA = if (keyA.contains('*')) keyA.indexOf('*') + 1 else keyA.length
    val baseLengthB = if (keyB.contains('*')) keyB.indexOf('*') + 1 else keyB.length
    if (baseLengthA != baseLengthB) return baseLengthA > baseLengthB
    if (!keyA.contains("*")) return false
    if (!keyB.contains("*")) return true
    keyA.length > keyB.length
  }

  private def _packageTargetResolve(
    subpath:      Option[String],
    exports:      JsonValue,
    packageRoot:  String,
    patternMatch: Option[String]
  ): Option[String] = exports match {
    case JsonValue.Str(s) if !s.startsWith("./") =>
      throw new IllegalArgumentException(s"Export '$s' must be a path relative to the package root at '$packageRoot'.")
    case JsonValue.Str(s) if patternMatch.isDefined =>
      val replaced = s.replaceFirst("\\*", patternMatch.get)
      val path = FilePath.of(packageRoot).resolve(replaced).normalize.pathString
      if (FileOps.exists(FilePath.of(path))) Some(path) else None
    case JsonValue.Str(s) =>
      Some(FilePath.of(packageRoot).resolve(s).pathString)
    case JsonValue.Obj(map) =>
      map.view.flatMap { case (key, value) =>
        if (!Set("sass", "style", "default").contains(key)) None
        else value match {
          case JsonValue.Null => None
          case v              => _packageTargetResolve(subpath, v, packageRoot, patternMatch)
        }
      }.headOption
    case JsonValue.Arr(Nil) => None
    case JsonValue.Arr(arr) =>
      arr.view.flatMap {
        case JsonValue.Null => None
        case v              => _packageTargetResolve(subpath, v, packageRoot, patternMatch)
      }.headOption
    case JsonValue.Null => None
    case other =>
      throw new IllegalArgumentException(s"Invalid 'exports' value $other in ${FilePath.of(packageRoot).resolve("package.json").pathString}.")
  }

  private def _getMainExport(exports: JsonValue): Option[JsonValue] = exports match {
    case s: JsonValue.Str      => Some(s)
    case a: JsonValue.Arr      => Some(a)
    case JsonValue.Obj(map) if !map.keys.exists(_.startsWith(".")) => Some(exports)
    case JsonValue.Obj(map)    => map.get(".").filter(_ != JsonValue.Null)
    case _                     => None
  }

  private def _exportsToCheck(subpath: Option[String], addIndex: Boolean): List[Option[String]] = {
    val effective =
      if (subpath.isEmpty && addIndex) Some("index")
      else if (subpath.isDefined && addIndex) Some(subpath.get + "/index")
      else subpath
    if (effective.isEmpty) return List(None)

    val sp    = effective.get
    val ext   = { val dot = sp.lastIndexOf('.'); if (dot >= 0) sp.substring(dot) else "" }
    val paths =
      if (ValidExtensions.contains(ext)) List(sp)
      else List(sp, sp + ".scss", sp + ".sass", sp + ".css")

    val basename = sp.substring(sp.lastIndexOf('/') + 1)
    if (basename.startsWith("_")) paths.map(Some(_))
    else {
      val withPartials = paths.flatMap { p =>
        val lastSlash = p.lastIndexOf('/')
        val partial   = if (lastSlash < 0) "_" + p else p.substring(0, lastSlash + 1) + "_" + p.substring(lastSlash + 1)
        List(p, partial)
      }
      (paths ::: withPartials.filter(!paths.contains(_))).map(Some(_))
    }
  }
}

/** Minimal JSON value type for package.json parsing. */
private[importer] enum JsonValue {
  case Str(value: String)
  case Num(value: Double)
  case Bool(value: Boolean)
  case Null
  case Arr(values: List[JsonValue])
  case Obj(fields: Map[String, JsonValue])

  def asString: Option[String] = this match {
    case Str(s) => Some(s)
    case _      => None
  }
}

private[importer] object JsonValue {

  def parse(json: String): JsonValue = {
    var pos = 0
    def peek(): Char = if (pos < json.length) json.charAt(pos) else 0
    def advance(): Char = { val c = json.charAt(pos); pos += 1; c }
    def skipWs(): Unit = while (pos < json.length && json.charAt(pos).isWhitespace) pos += 1

    def parseValue(): JsonValue = {
      skipWs()
      peek() match {
        case '"'                            => parseString()
        case '{'                            => parseObject()
        case '['                            => parseArray()
        case 't'                            => expect("true"); Bool(true)
        case 'f'                            => expect("false"); Bool(false)
        case 'n'                            => expect("null"); Null
        case c if c == '-' || c.isDigit     => parseNumber()
        case c                              => throw new IllegalArgumentException(s"Unexpected char '$c' at position $pos")
      }
    }

    def expect(s: String): Unit = {
      var i = 0
      while (i < s.length) {
        if (pos >= json.length || json.charAt(pos) != s.charAt(i))
          throw new IllegalArgumentException(s"Expected '$s' at position $pos")
        pos += 1; i += 1
      }
    }

    def parseString(): JsonValue.Str = {
      advance() // skip opening "
      val sb = new StringBuilder()
      while (peek() != '"') {
        val c = advance()
        if (c == '\\') {
          advance() match {
            case '"'  => sb.append('"')
            case '\\' => sb.append('\\')
            case '/'  => sb.append('/')
            case 'b'  => sb.append('\b')
            case 'f'  => sb.append('\f')
            case 'n'  => sb.append('\n')
            case 'r'  => sb.append('\r')
            case 't'  => sb.append('\t')
            case 'u'  =>
              val hex = json.substring(pos, pos + 4); pos += 4
              sb.append(Integer.parseInt(hex, 16).toChar)
            case other => sb.append(other)
          }
        } else sb.append(c)
      }
      advance() // skip closing "
      Str(sb.toString())
    }

    def parseNumber(): JsonValue.Num = {
      val start = pos
      if (peek() == '-') pos += 1
      while (pos < json.length && json.charAt(pos).isDigit) pos += 1
      if (pos < json.length && json.charAt(pos) == '.') { pos += 1; while (pos < json.length && json.charAt(pos).isDigit) pos += 1 }
      if (pos < json.length && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) { pos += 1; if (pos < json.length && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) pos += 1; while (pos < json.length && json.charAt(pos).isDigit) pos += 1 }
      Num(json.substring(start, pos).toDouble)
    }

    def parseObject(): JsonValue.Obj = {
      advance() // skip {
      skipWs()
      val fields = scala.collection.mutable.LinkedHashMap.empty[String, JsonValue]
      if (peek() != '}') {
        var more = true
        while (more) {
          skipWs()
          val key = parseString().value
          skipWs(); advance() // skip :
          skipWs()
          fields(key) = parseValue()
          skipWs()
          if (peek() == ',') { advance(); more = true } else more = false
        }
      }
      advance() // skip }
      Obj(fields.toMap)
    }

    def parseArray(): JsonValue.Arr = {
      advance() // skip [
      skipWs()
      val items = scala.collection.mutable.ListBuffer.empty[JsonValue]
      if (peek() != ']') {
        var more = true
        while (more) {
          skipWs()
          items += parseValue()
          skipWs()
          if (peek() == ',') { advance(); more = true } else more = false
        }
      }
      advance() // skip ]
      Arr(items.toList)
    }

    val result = parseValue()
    result
  }
}
