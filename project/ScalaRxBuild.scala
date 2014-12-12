import sbt._
import sbt.Keys._
import com.inthenow.sbt.scalajs._
import com.inthenow.sbt.scalajs.SbtScalajs._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import ScalaJSKeys._
import bintray.Plugin._
import org.eclipse.jgit.lib._

object ScalaRxBuild extends Build {

  val logger = ConsoleLogger()

  val buildSettings = bintrayPublishSettings ++ Seq(
    scalaVersion := "2.11.4",
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    organization := "uk.co.turingatemyhamster",
    name := "scalarx",
    version := "0.2.6",
    publishMavenStyle := false,
//    bintray.Keys.repository in bintray.Keys.bintray := "sbt-plugins",
    bintray.Keys.bintrayOrganization in bintray.Keys.bintray := None,
    licenses +=("MIT", url("http://www.opensource.org/licenses/mit-license.php"))
  )

  val module = XModule(id = "scalarx", defaultSettings = buildSettings)

  lazy val scalarx = module.project(scalarxPlatformJvm, scalarxPlatformJs)
  lazy val scalarxPlatformJvm = module.jvmProject(scalarxSharedJvm).
        settings(platformJvmSettings : _*)
  lazy val scalarxPlatformJs = module.jsProject(scalarxSharedJs).
        settings(platformJsSettings : _*)
  lazy val scalarxSharedJvm = module.jvmShared().
        settings(sharedJvmSettings : _*)
  lazy val scalarxSharedJs = module.jsShared(scalarxSharedJvm).
        settings(sharedJsSettings : _*)

  lazy val platformJsSettings = Seq(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6" % "provided"
    ),
    test in Test := (test in (Test, fastOptStage)).value
  )

  lazy val platformJvmSettings = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.3.2" % "provided",
      "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided",
      "com.lihaoyi" %% "utest" % "0.2.4" % "test"
    )
  )

  lazy val sharedJvmSettings = Seq(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "acyclic" % "0.1.2",
      "com.lihaoyi" %% "utest" % "0.2.4" % "test"
    )
  )

  lazy val sharedJsSettings = Seq(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "acyclic" % "0.1.2",
      "com.lihaoyi" %% "utest" % "0.2.4" % "test"
    )
  )

  def fetchGitBranch(): String = {
    val builder = new RepositoryBuilder()
    builder.setGitDir(file(".git"))
    val repo = builder.readEnvironment().findGitDir().build()
    val gitBranch = repo.getBranch
    logger.info(s"Git branch reported as: $gitBranch")
    repo.close()
    val travisBranch = Option(System.getenv("TRAVIS_BRANCH"))
    logger.info(s"Travis branch reported as: $travisBranch")

    val branch = (travisBranch getOrElse gitBranch) replaceAll ("/", "_")
    logger.info(s"Computed branch is $branch")
    branch
  }

  def makeVersion(baseVersion: String): String = {
    val branch = fetchGitBranch()
    if(branch == "main") {
      baseVersion
    } else {
      val tjn = Option(System.getenv("TRAVIS_JOB_NUMBER"))
      s"$branch-$baseVersion${
        tjn.map("." + _) getOrElse ""
      }"
    }
  }
}
