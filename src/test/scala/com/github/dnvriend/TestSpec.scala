/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.dnvriend

import java.io.InputStream
import java.util.UUID

import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, GivenWhenThen, Matchers, FlatSpec }

import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try

trait TestSpec extends FlatSpec with GivenWhenThen with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 50.seconds)

  type UUIDAsString = String
  def randomId: UUIDAsString = UUID.randomUUID.toString

  implicit class FutureToTry[T](f: Future[T]) {
    def toTry: Try[T] = Try(f.futureValue)
  }

  def withInputStream[T](fileName: String)(f: InputStream ⇒ T): T = {
    val is = fromClasspathAsStream(fileName)
    try { f(is) } finally { Try(is.close()) }
  }

  def streamToString(is: InputStream): String =
    Source.fromInputStream(is).mkString

  def fromClasspathAsString(fileName: String): String =
    streamToString(fromClasspathAsStream(fileName))

  def fromClasspathAsStream(fileName: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(fileName)

  /**
   * Returns the amount of memory in bytes
   */
  def allocatedMemory: Long = totalMemory - freeMemory

  /**
   * Returns the total allocated amount of memory in bytes
   */
  def totalMemory: Long = sys.runtime.totalMemory()

  /**
   * Returns the amount of memory in bytes
   */
  def maxMemory: Long = sys.runtime.maxMemory()

  /**
   * Return the amount of memory in bytes
   */
  def freeMemory: Long = sys.runtime.freeMemory()

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.toTry should be a 'success
  }
}
