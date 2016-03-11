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

import akka.stream._
import akka.stream.stage._
import akka.stream.testkit.scaladsl.TestSink

import scala.xml.pull._

class ProcessingLargeXmlStreamingUsingGraphStageTest extends TestSpec {

  case class Tax(taxType: String, value: String)

  case class Order(id: String)

  trait PushPull {
    def doPull[T](in: Inlet[T]): Unit
    def doPush[T](out: Outlet[T], elem: T): Unit
  }

  trait AbstractXMLEventStage[Out] extends GraphStage[FlowShape[XMLEvent, Out]] {
    val in = Inlet[XMLEvent]("XMLEvent.in")
    val out = Outlet[Out]("XMLEvent.out")

    override def shape: FlowShape[XMLEvent, Out] = FlowShape.of(in, out)

    def processEvents(context: PushPull): PartialFunction[XMLEvent, Unit]

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with PushPull { logic ⇒
      setHandler(in, new InHandler {
        override def onPush(): Unit =
          processEvents(logic).applyOrElse(grab(in), PartialFunction[XMLEvent, Unit] { case _ ⇒ pull(in) })
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })

      override def doPull[T](in: Inlet[T]): Unit = pull(in)

      override def doPush[T](out: Outlet[T], elem: T): Unit = push(out, elem)
    }
  }

  class TaxStatefulStage extends AbstractXMLEventStage[Tax] {
    var taxType: String = null
    var taxValue: String = null

    override def processEvents(ctx: PushPull): PartialFunction[XMLEvent, Unit] = {
      case EvElemStart(_, "tax", metadata, _) ⇒
        taxType = metadata.asAttrMap.getOrElse("type", "NOTHING")
        ctx.doPull(in)
      case EvText(text) ⇒
        taxValue = text
        ctx.doPull(in)
      case EvElemEnd(_, "tax") ⇒
        ctx.doPush(out, Tax(taxType, taxValue))
    }
  }

  class OrderStatefulStage extends AbstractXMLEventStage[Order] {
    var orderId: String = null

    override def processEvents(ctx: PushPull): PartialFunction[XMLEvent, Unit] = {
      case EvElemStart(_, "order", metadata, _) ⇒
        orderId = metadata.asAttrMap.getOrElse("id", "NOTHING")
        ctx.doPull(in)
      case EvElemEnd(_, "order") ⇒
        ctx.doPush(out, Order(orderId))
    }
  }

  "Processing XML with stateful stage" should "count the number of events" in {
    withXMLEventSource("one-order.xml") { source ⇒
      source.runFold(0) { (c, _) ⇒ c + 1 }.futureValue shouldBe 41
    }
  }

  it should "parse only events for Tax and generate Tax case classes" in {
    withXMLEventSource("one-order.xml") { source ⇒
      source.via(new TaxStatefulStage)
        .runWith(TestSink.probe[Tax])
        .request(Integer.MAX_VALUE)
        .expectNext(Tax("federal", "0.80"), Tax("state", "0.80"), Tax("local", "0.40"))
        .expectComplete()
    }
  }

  it should "parse only events for Order and generate Order case classes" in {
    withXMLEventSource("one-order.xml") { source ⇒
      source.via(new OrderStatefulStage)
        .runWith(TestSink.probe[Order])
        .request(Integer.MAX_VALUE)
        .expectNext(Order("1"))
        .expectComplete()
    }
  }

  ignore should "parse a large xml file" in {
    val start = System.currentTimeMillis()
    withXMLEventSource("lot-of-orders.xml") { source ⇒
      source.via(new TaxStatefulStage)
        .runFold(0) { case (c, _) ⇒ c + 1 }.futureValue shouldBe 300000
    }
    println(s"Processing took: ${System.currentTimeMillis() - start} ms")
  }
}
