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

import scala.collection.immutable.VectorMap

// Self-type is refined to MacroCommonsScala3 so this rule can drop to raw
// scala.quoted reflection for the one thing the cross-platform hearth API does
// not expose: destructuring an opaque applied type (Nullable[A] aliases the
// opaque Nullable.Impl[A], which Type.Ctor1's `unapply` — `case '[HKT[a]]` —
// cannot match). SSG is a Scala-3-only project, so this is sound; the macro
// itself runs on the JVM compiler regardless of the target platform.
private[data] trait AsDataViewMacrosImpl { this: MacroCommonsScala3 & MacroCommons & StdExtensions =>

  private lazy val asDataViewCtor: Type.Ctor1[AsDataView] = Type.Ctor1.of[AsDataView]

  /** If `A` is a [[lowlevel.Nullable]] (the opaque `Nullable.Impl[X]`), returns the existential inner type `X`; otherwise `None`.
    *
    * Uses raw reflection's `baseType` against the `Nullable.Impl` symbol, which (unlike the quoted `'[HKT[a]]` pattern that Type.Ctor1 uses) matches an opaque applied type.
    */
  protected def nullableInnerType[A: Type]: Option[??] = {
    import quotes.reflect.*
    val repr        = TypeRepr.of[A](using Type[A].asInstanceOf[scala.quoted.Type[A]])
    val nullableSym = TypeRepr.of[Nullable.Impl[Any]].typeSymbol
    repr.baseType(nullableSym) match {
      case AppliedType(_, List(arg)) =>
        Some(arg.asType.asInstanceOf[Type[Any]].as_??)
      case _ =>
        // `A` written directly as `Nullable.Impl[X]` (not via baseType) — handle
        // the AppliedType form too.
        repr match {
          case AppliedType(ctor, List(arg)) if ctor.typeSymbol == nullableSym =>
            Some(arg.asType.asInstanceOf[Type[Any]].as_??)
          case _ =>
            scala.None
        }
    }
  }

  private lazy val ignoredImplicits: Seq[UntypedMethod] =
    Type.of[AsDataView.type].asUntyped.methods.collect { case method if method.name == "derived" => method }.toSeq

  private object DVTypes {
    lazy val DataView: Type[DataView] = Type.of[DataView]
  }

  private var stdExtLoaded:         Boolean   = false
  private def ensureStdExtLoaded(): MIO[Unit] =
    if (stdExtLoaded) MIO.pure(())
    else
      Environment.loadStandardExtensions().toMIO(allowFailures = false).map { _ =>
        stdExtLoaded = true
        ()
      }

  private var selfType: Option[??] = scala.None

  def deriveAsDataView[A: Type]: Expr[AsDataView[A]] = {
    if (Type[A] =:= Type.of[Nothing].asInstanceOf[Type[A]] || Type[A] =:= Type.of[Any].asInstanceOf[Type[A]])
      Environment.reportErrorAndAbort(
        s"AsDataView.derived: type parameter was inferred as ${Type[A].prettyPrint}, which is likely unintended.\n" +
          s"Provide an explicit type parameter, e.g.: AsDataView.derived[MyType]"
      )

    selfType = Some(Type[A].as_??)

    MIO
      .scoped { runSafe =>
        runSafe(ensureStdExtLoaded())
        val result: Expr[AsDataView[A]] = Expr.quote {
          new AsDataView[A] {
            extension (a: A) def asDataView: DataView = {
              val _ = a
              Expr.splice {
                runSafe {
                  deriveConversion[A](Expr.quote(a))
                }
              }
            }
          }
        }
        result
      }
      .runToExprOrFail(
        "AsDataView.derived",
        infoRendering = DontRender,
        errorRendering = DontRender
      ) { (_, errors) =>
        val errorsRendered = errors.map(e => "  - " + e.getMessage).mkString("\n")
        s"AsDataView.derived[${Type[A].prettyPrint}] failed:\n$errorsRendered"
      }
  }

  private def deriveConversion[A: Type](value: Expr[A]): MIO[Expr[DataView]] =
    deriveConversionViaRules[A](value)

  private def isSelfType[A: Type]: Boolean =
    selfType.exists(st => Type[A] <:< st.Underlying.asInstanceOf[Type[A]])

  private def deriveConversionViaRules[A: Type](value: Expr[A]): MIO[Expr[DataView]] =
    Rules(
      UseImplicitWhenAvailableRule,
      HandleDataViewIdentityRule,
      HandlePrimitiveRule,
      HandleWideningRule,
      HandleOptionRule,
      HandleNullableRule,
      HandleMapRule,
      HandleCollectionRule,
      HandleSingletonRule,
      HandleCaseClassRule,
      HandleJavaBeanRule,
      HandleEnumRule
    )(_[A](value)).flatMap {
      case Right(result) =>
        MIO.pure(result)
      case Left(reasons) =>
        val reasonsStrings = reasons.toListMap.view.map { case (rule, reasons) =>
          if (reasons.isEmpty) s"  - ${rule.name}: not applicable"
          else s"  - ${rule.name}: ${reasons.mkString(", ")}"
        }.toList
        MIO.fail(
          new RuntimeException(
            s"Cannot derive AsDataView[${Type[A].prettyPrint}]:\n${reasonsStrings.mkString("\n")}"
          )
        )
    }

  abstract private class AsDataViewRule(val name: String) extends Rule {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]]
  }

  // --- Rules ---

  private object UseImplicitWhenAvailableRule extends AsDataViewRule("use implicit when available") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      if (isSelfType[A]) {
        MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is the type being derived (skipping to avoid recursion)"))
      } else {
        implicit val asDataViewA: Type[AsDataView[A]] = asDataViewCtor.apply[A]
        Expr.summonImplicitIgnoring[AsDataView[A]](ignoredImplicits*).toOption match {
          case Some(instance) =>
            MIO.pure(Rule.matched(Expr.quote {
              Expr.splice(instance).asDataView(Expr.splice(value))
            }))
          case None =>
            MIO.pure(Rule.yielded(s"No implicit AsDataView[${Type[A].prettyPrint}] found"))
        }
      }
  }

  private object HandleDataViewIdentityRule extends AsDataViewRule("handle DataView identity") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      if (Type[A] =:= DVTypes.DataView) {
        MIO.pure(Rule.matched(value.asInstanceOf[Expr[DataView]]))
      } else {
        MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not DataView"))
      }
  }

  private object HandlePrimitiveRule extends AsDataViewRule("handle primitive types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] = {
      val t = Type[A]
      if (
        t =:= Type.of[Boolean] || t =:= Type.of[Short] || t =:= Type.of[Int] ||
        t =:= Type.of[Long] || t =:= Type.of[Float] || t =:= Type.of[Double] ||
        t =:= Type.of[String] || t =:= Type.of[java.math.BigDecimal]
      ) {
        MIO.pure(
          Rule.matched(
            Expr.quote {
              DataView(
                Expr.splice(value).asInstanceOf[Boolean | Short | Int | Long | Float | Double | String | java.math.BigDecimal | Vector[DataView] | VectorMap[String, DataView]]
              )
            }
          )
        )
      } else {
        MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not a DataView-primitive type"))
      }
    }
  }

  private object HandleWideningRule extends AsDataViewRule("handle widening conversions") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] = {
      val t = Type[A]
      if (t =:= Type.of[Byte]) {
        MIO.pure(Rule.matched(Expr.quote {
          DataView(Expr.splice(value).asInstanceOf[Byte].toShort)
        }))
      } else if (t =:= Type.of[Char]) {
        MIO.pure(Rule.matched(Expr.quote {
          DataView(Expr.splice(value).asInstanceOf[Char].toString)
        }))
      } else {
        MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} has no widening conversion"))
      }
    }
  }

  private object HandleOptionRule extends AsDataViewRule("handle Option types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      Type[A] match {
        case IsOption(isOption) =>
          import isOption.Underlying as Item
          implicit val dvT: Type[DataView] = DVTypes.DataView
          val result:       Expr[DataView] = isOption.value.fold[DataView](value)(
            Expr.quote(DataView.nil),
            (itemExpr: Expr[Item]) =>
              MIO
                .scoped { runSafe =>
                  runSafe(deriveConversion[Item](itemExpr))
                }
                .unsafe
                .runSync
                ._2
                .fold(
                  errors => Environment.reportErrorAndAbort(errors.head.getMessage),
                  identity
                )
          )
          MIO.pure(Rule.matched(result))
        case _ =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not an Option"))
      }
  }

  private object HandleNullableRule extends AsDataViewRule("handle Nullable types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      nullableInnerType[A] match {
        case Some(item) =>
          import item.Underlying as Item
          // Nullable[A] mirrors HandleOptionRule: an empty Nullable becomes
          // DataView.nil, a present value recurses via AsDataView[Item]. Nullable
          // is an opaque type, so its present value is reached through its `fold`
          // extension rather than the std IsOption provider.
          MIO.scoped { runSafe =>
            val itemConverter: Expr[Item => DataView] = Expr.quote { (item: Item) =>
              val _ = item
              Expr.splice {
                runSafe(deriveConversion[Item](Expr.quote(item)))
              }
            }
            val nullableValue: Expr[Nullable[Item]] = value.asInstanceOf[Expr[Nullable[Item]]]
            Rule.matched(Expr.quote {
              Expr.splice(nullableValue).fold[DataView](DataView.nil)(Expr.splice(itemConverter))
            })
          }
        case scala.None =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not a Nullable"))
      }
  }

  private object HandleCollectionRule extends AsDataViewRule("handle collection types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      Type[A] match {
        case IsCollection(isCollection) =>
          import isCollection.Underlying as Item
          MIO.scoped { runSafe =>
            val itemConverter: Expr[Item => DataView] = Expr.quote { (item: Item) =>
              val _ = item
              Expr.splice {
                runSafe(deriveConversion[Item](Expr.quote(item)))
              }
            }
            val iterableExpr: Expr[Iterable[Item]] = isCollection.value.asIterable(value)
            Rule.matched(Expr.quote {
              DataView(Expr.splice(iterableExpr).iterator.map(Expr.splice(itemConverter)).toVector)
            })
          }
        case _ =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not a collection"))
      }
  }

  private object HandleMapRule extends AsDataViewRule("handle map types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      Type[A] match {
        case IsMap(isMap) =>
          import isMap.Underlying as Pair
          import isMap.value.Key as K
          import isMap.value.Value as V
          if (!(isMap.value.Key =:= Type.of[String])) {
            MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} key type ${isMap.value.Key.prettyPrint} is not String"))
          } else {
            MIO.scoped { runSafe =>
              val valueConverter: Expr[V => DataView] = Expr.quote { (v: V) =>
                val _ = v
                Expr.splice {
                  runSafe(deriveConversion[V](Expr.quote(v)))
                }
              }
              val iterableExpr: Expr[Iterable[Pair]] = isMap.value.asIterable(value)
              Rule.matched(
                Expr.quote {
                  var builder = VectorMap.empty[String, DataView]
                  Expr.splice(iterableExpr).foreach { pair =>
                    val _ = pair
                    val k = Expr.splice(isMap.value.key(Expr.quote(pair))).asInstanceOf[String]
                    val v = Expr.splice(isMap.value.value(Expr.quote(pair)))
                    builder = builder.updated(k, Expr.splice(valueConverter)(v))
                  }
                  DataView(builder)
                }
              )
            }
          }
        case _ =>
          MIO.pure(Rule.yielded(s"${Type[A].prettyPrint} is not a map type"))
      }
  }

  private object HandleSingletonRule extends AsDataViewRule("handle singleton types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      SingletonValue.parse[A].toEither match {
        case Right(_) =>
          val name = Type.shortName[A]
          MIO.pure(Rule.matched(Expr.quote {
            DataView(Expr.splice(Expr(name)))
          }))
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }
  }

  private object HandleCaseClassRule extends AsDataViewRule("handle case class types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      CaseClass.parse[A].toEither match {
        case Right(cc) =>
          deriveCaseClassConversion[A](cc, value).map(Rule.matched)
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }

    private def deriveCaseClassConversion[A: Type](
      cc:    CaseClass[A],
      value: Expr[A]
    ): MIO[Expr[DataView]] = {
      val fieldValues  = cc.caseFieldValuesAt(value)
      val fieldEntries = fieldValues.toList

      fieldEntries
        .foldLeft(MIO.pure(List.empty[(String, Expr[DataView])])) { case (accMIO, (fieldName, fieldExpr)) =>
          import fieldExpr.Underlying as FieldType
          accMIO.flatMap { acc =>
            deriveConversion[FieldType](fieldExpr.value.asInstanceOf[Expr[FieldType]]).map { dvExpr =>
              acc :+ (fieldName -> dvExpr)
            }
          }
        }
        .map { entries =>
          entries.foldRight(Expr.quote(VectorMap.empty[String, DataView])) { case ((name, dvExpr), accExpr) =>
            Expr.quote {
              Expr.splice(accExpr).updated(Expr.splice(Expr(name)), Expr.splice(dvExpr))
            }
          } match {
            case mapExpr =>
              Expr.quote {
                DataView(Expr.splice(mapExpr))
              }
          }
        }
    }
  }

  private object HandleJavaBeanRule extends AsDataViewRule("handle Java Bean types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      JavaBean.parse[A].toEither match {
        case Right(jb) =>
          deriveJavaBeanConversion[A](jb, value).map(Rule.matched)
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }

    private def deriveJavaBeanConversion[A: Type](
      jb:    JavaBean[A],
      value: Expr[A]
    ): MIO[Expr[DataView]] = {
      val getters = jb.beanGetters

      getters
        .foldLeft(MIO.pure(List.empty[(String, Expr[DataView])])) { case (accMIO, getter) =>
          // hearth: JavaBean.beanGetters is List[Existential[Method.OfInstance[A, *]]] — `.value`
          // unwraps the existential to the underlying Method.OfInstance, which exposes the Returned type
          // and `apply(instance, arguments)` to build the getter-call expression.
          val method = getter.value
          val name   = method.javaAccessorName.getOrElse(method.name)
          accMIO.flatMap { acc =>
            method.apply(value, Map.empty) match {
              case Right(getterResult) =>
                import method.Returned as ReturnType
                deriveConversion[ReturnType](getterResult).map { dvExpr =>
                  acc :+ (name -> dvExpr)
                }
              case Left(error) =>
                MIO.fail(new RuntimeException(s"Failed to call getter $name: $error"))
            }
          }
        }
        .map { entries =>
          entries.foldRight(Expr.quote(VectorMap.empty[String, DataView])) { case ((name, dvExpr), accExpr) =>
            Expr.quote {
              Expr.splice(accExpr).updated(Expr.splice(Expr(name)), Expr.splice(dvExpr))
            }
          } match {
            case mapExpr =>
              Expr.quote {
                DataView(Expr.splice(mapExpr))
              }
          }
        }
    }
  }

  private object HandleEnumRule extends AsDataViewRule("handle enum/sealed types") {
    def apply[A: Type](value: Expr[A]): MIO[Rule.Applicability[Expr[DataView]]] =
      Enum.parse[A].toEither match {
        case Right(enumm) =>
          deriveEnumConversion[A](enumm, value).map(Rule.matched)
        case Left(reason) =>
          MIO.pure(Rule.yielded(reason))
      }

    private def deriveEnumConversion[A: Type](
      enumm: Enum[A],
      value: Expr[A]
    ): MIO[Expr[DataView]] = {
      implicit val dvT: Type[DataView] = DVTypes.DataView

      enumm
        .matchOn[MIO, DataView](value) { caseExpr =>
          import caseExpr.Underlying as CaseType
          deriveConversion[CaseType](caseExpr.value.asInstanceOf[Expr[CaseType]])
        }
        .flatMap {
          case Some(expr) => MIO.pure(expr)
          case None       =>
            MIO.fail(
              new RuntimeException(
                s"Failed to derive enum match for ${Type[A].prettyPrint}"
              )
            )
        }
    }
  }
}
