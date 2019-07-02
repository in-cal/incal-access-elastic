package com.sksamuel.elastic4s.streams

import akka.actor.{Actor, ActorRefFactory, PoisonPill, Props, Stash}
import com.sksamuel.elastic4s.http.{HttpClient, HttpExecutable, IndicesOptionsParams, ResponseHandler}
import com.sksamuel.elastic4s.http.search.{SearchBodyBuilderFn, SearchHit, SearchResponse, SearchTypeHttpParameters}
import com.sksamuel.elastic4s.searches.SearchDefinition
import org.apache.http.entity.StringEntity
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.RestClient
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.apache.http.entity.ContentType

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Introduced only to pass a more efficient "_doc" sort for scrolling
  * Remove this class once it's supported natively by Elastic4S library
  *
  * @since 2019
  * @see: com.sksamuel.elastic4s.streams.ScrollPublisher
  */
@Deprecated
class DocSortScrollPublisher(client: HttpClient,
  search: SearchDefinition,
  elements: Long)
  (implicit actorRefFactory: ActorRefFactory) extends Publisher[SearchHit] {
  require(search.keepAlive.isDefined, "Search Definition must have a scroll to be used as Publisher")

  override def subscribe(s: Subscriber[_ >: SearchHit]): Unit = {
    // Rule 1.9 subscriber cannot be null
    if (s == null) throw new NullPointerException("Rule 1.9: Subscriber cannot be null")
    val subscription = new DocSortScrollSubscription(client, search, s, elements)
    s.onSubscribe(subscription)
    // rule 1.03 the subscription should not invoke any onNext's until the onSubscribe call has returned
    // even tho the user might call request in the onSubscribe, we can't start sending the results yet.
    // this ready method signals to the actor that its ok to start sending data.
    subscription.ready()
  }
}

class DocSortScrollSubscription(client: HttpClient, query: SearchDefinition, s: Subscriber[_ >: SearchHit], max: Long)
  (implicit actorRefFactory: ActorRefFactory) extends Subscription {

  private val actor = actorRefFactory.actorOf(Props(new DocSortPublishActor(client, query, s, max)))

  private[streams] def ready(): Unit = {
    actor ! DocSortPublishActor.Ready
  }

  override def cancel(): Unit = {
    // Rule 3.5: this call is idempotent, is fast, and thread safe
    // Rule 3.7: after cancelling, further calls should be no-ops, which is handled by the actor
    // we don't mind the subscriber having any pending requests before cancellation is processed
    actor ! PoisonPill
  }

  override def request(n: Long): Unit = {
    // Rule 3.9
    if (n < 1) s.onError(new java.lang.IllegalArgumentException("Rule 3.9: Must request > 0 elements"))
    // Rule 3.4 this method returns quickly as the search request is non-blocking
    actor ! DocSortPublishActor.Request(n)
  }
}

object DocSortPublishActor {
  object Ready
  case class Request(n: Long)
}

object CustomSearchHttpExecutable extends HttpExecutable[SearchDefinition, SearchResponse] {

    override def execute(client: RestClient,
      request: SearchDefinition): Future[SearchResponse] = {

      val endpoint = if (request.indexesTypes.indexes.isEmpty && request.indexesTypes.types.isEmpty)
        "/_search"
      else if (request.indexesTypes.indexes.isEmpty)
        "/_all/" + request.indexesTypes.types.mkString(",") + "/_search"
      else if (request.indexesTypes.types.isEmpty)
        "/" + request.indexesTypes.indexes.mkString(",") + "/_search"
      else
        "/" + request.indexesTypes.indexes.mkString(",") + "/" + request.indexesTypes.types.mkString(",") + "/_search"

      val params = scala.collection.mutable.Map.empty[String, String]
      request.keepAlive.foreach(params.put("scroll", _))
      request.pref.foreach(params.put("preference", _))
      request.requestCache.map(_.toString).foreach(params.put("request_cache", _))
      request.routing.foreach(params.put("routing", _))
      request.searchType.filter(_ != SearchType.DEFAULT).map(SearchTypeHttpParameters.convert).foreach(params.put("search_type", _))
      request.terminateAfter.map(_.toString).foreach(params.put("terminate_after", _))
      request.timeout.map(_.toMillis + "ms").foreach(params.put("timeout", _))
      request.version.map(_.toString).foreach(params.put("version", _))
      request.indicesOptions.foreach { opts =>
        IndicesOptionsParams(opts).foreach { case (key, value) => params.put(key, value) }
      }

      // hacky solution to add "_doc"-sort to the request
      val builder = SearchBodyBuilderFn(request)
      val body = builder.string()

      val extraBody = body.substring(0, body.length - 1) + ", \"sort\": [\"_doc\"] }"

      logger.debug("Executing search request: " + extraBody)

      val entity = new StringEntity(extraBody, ContentType.APPLICATION_JSON)
      client.async("POST", endpoint, params.toMap, entity, ResponseHandler.default)
    }
}

class DocSortPublishActor(client: HttpClient,
  query: SearchDefinition,
  s: Subscriber[_ >: SearchHit],
  max: Long) extends Actor with Stash {

  import com.sksamuel.elastic4s.http.ElasticDsl.{searchScroll, clearScroll, SearchScrollHttpExecutable, ClearScrollHttpExec}
  import context.dispatcher
  implicit val aa = CustomSearchHttpExecutable

  private var scrollId: String = _
  private var processed: Long = 0
  private val queue: mutable.Queue[SearchHit] = mutable.Queue.empty

  // Parse the keep alive setting out of the original query.
  private val keepAlive = query.keepAlive.map(_.toString).getOrElse("1m")

  // rule 1.03 the subscription should not send any results until the onSubscribe call has returned
  // even tho the user might call request in the onSubscribe, we can't start sending the results yet.
  // this ready method signals to the actor that its ok to start sending data. In the meantime we just stash requests.
  override def receive: PartialFunction[Any, Unit] = {
    case DocSortPublishActor.Ready =>
      context become ready
      unstashAll()
    case _ =>
      stash()
  }

  private def send(k: Long): Unit = {
    require(queue.size >= k)
    for (_ <- 0l until k) {
      if (max == 0 || processed < max) {
        s.onNext(queue.dequeue)
        processed = processed + 1
        if (processed == max && max > 0) {
          s.onComplete()
          context.stop(self)
        }
      }
    }
  }

  private def ready: Actor.Receive = {
    // if a request comes in for more than is currently available,
    // we will send a request for more while sending what we can now
    case DocSortPublishActor.Request(n) if n > queue.size =>
      Option(scrollId) match {
        case None => client.execute(query).onComplete(result => self ! result)
        case Some(id) => client.execute(searchScroll(id) keepAlive keepAlive).onComplete(result => self ! result)
      }
      // we switch state while we're waiting on elasticsearch, so we know not to send another request to ES
      // because we are using a scroll and can only have one active request at at time.
      context become fetching
      // queue up a new request to handle the remaining ones required when the ES response comes in
      self ! DocSortPublishActor.Request(n - queue.size)
      send(queue.size)
    // in this case, we have enough available so just send 'em
    case DocSortPublishActor.Request(n) =>
      send(n)
  }

  // fetching state is when we're waiting for a reply from es for a request we sent
  private def fetching: Actor.Receive = {
    // if we're in fetching mode, its because we ran out of results to send
    // so any requests must be stashed until a fresh batch arrives
    case DocSortPublishActor.Request(n) =>
      require(queue.isEmpty) // must be empty or why did we not send it before switching to this mode?
      stash()
    // handle when the es request dies
    case Success(resp: SearchResponse) if resp.isTimedOut =>
      s.onError(new ElasticsearchException("Request terminated early or timed out"))
      context.stop(self)
    // if the request to elastic failed we will terminate the subscription
    case Failure(t) =>
      s.onError(t)
      context.stop(self)
    // if we had no results from ES then we have nothing left to publish and our work here is done
    case Success(resp: SearchResponse) if resp.isEmpty =>
      s.onComplete()
      client.execute(clearScroll(scrollId))
      context.stop(self)
    // more results and we can unleash the beast (stashed requests) and switch back to ready mode
    case Success(resp: SearchResponse) =>
      scrollId = resp.scrollId.getOrElse("Query didn't return a scroll id")
      queue.enqueue(resp.hits.hits: _*)
      context become ready
      unstashAll()
  }
}
