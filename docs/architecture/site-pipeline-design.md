# SSG End-to-End Site Pipeline — Design (ISS-1054)

**Status:** Refreshed 2026-06-17 — reconciled with resolved blockers
(ISS-978/980/991/1010/1056/1057/1058/1092/1121); user decisions Q1/Q3/Q11
ratified. Approval pending orchestrator audit.
**Issue:** ISS-1054 (critical, infra, `[R0610-P0]`). Implements the design that
ISS-1055 (`Site.build()`) is BLOCKED-BY.
**Date:** 2026-06-17.
**Author role:** implementer (design-doc deliverable; no product code in scope).

This document is the design contract for the missing static site generator. Per
the 2026-06-10 codebase review, "the static site generator does not exist": the
`ssg/` aggregator is a `Version` constant (`ssg/src/main/scala/ssg/package.scala:9`)
plus a one-method Terser adapter (`ssg/src/main/scala/ssg/TerserJsCompressorAdapter.scala:21-24`).
There is no pipeline, no config loading, no front-matter→DataView bridge, no
layouts, no output writer, and no integration test
(`docs/reviews/codebase-review-2026-06-10.md:14`, §5:88).

Every codebase claim below carries a `file:line` citation so the auditor can
check it. Where the design would otherwise lean on a known-broken API, the open
issue that must land first is cited inline.

---

## 1. Goals and non-goals (v1 scope)

### Goals (v1)

1. A `Site.build(config)` entry point that turns a source tree into a rendered
   output tree, composing the already-ported modules: front-matter extraction
   (ssg-md), Liquid templating (ssg-liquid), Markdown rendering (ssg-md), SASS
   compilation (ssg-sass), and minification (ssg-minify).
2. `_config.yml` loading into typed config via **kindlings-yaml** (the mandated
   YAML library — never scala-yaml or hand-rolled; see §4).
3. A front-matter → `ssg.data.DataView` bridge so page/site variables reach
   Liquid (`ssg-data-commons` is the intended data spine;
   `ssg/src/main/scala/ssg/package.scala` aggregator depends on it via
   `build.sbt:344`).
4. Layout chain + `{% include %}` resolution + permalink rendering, flavored on
   Jekyll semantics (§2, §6).
5. Output writing via `ssg.commons.io.FileOps`
   (`ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:26-132`).
6. An in-repo fixture site + golden-output integration test proving the modules
   compose (§9).
7. A shared per-file diagnostics shape so a build can report errors uniformly
   despite the seven incompatible module error contracts
   (`docs/reviews/codebase-review-2026-06-10.md:100`; ties to ISS-1102) — §7.

### Non-goals (v1) — each deferred item gets a named follow-up issue

These are **out of v1 scope**, not "good enough" omissions. Each is a concrete
deliverable deferred to a tracked follow-up issue (IDs to be filed by the
orchestrator on approval; placeholders below state the exact deferred work):

- **Collections** (`_posts`, custom collections, `site.posts`, categories/tags
  taxonomies). Jekyll's collection model is large; v1 ships pages only.
  Follow-up: *"Site collections + posts (categories/tags/pagination)"*.
- **Incremental / cached builds.** v1 is a full rebuild every time. Follow-up:
  *"Incremental build with dependency tracking"*.
- **Plugin/hook SPI for third-party Scala plugins.** The flavor extension point
  (§4) is the only extensibility surface in v1. Follow-up: *"Plugin SPI"*.
- **Sass/Liquid concurrency.** ssg-sass's `EvaluationContext`/`CurrentEnvironment`
  are shared mutable statics, unsound under parallel `compileString`
  (`docs/reviews/codebase-review-2026-06-10.md:90`). v1 builds **single-threaded**;
  parallelism is deferred until that is fixed. Follow-up: *"Parallel page build
  (after sass static-context fix)"*.
- **Data files** (`_data/*.yml` → `site.data`). Follow-up: *"`_data` directory
  loading"*.
- **Sass `@import`/`loadPaths` from `_sass/`.** No longer blocked:
  `compileString` now honors `importers=`/`loadPaths=` (ISS-991 resolved;
  `ssg-sass/src/main/scala/ssg/sass/Compile.scala:58-59,89-123`). v1 can
  therefore support `_sass/` partials by configuring `loadPaths` to include
  `config.sassDir`. This is an **unblocked near-term follow-up** rather than a
  v1 goal because the fixture (§9) already demonstrates sass compilation via a
  self-contained `.scss` file, and wiring `loadPaths` requires plumbing
  `SiteConfig.sassDir` into the `compileString` call — straightforward but out
  of the critical path. Follow-up: *"`_sass/` partial resolution via
  loadPaths"*.

---

## 2. Reference semantics — Jekyll as the behavioral reference

SSG's default flavor mirrors **Jekyll** (the Liquid default flavor is already
`Flavor.JEKYLL`, `ssg-liquid/src/main/scala/ssg/liquid/TemplateParser.scala:131,134`).
The behaviors v1 adopts from Jekyll, with the documented semantics they follow:

| Jekyll behavior | v1 adoption | Jekyll-doc semantics referenced |
|---|---|---|
| **Front matter** — a file is processed iff it begins with `---\n ... \n---`; the block is parsed as YAML; absent → file copied verbatim (a "static file") | Adopted exactly | Jekyll "Front Matter": triple-dashed YAML at file top; files without it are copied as-is |
| **Layout chain** — `layout:` names a file in `_layouts/`; a layout may itself declare `layout:`; rendered content flows up the chain as `{{ content }}` | Adopted | Jekyll "Layouts": nested layouts via `layout` key; `content` variable |
| **Includes** — `{% include file.html %}` pulls from `_includes/`; `{% include_relative %}` relative to the current file | Adopted; `_includes/` is the include root (see resolver note §6) | Jekyll "Includes" |
| **Permalinks** — `permalink:` per page; site-wide default styles `date`/`pretty`/`none`; `{{ page.url }}` derives from it | v1: explicit per-page `permalink:` override; site-wide default `date` (which for pages yields source-relative path); per-page over site default (Q3 DECIDED, §6) | Jekyll "Permalinks": built-in styles + template placeholders |
| **Variables** — `site.*` (config + computed), `page.*` (front matter + computed `url`/`path`/`content`), `layout.*` | Adopted (§5) | Jekyll "Variables" |
| **Collections / `_posts`** | **Explicitly deferred** (§1 non-goals) | Jekyll "Collections" |

### Output-extension rule (which inputs run the markdown step, and the resulting extension)

A processed file (front matter present, §2 row 1) is routed by its **input
extension**:

- **`.md` and `.markdown` → `.html`.** These run the full pipeline (Liquid →
  Markdown → layout) and the output file's extension becomes `.html`. (Jekyll's
  `markdown_ext` defaults to `markdown,mkdown,mkdn,mkd,md`; v1 supports `.md` and
  `.markdown`, the two common forms; the rest are a follow-up.)
- **`.html` *with* front matter → stays `.html`, no markdown step.** Jekyll
  processes front-mattered `.html` files through Liquid + layouts but **does not**
  run the markdown renderer on them. v1 matches: such files skip
  `MarkdownRender` (§3 diagram) and keep their `.html` extension.
- **Any other extension *with* front matter → Liquid + layouts only, extension
  preserved.** e.g. `feed.xml`, `sitemap.xml`, `robots.txt` with front matter are
  rendered through Liquid (and layouts if declared) but **not** through markdown,
  and keep their original extension (`feed.xml` stays `feed.xml`). This is how
  Jekyll generates Atom/RSS feeds.
- **No front matter → static file**, copied verbatim with its extension unchanged
  (§3.2 bucket 5, §9 case 7).
- **`.scss`/`.sass` *with* (possibly empty) front matter → sass converter,
  extension becomes `.css`.** A `.scss`/`.sass` file carrying front matter (even
  the empty `---\n---` marker) routes to the sass converter via
  `Compile.compileString` (`ssg-sass/src/main/scala/ssg/sass/Compile.scala:50`),
  and the output file's extension becomes `.css` (e.g. `assets/style.scss` →
  `assets/style.css`). This is the adopted Jekyll semantic: in Jekyll, a sass/scss
  file becomes a *converted* page only when it carries front matter; **without**
  front matter it falls through to the static rule above and is copied verbatim
  (extension preserved). v1 matches this exactly.

The output path is then derived from the page's permalink/`page.url` (§6
Permalinks); the *extension* portion of that derivation follows the rules above.

### Is "Jekyll" a port or SSG-native? (anti-cheat C9)

**The `Site` pipeline is an SSG-native implementation, not a port** — there is no
single upstream file to port it from. Jekyll is the *behavioral reference* (Ruby +
Liquid + a plugin ecosystem), but SSG composes already-ported engines through new
glue. Per campaign anti-cheat C9, "port" claims require a comparable original in
`original-src/`; therefore **no `Covenant: full-port` / `Covenant-ruby-reference`
header may be stamped on the new `Site` code**. The new files carry only the SSG
license header (no covenant), exactly as `ssg-graphviz`, `ssg-graphs-commons`, and
`ssg-data-commons` are SSG-native (CLAUDE.md: "not a port of an external library").

**Recommendation on vendoring Jekyll:** Vendoring `jekyll` core into
`original-src/` is **not a prerequisite** and is **not recommended** for v1.
Jekyll core is a Ruby/Liquid orchestrator whose behavior we are *referencing*, not
*porting line-for-line*; the line-for-line ports already live in the engine modules
(flexmark→ssg-md, liqp→ssg-liquid, dart-sass→ssg-sass, jekyll-minifier→ssg-minify).
Vendoring it would invite a false "port" framing of glue code that has no faithful
1:1 original. We *should* however pin the **exact Jekyll documentation version**
this design targets in the test plan (§9) as the behavioral oracle. (Open question
Q9 puts this decision to the user.)

---

## 3. Architecture

### Module composition (text diagram)

```
                         ssg module (pipeline + integration test — Q1 DECIDED)
                         ┌───────────────────────────────────────────────────┐
  _config.yml ──────────►│ SiteConfig.load (kindlings-yaml)            §4     │
                         │        │                                           │
  source tree ──────────►│ SourceScan (FileOps walk)                  §3.2/§8 │
                         │        │  → Seq[SourceFile]                        │
                         │        ▼                                           │
                         │ FrontMatter.split  (ssg-md YAML FM ext)     §5     │
                         │        │  → (Map[String,List[String]], body)       │
                         │        ▼                                           │
                         │ FrontMatterBridge → DataView (page.*)       §5     │
                         │        │                                           │
                         │        ▼                                           │
                         │ LiquidRender  (TemplateParser/Template,            │
                         │                vars = site.* + page.*)      §5     │
                         │        │  (body still markdown if .md)             │
                         │        ▼                                           │
                         │ MarkdownRender (Parser → HtmlRenderer)             │
                         │        │  → HTML fragment                          │
                         │        ▼                                           │
                         │ LayoutChain (wrap in _layouts/*, Liquid)    §6     │
                         │        │                                           │
                         │        ▼                                           │
                         │ SassCompile  (Compile.compileString)               │
                         │   (single-threaded in v1 — §1 sass-static caveat)  │
                         │        │                                           │
                         │        ▼                                           │
                         │ Minify (Minifier.minifyFile)                       │
                         │        │                                           │
                         │        ▼                                           │
  output tree ◄──────────│ OutputWriter (FileOps.writeString, root-jail) §6/§8│
                         │                                                    │
                         │ Diagnostics collector (per-file)            §7     │
                         └───────────────────────────────────────────────────┘
```

Engine entry points the pipeline calls (all verified present today):

- Markdown: `Parser.builder()` / `parser.parse(input)`
  (`ssg-md/src/main/scala/ssg/md/parser/Parser.scala:449,142`) →
  `HtmlRenderer.builder()` / `renderer.render(node)`
  (`ssg-md/src/main/scala/ssg/md/html/HtmlRenderer.scala:226,134`).
- Front matter: YAML FM extension's visitor yields
  `Map[String, List[String]]`
  (`ssg-md/src/main/scala/ssg/md/ext/yaml/front/matter/AbstractYamlFrontMatterVisitor.scala:28,40`).
- Liquid: `TemplateParser.parse(input)` → `Template.render(JMap[String, DataView])`
  (`ssg-liquid/src/main/scala/ssg/liquid/TemplateParser.scala:101`;
  `ssg-liquid/src/main/scala/ssg/liquid/Template.scala:55`).
- Sass: `Compile.compileString(source, style, …)`
  (`ssg-sass/src/main/scala/ssg/sass/Compile.scala:50-68`). **Not** `Compile.compile`
  — that throws `UnsupportedOperationException` on every platform
  (`ssg-sass/src/main/scala/ssg/sass/Compile.scala:164`; method def at :149).
- Minify: `Minifier.minifyFile(input, path, options, jsCompressor)`
  (`ssg-minify/src/main/scala/ssg/minify/Minifier.scala:75-87`); JS compression via
  `ssg.TerserJsCompressorAdapter`
  (`ssg/src/main/scala/ssg/TerserJsCompressorAdapter.scala:21`).
- File I/O: `ssg.commons.io.FileOps` + `FilePath`
  (`ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:26-132`;
  `ssg-commons/src/main/scala/ssg/commons/io/FilePath.scala:21-57`).
  **Encoding assumption:** `FileOps.readString`/`writeString` default to **UTF-8**
  (`ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:33-34,45-46`); charset
  overloads exist (`…/FileOps.scala:37-38,49-50`) but the pipeline does not thread
  them. v1 therefore treats **all source and output as UTF-8** and **defers**
  Jekyll's `encoding:` config key (Jekyll defaults to `utf-8` too). Supporting a
  non-UTF-8 `encoding:` key is a named follow-up: *"Honor Jekyll `encoding:`
  config key in the site pipeline"* (file on approval; no fixture relies on a
  non-UTF-8 input in v1).
- Optional content engines (invoked only if a config flag enables fenced-block
  handling, deferred wiring): KaTeX `KaTeX.renderToString`
  (`ssg-katex/src/main/scala/ssg/katex/KaTeX.scala:55`), Mermaid `Mermaid.render`
  (`ssg-mermaid/src/main/scala/ssg/mermaid/Mermaid.scala:74`), highlight
  `ssg-highlight/src/main/scala/ssg/highlight/SyntaxHighlighter.scala`. v1 does
  **not** wire these into the default page pipeline. The former blockers are now
  resolved (Mermaid front-matter: ISS-1056; `%%{init}%%`/config: ISS-1057/1058;
  highlight overlap: ISS-1091; highlight JS UTF-8: ISS-1092). The remaining
  reasons to defer are **scope** (wiring content engines into the page pipeline
  is a non-trivial integration surface — fenced-code-block detection, per-engine
  config plumbing, output-format negotiation) and **highlight JS grammar
  provisioning** (ISS-1161, open — ssg-highlight's JS test suite still fails
  71/73 grammar-loading cases under WASM, meaning highlight would silently
  produce no output on JS for most languages). Content-engine wiring is a named
  follow-up: *"Wire KaTeX/Mermaid/highlight into the page pipeline behind config
  flags"*.

### 3.1 Where does it live? — DECIDED: fold into the existing `ssg` module (Q1)

**Decision (Q1 DECIDED):** the pipeline and integration test live in the
existing `ssg` module (`ssg/src/main/scala/ssg/`, `ssg/src/test/`). No new
`ssg-site` module is created.

**Prerequisite:** `ssg` is currently classified **`compileOnly`**
(`build.sbt:53-55`), so tests placed in `ssg` never run in CI
(`docs/reviews/codebase-review-2026-06-10.md:79`). ISS-1055 Phase 0 must
**remove `ssg` from `compileOnly`** so the integration test suite (§9) actually
executes. The `ssg` module already has `publishSettings` (`build.sbt:344`) and
`dependsOn` all engine modules (`build.sbt:346`), so it is the natural home
for the pipeline glue.

`FrontMatterBridge` and the other pipeline types live in package `ssg` (not
`ssg.site`), consistent with the module's existing namespace.

### 3.2 Source-file selection (what `SourceScan` includes vs skips)

`SourceScan` walks `config.source` and partitions every entry into one of three
buckets: **processed** (front-matter pages → rendered, including `.scss`/`.sass`
files with front matter, which route to the sass converter `Compile.compileString`
per the §2 extension rules — *not* the Markdown/Liquid page path), **static**
(copied verbatim), or **excluded** (never appears in `destination`). v1 follows
Jekyll's selection rules (Jekyll "Configuration"):

1. **Skip the destination directory.** Never scan into `config.destination`
   (`_site` by default, `SiteConfig.destination` §4) — prevents a build reading
   its own previous output.
2. **Skip underscore-prefixed top-level dirs by default.** `_layouts/`,
   `_includes/`, `_sass/`, `_data/`, `_posts/`, and any other `_*` directory are
   *special inputs*, not site content: they are consumed by their respective
   stages (layouts §6, includes §6, sass `_sass/` §1 follow-up, data §1 deferral),
   not copied to output. `_config.yml` itself is consumed by `SiteConfig.load`
   (§4) and never emitted.
3. **Skip dotfiles / dot-directories by default** (`.git/`, `.gitignore`,
   `.DS_Store`, etc.), matching Jekyll's default exclusion of hidden entries.
4. **Honor `exclude:` and `include:` config keys.** `exclude:` removes otherwise
   matched paths; `include:` re-adds paths that the default rules would skip
   (Jekyll's canonical use: `include: [.htaccess]` or surfacing a specific
   underscore file). Both are simple path/glob lists read from `SiteConfig.raw`
   (§4). Glob semantics are deferred to the same follow-up that fixes minify's
   substring-vs-glob bug if needed; v1 matches plain relative paths and directory
   prefixes.
5. **Everything else:** files **with** front matter (per the §2 rule and the
   output-extension rule §2/below) are *processed* — and within that bucket the
   §2 extension rules decide the converter: `.scss`/`.sass` route to the sass
   converter (`Compile.compileString`, `→ .css`), `.md`/`.markdown` through the
   Markdown pipeline (`→ .html`), and everything else through Liquid + layouts
   only (extension preserved); files **without** front matter are *static* and
   copied byte-for-byte (the `static.txt` fixture case, §9 case 7).

This is the rule the fixture golden test (§9) depends on: e.g. `_layouts/default.html`
is bucket (2) and therefore **must not** appear in `_expected/`, while
`static.txt` is bucket (5)-static and appears unchanged.

---

## 4. Site config (`_config.yml`)

### Loading

`_config.yml` is loaded with **kindlings-yaml** (mandated; never scala-yaml or a
hand-rolled parser — project ecosystem rule, MEMORY note "JSON/YAML libs in
re-scale"). JSON config, if ever supported, uses **kindlings-jsoniter**. Neither
dependency is in `build.sbt` today (verified: no `kindlings`/`yaml`/`jsoniter`
entries in `build.sbt`); **adding `kindlings-yaml` as an `ssg` dependency is a
prerequisite of ISS-1055 phase 1** (§10). It must resolve on all three platforms;
if a platform is missing, that gates the platform per §8.

### Typed config

```scala
final case class SiteConfig(
  source:      FilePath,            // default "."          (Jekyll: source)
  destination: FilePath,            // default "_site"      (Jekyll: destination)
  layoutsDir:  String = "_layouts",
  includesDir: String = "_includes",
  sassDir:     String = "_sass",
  permalink:   PermalinkStyle = PermalinkStyle.Date, // Q3 DECIDED — see §6 Permalinks
  baseurl:     String = "",
  minify:      Boolean = false,     // off by default; see §3 caveats on minify
  flavor:      SiteFlavor = SiteFlavor.Jekyll,        // §4 extension point
  raw:         DataView              // full parsed _config.yml as DataView → site.*
)
```

`raw` keeps the entire parsed config as a `DataView` so arbitrary user keys reach
templates as `site.<key>` without the case class enumerating them (Jekyll exposes
all config keys under `site`). The typed fields are the ones the pipeline itself
consumes. `FromDataView` derivation (`ssg-data-commons/src/main/scala/ssg/data/FromDataView.scala:15-17`)
can populate the typed fields from the parsed `DataView`; this is the intended use
of the Hearth-derived bridge.

### Flavor extension point (do not hardcode Jekyll)

```scala
trait SiteFlavor {
  def liquidFlavor: ssg.liquid.parser.Flavor              // default Flavor.JEKYLL
  def permalinkResolver: (SiteConfig, PageMeta) => String
  def layoutKey: String        // "layout"  (Jekyll) — Cobalt/MkDocs differ
  def isProcessable: SourceFile => Boolean   // front-matter rule per flavor
}
object SiteFlavor { val Jekyll: SiteFlavor = … }
```

Per the extensibility preference (MEMORY "Extensible design preference": do not
close doors to Cobalt.rs/MkDocs-style generators), Jekyll behavior is *one*
`SiteFlavor` instance, not baked into the pipeline. Non-Jekyll generators supply a
different `SiteFlavor`. This mirrors how ssg-liquid already flavors its dialects
(`ssg-liquid/src/main/scala/ssg/liquid/parser/Flavor.scala` via
`TemplateParser.withFlavor`, `ssg-liquid/src/main/scala/ssg/liquid/TemplateParser.scala:204`).

---

## 5. Front-matter → DataView bridge

### The exact types involved

- **Source (ssg-md):** the YAML front-matter visitor produces
  `java.util.Map[String, java.util.List[String]]`
  (`ssg-md/src/main/scala/ssg/md/ext/yaml/front/matter/AbstractYamlFrontMatterVisitor.scala:28,40`).
  Note this is **multi-valued and untyped** — every value is a *list of strings*,
  not a parsed YAML scalar/map. This is a real impedance mismatch (see Q5).
- **Sink (ssg-liquid):** `Template.render` takes
  `java.util.Map[String, ssg.data.DataView]`
  (`ssg-liquid/src/main/scala/ssg/liquid/Template.scala:55`).
- **Bridge currency:** `ssg.data.DataView`
  (`ssg-data-commons/src/main/scala/ssg/data/DataView.scala:14-89`), whose value
  union is Boolean/Short/Int/Long/Float/Double/String/BigDecimal/TemporalAccessor/
  `Vector[DataView]`/`VectorMap[String, DataView]`
  (`ssg-data-commons/src/main/scala/ssg/data/DataView.scala:89`).

### Where the bridge code lives

A new object `ssg.FrontMatterBridge` in the `ssg` module (Q1 DECIDED, §3.1).
**It does not belong in ssg-md** (ssg-md must not depend on ssg-data-commons or
Liquid) **nor in ssg-liquid** (Liquid must not know about Markdown front matter).
`ssg` is the module that depends on both, making it the correct seam.

### How variables reach Liquid

1. `FrontMatterBridge.parse(rawBody): (DataView /* page front matter */, String /* body */)`.
   Because the ssg-md visitor flattens scalars into `List[String]`, the bridge must
   **re-parse the front-matter block as YAML with kindlings-yaml** to recover real
   types (nested maps, booleans, dates) and emit a faithful `DataView`. (This is
   why Q5 asks whether to parse front matter via kindlings-yaml directly and use
   ssg-md only to *detect/split* the block.)
2. The pipeline builds the render variable map:
   - `site` → `DataView` from `SiteConfig.raw` (+ computed `site.time`, etc.).
   - `page` → front-matter `DataView` **plus** computed `page.url`, `page.path`,
     `page.content`, `page.title` (Jekyll "Variables").
   - `layout` → the active layout's own front matter, in the layout pass (§6).
3. These are placed into a `java.util.HashMap[String, DataView]` and handed to
   `Template.render` (`ssg-liquid/src/main/scala/ssg/liquid/Template.scala:55`).
4. `AsDataView` derivation (`ssg-data-commons/src/main/scala/ssg/data/AsDataView.scala:13-15`)
   converts any typed computed values (e.g. a `PageMeta` case class) to `DataView`.

---

## 6. Layouts, includes, permalinks

### Layout chain resolution

- A page declares `layout: foo` → load `<source>/_layouts/foo.html`.
- Render order: render the page body first (Liquid then Markdown), bind the result
  to `content`, then render the layout template with `content` + `page` + `site` +
  `layout` in scope. If the layout itself declares `layout: bar`, repeat upward
  until a layout has no `layout` key (Jekyll nested-layout semantics, §2). Guard
  against cycles with a visited-set (error via §7, not infinite loop).
- Layout file lookup uses `FilePath.resolve`
  (`ssg-commons/src/main/scala/ssg/commons/io/FilePath.scala:30`) under
  `config.layoutsDir`.

### Includes

- `{% include x.html %}` resolves under `<source>/_includes/`. ssg-liquid already
  has a `NameResolver` SPI and a filesystem resolver
  (`ssg-liquid/src/main/scala/ssg/liquid/antlr/LocalFSNameResolver.scala:36-52`);
  the `ssg` pipeline configures the `TemplateParser` with a resolver rooted at
  `_includes/`.
- **Former blocker resolved:** unquoted dotted include names
  (`{% include footer.html %}`) previously failed to lex in the Jekyll flavor
  (`docs/reviews/codebase-review-2026-06-10.md:33`). **ISS-1010** (resolved)
  added `PATH_SEP` token handling and `parseJekyllIncludeFileName` to the
  parser (`ssg-liquid/src/main/scala/ssg/liquid/parser/LiquidLexer.scala`,
  `ssg-liquid/src/main/scala/ssg/liquid/parser/LiquidParser.scala`). Includes
  now work in the Jekyll flavor, and the integration test (§9 case 5) is a
  **normal green test** — no `.fail` pin, no ISS-1010 citation.

### include_relative resolution root + jail

- `{% include_relative path %}` resolves **relative to the directory of the file
  currently being rendered** (Jekyll "Includes": `include_relative` is page-local,
  not rooted at `_includes/`). v1 computes the include base as
  `currentFile.parent` and resolves the relative name under it.
- The result is subject to the **same root-jail** as everything else (§6
  Root-jail below): after `resolve(...).normalize.toAbsolute`, the path must stay
  under `config.source`; an `include_relative ../../secret` escaping the source
  root is rejected via §7 diagnostics. `include_relative` therefore never reaches
  outside the site source, even though its base is page-local rather than
  `_includes/`.

### Permalinks (Q3 DECIDED — match Jekyll's actual current behavior)

Jekyll's documented global `permalink` default is `date`
(`/:categories/:year/:month/:day/:title:output_ext`), but `date` is a
posts/collections template — its category/date placeholders are meaningless for
**regular pages**, which are v1's entire scope (posts/collections are deferred,
§1). For regular pages, Jekyll outputs to the **source-relative path**: e.g.
`about.md` becomes `about.html` in the output, honoring any explicit per-page
`permalink:` front-matter override. This is equivalent to the `none`
(`/:path:output_ext`) built-in style applied to pages.

v1 therefore:
- Defaults `SiteConfig.permalink` to `PermalinkStyle.Date` (matching Jekyll's
  documented default), but the page-rendering path resolves it to the
  source-relative output path (since `date`'s `:categories`/`:year`/`:month`/
  `:day` placeholders yield empty for non-collection pages).
- Honors an explicit per-page `permalink:` front-matter key (e.g.
  `permalink: /about/` → `_site/about/index.html`, pretty semantics).
- Defines the `PermalinkStyle` enum with `None`, `Pretty`, `Date` (Jekyll
  built-in styles) plus a template form for the deferred posts follow-up, where
  the `date` style's category/date placeholders become meaningful.
- `page.url` is computed from the active style + the source path + any explicit
  `permalink:` override. The output file path is derived from `page.url`.

### baseurl / site.url semantics

`SiteConfig.baseurl` (§4, default `""`) is the **subpath the site is served
under** (e.g. `/blog`). v1 adopts Jekyll's semantics precisely:

- `baseurl` does **not** alter `page.url` and does **not** alter the on-disk
  output path. `page.url` stays root-relative (`/about/`) and output stays
  `_site/about/index.html`, regardless of `baseurl`. This matches Jekyll, where
  `page.url` is baseurl-free.
- `baseurl` is exposed to templates as `site.baseurl` so authors prepend it
  explicitly, exactly as Jekyll requires (`{{ site.baseurl }}{{ page.url }}` or
  the `relative_url`/`absolute_url` filters). v1 surfaces `site.baseurl` via
  `SiteConfig.raw` (§4); the `relative_url`/`absolute_url` filter pair is a named
  follow-up if not already provided by ssg-liquid (see open question Q12).
- **Jekyll's separate `site.url`** (the scheme+host, e.g. `https://example.com`)
  is **deferred** in v1: it participates only in `absolute_url` and is purely a
  template convenience with no effect on output paths. v1 exposes whatever `url:`
  the user puts in `_config.yml` under `site.url` (via `SiteConfig.raw`) but adds
  no typed field and no automatic prefixing — see open question Q12.

### Root-jail decision (ties to ISS-1020)

`LocalFSNameResolver.resolve` today honors **absolute** include names directly
(`ssg-liquid/src/main/scala/ssg/liquid/antlr/LocalFSNameResolver.scala:41-44`) and
resolves relative names under `root` **without verifying the result stays under
root** (`…antlr/LocalFSNameResolver.scala:49-51`) — a path-traversal surface
(ISS-1020, `pit-of-success`, high). **Decision for v1:** the `ssg` pipeline MUST
jail all include/layout/output resolution to the configured `source`/`destination`
roots: after `resolve(...).normalize.toAbsolute`, assert the path string starts
with the jailed root's absolute path; reject otherwise via §7 diagnostics. This is
the artifact ISS-1020 asks for, applied at the pipeline boundary. The root-jail is
now **sound on all three platforms**: the Native `FilePath.normalize` bug (ISS-980)
that previously dropped the leading slash on absolute paths was fixed by delegating
to `java.nio.file.Paths` (`ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala:64-67`,
ISS-980 resolved).

---

## 7. Error / diagnostics contract

The review documents **seven incompatible module error contracts** (throw
`SassException` / bare `RuntimeException` / `ParseError`-or-error-span / in-band
HTML comment / silent passthrough / `Option[String]`)
(`docs/reviews/codebase-review-2026-06-10.md:100`), and ISS-1102 proposes a shared
`ssg.commons.Diagnostics`. v1 proposes the **minimum shared shape the pipeline
actually needs**:

```scala
final case class BuildDiagnostic(
  file:     FilePath,
  stage:    BuildStage,      // enum: Config|Scan|FrontMatter|Liquid|Markdown|Sass|Minify|Layout|Write
  severity: Severity,        // enum: Error|Warning
  message:  String,
  cause:    Nullable[Throwable] = Nullable.empty
)

final case class BuildResult(
  written:     Vector[FilePath],
  diagnostics: Vector[BuildDiagnostic]
)
```

- Each stage wraps the engine call and **adapts that engine's native error
  contract into a `BuildDiagnostic`** — e.g. catch `SassException` from
  `Compile.compileString`, capture `CompileResult.warnings`
  (`ssg-sass/src/main/scala/ssg/sass/Compile.scala:36`) as `Warning`
  diagnostics, treat Liquid `STRICT`-mode throws as `Error`.
- **No silent swallow.** Minify currently swallows exceptions and returns input
  unchanged with no channel (`docs/reviews/codebase-review-2026-06-10.md:99`);
  the `ssg` pipeline records a `Warning` diagnostic when a minify pass is skipped, so a
  build never silently ships unminified assets without a trace.
- `Site.build` returns `BuildResult` (not throwing) so a caller (CLI, test) can
  decide policy; a `failOnError` config flag can promote any `Error` to a thrown
  exception. This is the v1 concretion of ISS-1102 at the pipeline layer; the
  fuller cross-module `Diagnostics` unification stays ISS-1102's job.

---

## 8. Cross-platform reality at v1 (Q11 DECIDED — all 3 platforms)

The former blockers that prevented cross-platform file I/O have all been
resolved. Per-platform status for the pipeline:

| Capability | JVM | Native | JS | Status |
|---|---|---|---|---|
| `FileOps` read/write/exists | ✅ | ✅ (ISS-977 resolved) | ✅ (ISS-978 resolved; `ssg-commons/src/main/scalajs/ssg/commons/io/FileOpsPlatform.scala`) | all platforms |
| **Directory APIs** (list / walkTree / createDirectories / copy / deleteRecursively) | ✅ | ✅ | ✅ | **ISS-1121 resolved** — all five APIs present on all platforms (`ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:74-127`) |
| `FilePath.normalize` (root-jail soundness) | ✅ | ✅ (ISS-980 resolved; delegates to `java.nio.file.Paths`, `ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala:64-67`) | ✅ | all platforms |
| Sass `compileString` + importers/loadPaths | ✅ | ✅ | ✅ (no FS needed) | ISS-991 resolved; `loadPaths` honored (`ssg-sass/src/main/scala/ssg/sass/Compile.scala:58-59`) |
| ssg-md JS resource loading (entities/emoji) | ✅ | ✅ | ✅ (ISS-979 resolved; embedded at build time) | all platforms |
| kindlings-yaml availability | assumed ✅ | TBD | TBD | verify at ISS-1055 phase 1 |

**v1 platform target (Q11 DECIDED):** ship and CI-run the integration test on
**all three platforms (JVM + Native + JS)**, honoring CLAUDE.md's "all 3
platforms are baseline" rule. The blockers that previously gated Native (ISS-980
normalize bug + ISS-1121 directory APIs) and JS (ISS-978 FileOps I/O) are all
resolved. The only remaining TBD is `kindlings-yaml` platform availability,
verified at ISS-1055 phase 1; if a platform is missing kindlings-yaml, that
platform's integration suite is gated until it resolves (a named, tracked
dependency, not a permanent scoping).

**NOTE:** ISS-1120 (open, `[R0610-P3]`, "Enable the ISS-1054 site-pipeline
golden integration suite on Scala Native") was filed when Native was deferred.
Its premise is now obsolete — Native is a v1 target. ISS-1120 is a candidate
for the orchestrator to close as premise-obsolete.

---

## 9. Integration test plan

### Fixture site (in-repo)

A minimal site under `ssg/src/test/resources/fixture-site/`:

```
fixture-site/
  _config.yml                # title, baseurl, minify: false (no permalink: key — uses Jekyll default)
  _layouts/default.html      # <html>…{{ content }}…</html>, uses {{ site.title }}, {{ page.title }}
  _layouts/post.html         # layout: default  (proves the nested layout chain)
  _includes/header.html      # proves {% include %} (ISS-1010 resolved; unquoted names work)
  index.md                   # front matter title + layout: default + markdown body + {{ site.title }}
  about.md                   # permalink: /about/  + layout: default
  assets/style.scss          # empty front matter (`---\n---`) so it routes to SassCompile; self-contained scss (no @import; ISS-991 not relied on)
  static.txt                 # no front matter → copied verbatim (Jekyll static-file rule)
  _expected/                 # golden output tree (minify: false)
  _expected-min/             # golden output tree (minify: true) — differential, test case 11
```

### Golden-output comparison

The test runs `Site.build(config)` against the fixture, then compares the produced
`_site` tree byte-for-byte against `_expected/`. Assertions are **structural and
exact** (not substring/non-emptiness — the review repeatedly faults smoke tests,
`docs/reviews/codebase-review-2026-06-10.md:82-83`). The golden files are committed
and reviewed.

### Platform(s) CI runs it on

**All three platforms (JVM + Native + JS)** per Q11 DECIDED (§8). The former
platform blockers (ISS-978/980/1121) are resolved; see §8 for the full
capability matrix. The `ssg` module must be **removed from `compileOnly`**
(`build.sbt:53-55`) so the suite actually executes in CI on all platforms.

### Concrete test cases

1. **Markdown + layout:** `index.md` → `_site/index.html` wrapped by
   `default.html`; `{{ content }}` substituted; `<h1>` from markdown present.
2. **Front-matter variable:** `{{ page.title }}` in layout renders the page's
   front-matter title (proves the §5 bridge end-to-end).
3. **Site variable:** `{{ site.title }}` from `_config.yml` renders (proves §4
   load + `site.*` exposure).
4. **Nested layout chain:** a page with `layout: post` → `post.html` → `default.html`
   (proves §6 chain + cycle-free upward resolution).
5. **Include:** `{% include header.html %}` content appears in the rendered
   output (proves §6 includes). ISS-1010 is resolved; unquoted dotted include
   names now lex and parse correctly in the Jekyll flavor. This is a **normal
   green test** — no `.fail` pin.
6. **Permalink:** `about.md` with explicit `permalink: /about/` front-matter
   override → `_site/about/index.html` and `page.url == /about/` (proves the
   per-page override; the default `date` style for pages without an explicit
   permalink yields the source-relative path per §6 Permalinks Q3 DECIDED).
7. **Static passthrough:** `static.txt` (no front matter) copied byte-identical.
8. **Sass:** `assets/style.scss` carries empty front matter (`---\n---`), so per
   the §2 extension rules it routes to the sass converter — compiled via
   `Compile.compileString` (`ssg-sass/src/main/scala/ssg/sass/Compile.scala:50`)
   and emitted as `_site/assets/style.css` (extension `.scss` → `.css`).
9. **Diagnostics:** a deliberately broken page (e.g. `layout: missing`) yields a
   `BuildDiagnostic(stage = Layout, severity = Error)` and **does not** crash the
   whole build (proves §7).
10. **Root-jail:** an include/permalink attempting `../../etc/passwd` is rejected
    with a diagnostic (proves §6 jail; sound on all platforms now that ISS-980 is
    resolved, §8).
11. **Minify differential (green coverage for the silent-no-op class):** build the
    *same* fixture twice — once with `minify: false` and once with `minify: true`
    — and assert the HTML output **differs** (the `minify: true` output is
    byte-for-byte equal to a committed `_expected-min/` golden variant, and is
    *not* equal to the `minify: false` output). This gives minify positive,
    asserted coverage rather than a silent no-op: `Minifier.minifyFile`
    (`ssg-minify/src/main/scala/ssg/minify/Minifier.scala:75-87`) must actually
    transform the page. If `minify: true` produces output identical to
    `minify: false`, the test fails — catching the swallow-and-passthrough trap
    (`docs/reviews/codebase-review-2026-06-10.md:99`) the design warns about in §7.

---

## 10. Phased implementation plan for ISS-1055

Each phase is independently reviewable with its own Definition of Done (DoD).
ISS-1055 is BLOCKED-BY this doc; on approval it executes these phases.

- **Phase 0 — scaffolding.** Remove `ssg` from `compileOnly` (`build.sbt:53-55`)
  so tests run in CI (Q1 DECIDED); add `kindlings-yaml` dependency to `ssg`. The
  directory APIs the pipeline needs (`FileOps.list`, `FileOps.walkTree`,
  `FileOps.createDirectories`, `FileOps.copy`, `FileOps.deleteRecursively`) are
  already present on all three platforms (ISS-1121 resolved;
  `ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:74-127`). **DoD:**
  `ssg` module compiles on all three platforms with the `kindlings-yaml`
  dependency; `ssg` is no longer in `compileOnly`.
- **Phase 1 — config + front-matter bridge.** `SiteConfig.load` + `FrontMatterBridge`.
  **DoD:** unit tests: a `_config.yml` parses to typed `SiteConfig`; a front-matter
  block parses to the expected `DataView` (exact values, nested map).
- **Phase 2 — single-page render (no layout).** Liquid → Markdown → write one page.
  **DoD:** integration test cases 1-3 pass on all three platforms.
- **Phase 3 — layouts + includes + permalinks + static passthrough.** **DoD:**
  cases 4, 5, 6, 7 pass on all three platforms (case 5 is a normal green test
  now that ISS-1010 is resolved).
- **Phase 4 — sass + minify + diagnostics.** **DoD:** cases 8, 9, **11** pass;
  minify off-by-default honored; the minify differential (case 11) proves
  `minify: true` actually transforms output (no silent swallow).
- **Phase 5 — root-jail + hardening.** **DoD:** case 10 passes on all three
  platforms (ISS-980 resolved; jail is sound everywhere).
- **(Former Phase 6 — Native enablement — folded into main phases.)** ISS-1121
  and ISS-980 are both resolved; Native is a v1 target. Each phase above now
  targets all three platforms. **ISS-1120** (open, `[R0610-P3]`) was filed for
  the deferral; its premise is obsolete and it is a candidate for the
  orchestrator to close.

---

## 11. Open questions for the user

Each is a genuine scope decision with a recommendation and the alternatives.

**Q1 — Module name / location. DECIDED: fold into `ssg`.**
The pipeline and integration test live in the existing `ssg` module. `ssg` is
removed from `compileOnly` (`build.sbt:53-55`) so tests run in CI. No new
module is created. See §3.1.

**Q2 — Config format strictness.** Should an unknown `_config.yml` key be an error,
a warning, or silently exposed under `site.*`?
*Recommendation:* **silently expose** under `site.*` (Jekyll behavior — arbitrary
keys are template-visible), and warn only on malformed YAML. *Alternatives:* strict
mode rejecting unknown top-level keys; lenient with a `--strict` opt-in.

**Q3 — Default permalink style. DECIDED: match Jekyll's actual current behavior.**
Jekyll's documented global `permalink` default is `date`
(`/:categories/:year/:month/:day/:title:output_ext`), but `date` is a
posts/collections template — for **regular pages** (v1's entire scope) Jekyll
outputs to the source-relative path (e.g. `about.md` -> `about.html`), honoring
any explicit per-page `permalink:` front-matter override. v1 keeps
`PermalinkStyle.Date` as the enum default (faithful to Jekyll's documented
default) and resolves it to source-relative output for pages. The named styles
(`date`/`pretty`/`none`) are wired in the `PermalinkStyle` enum for the deferred
posts follow-up, where `date`'s placeholders become meaningful. See §6
Permalinks.

**Q4 — Collections / `_posts` in or out of v1.**
*Recommendation:* **out** (§1 non-goals), tracked as a named follow-up.
*Alternatives:* include a minimal `_posts` → `site.posts` now (significantly larger
scope: dates-in-filenames, categories, ordering).

**Q5 — Front-matter parsing path.** The ssg-md YAML FM visitor flattens values to
`Map[String, List[String]]`
(`…/AbstractYamlFrontMatterVisitor.scala:28,40`), losing real YAML types.
*Recommendation:* use ssg-md only to **detect/split** the `---…---` block, then
**re-parse the block with kindlings-yaml** to get faithful types into `DataView`.
*Alternatives:* enrich the ssg-md visitor to preserve types (changes a covenanted
flexmark port — undesirable); accept the lossy string-list form (breaks booleans,
dates, nested maps in templates).

**Q6 — Plugin / hook story.** Any third-party plugin SPI in v1?
*Recommendation:* **no plugin SPI in v1**; the `SiteFlavor` trait (§4) is the only
extension point. *Alternatives:* a minimal `BuildHook` SPI (pre/post render) now.

**Q7 — Content engines (KaTeX/Mermaid/highlight) in the default pipeline.**
*Recommendation:* **not wired in v1** — the former blockers are resolved (Mermaid
front-matter ISS-1056, `%%{init}%%`/config ISS-1057/1058, highlight overlap
ISS-1091, highlight JS UTF-8 ISS-1092), but wiring content engines into the
page pipeline is a non-trivial integration surface (fenced-code-block detection,
per-engine config plumbing, output-format negotiation), and highlight JS grammar
provisioning remains broken (ISS-1161, open). *Alternatives:* wire highlight
only (lowest risk, but JS grammar gap ISS-1161 means silent no-output on JS for
most languages); wire all behind config flags.

**Q8 — Build failure policy.** Should `Site.build` throw on the first `Error`
diagnostic, or always return a `BuildResult` and let the caller decide?
*Recommendation:* **return `BuildResult`**, with a `failOnError` config flag
(default `false`) that promotes errors to a thrown exception (§7). *Alternatives:*
always throw (simpler, less testable); collect-and-throw at the end.

**Q9 — Vendor Jekyll into `original-src/`?** Anti-cheat C9 requires a comparable
original for "port" claims.
*Recommendation:* **do not vendor**; the `Site` pipeline is SSG-native glue (no
covenant header), and the line-for-line ports already live in the engine modules
(§2). Pin a Jekyll **documentation** version as the behavioral oracle in §9 tests.
*Alternatives:* vendor jekyll core to enable C9-style comparison (risks
mis-framing glue as a port).

**Q10 — Minify default.** On or off by default?
*Recommendation:* **off** (`SiteConfig.minify = false`, §4) — minify HTML/JS
currently swallow errors silently (`docs/reviews/codebase-review-2026-06-10.md:99`)
and the JS `compress=true` trap inverts behavior (`…:98`). *Alternatives:* on by
default (matches jekyll-minifier's purpose) once those traps are fixed.

**Q11 — Cross-platform v1 target. DECIDED: all 3 platforms (JVM + Native + JS).**
The former blockers are resolved: ISS-978 (JS FileOps), ISS-980 (Native
normalize), ISS-1121 (directory APIs). The integration suite runs on all three
platforms. See §8.

**Q12 — `baseurl` and `site.url` handling.** How much of Jekyll's URL model does
v1 adopt (§6 baseurl/url)?
*Recommendation:* adopt `baseurl` as a **template-only** value (`site.baseurl`,
no effect on `page.url` or output paths, matching Jekyll) and **defer** typed
`site.url` plus automatic `relative_url`/`absolute_url` prefixing to a named
follow-up. *Alternatives:* prepend `baseurl` into `page.url` automatically
(diverges from Jekyll, breaks `relative_url`); ship `relative_url`/`absolute_url`
filters in v1.

**Q13 — Clean-build behavior (destructive, user-visible).** Should `Site.build`
**wipe `destination` before writing**? Jekyll cleans `_site` on every build
*except* paths matching `keep_files` (default `['.git', '.svn']`) and the
`.jekyll-metadata` file.
*Recommendation:* **v1 does a full clean** of `destination` before writing,
honoring a `keep_files` list (default `['.git', '.svn']`) — matching Jekyll. This
is destructive, so it is called out explicitly here and gated behind the
fixture-only test directory in §9 (the test writes to a temp/`_site` under test
resources, never a user tree). Clean uses `FileOps.deleteRecursively`
(`ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:126-127`, ISS-1121
resolved).
*Alternatives:* never clean (stale outputs accumulate, diverges from Jekyll);
clean only files this build did not produce (more code, needs a manifest).
