def aetherLib(lib: String) = {
  "org.eclipse.aether" % s"aether-$lib" % "1.0.0.v20140518"
}

lazy val root = (project in file(".")).settings(
  name := "pgb",
  version := "0.0.1",
  // TODO: See if we can go back to 2.11 after fixing the compiler issues.
  scalaVersion := "2.10.5",
  libraryDependencies ++= Seq(
    // Aether, for downloading maven artifacts.
    aetherLib("api"),
    aetherLib("connector-basic"),
    aetherLib("transport-file"),
    aetherLib("transport-http"),
    // Required for parsing POM files.
    "org.apache.maven" % "maven-aether-provider" % "3.3.9",
    // Base compiler implementation.
    "org.scala-sbt" % "compile" % "0.13.8",
    // Base compiler interface.
    "org.scala-sbt" % "compiler-interface" % "0.13.8",
    // Incremental compiler.
    "org.scala-sbt" % "incremental-compiler" % "0.13.8",
    // Persisting the Analysis of the incremental compiler.
    "org.scala-sbt" % "persist" % "0.13.8",
    // Not in Scala 2.10.X.
    // "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  ),
  fork in run := true
)

// Uncomment to get scalac debugging enabled.
// logLevel := Level.Debug
