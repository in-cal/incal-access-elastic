package org.incal.access.elastic.caseclass

import com.sksamuel.elastic4s.Indexable
import org.incal.access.elastic.ElasticSerializer
import org.incal.core.util.ReflectionUtil.{getCaseClassMemberMethods, getFieldNamesAndValues}

import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import java.util.{Date, UUID}

import com.sksamuel.elastic4s.http.get.GetResponse
import com.sksamuel.elastic4s.http.search.{SearchHit, SearchResponse}
import org.apache.commons.lang3.StringEscapeUtils

trait ElasticCaseClassSerializer[E] extends ElasticSerializer[E] with HasDynamicConstructor[E] {

  protected implicit val classTag: ClassTag[E]

  private val members = getCaseClassMemberMethods[E]

  // override if a special json serialization is needed
  protected implicit val indexable = new Indexable[E] {
    def json(t: E) = {

      val jsonString = getFieldNamesAndValues(t, members).flatMap {
        case (fieldName, value) =>
          valueToJsonString(value).map(jsonValue =>
            "  \"" + fieldName + "\": " + jsonValue
          )
      }.mkString(", ")

      s"{${jsonString}}"
    }
  }

  protected def valueToJsonString(value: Any): Option[String] =
    value match {
      case None => None
      case Some(x) => valueToJsonString(x)
      case _ =>
        val x: String = value match {
          case string: String => "\"" + StringEscapeUtils.escapeJava(string) + "\""
          case date: Date => date.getTime.toString
          case uuid: UUID => "\"" + uuid.toString + "\""
          case _ => value.toString
        }

        Some(x)
    }

  override protected def serializeGetResult(response: GetResponse): Option[E] = {
    if (response.exists) {
      val sourceMap = response.sourceAsMap
      val constructor = constructorOrException(sourceMap)
      constructor(sourceMap)
    } else
      None
  }

  override protected def serializeSearchResult(
    response: SearchResponse
  ): Traversable[E] =
    response.hits.hits.toTraversable match {
      case Nil => Nil

      case hits =>
        val constructor = constructorOrException(hits.head.sourceAsMap)
        hits.map( hit =>
          constructor(hit.sourceAsMap).get
        )
    }

  override protected def serializeSearchHit(
    result: SearchHit
  ): E = {
    val sourceMap = result.sourceAsMap
    val constructor = constructorOrException(sourceMap)
    constructor(sourceMap).get
  }

  override protected def serializeProjectionSearchResult(
    projection: Seq[String],
    result: Traversable[(String, Any)]
  ) = {
    val fieldNameValueMap = result.toMap

    val constructor =
      constructorOrException(
        projection,
        fieldNameValueMap.get(concreteClassFieldName).map(_.asInstanceOf[String])
      )

    constructor(fieldNameValueMap).get
  }

  override protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[SearchHit]
  ): Traversable[E] =
    if (!projection.contains(concreteClassFieldName)) {
      val constructor = constructorOrException(projection)
      results.map { result =>
        constructor(result.fields).get
      }
    } else
      // TODO: optimize me... we should group the results by a concrete class field name
      results.map { serializeProjectionSearchHit(projection, _) }
}