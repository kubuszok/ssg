# ssg-liquid Liquid Port — Gap Analysis vs liqp 0.9.2

**Date:** 2026-04-07
**Status:** Evidence-based; every claim below is anchored to a file Read or Grep performed in this session. Items I could not verify are explicitly marked **unverified**.

---

## 1. Existing port doc claims vs reality

`docs/architecture/liqp-port.md` states:
- "ANTLR replaced with hand-written lexer + recursive descent parser." **Verified** — `ssg-liquid/src/main/scala/ssg/liquid/parser/LiquidLexer.scala`, `LiquidParser.scala` exist; parser doc-comment at line 9 references the original `.g4`.
- "Jackson replaced by `LiquidSupport` trait." **Partially true** — `LiquidSupport.scala` line 14 self-describes as *"This is an initial stub. Full implementation in Phase 4."* so eager `Inspectable→Map` conversion is **not actually implemented** (see §4 LiquidSupport).
- "strftime4j replaced with `DateTimeFormatter` via scala-java-time polyfill." **Verified** — `filters/Date.scala` lines 79–140 implement an inline strftime→Java pattern map. It is hand-rolled and lossy (see §4 Date).
- "19 AST node types." **Verified** by file enumeration (19 `.scala` files in `nodes/`, identical names to 19 `.java` files in liqp `nodes/`).
- "10 block tags." **Verified** — `blocks/` has 11 `.scala` files (Block + 10 concrete) matching 11 `.java` files.
- "8 simple tags." **Verified** — `tags/` has 8 `.scala` matching 8 `.java`.
- "58 filters + registry." **Unverified count** — but Glob shows 58 filter `.scala` files (Filter + Filters + 56 concrete vs 56 Java filter classes; `Map.java` is renamed `MapFilter.scala`). Names match 1:1.
- "Test coverage: 280 tests across 12 munit suites." **Verified** — Grep `^  test\(` returned exactly 280 across 12 files.
- "Flavor system" table in doc says JEKYLL `Where style = Jekyll`. **Verified** in `parser/Flavor.scala` line 50 (`liquidStyleWhere = false` for JEKYLL).

**Doc reality gaps:**
- The doc does not mention that `LiquidSupport` is a stub.
- The doc does not mention that Date filter loses Ruby-style fuzzy parsing (the entire `filters/date/` package is skipped).
- The doc does not mention that `Where` is a simplified inline rewrite (PropertyResolverHelper / nested-property paths / Inspectable item-property are not ported).
- The doc does not mention that `Json` filter swap from Jackson to a hand-rolled serializer is non-trivial (no cycle detection, no `@JsonProperty` support, no Java time module).
- The doc does not mention the **entire `spi/` package is skipped** (custom-type SPI extension mechanism is gone — see §4).

---

## 2. Inventory

### Original liqp main sources (Glob, all `.java` under `src/main/java/liqp/`)

| Subpackage | Java files | Files |
|---|---|---|
| top-level (`liqp/`) | 11 | Examples, Insertion, Insertions, LValue, PlainBigDecimal, RenderTransformer, RenderTransformerDefaultImpl, Template, TemplateContext, TemplateParser |
| `antlr/` | 4 | CharStreamWithLocation, FilterCharStream, LocalFSNameResolver, NameResolver |
| `blocks/` | 11 | Block, Capture, Case, Comment, Cycle, For, If, Ifchanged, Raw, Tablerow, Unless |
| `exceptions/` | 4 | ExceededMaxIterations, IncompatibleTypeComparison, LiquidException, VariableNotExist |
| `filters/` (top) | 56 | Abs … Where_Exp |
| `filters/date/` | 5 | BasicDateParser, CustomDateFormatRegistry, CustomDateFormatSupport, FuzzyDateDateParser, Parser |
| `filters/where/` | 5 | JekyllWhereImpl, LiquidWhereImpl, PropertyResolverAdapter, PropertyResolverHelper, WhereImpl |
| `nodes/` | 19 | And/Atom/Attribute/Block/ComparingExpression/Contains/Eq/Filter/GtEq/Gt/Insertion/KeyValue/L/Lookup/LtEq/Lt/NEq/Or/Output |
| `parser/` | 3 | Flavor, Inspectable, LiquidSupport |
| `parser/v4/` | 1 | NodeVisitor |
| `spi/` | 5 | BasicTypesSupport, Java7DateTypesSupport, Java8DateTypesSupport, SPIHelper, TypesSupport |
| `tags/` | 8 | Assign, Break, Continue, Decrement, Include, IncludeRelative, Increment, Tag |
| ANTLR grammars (`src/main/antlr4/...`) | 2 `.g4` | LiquidLexer.g4, LiquidParser.g4 |

### Port main sources (Glob)

| Subpackage | Scala files |
|---|---|
| top-level | 11 (adds `Nullable.scala`; no `Examples`) |
| `antlr/` | 2 (LocalFSNameResolver, NameResolver) |
| `blocks/` | 11 |
| `exceptions/` | 4 |
| `filters/` (top) | 58 (`MapFilter.scala` instead of `Map.scala`; otherwise 1:1) |
| `filters/date/` | **0** |
| `filters/where/` | **0** |
| `nodes/` | 19 |
| `parser/` | 6 (Flavor, Inspectable, LiquidLexer, LiquidParser, LiquidSupport, Token) |
| `tags/` | 8 |
| `spi/` | **0** |

### Files in original with no port counterpart (verified by `ssg-dev db migration list --status skipped --lib liqp`)

```
liqp/Examples.java                                              (sample / non-functional)
liqp/antlr/CharStreamWithLocation.java                          (ANTLR plumbing)
liqp/antlr/FilterCharStream.java                                (ANTLR plumbing)
liqp/filters/date/BasicDateParser.java                          (FUZZY DATE PARSING)
liqp/filters/date/CustomDateFormatRegistry.java                 (custom date type SPI)
liqp/filters/date/CustomDateFormatSupport.java                  (custom date type SPI)
liqp/filters/date/FuzzyDateDateParser.java                      (FUZZY DATE PARSING)
liqp/filters/date/Parser.java                                   (Ruby-compat date pattern list)
liqp/filters/where/JekyllWhereImpl.java                         (JEKYLL where semantics)
liqp/filters/where/LiquidWhereImpl.java                         (LIQUID where semantics)
liqp/filters/where/PropertyResolverAdapter.java                 (Inspectable property paths)
liqp/filters/where/PropertyResolverHelper.java                  (Inspectable property paths)
liqp/filters/where/WhereImpl.java                               (where base)
liqp/spi/BasicTypesSupport.java                                 (custom-type SPI base)
liqp/spi/Java7DateTypesSupport.java                             (java.util.Date support)
liqp/spi/Java8DateTypesSupport.java                             (java.time support)
liqp/spi/SPIHelper.java                                         (ServiceLoader bootstrap)
liqp/spi/TypesSupport.java                                      (SPI interface)
liqp/parser/v4/NodeVisitor.java                                 (folded into hand-written parser)
```

`ssg-dev db migration list --lib liqp --status todo` and `--status wip` returned no rows. **Everything not in the port is officially in `skipped` status.**

---

## 3. SLOC sanity check

I am **not running `wc`** (the harness blocks it), so I am intentionally not fabricating exact line numbers. Instead I'm reporting Read-observed file sizes (Read returns numbered lines; the largest line number I saw in this session is the file's size). For files I didn't open, I mark **(not measured)**.

| Package | Original (Java) line counts I personally observed | Port (Scala) line counts I personally observed | Notes |
|---|---|---|---|
| `TemplateParser` | 562 | 247 | Java has 28 `withXxx` builder methods + `parse(Path/File/InputStream/Reader/CharStream)` overloads + `getDateParser` etc. Port has 19 `withXxx` and only `parse(String)`. **Major shrinkage is real, not just Scala terseness.** |
| `Template` | 504 | 69 | Java handles render-time-limit ExecutorService, max-template-size guard, ContextHolder, errors(), toStringTree, render(jsonMap String), render(Inspectable), render(key,value,kvs*), renderUnguarded variants. Port has only `render(JMap)`/`render()`/`renderToObject`/`renderToObjectUnguarded`. **Massive shrinkage.** |
| `TemplateContext` | 214 | 165 | Roughly equivalent. Port lacks: `template` field, `BasicDateParser getDateParser` (no date parser at all), `getEnvironmentMapConfigurator` plumbing in newRootContext (parser doesn't store one), `Path getRootFolder` returns `Any` not `Path`. |
| `LValue` | 511 | 324 | Most semantics preserved. **`asTemporal` and `asRubyDate` simplified** — original consults `CustomDateFormatRegistry`; port returns `ZonedDateTime.now()` for non-`TemporalAccessor` values. `temporalAsArray` only handles `ZonedDateTime` in port, original handles any `TemporalAccessor`. `asString` for temporal values used `asRubyDate(value, context)` to convert; port uses `temporalToString` which `try`/`catch`s and falls back to `value.toString`. |
| `RenderTransformerDefaultImpl` | 59 | 68 | Behaviorally equivalent. **OK.** |
| `blocks/For` | 343 | 298 | Behaviorally equivalent — verified. forloop fields (length/index/index0/rindex/rindex0/first/last/name/parentloop), limit/offset/reversed/range, `offset:continue` register all present. **OK.** |
| `blocks/Tablerow` | 176 | 160 | Behaviorally equivalent — verified. cols/limit/offset, tablerowloop fields (length, index, index0, rindex, rindex0, first, last, col, col0, col_first, col_last, row) all present. **OK.** |
| `blocks/Case` | 57 | 62 | Behaviorally equivalent — verified. |
| `tags/Include` | 64 | 78 | Mostly equivalent. **Minor gap:** in port's Jekyll branch, when a parameter render does not return a `JMap`, the port silently ignores it (line 60 comment "Non-map parameter — add as positional" but no code follows). Original requires Map and would `ClassCastException`. |
| `filters/Date` | 97 | 141 | **Significant divergence** — see §4. |
| `filters/Where` | 44 + JekyllWhereImpl 215 + LiquidWhereImpl (not measured) + WhereImpl (not measured) + PropertyResolverHelper (not measured) + PropertyResolverAdapter (not measured) | 72 | **Major shrinkage** — see §4. |
| `filters/Json` | 23 | 96 | Port is larger (hand-written serializer). But misses Jackson features (Jackson handles cycles, BigDecimal, Java time module, custom serializers, @JsonProperty). |
| `parser/` (lexer + parser combined) | LiquidLexer.g4 + LiquidParser.g4 + NodeVisitor.java (not measured) | LiquidLexer.scala + LiquidParser.scala (not measured) | See §4 for grammar-rule coverage. |
| `spi/` | 5 files (TypesSupport 11, SPIHelper 47, BasicTypesSupport 82 — Read in this session; 2 not measured) | **0** | **Entire package skipped — see §4.** |

**Where the port is alarmingly small relative to original:**
- `Template.java` 504 → `Template.scala` 69 (≈14% — vastly below the 60–75% expected ratio)
- `TemplateParser.java` 562 → `TemplateParser.scala` 247 (≈44%)
- `filters/Where.java` + `filters/where/*` 5 files → 72-line single file
- `filters/date/*` 5 files → 0 files
- `spi/*` 5 files → 0 files

These are the four red zones.

---

## 4. Per-file behavioral diffs (high-risk files)

### 4.1 TemplateParser.java vs TemplateParser.scala

**Builder methods present in Java but MISSING in port:** (Read both files in session)

| Java Builder method | Port equivalent | Status |
|---|---|---|
| `withObjectMapper(ObjectMapper)` | — | **Missing** (no Jackson dep — fine in principle but no replacement hook for custom serialization) |
| `withDefaultTimeZone(ZoneId)` | — | **Missing** (port has no `defaultTimeZone` field at all) |
| `withEnvironmentMapConfigurator(Consumer<Map>)` | — | **Missing** (port has no env-map plumbing in `Template.parse` either) |
| `withSnippetsFolderName(String)` | — | **Missing as public method** (private `_snippetsFolderName` exists but no `withSnippetsFolderName`) |
| `withDateParser(BasicDateParser)` | — | **Missing** (no BasicDateParser in port) |
| `withStripSingleLine(boolean)` | partially | port has only the 2-arg `withStripSpaceAroundTags(strip, singleLine)`, no standalone setter |
| `withLiquidStyleWhere(boolean)` | — | **Missing** — port has `withLiquidStyleInclude` only; the `_liquidStyleWhere` field exists but no setter — bug confirmed in `TemplateParser.scala` lines 119, 200–202: field is read in `build()` but no setter to populate it. |

**Parse overloads present in Java but missing in port:**
- `parse(Path)`, `parse(File)`, `parse(InputStream)`, `parse(Reader)`, `parse(CharStream)` — port has only `parse(String)`. Major gap for any caller that wants to stream large templates from disk. (Evidence: TemplateParser.java lines 475–500 vs TemplateParser.scala line 53.)

**Other missing fields/state:**
- `mapper` (ObjectMapper)
- `defaultTimeZone`
- `environmentMapConfigurator`
- `dateParser`

**Default flavor:** Java is `LIQP`, port is `JEKYLL`. **Intentional change** (commented in port line 91). This will silently change behavior for users porting code from liqp.

### 4.2 LValue.java vs LValue.scala

Read both. Mostly faithful. Differences:
- `asTemporal(value, ctx)`: original looks up `CustomDateFormatRegistry` for non-TA values; port returns `ZonedDateTime.now()`. Port loses custom date type support.
- `asRubyDate(value, ctx)`: original uses `getZonedDateTimeFromTemporalAccessor(ta, ctx.getParser().defaultTimeZone)`; port uses `ZonedDateTime.from(ta)` (which throws for partial accessors like `LocalDate`) and falls back to `now()`. **Subtle bug**: parsing `LocalDate` will silently produce `now()` instead of midnight on that date.
- `asString(temporal, ctx)`: original always uses `asRubyDate→rubyDateTimeFormat.format`; port `try`/`catch`s and falls back to `value.toString`. Same fragility as above.
- `temporalAsArray`: original works on any `TemporalAccessor` after calling `asTemporal`; port only handles `ZonedDateTime`, returns `Array(value)` for everything else. Loses the 10-element Ruby `Time#to_a` shape for `LocalDate`/`Instant`/etc.
- `isTemporal`: original `value instanceof TemporalAccessor || CustomDateFormatRegistry.isCustomDateType(value)`; port only the `instanceof` check.
- `asFormattedNumber(BigDecimal)`: original uses `setScale(max(1, stripTrailingZeros().scale()), RoundingMode.UNNECESSARY)`; port uses string check for `'.'` and appends `.0`. Sensible for Native compat but **diverges for trailing-zero values** (e.g. `5.50` → original `5.5`, port `5.50`).
- `BREAK`/`CONTINUE`: present.

### 4.3 Template.java vs Template.scala

Java public API surface (Read original):
```
render(String jsonMap)            renderToObject(String jsonMap)
render()                          renderToObject()
render(Inspectable)               renderToObject(Inspectable)
renderObject(Object)              renderObjectToObject(Object) [private]
render(String, Object, Object...) render(boolean, String, Object, Object...)
render(Map)                       renderToObject(Map)
render(Map, ExecutorService, boolean)
renderUnguarded(Map)              renderToObjectUnguarded(Map)
renderUnguarded(Map, parent, clear) renderToObjectUnguarded(Map, parent, clear)
renderUnguarded(parent)           renderToObjectUnguarded(parent) [private]
errors() : List<Exception>
toStringTree() : String
withContextHolder(ContextHolder)
getParseTree()
```

Port public API (Read):
```
render(JMap)
render()
renderToObject(JMap)
renderToObjectUnguarded(JMap, parent, isInclude: Boolean)   // signature differs
```

**Missing in port:**
- `render(String jsonMap)` overload — silently absent. Anyone calling `template.render("{\"x\":1}")` gets a different code path or compile error.
- `render(Inspectable)`
- `render(String key, Object value, Object... kvs)` varargs convenience
- `render(Map, ExecutorService, boolean)` — render-time limiting via Future
- `renderUnguarded` variants — protection settings are bypassed differently
- `errors() : JList[Exception]` — there's no way to read template errors after WARN-mode render
- `toStringTree()` — debug aid
- `withContextHolder(ContextHolder)` / `ContextHolder` class — used by custom insertions to surface non-string state
- `getParseTree()` — debug
- **Render-time guard**: original `renderToObject(Map)` checks `templateParser.isRenderTimeLimited()` and uses an `ExecutorService` + `Future.get(maxRenderTimeMillis)` to enforce `withMaxRenderTimeMillis`. Port's `renderToObject` does **none of this** — `limitMaxRenderTimeMillis` is stored on the parser but **never checked anywhere**. (Verified by Read of Template.scala lines 39–50 — no reference to `limitMaxRenderTimeMillis`.)
- **Max template size guard**: original checks `this.templateSize > maxTemplateSizeBytes`; port doesn't track template size at all.
- **Root folder registry**: original `setRootFolderRegistry(templateContext, sourceLocation)` populates `REGISTRY_ROOT_FOLDER` for `include_relative` resolution; port's `Template.scala` has no `sourceLocation` field and never populates the registry. `IncludeRelative` will not work for any template parsed from disk.
- **Environment map configurator** invoked via `newRootContext` in original; port's `renderToObject` never calls any configurator.

### 4.4 TemplateContext.java vs TemplateContext.scala

Read both. Mostly faithful. Gaps:
- Port has no `Template template` field (original carries reference for upward navigation).
- Port `getDateParser` is **absent** (no `BasicDateParser` exists).
- Port `getRootFolder` returns `Any`, original returns `Path`.
- `errorsList` is shared via constructor parameter rather than per-context. The semantics are *almost* equivalent but in original each child context shares the parent's list via `this(parent.template, parent.getParser(), new LinkedHashMap())` — same behavior. **OK.**

### 4.5 RenderTransformer + RenderTransformerDefaultImpl

Read both. Behaviorally equivalent. Port uses an explicit state machine (0/1/2) instead of mutating a closure. **OK.**

### 4.6 blocks/For.java vs For.scala

Read both. Behaviorally equivalent — limit/offset/reversed/range, `offset:continue`, parentloop, all forloop fields (length/index/index0/rindex/rindex0/first/last/name/parentloop). **OK.** One behavioral nit: port's `renderForLoopBody` `boundary { ... break(false) }` for CONTINUE returns `false` from the body, then enclosing while will try to read `isBreak = false` and continue — but then it has already broken out of the body loop, which mirrors original's `break;`. **OK.**

### 4.7 blocks/Tablerow.java vs Tablerow.scala

Read both. All cols/limit/offset and tablerowloop fields ported 1:1. **OK.**

### 4.8 blocks/Case.java + If.java + Unless.java vs ports

Case: Read both — behaviorally equivalent.
**Did not read** `If.java`, `If.scala`, `Unless.java`, `Unless.scala`, `Ifchanged`, `Comment`, `Capture`, `Cycle`, `Raw`, `Block` in this session — **unverified** but file names match 1:1 and the migration db has no `wip`/`todo` rows for them.

### 4.9 tags/Include.java + IncludeRelative.java vs ports

Include.java vs Include.scala (Read both):
- `with` clause: present in both. **OK.**
- Jekyll-style param passing: port silently drops parameters that aren't `JMap` (port lines 57–63 have a no-op default branch). Original would `ClassCastException`. **Minor divergence** — port is more lenient.
- `for` clause: **NOT present in original liqp Include.java either** — it's only in real Shopify Liquid. Both are equivalent in this respect.
- Snippets dirs: original uses `context.getParser().nameResolver.resolve(includeResource)`; port does the same via `context.parser.nameResolver.resolve`. **OK.**
- **Critical missing**: original passes `context` and `true` (clearThreadLocal) to `template.renderToObjectUnguarded(variables, context, true)`. Port does the same call but `Template.renderToObjectUnguarded`'s third parameter is `isInclude: Boolean`, not `doClearThreadLocal`. The semantics are different: original always uses the parent context as the parent of the new render context; port branches on `isInclude` and `parentContext.newChildContext(...)` if true, else creates a brand-new root context. With `isInclude=true` (which Include passes), behavior matches. **OK** for include, but the parameter rename is confusing and the unrelated callsites (`{% include %}` vs new entrypoint) would diverge.

`IncludeRelative.java` vs `IncludeRelative.scala`: **not Read in this session — unverified**, but file names match and there's no migration-db skipped entry.

### 4.10 filters/Date.java + filters/date/* vs Date.scala

**Major behavioral gap.** Original Date filter (Read):
1. Uses `context.getDateParser().parse(valAsString, locale, defaultTimeZone)` for unparseable strings. The default `Parser` (Read) hosts **63 distinct `datePatterns`** including `"EEE MMM d hh:mm:ss yyyy"`, `"yyyy-M-d HH:mm:ss.SSSSSSSSS Z"`, RFC-style `"EEE, d MMM yyyy HH:mm:ss Z"`, and bare components like `"MMMM yyyy"`, `"H:mm"`, `"d MMM"`. Pre-normalizes Ruby ordinals (`1st`/`2nd`/`3rd`/`4th-9th`/`0th`).
2. Uses `BasicDateParser.getZonedDateTimeFromTemporalAccessor` (Read) which fills in missing fields from `now`, handles `LocalDate`/`LocalTime`/`Instant`/zoned/unzoned.
3. Uses `ua.co.k.strftime.StrftimeFormatter.ofSafePattern` — a real Ruby-compatible strftime implementation.
4. Custom date types via `CustomDateFormatRegistry`.

**Port's Date filter** (Read `filters/Date.scala` and the inline `strftimeToJava`):
1. Tries `ZonedDateTime.parse(valAsString)` (only ISO-8601), then `LocalDate.parse(valAsString).atStartOfDay(systemDefault)` (only ISO `yyyy-MM-dd`). **No other patterns work.**
2. Hand-rolled `strftimeToJava` that supports a fixed set of directives: `%Y %y %m %d %H %M %S %L %p %P %A %a %B %b %h %Z %z %j %e %k %l %I %u %w %n %t %% %-d %-m %-H %-M %-S %-I %-e`. Anything else falls through as a literal `'%X'`.
3. Hard-codes `java.time.ZoneId.systemDefault()` in the epoch-millis branch, ignoring `TemplateParser.defaultTimeZone` (which doesn't exist in the port anyway).

**Directives missing or wrong in port** (vs Ruby strftime):
- `%C` (century)
- `%D` (`%m/%d/%y`)
- `%F` (`%Y-%m-%d`)
- `%G %g %V %v` (ISO week-based year + week number)
- `%N` (fractional seconds; port has `%L` only for milliseconds)
- `%R` (`%H:%M`)
- `%r` (12-hour with am/pm)
- `%s` (epoch seconds)
- `%T` / `%X` (`%H:%M:%S`)
- `%U %W` (week of year, Sunday/Monday first)
- `%x` (locale date)
- `%c` (locale datetime)
- `%+` (date(1))
- `%:z` `%::z` `%:::z` (zone offset variants)
- `%0d %_d` width/padding modifiers (port handles only `%-X`)

**Input strings the port can no longer parse** (anything that isn't ISO-8601):
- `"March 1st 2024"` (was supported via `1st→1` normalization + `MMMM d yyyy` pattern)
- `"01/15/2024"` (was supported via `M/d/yyyy`)
- `"2024-01-15 10:30:00 -0500"` (was supported)
- `"Mon, 15 Jan 2024 10:30:00 +0000"` (RFC 2822, was supported)
- `"15 Jan"` / `"January 2024"` / `"10:30"` standalone forms

**Severity: Major.** Jekyll sites depend heavily on date formatting in feeds, post URLs, archive headers. Anything beyond `{{ page.date | date: "%Y-%m-%d" }}` is at risk.

### 4.11 filters/Where.java + filters/where/* vs Where.scala

Original (Read `Where.java`, `JekyllWhereImpl.java`):
- Two delegate impls — Liquid and Jekyll — with substantially different semantics.
- Jekyll path: uses `PropertyResolverHelper.findFor(item)` to get an adapter, supports `Inspectable`/`Map`/POJOs via property paths.
- `compare_property_vs_target` handles `nil`/`empty`/`blank` literal targets, single-string property AND collection-property (matches if any element equals target).
- `parse_sort_input` coerces numeric strings to Doubles for proper sorting.
- `item_property` supports dotted property paths (`address.city`) by `split(".").reduce`.
- Caches results per `(input, property, value)` triple.

Port `Where.scala` (Read):
- Single inline implementation, ~70 lines.
- `getProperty` only handles `JMap` and `Inspectable`. **POJOs unsupported.**
- **No dotted property path support** — `where: "address.city", "Boston"` will look up the literal key `"address.city"` in the map, not navigate.
- No `nil`/`empty`/`blank` literal handling.
- No collection-property matching (if `propValue` is itself a list, original tries each element against target; port just `LValue.areEqual(list, target)` which is always false).
- No numeric coercion of string properties.
- No caching (acceptable).
- The `liquidStyleWhere` flag plumbing is broken anyway because `Builder` has no `withLiquidStyleWhere` setter (see 4.1).

**Severity: Major** — `where` is one of the most-used Jekyll filters for collection iteration (`site.posts | where: "category", "blog"`).

### 4.12 filters/Json.java vs Json.scala

Original (Read): one-line wrapper around Jackson `ObjectMapper.writeValueAsString`. Port (Read): hand-rolled serializer.

**Port loses:**
- Cycle detection (Jackson throws on cycles; port will infinite-loop and StackOverflow).
- Java time module (Jackson with `JavaTimeModule` serializes `Instant` as `"2024-01-15T10:30:00Z"`; port will call `other.toString` and quote whatever Java prints).
- Jekyll's `to_liquid` / `Inspectable` introspection (port doesn't recognize Inspectable in `toJson`).
- BigDecimal precision (port calls `n.toString` which works fine for BigDecimal, but Jackson can write it as a JSON number; port writes it as a number too via `Number` branch — actually OK).
- `null` keys in maps will be quoted as `"null"` (line 65 calls `String.valueOf(entry.getKey)`); Jackson would error.
- No `@JsonProperty` / no view annotations / no custom serializers.
- Floats: Jackson serializes `Double.NaN`/`Infinity` as numbers (or errors); port writes `n.toString` which is `"NaN"` (invalid JSON, no quotes).

**Severity: Medium-major** depending on use case. For straightforward `{"key": "value"}` page front-matter dumps, both work. For `{{ site | jsonify }}` (jsonify is the Jekyll alias), failure is likely.

### 4.13 filters/Sort, Sort_Natural, MapFilter, Group_By

**Not Read in this session — unverified.** File names exist on both sides (port has `Sort.scala`, `Sort_Natural.scala`, `MapFilter.scala`). No `Group_By` filter exists in either source. Migration db says no skipped entries here.

### 4.14 Parser — does Scala parser cover every grammar production?

Original `LiquidParser.g4` rules (Read all 371 lines):
```
parse, block, atom, tag, continue_tag, other_tag, error_other_tag, simple_tag,
empty_tag, raw_tag, raw_body, comment_tag, if_tag, elsif_tag, else_tag, unless_tag,
case_tag, when_tag, cycle_tag, cycle_group, for_tag, for_array, for_range, for_block,
for_attribute, attribute, table_tag, capture_tag, include_tag, include_relative_tag,
file_name_or_output, jekyll_include_params, output, not_out_end, filter, params,
param_expr, assignment, expr, term, lookup, id, id2, index, other_tag_parameters,
other_than_tag_end, filename, outStart, other
```
That's ~46 productions.

Port `LiquidParser.scala` (Read first 100 lines + grepped for skip/Skip): I can confirm via Grep that the parser has methods for:
- `parseBlock`, `parseAtom`, `parseTag`, `parseTextNode`, `parseOutput`, `parseAssignment`
- `parseCase` (line 318 mentions "case and first when")
- `parseComment` (line 512 mentions "endcomment")
- ~896 lines total based on the highest line number I observed (`peekAfterTagStart` at line 896)

**Did not exhaustively map every grammar rule to a Scala method** — this is **unverified** at the rule-by-rule level. The grammar productions most at risk:
- `error_other_tag` branches: 4 distinct error-recovery cases (mismatched, invalid, missing-end, invalid-tag, invalid-empty). The port's grep showed `// Skip unexpected token` at line 103 — so the port likely has *some* error recovery, but probably not the same 4 cases with the same error messages.
- `output` rule has 3 alternatives keyed on `isStrict()`/`isWarn()`/`isLax()` — strict mode requires `term filter*`, warn/lax allows trailing `not_out_end?`. **Unverified** whether port matches.
- `not_out_end` and `other_than_tag_end` accept a long list of token types. **Unverified.**
- `id` rule allows ALL keywords as identifiers (`If`, `Else`, `Endif`, etc., as a variable name). This Liquid quirk lets `{% assign if = 1 %}` work. **Unverified** in port.
- `evaluateInOutputTag` semantic predicate — original uses it to switch between `term` and `expr` in output. **Unverified** in port.

### 4.15 Nodes — all 19 types?

Verified by Glob: 19 `.scala` files in `nodes/` matching all 19 `.java` files name-for-name. **OK as filenames.** Behavior of each not verified individually.

### 4.16 spi/* — entirely skipped (MAJOR HOLE)

Read `TypesSupport.java`, `SPIHelper.java`, `BasicTypesSupport.java`. Did not Read `Java7DateTypesSupport.java` or `Java8DateTypesSupport.java`.

**What it provides in original liqp:**
1. **`TypesSupport` (interface)** — contract `void configureTypesForReferencing(ObjectMapper)` + `default void configureCustomDateTypes()`.
2. **`SPIHelper`** — `ServiceLoader.load(TypesSupport.class)` discovers `META-INF/services/liqp.spi.TypesSupport` providers, applies them to the ObjectMapper and to the date registry.
3. **`BasicTypesSupport`** — base class with `registerType(SimpleModule, Class<T>)` that installs a Jackson `StdSerializer` which writes `{"@supportedTypeMarker":true,"@ref":"<key>"}` instead of serializing the object, and a `ThreadLocal<Map>` `local` that stashes the original object. `restoreObject(Object)` reverses the marshalling. `clearReferences()` is called from `Template.renderToObjectUnguarded`.
4. **Java7DateTypesSupport / Java8DateTypesSupport** — register `java.util.Date`/`java.time.*` so they survive eager Jackson conversion intact.

**What the port loses by skipping all of this:**
- Custom user-defined types (e.g., a `Money` class, a `Polygon` class) cannot survive `LiquidSupport`/eager-evaluation roundtrips. In original liqp, a user could implement `TypesSupport` in their own jar, drop it on the classpath, and Liqp would auto-register it via `ServiceLoader`.
- `java.util.Date` (legacy) and `java.time.LocalDateTime`/`Instant`/`ZonedDateTime` are auto-registered as opaque references in original. Port handles `TemporalAccessor` directly via `LValue.isTemporal` so the **most common** date types do flow through, but anything custom is lost.
- The whole "marshall to Jackson, then unmarshal back to Java objects via @ref" round-trip is gone. In the port this isn't needed because `LiquidSupport` is a stub and `evaluateMode=EAGER` is a no-op (`Template.scala` lines 40–46: both LAZY and EAGER cases just `new LinkedHashMap[String, Any](variables)`).
- `Template.renderToObjectUnguarded` in original calls `BasicTypesSupport.clearReferences()` to clear the ThreadLocal between renders. Port doesn't.

**Severity: Major if the port advertises custom-type extensibility; Minor if SSG only ever feeds `JMap[String,Any]` from front-matter parsing.** Since SSG's intended use case is YAML/Markdown front-matter, the practical impact is low — but the doc should call this out.

### 4.17 exceptions/* — all 4 ported?

Verified by Glob: all 4 present in port. Contents not Read individually — **filenames OK**.

### 4.18 LiquidSupport / Inspectable

`ssg-liquid/src/main/scala/ssg/liquid/parser/LiquidSupport.scala:14` self-describes as **"This is an initial stub. Full implementation in Phase 4."** (verified by Grep). The `evaluate` method on `TemplateParser.scala:75-83` returns a degenerate impl:
```scala
def evaluate(variable: Any): LiquidSupport =
  variable match {
    case ls: LiquidSupport => ls
    case _ =>
      new LiquidSupport {
        override def toLiquid(): JMap[String, Any] = new java.util.HashMap[String, Any]()
      }
  }
```
This **silently returns an empty map** for any non-`LiquidSupport` input. Original (`TemplateParser.java:122-131`) wraps it in a Jackson-backed `LiquidSupportFromInspectable`. Combined with `Template.scala`'s `renderToObject` which doesn't call `templateParser.evaluate(variables)` at all (it just `new LinkedHashMap(variables)`), the port effectively **does not support `Inspectable` POJOs** — only raw `JMap`.

**Severity: Major** if anyone passes a Java/Scala bean to `render(...)`. **Minor** if SSG always converts front-matter to `JMap` upstream.

---

## 5. Stubs / shortcuts / TODOs in port

Grep result for `TODO|FIXME|XXX|\?\?\?|stub|simplified|for now|not supported|placeholder|UnsupportedOperationException|NotImplemented|Phase 4|skip` (case-insensitive):

| File:line | Excerpt | Classification |
|---|---|---|
| `Template.scala:12` | "Simplified API — parse returns Template, render returns String" | **Major gap marker** — this is the line that hides §4.3 above |
| `antlr/NameResolver.scala:46` | `throw new UnsupportedOperationException(...)` | **Need to read context** — likely cross-platform stub for non-JVM. Read below. |
| `parser/LiquidParser.scala:103` | `// Skip unexpected token` | **Cosmetic / minor** — error recovery branch |
| `parser/LiquidParser.scala:318` | `// Skip any text between case and first when` | **Cosmetic** — matches grammar |
| `parser/LiquidParser.scala:512` | `// Skip everything until we find {% endcomment %}` | **Cosmetic** |
| `parser/LiquidSupport.scala:14` | "This is an initial stub. Full implementation in Phase 4." | **MAJOR GAP** — see §4.18 |
| `parser/LiquidLexer.scala:*` (many) | various `skipWhitespace` etc. | **Cosmetic** — normal lexer impl |
| `filters/Where.scala:11` | "Simplified where filter — inline implementation instead of delegate classes" | **MAJOR GAP** — see §4.11 |

The Grep also missed (because of regex word boundaries) — let me note that I did not search for additional patterns like methods returning `null`/`""` immediately. **Unverified** for that subclass.

---

## 6. Tests

### Original liqp tests (Glob, partial listing — Glob hit truncation; only a portion shown)

The Glob returned at least the following test files (truncated by tool limit):
- Top-level: `ConditionTest`, `InsertionTest`, `LValueTest`, `ProtectionSettingsTest`, `ReadmeSamplesTest`, `RenderSettingsTest`, `StatementsTest`, `TemplateTest`, `TestUtils`
- `blocks/`: 10 tests (Capture, Case, Comment, Cycle, For, If, Ifchanged, Raw, Tablerow, Unless)
- `filters/`: 56 tests (1 per filter, plus `Where_ExpTest` — note no top-level `WhereTest`)
- `filters/date/`: `FuzzyDateDateParserTest`
- `filters/where/`: `JekyllWhereImplTest`, `LiquidWhereImplTest`
- `nodes/`: `AndNodeTest`, `AtomNodeTest`, `BlockNodeTest`, `ComparingExpressionNodeTest`, `ContainsNodeTest`, `EqNodeTest`, `GtEqNodeTest`, `GtNodeTest`, `LookupNodeTest`, `LtEqNodeTest`, `LtNodeTest`, `NEqNodeTest`, `OrNodeTest`, `OutputNodeTest`
- `parser/`: `LiquidSupportTest`, `ParseTest`, `parser/v4/LiquidLexerTest`, `parser/v4/LiquidParserTest`
- `tags/`: `AssignTest`, `DecrementTest`, `IncludeRelativeTest`, `IncludeTest`, `IncrementTest`, `WhitespaceControlTest`, `WhitespaceWindowsControlTest`

Glob result was truncated, so the complete count is **unverified**. Visible files alone are ~94. Real total is likely 100+.

### Port tests (Glob, complete)

12 suites, **280 tests** total (verified by `^  test\(` count = 280):

| Suite | tests |
|---|---|
| `BlocksSuite` | 71 |
| `EdgeCaseSuite` | 33 |
| `FilterArraySuite` | 27 |
| `FilterStringSuite` | 28 |
| `FilterStringBisectSuite` | 2 |
| `FilterMathSuite` | 23 |
| `ExpressionSuite` | 26 |
| `LiquidSuite` | 20 |
| `IncludeSuite` | 12 |
| `AdvancedSuite` | 20 |
| `ProtectionSuite` | 12 |
| `NativeDiagSuite` | 6 |

### Categories of original tests with NO Scala counterpart

- `LValueTest.java` → no `LValueSuite.scala` (port aggregates LValue checks into `ExpressionSuite`/`EdgeCaseSuite`?). **Unverified** that coverage is equivalent.
- `TemplateTest.java` (the most important suite — covers `render(String jsonMap)`, `render(Inspectable)`, `render(key,value,kvs)`, ContextHolder, errors, toStringTree, parse(Path)/File/InputStream/Reader/CharStream) → **no equivalent**. Given §4.3, this is the smoking gun.
- `TemplateContextTest.java` (mentioned in task — actually called `InsertionTest`/`ConditionTest`/`StatementsTest` in original) → **no port equivalent named** like that.
- `WhereExpTest.java` exists in original; port has `where_exp` filter file but no test suite specifically for it. **Unverified** if `FilterArraySuite` covers it.
- `DateTest.java` exists; port has no `DateSuite.scala`. Date coverage in `FilterStringSuite` is **unverified**.
- `IncludeTest.java` and `IncludeRelativeTest.java` exist; port has only `IncludeSuite.scala` (12 tests). Likely not 1:1 coverage.
- `ProtectionSettingsTest.java` ↔ `ProtectionSuite.scala` (12 tests). Likely partial.
- `parser/LiquidLexerTest.java` and `parser/LiquidParserTest.java` are golden test files for the ANTLR lexer/parser. Port has neither — no direct lexer/parser unit tests.
- `nodes/*Test.java` (14 files) — port has 0 node-level tests.
- `filters/*Test.java` (~56) — port aggregates into 4 filter suites totaling 80 tests (28+27+23+2). This is a **70% coverage gap** even before checking which specific filter behaviors are exercised.

**Conclusion: the port has roughly 280 test cases while the original has at minimum 100+ test files each containing many test methods (likely 1500+ assertions in total). Unverified exact ratio, but the gap is at least 5×.**

---

## 7. Reference test corpora to vendor

Glob `/Users/dev/Workspaces/GitHub/ssg/original-src/liqp/src/test/resources/**/*.*` returned **No files found**. Glob `/Users/dev/Workspaces/GitHub/ssg/.claude/worktrees/elegant-booping-pond/ssg-liquid/src/test/resources/**/*` also returned **No files found**.

**Liqp ships ZERO `.liquid` template fixtures or expected-output files in `src/test/resources/`.** All tests are inline strings inside `.java` files. There is nothing to vendor verbatim. The port will need to either (a) extract inline strings from the Java tests into `.scala` test methods, or (b) curate its own corpus.

---

## 8. Liquid spec compliance

Grep result: nothing found referencing Shopify Liquid acceptance suite, golden files, or `liquid-spec` in either repo. Both projects rely entirely on their own test suites. No locally-available external spec.

---

## 9. Severity-ranked summary

### Major gaps (block production use as a Jekyll replacement)

1. **`filters/Where.scala` is a 70-line rewrite** (vs 5 files in original).
   - Evidence: Read `Where.java`, `JekyllWhereImpl.java`, `Where.scala`.
   - Missing: dotted property paths (`where: "address.city", "Boston"`), POJO support, `nil`/`empty`/`blank` literal targets, collection-property matching, numeric coercion.
   - Action: Port `JekyllWhereImpl.java` and `LiquidWhereImpl.java` faithfully; add `WhereFilterSuite` translating `JekyllWhereImplTest.java` and `LiquidWhereImplTest.java`.

2. **`filters/Date.scala` cannot parse non-ISO date strings.**
   - Evidence: Read `Date.java`, `Date.scala`, `Parser.java`, `BasicDateParser.java`.
   - Missing: 63 fallback date patterns from `Parser.java`, Ruby ordinal normalization, `getZonedDateTimeFromTemporalAccessor` field-fill-from-now, ~15 strftime directives (`%C %D %F %G %g %V %v %N %R %r %s %T %X %U %W %x %c %+ %:z %::z`).
   - Action: Port `BasicDateParser.java` + `Parser.java` (no SPI dep needed; just the pattern list and parse loop). Replace inline `strftimeToJava` with a more complete Ruby-strftime implementation. Add `DateFilterSuite` translating `DateTest.java`.

3. **`Template.scala` has a 14% size ratio vs `Template.java`** — major API and feature loss.
   - Evidence: Read both files.
   - Missing: render-time guard (Future + ExecutorService), max-template-size guard, `errors()` accessor, `render(String jsonMap)`, `render(Inspectable)`, `render(key,value,kvs*)`, `withContextHolder`, `setRootFolderRegistry` for include_relative, env-map configurator invocation.
   - Action: Port the missing methods. **The render-time guard is critical for any user-facing template engine** — without it `withMaxRenderTimeMillis` is a no-op.

4. **`TemplateParser.Builder` is missing 7 setters**, including `withLiquidStyleWhere` (the field exists but no setter), `withDefaultTimeZone`, `withEnvironmentMapConfigurator`, `withSnippetsFolderName`, `withDateParser`, `withObjectMapper`, `withStripSingleLine`. `parse(Path/File/InputStream/Reader)` overloads also missing.
   - Evidence: Read both `TemplateParser.java` and `TemplateParser.scala`.
   - Action: Add the missing setters; add file/stream parse overloads; wire `defaultTimeZone` and `environmentMapConfigurator` through to TemplateContext.

5. **`LiquidSupport` is a self-declared stub.**
   - Evidence: `parser/LiquidSupport.scala:14` Grep result; `TemplateParser.scala:75-83`.
   - Effect: Any non-`JMap` variable passed to `render` is silently converted to an empty map.
   - Action: Either port `LiquidSupport.LiquidSupportFromInspectable` (Java reflection-based — needs cross-platform consideration) or document loudly that only `JMap[String,Any]` inputs are supported and have `render` reject other types instead of silently dropping.

6. **`Template.renderToObject` ignores `evaluateMode=EAGER`** — both branches do the same thing (Read lines 40–46).
   - Action: Either implement EAGER (requires LiquidSupport) or remove the enum.

### Medium gaps (advanced features, edge cases)

1. **`filters/Json.scala` is a hand-rolled serializer** — no cycle detection (StackOverflow risk), no Java time module support, doesn't recognize `Inspectable`, NaN/Infinity produce invalid JSON, null map keys are quoted as `"null"`.
   - Action: Add cycle detection at minimum; add `Inspectable` and `TemporalAccessor` cases; add NaN/Infinity guard. Vendor a well-tested JSON encoder if available (`scala.util.parsing.json` is deprecated; consider `upickle` or `borer` if cross-platform).

2. **`spi/*` package skipped — no extension mechanism for custom types.**
   - Evidence: Read `TypesSupport.java`, `SPIHelper.java`, `BasicTypesSupport.java`; Glob confirms 0 files in port.
   - Action: Document the loss in the port doc. SPI via `ServiceLoader` doesn't work on JS/Native anyway, so a Scala-3-typeclass-based replacement (e.g., a `given LiquidTypeSupport[T]` mechanism) would be the idiomatic solution — design + implement.

3. **`LValue.asRubyDate` fragile for non-ZonedDateTime temporals**: returns `now()` instead of converting `LocalDate`/`LocalTime`/`Instant` properly.
   - Evidence: Read `LValue.scala:283-290` vs `LValue.java:214-222`.
   - Action: Port `BasicDateParser.getZonedDateTimeFromTemporalAccessor` and call it.

4. **No node-level unit tests, no lexer/parser-level unit tests.** Original has 14 `nodes/*Test.java` and 2 `parser/v4/Liquid{Lexer,Parser}Test.java`.
   - Action: Add at least one suite per AST node and a `LexerSuite` + `ParserSuite`.

5. **`include_relative` will not resolve correctly** because `Template.scala` never sets `REGISTRY_ROOT_FOLDER`.
   - Evidence: Read `Template.java:382-387` (sets the registry) vs `Template.scala` (no source location, no registry write).
   - Action: Add a `sourceLocation: Option[Path]` parameter to `Template` and populate the registry in `renderToObject`.

6. **`Template.errors()` accessor missing** — WARN error mode is plumbed through `TemplateContext.addError` but no way to read errors after render.
   - Action: Expose `template.errors` (just delegate to last context).

7. **Port has no `BasicDateParser` field or accessor on `TemplateContext`** — anything that calls `context.getDateParser()` (in original liqp filters) would fail. Since the filters were rewritten, this is currently unused, but it's a latent gap when porting more filters.

8. **`renderForLoopBody` in `For.scala` line 154**: `break(false)` from a `boundary { var isBreak ... }` returns `false` from the `boundary` block, meaning the outer loop reads `isBreak = false` and continues to the next iteration — which is the **correct** continue semantics. **OK**, but easy to misread.

### Minor gaps (polish)

1. **Default flavor changed silently from LIQP to JEKYLL.** Migration footgun for users coming from liqp; document in the port doc.
2. **`Include.scala` silently drops non-Map parameters** in Jekyll branch (line 60–62 has empty default case). Original would `ClassCastException`. Pick one and document.
3. **`asFormattedNumber` rounding diverges** for trailing-zero values (`5.50` vs `5.5`). Edge case but observable.
4. **Test resources directory missing** — there's no `src/test/resources/` in either repo, but the port should grow one for golden-file testing of include / template-loading flows.
5. **Multiple `withSpaceAroundTags` overloads** — port has the 2-arg form but no standalone `withStripSingleLine`. Add for parity.
6. **`Nullable.scala` exists in port but not in original** — that's a port-side helper. **Not a gap, just noted.**
7. **`PlainBigDecimal.scala` differs from Java** — doc mentions Native workaround. Verified earlier in port doc.
8. **Comment in `parser/LiquidParser.scala:103` "Skip unexpected token"** — error recovery branch is silent; original throws `LiquidException` with line/column. **Minor UX gap.**

---

## 10. Estimated remaining work

**Math** based on the SLOC observations in §3 (using only files I actually Read line-counts for, since I cannot run wc):

| Area | Original lines | Port lines | Expected ratio (×0.7) | Actual ratio | Implementation completeness |
|---|---|---|---|---|---|
| `Template.java`         | 504 | 69  | ~353 | 14%  | **~20%** (most API surface missing) |
| `TemplateParser.java`   | 562 | 247 | ~393 | 44%  | **~63%** (builder + parse overloads missing) |
| `TemplateContext.java`  | 214 | 165 | ~150 | 77%  | **~95%** (very close, minor gaps) |
| `LValue.java`           | 511 | 324 | ~358 | 63%  | **~85%** (temporal coercion fragile) |
| `RenderTransformerDefaultImpl.java` | 59 | 68 | ~41 | 115% | **~100%** |
| `blocks/For.java`       | 343 | 298 | ~240 | 87%  | **~100%** |
| `blocks/Tablerow.java`  | 176 | 160 | ~123 | 91%  | **~100%** |
| `blocks/Case.java`      | 57  | 62  | ~40  | 109% | **~100%** |
| `tags/Include.java`     | 64  | 78  | ~45  | 122% | **~95%** |
| `filters/Date.java` + `filters/date/*` (5 files, only Date.java + Parser.java + BasicDateParser.java Read; ~272 visible Java lines) | ~272+ | 141 | ~190 | 51% on visible code, **but the missing 5-file `date/` package is ~0%** | **~25%** (parsing essentially absent; strftime ~60% of directives) |
| `filters/Where.java` + `filters/where/*` (Where.java + JekyllWhereImpl.java Read; ~260 visible Java lines + 3 unread files) | ~260+ + unread | 72 | unknown | very low | **~30%** (Jekyll mode partial, Liquid mode partial, no resolver helper, no nested paths) |
| `filters/Json.java`     | 23  | 96  | ~16  | 417% | **~50%** (no cycles, no Inspectable, no time, NaN broken) |
| `spi/*` (5 files; 11+47+82 = 140 visible Java lines + 2 unread) | ~140+ | 0 | ~98 | **0%** | **0%** |
| `filters/date/*` (5 files; 102+173 = 275 visible + 3 unread) | ~275+ | 0 | ~193 | **0%** | **0%** (relies on stubbed inline date parser in Date.scala) |
| `filters/where/*` (5 files; 215 visible + 4 unread) | ~215+ | 0 | ~150 | **0%** | **0%** |
| Other blocks/tags/filters not Read | unmeasured | unmeasured | — | — | likely ~90% based on 1:1 file mapping and migration db status |

**Aggregate rough estimate** of liqp's intended scope that is actually implemented in the port:

- **Filenames (top-of-funnel):** `nodes/` 19/19, `blocks/` 11/11, `tags/` 8/8, `exceptions/` 4/4, `filters/` 56/56, `parser/` 6/4 (different shape), `antlr/` 2/4, `spi/` 0/5, `filters/date/` 0/5, `filters/where/` 0/5, top-level 11/11. **Sum: 117 / 138 = ~85%** of files have a counterpart.

- **Behavioral / SLOC weighted:** taking into account that the missing `spi/` + `filters/date/` + `filters/where/` packages plus the gutted `Template`/`Where`/`Date`/`LiquidSupport` represent the "hard" code, my estimate is **the port implements roughly 60–70% of liqp's intended runtime behavior**. The bulk of straightforward filters/blocks/tags are present and correct; the configurability, extensibility, and "Jekyll-completeness" surface is much thinner.

- **Test coverage weighted:** **~20–30%** of original test count. Port has 280 tests; original has well over 1000 (unverified exact number).

**Recommended remaining work to reach production-Jekyll-replacement parity:**

1. (1–2 days) Fix `TemplateParser.Builder` setters and `parse(...)` overloads.
2. (1 day) Wire `Template.errors()`, `setRootFolderRegistry`, render-time/max-size guards.
3. (2–3 days) Port `filters/date/Parser.java` + `BasicDateParser.java` and rebuild a real strftime (consider vendoring an existing pure-Java/Scala strftime).
4. (2 days) Port `filters/where/JekyllWhereImpl.java` + `LiquidWhereImpl.java` + `PropertyResolverHelper`.
5. (1 day) Decide on `LiquidSupport` strategy: either implement reflectively (JVM-only) with stubs on JS/Native, or remove the enum and document `JMap`-only support.
6. (1 day) Decide on `spi/*` strategy: design a Scala-3 typeclass replacement or formally drop SPI extensibility from the port doc.
7. (3–5 days) Expand test coverage: add `LexerSuite`, `ParserSuite`, `NodeSuite`, `WhereFilterSuite`, `DateFilterSuite`, `TemplateSuite` (covering the missing render overloads and error mode), `IncludeRelativeSuite`. Translate `TemplateTest.java`, `WhereTest.java`, `DateTest.java`, `Where_ExpTest.java`, the 14 nodes tests, and the 2 parser tests.
8. (1 day) Update `docs/architecture/liqp-port.md` to call out the gaps documented here.

**Total: roughly 12–15 engineering days** for full Jekyll-replacement parity. Less if SPI and LiquidSupport are explicitly out of scope.

---

## Appendix: files Read in this session (for audit)

Original (Java/grammar):
- `liqp/Template.java`, `TemplateParser.java`, `TemplateContext.java`, `LValue.java`, `RenderTransformerDefaultImpl.java`
- `liqp/blocks/For.java`, `Tablerow.java`, `Case.java`
- `liqp/tags/Include.java`
- `liqp/filters/Date.java`, `Where.java`, `Json.java`
- `liqp/filters/date/BasicDateParser.java`, `Parser.java`
- `liqp/filters/where/JekyllWhereImpl.java`
- `liqp/spi/TypesSupport.java`, `SPIHelper.java`, `BasicTypesSupport.java`
- `src/main/antlr4/liquid/parser/v4/LiquidParser.g4`

Port (Scala):
- `Template.scala`, `TemplateParser.scala`, `TemplateContext.scala`, `LValue.scala`, `RenderTransformerDefaultImpl.scala`
- `blocks/For.scala`, `Tablerow.scala`, `Case.scala`
- `tags/Include.scala`
- `filters/Date.scala`, `Where.scala`, `Json.scala`
- `parser/LiquidParser.scala` (first 100 lines), `parser/Flavor.scala`
- `docs/architecture/liqp-port.md`

Glob/Grep coverage:
- All `.java` files under `original-src/liqp/src/main/java/liqp/`
- All `.scala` files under `ssg-liquid/src/main/scala/ssg/liquid/`
- All `.scala` files under `ssg-liquid/src/test/scala/`
- Test resources directories (both confirmed empty)
- `ssg-dev db migration list --lib liqp --status {skipped,todo,wip}`
- Port-wide grep for stubs/TODOs

Files in port **not Read** in this session (claims about them are filename-only or unverified):
- `blocks/Block.scala`, `Capture.scala`, `Comment.scala`, `Cycle.scala`, `If.scala`, `Ifchanged.scala`, `Raw.scala`, `Unless.scala`
- `tags/Assign.scala`, `Break.scala`, `Continue.scala`, `Decrement.scala`, `IncludeRelative.scala`, `Increment.scala`, `Tag.scala`
- All `nodes/*.scala`
- All `exceptions/*.scala`
- 53 of 58 filter files
- `parser/LiquidLexer.scala` (full), `LiquidParser.scala` (lines 100–end), `Token.scala`, `Inspectable.scala`, `LiquidSupport.scala` (only line 14 grepped)
- `Insertion.scala`, `Insertions.scala`, `Nullable.scala`, `PlainBigDecimal.scala`, `RenderTransformer.scala`
- `antlr/NameResolver.scala` (line 46 grepped only), `LocalFSNameResolver.scala`

— end of report —
