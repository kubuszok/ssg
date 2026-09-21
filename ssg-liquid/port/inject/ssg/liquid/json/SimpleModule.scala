/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the jackson types the four classes under src/main/java/liqp/spi and
 *   src/main/java/liqp/parser/LiquidSupport.java name — SimpleModule, StdSerializer,
 *   JsonGenerator, SerializerProvider, TypeReference, SerializationFeature, JavaTimeModule and
 *   JsonProcessingException
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: jackson-databind and jackson-datatype-jsr310 are JVM-only artifacts.
 *   Convention: java's member names and arities, so liqp's five `spi` classes and its
 *     `LiquidSerializer` port unchanged on top of them.
 *   Idiom: a generator BUILDS a value rather than writing bytes, because every liqp serializer
 *     exists to produce a small object that another liqp function reads straight back.
 */
package ssg
package liquid
package json

import java.util.{ LinkedHashMap => JLinkedHashMap, Map => JMap }

/** A bundle of type serializers, registered on a mapper. */
class SimpleModule(val name: String) {

  private var registered: List[StdSerializer[?]] = Nil

  def this() = this("")

  def addSerializer[T](ser: StdSerializer[T]): SimpleModule = {
    registered = ser :: registered
    this
  }

  private[json] def serializers: List[StdSerializer[?]] = registered
}

/** What liqp registers to teach the mapper about `java.time`. It carries no serializers of its
  * own: the temporal types reach `Json.write` as themselves, and the `spi` providers are what
  * mark a value for restoring after an eager conversion.
  */
class JavaTimeModule extends SimpleModule("java time")

/** Writes one value of a type the mapper was taught about. */
abstract class StdSerializer[T](private val handled: Class[T]) extends java.io.Serializable {

  def handledType(): Class[T] = handled

  def serialize(value: T, gen: JsonGenerator, provider: SerializerProvider): Unit
}

/** Builds the value a serializer writes. liqp writes a flat object or hands over a whole value,
  * so a nested object is refused rather than silently flattened.
  */
final class JsonGenerator private[json] (mapper: JsonMapper) {

  private var built:   Object                        = null
  private var current: JLinkedHashMap[String, Object] = null

  def writeStartObject(): Unit = {
    if (current != null) throw new UnsupportedOperationException("nested JSON objects are not written here")
    current = new JLinkedHashMap[String, Object]()
    built = current
  }

  def writeEndObject(): Unit = ()

  def writeBooleanField(fieldName: String, value: Boolean): Unit =
    fields.put(fieldName, java.lang.Boolean.valueOf(value))

  def writeStringField(fieldName: String, value: String): Unit =
    fields.put(fieldName, value)

  def writeObject(value: Object): Unit =
    built = mapper.normalise(value)

  private def fields: JMap[String, Object] =
    if (current == null) throw new IllegalStateException("no JSON object has been started") else current

  private[json] def result: Object = built
}

/** Passed to a serializer and never read. */
final class SerializerProvider

/** A super-type token. Nothing reads its type argument here — this mapper converts structurally —
  * so it exists to keep java's `convertValue(value, MAP_TYPE_REF)` spelled as java spelled it.
  */
abstract class TypeReference[T]

/** The one mapper feature liqp sets. */
enum SerializationFeature {
  case FAIL_ON_EMPTY_BEANS
}

/** What `writeValueAsString` declares, so liqp's `json` filter keeps its own catch clause. */
class JsonProcessingException(message: String, cause: Throwable) extends java.io.IOException(message, cause) {

  def this(message: String) = this(message, null)
}
