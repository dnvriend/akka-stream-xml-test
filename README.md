# akka-stream-xml-test
Very small study project on processing large xml files using scala XML and akka stream.

# Outline
As expected, scala xml is rather strict, ie. when a large XML file is processed, the whole NodeSeq is in memory.
When processing large XML files, using akka stream can become a good solution to process in a bounded memory context.

Using an `akka.stream.stage.StatefulStage` can be of use when we just want to process `scala.xml.pull.XMLEvent` 
and convert them into case classes to be processed ie. by Slick. 

Creating `scala.xml.pull.XMLEvent` messages is made possible by the `scala.xml.pull.XMLEventReader` that acceps an
`scala.io.Source` object, which has been created from an `java.io.InputStream`.  

