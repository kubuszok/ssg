/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the jackson-databind ObjectMapper liqp holds in TemplateParser.mapper and
 *   reaches from src/main/java/liqp/{Template,TemplateParser}.java,
 *   src/main/java/liqp/filters/Json.java, src/main/java/liqp/filters/where/LiquidWhereImpl.java,
 *   src/main/java/liqp/parser/LiquidSupport.java and the four classes under src/main/java/liqp/spi
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: jackson-databind is a JVM-only artifact, so the four operations liqp asks of an
 *     ObjectMapper — read a JSON string, write a JSON string, normalise a value into JSON's own
 *     shape, and carry the registered type serializers — are answered here.
 *   Convention: java's member names and arities, so every liqp call site ports unchanged.
 *   Refusal: reading an arbitrary object's FIELDS is what jackson did and what no Scala.js or
 *     Scala Native build can do; `normalise` raises instead of guessing.
 */
package ssg
package liquid
package json

import java.util.{ ArrayList => JArrayList, LinkedHashMap => JLinkedHashMap, List => JList, Map => JMap }

/** The four things liqp asks of a JSON mapper. */
class JsonMapper {

  /** Serializers registered by the `TypesSupport` providers, newest first, so a provider that
    * claims a subtype is consulted before one that claims its supertype.
    */
  private var serializers: List[StdSerializer[?]] = Nil

  def registerModule(module: SimpleModule): JsonMapper = {
    serializers = module.serializers ++ serializers
    this
  }

  /** liqp sets one feature, `FAIL_ON_EMPTY_BEANS` to false, which only mattered while jackson was
    * reading beans; there are none to read here.
    */
  def configure(feature: SerializationFeature, state: Boolean): JsonMapper = {
    val _ = (feature, state)
    this
  }

  def copy(): JsonMapper = {
    val fresh = new JsonMapper()
    fresh.serializers = serializers
    fresh
  }

  /** `readValue(content, HashMap.class)` — the one shape liqp reads. */
  def readValue[T](content: String, valueType: Class[T]): T =
    if (valueType.isAssignableFrom(classOf[java.util.HashMap[?, ?]])) Json.readObject(content).asInstanceOf[T]
    else Json.read(content).asInstanceOf[T]

  def writeValueAsString(value: Object): String =
    Json.write(normalise(value))

  /** `convertValue(from, Map.class | List.class | ObjectNode.class | JsonNode.class)`. */
  def convertValue[T](fromValue: Object, toValueType: Class[T]): T = {
    val normalised = normalise(fromValue)
    if (toValueType == classOf[ObjectNode]) new ObjectNode(asObjectMap(normalised)).asInstanceOf[T]
    else if (toValueType == classOf[JsonNode]) new JsonNode(normalised).asInstanceOf[T]
    else if (classOf[JMap[?, ?]].isAssignableFrom(toValueType)) asObjectMap(normalised).asInstanceOf[T]
    else if (classOf[JList[?]].isAssignableFrom(toValueType)) asList(normalised).asInstanceOf[T]
    else normalised.asInstanceOf[T]
  }

  /** `convertValue(from, MAP_TYPE_REF)` — the super-type token asks for the same map. */
  def convertValue[T](fromValue: Object, toValueTypeRef: TypeReference[T]): T = {
    val _ = toValueTypeRef
    asObjectMap(normalise(fromValue)).asInstanceOf[T]
  }

  private def asObjectMap(value: Object): JMap[String, Object] =
    value match {
      case m: JMap[?, ?] => m.asInstanceOf[JMap[String, Object]]
      case n: ObjectNode => n.fields
      case n: JsonNode   => asObjectMap(n.value)
      case other         =>
        throw new IllegalArgumentException("cannot convert " + describe(other) + " to a JSON object")
    }

  private def asList(value: Object): JList[Object] =
    value match {
      case l: JList[?] => l.asInstanceOf[JList[Object]]
      case n: JsonNode => asList(n.value)
      case other       =>
        throw new IllegalArgumentException("cannot convert " + describe(other) + " to a JSON array")
    }

  private def describe(value: Object): String =
    if (value == null) "null" else value.getClass.getName

  /** Puts a value into JSON's own shape: objects become maps of strings to values, arrays and
    * collections become lists, and everything else is a scalar jackson would have written as one.
    *
    * A registered `TypesSupport` serializer is consulted first, which is how a `Date`, a `Calendar`
    * or a `Temporal` survives an eager conversion: the provider writes a marker object holding a
    * reference, and `BasicTypesSupport.restoreObject` swaps the original back afterwards.
    */
  private[liquid] def normalise(raw: Object): Object = {
    // Over BOTH representations, because this library's whole data model is `Object` and a value
    // reaching here holds either java's collection or the one the port retyped it to. Read only as
    // java's, a retyped map is an object with fields and the refusal below would fire on it.
    val value = balticporter.runtime.JavaCollections.Reified.toJavaValue(raw)
    value match {
      case null                                                             => null
      case _: java.lang.Boolean | _: java.lang.String | _: java.lang.Number => value
      case _: java.lang.Character                                           => value.toString
      // A node this mapper made earlier in the same conversion: `objectToMap` converts to an
      // ObjectNode and then converts THAT to a map.
      case n: JsonNode => n.value
      case _           =>
        serializerFor(value) match {
          case Some(ser) => runSerializer(ser, value)
          case None      =>
            value match {
              // What java's `@JsonSerialize(using = LiquidSerializer.class)` on LiquidSupport did:
              // the interface's own `toLiquid()` answers, never its fields.
              // A DATE is a scalar to liquid, not an object with properties: `LValue.isTemporal`
              // and `asTemporal` read both shapes natively, and jackson had a serializer for each
              // rather than reflecting over them. AFTER the registered serializers above, which is
              // what marks one of these for restoring after an eager conversion — and that is also
              // where a `java.util.Calendar` is answered, by the provider that claims it, because
              // naming the type here would put a class the Scala.js javalib does not have into the
              // row every platform compiles.
              case _:  java.time.temporal.TemporalAccessor | _: java.util.Date => value
              case ls: ssg.liquid.parser.LiquidSupport                         => normalise(ls.toLiquid())
              case m:  JMap[?, ?]                      => normaliseMap(m)
              case c:  java.util.Collection[?]         => normaliseIterator(c.iterator())
              case a:  Array[?]                        => normaliseIterator(java.util.Arrays.asList(a*).iterator())
              case cs: CharSequence                    => cs.toString
              // ONE call site for the one operation whose ANSWER differs per platform: the JVM row
              // reads the object's properties, as java did, and the other two rows raise. Both live
              // in `ssg.liquid.json.Beans`, one file per row.
              case other => normaliseMap(Beans.toMap(other))
            }
        }
    }
  }

  private def normaliseMap(map: JMap[?, ?]): JMap[String, Object] = {
    val out = new JLinkedHashMap[String, Object]()
    val it  = map.entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      out.put(String.valueOf(entry.getKey), normalise(entry.getValue.asInstanceOf[Object]))
    }
    out
  }

  private def normaliseIterator(it: java.util.Iterator[?]): JList[Object] = {
    val out = new JArrayList[Object]()
    while (it.hasNext) out.add(normalise(it.next().asInstanceOf[Object]))
    out
  }

  private def serializerFor(value: Object): Option[StdSerializer[?]] =
    serializers.find(ser => ser.handledType() != null && ser.handledType().isInstance(value))

  private def runSerializer(ser: StdSerializer[?], value: Object): Object = {
    val gen = new JsonGenerator(this)
    ser.asInstanceOf[StdSerializer[Object]].serialize(value, gen, new SerializerProvider())
    gen.result
  }

}
