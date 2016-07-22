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

import akka.stream.contrib.Counter
import akka.stream.integration.xml.XMLParser
import akka.stream.integration.xml.XMLParser._
import akka.stream.testkit.scaladsl.TestSink

import scala.xml.pull._

class ProcessingLargeXmlStreamingUsingGraphStageTest extends TestSpec {

  case class Tax(taxType: String, value: String)
  case class Order(id: String)

  val orderParser = {
    var orderId: String = null
    XMLParser.flow {
      case EvElemStart(_, "order", meta, _) =>
        orderId = getAttr(meta)("id"); emit()
      case EvElemEnd(_, "order") =>
        emit(Order(orderId))
    }
  }

  val tagParser = {
    import XMLParser._
    var taxType: String = null
    var taxValue: String = null
    XMLParser.flow {
      case EvElemStart(_, "tax", meta, _) =>
        taxType = getAttr(meta)("type"); emit()
      case EvText(text) =>
        taxValue = text; emit()
      case EvElemEnd(_, "tax") =>
        emit(Tax(taxType, taxValue))
    }
  }

  "Processing XML with stateful stage" should "count the number of events" in {
    withXMLEventSource("one-order.xml") { source =>
      source.runWith(Counter.sink).futureValue shouldBe 41
    }
  }

  it should "parse only events for Tax and generate Tax case classes" in {
    withXMLEventSource("one-order.xml") { source =>
      source.via(tagParser)
        .runWith(TestSink.probe[Tax])
        .request(Integer.MAX_VALUE)
        .expectNext(Tax("federal", "0.80"), Tax("state", "0.80"), Tax("local", "0.40"))
        .expectComplete()
    }
  }

  it should "parse only events for Order and generate Order case classes" in {
    withXMLEventSource("one-order.xml") { source =>
      source.via(orderParser)
        .runWith(TestSink.probe[Order])
        .request(Integer.MAX_VALUE)
        .expectNext(Order("1"))
        .expectComplete()
    }
  }

  ignore should "parse a large xml file" in {
    val start = System.currentTimeMillis()
    withXMLEventSource("lot-of-orders.xml") { source =>
      source.via(tagParser).runWith(Counter.sink).futureValue shouldBe 300000
    }
    println(s"Processing took: ${System.currentTimeMillis() - start} ms")
  }
}
