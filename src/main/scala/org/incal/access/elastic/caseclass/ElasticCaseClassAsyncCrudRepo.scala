package org.incal.access.elastic.caseclass

import com.sksamuel.elastic4s.ElasticDsl
import org.incal.access.elastic.{ElasticAsyncCrudRepo, ElasticSetting}
import org.incal.core.Identity

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

abstract class ElasticCaseClassAsyncCrudRepo[E, ID](
  indexName: String,
  typeName: String,
  setting: ElasticSetting)(
  implicit val typeTag: TypeTag[E], val classTag: ClassTag[E], identity: Identity[E, ID]
) extends ElasticAsyncCrudRepo[E, ID](indexName, typeName, setting) with ElasticCaseClassSerializer[E] {

  override protected def createSaveDef(entity: E, id: ID) =
    indexInto(index).source(entity)

  override def createUpdateDef(entity: E, id: ID) =
    ElasticDsl.update(stringId(id)) in index source entity
}