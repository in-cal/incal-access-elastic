package org.incal.access_elastic.format

import com.sksamuel.elastic4s.{ElasticClient, IndexDefinition}
import org.incal.access_elastic.{ElasticAsyncRepo, ElasticSetting}
import org.incal.core.Identity
import play.api.libs.json.Format

class ElasticFormatAsyncRepo[E, ID](
  indexName: String,
  typeName: String,
  client: ElasticClient,
  setting: ElasticSetting)(
  implicit val format: Format[E], val manifest: Manifest[E], identity: Identity[E, ID]
) extends ElasticAsyncRepo[E, ID](indexName, typeName, client, setting) with ElasticFormatSerializer[E] {

  private implicit val indexable = toIndexable[E]

  override protected def createSaveDef(entity: E, id: ID): IndexDefinition =
    index into indexAndType source entity id id
}