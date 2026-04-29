#!/usr/bin/env node
/**
 * Parses Terser compress test files and generates Scala test suites.
 *
 * Usage: node scripts/gen-compress-tests.js <test-file.js> <SuiteName> <output.scala>
 */

const fs = require('fs');
const path = require('path');

const [,, inputFile, suiteName, outputFile] = process.argv;

if (!inputFile || !suiteName || !outputFile) {
  console.error('Usage: node gen-compress-tests.js <input.js> <SuiteName> <output.scala>');
  process.exit(1);
}

const content = fs.readFileSync(inputFile, 'utf-8');
const basename = path.basename(inputFile);

// Parse test cases from the Terser test format
function parseTests(src) {
  const tests = [];
  const lines = src.split('\n');
  let i = 0;

  while (i < lines.length) {
    const nameMatch = lines[i].match(/^([a-zA-Z_][a-zA-Z0-9_]*)\s*:\s*\{/);
    if (!nameMatch) { i++; continue; }

    const testName = nameMatch[1];
    let braceCount = 1;
    i++;
    let testBody = '';

    // Track string/regex context to avoid counting braces inside strings
    while (i < lines.length && braceCount > 0) {
      const line = lines[i];
      for (let j = 0; j < line.length; j++) {
        const ch = line[j];
        if (ch === '"' || ch === "'") {
          // Skip string content
          const quote = ch;
          j++;
          while (j < line.length && line[j] !== quote) {
            if (line[j] === '\\') j++; // skip escaped char
            j++;
          }
        } else if (ch === '{') {
          braceCount++;
        } else if (ch === '}') {
          braceCount--;
        }
      }
      if (braceCount > 0) {
        testBody += line + '\n';
      } else {
        const lastBrace = line.lastIndexOf('}');
        testBody += line.substring(0, lastBrace) + '\n';
      }
      i++;
    }

    const test = parseTestBody(testName, testBody);
    if (test) tests.push(test);
  }

  return tests;
}

function parseTestBody(name, body) {
  const options = extractOptions(body);
  const input = extractBlock(body, 'input');
  const expect = extractBlock(body, 'expect');

  if (input === null || expect === null) {
    console.warn(`Skipping ${name}: missing input or expect block`);
    return null;
  }

  return { name, options, input, expect };
}

function extractOptions(body) {
  // Find the options block, handling nested braces (like global_defs: { ... })
  const optStart = body.match(/options\s*=\s*\{/);
  if (!optStart) return {};

  let start = optStart.index + optStart[0].length;
  let braceCount = 1;
  let i = start;
  while (i < body.length && braceCount > 0) {
    if (body[i] === '{') braceCount++;
    if (body[i] === '}') braceCount--;
    if (braceCount > 0) i++;
  }
  const optStr = body.substring(start, i);

  const opts = {};

  // Parse line by line, handling nested objects and arrays
  const lines = optStr.split('\n');
  let skipNesting = 0;
  let inArray = false;
  let arrayKey = '';
  let arrayContent = '';

  for (const line of lines) {
    const trimmed = line.trim();

    // If we're collecting an array
    if (inArray) {
      arrayContent += trimmed;
      if (trimmed.includes(']')) {
        inArray = false;
        // Parse the array of strings
        const items = [];
        const re = /["']([^"']+)["']/g;
        let am;
        while ((am = re.exec(arrayContent)) !== null) {
          items.push(am[1]);
        }
        opts[arrayKey] = { type: 'array', value: items };
      }
      continue;
    }

    // Track nested braces (global_defs: { ... })
    for (const ch of trimmed) {
      if (ch === '{') skipNesting++;
      if (ch === '}') skipNesting--;
    }
    if (skipNesting > 0) continue;
    if (skipNesting < 0) { skipNesting = 0; continue; }

    // Skip lines that open a nested block
    if (/\{/.test(trimmed) && !/^\w+\s*:/.test(trimmed)) continue;
    if (/\{/.test(trimmed)) continue; // Any line with { is a nested object opener

    // Split on comma-separated key: value pairs first
    // Handle single-line multi-option: "toplevel: true, defaults: true"
    const pairs = trimmed.split(/,\s*(?=\w+\s*:)/);
    for (const pair of pairs) {
      const pairTrimmed = pair.trim();
      const m = pairTrimmed.match(/^(\w+)\s*:\s*(.+?)(?:,\s*)?$/);
      if (!m) continue;

      const key = m[1];
      let val = m[2].trim().replace(/,\s*$/, '');

      // Skip if it's opening a nested object
      if (val.endsWith('{')) continue;

      // Check if it's starting an array
      if (val.startsWith('[')) {
        if (val.includes(']')) {
          // Single-line array
          const items = [];
          const re = /["']([^"']+)["']/g;
          let am;
          while ((am = re.exec(val)) !== null) {
            items.push(am[1]);
          }
          opts[key] = { type: 'array', value: items };
        } else {
          // Multi-line array
          inArray = true;
          arrayKey = key;
          arrayContent = val;
        }
        continue;
      }

      const strMatch = val.match(/^"([^"]*)"$/);
      if (strMatch) {
        opts[key] = { type: 'string', value: strMatch[1] };
      } else if (val === 'true') {
        opts[key] = true;
      } else if (val === 'false') {
        opts[key] = false;
      } else if (/^\d+$/.test(val)) {
        opts[key] = parseInt(val);
      } else {
        // Complex value (regex, function, etc.) - store as string
        opts[key] = val;
      }
    }
  }

  return opts;
}

function extractBlock(body, blockName) {
  const regex = new RegExp(`(?:^|\\n)\\s*${blockName}\\s*:\\s*\\{`, 'm');
  const match = regex.exec(body);
  if (!match) return null;

  let start = match.index + match[0].length;
  let braceCount = 1;
  let i = start;

  while (i < body.length && braceCount > 0) {
    const ch = body[i];
    if (ch === '"' || ch === "'") {
      const quote = ch;
      i++;
      while (i < body.length && body[i] !== quote) {
        if (body[i] === '\\') i++;
        i++;
      }
    } else if (ch === '`') {
      // Template literal - skip to end
      i++;
      while (i < body.length && body[i] !== '`') {
        if (body[i] === '\\') i++;
        if (body[i] === '$' && i + 1 < body.length && body[i + 1] === '{') {
          // Template expression - track braces
          i += 2;
          let tplBraces = 1;
          while (i < body.length && tplBraces > 0) {
            if (body[i] === '{') tplBraces++;
            if (body[i] === '}') tplBraces--;
            if (tplBraces > 0) i++;
          }
        }
        i++;
      }
    } else if (ch === '/' && i + 1 < body.length && body[i + 1] === '/') {
      // Line comment - skip to EOL
      while (i < body.length && body[i] !== '\n') i++;
    } else if (ch === '{') {
      braceCount++;
    } else if (ch === '}') {
      braceCount--;
    }
    if (braceCount > 0) i++;
  }

  let result = body.substring(start, i).trim();
  result = result.replace(/;\s*$/, '');
  return result;
}

// Map JS option names to Scala CompressorOptions field names and values
function mapOption(name, value) {
  const mapping = {
    'dead_code': 'deadCode',
    'drop_debugger': 'dropDebugger',
    'join_vars': 'joinVars',
    'collapse_vars': 'collapseVars',
    'reduce_vars': 'reduceVars',
    'reduce_funcs': 'reduceFuncs',
    'drop_console': 'dropConsole',
    'hoist_funs': 'hoistFuns',
    'hoist_vars': 'hoistVars',
    'hoist_props': 'hoistProps',
    'if_return': 'ifReturn',
    'keep_fargs': 'keepFargs',
    'keep_fnames': 'keepFnames',
    'negate_iife': 'negateIife',
    'pure_getters': 'pureGetters',
    'pure_funcs': 'pureFuncs',
    'side_effects': 'sideEffects',
    'computed_props': 'computedProps',
    'unsafe_arrows': 'unsafeArrows',
    'unsafe_comps': 'unsafeComps',
    'unsafe_Function': 'unsafeFunction',
    'unsafe_math': 'unsafeMath',
    'unsafe_methods': 'unsafeMethods',
    'unsafe_proto': 'unsafeProto',
    'unsafe_regexp': 'unsafeRegexp',
    'unsafe_symbols': 'unsafeSymbols',
    'unsafe_undefined': 'unsafeUndefined',
    'keep_classnames': 'keepClassnames',
    'keep_infinity': 'keepInfinity',
    'lhs_constants': 'lhsConstants',
    'booleans_as_integers': 'booleansAsIntegers',
    'pure_new': 'pureNew',
  };

  const scalaName = mapping[name] || name;

  // Skip options we can't map to CompressorOptions
  // pure_funcs as a function value is unsupported; array values are handled above
  const unsupported = ['global_defs'];

  // Handle array-typed values (parsed as { type: 'array', value: [...] })
  if (value && typeof value === 'object' && value.type === 'array') {
    if (name === 'pure_funcs') {
      const items = value.value.map(s => `"${s}"`).join(', ');
      return { name: 'pureFuncs', value: `List(${items})` };
    }
    if (name === 'drop_console') {
      const items = value.value.map(s => `"${s}"`).join(', ');
      return { name: 'dropConsole', value: `DropConsoleConfig.Methods(Set(${items}))` };
    }
    // Skip other array options we can't easily map
    return null;
  }

  // Handle string-typed values (parsed as { type: 'string', value: ... })
  if (value && typeof value === 'object' && value.type === 'string') {
    if (name === 'pure_getters') {
      return { name: 'pureGetters', value: `"${value.value}"` };
    }
    if (name === 'toplevel') {
      const funcs = value.value.includes('funcs');
      const vars = value.value.includes('vars');
      return { name: 'toplevel', value: `ToplevelConfig(funcs = ${funcs}, vars = ${vars})` };
    }
    if (name === 'ecma') {
      // ecma: "6" => ecma = 2015, ecma: "2015" => 2015
      let num = parseInt(value.value);
      if (num === 6) num = 2015;
      return { name: 'ecma', value: String(num) };
    }
    if (name === 'top_retain') {
      // top_retain: "foo,bar" -> topRetain = Some(Set("foo","bar"))
      const names = value.value.split(',').map(s => `"${s.trim()}"`).join(', ');
      return { name: 'topRetain', value: `Some((n: String) => Set(${names}).contains(n))` };
    }
    if (unsupported.includes(name)) return null;
    // For boolean fields that receive string values, skip
    if (['unused', 'keep_fargs', 'keep_fnames', 'keep_classnames',
         'module', 'drop_console'].includes(name)) {
      return null;
    }
    return { name: scalaName, value: `"${value.value}"` };
  }

  // drop_console
  if (name === 'drop_console' && value === true) {
    return { name: 'dropConsole', value: 'DropConsoleConfig.All' };
  }
  if (name === 'drop_console' && value === false) {
    return { name: 'dropConsole', value: 'DropConsoleConfig.Disabled' };
  }

  // Special handling for sequences
  if (name === 'sequences' && value === true) {
    return { name: 'sequencesLimit', value: '200' };
  }
  if (name === 'sequences' && value === false) {
    return { name: 'sequencesLimit', value: '0' };
  }
  if (name === 'sequences' && typeof value === 'number') {
    return { name: 'sequencesLimit', value: String(value) };
  }

  // toplevel
  if (name === 'toplevel' && value === true) {
    return { name: 'toplevel', value: 'ToplevelConfig(funcs = true, vars = true)' };
  }
  if (name === 'toplevel' && value === false) {
    return { name: 'toplevel', value: 'ToplevelConfig()' };
  }

  // inline
  if (name === 'inline' && value === true) {
    return { name: 'inline', value: 'InlineLevel.InlineFull' };
  }
  if (name === 'inline' && value === false) {
    return { name: 'inline', value: 'InlineLevel.InlineDisabled' };
  }
  if (name === 'inline' && typeof value === 'number') {
    const levels = ['InlineDisabled', 'InlineSimple', 'InlineWithArgs', 'InlineFull'];
    return { name: 'inline', value: `InlineLevel.${levels[Math.min(value, 3)]}` };
  }

  // pure_getters
  if (name === 'pure_getters' && value === true) {
    return { name: 'pureGetters', value: '"strict"' };
  }
  if (name === 'pure_getters' && value === false) {
    // Keep default "strict" value from AllOff
    return null; // skip this option
  }

  // ecma
  if (name === 'ecma' && value === true) {
    // ecma: true is non-standard; treat as ecma: 2015
    return { name: 'ecma', value: '2015' };
  }
  if (name === 'ecma' && value === false) {
    return { name: 'ecma', value: '5' };
  }
  if (name === 'ecma' && typeof value === 'number') {
    return { name: 'ecma', value: String(value) };
  }

  // passes
  if (name === 'passes' && typeof value === 'number') {
    return { name: 'passes', value: String(value) };
  }

  // Skip unsupported options
  if (unsupported.includes(name)) {
    return null;
  }

  // Boolean options that sometimes take string values in Terser
  // (like unused: "keep_assign") - skip these unsupported variants
  const booleanOnly = ['unused', 'booleans', 'arrows', 'comparisons', 'conditionals',
    'deadCode', 'evaluate', 'properties', 'loops', 'module', 'switches', 'typeofs',
    'directives', 'ie8', 'arguments', 'unsafe', 'sideEffects', 'keepFargs',
    'keepFnames', 'keepClassnames', 'keepInfinity', 'lhsConstants', 'negateIife',
    'pureNew', 'reduceFuncs', 'reduceVars', 'collapseVars', 'joinVars',
    'hoistFuns', 'hoistVars', 'hoistProps', 'ifReturn', 'dropDebugger',
    'computedProps', 'expression', 'warnings',
    'unsafeArrows', 'unsafeComps', 'unsafeFunction', 'unsafeMath',
    'unsafeMethods', 'unsafeProto', 'unsafeRegexp', 'unsafeSymbols',
    'unsafeUndefined', 'booleansAsIntegers'];

  if (typeof value === 'boolean') {
    return { name: scalaName, value: String(value) };
  }
  if (typeof value === 'number') {
    return { name: scalaName, value: String(value) };
  }
  if (typeof value === 'string') {
    // String value for a boolean-only option: skip
    if (booleanOnly.includes(scalaName)) return null;
    return { name: scalaName, value: `"${value}"` };
  }

  // For complex values (arrays, objects), skip
  return null;
}

function formatStringForScala(str) {
  if (str.length === 0) return '""';

  const lines = str.split('\n');

  if (lines.length === 1) {
    // Single line - use regular string with escaping
    return '"' + str
      .replace(/\\/g, '\\\\')
      .replace(/"/g, '\\"')
      .replace(/\n/g, '\\n')
      .replace(/\t/g, '\\t')
      .replace(/\r/g, '\\r')
      + '"';
  }

  // Multi-line: check if triple-quotes would work
  // Triple-quoted strings can't contain """ and some escape sequences
  // behave differently inside triple-quoted strings
  const hasTripleQuote = str.includes('"""');
  const hasProblematicContent = /[\x00-\x08\x0e-\x1f\x7f]/.test(str);
  if (hasTripleQuote || hasProblematicContent) {
    // Fall back to concatenated regular strings with proper escaping
    return lines.map(l => {
      let escaped = '';
      for (let j = 0; j < l.length; j++) {
        const code = l.charCodeAt(j);
        if (code === 0x5C) { escaped += '\\\\'; } // backslash
        else if (code === 0x22) { escaped += '\\"'; } // double quote
        else if (code === 0x09) { escaped += '\\t'; }
        else if (code === 0x0A) { escaped += '\\n'; }
        else if (code === 0x0D) { escaped += '\\r'; }
        else if (code === 0x08) { escaped += '\\b'; }
        else if (code === 0x0C) { escaped += '\\f'; }
        else if (code < 0x20 || code === 0x7F) {
          escaped += '\\u' + code.toString(16).padStart(4, '0');
        } else {
          escaped += l[j];
        }
      }
      return '"' + escaped + '"';
    }).join(' + "\\n" +\n      ');
  }

  // Use triple-quoted string
  // Need to escape $ for Scala string interpolation
  let escaped = str.replace(/\$/g, '$$$$'); // $$ in replacement = literal $
  return '"""' + escaped + '""".stripMargin.trim';
}

function generateScalaTest(test) {
  const { name, options, input, expect } = test;

  const optParts = [];
  for (const [key, val] of Object.entries(options)) {
    if (key === 'defaults') continue;
    const mapped = mapOption(key, val);
    if (mapped) {
      optParts.push(`${mapped.name} = ${mapped.value}`);
    }
  }

  optParts.sort();

  const optionsStr = optParts.length > 0
    ? `AllOff.copy(\n        ${optParts.join(',\n        ')}\n      )`
    : 'AllOff';

  const inputStr = formatStringForScala(input);
  const expectStr = formatStringForScala(expect);

  return `  // =========================================================================
  // ${name}
  // =========================================================================
  test("${name}") {
    assertCompresses(
      input = ${inputStr},
      expected = ${expectStr},
      options = ${optionsStr}
    )
  }`;
}

// Main
const tests = parseTests(content);
console.error(`Parsed ${tests.length} test cases from ${basename}`);

if (tests.length === 0) {
  console.error(`Skipping ${basename}: no test cases with input/expect blocks`);
  process.exit(0);
}

// Build the generated code first to determine needed imports
const testCode = tests.map(t => generateScalaTest(t)).join('\n\n');
const needsToplevel = testCode.includes('ToplevelConfig');
const needsInline = testCode.includes('InlineLevel');
const needsDropConsole = testCode.includes('DropConsoleConfig');

let extraImports = '';
if (needsToplevel || needsInline || needsDropConsole) {
  const parts = [];
  if (needsDropConsole) parts.push('DropConsoleConfig');
  if (needsInline) parts.push('InlineLevel');
  if (needsToplevel) parts.push('ToplevelConfig');
  extraImports = `\nimport ssg.js.compress.{ ${parts.join(', ')} }`;
}

let scala = `/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for ${basename.replace('.js', '')}.
 * Ported from: terser/test/compress/${basename} (${tests.length} test cases)
 *
 * Uses CompressTestHelper with false_by_default mode.
 * Auto-generated by scripts/gen-compress-tests.js */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }${extraImports}

final class ${suiteName} extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

`;

scala += testCode + '\n\n';

scala += '}\n';

fs.writeFileSync(outputFile, scala);
console.error(`Generated ${outputFile} with ${tests.length} tests`);
