lazy val root = project.in(file(".")).aggregate(js)

lazy val js = project.in(file("js"))

Build.sharedSettings

unmanagedSourceDirectories in Compile <+= baseDirectory(_ / "shared" / "main")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test")

libraryDependencies ++= Seq(
    "org.scalatest" % "scalatest_2.10" % "2.0" % "test",
    "com.typesafe.akka" %% "akka-actor" % "2.2.3"
)

