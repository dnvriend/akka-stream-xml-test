# akka-stream-xml-test
Very small study project on processing large xml files using scala XML and akka stream.

## akka-persistence-query-extensions
The results of the study on how to process large XML files efficiently has lead to akka-stream components that I have
made open source in the librray [akka-persistence-query-extensions](https://github.com/dnvriend/akka-persistence-query-extensions):

```
"com.github.dnvriend" %% "akka-persistence-query-extensions" % "0.0.4"
```

This library contains a lot of akka-streams utilities in context of akka-persistence but also I/O related components like
for example parsing and validating XML.

The following XML components are available in [akka-persistence-query-extensions](https://github.com/dnvriend/akka-persistence-query-extensions):

## akka.stream.integration.xml.Validation
[Validation](https://github.com/dnvriend/akka-persistence-query-extensions/blob/master/src/main/scala/akka/stream/integration/xml/Validation.scala): Given a stream of ByteString, it validates an XML file given an XSD.

## akka.stream.integration.xml.XMLEventSource
[XMLEventSource](https://github.com/dnvriend/akka-persistence-query-extensions/blob/master/src/main/scala/akka/stream/integration/xml/XMLEventSource.scala): Given an inputstream or filename, it creates a `Source[XMLEvent, NotUsed]` that can be used to process
an XML file. It can be used together with akka-stream's processing stages and the
`akka.persistence.query.extension.Journal` to store the transformed messages in the journal to be consumed
by other components. It can also be used with `reactive-activemq`'s
`akka.stream.integration.activemq.ActiveMqProducer` to send these messages to a VirtualTopic.

## akka.stream.integration.xml.XMLParser
[XMLParser](https://github.com/dnvriend/akka-persistence-query-extensions/blob/master/src/main/scala/akka/stream/integration/xml/XMLParser.scala): 
It should be easy to write XML parsers to process large XML files efficiently. Most often this means reading the XML
sequentially, parsing a known XML fragment and converting it to DTOs using case classes. For such a use case the
`akka.stream.integration.xml.XMLParser` should help you get you up and running fast!

For example, let's process the following XML:

```xml
<orders>
    <order id="1">
        <item name="Pizza" price="12.00">
            <pizza>
                <crust type="thin" size="14"/>
                <topping>cheese</topping>
                <topping>sausage</topping>
            </pizza>
        </item>
        <item name="Breadsticks" price="4.00"/>
        <tax type="federal">0.80</tax>
        <tax type="state">0.80</tax>
        <tax type="local">0.40</tax>
    </order>
</orders>
```

Imagine we are interested in only orders, and only the tax, lets write two parsers:

```scala
import scala.xml.pull._
import akka.stream.scaladsl._
import akka.stream.integration.xml.XMLParser
import akka.stream.integration.xml.XMLParser._
import akka.stream.integration.xml.XMLEventSource

case class Order(id: String)

val orderParser: Flow[XMLEvent, Order] = {
 var orderId: String = null
 XMLParser.flow {
  case EvElemStart(_, "order", meta, _) ⇒
    orderId = getAttr(meta)("id"); emit()
  case EvElemEnd(_, "order") ⇒
    emit(Order(orderId))
 }
}

case class Tax(taxType: String, value: String)

val tagParser: Flow[XMLEvent, Tax] = {
  var taxType: String = null
  var taxValue: String = null
  XMLParser.flow {
    case EvElemStart(_, "tax", meta, _) =>
      taxType = getAttr(meta)("type"); emit()
    case EvText(text) ⇒
      taxValue = text; emit()
    case EvElemEnd(_, "tax") ⇒
      emit(Tax(taxType, taxValue))
  }
}

XMLEventSource.fromFileName("orders.xml")
 .via(orderParser).runForeach(println)

XMLEventSource.fromFileName("orders.xml")
 .via(tagParser).runForeach(println)
```

Have fun!