package org.incal.access_elastic.format

import com.sksamuel.elastic4s.ElasticClient
import org.incal.access_elastic.{ElasticAsyncReadonlyRepo, ElasticSetting}
import play.api.libs.json.Format

class ElasticFormatAsyncReadonlyRepo[E, ID](
  indexName: String,
  typeName: String,
  identityName : String,
  client: ElasticClient,
  setting: ElasticSetting)(
  implicit val format: Format[E], val manifest: Manifest[E]
) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName, identityName, client, setting) with ElasticFormatSerializer[E]