/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Typed site configuration loaded from _config.yml.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 4 for design.
 */
package ssg
package site

import lowlevel.Nullable

import ssg.commons.io.FilePath
import ssg.data.DataView

import scala.collection.immutable.VectorMap

/** Typed configuration for the site pipeline, loaded from `_config.yml`.
  *
  * The typed fields are the ones the pipeline itself consumes. The `raw` field keeps the entire parsed config as a [[ssg.data.DataView]] so arbitrary user keys reach templates as `site.<key>` without
  * this case class enumerating them (Jekyll exposes all config keys under `site`).
  *
  * @param source
  *   the source directory (Jekyll: `source`, default `.`)
  * @param destination
  *   the output directory (Jekyll: `destination`, default `_site`)
  * @param layoutsDir
  *   the layouts directory name (Jekyll: `layouts_dir`, default `_layouts`)
  * @param includesDir
  *   the includes directory name (Jekyll: `includes_dir`, default `_includes`)
  * @param sassDir
  *   the sass directory name (Jekyll: `sass_dir`, default `_sass`)
  * @param permalink
  *   the site-wide permalink style (Jekyll: `permalink`, default `date`)
  * @param baseurl
  *   the subpath the site is served under (Jekyll: `baseurl`, default `""`)
  * @param minify
  *   whether to minify output (off by default; see design section 3/10)
  * @param flavor
  *   the site flavor (default Jekyll; extensibility point for Cobalt/MkDocs)
  * @param raw
  *   the full parsed `_config.yml` as DataView, so arbitrary keys reach templates as `site.*`
  */
final case class SiteConfig(
  source:      FilePath = FilePath.of("."),
  destination: FilePath = FilePath.of("_site"),
  layoutsDir:  String = "_layouts",
  includesDir: String = "_includes",
  sassDir:     String = "_sass",
  permalink:   PermalinkStyle = PermalinkStyle.Date,
  baseurl:     String = "",
  minify:      Boolean = false,
  flavor:      SiteFlavor = SiteFlavor.Jekyll,
  raw:         DataView = DataView.from(VectorMap.empty[String, DataView])
)

object SiteConfig {

  /** Loads a `SiteConfig` from the YAML text of a `_config.yml` file.
    *
    * Parses the YAML with kindlings-yaml into a [[ssg.data.DataView]], then extracts the typed fields the pipeline needs. Unknown keys are silently kept in `raw` so they reach templates as
    * `site.<key>` (Q2 default = lenient).
    *
    * @param yamlText
    *   the raw YAML content of `_config.yml`
    * @return
    *   the parsed `SiteConfig`, or a default config if parsing fails
    */
  def load(yamlText: String): SiteConfig = {
    val parsed: Nullable[DataView] = YamlDataViewDecoder.parse(yamlText)
    parsed.fold(SiteConfig())(fromDataView)
  }

  /** Extracts typed `SiteConfig` fields from a parsed `DataView` mapping.
    *
    * The DataView is expected to be a top-level mapping (the normal shape of `_config.yml`). If it is not a mapping, defaults are used for all typed fields and `raw` holds the parsed value as-is.
    */
  private def fromDataView(dv: DataView): SiteConfig = {
    val mapOpt = dv.asMap.toOption
    mapOpt match {
      case scala.None =>
        // Non-mapping top-level value; use defaults, store raw.
        SiteConfig(raw = dv)
      case Some(map) =>
        SiteConfig(
          source = stringField(map, "source").fold(FilePath.of("."))(FilePath.of),
          destination = stringField(map, "destination").fold(FilePath.of("_site"))(FilePath.of),
          layoutsDir = stringField(map, "layouts_dir").getOrElse("_layouts"),
          includesDir = stringField(map, "includes_dir").getOrElse("_includes"),
          sassDir = stringField(map, "sass_dir").getOrElse("_sass"),
          permalink = stringField(map, "permalink").fold(PermalinkStyle.Date)(parsePermalinkStyle),
          baseurl = stringField(map, "baseurl").getOrElse(""),
          minify = booleanField(map, "minify").getOrElse(false),
          flavor = SiteFlavor.Jekyll,
          raw = dv
        )
    }
  }

  /** Extracts a string value from a DataView mapping by key. */
  private def stringField(map: VectorMap[String, DataView], key: String): Option[String] =
    map.get(key).flatMap(_.asString.toOption)

  /** Extracts a boolean value from a DataView mapping by key. */
  private def booleanField(map: VectorMap[String, DataView], key: String): Option[Boolean] =
    map.get(key).flatMap(_.asBoolean.toOption)

  /** Parses a permalink style string into a `PermalinkStyle`.
    *
    * Recognizes the three Jekyll built-in style names. An unrecognized value falls back to `PermalinkStyle.Date` (the Jekyll documented default).
    */
  private def parsePermalinkStyle(value: String): PermalinkStyle =
    value.trim.toLowerCase match {
      case "date"   => PermalinkStyle.Date
      case "pretty" => PermalinkStyle.Pretty
      case "none"   => PermalinkStyle.None
      case _        => PermalinkStyle.Date
    }
}
