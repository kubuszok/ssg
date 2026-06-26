/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data
package internal.compiletime

import hearth.MacroCommons
import hearth.MacroCommonsScala3
import hearth.fp.effect.*
import hearth.std.*

import lowlevel.Nullable

// Self-type refined to MacroCommonsScala3 — see AsDataViewMacrosImpl for why raw
// scala.quoted reflection is needed to destructure the opaque Nullable.Impl[A].
private[data] trait FromDataViewMacrosImpl { this: MacroCommonsScala3 & MacroCommons & StdExtensions =>

  private lazy val fromDataViewCtor: Type.Ctor1[FromDataView] = Type.Ctor1.of[FromDataView]

  private lazy val fromOptionCtor: Type.Ctor1[Option] = Type.Ctor1.of[Option]

  private lazy val ignoredFromImplicits: Seq[UntypedMethod] =
    Type.of[FromDataView.type].asUntyped.methods.collect { case method if method.name == "derived" => method }.toSeq

  private object FDVTypes {
    def nullableType[A: Type]: Type[Nullable[A]] = Type.of[Nullable[A]]
  }

  /** If `A` is a [[lowlevel.Nullable]] (the opaque `Nullable.Impl[X]`), returns the existential inner type `X`; otherwise `None`. See [[AsDataViewMacrosImpl.nullableInnerType]] for the rationale. */
  private def fromNullableInnerType[A: Type]: Option[??] = {
    import quotes.reflect.*
    val repr        = TypeRepr.of[A](using Type[A].asInstanceOf[scala.quoted.Type[A]])
    val nullableSym = TypeRepr.of[Nullable.Impl[Any]].typeSymbol
    repr.baseType(nullableSym) match {
      case AppliedType(_, List(arg)) =>
        Some(arg.asType.asInstanceOf[Type[Any]].as_??)
      case _ =>
        repr match {
          case AppliedType(ctor, List(arg)) if ctor.typeSymbol == nullableSym =>
            Some(arg.asType.asInstanceOf[Type[Any]].as_??)
          case _ =>
            scala.None
        }
    }
  }

  private var stdExtLoadedFrom:         Boolean   = false
  private def ensureStdExtLoadedFrom(): MIO[Unit] =
    if (stdExtLoadedFrom) MIO.pure(())
    else
      Environment.loadStandardExtensions().toMIO(allowFailures = false).map { _ =>
        stdExtLoadedFrom = true
        ()
      }

  private var fromSelfType: Option[??] = scala.None

  private def isFromSelfType[A: Type]: Boolean =
    fromSelfType.exists(st => Type[A] <:< st.Underlying.asInstanceOf[Type[A]])

  def deriveFromDataView[A: Type]: Expr[FromDataView[A]] = {
    if (Type[A] =:= Type.of[Nothing].asInstanceOf[Type[A]] || Type[A] =:= Type.of[Any].asInstanceOf[Type[A]])
      Environment.reportErrorAndAbort(
        s"FromDataView.derived: type parameter was inferred as ${Type[A].prettyPrint}, which is likely unintended.\n" +
          s"Provide an explicit type parameter, e.g.: FromDataView.derived[MyType]"
      )

    fromSelfType = Some(Type[A].as_??)

    MIO
      .scoped { runSafe =>
        runSafe(ensureStdExtLoadedFrom())
        val result: Expr[FromDataView[A]] = Expr.quote {
          new FromDataView[A] {
            extension (dv: DataView) def fromDataView: Nullable[A] = {
              val _ = dv
              Expr.splice {
                runSafe {
                  deriveExtraction[A](Expr.quote(dv))
                }
              }
            }
          }
        }
        result
      }
      .runToExprOrFail(
        "FromDataView.derived",
        infoRendering = DontRender,
        errorRendering = DontRender,
        timeout = scala.concurrent.duration.FiniteDuration(10, java.util.concurrent.TimeUnit.SECONDS)
      ) { (_, errors) =>
        val errorsRendered = errors.map(e => "  - " + e.getMessage).mkString("\n")
        s"FromDataView.derived[${Type[A].prettyPrint}] failed:\n$errorsRendered"
      }
  }

  private def deriveExtraction[A: Type](dv: Expr[DataView]): MIO[Expr[Nullable[A]]] =
    deriveExtractionViaRules[A](dv)

  private def deriveExtractionViaRules[A: Type](dv: Expr[DataView]): MIO[Expr[Nullable[A]]] =
    Rules(
      FromUseImplicitRule,
      FromDataViewIdentityRule,
      FromNullableRule,
      FromOptionRule,
      FromPrimitiveRule,
      FromWideningRule,
      FromSingletonRule,
      FromMapRule,
      FromCaseClassRule,
      FromJavaBeanRule,
      FromEnumRule
    )(_[A](dv)).flatMap {
      case Right(result) =>
        MIO.pure(result)
      case Left(reasons) =>
        val reasonsStrings = reasons.toListMap.view.map { case (rule, reasons) =>
          if (reasons.isEmpty) s"  - ${rule.name}: not applicable"
          else s"  - ${rule.name}: ${reasons.mkString(", ")}"
        }.toList
        MIO.fail(
          new RuntimeException(
            s"Cannot derive FromDataView[${Type[A].prettyPrint}]:\n${reasonsStrings.mkString("\n")}"
          )
        )
    }

  abstract private class FromDataViewRule(val name: String) extends Rule {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]]
  }

  private object FromUseImplicitRule extends FromDataViewRule("use implicit when available") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      if (isFromSelfType[A]) {
        MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is the type being derived (skipping to avoid recursion)"))
      } else {
        implicit val fromDataViewA: Type[FromDataView[A]] = fromDataViewCtor.apply[A]
        implicit val nullableAT:    Type[Nullable[A]]     = FDVTypes.nullableType[A]
        Expr.summonImplicitIgnoring[FromDataView[A]](ignoredFromImplicits*).toOption match {
          case Some(instance) =>
            MIO.pure(Rule.matched(Expr.quote {
              Expr.splice(instance).fromDataView(Expr.splice(dv))
            }))
          case None =>
            MIO.pure(Rule.yielded(s"No implicit FromDataView[${Type[A].prettyPrint}] found"))
        }
      }
  }

  private object FromDataViewIdentityRule extends FromDataViewRule("handle DataView identity") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      if (Type[A] =:= Type.of[DataView]) {
        MIO.pure(Rule.matched(Expr.quote {
          Nullable(Expr.splice(dv).asInstanceOf[A])
        }))
      } else {
        MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not DataView"))
      }
  }

  private object FromNullableRule extends FromDataViewRule("handle Nullable types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      fromNullableInnerType[A] match {
        case Some(item) =>
          import item.Underlying as Item
          // DataView -> Nullable[Item]: a null/absent DataView yields an empty
          // Nullable[Item]; otherwise recurse FromDataView[Item]. The rule's own
          // result is the FromDataView wrapper Nullable[Nullable[Item]], which is
          // always present (extraction of a Nullable never "fails"): absence is
          // modelled by the inner Nullable, mirroring how Option/null map to a
          // present-but-empty value.
          deriveExtraction[Item](dv).map { innerExtraction =>
            val result: Expr[Nullable[Nullable[Item]]] = Expr.quote {
              if (Expr.splice(dv).isNull) Nullable(Nullable.empty[Item])
              else Nullable(Expr.splice(innerExtraction))
            }
            Rule.matched(result.asInstanceOf[Expr[Nullable[A]]])
          }
        case scala.None =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not a Nullable"))
      }
  }

  private object FromOptionRule extends FromDataViewRule("handle Option types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      fromOptionCtor.unapply(Type[A]) match {
        case Some(item) =>
          import item.Underlying as Item
          // DataView -> Option[Item]: a null/absent DataView yields None;
          // otherwise Some(FromDataView[Item]). Placed before FromEnumRule so the
          // sealed Option is not mis-handled as an enum. As with Nullable, the
          // rule's own FromDataView wrapper Nullable[Option[Item]] is always
          // present.
          deriveExtraction[Item](dv).map { innerExtraction =>
            val result: Expr[Nullable[Option[Item]]] = Expr.quote {
              if (Expr.splice(dv).isNull) Nullable(Option.empty[Item])
              else Nullable(Expr.splice(innerExtraction).fold(Option.empty[Item])(Some(_)))
            }
            Rule.matched(result.asInstanceOf[Expr[Nullable[A]]])
          }
        case scala.None =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not an Option"))
      }
  }

  private object FromPrimitiveRule extends FromDataViewRule("handle primitive types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] = {
      val t = Type[A]
      val accessor: Option[Expr[DataView] => Expr[Nullable[A]]] =
        if (t =:= Type.of[Boolean]) Some(d => Expr.quote(Expr.splice(d).asBoolean.asInstanceOf[Nullable[A]]))
        else if (t =:= Type.of[Short]) Some(d => Expr.quote(Expr.splice(d).asShort.asInstanceOf[Nullable[A]]))
        else if (t =:= Type.of[Int]) Some(d => Expr.quote(Expr.splice(d).asInt.asInstanceOf[Nullable[A]]))
        else if (t =:= Type.of[Long]) Some(d => Expr.quote(Expr.splice(d).asLong.asInstanceOf[Nullable[A]]))
        else if (t =:= Type.of[Float]) Some(d => Expr.quote(Expr.splice(d).asFloat.asInstanceOf[Nullable[A]]))
        else if (t =:= Type.of[Double]) Some(d => Expr.quote(Expr.splice(d).asDouble.asInstanceOf[Nullable[A]]))
        else if (t =:= Type.of[String]) Some(d => Expr.quote(Expr.splice(d).asString.asInstanceOf[Nullable[A]]))
        else if (t =:= Type.of[java.math.BigDecimal]) Some(d => Expr.quote(Expr.splice(d).asBigDecimal.asInstanceOf[Nullable[A]]))
        else None

      accessor match {
        case Some(acc) =>
          MIO.pure(Rule.matched(acc(dv)))
        case None =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not a DataView-primitive type"))
      }
    }
  }

  private object FromWideningRule extends FromDataViewRule("handle widening conversions") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] = {
      val t = Type[A]
      if (t =:= Type.of[Byte]) {
        MIO.pure(Rule.matched(Expr.quote {
          Expr.splice(dv).asShort.map(_.toByte).asInstanceOf[Nullable[A]]
        }))
      } else if (t =:= Type.of[Char]) {
        MIO.pure(Rule.matched(Expr.quote {
          Expr.splice(dv).asString.map(s => if (s.nonEmpty) s.charAt(0) else ' ').asInstanceOf[Nullable[A]]
        }))
      } else {
        MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} has no widening conversion"))
      }
    }
  }

  private object FromSingletonRule extends FromDataViewRule("handle singleton types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      SingletonValue.parse[A].toEither match {
        case Right(sv) =>
          val name = Type.shortName[A]
          MIO.pure(
            Rule.matched(
              Expr.quote {
                val s = Expr.splice(dv).asString
                if (s.isDefined && s.get == Expr.splice(Expr(name))) Nullable(Expr.splice(sv.singletonExpr))
                else Nullable.empty[A]
              }
            )
          )
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }
  }

  /** Unwraps a field's extraction result `Nullable[F]` to the field value `F`.
    *
    * For a normal field type the extraction either succeeded (`.get`) or, on a type mismatch, throws — the field could not be reconstructed. For a field whose type is itself a [[lowlevel.Nullable]],
    * extraction is infallible (an absent value simply reconstructs as an empty Nullable), so the result is a `Nullable[Nullable[X]]` whose two optionality levels must be `flatten`-ed into the single
    * field-level `Nullable[X]`: `Nullable.apply` collapses a nested empty into `NestedNone(1)`, which `.get` cannot bind (the inner TypeTest rejects a `NestedNone`), so `.flatten` — which maps
    * `NestedNone(1) -> NestedNone(0)` and a present-present to the inner value — is the correct unwrap. (The outer level is always present because Nullable-field extraction never fails.)
    */
  private def unwrapFieldValue[F: Type](nullableFieldExpr: Expr[Nullable[F]]): Expr[F] =
    fromNullableInnerType[F] match {
      case Some(item) =>
        import item.Underlying as Item
        Expr
          .quote {
            Expr.splice(nullableFieldExpr.asInstanceOf[Expr[Nullable[Nullable[Item]]]]).flatten
          }
          .asInstanceOf[Expr[F]]
      case scala.None =>
        Expr.quote {
          Expr.splice(nullableFieldExpr).get
        }
    }

  private object FromMapRule extends FromDataViewRule("handle map types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      Type[A] match {
        case IsMap(isMap) =>
          import isMap.Underlying as Pair
          import isMap.value.Key as K
          import isMap.value.Value as V
          if (!(isMap.value.Key =:= Type.of[String])) {
            MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} key type ${isMap.value.Key.prettyPrint} is not String"))
          } else {
            // DataView -> Map[String, V]: read the DataView's VectorMap, extract
            // each value via FromDataView[V] (dropping entries whose value cannot
            // be reconstructed), and rebuild the target map via its Factory.
            // Mirrors AsDataView.HandleMapRule in the opposite direction. A
            // non-map DataView yields an empty Nullable[A].
            MIO.scoped { runSafe =>
              val valueExtractor: Expr[DataView => Nullable[V]] = Expr.quote { (entry: DataView) =>
                val _ = entry
                Expr.splice {
                  runSafe(deriveExtraction[V](Expr.quote(entry)))
                }
              }
              val factory: Expr[scala.collection.Factory[Pair, A]] =
                isMap.value.factory.asInstanceOf[Expr[scala.collection.Factory[Pair, A]]]
              val pairBuilder: Expr[(String, V) => Pair] = Expr.quote { (k: String, v: V) =>
                Expr.splice {
                  isMap.value.pair(Expr.quote(k).asInstanceOf[Expr[K]], Expr.quote(v))
                }
              }
              Rule.matched(
                Expr.quote {
                  Expr.splice(dv).asMap.fold[Nullable[A]](Nullable.empty[A]) { m =>
                    val pairs = scala.collection.mutable.ListBuffer.empty[Pair]
                    m.foreach { case (k, valueDv) =>
                      Expr.splice(valueExtractor)(valueDv).foreach { v =>
                        pairs += Expr.splice(pairBuilder)(k, v)
                      }
                    }
                    Nullable(Expr.splice(factory).fromSpecific(pairs))
                  }
                }
              )
            }
          }
        case _ =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not a map type"))
      }
  }

  private object FromCaseClassRule extends FromDataViewRule("handle case class types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      CaseClass.parse[A].toEither match {
        case Right(cc) =>
          deriveCaseClassExtraction[A](cc, dv).map(Rule.matched)
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }

    private def deriveCaseClassExtraction[A: Type](
      cc: CaseClass[A],
      dv: Expr[DataView]
    ): MIO[Expr[Nullable[A]]] =
      cc.construct[MIO](
        new CaseClass.ConstructField[MIO] {
          def apply(field: Parameter): MIO[Expr[field.tpe.Underlying]] = {
            import field.tpe.Underlying as FieldType
            deriveExtraction[FieldType](
              Expr.quote {
                val m = Expr.splice(dv).asMap
                if (m.isDefined) m.get.getOrElse(Expr.splice(Expr(field.name)), DataView.nil)
                else DataView.nil
              }
            ).map { nullableFieldExpr =>
              unwrapFieldValue[FieldType](nullableFieldExpr).asInstanceOf[Expr[field.tpe.Underlying]]
            }
          }
        }
      ).flatMap {
        case Some(constructExpr) =>
          MIO.pure(Expr.quote {
            val m = Expr.splice(dv).asMap
            if (m.isDefined) Nullable(Expr.splice(constructExpr))
            else Nullable.empty[A]
          })
        case None =>
          MIO.fail(
            new RuntimeException(
              s"Failed to construct ${Type[A].prettyPrint} from DataView"
            )
          )
      }
  }

  private object FromJavaBeanRule extends FromDataViewRule("handle Java Bean types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      JavaBean.parse[A].toEither match {
        case Right(jb) =>
          deriveJavaBeanExtraction[A](jb, dv).map(Rule.matched)
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }

    private def deriveJavaBeanExtraction[A: Type](
      jb: JavaBean[A],
      dv: Expr[DataView]
    ): MIO[Expr[Nullable[A]]] = {
      val setField: JavaBean.SetField[MIO] = new JavaBean.SetField[MIO] {
        def apply(name: String, input: Parameter): MIO[Expr[input.tpe.Underlying]] = {
          import input.tpe.Underlying as FieldType
          deriveExtraction[FieldType](
            Expr.quote {
              val m = Expr.splice(dv).asMap
              if (m.isDefined) m.get.getOrElse(Expr.splice(Expr(name)), DataView.nil)
              else DataView.nil
            }
          ).map { nullableFieldExpr =>
            unwrapFieldValue[FieldType](nullableFieldExpr).asInstanceOf[Expr[input.tpe.Underlying]]
          }
        }
      }
      jb.constructWithSetters[MIO](setField).flatMap {
        case Some(constructExpr) =>
          MIO.pure(Expr.quote {
            val m = Expr.splice(dv).asMap
            if (m.isDefined) Nullable(Expr.splice(constructExpr))
            else Nullable.empty[A]
          })
        case None =>
          MIO.fail(
            new RuntimeException(
              s"Failed to construct ${Type[A].prettyPrint} from DataView via JavaBean setters"
            )
          )
      }
    }
  }

  private object FromEnumRule extends FromDataViewRule("handle enum/sealed types") {
    def apply[A: Type](dv: Expr[DataView]): MIO[Rule.Applicability[Expr[Nullable[A]]]] =
      Enum.parse[A].toEither match {
        case Right(enumm) =>
          deriveEnumExtraction[A](enumm, dv).map(Rule.matched)
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }

    private def deriveEnumExtraction[A: Type](
      enumm: Enum[A],
      dv:    Expr[DataView]
    ): MIO[Expr[Nullable[A]]] = {
      val children = enumm.exhaustiveChildren
      children match {
        case Some(childMap) =>
          val entries = childMap.toListMap.toList
          entries
            .foldLeft(MIO.pure(Expr.quote(Nullable.empty[A]))) { case (accMIO, (_, childType)) =>
              import childType.Underlying as CaseType
              accMIO.flatMap { accExpr =>
                deriveExtraction[CaseType](dv).map { caseExtraction =>
                  Expr.quote {
                    val prev = Expr.splice(accExpr)
                    if (prev.isDefined) prev
                    else Expr.splice(caseExtraction).map(_.asInstanceOf[A])
                  }
                }
              }
            }
            .map(resultExpr => resultExpr)
        case None =>
          MIO.fail(
            new RuntimeException(
              s"Cannot derive FromDataView for non-exhaustive enum ${Type[A].prettyPrint}"
            )
          )
      }
    }
  }
}
