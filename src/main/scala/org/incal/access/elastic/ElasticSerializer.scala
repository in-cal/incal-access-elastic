package org.incal.access.elastic

import com.sksamuel.elastic4s.http.get.GetResponse
import com.sksamuel.elastic4s.http.search.{SearchHit, SearchResponse}

/**
  * Trait describing a serializer of ES "search" and "get" responses.
  *
  * @tparam E
  * @since 2018
  * @author Peter Banda
  */
trait ElasticSerializer[E] {

  protected def serializeGetResult(
    response: GetResponse
  ): Option[E]

  protected def serializeSearchResult(
    response: SearchResponse
  ): Traversable[E]

  protected def serializeProjectionSearchResult(
    projection: Seq[String],
    result: Traversable[(String, Any)]
  ): E

  // by default just iterate through and serialize each result independently
  protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[SearchHit]
  ): Traversable[E] =
    results.map { serializeProjectionSearchHit(projection, _) }

  protected def serializeProjectionSearchHit(
    projection: Seq[String],
    result: SearchHit
  ): E = {
    val fieldValues = result.fields
    serializeProjectionSearchResult(projection, fieldValues)
  }

  protected def serializeSearchHit(
    result: SearchHit
  ): E
//
//  {
//    val fieldValues = result.fieldsSeq.map(field => (field.name, field.getValue[Any]))
//    serializeProjectionSearchResult(fieldValues.map(_._1), fieldValues)
//  }
}