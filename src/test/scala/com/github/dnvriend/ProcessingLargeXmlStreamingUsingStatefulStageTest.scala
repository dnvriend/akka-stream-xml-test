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

import akka.stream.scaladsl.Source
import akka.stream.stage.{ Context, StageState, StatefulStage, SyncDirective }
import akka.stream.testkit.scaladsl.TestSink

import scala.xml.pull._

class ProcessingLargeXmlStreamingUsingStatefulStageTest extends TestSpec {

  case class Tax(taxType: String, value: String)

  case class Order(id: String)

  trait AbstractXMLEventStage[Out] extends StatefulStage[XMLEvent, Out] {
    def processEvents(ctx: Context[Out]): PartialFunction[XMLEvent, SyncDirective]

    override def initial = new StageState[XMLEvent, Out] {
      def default(ctx: Context[Out]): PartialFunction[XMLEvent, SyncDirective] = {
        case _ ⇒ ctx.pull()
      }

      override def onPush(elem: XMLEvent, ctx: Context[Out]): SyncDirective =
        processEvents(ctx).applyOrElse(elem, default(ctx))
    }
  }

  class TaxStatefulStage extends AbstractXMLEventStage[Tax] {
    var taxType: String = null
    var taxValue: String = null

    override def processEvents(ctx: Context[Tax]): PartialFunction[XMLEvent, SyncDirective] = {
      case EvElemStart(_, "tax", metadata, _) ⇒
        taxType = metadata.asAttrMap.getOrElse("type", "NOTHING")
        ctx.pull()
      case EvText(text) ⇒
        taxValue = text
        ctx.pull()
      case EvElemEnd(_, "tax") ⇒
        ctx.push(Tax(taxType, taxValue))
    }
  }

  class OrderStatefulStage extends AbstractXMLEventStage[Order] {
    var orderId: String = null

    override def processEvents(ctx: Context[Order]): PartialFunction[XMLEvent, SyncDirective] = {
      case EvElemStart(_, "order", metadata, _) ⇒
        orderId = metadata.asAttrMap.getOrElse("id", "NOTHING")
        ctx.pull()
      case EvElemEnd(_, "order") ⇒
        ctx.push(Order(orderId))
    }
  }

  "Processing XML with stateful stage" should "count the number of events" in {
    withXMLEventReader("one-order.xml") { reader ⇒
      Source(() ⇒ reader).runFold(0) { (c, _) ⇒ c + 1 }.futureValue shouldBe 41
    }
  }

  it should "parse only events for Tax and generate Tax case classes" in {
    withXMLEventReader("one-order.xml") { reader ⇒
      Source(() ⇒ reader).transform(() ⇒ new TaxStatefulStage)
        .runWith(TestSink.probe[Tax])
        .request(41)
        .expectNext(Tax("federal", "0.80"), Tax("state", "0.80"), Tax("local", "0.40"))
        .expectComplete()
    }
  }

  it should "parse only events for Order and generate Order case classes" in {
    withXMLEventReader("one-order.xml") { reader ⇒
      Source(() ⇒ reader).transform(() ⇒ new OrderStatefulStage)
        .runWith(TestSink.probe[Order])
        .request(41)
        .expectNext(Order("1"))
        .expectComplete()
    }
  }

  ignore should "parse a large xml file" in {
    val start = System.currentTimeMillis()
    withXMLEventReader("lot-of-orders.xml") { reader ⇒
      Source(() ⇒ reader).transform(() ⇒ new TaxStatefulStage)
        .runFold(0) { case (c, _) ⇒ c + 1 }.futureValue shouldBe 300000
    }
    println(s"Processing took: ${System.currentTimeMillis() - start} ms")
  }

  /**
   * All events from a given Order are in context of that order and as such must be tagged with information
   * from that order, like eg. the orderId
   *
   * TaggedXMLEvent(id: String, event: XMLEvent)
   */

  case class TaggedXMLEvent(id: String, event: XMLEvent)

  class OrderTaggingEventStatefulStage extends AbstractXMLEventStage[TaggedXMLEvent] {
    var orderId: String = null

    override def processEvents(ctx: Context[TaggedXMLEvent]) = {
      case EvElemStart(_, "order", metadata, _) ⇒
        orderId = metadata.asAttrMap.getOrElse("id", "NOTHING")
        ctx.pull()
      case EvElemEnd(_, "order") ⇒ ctx.pull()
      case event                 ⇒ ctx.push(TaggedXMLEvent(orderId, event))
    }
  }

  trait AbstractTaggedXMLEventStage[Out] extends StatefulStage[TaggedXMLEvent, Out] {
    def processEvents(ctx: Context[Out]): PartialFunction[TaggedXMLEvent, SyncDirective]

    override def initial = new StageState[TaggedXMLEvent, Out] {
      def default(ctx: Context[Out]): PartialFunction[TaggedXMLEvent, SyncDirective] = {
        case _ ⇒ ctx.pull()
      }

      override def onPush(elem: TaggedXMLEvent, ctx: Context[Out]): SyncDirective =
        processEvents(ctx).applyOrElse(elem, default(ctx))
    }
  }

  case class TaggedTax(id: String, tax: Tax)

  class TaggedTaxStatefulStage extends AbstractTaggedXMLEventStage[TaggedTax] {
    var taxType: String = null
    var taxValue: String = null

    override def processEvents(ctx: Context[TaggedTax]): PartialFunction[TaggedXMLEvent, SyncDirective] = {
      case TaggedXMLEvent(id, EvElemStart(_, "tax", metadata, _)) ⇒
        taxType = metadata.asAttrMap.getOrElse("type", "NOTHING")
        ctx.pull()
      case TaggedXMLEvent(id, EvText(text)) ⇒
        taxValue = text
        ctx.pull()
      case TaggedXMLEvent(id, EvElemEnd(_, "tax")) ⇒
        ctx.push(TaggedTax(id, Tax(taxType, taxValue)))
    }
  }

  "Processing XML within context" should "process TaggedTax for one order" in {
    withXMLEventSource("one-order.xml") { src ⇒
      src.transform(() ⇒ new OrderTaggingEventStatefulStage)
        .transform(() ⇒ new TaggedTaxStatefulStage)
        .runWith(TestSink.probe[TaggedTax])
        .request(41)
        .expectNext(TaggedTax("1", Tax("federal", "0.80")), TaggedTax("1", Tax("state", "0.80")), TaggedTax("1", Tax("local", "0.40")))
        .expectComplete()
    }
  }

  it should "process TaggedTax for two orders" in {
    withXMLEventSource("two-orders.xml") { src ⇒
      src.transform(() ⇒ new OrderTaggingEventStatefulStage)
        .transform(() ⇒ new TaggedTaxStatefulStage)
        .runWith(TestSink.probe[TaggedTax])
        .request(72)
        .expectNext(TaggedTax("1", Tax("federal", "0.80")), TaggedTax("1", Tax("state", "0.80")), TaggedTax("1", Tax("local", "0.40")), TaggedTax("2", Tax("federal", "0.80")), TaggedTax("2", Tax("state", "0.80")), TaggedTax("2", Tax("local", "0.40")))
        .expectComplete()
    }
  }

  ignore should "process TaggedTax for a lot of orders" in {
    withXMLEventSource("lot-of-orders.xml") { src ⇒
      src.transform(() ⇒ new OrderTaggingEventStatefulStage)
        .transform(() ⇒ new TaggedTaxStatefulStage)
        .runFold(0) { (c, _) ⇒ c + 1 }
        .futureValue shouldBe 300000
    }
  }
}
