package org.incal.access.elastic

import com.sksamuel.elastic4s.{RichGetResponse, RichSearchHit, RichSearchResponse}

/**
  * Trait describing a serializer of ES "search" and "get" responses.
  *
  * @tparam E
  * @since 2018
  * @author Peter Banda
  */
trait ElasticSerializer[E] {

  protected def serializeGetResult(
    response: RichGetResponse
  ): Option[E]

  protected def serializeSearchResult(
    response: RichSearchResponse
  ): Traversable[E]

  protected def serializeProjectionSearchResult(
    projection: Seq[String],
    result: Traversable[(String, Any)]
  ): E

  // by default just iterate through and serialize each result independently
  protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[RichSearchHit]
  ): Traversable[E] =
    results.map { serializeProjectionSearchHit(projection, _) }

  protected def serializeProjectionSearchHit(
    projection: Seq[String],
    result: RichSearchHit
  ): E = {
    val fieldValues = result.fieldsSeq.map(field => (field.name, field.getValue[Any]))
    serializeProjectionSearchResult(projection, fieldValues)
  }

  protected def serializeSearchHit(
    result: RichSearchHit
  ): E
//
//  {
//    val fieldValues = result.fieldsSeq.map(field => (field.name, field.getValue[Any]))
//    serializeProjectionSearchResult(fieldValues.map(_._1), fieldValues)
//  }
}