package org.incal.access.elastic

import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http._
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.exts.StringOption
import com.typesafe.config.Config
import javax.inject.Provider
import org.apache.http.client.config.RequestConfig
import org.elasticsearch.client.RestClientBuilder.RequestConfigCallback
import org.incal.core.dataaccess.InCalDataAccessException

import scala.collection.JavaConversions._

/**
  * IOC provider of an Elastic client using the application config, which must be provided (overridden).
  *
  * @since 2018
  * @author Peter Banda
  */
trait ElasticClientProvider extends Provider[HttpClient] {

  protected def config: Config

  protected def shutdownHook(client: HttpClient): Unit =
    scala.sys.addShutdownHook(client.close())

  override def get(): HttpClient = {
    val elasticConfig = config.getConfig("elastic")

    val (host, port, options) = if (elasticConfig.hasPath("uri")) {
      val uri = elasticConfig.getString("uri")
      val uriParts = uri.split("\\?", -1)
      val hostPort = uriParts.head.split(":", -1)

      val opts = if (uriParts.size > 1) {
        StringOption(uriParts(1))
          .map(_.split('&')).getOrElse(Array.empty)
          .map(_.split('=')).collect {
          case Array(key, value) => (key, value)
          case _ => sys.error(s"Invalid query ${uriParts(1)}")
        }.toMap
      } else Map()

      if (hostPort.size == 2) {
        (hostPort(0), hostPort(1).toInt, opts)
      } else
        throw new InCalDataAccessException(s"Elastic Search URI $uri cannot be parsed to host:port.")
    } else {
      val host = elasticConfig.getString("host")
      val port = elasticConfig.getInt("port")
      (host, port,  Map())
    }

    val finalOptions = options ++ elasticConfig.entrySet.map { entry =>
      entry.getKey -> entry.getValue.unwrapped.toString
    }.filter(_._1 != "uri").toMap

    val connectionRequestTimeout = finalOptions.get("connection_request.timeout").map(_.toString.toInt).getOrElse(600000)
    val connectionTimeout = finalOptions.get("connection.timeout").map(_.toString.toInt).getOrElse(600000)
    val socketTimeout = finalOptions.get("socket.timeout").map(_.toString.toInt).getOrElse(600000)

    val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://$host:$port", List((host, port)), finalOptions),
      new RequestConfigCallback {
        override def customizeRequestConfig(requestConfigBuilder: RequestConfig.Builder) =
          requestConfigBuilder
            .setConnectionRequestTimeout(connectionRequestTimeout)
            .setConnectTimeout(connectionTimeout)
            .setSocketTimeout(socketTimeout)
      },
      NoOpHttpClientConfigCallback
    )

    // add a shutdown hook to the client
    shutdownHook(client)

    client
  }
}
