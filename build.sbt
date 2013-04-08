organization  := "com.rx"

name          := "scalarx"

version       := "0.1"

scalaVersion  := "2.10.1"

resolvers += "akka nightlies" at " http://repo.akka.io/snapshots/"

libraryDependencies ++= Seq(
    "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
    "com.typesafe.akka" %% "akka-actor" % "2.2-SNAPSHOT"
)
