package org.incal.access.elastic

import com.typesafe.config.Config
import javax.inject.Inject

/**
  * Basic IOC provider of an Elastic client using the injected application config.
  *
  * @since 2018
  * @author Peter Banda
  */
class BasicElasticClientProvider extends ElasticClientProvider {

  @Inject protected var config: Config = _
}
