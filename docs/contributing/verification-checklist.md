# Post-Conversion Verification Checklist

Use this checklist after converting any file to Scala 3.

## 1. License Header
- [ ] Apache 2.0 header present
- [ ] Original source file path noted
- [ ] Original author/copyright noted
- [ ] Original license noted

## 2. Compilation
- [ ] Zero errors on `ssg-dev build compile --module <module>`
- [ ] Zero warnings (warnings are fatal with `-Werror`)
- [ ] Compiles on JVM
- [ ] Compiles on Scala.js (if applicable)
- [ ] Compiles on Scala Native (if applicable)

## 3. Completeness
- [ ] All public methods from source present
- [ ] All public constants/enums present
- [ ] All public inner classes/types present
- [ ] No methods left as stubs/TODOs

## 4. Scala Idioms
- [ ] No `return` keyword (use `boundary`/`break`)
- [ ] No raw `null` (use `Nullable[A]`)
- [ ] No Java/Dart/Ruby keywords (`public`, `static`, `void`, `dynamic`, `def`-as-Ruby)
- [ ] Uses `boundary`/`break` for early returns
- [ ] Uses `Nullable[A]` for nullable values
- [ ] All `case class` are `final`
- [ ] Split packages used

## 5. Type Mappings
- [ ] All Java collections → Scala equivalents
- [ ] All Dart types → Scala equivalents
- [ ] Package names follow SSG conventions

## 6. Testing
- [ ] Tests ported if original had them
- [ ] MUnit test suite created
- [ ] Tests pass on JVM: `ssg-dev test unit --jvm --module <module>`

## 7. Status Update
- [ ] Migration database updated: `ssg-dev db migration set <path> --status ai_converted`

## 8. Documentation
- [ ] Migration notes block in header comment
- [ ] Renames documented
- [ ] Convention changes documented
