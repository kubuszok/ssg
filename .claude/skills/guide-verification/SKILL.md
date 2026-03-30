---
description: Load the post-conversion verification checklist for SSG files covering compilation, completeness, idioms, and testing
---

Load the verification checklist.

$READ docs/contributing/verification-checklist.md

Apply this checklist after converting any file:
1. License header present with original source attribution
2. Compilation: zero errors, zero warnings (warnings are fatal)
3. Completeness: all public methods, constants, enums from source present
4. Scala idioms: no return, null, Java keywords; uses boundary, Nullable[A]
5. Type mappings: all source collections → Scala equivalents
6. Testing: tests present if source had them
7. Status progression: update migration database
8. Header documentation: migration notes block
