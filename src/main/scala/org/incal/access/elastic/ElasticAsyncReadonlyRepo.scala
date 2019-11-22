package org.incal.access.elastic

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchRequest}
import com.sksamuel.elastic4s.streams.DocSortScrollPublisher
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.ExistsRequest
import com.sksamuel.elastic4s.requests.mappings.{FieldDefinition, MappingDefinition}
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, Index, Indexes}
import org.incal.core.dataaccess._
import org.reactivestreams.Publisher

import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Basic (abstract) ready-only repo for searching and counting of documents in Elastic Search.
  *
  * @param indexName
  * @param identityName
  * @param setting
  * @tparam E
  * @tparam ID
  *
  * @since 2018
  * @author Peter Banda
  */
abstract class ElasticAsyncReadonlyRepo[E, ID](
  indexName: String,
  identityName : String,
  setting: ElasticSetting
) extends AsyncReadonlyRepo[E, ID]
  with ElasticReadonlyRepoExtra
  with ElasticSerializer[E]
  with ElasticDsl {

  protected val index = Index(indexName)
  protected val unboundLimit = Integer.MAX_VALUE
  protected val scrollKeepAlive = "3m"

  protected val client: ElasticClient

  protected def stringId(id: ID) = id.toString

  def get(id: ID): Future[Option[E]] =
    client execute {
      ElasticDsl get stringId(id) from index
    } map { response =>
      serializeGetResult(response.result)
    }

  override def find(
    criteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[E]] = {
    val searchDefinition = createSearchDefinition(criteria, sort, projection, limit, skip)
    {
      client execute {
        searchDefinition
      } map { searchResult =>
        val projectionSeq = projection.map(toDBFieldName).toSeq

        val serializationStart = new Date()

        if (searchResult.shards.failed > 0) {
          // TODO: dig a reason for the failure
          throw new InCalDataAccessException(s"Search failed at ${searchResult.shards.failed} shards.")
        }

        val result: Traversable[E] = projection match {
          case Nil => serializeSearchResult(searchResult)
          case _ => serializeProjectionSearchHits(projectionSeq, searchResult.hits.hits)
        }
        logger.debug(s"Serialization for the projection '${projection.mkString(", ")}' finished in ${new Date().getTime - serializationStart.getTime} ms.")
        result
      }
    }.recover(handleExceptions)
  }

  implicit val system = ActorSystem()

  override def findAsStream(
    criteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int])(
    implicit materializer: Materializer
  ): Future[Source[E, _]] = {
    val scrollLimit = limit.getOrElse(setting.scrollBatchSize)

    val searchDefinition = createSearchDefinition(criteria, sort, projection, Some(scrollLimit), skip)
    val extraScrollDef = (searchDefinition scroll scrollKeepAlive)

    val publisher: Publisher[SearchHit] =
      if (setting.useDocScrollSort && sort.isEmpty)
        new DocSortScrollPublisher(client, extraScrollDef, Long.MaxValue)
    else
        client publisher { extraScrollDef }

    val source = Source.fromPublisher(publisher).map { searchHit =>
      val projectionSeq = projection.map(toDBFieldName).toSeq

      if (searchHit.exists) {
        val result = projection match {
          case Nil => serializeSearchHit(searchHit)
          case _ => serializeProjectionSearchHit(projectionSeq, searchHit)
        }
        Some(result)
      } else
        None
    }.collect { case Some(x) => x }

    Future(source)
  }

  private def createSearchDefinition(
    criteria: Seq[Criterion[Any]] = Nil,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None
  ): SearchRequest = {
    val projectionSeq = projection.map(toDBFieldName).toSeq

    val searchDefs: Seq[(Boolean, SearchRequest => SearchRequest)] =
      Seq(
        // criteria
        (
          criteria.nonEmpty,
          (_: SearchRequest) bool must (criteria.map(toQuery))
        ),

        // projection
        (
          projection.nonEmpty,
          (_: SearchRequest) storedFields projectionSeq
        ),

        // sort
        (
          sort.nonEmpty,
          (_: SearchRequest) sortBy toSort(sort)
        ),

        // start and skip
        (
          true,
          if (limit.isDefined)
            (_: SearchRequest) start skip.getOrElse(0) limit limit.get
          else
            // if undefined we still need to pass "unbound" limit, since by default ES returns only 10 items
            (_: SearchRequest) limit unboundLimit
        ),

        // fetch source (or not)
        (
          true,
          (_: SearchRequest) fetchSource(projection.isEmpty)
        )
      )

    searchDefs.foldLeft(search(index)) {
      case (sd, (cond, createNewDef)) =>
        if (cond) createNewDef(sd) else sd
    }
  }

  private def toSort(sorts: Seq[Sort]): Seq[com.sksamuel.elastic4s.requests.searches.sort.Sort] =
    sorts map {
      case AscSort(fieldName) => FieldSort(toDBFieldName(fieldName)) order SortOrder.ASC
      case DescSort(fieldName) => FieldSort(toDBFieldName(fieldName)) order SortOrder.DESC
    }

  protected def toQuery[T, V](criterion: Criterion[T]): Query = {
    val fieldName = toDBFieldName(criterion.fieldName)

    val qDef = criterion match {
      case c: EqualsCriterion[T] =>
        TermQueryDefinition(fieldName, toDBValue(c.value))

      case c: EqualsNullCriterion =>
        new BoolQueryDefinition().not(ExistsQueryDefinition(fieldName))

      case c: RegexEqualsCriterion =>
        RegexQueryDefinition(fieldName, toDBValue(c.value).toString)

      case c: RegexNotEqualsCriterion =>
        new BoolQueryDefinition().not(RegexQueryDefinition(fieldName, toDBValue(c.value).toString))

      case c: NotEqualsCriterion[T] =>
        new BoolQueryDefinition().not(TermQueryDefinition(fieldName, toDBValue(c.value)))

      case c: NotEqualsNullCriterion =>
        ExistsQueryDefinition(fieldName)

      case c: InCriterion[V] =>
        TermsQueryDefinition(fieldName, c.value.map(value => toDBValue(value).toString))

      case c: NotInCriterion[V] =>
        new BoolQueryDefinition().not(TermsQueryDefinition(fieldName, c.value.map(value => toDBValue(value).toString)))

      case c: GreaterCriterion[T] =>
        RangeQueryDefinition(fieldName) gt toDBValue(c.value).toString

      case c: GreaterEqualCriterion[T] =>
        RangeQueryDefinition(fieldName) gte toDBValue(c.value).toString

      case c: LessCriterion[T] =>
        RangeQueryDefinition(fieldName) lt toDBValue(c.value).toString

      case c: LessEqualCriterion[T] =>
        RangeQueryDefinition(fieldName) lte toDBValue(c.value).toString
    }

    if (fieldName.contains(".")) {
      val path = fieldName.takeWhile(!_.equals('.'))
      NestedQueryDefinition(path, qDef)
    } else
      qDef
  }

  protected def toDBValue(value: Any): Any =
    value match {
      case e: Date => e.getTime
      case _ => value
    }

  protected def toDBFieldName(fieldName: String): String = fieldName

  override def count(criteria: Seq[Criterion[Any]]): Future[Int] = {
    val countDef = createSearchDefinition(criteria) size 0

    client.execute(countDef)
      .map(_.totalHits.toInt)
      .recover(handleExceptions)
  }

  override def exists(id: ID): Future[Boolean] =
    count(Seq(EqualsCriterion(identityName, id))).map(_ > 0)

  protected def createIndex: Future[_] =
    client execute {
      ElasticDsl.createIndex(indexName)
        .shards(setting.shards)
        .replicas(setting.replicas)
        .mapping(MappingDefinition(fields = fieldDefs))
        .indexSetting("max_result_window", unboundLimit)
        .indexSetting("mapping.total_fields.limit", setting.indexFieldsLimit)
        .indexSetting("mapping.single_type", setting.indexSingleTypeMapping)
    }

  // TODO: serialization of index names is buggy for the reindex function, therefore we pass there apostrophes
  override def reindex(newIndexName: String): Future[_] =
    client execute {
      ElasticDsl reindex (
        Indexes(Seq("\"" + indexName + "\"")),
        Index("\"" + newIndexName +"\"").refresh(true).waitForActiveShards(setting.shards)
      )
    }

  override def getMappings: Future[Map[String, Map[String, Any]]] =
    for {
      mappings <- client execute {
        ElasticDsl.getMapping(indexName)
      }
    } yield
      mappings.headOption.map(_.mappings).getOrElse(Map())

  // override if needed to customize field definitions
  protected def fieldDefs: Seq[FieldDefinition] = Nil

  protected def existsIndex: Future[Boolean] =
    client execute {
      createIndex
      ElasticDsl.indexExists(indexName)
    } map { res =>
      res.result.exists
    }

  protected def createIndexIfNeeded: Unit =
    result(
      {
        for {
          exists <- existsIndex
          _ <- if (!exists) createIndex else Future(())
        } yield
          ()
      },
      30 seconds
    )

  protected def handleExceptions[A]: PartialFunction[Throwable, A] = {
    case e: ElasticsearchTimeoutException =>
      val message = "Elastic Search operation timed out."
      logger.error(message, e)
      throw new InCalDataAccessException(message, e)

    case e: ElasticsearchException =>
      val message = "Problem with Elastic Search detected."
      logger.error(message, e)
      throw new InCalDataAccessException(message, e)
  }
}

trait ElasticReadonlyRepoExtra {

  def getMappings: Future[Map[String, Map[String, Any]]]

  def reindex(newIndexName: String): Future[_]
}