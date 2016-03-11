# akka-stream-xml-test
Very small study project on processing large xml files using scala XML and akka stream.

# Outline
As expected, scala xml is rather strict, ie. when a large XML file is processed, the whole NodeSeq is in memory.
When processing large XML files, using akka stream can become a good solution to process in a bounded memory context.

~~Using an `akka.stream.stage.StatefulStage` can be of use when we just want to process `scala.xml.pull.XMLEvent` 
and convert them into case classes to be processed ie. by Slick.~~ Since Akka `v2.4.2` the `akka.stream.stage.StatefulStage` has been
replaced by the `akka.stream.stage.GraphStage`. The `StatefulStage` class had some flaws and limitations, most notably around 
completion handling which caused subtle bugs. The new `GraphStage` ([Custom processing with GraphStage](http://doc.akka.io/docs/akka/2.4.2/scala/stream/stream-customize.html)) 
solves these issues and should be used instead. The `GraphStage` abstraction can be used to create arbitrary graph processing stages with any number of input or output ports.
We can use the `GraphStage` to create a stage that is stateful. For a working example take a look at [ProcessingLargeXmlStreamingUsingStatefulStageTest](https://github.com/dnvriend/akka-stream-xml-test/blob/master/src/test/scala/com/github/dnvriend/ProcessingLargeXmlStreamingUsingGraphStageTest.scala).

Why use a `GraphStage` and not the stages that come out of the box with Akka Stream and are available using the Akka Stream DSL? Simple really,
sometimes the operation you are looking for is just missing from Akka Stream, so you should implement that functionality yourself (like eg. 
processing an XML graph stage) or for performance reasons. The downside is that you should maintain the custom stage, which should not be 
your primary concern (your primary concern should be creating domain solutions for your client/patron, not maintaining framework components).

Creating `scala.xml.pull.XMLEvent` messages is made possible by the `scala.xml.pull.XMLEventReader` that accepts an
`scala.io.Source` (please notice the __scala.io__ package), object, which has been created from an `java.io.InputStream`.  

# Some Akka Stream Parley:
Akka stream uses the following abstractions:

* `Stream`: a continually moving flow of elements, 
* `Element`: the processing unit of streams,
* `Processing stage`: building blocks that build up a `Flow` or `FlowGraph` for example `map()`, `filter()`, `transform()`, `junction()` etc,
* `Source`: a processing stage with exactly one output, emitting data elements when downstream processing stages are ready to receive them,
* `Sink`: a processing stage with exactly one input, requesting and accepting data elements
* `Flow`: a processing stage with exactly one input and output, which connects its up- and downstream by moving/transforming the data elements flowing through it,
* `Runnable flow`: A flow that has both ends attached to a `Source` and `Sink` respectively,
* `Materialized flow`: An instantiation / incarnation / materialization of the abstract processing-flow template. 

The abstractions above (`Flow`, `Source`, `Sink`, `Processing stage`) are used to create a processing-stream `template` or `blueprint`. When the template has a `Source` connected to a `Sink` with optionally some `processing stages` between them, such a `template` is called a `Runnable Flow`. 

The materializer for `akka-stream` is the [ActorMaterializer](http://doc.akka.io/api/akka-stream-and-http-experimental/1.0/#akka.stream.ActorMaterializer) 
which takes the list of transformations comprising a [akka.stream.scaladsl.Flow](http://doc.akka.io/api/akka-stream-and-http-experimental/1.0/#akka.stream.javadsl.Flow) 
and materializes them in the form of [org.reactivestreams.Processor](https://github.com/reactive-streams/reactive-streams-jvm/blob/master/api/src/main/java/org/reactivestreams/Processor.java) 
instances, in which every stage is converted into one actor.

In akka-http parley, a 'Route' is a `Flow[HttpRequest, HttpResponse, Unit]` so it is a processing stage that transforms 
`HttpRequest` elements to `HttpResponse` elements. 