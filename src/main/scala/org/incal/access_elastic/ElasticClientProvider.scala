package org.incal.access_elastic

import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import javax.inject.{Inject, Provider}
import org.elasticsearch.common.settings.Settings
import scala.collection.JavaConversions._

/**
  * IOC provider of an Elastic client using the injected application conf.
  *
  * @since 2018
  * @author Peter Banda
  */
class ElasticClientProvider extends Provider[ElasticClient] {

  @Inject private var config: Config = _

  protected def shutdownHook(client: ElasticClient): Unit =
    scala.sys.addShutdownHook(client.close())

  override def get(): ElasticClient = {
    val elasticConfig = config.getConfig("elastic")

    val host = elasticConfig.getString("host")
    val port = elasticConfig.getInt("port")

    val settings = Settings.settingsBuilder

    elasticConfig.entrySet().foreach { entry =>
      settings.put(entry.getKey, entry.getValue.unwrapped.toString)
    }

    val client = ElasticClient.transport(
      settings.build,
      ElasticsearchClientUri(s"elasticsearch://$host:$port")
    )

    // add a shutdown hook to the client
    shutdownHook(client)

    client
  }
}