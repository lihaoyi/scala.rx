lazy val root = project.in(file("."))

lazy val js = project.in(file("js"))

Build.sharedSettings

unmanagedSourceDirectories in Compile <+= baseDirectory(_ / "shared" / "main" / "scala")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala")

libraryDependencies ++= Seq(
  "com.lihaoyi" % "utest_2.10" % "0.1.0" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.lihaoyi" % "utest_2.10" % "0.1.0" % "test",
  "com.lihaoyi" % "utest-runner_2.10" % "0.1.0" % "test"
)

testFrameworks += new TestFramework("utest.runner.JvmFramework")
