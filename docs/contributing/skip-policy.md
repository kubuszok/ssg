# SSG Skip Policy

## Purpose

Every file in `original-src/<lib>/` must be one of:

1. **Ported** — there is a corresponding Scala file in
   `ssg-<module>/src/main/scala/...`, audited as `pass`/`minor_issues` in
   `scripts/data/audit.tsv`.
2. **Skipped with documented justification** — listed in
   `scripts/data/skip-policy.tsv` with a category, justification,
   `decided_by`, and (where applicable) `replacement`.

**Anything else** is a silent omission and is treated as a `major_issues`
gap by the audit infrastructure. Unjustified skips are how 25 of the 102
issues from the gap audit became invisible — there was no place to record
"we deliberately did not port this and here's why".

## Categories

The `category` column in `skip-policy.tsv` must be one of:

| Category | Meaning | Example |
|---|---|---|
| `jvm-only-dep` | Only works on JVM with a non-portable library | `flexmark-pdf-converter` (OpenHTMLToPDF), `flexmark-docx-converter` (docx4j) |
| `cli-shell` | Command-line entry point we will reimplement ourselves | terser's `bin/terser`, dart-sass's `bin/sass.dart` |
| `binding-adapter` | IDE / build tool / framework integration we don't consume | flexmark's IntelliJ-PSI bindings, jekyll-minifier's Jekyll plugin glue |
| `reverse-converter` | Output format converters going the wrong direction | `flexmark-html2md-converter`, `flexmark-jira-converter`, `flexmark-youtrack-converter` |
| `deprecated-upstream` | Marked deprecated/unstable by upstream maintainers | `flexmark-util-experimental`, `flexmark-profile-pegdown` |
| `dev-tooling` | Upstream's own test runners, sample apps, or fixture generators | `flexmark-ext-spec-example`, `flexmark-ext-zzzzzz` (template), `flexmark-tree-iteration` (unused utility) |
| `replaced-by-scala-idiom` | Replaced by a better Scala-native implementation | `Pattern.quote` → `RegexCompat.regexEscape`; `Class.getResourceAsStream` → `PlatformResources` |
| `not-yet-migrated` | Future work — **must reference an open issue** and a `review_date` |  |

## Required fields

Every row in `skip-policy.tsv` must have:

- `original_path` — relative to `original-src/`, e.g. `flexmark-java/flexmark-pdf-converter`
- `library` — `flexmark`, `liqp`, `dart-sass`, `jekyll-minifier`, `terser`
- `category` — one of the canonical categories above
- `justification` — 1–3 sentences explaining *why* this is being skipped
- `decided_by` — commit SHA, PR number, or audit date that authorized the skip
- `review_date` — required for `not-yet-migrated`; optional otherwise. Format: `YYYY-MM-DD`
- `replacement` — required for `replaced-by-scala-idiom`; the path of the Scala-native equivalent

## Enforcement

`ssg-dev port skip verify` checks every row has a `category`,
`justification`, and `decided_by`. It is run as part of the CI gate
(Phase 5). Future enhancements (Phase 4 follow-up):

- Walk every file in `original-src/<lib>/<lib>/...` and confirm each has
  either a corresponding Scala port or a skip-policy row. Any file with
  neither fails the audit.
- For `not-yet-migrated` rows, fail if the `review_date` is in the past
  and the issue isn't either resolved or re-scheduled.
- For `replaced-by-scala-idiom`, confirm the `replacement` file exists.

## CLI

```
ssg-dev port skip list [--library L] [--category C]
ssg-dev port skip add <original-path> <category> <justification>
ssg-dev port skip verify
```

## Migration from per-library port docs

The skip lists currently embedded in:

- `docs/architecture/flexmark-port.md` § "Skipped modules"
- `docs/architecture/liqp-port.md` § "Gap analysis"
- `docs/architecture/jekyll-minifier-port.md`
- `docs/architecture/terser-port.md`
- `docs/architecture/sass-port.md`

…are seeded into `scripts/data/skip-policy.tsv` at Phase 4 commit time.
After that point, those per-library docs **reference** the central policy
(`See [skip-policy.md](../contributing/skip-policy.md) for the canonical
list of skipped files`) instead of carrying their own copies. The central
policy is the only place to add or remove skips.
