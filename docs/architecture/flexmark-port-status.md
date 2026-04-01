# flexmark-java Port Status

Quick-reference for the current state of the flexmark-java → `ssg-md` port.

## Module Status

### Utility Modules (11) — All Ported & Tested

| Module | SSG Package | Files | Audit | Tests |
|--------|-------------|-------|-------|-------|
| flexmark-util-misc | `ssg.md.util.misc` | 17 | Pass | Via core |
| flexmark-util-visitor | `ssg.md.util.visitor` | 4 | Pass | Via core |
| flexmark-util-collection | `ssg.md.util.collection` | 32 | Pass | Via core |
| flexmark-util-data | `ssg.md.util.data` | 18 | Pass | Via core |
| flexmark-util-sequence | `ssg.md.util.sequence` | 59 | Pass | Via core |
| flexmark-util-html | `ssg.md.util.html` | 9 | Pass | Via core |
| flexmark-util-options | `ssg.md.util.options` | 8 | Pass | Via core |
| flexmark-util-builder | `ssg.md.util.build` | 1 | Pass | Via core |
| flexmark-util-ast | `ssg.md.util.ast` | 46 | Pass | Via core |
| flexmark-util-dependency | `ssg.md.util.dependency` | 11 | Pass | Via core |
| flexmark-util-format | `ssg.md.util.format` | 44 | Pass | Via core |

### Core Module — Ported & Tested

| Component | SSG Package | Files | Tests |
|-----------|-------------|-------|-------|
| AST nodes | `ssg.md.ast` + `ssg.md.ast.util` | ~68 | 624/624 CommonMark |
| Parser | `ssg.md.parser.*` | ~68 | 624/624 CommonMark |
| HTML Renderer | `ssg.md.html.*` | ~39 | 624/624 CommonMark |
| Formatter | `ssg.md.formatter.*` | ~26 | Ported, untested |

### Extensions — All Ported & Tested

| Extension | SSG Package | Files | Tests | Pass |
|-----------|-------------|-------|-------|------|
| yaml-front-matter | `ssg.md.ext.yaml.front.matter` | 9 | 8 | 100% |
| jekyll-front-matter | `ssg.md.ext.jekyll.front.matter` | 7 | 4 | 100% |
| jekyll-tag | `ssg.md.ext.jekyll.tag` | 11 | 15 | 100% |
| tables | `ssg.md.ext.tables` | 17 | 89 | 100% |
| gfm-strikethrough | `ssg.md.ext.gfm.strikethrough` | 13 | 12 | 100% |
| gfm-tasklist | `ssg.md.ext.gfm.tasklist` | 10 | 37 | 100% |
| autolink | `ssg.md.ext.autolink` | 2 | 34 | 100% |
| emoji | `ssg.md.ext.emoji` | 13 | 47 | 100% |
| typographic | `ssg.md.ext.typographic` | 11 | 32 | 100% |
| footnotes | `ssg.md.ext.footnotes` | 12 | 30 | 100% |
| abbreviation | `ssg.md.ext.abbreviation` | 12 | 22 | 100% |
| definition | `ssg.md.ext.definition` | 13 | 38 | 100% |
| toc | `ssg.md.ext.toc` | 26 | 91 | 100% |
| attributes | `ssg.md.ext.attributes` | 18 | 132 | 100% |
| anchorlink | `ssg.md.ext.anchorlink` | 7 | 14 | 100% |
| aside | `ssg.md.ext.aside` | 8 | 44 | 100% |
| admonition | `ssg.md.ext.admonition` | 9 | 55 | 100% |
| ins | `ssg.md.ext.ins` | 6 | 18 | 100% |
| superscript | `ssg.md.ext.superscript` | 6 | 8 | 100% |
| escaped-character | `ssg.md.ext.escaped.character` | 7 | 8 | 100% |
| wikilink | `ssg.md.ext.wikilink` | 13 | 114 | 100% |
| gitlab | `ssg.md.ext.gitlab` | 14 | 55 | 100% |
| macros | `ssg.md.ext.macros` | 12 | 19 | 100% |
| gfm-issues | `ssg.md.ext.gfm.issues` | 7 | 5 | 100% |
| gfm-users | `ssg.md.ext.gfm.users` | 7 | 5 | 100% |
| enumerated-reference | `ssg.md.ext.enumerated.reference` | 23 | 4 | 100% |
| media-tags | `ssg.md.ext.media.tags` | 18 | 4 | 100% |
| resizable-image | `ssg.md.ext.resizable.image` | 6 | 2 | 100% |
| youtube-embedded | `ssg.md.ext.youtube.embedded` | 8 | 2 | 100% |

### Skipped Modules (14)

See [flexmark-port.md](flexmark-port.md) for reasons.

## Known Technical Debt

See [../contributing/flexmark-tech-debt.md](../contributing/flexmark-tech-debt.md).
