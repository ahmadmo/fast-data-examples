name := "event-transporter"
version := "1.0"
scalaVersion := "2.11.12"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/")

scalacOptions ++= Seq(
  "-feature", "-deprecation",
  "-language:implicitConversions",
  "-language:postfixOps")

enablePlugins(AssemblyPlugin)

val hoconVersion = "1.3.3"
val scalaLoggingVersion = "3.9.0"
val log4jVersion = "2.11.1"
val sparkVersion = "2.4.0"
val sparkCassandraVersion = "2.4.0"
val sparkElasticsearchVersion = "6.6.0"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % hoconVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.apache.logging.log4j" % "log4j-core" % log4jVersion,
  "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion % "provided",
  "com.datastax.spark" %% "spark-cassandra-connector" % sparkCassandraVersion,
  "org.elasticsearch" %% "elasticsearch-spark-20" % sparkElasticsearchVersion,
  "com.thesamet.scalapb" %% "sparksql-scalapb" % "0.8.0")

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("com.google.protobuf.**" -> "shadeproto.@1").inAll
)
