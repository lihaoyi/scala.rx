crossScalaVersions := Seq("2.10.4", "2.11.4")

val scalarx = crossProject.settings(
  organization := "com.lihaoyi",
  name := "scalarx",
  scalaVersion := "2.10.4",
  version := "0.2.8",

  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "utest" % "0.3.1" % "test",
    "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided"
  ),
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.2"),
  testFrameworks += new TestFramework("utest.runner.Framework"),
  autoCompilerPlugins := true,
  // Sonatype

  publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),

  pomExtra :=
    <url>https://github.com/lihaoyi/scalatags</url>
      <licenses>
        <license>
          <name>MIT license</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
        </license>
      </licenses>
      <scm>
        <url>git://github.com/lihaoyi/scalatags.git</url>
        <connection>scm:git://github.com/lihaoyi/scalatags.git</connection>
      </scm>
      <developers>
        <developer>
          <id>lihaoyi</id>
          <name>Li Haoyi</name>
          <url>https://github.com/lihaoyi</url>
        </developer>
      </developers>

).jsSettings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.7.0" % "provided"
  ),
  scalaJSStage in Test := FullOptStage,
  scalacOptions ++= (if (isSnapshot.value) Seq.empty else Seq({
    val a = baseDirectory.value.toURI.toString.replaceFirst("[^/]+/?$", "")
    val g = "https://raw.githubusercontent.com/lihaoyi/scala.rx"
    s"-P:scalajs:mapSourceURI:$a->$g/v${version.value}/"
  }))
).jvmSettings(
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.2" % "provided"
  )
)
lazy val js = scalarx.js

lazy val jvm = scalarx.jvm
