package org.incal.access_elastic

import com.typesafe.config.Config
import javax.inject.Inject

/**
  * IOC provider of an Elastic client using the injected application config.
  *
  * @since 2018
  * @author Peter Banda
  */
class InjectedConfigElasticClientProvider extends ElasticClientProvider {

  @Inject protected var config: Config = _
}
