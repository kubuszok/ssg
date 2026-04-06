# ssg-sass Shortcuts and Stubs Tracker

This file tracks all incomplete implementations in `ssg-sass`. The initial port
created compiling skeletons for the entire dart-sass codebase; this document
catalogs what still needs real implementation.

**Status:** 85 distinct stubs across ~50 files. Tests pass (167/167) but only
exercise the fully-ported value/utility/AST layers — not the parser, evaluator,
or serializer.

## Legend

- **CRITICAL** — Foundation methods; nothing works without them
- **HIGH** — Core algorithms; called by critical paths
- **MEDIUM** — Helper methods, less central
- **LOW** — Cosmetic / deferred features
- ✅ — Implemented
- ⚠️  — Partial / approximate
- ❌ — Stub (throws or returns default)

---

## CRITICAL — Parser Foundation (38 items)

### `parse/Parser.scala` — base tokenizer ✅ IMPLEMENTED
- ✅ `whitespace(consumeNewlines)`
- ✅ `whitespaceWithoutComments(consumeNewlines)`
- ✅ `spaces()`
- ✅ `scanComment()` + `silentComment()` + `loudComment()`
- ✅ `identifier(normalize, unit)` + `identifierBody()`
- ✅ `scanIdentifier(text, caseSensitive)`
- ✅ `expectIdentifier(text, name)`
- ✅ `lookingAtIdentifier(forward)` + `lookingAtIdentifierBody()`
- ✅ `string()` with escape sequences
- ✅ `declarationValue(allowEmpty)` with bracket balancing
- ✅ `expectWhitespace()`
- ✅ `escape()` + `escapeCharacter()` (hex escapes, unicode)
- ✅ `naturalNumber()`
- ✅ `variableName()`
- ✅ `Parser.parseIdentifier(text)`
- ✅ `Parser.isIdentifier(text)`
- ✅ `Parser.isVariableDeclarationLike(text)`
- ✅ `wrapSpanFormatException` — now rewrites StringScannerException

### `parse/StylesheetParser.scala` ⚠️  MINIMUM VIABLE
- ✅ `parse()` — full stylesheets with style rules, variables, comments, generic @-rules
- ✅ `parseExpression()` — numbers, strings, booleans, null, variables
- ✅ `parseNumber()` — with units
- ✅ `parseVariableDeclaration()` — with !default/!global flags
- ❌ `parseUseRule()` — still throws (UseRule factory incomplete)
- ❌ `parseSignature(requireParens)` — medium
- ⚠️  Expression parsing is TEXT-BASED — collects raw text then pattern-matches.
  A proper tokenizer for operators (+/-/*), function calls, interpolation `#{...}`,
  and space-separated lists is still TODO.
- ⚠️  Style rule selectors stored as plain Interpolation (no interpolation parsing yet).

### `parse/ScssParser.scala` ✅ IMPLEMENTED
- ✅ `styleRuleSelector()` — collects raw selector text
- ✅ `expectStatementSeparator(name)`
- ✅ `atEndOfStatement()`
- ✅ `lookingAtChildren()`
- ✅ `scanElse(ifIndentation)`
- ✅ `children(child)` — block parsing with `{...}`
- ✅ `statements(statement)` — top-level sequence

### `parse/SassParser.scala` — same 7 methods as ScssParser, indented variants

### `parse/SelectorParser.scala`
- ❌ `parse()` — medium
- ❌ `parseComplexSelector()` — medium
- ❌ `parseCompoundSelector()` — medium
- ❌ `parseSimpleSelector()` — large (many pseudo-class variants)

### `parse/MediaQueryParser.scala`
- ❌ `parse()` — medium

### `parse/KeyframeSelectorParser.scala`
- ❌ `parse()` — small

### `parse/AtRootQueryParser.scala`
- ❌ `parse()` — small

---

## HIGH — Evaluator and Serializer (3 items, but huge)

### `visitor/EvaluateVisitor.scala`
- ❌ `run(stylesheet)` — **large (1000+ lines)** — orchestrates entire pipeline
- ❌ `runExpression(stylesheet, expression)` — large

### `visitor/SerializeVisitor.scala`
- ❌ `serialize(node)` — large (500-800 lines)

---

## HIGH — Selector Unification & Extend (10 items)

### `extend/ExtendFunctions.scala`
- ⚠️ `unifyComplex(complexes, span)` — large (~150 lines)
- ⚠️ `unifyCompound(compound1, compound2)` — medium (~80 lines)
- ⚠️ `weave(complexes, span)` — large (~150 lines, second law of extend)
- ⚠️ `paths(choices)` — small (~30 lines)

### `extend/ExtensionStore.scala`
- ⚠️ `extensionsWhereTarget` — small (MergedExtensions unwrap)
- ⚠️ `addSelector` — medium (selector rewriting)
- ⚠️ `addExtension` — medium (apply to existing)
- ⚠️ `addExtensions` — medium (merge stores)
- ⚠️ `cloneStore` — small
- ⚠️ `registerSelector` — small

### `ast/selector/SelectorList.scala`
- ⚠️ `unify(other)` — small (delegates to ExtendFunctions)

### `ast/selector/ComplexSelector.scala`
- ⚠️ `isSuperselector(other)` — medium (basic approximation only)
- ⚠️ `complexIsSuperselector` — medium

### `ast/selector/CompoundSelector.scala`
- ⚠️ `isSuperselector(other)` — small

---

## HIGH — Environment & Modules (10 items)

### `Environment.scala`
- ⚠️ `closure()` — small (returns `this`; needs deep copy)
- ⚠️ `global()` — trivial

### `Module.scala`
- ❌ `BuiltInModule.css` — intentionally throws
- ⚠️ `ForwardedView` — medium (no shown/hidden/prefix filtering)
- ⚠️ `ShadowedView` — small (no shadowing logic)

### `Callable.scala`
- ⚠️ `function/mixin` factories — small (don't parse arg signatures)
- ⚠️ `overloadedFunction` — medium (just picks first overload)
- ⚠️ `UserDefinedCallable.name` — trivial (returns "user-defined")

### `Configuration.scala`
- ⚠️ `throwErrorForUnknownVariables()` — small
- ⚠️ `implicitConfig(values)` — trivial

### `EvaluationContext.scala`
- ⚠️ `current` — medium (zone-style propagation)

---

## HIGH — Import Resolution (7 items)

### `ImportCache.scala`
- ❌ `canonicalize(url, ...)` — large (resolution against importers + load paths)
- ❌ `importCanonical(...)` — medium (load + parse)

### `importer/Importer.scala`
- ❌ `FilesystemImporter.canonicalize(url)` — medium (partials, extensions)
- ❌ `FilesystemImporter.load(url)` — medium (file I/O)
- ❌ `PackageImporter` — large (config parsing)
- ❌ `NodePackageImporter` — large (node_modules traversal)

### `StylesheetGraph.scala`
- ⚠️ `addCanonical(...)` — medium (circular dep detection)

---

## HIGH — Built-in Functions (8 files, all stubbed)

### `functions/*.scala`
All function modules are skeletons with empty `globals` lists:
- ❌ `ColorFunctions` — large (~1500 lines in Dart)
- ❌ `MathFunctions` — medium
- ❌ `StringFunctions` — medium
- ❌ `ListFunctions` — small
- ❌ `MapFunctions` — small
- ❌ `MetaFunctions` — medium
- ❌ `SelectorFunctions` — medium (depends on extend)
- ⚠️ `Functions.scala` (barrel) — registers all module functions

### `visitor/FindDependenciesVisitor.scala`
- ⚠️ `visitIncludeRule` — handles meta.load-css with literal strings (TODO)

---

## LOW — Compile Orchestration (2 items)

### `Compile.scala`
- ❌ `compileString(source)` — trivial once parser/evaluator/serializer exist
- ❌ `compile(path)` — trivial (file I/O + compileString)

---

## Implementation Order

1. **Parser tokenizer methods** (`Parser.scala`) — foundation for everything
2. **StylesheetParser.parse()** + ScssParser overrides — needed to get an AST
3. **SelectorParser.parse()** — selector parsing
4. **EvaluateVisitor.run()** — evaluator (largest piece)
5. **SerializeVisitor.serialize()** — output
6. **Compile.compileString()** — wires it all together
7. **Built-in functions** — math/string first, color last
8. **Extend algorithm** — unifyComplex, weave (parallel to evaluator)
9. **Import resolution** — filesystem importer
