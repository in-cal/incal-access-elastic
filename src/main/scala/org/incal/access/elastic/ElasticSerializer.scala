package org.incal.access.elastic

import com.sksamuel.elastic4s.http.get.GetResponse
import com.sksamuel.elastic4s.http.search.{SearchHit, SearchResponse}
import com.sksamuel.exts.Logging
import org.incal.core.dataaccess.InCalDataAccessException

/**
  * Trait describing a serializer of ES "search" and "get" responses.
  *
  * @tparam E
  * @since 2018
  * @author Peter Banda
  */
trait ElasticSerializer[E] {

  logging: Logging =>

  protected def serializeGetResult(
    response: GetResponse
  ): Option[E]

  protected def serializeSearchResult(
    response: SearchResponse
  ): Traversable[E]

  // by default just iterate through and serialize each result independently
  protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[SearchHit]
  ): Traversable[E] =
    results.flatMap { searchHit =>
      if (searchHit.exists) {
        Some(serializeProjectionSearchHit(projection, searchHit))
      } else {
        logger.warn(s"Received an empty search hit '${searchHit.index}'. Total search hits: ${results.size}.")
        None
      }
    }

  protected def serializeProjectionSearchHit(
    projection: Seq[String],
    result: SearchHit
  ): E =
    if (result.exists) {
      val fieldValues = if (result.fields != null) result.fields else Nil
      serializeProjectionSearchResult(projection, fieldValues)
    } else
      throw new InCalDataAccessException(s"Got an empty result '$result' but at this point should contain some data.")

  protected def serializeProjectionSearchResult(
    projection: Seq[String],
    result: Traversable[(String, Any)]
  ): E

  protected def serializeSearchHit(
    result: SearchHit
  ): E
}