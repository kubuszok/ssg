---
description: Load the Nullable[A] opaque type guide for null-safe patterns in SSG code
---

Load the Nullable[A] guide for SSG null-safe patterns.

$READ docs/contributing/nullable-guide.md

Apply these patterns when converting code that uses null. Key patterns:
1. Null-or-value: `nullable.getOrElse(default)`
2. Null-or-throw: `nullable.fold(throw Error())(a => doSomething(a))`
3. Null-or-compute: `nullable.fold(computeDefault())(a => transform(a))`
4. Non-null only: `nullable.foreach { a => doSomething(a) }`
5. Boolean checks: `isDefined` / `isEmpty`
