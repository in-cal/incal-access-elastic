package org.incal.access_elastic.format

import org.incal.access_elastic.{ElasticAsyncReadonlyRepo, ElasticSetting}
import play.api.libs.json.Format

abstract class ElasticFormatAsyncReadonlyRepo[E, ID](
  indexName: String,
  typeName: String,
  identityName : String,
  setting: ElasticSetting)(
  implicit val format: Format[E], val manifest: Manifest[E]
) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName, identityName, setting) with ElasticFormatSerializer[E]