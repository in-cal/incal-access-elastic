package org.incal.access_elastic

import com.sksamuel.elastic4s.mappings.FieldType.DoubleType
import com.sksamuel.elastic4s.mappings.{NumberFieldDefinition}
import org.elasticsearch.common.xcontent.XContentBuilder

/**
  * This is a hacky class that add "coerce" to the (Double) type definition.
  * Once "coerce" attribute is supported by Elastic4s, this class can be removed
  *
  * @param name
  * @since 2018
  * @author Peter Banda
  */
final class CoerceDoubleFieldDefinition(name: String) extends NumberFieldDefinition[Double](DoubleType, name) {
  override def build(source: XContentBuilder, startObject: Boolean = true): Unit = {
    if (startObject)
      source.startObject(name)

    super.build(source, false)
    source.field("coerce", true)

    if (startObject)
      source.endObject()
  }
}