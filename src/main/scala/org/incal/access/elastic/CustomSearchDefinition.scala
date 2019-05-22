package org.incal.access.elastic

import java.io.IOException

import com.sksamuel.elastic4s.{IndexesAndTypes, ProxyClients, SearchDefinition}
import org.elasticsearch.action.search.{SearchAction, SearchRequestBuilder}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.search.builder.SearchSourceBuilder

/**
  * Search definition with a custom extension, used for adhoc parameter addition.
  * Meant only for temporary use, and should/will be dereleased.
  *
  * @since 2019
  * @author Peter Banda
  */
class CustomSearchDefinition(
  override val indexesTypes: IndexesAndTypes
) extends SearchDefinition(indexesTypes) {

  private var extraParamsFun: Option[XContentBuilder => Unit] = None

  def setExtraParams(extra: XContentBuilder => Unit) = {
    this.extraParamsFun = Some(extra)
    this
  }

  private class CustomSearchSourceBuilder extends SearchSourceBuilder {

    @throws[IOException]
    override def innerToXContent(
      builder: XContentBuilder,
      params: ToXContent.Params
    ) = {
      super.innerToXContent(builder, params)
      extraParamsFun.foreach(_ (builder))
    }
  }

  override val _builder = {
    new SearchRequestBuilder(ProxyClients.client, SearchAction.INSTANCE)
      .setIndices(indexesTypes.indexes: _*)
      .setTypes(indexesTypes.types: _*)
      .internalBuilder(new CustomSearchSourceBuilder())
  }
}