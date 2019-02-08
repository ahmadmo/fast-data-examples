addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.19")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin" % "0.8.4",
  "com.thesamet.scalapb" %% "sparksql-scalapb-gen" % "0.7.0")
