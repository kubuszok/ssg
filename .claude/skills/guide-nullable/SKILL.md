---
description: Load the Nullable[A] opaque type guide for null-safe patterns in SSG code
---

Load the Nullable[A] guide for SSG null-safe patterns.

$READ docs/contributing/nullable-guide.md

Nullable comes from [lls](https://github.com/kubuszok/lls) (`lowlevel.Nullable`).
Two re-export files provide package-local aliases in ssg-md and ssg-sass.

Apply these patterns when converting code that uses null. Key patterns:
1. Null-or-value: `nullable.getOrElse(default)`
2. Null-or-throw: `nullable.fold(throw Error())(a => doSomething(a))`
3. Null-or-compute: `nullable.fold(computeDefault())(a => transform(a))`
4. Non-null only: `nullable.foreach { a => doSomething(a) }`
5. Boolean checks: `isDefined` / `isEmpty`
6. Convert to Option: `nullable.toOption`
7. Empty value: `Nullable.empty` or `Nullable.Null`
