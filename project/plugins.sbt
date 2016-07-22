resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

resolvers += "bintray-sbt-plugin-releases" at "http://dl.bintray.com/content/sbt/sbt-plugin-releases"

// to format scala source code
// https://github.com/sbt/sbt-scalariform
addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

// enable updating file headers eg. for copyright
// https://github.com/sbt/sbt-header
addSbtPlugin("de.heikoseeberger" % "sbt-header"      % "1.5.1")