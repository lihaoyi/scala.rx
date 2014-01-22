
scalaJSSettings

Build.sharedSettings

version := "0.1.2-JS"

libraryDependencies ++= Seq(
  "org.scala-lang.modules.scalajs" %% "scalajs-jasmine-test-framework" % scalaJSVersion % "test"
)

