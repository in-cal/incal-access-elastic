package org.incal.access.elastic.caseclass

import java.util.{Date, UUID}

import org.incal.core.dataaccess.InCalDataAccessException
import org.incal.core.util.{DynamicConstructor, DynamicConstructorFinder}

import scala.collection.mutable.{Map => MMap}
import scala.reflect.runtime.{universe => ru}
import scala.reflect.runtime.universe.TypeTag

trait HasDynamicConstructor[E] {

  protected implicit val typeTag: TypeTag[E]

  protected val concreteClassFieldName = "concreteClass"

  // default type value converters
  protected val typeValueConverters: Seq[(ru.Type, Any => Any)] =
    Seq(TypeValueConverters.date, TypeValueConverters.uuid)

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

object TypeValueConverters {

  val date: (ru.Type, Any => Any) = (
    ru.typeOf[Date], (_: Any) match {
      case ms: Long => new Date(ms)
      case ms: Int => new Date(ms)
      case value: Any => value
    }
  )

  val uuid: (ru.Type, Any => Any) = (
    ru.typeOf[UUID], (_: Any) match {
      case uuid: String => UUID.fromString(uuid)
      case value: Any => value
    }
  )

  def enum[E <: Enumeration : TypeTag](enum: E) = (
    ru.typeOf[E#Value], (_: Any) match {
      case string: String => enum.withName(string)
      case value: Any => throw new InCalDataAccessException(s"Enum ${enum} expects a String value got ${value}.")
    }
  )
}