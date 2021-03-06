/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.slick.javadsl

import java.util.concurrent.CompletionStage
import java.util.function.{Function => JFunction}

import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters._

import akka.Done
import akka.NotUsed
import akka.stream.javadsl._

import slick.dbio.DBIO
import slick.jdbc.GetResult
import slick.jdbc.SQLActionBuilder
import slick.jdbc.SetParameter

import akka.stream.alpakka.slick.scaladsl.{Slick => ScalaSlick}

object Slick {

  /**
   * Java API: creates a Source that performs the specified query against
   *           the specified Slick database and streams the results through
   *           the specified mapper function to turn database each row
   *           element into an instance of T.
   *
   * @param session The database session to use.
   * @param query The query string to execute. There is currently no Java
   *              DSL support for parameter substitution so you will have
   *              to build the full query statement before passing it in.
   * @param mapper A function that takes an individual result row and
   *               transforms it to an instance of T.
   */
  def source[T](
      session: SlickSession,
      query: String,
      mapper: JFunction[SlickRow, T]
  ): Source[T, NotUsed] = {
    val streamingAction = SQLActionBuilder(query, SetParameter.SetUnit).as[T](toSlick(mapper))

    ScalaSlick
      .source[T](streamingAction)(session)
      .asJava
  }

  /**
   * Java API: creates a Flow that takes a stream of elements of
   *           type T, transforms each element to a SQL statement
   *           using the specified function, and then executes
   *           those statements against the specified Slick database.
   *
   * @param session The database session to use.
   * @param toStatement A function that creeates the SQL statement to
   *                    execute from the current element. Any DML or
   *                    DDL statement is acceptable.
   */
  def flow[T](
      session: SlickSession,
      toStatement: JFunction[T, String] // TODO: or use the akka japi Function2 interface?
  ): Flow[T, java.lang.Integer, NotUsed] =
    flow(session, 1, toStatement)

  /**
   * Java API: creates a Flow that takes a stream of elements of
   *           type T, transforms each element to a SQL statement
   *           using the specified function, and then executes
   *           those statements against the specified Slick database.
   *
   * @param session The database session to use.
   * @param parallelism How many parallel asynchronous streams should be
   *                    used to send statements to the database. Use a
   *                    value of 1 for sequential execution.
   * @param toStatement A function that creeates the SQL statement to
   *                    execute from the current element. Any DML or
   *                    DDL statement is acceptable.
   */
  def flow[T](
      session: SlickSession,
      parallelism: Int,
      toStatement: JFunction[T, String]
  ): Flow[T, java.lang.Integer, NotUsed] =
    ScalaSlick
      .flow[T](parallelism, toDBIO(toStatement))(session)
      .map(Int.box(_))
      .asJava

  /**
   * Java API: creates a Sink that takes a stream of elements of
   *           type T, transforms each element to a SQL statement
   *           using the specified function, and then executes
   *           those statements against the specified Slick database.
   *
   * @param session The database session to use.
   * @param toStatement A function that creeates the SQL statement to
   *                    execute from the current element. Any DML or
   *                    DDL statement is acceptable.
   */
  def sink[T](
      session: SlickSession,
      toStatement: JFunction[T, String] // TODO: or use the akka japi Function2 interface?
  ): Sink[T, CompletionStage[Done]] =
    sink(session, 1, toStatement)

  /**
   * Java API: creates a Sink that takes a stream of elements of
   *           type T, transforms each element to a SQL statement
   *           using the specified function, and then executes
   *           those statements against the specified Slick database.
   *
   * @param session The database session to use.
   * @param parallelism How many parallel asynchronous streams should be
   *                    used to send statements to the database. Use a
   *                    value of 1 for sequential execution.
   * @param toStatement A function that creeates the SQL statement to
   *                    execute from the current element. Any DML or
   *                    DDL statement is acceptable.
   */
  def sink[T](
      session: SlickSession,
      parallelism: Int,
      toStatement: JFunction[T, String]
  ): Sink[T, CompletionStage[Done]] =
    ScalaSlick
      .sink[T](parallelism, toDBIO(toStatement))(session)
      .mapMaterializedValue(_.toJava)
      .asJava

  /**
   * Java API: creates a Sink that takes a stream of complete SQL
   *           statements (e.g. a stream of Strings) to execute
   *           against the specified Slick database.
   *
   * @param session The database session to use.
   */
  def sink(
      session: SlickSession
  ): Sink[String, CompletionStage[Done]] =
    sink[String](session, 1, JFunction.identity[String]())

  /**
   * Java API: creates a Sink that takes a stream of complete SQL
   *           statements (e.g. a stream of Strings) to execute
   *           against the specified Slick database.
   *
   * @param session The database session to use.
   * @param parallelism How many parallel asynchronous streams should be
   *                    used to send statements to the database. Use a
   *                    value of 1 for sequential execution.
   */
  def sink(
      session: SlickSession,
      parallelism: Int
  ): Sink[String, CompletionStage[Done]] =
    sink[String](session, parallelism, JFunction.identity[String]())

  private def toSlick[T](mapper: JFunction[SlickRow, T]): GetResult[T] =
    GetResult(pr => mapper(new SlickRow(pr)))

  private def toDBIO[T](javaDml: JFunction[T, String]): T => DBIO[Int] = { t =>
    SQLActionBuilder(javaDml.asScala(t), SetParameter.SetUnit).asUpdate
  }
}
