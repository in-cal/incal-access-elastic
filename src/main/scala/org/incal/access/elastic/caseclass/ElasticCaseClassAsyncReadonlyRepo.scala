package org.incal.access.elastic.caseclass

import org.incal.access.elastic.{ElasticAsyncReadonlyRepo, ElasticSetting}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

abstract class ElasticCaseClassAsyncReadonlyRepo[E, ID](
  indexName: String,
  identityName : String,
  setting: ElasticSetting)(
  implicit val typeTag: TypeTag[E], val classTag: ClassTag[E]
) extends ElasticAsyncReadonlyRepo[E, ID](indexName, identityName, setting) with ElasticCaseClassSerializer[E]