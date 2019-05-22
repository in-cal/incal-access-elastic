package org.incal.access.elastic.caseclass

import java.util.{Date, UUID}

import org.incal.core.util.{DynamicConstructor, DynamicConstructorFinder}

import scala.collection.mutable.{Map => MMap}
import scala.reflect.runtime.{universe => ru}
import scala.reflect.runtime.universe.TypeTag

trait HasDynamicConstructor[E] {

  protected implicit val typeTag: TypeTag[E]

  protected val concreteClassFieldName = "concreteClass"

  protected val typeValueConverters: Seq[(ru.Type, Any => Any)] =
    Seq(
      (
        ru.typeOf[Date],
        (value: Any) =>
          value match {
            case ms: Long => new Date(ms)
            case ms: Int => new Date(ms)
            case _ => value
          }
      ),
      (
        ru.typeOf[UUID],
        (value: Any) =>
          value match {
            case uuid: String => UUID.fromString(uuid)
            case _ => value
          }
      )
    )

  protected val defaultConstructorFinder = DynamicConstructorFinder.apply[E]
  protected val classNameConstructorFinderMap = MMap[String, DynamicConstructorFinder[E]]()

  protected def constructorOrException(
    fieldNames: Seq[String],
    concreteClassName: Option[String] = None
  ): DynamicConstructor[E] = {
    val unrenamedFieldNames = fieldNames.map(unrename)

    val constructorFinder =
    // if concrete class name is defined check the map (cache) or create a new constructor using the class name
      concreteClassName.map { className =>
        classNameConstructorFinderMap.getOrElseUpdate(
          className,
          DynamicConstructorFinder.apply(className)
        )
        // otherwise use the default one, which opts to call constructor of the core class associated with a type E
      }.getOrElse(
        defaultConstructorFinder
      )

    constructorFinder(unrenamedFieldNames, typeValueConverters).getOrElse {
      throw new IllegalArgumentException(s"No constructor of the class '${constructorFinder.classSymbol.fullName}' matches the query result fields '${fieldNames.mkString(", ")}'. Adjust your query or introduce an appropriate constructor.")
    }
  }

  protected def constructorOrException(
    sourceMap: Map[String, Any]
  ): DynamicConstructor[E] = {
    val fieldNames = sourceMap.map(_._1).toSeq
    val concreteClass = sourceMap.get(concreteClassFieldName).map(_.asInstanceOf[String])
    constructorOrException(fieldNames, concreteClass)
  }

  protected def unrename(fieldName: String): String = fieldName
}
