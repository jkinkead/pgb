package pgb.task

import pgb.{
  Artifact,
  BuildState,
  FileOutputTask,
  FilesArtifact,
  Input,
  StringArtifact,
  Task
}
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
  override val taskType: String = "scalac"

  override val argumentTypes = Map(
    // Source file(s) to compile.
    "src" -> Input.RequiredFileList,
    // The version of Scala to compile against.
    "scalaVersion" -> Input.OptionalString
  )

  /** @param jars the jar files to set as the classpath
    * @param useParent if true, set the current class's loader as the parent of the returned class
    * loader
    * @return a class loader loading files out of the given sequence of jar files
    */
  def getClassLoader(jars: Seq[File], useParent: Boolean): ClassLoader = {
    val convertedJars = jars.map(_.toURI.toURL).toArray[URL]
    // TODO: We never use a parent loader for compiling the SBT compiler, since it will pick up
    // things on our boot classpath if we do.
    val parent = if (useParent) { this.getClass.getClassLoader } else { null }
    new URLClassLoader(convertedJars, parent)
  }

  /** Builds a ScalaInstance instance using jars from the given scala home directory. This is a
    * special bundle used internally by SBT to store information about an installed Scala version.
    * @param scalaVersion the numeric scala version, e.g. `"2.10.5"`
    * @param scalaHome the directory where scala is loaded
    * @return the ScalaInstance wrapping that home directory
    */
  def getScalaInstance(scalaVersion: String, scalaHome: File): ScalaInstance = {
    // TODO: Resolve properly!
    val scalaCompiler = new File(scalaHome, "lib/scala-compiler.jar")
    val scalaLibrary = new File(scalaHome, "lib/scala-library.jar")
    val scalaReflect = new File(scalaHome, "lib/scala-reflect.jar")
    val scalaJars = (ScalaInstance.allJars(scalaHome).toSet - scalaCompiler - scalaLibrary).toSeq
    // Note that this class loader contains all of the core Scala libraries needed to build and run
    // the SBT analyzing compiler.
    val classLoader = getClassLoader(Seq(scalaCompiler, scalaLibrary, scalaReflect), false)

    new ScalaInstance(scalaVersion, classLoader, scalaLibrary, scalaCompiler, scalaJars, None)
  }

  /** Implementation copied from IncrementalCompiler in sbt integration. */
  def compileInterfaceJar(
    sourceJar: File,
    targetJar: File,
    interfaceJar: File,
    instance: ScalaInstance,
    sbtLogger: Logger
  ): Unit = {
    val raw = new RawCompiler(instance, ClasspathOptions.auto, sbtLogger)
    AnalyzingCompiler.compileSources(
      Seq(sourceJar),
      targetJar,
      // TODO: What if we put the reflect jar here?
      Seq(interfaceJar),
      "sbt compiler",
      raw,
      sbtLogger
    )
  }

  override def executeValidated(
    name: Option[String],
    stringArguments: Map[String, Seq[StringArtifact]],
    fileArguments: Map[String, FilesArtifact],
    buildState: BuildState
  ): Artifact = {
    val targetDirectory = buildState.targetDirectory

    // TODO: Default from a task arg.
    val extraArgs = Seq("-g:line", "-unchecked")

    val sourceFiles = fileArguments("src").values

    val scalaVersion = stringArguments.get("scalaVersion") map { _.head.value } getOrElse {
      "2.10.5"
    }

    // So, the below is some nasty stuff for interfacing with the SBT internals.
    //
    // Most of SBT is compiled into a version-agnostic set of libraries (they will run on any
    // version of Scala). However, it needs to recompile the core incremental compiler / reflection
    // interface anew for each Scala library version, since these APIs change much more frequently
    // than do the rest of Scala.

    println(s"Getting ScalaInstance for scala $scalaVersion")

    // TODO: Get scala home in some other way. SBT uses the sbt launcher to get this.
    val scalaInstance =
      getScalaInstance(scalaVersion, new File(s"/Users/Kinkead/lib/scala-$scalaVersion"))

    println(s"got ScalaInstance = $scalaInstance")

    // The directory SBT will write compiler output to. This is used in a couple of places.
    val outputDirectory = CompileOutput(targetDirectory.toFile)

    val analysisFile = targetDirectory.resolve("analysis.txt").toFile
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

    // TODO: Configure this correctly! You can pass in a stream.
    // http://www.scala-sbt.org/0.13.7/api/index.html#sbt.ConsoleLogger$
    val sbtLogger = {
      val logger = ConsoleLogger()
      logger.setLevel(sbt.Level.Debug)
      logger
    }

    // TODO: SBT sets the compiler thread's ClassLoader (via setContextClassLoader) in order to make
    // these resolve to the correct thing.
    // The ClassLoader itself is just a URLClassLoader wrapping the target Scala library classpath,
    // with the parent loader returned from ScalaProvider.loader.
    // ScalaProvider.loader is an instance of BootFilteredLoader, which does two things: delegates
    // getResource(s) calls to the root class loader, and throws exceptions when requested to load
    // embedded classes (ivy, sbt, scala, and fjbg).

    // CompilerInterfaceProvider is a special shiv that lets SBT dynamically compile new versions of
    // the SBT compiler from source.
    //
    // We *might* be able to get away with using the stale Scala jar, but might have to recompile
    // it.
    val jarDest = targetDirectory.resolve(s"compiler-interface-$scalaVersion.jar").toFile
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
        singleOutput = targetDirectory.toFile,
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

    val files = Resolver.resolvePath("**.class", targetDirectory.toUri)

    FilesArtifact(files)
  }
}
