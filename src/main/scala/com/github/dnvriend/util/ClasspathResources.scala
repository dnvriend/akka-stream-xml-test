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

package com.github.dnvriend.util

import java.io.InputStream

import akka.stream.scaladsl.Source

import scala.io.{ Source ⇒ ScalaIOSource }
import scala.util.Try
import scala.xml.pull.{ XMLEvent, XMLEventReader }

trait ClasspathResources {
  def withInputStream[T](fileName: String)(f: InputStream ⇒ T): T = {
    val is = fromClasspathAsStream(fileName)
    try {
      f(is)
    } finally {
      Try(is.close())
    }
  }

  def withXMLEventReader[T](fileName: String)(f: XMLEventReader ⇒ T): T =
    withInputStream(fileName) { is ⇒
      f(new XMLEventReader(ScalaIOSource.fromInputStream(is)))
    }

  def withXMLEventSource[T](fileName: String)(f: Source[XMLEvent, Unit] ⇒ T): T =
    withXMLEventReader(fileName) { reader ⇒
      f(Source(() ⇒ reader))
    }

  def streamToString(is: InputStream): String =
    ScalaIOSource.fromInputStream(is).mkString

  def fromClasspathAsString(fileName: String): String =
    streamToString(fromClasspathAsStream(fileName))

  def fromClasspathAsStream(fileName: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(fileName)

}
