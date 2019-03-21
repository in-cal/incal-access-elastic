package org.incal.access_elastic.format

import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import org.incal.access_elastic.{ElasticAsyncCrudRepo, ElasticSetting}
import org.incal.core.Identity
import play.api.libs.json.Format

class ElasticFormatAsyncCrudRepo[E, ID](
  indexName: String,
  typeName: String,
  client: ElasticClient,
  setting: ElasticSetting)(
  implicit val format: Format[E], val manifest: Manifest[E], identity: Identity[E, ID]
) extends ElasticAsyncCrudRepo[E, ID](indexName, typeName, client, setting) with ElasticFormatSerializer[E] {

  private implicit val indexable = toIndexable[E]

  override protected def createSaveDef(entity: E, id: ID) =
    index into indexAndType source entity id id

  override def createUpdateDef(entity: E, id: ID) =
    ElasticDsl.update id id in indexAndType source entity
}