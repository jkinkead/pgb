package pgb.task

import pgb.{ FileOutputTask, FilesArtifact, Input, Task }
import pgb.path.Resolver

// TODO: Add the dependency for ScalaInstance!!!
// TODO: Verify {Console,}Logger is in the core libs we have.
import sbt.{ ClasspathOptions, CompileOptions, CompileSetup, ConsoleLogger, Logger, ScalaInstance }
import sbt.compiler.{
  AnalyzingCompiler,
  CompileOutput,
  CompilerCache,
  CompilerInterfaceProvider,
  RawCompiler
}
import sbt.inc.{ Analysis, AnalysisStore, FileBasedStore, IncOptions, IncrementalCompile, Locate }
import xsbti.AnalysisCallback
import xsbti.compile.{ CompileOrder, DependencyChanges }

import java.io.File
import java.net.{ URI, URL, URLClassLoader }
import java.nio.file.{ Files, Paths }

object SbtScalaTask extends FileOutputTask {
  override val taskName: String = "scalac"

  override val argumentTypes = Map(
    "src" -> Task.FileType
  )

  /** @return a class loader loading files out of the given sequence of jars. */
  def getClassLoader(jars: Seq[File]): ClassLoader = {
    new URLClassLoader(jars.map(_.toURI.toURL).toArray[URL], this.getClass.getClassLoader)
  }

  /** Implementation copied from IncrementalCompiler in sbt integration. */
  def compileInterfaceJar(
    sourceJar: File,
    targetJar: File,
    interfaceJar: File,
    instance: ScalaInstance,
    sbtLogger: Logger
  ): Unit = {
    // TODO: ClasspathOptions doesn't seem to popluate the classpath the way we want. `auto` in
    // particular uses the wrong scala version.
    val raw = new RawCompiler(
      instance,
      //ClasspathOptions(true, true, false, true, true),
      ClasspathOptions.auto,
      sbtLogger
    )
    AnalyzingCompiler.compileSources(
      Seq(sourceJar),
      targetJar,
      Seq(interfaceJar),
      "sbt compiler",
      raw,
      sbtLogger
    )
  }

  override def execute(
    name: Option[String],
    buildRoot: URI,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[FilesArtifact]
  ): FilesArtifact = {
    // TODO: Pass this in; the framework should create this.
    val destPath = Paths.get(buildRoot).resolve("target/pgb/scalac")
    Files.createDirectories(destPath)

    val outputDirectory = CompileOutput(destPath.toFile)

    // TODO: SBT sets the compiler thread's ClassLoader (via setContextClassLoader) in order to make
    // these resolve to the correct thing.
    // The ClassLoader itself is just a URLClassLoader wrapping the target Scala library classpath,
    // with the parent loader returned from ScalaProvider.loader.
    // ScalaProvider.loader is an instance of BootFilteredLoader, which does two things: delegates
    // getResource(s) calls to the root class loader, and throws exceptions when requested to load
    // embedded classes (ivy, sbt, scala, and fjbg).

    println("Getting ScalaInstance")

    // TODO: Get scala home in some other way. SBT uses the sbt launcher to get this.
    val scalaVersion = "2.11.6"
    // val scalaVersion = "2.10.5"
    val scalaHome = new File(s"/Users/Kinkead/lib/scala-$scalaVersion")
    val scalaCompiler = new File(scalaHome, "lib/scala-compiler.jar")
    val scalaLibrary = new File(scalaHome, "lib/scala-library.jar")
    val scalaJars = (ScalaInstance.allJars(scalaHome).toSet - scalaCompiler - scalaLibrary).toSeq
    val classLoader = getClassLoader(Seq(scalaCompiler, scalaLibrary))
    val scalaInstance =
      new ScalaInstance(scalaVersion, classLoader, scalaLibrary, scalaCompiler, scalaJars, None)
    println(s"got ScalaInstance = $scalaInstance")

    // TODO: Use task name or similar for a sub-directory, in case of multiple scalac targets.

    val analysisFile = destPath.resolve("analysis.txt").toFile
    val analysisStore = AnalysisStore.sync(AnalysisStore.cached(FileBasedStore(analysisFile)))
    println(s"loading analysis from $analysisFile")
    val (oldAnalysis, compileSetup) = {
      if (analysisFile.exists) {
        analysisStore.get()
      } else {
        None
      }
    } getOrElse {
      println("no analysis found, creating empty")
      val defaultCompileSetup = new CompileSetup(
        output = outputDirectory,
        // TODO: Set scalac options correctly.
        options = new CompileOptions(options = Seq.empty, javacOptions = Seq.empty),
        // TODO: This probably should be something else . . .
        compilerVersion = scalaInstance.version,
        order = CompileOrder.ScalaThenJava,
        nameHashing = true
      )
      (Analysis.Empty, defaultCompileSetup)
    }

    // TODO: Default from a task arg.
    val extraArgs = Seq("-g:line", "-unchecked")

    // TODO: Take source path from args.
    //val sourceFiles = Resolver.resolvePath("src/main/scala/**.scala", buildRoot)
    val sourceFiles = Resolver.resolvePath("src/main/scala/**/Artifact.scala", buildRoot)

    // TODO: Configure this correctly! You can pass in a stream.
    // http://www.scala-sbt.org/0.13.7/api/index.html#sbt.ConsoleLogger$
    val sbtLogger = {
      val logger = ConsoleLogger()
      logger.setLevel(sbt.Level.Debug)
      logger
    }

    // CompilerInterfaceProvider is a special shiv that lets SBT dynamically compile new versions of
    // the SBT compiler from source.
    //
    // We *might* be able to get away with using the stale Scala jar, but might have to recompile
    // it.
    val jarDest = new File(s"/Users/Kinkead/compiler-interface-$scalaVersion.jar")
    if (!jarDest.exists) {
      // TODO: This compilation should happen against the target scala version, not the current
      // scala version.
      println("compiling scala . . . ")
      compileInterfaceJar(
        // TODO: Get from deps.
        sourceJar = new File("/Users/Kinkead/.ivy2/cache/org.scala-sbt/compiler-interface/jars/compiler-interface-src-0.13.8.jar"),
        targetJar = jarDest,
        // TODO: Get from deps.
        interfaceJar = new File("/Users/Kinkead/.ivy2/cache/org.scala-sbt/interface/jars/interface-0.13.8.jar"),
        instance = scalaInstance,
        sbtLogger = sbtLogger
      )
      println("Done compiling scala.")
    }
    val interfaceProvider = CompilerInterfaceProvider.constant(jarDest)
    println("got interface provider.")

    // TODO: Run Scala compiler.
    val analyzingCompiler = new AnalyzingCompiler(
      scalaInstance,
      interfaceProvider,
      // TODO: Figure out WTF to do here.
      ClasspathOptions.boot
    /*
      new ClasspathOptions(
        bootLibrary = true,
        compiler = true,
        extra = true,
        autoBoot = true,
        filterLibrary = false
      )
      */
    )
    println("created AnalyzingCompiler")

    // Compilation function for the compiler.
    def compilationFunction(
      sources: Set[File],
      changes: DependencyChanges,
      callback: AnalysisCallback
    ): Unit = {
      analyzingCompiler(
        sources.toSeq,
        changes,
        // This classpath needs to have our scala instance on it, which is copied over to the
        // -bootclasspath option to the scala compiler (if autoBoot is set in ClasspathOptions).
        // Other classpath items are not special-cased.
        // TODO: Take from real classpath.
        classpath = Seq(scalaInstance.libraryJar),
        // TODO: This is probably ignored.
        singleOutput = destPath.toFile,
        // TODO: Figure out good compiler options.
        options = Seq.empty[String],
        callback,
        // TODO: Figure out what this does!
        maximumErrors = 10,
        // TODO: Don't do this?
        cache = CompilerCache.fresh,
        sbtLogger
      )
    }

    // Call the incremental compiler, using the AnalyzingCompiler we created.
    println(s"calling incremental compile")
    val (success, newAnalysis) = IncrementalCompile(
      sourceFiles.toSet,
      // Class name -> source file lookup function.
      // TODO: Make the real classpath for the target.
      Locate.entry(classpath = Seq.empty[File], Locate.definesClass),
      // Compilation function.
      compilationFunction,
      oldAnalysis,
      // TODO: Figure out a real function mapping files to analysis entries?
      forEntry = { _: File =>
        Some(oldAnalysis)
      },
      outputDirectory,
      sbtLogger,
      IncOptions.Default
    )

    // Persist!
    println("persisting analysis")
    analysisStore.set(newAnalysis, compileSetup)

    println("Done!")

    val files = Resolver.resolvePath("**.class", destPath.toUri)

    FilesArtifact(files)
  }
}
