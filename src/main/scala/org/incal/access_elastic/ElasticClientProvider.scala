package org.incal.access_elastic

import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import javax.inject.Provider
import org.elasticsearch.common.settings.Settings
import scala.collection.JavaConversions._

/**
  * IOC provider of an Elastic client using the application config, which must be provided (overridden).
  *
  * @since 2018
  * @author Peter Banda
  */
trait ElasticClientProvider extends Provider[ElasticClient] {

  protected def config: Config

  protected def shutdownHook(client: ElasticClient): Unit =
    scala.sys.addShutdownHook(client.close())

  override def get(): ElasticClient = {
    val elasticConfig = config.getConfig("elastic")

    val uri = if (elasticConfig.hasPath("uri")) {
      elasticConfig.getString("uri")
    } else {
      val host = elasticConfig.getString("host")
      val port = elasticConfig.getInt("port")
      s"$host:$port"
    }

    val settings = Settings.settingsBuilder

    elasticConfig.entrySet().foreach { entry =>
      settings.put(entry.getKey, entry.getValue.unwrapped.toString)
    }

    val client = ElasticClient.transport(
      settings.build,
      ElasticsearchClientUri(s"elasticsearch://$uri")
    )

    // add a shutdown hook to the client
    shutdownHook(client)

    client
  }
}