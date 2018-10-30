name := "sbt-sonar-reporter"
organization := "com.evolutiongaming"
version := "0.1-SNAPSHOT"

sbtPlugin := true

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

bintrayPackageLabels := Seq("sbt","plugin")
bintrayVcsUrl := Some("""git@github.com:com.evolutiongaming/sbt-sonar-reporter.git""")

initialCommands in console := """import com.evolutiongaming.sbt.sonar._"""

enablePlugins(ScriptedPlugin)
// set up 'scripted; sbt plugin for testing sbt plugins
scriptedLaunchOpts ++=
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
