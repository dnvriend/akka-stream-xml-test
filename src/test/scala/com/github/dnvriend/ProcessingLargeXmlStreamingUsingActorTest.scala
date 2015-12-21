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

import akka.actor.{ Actor, ActorLogging, PoisonPill, Props }
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.TestProbe

import scala.xml.pull._

/**
 * see: https://github.com/codesurf42/wikiParser/blob/master/src/main/scala/Parser.scala#L66-L71
 */
class ProcessingLargeXmlStreamingUsingActorTest extends TestSpec {

  class TaxActor extends Actor with ActorLogging {
    case class Tax(taxType: String, value: String)

    var inTax: Boolean = false
    var taxType: String = null
    var taxValue: String = null

    override def receive: Receive = {
      case EvElemStart(_, "tax", metadata, _) ⇒
        taxType = metadata.asAttrMap.getOrElse("type", "NOTHING")
        inTax = true
      case EvText(text) if inTax ⇒
        taxValue = text
      case EvElemEnd(_, "tax") if inTax ⇒
        // side effect here
        log.info(Tax(taxType, taxValue).toString)
        inTax = false
      case _ ⇒
    }
  }

  "Loading a big XML file whilst generating XMLEvents" should "consume less memory" ignore {
    val start = System.currentTimeMillis()
    withXMLEventReader("lot-of-orders.xml") { reader ⇒
      Source(() ⇒ reader).runFold(0L) { (c, _) ⇒ c + 1 }.futureValue shouldBe 4800003 // 4.8 million events :)
    }
    println(s"Processing took: ${System.currentTimeMillis() - start} ms")
  }

  it should "parse only events for Tax and generate Tax case classes" in {
    val taxActor = system.actorOf(Props(new TaxActor))
    val probe = TestProbe()
    probe watch taxActor
    withXMLEventReader("one-order.xml") { reader ⇒
      Source(() ⇒ reader).runWith(Sink.actorRef(taxActor, PoisonPill))
      probe.expectTerminated(taxActor)
    }
  }
}
