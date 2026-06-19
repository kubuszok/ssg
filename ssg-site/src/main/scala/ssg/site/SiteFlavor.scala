/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Site flavor extension point for the site pipeline.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 4 for design.
 *
 * Full flavor resolvers (Cobalt.rs, MkDocs, etc.) are future follow-ups.
 */
package ssg
package site

/** Extension point that parameterises the site pipeline's behavior per generator flavor (Jekyll, Cobalt.rs, MkDocs, etc.).
  *
  * Jekyll behavior is the default instance (`SiteFlavor.Jekyll`). Non-Jekyll generators supply a different `SiteFlavor`. This mirrors how ssg-liquid already flavors its dialects via
  * `ssg.liquid.parser.Flavor`.
  *
  * Full flavor resolver wiring (permalinkResolver, isProcessable) is resolved in later pipeline phases. This trait defines the surface that `SiteConfig` needs to compile and `SiteConfig.load` needs
  * to set a sane default.
  */
trait SiteFlavor {

  /** The Liquid template flavor to use for rendering. */
  def liquidFlavor: ssg.liquid.parser.Flavor

  /** The front-matter key that names the layout file. */
  def layoutKey: String
}

object SiteFlavor {

  /** The default Jekyll flavor. */
  val Jekyll: SiteFlavor = new SiteFlavor {
    override def liquidFlavor: ssg.liquid.parser.Flavor = ssg.liquid.parser.Flavor.JEKYLL
    override def layoutKey:    String                   = "layout"
  }
}
