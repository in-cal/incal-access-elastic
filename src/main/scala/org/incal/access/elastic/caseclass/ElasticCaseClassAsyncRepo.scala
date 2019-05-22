package org.incal.access.elastic.caseclass

import com.sksamuel.elastic4s.IndexDefinition
import org.incal.access.elastic.ElasticAsyncRepo
import org.incal.access.elastic.ElasticSetting
import org.incal.core.Identity

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

abstract class ElasticCaseClassAsyncRepo[E, ID](
  indexName: String,
  typeName: String,
  setting: ElasticSetting)(
  implicit val typeTag: TypeTag[E], val classTag: ClassTag[E], identity: Identity[E, ID]
) extends ElasticAsyncRepo[E, ID](indexName, typeName, setting) with ElasticCaseClassSerializer[E] {

  override protected def createSaveDef(entity: E, id: ID): IndexDefinition =
    index into indexAndType source entity id id
}