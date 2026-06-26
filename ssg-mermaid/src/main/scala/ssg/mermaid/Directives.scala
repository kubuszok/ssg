/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/utils.ts (detectInit, detectDirective),
 *              mermaid/packages/mermaid/src/utils/sanitizeDirective.ts (sanitizeDirective),
 *              mermaid/packages/mermaid/src/assignWithDepth.ts (assignWithDepth),
 *              mermaid/packages/mermaid/src/diagram-api/regexes.ts (directiveRegex)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: the upstream directive channel collects `%%{init: {...}}%%` /
 *     `%%{initialize: {...}}%%` directives and merges them into a config overlay.
 *     The overlay is modelled as the SSG-native untyped [[ssg.data.DataView]]
 *     (the analogue of mermaid's untyped JS object), so it can be deep-merged
 *     onto a config via [[ssg.data.DataView.deepMerge]] and round-tripped through
 *     AsDataView/FromDataView.
 *   Idiom: directive bodies are parsed by reusing [[YamlDataViewDecoder.parse]]:
 *     after the upstream `'`->`"` normalisation a directive body is valid JSON,
 *     and JSON is a strict subset of YAML, so the YAML decoder parses it
 *     correctly without adding a JSON dependency.
 *   Renames: detectInit -> Directives.detectInit; detectDirective ->
 *     Directives.detectDirective; sanitizeDirective -> Directives.sanitize;
 *     assignWithDepth's object-merge is delegated to DataView.deepMerge;
 *     cleanAndMerge -> Directives.cleanAndMerge.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-06-17
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

import lowlevel.Nullable

import ssg.data.DataView

import scala.collection.immutable.VectorMap
import scala.util.matching.Regex

/** A single parsed directive: a `type` and its `args` (the parsed body).
  *
  * Ports the `Directive` interface (`utils.ts:133-136`). `args` is the parsed directive body as an untyped [[ssg.data.DataView]] (mirroring the untyped `unknown` of the original), or
  * [[lowlevel.Nullable.empty]] for `null`.
  */
final case class Directive(
  `type`: Nullable[String] = Nullable.empty,
  args:   Nullable[DataView] = Nullable.empty
)

/** The directive channel: init-directive collection and merging.
  *
  * Ports `detectInit` / `detectDirective` (`utils.ts`), `sanitizeDirective` (`utils/sanitizeDirective.ts`), the object-merge of `assignWithDepth` (`assignWithDepth.ts`, delegated to
  * [[ssg.data.DataView.deepMerge]]) and `cleanAndMerge` (`utils.ts:858-860`).
  */
object Directives {

  /** Matches `%%{...}%%` directives.
    *
    * Ports `directiveRegex` (`regexes.ts:8-9`): {{{/%{2}{\s*(?:(\w+)\s*:|(\w+))\s*(?:(\w+)|((?:(?!}%{2}).|\r?\n)*))?\s*(?:}%{2})?/gi}}}
    *
    * Capture groups: 1 = `name:` form, 2 = bare `name` form, 3 = simple word argument, 4 = complex body terminated by `}%%`, 5 = complex body without a closing `}%%` (to end of text).
    *
    * Cross-platform (re2): the original group-4 negative lookahead `(?:(?!\}%%)[\s\S])*` ("everything up to the first `}%%`") is not supported on Scala Native. It is replaced by two alternation
    * branches — `([\s\S]*?)\}%%` (reluctant, terminates at the first `}%%`) and `([\s\S]*)` (greedy, to end-of-text when there is no `}%%`) — reproducing the same match semantics without a lookahead
    * (cross-platform regex guide, pattern #1: programmatic post-match via alternation). The word-arg branch `(\w+)` retains its own `\s*(?:\}%%)?` tail to consume the optional close.
    */
  private val DirectiveRegex: Regex =
    """(?i)%%\{\s*(?:(\w+)\s*:|(\w+))\s*(?:(\w+)\s*(?:\}%%)?|([\s\S]*?)\}%%|([\s\S]*))?""".r

  /** Matches `%%`-comments that are NOT directives, used to strip them before the directive scan.
    *
    * Ports the `commentWithoutDirectives` regex (`utils.ts:165-168`): {{{`[%]{2}(?![{]${directiveWithoutOpen.source})(?=[}][%]{2}).*\n`}}} where `directiveWithoutOpen` is the directive body without
    * the opening `%%{` (`regexes.ts` shares the source). The negative lookahead keeps real directives; the positive lookahead requires the `}%%` close.
    *
    * Cross-platform (re2): the original's negative lookahead `(?!\{...)` excludes directives and its positive lookahead `(?=\}%%)` requires `}%%`. These are mutually exclusive at the character level:
    * the negative lookahead checks for `{` while the positive requires `}`, so the negative always passes when the positive does. The reduced `%%\}%%` form matches `%%` immediately followed by `}%%`
    * — exactly the set of `%%`-comments that contain a closing `}%%` but are not real `%%{...}%%` directives. This avoids all lookaheads (cross-platform regex guide, pattern #1).
    */
  private val CommentWithoutDirectives: Regex =
    """(?i)%%\}%%.*\n""".r

  /** Detects the directive(s) of a given `type` (or all directives if `type` is empty) from the text.
    *
    * Ports `detectDirective` (`utils.ts:160-199`). Before scanning, the text is trimmed, non-directive comments are stripped, and every `'` is replaced with `"` (`utils.ts:169`) so a directive body
    * becomes valid JSON. Each matched body (group 4) is parsed as JSON via [[YamlDataViewDecoder.parse]] (JSON is a subset of YAML); a simple word argument (group 3) is kept as a trimmed string. If
    * no directive matches, a single `Directive(type = text)` is returned (mirroring `{ type: text, args: null }`).
    *
    * @param text
    *   the diagram text
    * @param `type`
    *   the directive name to match (a regex/word); empty matches every directive
    * @return
    *   the list of matched directives (possibly a single-element list)
    */
  def detectDirective(text: String, `type`: Nullable[String] = Nullable.empty): List[Directive] = {
    // text = text.trim().replace(commentWithoutDirectives, '').replace(/'/gm, '"');
    val cleaned =
      CommentWithoutDirectives.replaceAllIn(text.trim, "").replace('\'', '"')

    val result = List.newBuilder[Directive]
    var any    = false
    val typePattern: Nullable[Regex] = `type`.map(t => t.r)

    DirectiveRegex.findAllMatchIn(cleaned).foreach { m =>
      val g1 = Option(m.group(1))
      val g2 = Option(m.group(2))
      val g3 = Option(m.group(3))
      // Group 4 = body terminated by `}%%`; group 5 = body without closing `}%%`
      // (to end of text). At most one is non-null; coalesce to a single option.
      val g4 = Option(m.group(4)).orElse(Option(m.group(5)))

      // (match && !type) || (type && match[1]?.match(type)) || (type && match[2]?.match(type))
      val matches =
        typePattern.fold(true) { tp =>
          g1.exists(s => tp.findFirstIn(s).isDefined) || g2.exists(s => tp.findFirstIn(s).isDefined)
        }

      if (matches) {
        any = true
        // const type = match[1] ? match[1] : match[2];
        val tpe: Nullable[String] = g1 match {
          case Some(s)    => Nullable(s)
          case scala.None =>
            g2 match {
              case Some(s)    => Nullable(s)
              case scala.None => Nullable.empty
            }
        }
        // const args = match[3] ? match[3].trim()
        //   : match[4] ? JSON.parse(match[4].trim()) : null;
        val args: Nullable[DataView] = g3 match {
          case Some(s)    => Nullable(DataView.from(s.trim))
          case scala.None =>
            g4 match {
              case Some(s) if s.trim.nonEmpty =>
                // JSON.parse(match[4].trim()) — JSON is a subset of YAML, and the
                // body has already had `'`->`"` applied above, so the YAML decoder
                // parses it as JSON would.
                YamlDataViewDecoder.parse(s.trim)
              case _ => Nullable.empty
            }
        }
        result += Directive(`type` = tpe, args = args)
      }
    }

    if (!any) {
      // if (result.length === 0) { return { type: text, args: null }; }
      List(Directive(`type` = Nullable(cleaned), args = Nullable.empty))
    } else {
      result.result()
    }
  }

  /** Collects the `init`/`initialize` directives and merges them into a single config overlay, applying the `config`-key remapping.
    *
    * Ports `detectInit` (`utils.ts:99-131`):
    *   - collect every `init`/`initialize` directive via [[detectDirective]];
    *   - sanitize each directive's args ([[sanitize]]);
    *   - deep-merge them in order ([[ssg.data.DataView.deepMerge]], the object-merge of `assignWithDepth`);
    *   - if the merged result has a `config` key, move its value under the detected diagram-type key, remapping `flowchart-v2` -> `flowchart` (`utils.ts:120-128`).
    *
    * @param text
    *   the diagram text (already frontmatter-stripped)
    * @param diagramType
    *   the detected diagram type, used for the `config`-key remapping
    * @return
    *   the merged init overlay as a [[ssg.data.DataView]], or [[lowlevel.Nullable.empty]] if there is no init directive
    */
  def detectInit(text: String, diagramType: DiagramType): Nullable[DataView] = {
    val inits = detectDirective(text, Nullable("""(?:init\b)|(?:initialize\b)"""))

    // const args = inits.map((init) => init.args); sanitizeDirective(args);
    // results = assignWithDepth(results, [...args]);  (results starts as {})
    //
    // Only directives that actually matched init/initialize and that carry a
    // mapping body contribute. detectDirective returns a synthetic
    // Directive(type = text) when nothing matched (args empty), which is skipped
    // here.
    val argDataViews: List[DataView] =
      inits.flatMap(_.args.fold[List[DataView]](Nil)(dv => List(sanitize(dv))))

    if (argDataViews.isEmpty) {
      Nullable.empty
    } else {
      // assignWithDepth({}, [...args]) folds each arg onto the accumulator.
      var results: DataView = DataView.from(VectorMap.empty[String, DataView])
      argDataViews.foreach { arg =>
        results = DataView.deepMerge(results, arg)
      }

      // Move the `config` value to the appropriate diagram-type key.
      // const prop = 'config'; if (results[prop] !== undefined) { ... }
      results.asMap.fold[Nullable[DataView]](Nullable(results)) { map =>
        map.get("config") match {
          case Some(configValue) =>
            // if (type === 'flowchart-v2') { type = 'flowchart'; }
            val typeKey =
              if (diagramType == DiagramType.FlowchartV2) DiagramType.Flowchart.keyword
              else diagramType.keyword
            // results[type] = results[prop]; delete results[prop];
            val remapped = map.removed("config").updated(typeKey, configValue)
            Nullable(DataView.from(remapped))
          case scala.None =>
            Nullable(results)
        }
      }
    }
  }

  /** Sanitizes a directive's args object.
    *
    * Ports `sanitizeDirective` (`utils/sanitizeDirective.ts:9-63`). Deletes keys that start with `__`, contain `proto`/`constr`, or are `null`; recurses into nested objects. The
    * `configKeys`-membership filter of the original is not applied here: SSG models the config as the open-ended [[ssg.data.DataView]] (there is no compile-time `configKeys` set), and unknown keys
    * are simply ignored later by `FromDataView` when reconstructing the typed config, so the security-relevant prototype-pollution filters are what matter and are kept. The CSS sanitisation
    * (`sanitizeCss`) and the `themeVariables` value filter are applied to preserve the original's behaviour on those keys.
    *
    * @param dv
    *   the directive args
    * @return
    *   the sanitised args
    */
  def sanitize(dv: DataView): DataView =
    dv.asVector.fold[DataView](
      dv.asMap.fold[DataView](dv) { map =>
        // for (const key of Object.keys(args)) { ... }
        var out = VectorMap.empty[String, DataView]
        map.foreach { case (key, value) =>
          // key.startsWith('__') || key.includes('proto') || key.includes('constr') || args[key] == null
          val drop =
            key.startsWith("__") || key.contains("proto") || key.contains("constr") || value.isNull
          if (!drop) {
            // if (typeof args[key] === 'object') { sanitizeDirective(args[key]); }
            val sanitisedValue =
              if (value.asMap.isDefined || value.asVector.isDefined) sanitize(value)
              else {
                // const cssMatchers = ['themeCSS', 'fontFamily', 'altFontFamily'];
                val cssMatchers = List("themeCSS", "fontFamily", "altFontFamily")
                if (cssMatchers.exists(key.contains)) {
                  value.asString.fold[DataView](value)(s => DataView.from(sanitizeCss(s)))
                } else {
                  value
                }
              }
            // if (args.themeVariables) { ... } — filter each themeVariables value.
            val finalValue =
              if (key == "themeVariables") sanitizeThemeVariables(sanitisedValue)
              else sanitisedValue
            out = out.updated(key, finalValue)
          }
        }
        DataView.from(out)
      }
    ) { vec =>
      // if (Array.isArray(args)) { args.forEach((arg) => sanitizeDirective(arg)); }
      DataView.from(vec.map(sanitize))
    }

  /** Filters each `themeVariables` value, blanking values that contain anything outside the allowed character class (`sanitizeDirective.ts:54-61`):
    * {{{if (val?.match && !val.match(/^[\d "#%(),.;A-Za-z]+$/)) { ... = ''; }}}}
    */
  private def sanitizeThemeVariables(dv: DataView): DataView =
    dv.asMap.fold[DataView](dv) { map =>
      var out = VectorMap.empty[String, DataView]
      map.foreach { case (k, v) =>
        val filtered =
          v.asString.fold[DataView](v) { s =>
            if (ThemeVariableAllowed.matches(s)) v else DataView.from("")
          }
        out = out.updated(k, filtered)
      }
      DataView.from(out)
    }

  /** The allowed character class for a `themeVariables` value (the original's `/^[\d "#%(),.;A-Za-z]+$/`).
    */
  private val ThemeVariableAllowed: Regex = """[\d "#%(),.;A-Za-z]+""".r

  /** Sanitises a CSS string by checking for balanced braces.
    *
    * Ports `sanitizeCss` (`sanitizeDirective.ts:65-84`).
    */
  def sanitizeCss(str: String): String = {
    var startCnt = 0
    var endCnt   = 0
    var error    = false
    val it       = str.iterator
    while (!error && it.hasNext) {
      val element = it.next()
      if (startCnt < endCnt) {
        error = true
      } else if (element == '{') {
        startCnt += 1
      } else if (element == '}') {
        endCnt += 1
      }
    }
    if (error || startCnt != endCnt) "{ /* ERROR: Unbalanced CSS */ }"
    else str
  }

  /** The default `secure` config-key set (`config.schema.yaml:197-205`).
    *
    * `config.ts sanitize()` (`config.ts:151`) protects `['secure', ...(siteConfig.secure ?? [])]` — author markup (frontmatter / directive) may not override these keys, so the caller/site config
    * value wins. SSG's [[MermaidConfig]] does not model a `secure` field, so the upstream default array is used (`config.schema.yaml` `secure.default`):
    * {{{['secure', 'securityLevel', 'startOnLoad', 'maxTextSize', 'suppressErrorRendering', 'maxEdges']}}}
    */
  val SecureKeys: Set[String] =
    Set("secure", "securityLevel", "startOnLoad", "maxTextSize", "suppressErrorRendering", "maxEdges")

  /** Sanitizes a merged config overlay, mirroring `config.ts sanitize()` (`config.ts:146-181`).
    *
    * This is the analogue of upstream's `updateCurrentConfig` running `sanitize(d)` over the merged directive config `d` (`config.ts:23`) before `assignWithDepth`-ing it onto the siteConfig. It is
    * applied ONCE to the merged overlay (`cleanAndMerge(frontmatter, directive)`), so frontmatter `config:` blocks receive the same sanitization as init directives.
    *
    * Faithful to `config.ts sanitize()`:
    *   - drops every key in the secure set ([[SecureKeys]] = `['secure', ...siteConfig.secure]`, `config.ts:151-158`) at the top level, so the caller/site config value wins for those keys;
    *   - drops top-level `__`-prefixed keys (prototype-pollution guard, `config.ts:161-165`);
    *   - recurses into every nested object/array and deletes any string value containing `<`, `>`, or `url(data:` (XSS guard, `config.ts:168-180`).
    *
    * The directive-side [[sanitize]] (`sanitizeDirective.ts`, proto/CSS/ themeVariables filters) is composed first so the merged overlay receives the full sanitization that `addDirective` applies
    * upstream (`config.ts:188-200`: `sanitizeDirective` then `updateCurrentConfig` -> `sanitize`).
    *
    * @param dv
    *   the merged config overlay
    * @return
    *   the sanitised overlay
    */
  def sanitizeConfig(dv: DataView): DataView = {
    // addDirective runs sanitizeDirective (proto/CSS/themeVariables) first
    // (config.ts:188-189), then updateCurrentConfig -> config.ts sanitize().
    val directiveSanitised = sanitize(dv)
    sanitizeSecureAndXss(directiveSanitised, topLevel = true)
  }

  /** Ports `config.ts sanitize()` (`config.ts:146-181`).
    *
    * The secure-set drop (`config.ts:151-158`) and the `__`-prefix proto- pollution drop (`config.ts:161-165`) apply only at the level on which `sanitize` was invoked — they are NOT applied while
    * recursing (upstream's recursion at `config.ts:178` only carries the XSS string filter). The XSS string filter (`config.ts:168-180`) deletes any string value containing `<`, `>`, or `url(data:`
    * and recurses into nested objects (and, for faithfulness with the directive arrays, nested arrays).
    *
    * @param dv
    *   the value to sanitise
    * @param topLevel
    *   whether this is the top-level invocation (secure + `__` drops apply)
    */
  private def sanitizeSecureAndXss(dv: DataView, topLevel: Boolean): DataView =
    dv.asVector.fold[DataView](
      dv.asMap.fold[DataView](dv) { map =>
        var out = VectorMap.empty[String, DataView]
        map.foreach { case (key, value) =>
          // ['secure', ...siteConfig.secure].forEach((key) => delete options[key]);
          // Object.keys(options).forEach((key) => if (key.startsWith('__')) delete);
          // Both apply only at the invoked level, not while recursing.
          val secureDrop = topLevel && (SecureKeys.contains(key) || key.startsWith("__"))
          if (!secureDrop) {
            // if (typeof options[key] === 'string' && (includes('<')||includes('>')||includes('url(data:'))) delete;
            val isXssString =
              value.asString.fold(false)(s => s.contains("<") || s.contains(">") || s.contains("url(data:"))
            if (!isXssString) {
              // if (typeof options[key] === 'object') sanitize(options[key]);
              val sanitisedValue =
                if (value.asMap.isDefined || value.asVector.isDefined) sanitizeSecureAndXss(value, topLevel = false)
                else value
              out = out.updated(key, sanitisedValue)
            }
          }
        }
        DataView.from(out)
      }
    ) { vec =>
      // Arrays: recurse into each element (mirrors sanitize being re-entered on
      // each object element). String elements are not deleted by config.ts
      // sanitize() (it iterates object keys, not array indices), so only nested
      // objects/arrays are recursed.
      DataView.from(vec.map(v => sanitizeSecureAndXss(v, topLevel = false)))
    }

  /** Cleans and merges a default config with a directive overlay.
    *
    * Ports `cleanAndMerge` (`utils.ts:858-860`): `merge({}, defaultData, data)`. lodash `merge` is a present-keys-win deep merge, with `data` (the directive overlay) winning over `defaultData` (the
    * frontmatter config). Delegates to [[ssg.data.DataView.deepMerge]] with the directive overlay applied last.
    *
    * @param frontmatterConfig
    *   the frontmatter `config` (the lower-precedence `defaultData`)
    * @param directive
    *   the init-directive overlay (the higher-precedence `data`); empty when there is no directive
    * @return
    *   the merged overlay as a [[ssg.data.DataView]]
    */
  def cleanAndMerge(frontmatterConfig: Nullable[DataView], directive: Nullable[DataView]): DataView = {
    val base    = frontmatterConfig.getOrElse(DataView.from(VectorMap.empty[String, DataView]))
    val overlay = directive.getOrElse(DataView.from(VectorMap.empty[String, DataView]))
    // merge({}, defaultData, data): directive (data) wins over frontmatter.
    DataView.deepMerge(base, overlay)
  }
}
