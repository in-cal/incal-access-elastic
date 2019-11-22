package org.incal.access.elastic

import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.update.UpdateRequest
import org.incal.core.Identity
import org.incal.core.dataaccess.AsyncCrudRepo

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
abstract class ElasticAsyncCrudRepo[E, ID](
  indexName: String,
  typeName: String,
  setting: ElasticSetting = ElasticSetting())(
  implicit identity: Identity[E, ID]
) extends ElasticAsyncRepo[E, ID](indexName, typeName, setting)
  with AsyncCrudRepo[E, ID]
  with ElasticCrudRepoExtra {

  override def update(entity: E): Future[ID] = {
    val (updateDef, id) = createUpdateDefWithId(entity)

    client execute (updateDef refresh asNative(setting.updateRefresh)) map (_ => id)

  }.recover(
    handleExceptions
  )

  override def update(entities: Traversable[E]): Future[Traversable[ID]] = {
    val updateDefAndIds = entities map createUpdateDefWithId

    if (updateDefAndIds.nonEmpty) {
      client execute {
        bulk {
          updateDefAndIds.toSeq map (_._1)
        } refresh asNative(setting.updateBulkRefresh)
      } map (_ =>
        updateDefAndIds map (_._2)
        )
    } else
      Future(Nil)

  }.recover(
    handleExceptions
  )

  protected def createUpdateDefWithId(entity: E): (UpdateRequest, ID) = {
    val id = identity.of(entity).getOrElse(
      throw new IllegalArgumentException(s"Elastic update method expects an entity with id but '$entity' provided.")
    )
    (createUpdateDef(entity, id), id)
  }

  protected def createUpdateDef(entity: E, id: ID): UpdateRequest

  override def delete(id: ID): Future[Unit] = {
    client execute {
      ElasticDsl.delete(stringId(id)) from index
    } map (_ => ())

  }.recover(
    handleExceptions
  )

  override def deleteIndex: Future[_] =
    client execute {
      ElasticDsl.deleteIndex(indexName)
    }

  override def deleteAll: Future[Unit] = {
    for {
      indexExists <- existsIndex
      _ <- if (indexExists) deleteIndex else Future(())
      _ <- createIndex
    } yield
      ()
  }.recover(
    handleExceptions
  )
}

trait ElasticCrudRepoExtra extends ElasticReadonlyRepoExtra {
  def deleteIndex: Future[_]
}