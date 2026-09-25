import balticporter.frontend.ts.ReferenceDeclarations

/** The object a translated body is emitted into, which the body translator needs to decide whether a private member it names is reachable from there.
  *
  * `byName` holds, per `(file stem, member)`, every term declaration of that name in the reference files with that stem.
  */
final case class ReferenceOwners(byName: Map[(String, String), List[ReferenceDeclarations.Declaration]]) {

  /** The owning object of the reference member(s) a body keyed `key` is placed at — `key` itself and every reference name `aliases` maps to it — when they all sit in one object; otherwise none, and
    * the translator then refuses every private reference in the body.
    */
  def ownerOf(file: String, key: String, aliases: Map[String, List[String]]): Option[String] = {
    val names  = key :: aliases.collect { case (ref, keys) if keys.contains(key) => ref }.toList
    val owners = names.flatMap(n => byName.getOrElse((file, n), Nil)).map(d => (d.owner, d.ownerIsObject)).distinct
    owners match {
      case List((owner, true)) if owner.nonEmpty => Some(owner)
      case _                                     => None
    }
  }
}

object ReferenceOwners {

  private val termKinds: Set[ReferenceDeclarations.Kind] = {
    import ReferenceDeclarations.Kind.*
    Set(Def, AbstractDef, Val, Var)
  }

  /** Reads the term declarations of every `(file stem, source)` pair; a file that does not parse contributes nothing, so its bodies get no owner. */
  def of(sources: List[(String, String)]): ReferenceOwners =
    ReferenceOwners(
      sources
        .flatMap { case (file, source) =>
          ReferenceDeclarations.read(file, source).toOption.toList.flatten.filter(d => termKinds(d.kind)).map(d => (file, d.name) -> d)
        }
        .groupBy(_._1)
        .map { case (key, entries) => key -> entries.map(_._2) }
    )
}
