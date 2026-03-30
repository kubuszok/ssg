---
description: Load the multi-language conversion procedure for porting Java/Dart/Ruby source files to Scala 3
---

Load the appropriate conversion rules based on the source language.

For Java sources (flexmark-java, liqp):
$READ docs/contributing/conversion-rules-java.md

For Dart sources (dart-sass):
$READ docs/contributing/conversion-rules-dart.md

For Ruby sources (jekyll-minifier):
$READ docs/contributing/conversion-rules-ruby.md

Also load the type mappings:
$READ docs/contributing/type-mappings.md

Apply these rules to convert the file specified in $ARGUMENTS. Detect the source language
from the file extension (.java, .dart, .rb) or the containing directory.
