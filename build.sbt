crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.0")

val scalarx = crossProject.settings(
  organization := "com.lihaoyi",
  name := "scalarx",
  scalaVersion := "2.12.0",
  version := "0.3.2",

  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "com.lihaoyi" %%% "utest" % "0.4.4" % "test",
    "com.lihaoyi" %% "acyclic" % "0.1.5" % "provided"
  ) ++ (
    CrossVersion.partialVersion(scalaVersion.value) match {
      // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        Nil
      // in Scala 2.10, quasiquotes are provided by macro paradise
      case Some((2, 10)) =>
        Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
          "org.scalamacros" %% "quasiquotes" % "2.0.0" cross CrossVersion.binary)
    }
  ),
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.5"),
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
    "org.scala-js" %%% "scalajs-dom" % "0.9.1" % "provided"
  ),
  scalaJSStage in Test := FullOptStage,
  scalacOptions ++= (if (isSnapshot.value) Seq.empty else Seq({
    val a = baseDirectory.value.toURI.toString.replaceFirst("[^/]+/?$", "")
    val g = "https://raw.githubusercontent.com/lihaoyi/scala.rx"
    s"-P:scalajs:mapSourceURI:$a->$g/v${version.value}/"
  }))
).jvmSettings(
  libraryDependencies ++= Seq(
    if (scalaVersion.value.startsWith("2.10."))
      "com.typesafe.akka" %% "akka-actor" % "2.3.15" % "provided"
    else
      "com.typesafe.akka" %% "akka-actor" % "2.4.12" % "provided")
)

lazy val js = scalarx.js

lazy val jvm = scalarx.jvm
