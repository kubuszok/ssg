/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Environment delimiters: \begin and \end. HTML/MathML rendering is defined
 * in the corresponding defineEnvironment definitions.
 *
 * Original source: katex src/functions/environment.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import ssg.commons.Nullable
import ssg.katex.ParseError
import ssg.katex.environments.{ EnvContext, EnvironmentDef }
import ssg.katex.parse._

object EnvironmentFunc {

  def register(): Unit =
    // Environment delimiters. HTML/MathML rendering is defined in the corresponding
    // defineEnvironment definitions.
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "environment",
        names = Array("\\begin", "\\end"),
        props = FunctionPropSpec(
          numArgs = 1,
          argTypes = Nullable(Array(ArgType.TextMode))
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser    = context.parser.asInstanceOf[Parser]
          val nameGroup = args(0)
          if (nameGroup.nodeType != "ordgroup") {
            throw new ParseError("Invalid environment name", Nullable(nameGroup))
          }
          val og      = nameGroup.asInstanceOf[ParseNodeOrdgroup]
          var envName = ""
          var i       = 0
          while (i < og.body.length) {
            envName += ParseNode.assertNodeType(Nullable(og.body(i)), "textord").asInstanceOf[ParseNodeTextord].text
            i += 1
          }

          if (context.funcName == "\\begin") {
            // begin...end is similar to left...right
            if (!EnvironmentDef._environments.contains(envName)) {
              throw new ParseError("No such environment: " + envName, Nullable(nameGroup))
            }
            // Build the environment object. Arguments and other information will
            // be made available to the begin and end methods using properties.
            val env                   = EnvironmentDef._environments(envName)
            val (envArgs, envOptArgs) =
              parser.parseArguments("\\begin{" + envName + "}", env)
            val envContext = EnvContext(
              mode = parser.mode,
              envName = envName,
              parser = parser
            )
            val result = env.handler(envContext, envArgs, envOptArgs)
            parser.expect("\\end", false)
            val endNameToken = parser.nextToken
            val end          = ParseNode.assertNodeType(parser.parseFunction(), "environment").asInstanceOf[ParseNodeEnvironment]
            if (end.name != envName) {
              throw new ParseError(
                s"Mismatch: \\begin{$envName} matched by \\end{${end.name}}",
                endNameToken.asInstanceOf[Nullable[SourceLocation.HasLoc]]
              )
            }
            // TODO(ts), "environment" handler returns an environment ParseNode
            result
          } else {
            ParseNodeEnvironment(
              mode = parser.mode,
              name = envName,
              nameGroup = nameGroup
            )
          }
        }
      )
    )
}
