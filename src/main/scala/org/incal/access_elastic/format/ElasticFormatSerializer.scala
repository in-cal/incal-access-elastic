package org.incal.access_elastic.format

import java.util.Date

import com.sksamuel.elastic4s.source.Indexable
import com.sksamuel.elastic4s.{HitAs, RichGetResponse, RichSearchHit, RichSearchResponse}
import org.incal.access_elastic.ElasticSerializer
import org.incal.core.util.DynamicConstructorFinder
import play.api.libs.json.{Format, Json, Reads, Writes}

import scala.collection.mutable.{Map => MMap}
import scala.reflect.runtime.{universe => ru}

trait ElasticFormatSerializer[E] extends ElasticSerializer[E] {

  protected implicit val format: Format[E]
  protected implicit val manifest: Manifest[E]

  private val concreteClassFieldName = "concreteClass"

  private val typeValueConverters: Seq[(ru.Type, Any => Any)] =
  Seq((
    ru.typeOf[Date],
    (value: Any) =>
      value match {
        case ms: Long => new Date(ms)
        case ms: Int => new Date(ms)
        case _ => value
      }
  ))

  protected val defaultConstructorFinder = DynamicConstructorFinder.apply[E]
  protected val classNameConstructorFinderMap = MMap[String, DynamicConstructorFinder[E]]()

  override protected def serializeGetResult(response: RichGetResponse): Option[E] = {
    val originalResponse = response.original
    if (originalResponse.isExists)
      Some(Json.parse(originalResponse.getSourceAsBytes).as[E])
    else
      None
  }

  private implicit def toHitAs[A: Reads] = new HitAs[A] {
    def as(hit: RichSearchHit) = Json.parse(hit.source).as[A] // TODO: this.source or result.sourceAsString
  }

  protected implicit def toIndexable[A: Writes] = new Indexable[A] {
    def json(t: A) = Json.toJson(t).toString()
  }

  override protected def serializeSearchResult(
    response: RichSearchResponse
  ): Traversable[E] =
    response.as[E]

  override protected def serializeSearchHit(
    result: RichSearchHit
  ): E = result.as[E]

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
    results: Array[RichSearchHit]
  ): Traversable[E] =
    if (!projection.contains(concreteClassFieldName)) {
      val constructor = constructorOrException(projection)
      results.map { result =>
        val fieldValues = result.fieldsSeq.map(field => (field.name, field.getValue[Any]))
        constructor(fieldValues.toMap).get
      }
    } else
    // TODO: optimize me... we should group the results by a concrete class field name
      results.map { serializeProjectionSearchHit(projection, _) }

  protected def constructorOrException(
    fieldNames: Seq[String],
    concreteClassName: Option[String] = None
  ) = {
    val unrenamedFieldNames = fieldNames.map(unrename)

    val constructorFinder =
    // if concrete class name is defined check the map (cache) or create a new constructor using the class name
      concreteClassName.map { className =>
        classNameConstructorFinderMap.getOrElseUpdate(
          className,
          DynamicConstructorFinder.apply(className)
        )
        // otherwise use the default one, which opts to call constructor of the core class associated with a type E
      }.getOrElse(
        defaultConstructorFinder
      )

    constructorFinder(unrenamedFieldNames, typeValueConverters).getOrElse {
      throw new IllegalArgumentException(s"No constructor of the class '${constructorFinder.classSymbol.fullName}' matches the query result fields '${fieldNames.mkString(", ")}'. Adjust your query or introduce an appropriate constructor.")
    }
  }

  protected def unrename(fieldName: String): String = fieldName
  //       ElasticIdRenameUtil.unrename(fieldName)
}