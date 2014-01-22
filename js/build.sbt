
scalaJSSettings

Build.sharedSettings

version := "0.1.2-JS"

unmanagedSourceDirectories in Compile <+= baseDirectory(_ / ".." / "shared" / "main")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / ".." / "shared" / "test")

libraryDependencies ++= Seq(
  "org.scala-lang.modules.scalajs" %% "scalajs-jasmine-test-framework" % scalaJSVersion % "test",
  "org.scala-lang.modules.scalajs" %% "scalajs-dom" % "0.1-SNAPSHOT"
)

