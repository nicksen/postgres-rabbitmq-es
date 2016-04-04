name := "postgres-rabbitmq-es"

version := "1.0.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := Versions.scala

scalacOptions ++= Seq("-feature")

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}

libraryDependencies ++= Seq(
  cache,
  evolutions,
  filters,
  jdbc,
  ws,

  "org.scala-lang.modules" %% "scala-parser-combinators" % Versions.scalaModules,
  "org.scala-lang.modules" %% "scala-xml" % Versions.scalaModules,
  "xalan" % "xalan" % Versions.xalan,
  "xalan" % "serializer" % Versions.xalan,
  "org.apache.httpcomponents" % "httpcore" % Versions.httpcore,
  "org.apache.httpcomponents" % "httpclient" % Versions.httpclient,
  "com.google.guava" % "guava" % Versions.guava,

  "org.postgresql" % "postgresql" % Versions.postgres,
  "com.typesafe.play" %% "anorm" % Versions.anorm,
  "com.thenewmotion.akka" %% "akka-rabbitmq" % Versions.rabbitmq,
  "org.sangria-graphql" %% "sangria" % Versions.sangria,
  "org.sangria-graphql" %% "sangria-relay" % Versions.sangria,
  "com.lambdaworks" % "scrypt" % Versions.scrypt,

  "redis.clients" % "jedis" % "2.8.0",

  specs2 % Test
)

resolvers += "Local Maven repo" at s"file://${Path.userHome.absolutePath}/.m2/repository"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

resolvers += "The New Motion Public Repo" at "http://nexus.thenewmotion.com/content/groups/public/"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// Discard documentation when packaging
sources in(Compile, doc) := Seq.empty

publishArtifact in(Compile, packageDoc) := false
