lazy val root = (project in file(".")).settings(
  name := "pgb",
  version := "0.0.1",
  scalaVersion := "2.11.6",
  libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  )
)
