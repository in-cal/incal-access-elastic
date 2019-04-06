package org.incal.access_elastic

import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.{ElasticsearchException, ElasticsearchTimeoutException}
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s._
import org.incal.core.dataaccess._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Await.result
import java.util.Date

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.mappings.TypedFieldDefinition
import org.slf4j.LoggerFactory

/**
  * Basic (abstract) ready-only repo for searching and counting of documents in Elastic Search.
  *
  * @param indexName
  * @param typeName
  * @param client
  * @param setting
  * @param identity
  * @tparam E
  * @tparam ID
  *
  * @since 2018
  * @author Peter Banda
  */
abstract class ElasticAsyncReadonlyRepo[E, ID](
  indexName: String,
  typeName: String,
  identityName : String,
  setting: ElasticSetting
) extends AsyncReadonlyRepo[E, ID]
  with ElasticSerializer[E]
  with ElasticDsl {

  protected val indexAndType = IndexAndType(indexName, typeName)
  protected val unboundLimit = Integer.MAX_VALUE
  protected val scrollKeepAlive = "3m"
  protected val logger = LoggerFactory.getLogger(this.getClass)

  protected val client: ElasticClient

  def get(id: ID): Future[Option[E]] =
    client execute {
      ElasticDsl.get id id from indexAndType
    } map (serializeGetResult)

  override def find(
    criteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[E]] = {
    val searchDefinition = createSearchDefinition(criteria, sort, projection, limit, skip)

    {
      client execute (
        searchDefinition
        ) map { searchResult =>
        val projectionSeq = projection.map(toDBFieldName).toSeq

        val serializationStart = new Date()

        if (searchResult.shardFailures.nonEmpty) {
          throw new InCalDataAccessException(searchResult.shardFailures(0).reason())
        }

        val result: Traversable[E] = projection match {
          case Nil => serializeSearchResult(searchResult)
          case _ => serializeProjectionSearchHits(projectionSeq, searchResult.hits)
        }
        println(s"Serialization for the projection '${projection.mkString(", ")}' finished in ${new Date().getTime - serializationStart.getTime} ms.")
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
    skip: Option[Int]
  ): Future[Source[E, _]] = {
    val scrollLimit = limit.getOrElse(setting.scrollBatchSize)
    val searchDefinition = createSearchDefinition(criteria, sort, projection, Some(scrollLimit), skip)

    val publisher = client publisher {
      searchDefinition scroll scrollKeepAlive
    }
    val source = Source.fromPublisher(publisher).map { richSearchHit =>
      val projectionSeq = projection.map(toDBFieldName).toSeq

      projection match {
        case Nil => serializeSearchHit(richSearchHit)
        case _ => serializeProjectionSearchHit(projectionSeq, richSearchHit)
      }
    }
    Future(source)
  }

  private def createSearchDefinition(
    criteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): SearchDefinition = {
    val projectionSeq = projection.map(toDBFieldName).toSeq

    val searchDefs: Seq[(Boolean, SearchDefinition => SearchDefinition)] =
      Seq(
        // criteria
        (
          criteria.nonEmpty,
          (_: SearchDefinition) bool must (criteria.map(toQuery)) fetchSource(projection.isEmpty)
        ),

        // projection
        (
          projection.nonEmpty,
          (_: SearchDefinition) fields (projectionSeq: _*)
        ),

        // sort
        (
          sort.nonEmpty,
          (_: SearchDefinition) sort (toSort(sort): _*)
        ),

        // start and skip
        (
          true,
          if (limit.isDefined)
            (_: SearchDefinition) start skip.getOrElse(0) limit limit.get
          else
          // if undefined we still need to pass "unbound" limit, since by default ES returns only 10 items
            (_: SearchDefinition) limit unboundLimit
        )
      )

    searchDefs.foldLeft(search in indexAndType) {
      case (sd, (cond, createNewDef)) =>
        if (cond) createNewDef(sd) else sd
    }
  }

  private def toSort(sorts: Seq[Sort]): Seq[SortDefinition] =
    sorts map {
      _ match {
        case AscSort(fieldName) => FieldSortDefinition(toDBFieldName(fieldName)) order SortOrder.ASC
        case DescSort(fieldName) => FieldSortDefinition(toDBFieldName(fieldName)) order SortOrder.DESC
      }
    }

  protected def toQuery[T, V](criterion: Criterion[T]): QueryDefinition = {
    val fieldName = toDBFieldName(criterion.fieldName)

    val qDef = criterion match {
      case c: EqualsCriterion[T] =>
        TermQueryDefinition(fieldName, toDBValue(c.value))

      case c: EqualsNullCriterion =>
        new BoolQueryDefinition().not(ExistsQueryDefinition(fieldName))

      case c: RegexEqualsCriterion =>
        RegexQueryDefinition(fieldName, c.value)

      case c: RegexNotEqualsCriterion =>
        new BoolQueryDefinition().not(RegexQueryDefinition(fieldName, c.value))

      case c: NotEqualsCriterion[T] =>
        new BoolQueryDefinition().not(TermQueryDefinition(fieldName, toDBValue(c.value)))

      case c: NotEqualsNullCriterion =>
        ExistsQueryDefinition(fieldName)

      case c: InCriterion[V] =>
        TermsQueryDefinition(fieldName, c.value.map(value => toDBValue(value).toString))

      case c: NotInCriterion[V] =>
        new BoolQueryDefinition().not(TermsQueryDefinition(fieldName, c.value.map(_.toString)))

      case c: GreaterCriterion[T] =>
        RangeQueryDefinition(fieldName) from toDBValue(c.value) includeLower false

      case c: GreaterEqualCriterion[T] =>
        RangeQueryDefinition(fieldName) from toDBValue(c.value) includeLower true

      case c: LessCriterion[T] =>
        RangeQueryDefinition(fieldName) to toDBValue(c.value) includeUpper false

      case c: LessEqualCriterion[T] =>
        RangeQueryDefinition(fieldName) to toDBValue(c.value) includeUpper true
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
    val countDef =
      ElasticDsl.count from indexAndType query new BoolQueryDefinition().must(criteria.map(toQuery))

    client.execute(countDef)
      .map(_.getCount.toInt)
      .recover(handleExceptions)
  }

  override def exists(id: ID): Future[Boolean] =
    count(Seq(EqualsCriterion(identityName, id))).map(_ > 0)

  protected def createIndex(): Future[_] =
    client execute {
      create index indexName replicas 0 mappings (
        indexName as fieldDefs
      ) indexSetting("max_result_window", unboundLimit) // indexSetting("_all", false)
    }

  // override if needed to customize field definitions
  protected def fieldDefs: Iterable[TypedFieldDefinition] = Nil

  protected def existsIndex(): Future[Boolean] =
    client execute {
      admin IndexExistsDefinition (Seq(indexName))
    } map (_.isExists)

  protected def createIndexIfNeeded(): Unit =
    result(
      {
        for {
          exists <- existsIndex()
          _ <- if (!exists) createIndex() else Future(())
        } yield
          ()
      },
      20 seconds
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