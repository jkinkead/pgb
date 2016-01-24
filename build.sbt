lazy val root = (project in file(".")).settings(
  name := "pgb",
  version := "0.0.1",
  scalaVersion := "2.10.5",
  libraryDependencies ++= Seq(
    // Base compiler implementation.
    "org.scala-sbt" % "compile" % "0.13.9",
    // Base compiler interface.
    "org.scala-sbt" % "compiler-interface" % "0.13.9",
    // Incremental compiler.
    "org.scala-sbt" % "incremental-compiler" % "0.13.9",
    // Persisting the Analysis of the incremental compiler.
    "org.scala-sbt" % "persist" % "0.13.9",
    // Not in 2.10.X.
    // "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  ),
  fork in run := true
)

// Uncomment to get scalac debugging enabled.
// logLevel := Level.Debug
