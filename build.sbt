lazy val root = project.in(file("."))

lazy val js = project.in(file("js"))

Build.sharedSettings

version := "0.2.3"

unmanagedSourceDirectories in Compile <+= baseDirectory(_ / "shared" / "main" / "scala")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.0" % "provided",
  "com.lihaoyi.utest" % "utest_2.10" % "0.1.2" % "test",
  "com.lihaoyi.acyclic" %% "acyclic" % "0.1.1" % "provided"
)

testFrameworks += new TestFramework("utest.runner.JvmFramework")

addCompilerPlugin("com.lihaoyi.acyclic" %% "acyclic" % "0.1.1")

autoCompilerPlugins := true
