/*
 * D-liqp-1b — a CLASSPATH-ONLY stub, never shipped and never on scalac's classpath.
 *
 * The ANTLR-generated parser is external (D-liqp-1) and references back INTO the library
 * (`liqp.TemplateParser.ErrorMode`); the port renames the library to `ssg.liquid` (D-liqp-2).
 * `LiqpClasspath` therefore rewrites the generated sources into the emitted namespace before
 * javac sees them, and this file is what javac then resolves `ssg.liquid.TemplateParser` against.
 * At RUN time the emitted Scala class of the same FQN is what stands here, so this file's only
 * obligation is to be SHAPE-HONEST about that class — it must admit exactly what the emitted
 * Scala admits, and refuse everything else.
 *
 * The port emits a java enum as a Scala `sealed abstract class` plus a companion `object` of
 * `case object`s, and Scala's static forwarders put `values()` and `valueOf(String)` on the
 * companion CLASS (`ssg/liquid/TemplateParser$ErrorMode`) while each constant is a static field
 * of the MODULE class (`…$ErrorMode$`). So `ErrorMode.LAX` — a `getstatic` javac would happily
 * emit against a real java enum — is a `NoSuchFieldError` at run time, and `ErrorMode.valueOf("LAX")`
 * is not. Declaring `ErrorMode` as a java `enum` here would therefore be a stub that LIES: it would
 * compile the one form the runtime cannot link. It is an abstract class with no constants instead,
 * and the constant references are rewritten to `valueOf` beside the package rename.
 *
 * Nothing is ever executed from here: every body throws.
 */
package ssg.liquid;

public class TemplateParser {

    public static abstract class ErrorMode {

        public static ErrorMode valueOf(String name) {
            throw new UnsupportedOperationException("D-liqp-1b stub — the emitted Scala stands here at run time");
        }

        public static ErrorMode[] values() {
            throw new UnsupportedOperationException("D-liqp-1b stub — the emitted Scala stands here at run time");
        }

        public String name() {
            throw new UnsupportedOperationException("D-liqp-1b stub — the emitted Scala stands here at run time");
        }

        public int ordinal() {
            throw new UnsupportedOperationException("D-liqp-1b stub — the emitted Scala stands here at run time");
        }
    }
}
