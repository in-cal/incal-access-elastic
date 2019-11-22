package org.incal.access.elastic

import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import org.incal.core.Identity
import org.incal.core.dataaccess._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Abstract CRUD (create, ready, update, delete) repo for handling storage and retrieval of documents in Elastic Search.
  *
  * @param indexName
  * @param typeName
  * @param setting
  * @param identity
  * @tparam E
  * @tparam ID
  *
  * @since 2018
  * @author Peter Banda
  */
abstract class ElasticAsyncRepo[E, ID](
    indexName: String,
    typeName: String,
    setting: ElasticSetting)(
    implicit identity: Identity[E, ID]
  ) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName, identity.name, setting) with AsyncRepo[E, ID] {

  override def save(entity: E): Future[ID] = {
    val (saveDef, id) = createSaveDefWithId(entity)

    client execute (saveDef refresh asNative(setting.saveRefresh)) map (_ => id)
  }.recover(
    handleExceptions
  )

  protected def asNative(refreshPolicy: RefreshPolicy.Value) =
    com.sksamuel.elastic4s.requests.common.RefreshPolicy.valueOf(refreshPolicy.toString)

  override def save(entities: Traversable[E]): Future[Traversable[ID]] = {
    val saveDefAndIds = entities map createSaveDefWithId

    if (saveDefAndIds.nonEmpty) {
      client execute {
        bulk {
          saveDefAndIds.toSeq map (_._1)
        } refresh asNative(setting.saveBulkRefresh)
      } map (_ =>
        saveDefAndIds map (_._2)
      )
    } else
      Future(Nil)
  }.recover(
    handleExceptions
  )

  protected def createSaveDefWithId(entity: E): (IndexRequest, ID) = {
    val (id, entityWithId) = getIdWithEntity(entity)
    (createSaveDef(entityWithId, id), id)
  }

  protected def createSaveDef(entity: E, id: ID): IndexRequest

  protected def getIdWithEntity(entity: E): (ID, E) =
    identity.of(entity) match {
      case Some(givenId) => (givenId, entity)
      case None =>
        val id = identity.next
        (id, identity.set(entity, id))
    }

  override def flushOps = {
    client execute flushIndex(indexName) map (_ => ())
  }.recover(
    handleExceptions
  )
}