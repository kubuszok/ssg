/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Top-level minification facade — dispatches to type-specific minifiers.
 *
 * Original source: jekyll-minifier lib/jekyll-minifier.rb
 * Original author: DigitalSparky
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: Jekyll::Compressor → ssg.minify.Minifier
 *   Convention: Stateless facade, pure functions
 *   Idiom: Delegates to HtmlMinifier, CssMinifier, JsMinifier, JsonMinifier
 *
 * Covenant: full-port
 * Covenant-ruby-reference: lib/jekyll-minifier.rb
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 5422b3570321668b419ec8271391a029f385c390
 */
package ssg
package minify

import ssg.minify.css.CssMinifier
import ssg.minify.js.JsMinifier as BasicJsMinifier
import ssg.minify.json.JsonMinifier
import ssg.minify.html.HtmlMinifier
import ssg.commons.DiagResult

/** Top-level minification facade.
  *
  * By default uses the basic JS minifier (comment/whitespace removal). For full AST-based JS minification, pass `ssg.js.TerserJsCompressor` as the `jsCompressor` parameter.
  */
object Minifier {

  /** Minify HTML content. */
  def minifyHtml(input: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    HtmlMinifier.minify(input, options.html)

  /** Minify HTML content with a custom JS compressor. */
  def minifyHtml(input: String, options: MinifyOptions, jsCompressor: JsCompressor): String =
    HtmlMinifier.minify(input, options.html, jsCompressor, options.jsCompressorOpts)

  /** Minify CSS content. */
  def minifyCss(input: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    CssMinifier.minify(input, options.css)

  /** Minify JavaScript content (basic: comment/whitespace removal). For full minification use ssg.js.TerserJsCompressor. */
  def minifyJs(input: String, options: MinifyOptions = MinifyOptions.Defaults): String =
    BasicJsMinifier.minify(input, options.js)

  /** Minify JSON content. */
  def minifyJson(input: String): String =
    JsonMinifier.minify(input)

  /** Minify content based on file type.
    *
    * Respects file-type toggles (compressCss, compressJs, compressJson) — returns input unchanged if the toggle is off.
    */
  def minify(
    input:        String,
    fileType:     FileType,
    options:      MinifyOptions = MinifyOptions.Defaults,
    jsCompressor: JsCompressor = BasicJsMinifier
  ): String =
    fileType match {
      case FileType.Html => HtmlMinifier.minify(input, options.html, jsCompressor, options.jsCompressorOpts)
      case FileType.Xml  => HtmlMinifier.minify(input, options.html, jsCompressor, options.jsCompressorOpts)
      case FileType.Css  => if (options.compressCss) CssMinifier.minify(input, options.css) else input
      case FileType.Js   => if (options.compressJs) options.jsCompressorOpts.fold(jsCompressor.compress(input))(opts => jsCompressor.compress(input, opts)) else input
      case FileType.Json => if (options.compressJson) JsonMinifier.minify(input) else input
    }

  /** Minify content based on file path, respecting exclude patterns and file-type toggles. */
  def minifyFile(
    input:        String,
    filePath:     String,
    options:      MinifyOptions = MinifyOptions.Defaults,
    jsCompressor: JsCompressor = BasicJsMinifier
  ): String =
    // jekyll-minifier.rb:1091-1093 exclude?(dest, dest_path): exclude.any? { |e| e == file_name || File.fnmatch(e, file_name) }
    // `filePath` is the destination-RELATIVE path (the Ruby `file_name`). Match each pattern either
    // exactly (the `e == file_name` arm) or via Ruby's flag-less File.fnmatch (the fnmatch arm).
    if (options.exclude.exists(pattern => pattern == filePath || fnmatch(pattern, filePath))) input
    else {
      // jekyll-minifier.rb:976-990 output_js_or_file / output_css_or_file and rb:1174-1188
      // process_js_file / process_css_file: a `.min.js` / `.min.css` destination path is copied
      // through untouched (output_file/copy_file) BEFORE any compression dispatch. `.min.json`
      // has no such guard (rb:967-968 / rb:1163-1164 dispatch `.json` straight to output_json).
      if (filePath.endsWith(".min.js") || filePath.endsWith(".min.css")) input
      else {
        fileTypeFromPath(filePath) match {
          case Some(ft) => minify(input, ft, options, jsCompressor)
          case None     => input
        }
      }
    }

  /** Minify content based on file path, returning a diagnostics envelope (ISS-1376).
    *
    * Additive facade over [[minifyFile]] per docs/architecture/error-contracts.md section 2.4, letting ssg-site consume one envelope for all file types. The produced value matches [[minifyFile]]
    * exactly; HTML/XML routes through [[ssg.minify.html.HtmlMinifier.minifyResult]] so the passthrough-on-failure degradation surfaces as a `Severity.Warning` + success (section 1.1). CSS/JS/JSON
    * carry no passthrough contract, so their native exceptions keep propagating (the specific-catches-only rule — this facade adds no catch).
    */
  def minifyFileResult(
    input:        String,
    filePath:     String,
    options:      MinifyOptions = MinifyOptions.Defaults,
    jsCompressor: JsCompressor = BasicJsMinifier
  ): DiagResult[String] =
    // jekyll-minifier.rb:1091-1093 exclude?: excluded destinations are copied through untouched.
    if (options.exclude.exists(pattern => pattern == filePath || fnmatch(pattern, filePath))) DiagResult.success(input)
    else {
      // jekyll-minifier.rb:976-990 / rb:1174-1188: a `.min.js` / `.min.css` destination is copied through untouched.
      if (filePath.endsWith(".min.js") || filePath.endsWith(".min.css")) DiagResult.success(input)
      else {
        fileTypeFromPath(filePath) match {
          case Some(FileType.Html) | Some(FileType.Xml) =>
            HtmlMinifier.minifyResult(input, options.html, jsCompressor, options.jsCompressorOpts)
          case Some(other) => DiagResult.success(minify(input, other, options, jsCompressor))
          case None        => DiagResult.success(input)
        }
      }
    }

  /** Ruby `File.fnmatch(pattern, path)` with NO flags (the only form jekyll-minifier uses, rb:1093).
    *
    * Faithful to CRuby's flag-less `File.fnmatch` for the features exercised below, each pinned against the `ruby 2.6.10` oracle `ruby -e 'p File.fnmatch(p,s)'` (see `FnmatchOracleIss1026Suite` for
    * the full verified probe corpus). Matching steps over Unicode CODEPOINTS, not UTF-16 code units, mirroring CRuby (`File.fnmatch('?','😀') #=> true`, `File.fnmatch('??','😀') #=> false`,
    * `File.fnmatch('[😀]','😀') #=> true`). Per feature:
    *   - `*` — "Matches any number of characters (including none)." With File::FNM_PATHNAME OFF (the default), `*` also matches the directory separator `/`:
    *     `File.fnmatch('*', 'dave/.profile') #=> true`. Hence `*.css` matches `assets/style.css`.
    *   - `?` — "Matches any single character" (one codepoint). With FNM_PATHNAME off it likewise matches `/`: `File.fnmatch('?', '/') #=> true`.
    *   - `[set]` — "Matches any one character in set." Supports ranges (`[a-z]`) and negation with a leading `!` or `^` (`[!set]`). `File.fnmatch('[a-z]', 'a') #=> true`. The FIRST `]` (immediately
    *     after `[`, `[!` or `[^`) CLOSES the class, it is never a member, so `[]` is an empty class (matches nothing, `File.fnmatch('[]a]','ax.css') #=> false`) and `[!]` is a negated empty class
    *     (matches any one char). Inside the class `\` escapes the next char (FNM_NOESCAPE off). Following CRuby `dir.c bracket()`, each member ENDPOINT is read with an optional escape consumed first,
    *     then a RAW `-` (not the last char before `]`) introduces a range to a second (also escape-aware) endpoint: so an escaped ENDPOINT still forms a range — `File.fnmatch('[\\a-c]x.css','bx.css')
    *     #=> true`, `File.fnmatch('[a-\\c]x.css','bx.css') #=> true` — whereas an escaped DASH is a literal member, killing the range: `File.fnmatch('[a\\-c]x.css','-x.css') #=> true` (literal `-`),
    *     `File.fnmatch('[a\\-c]x.css','bx.css') #=> false`. An UNTERMINATED `[` (no closing `]`) fails the whole match — there is NO literal-`[` fallback: `File.fnmatch('[ab*','[ab.css') #=> false`.
    *   - `\\` — "Escapes the next metacharacter" (File::FNM_NOESCAPE is OFF by default), so `\\*` matches a literal `*`. A DANGLING trailing `\\` (last char of the pattern) matches nothing — it is
    *     dropped: `File.fnmatch('a.css\\','a.css') #=> true`, `File.fnmatch('a.css\\','a.css\\') #=> false`.
    *   - any other char matches itself literally.
    *   - leading period: File::FNM_PERIOD is ON by default, so a `.` at index 0 of the WHOLE string can be matched ONLY by an explicit literal `.` (a plain `.` or an escaped `\\.`) in the pattern —
    *     never by `*`, `?` or `[set]`, not even by `*` expanding to zero chars: `File.fnmatch('*','.profile') #=> false`, `File.fnmatch('*.css','.css') #=> false`,
    *     `File.fnmatch('?profile.css','.profile.css') #=> false`, `File.fnmatch('[.]profile.css','.profile.css') #=> false`, but `File.fnmatch('.*','.profile') #=> true`. With FNM_PATHNAME OFF (the
    *     default) there is NO per-component dot rule — only index 0 of the whole string is special, so `*` spans `/` onto a dot freely: `File.fnmatch('*','dave/.profile') #=> true`, and a star after
    *     a slash matches a dotfile, e.g. pattern `a/` then `*` vs `a/.b` is true.
    */
  private def fnmatch(patternStr: String, pathStr: String): Boolean = {
    // Backtracking matcher mirroring CRuby's flag-less glob: `*` consumes greedily with last-star
    // backtracking, `?` consumes exactly one char, `[...]` a character class, `\` escapes, everything
    // else literal. The leading-period rule (FNM_PERIOD on) is enforced via `leadingPeriod`.

    // CRuby `fnmatch` steps over CHARACTERS (codepoints), not UTF-16 code units. We therefore decode
    // both the pattern and the string to arrays of Unicode codepoints up front and index those. This
    // makes `?`/class-members/ranges/`*` operate per-codepoint so astral chars (e.g. U+1F600) count as
    // ONE character, matching CRuby: `File.fnmatch('?','😀') #=> true`, `File.fnmatch('??','😀') #=> false`,
    // `File.fnmatch('[😀]','😀') #=> true`. Done with `String.codePointAt`/`Character.charCount`, which are
    // available on all three platforms (JVM, Scala.js, Scala Native).
    def toCodepoints(s: String): Array[Int] = {
      val buf = scala.collection.mutable.ArrayBuffer.empty[Int]
      var i   = 0
      while (i < s.length) {
        val cp = s.codePointAt(i)
        buf += cp
        i += Character.charCount(cp)
      }
      buf.toArray
    }
    val pattern: Array[Int] = toCodepoints(patternStr)
    val path:    Array[Int] = toCodepoints(pathStr)

    // True when string position `si` holds a `.` that the period rule protects: index 0 of the whole
    // string (FNM_PATHNAME off => no per-component check). Such a `.` may only be matched by a literal.
    def leadingPeriod(si: Int): Boolean = si == 0 && si < path.length && path(si) == '.'.toInt

    // Backtracking core (last-star optimisation): `star` records the pattern/string position of the
    // most recent `*` plus the string index it is currently allowed to have matched up to (`starS`).
    // On a mismatch we rewind to the last `*` and let it swallow one more char. This is O(n*m) rather
    // than the exponential full recursion, with identical semantics.
    def matches(): Boolean = scala.util.boundary {
      var pi    = 0
      var si    = 0
      var starP = -1 // pattern index just after the last `*`, or -1 if none seen
      var starS = -1 // string index the last `*` may resume re-matching from

      while (si < path.length)
        if (pi < pattern.length && pattern(pi) == '*'.toInt) {
          // The leading-period rule (FNM_PERIOD) forbids `*` at the protected leading period from
          // matching at all — neither swallowing it nor expanding to zero chars and deferring it to a
          // later literal is allowed (`File.fnmatch('*.css','.css') #=> false`). So a `*` facing the
          // protected `.` is an immediate NOMATCH.
          if (leadingPeriod(si)) scala.util.boundary.break(false)
          // Record this `*` and tentatively let it match zero chars.
          starP = pi + 1
          starS = si
          pi += 1
        } else if (pi < pattern.length && matchOne(pi, si) && !leadingPeriodViolated(pi, si)) {
          // Single (non-`*`) token consumed one char.
          pi = nextPi(pi)
          si += 1
        } else if (starP >= 0 && canStarConsume(starS)) {
          // Backtrack to the last `*` and let it swallow one more char (`path(starS)`).
          starS += 1
          si = starS
          pi = starP
        } else {
          scala.util.boundary.break(false)
        }

      // String exhausted: any trailing pattern must be `*`s (and a dangling trailing `\` which is
      // dropped). Skip them; anything else left over fails.
      while (pi < pattern.length && pattern(pi) == '*'.toInt) pi += 1
      if (pi == pattern.length - 1 && pattern(pi) == '\\'.toInt) { pi += 1 } // dangling trailing `\`
      pi == pattern.length
    }

    // The last `*` may consume `path(starS)` only if that char is not a protected leading period.
    def canStarConsume(starS: Int): Boolean =
      starS < path.length && !leadingPeriod(starS)

    // True when token at `pi` is a wildcard (`?`/`[`) that would illegally consume a protected
    // leading period at `si` (`*` is handled separately and never reaches matchOne).
    def leadingPeriodViolated(pi: Int, si: Int): Boolean =
      leadingPeriod(si) && {
        pattern(pi) match {
          case q if q == '?'.toInt  => true
          case b if b == '['.toInt  => true
          case e if e == '\\'.toInt => false // an escaped literal `.` is allowed to match a leading period
          case _                    => false // a plain literal token only matches `.` if it IS `.`, which is allowed
        }
      }

    // Pattern index AFTER the single token starting at `pi` (a `?`, a literal, an escape pair, or a
    // `[...]` class). `pi` must NOT point at `*`.
    def nextPi(pi: Int): Int =
      pattern(pi) match {
        case q if q == '?'.toInt  => pi + 1
        case e if e == '\\'.toInt =>
          if (pi + 1 < pattern.length) pi + 2 else pi + 1 // dangling `\` handled by caller
        case b if b == '['.toInt => classEnd(pi) + 1
        case _                   => pi + 1
      }

    // Does the single (non-`*`) token starting at `pi` match `path(si)`? Caller guarantees si in range.
    def matchOne(pi: Int, si: Int): Boolean =
      pattern(pi) match {
        case q if q == '?'.toInt  => true // any single char (including `/`)
        case b if b == '['.toInt  => matchClass(pi, si)
        case e if e == '\\'.toInt =>
          if (pi + 1 < pattern.length) path(si) == pattern(pi + 1)
          else false // dangling trailing `\` matches no char (dropped at end of string instead)
        case c => path(si) == c
      }

    // Index of the `]` that closes the class opened at `pi`, or -1 if the `[` is unterminated.
    // The FIRST `]` (right after `[`, `[!` or `[^`) closes the class; `\` escapes inside the class.
    def classEnd(pi: Int): Int = scala.util.boundary {
      var j = pi + 1
      if (j < pattern.length && (pattern(j) == '!'.toInt || pattern(j) == '^'.toInt)) { j += 1 }
      // The very first member position: a `]` here closes an (empty) class.
      while (j < pattern.length) {
        val pc = pattern(j)
        if (pc == '\\'.toInt) { j += 2 } // escaped char inside the class is never a closing `]`
        else if (pc == ']'.toInt) { scala.util.boundary.break(j) }
        else { j += 1 }
      }
      -1 // unterminated
    }

    // Matches a `[...]` class starting at pattern index `pi` against `path(si)`.
    def matchClass(pi: Int, si: Int): Boolean = {
      val closing = classEnd(pi)
      if (closing < 0) {
        // Unterminated `[` — CRuby fails the whole match (no literal-`[` fallback).
        false
      } else {
        var j       = pi + 1
        val negated = pattern(j) == '!'.toInt || pattern(j) == '^'.toInt
        if (negated) { j += 1 }
        val ch      = path(si)
        var matched = false
        // CRuby `dir.c` `bracket()`: each iteration reads ONE member endpoint (escape-aware — a leading
        // `\` is consumed and the following codepoint is the literal endpoint), THEN checks for a range:
        // if the very next raw codepoint is `-` and the one after it is not the closing `]`, it reads a
        // SECOND endpoint (again escape-aware) and the member denotes the inclusive range [lo..hi].
        // Crucially the `-` itself must be a RAW `-` to introduce a range — an ESCAPED `\-` is read as
        // an ordinary member endpoint above and never reaches this check, so `[a\-c]` is the literals
        // `a`, `-`, `c` (no range), while `[\a-c]` (escaped FIRST endpoint) and `[a-\c]` (escaped SECOND
        // endpoint) are both the range a..c. Verified against the CRuby oracle:
        //   File.fnmatch('[\a-c]x','bx') #=> true   File.fnmatch('[\a-c]x','-x') #=> false
        //   File.fnmatch('[a-\c]x','bx') #=> true   File.fnmatch('[a\-c]x','bx') #=> false (literal `-`)
        while (j < closing) {
          // Read the (first) endpoint, consuming a `\` escape if present.
          var lo = pattern(j)
          if (lo == '\\'.toInt && j + 1 < closing) {
            lo = pattern(j + 1)
            j += 2
          } else {
            j += 1
          }
          if (j < closing && pattern(j) == '-'.toInt && j + 1 < closing && pattern(j + 1) != ']'.toInt) {
            // A raw `-` (not the last char before `]`) introduces a range: read the second endpoint,
            // again consuming a `\` escape if present.
            j += 1 // skip the `-`
            var hi = pattern(j)
            if (hi == '\\'.toInt && j + 1 < closing) {
              hi = pattern(j + 1)
              j += 2
            } else {
              j += 1
            }
            if (ch >= lo && ch <= hi) { matched = true }
          } else {
            // Plain single-codepoint member.
            if (ch == lo) { matched = true }
          }
        }
        if (negated) !matched else matched
      }
    }

    matches()
  }

  /** Determine file type from a file path extension. */
  def fileTypeFromPath(path: String): Option[FileType] = {
    val dot = path.lastIndexOf('.')
    if (dot < 0) {
      None
    } else {
      path.substring(dot + 1).toLowerCase match {
        case "html" | "htm" => Some(FileType.Html)
        case "xml"          => Some(FileType.Xml)
        case "css"          => Some(FileType.Css)
        case "js"           => Some(FileType.Js)
        case "json"         => Some(FileType.Json)
        case _              => None
      }
    }
  }
}
