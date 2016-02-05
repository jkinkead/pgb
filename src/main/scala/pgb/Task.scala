package pgb

/** A single task in the build system.
  * @tparam O the type of the task's output
  */
trait Task {
  /** Return the type of this task. This is the name that would appear in a build file. */
  def taskType: String

  /** Return the artifact type of this task. */
  def artifactType: Artifact.Type

  /** Return all of the expected task argument's types. Any arguments of the names returned will be
    * implicitly converted to the type they are paired with.
    * @return the expected names and types of this task's arguments
    */
  def argumentTypes: Map[String, Input.Type] = Map.empty

  /** If this returns false, unexpected arguments (those not in `argumentTypes`) will be considered
    * errors when parsing.
    */
  def allowUnknownArguments: Boolean = false

  /** Runs a task with arguments specified in the build file, and the result of the previous run, if
    * it's still present. This will be invoked only after all inputs in all arguments have completed
    * execution.
    * @param name the special name argument for the task
    * @param buildState the build state that this task has access to
    * @param arguments all non-name arguments for the task
    * @return the output(s) for this task
    * @throws ConfigException if there was a problem with the task's arguments or name
    * @throws ExecutionException if there was a problem executing the task
    */
  def execute(
    name: Option[String],
    arguments: Map[String, Seq[Artifact]],
    buildState: BuildState
  ): Artifact = {
    // TODO: The error messages produced below are not going to be very helpful. This should use the
    // build state to throw a ConfigException with the build file line.
    val (stringArguments, taskArguments) = {
      arguments.foldLeft(
        (Map.empty[String, Seq[StringArtifact]], Map.empty[String, FilesArtifact])
      ) {
          case ((stringArguments, fileArguments), (name, values)) => {
            val (validatedStrings, validatedFiles) = validateArgument(name, values, buildState)
            val newStringArguments = if (validatedStrings.nonEmpty) {
              stringArguments + (name -> validatedStrings)
            } else {
              stringArguments
            }
            val newFileArguments = if (validatedFiles.values.nonEmpty) {
              fileArguments + (name -> validatedFiles)
            } else {
              fileArguments
            }
            (newStringArguments, newFileArguments)
          }
        }
    }
    executeValidated(name, stringArguments, taskArguments, buildState)
  }

  /** Runs a task with fully-validated arguments specified.  This will be invoked only after all
    * inputs in all arguments have completed execution, and is the method an implementing task
    * should override.
    * @param name the special name argument for the task
    * @param buildState the build state that this task has access to
    * @param arguments all non-name arguments for the task
    * @return the output(s) for this task
    * @throws ConfigException if there was a problem with the task's arguments or name
    * @throws ExecutionException if there was a problem executing the task
    */
  def executeValidated(
    name: Option[String],
    stringArguments: Map[String, Seq[StringArtifact]],
    fileArguments: Map[String, FilesArtifact],
    buildState: BuildState
  ): Artifact

  /** Validates a single argument. The argument will already have been validated by declared type -
    * the `values` sequence will only contain output from tasks of the expected type from
    * `argumentTypes`.
    * @param name the name of the argument
    * @param the value of the argument
    * @return the new string arguments paired with the new file arguments produced from the
    *     validation
    * @throws ConfigException if any of the arguments are invalid
    */
  def validateArgument(
    name: String,
    values: Seq[Artifact],
    buildState: BuildState
  ): (Seq[StringArtifact], FilesArtifact) = {
    // Partition into string and file artifacts, flattening the files found.
    val strings: Seq[StringArtifact] = values collect { case string: StringArtifact => string }
    val files: FilesArtifact = (values collect {
      case file: FilesArtifact => file
    }).foldLeft(FilesArtifact(Seq.empty)) { (aggregate, file) =>
      aggregate.copy(values = aggregate.values ++ file.values)
    }

    argumentTypes.get(name) match {
      case None => {
        // Return the partitioned types.
        (strings, files)
      }
      case Some(inputType) => {
        inputType.artifactType match {
          case Artifact.StringType => {
            if (files.values.nonEmpty) {
              buildState.configException(s"""argument "$name" has file arguments: [${
                files.values.mkString(", ")
              }]""")
            }
          }
          case Artifact.FileType => {
            if (strings.nonEmpty) {
              buildState.configException(s"""argument "$name" has string arguments: ["${
                strings.mkString("""", """")
              }"]""")
            }
          }
          case Artifact.NoType => // Ignore. Although maybe raise an error?
        }
      }
    }
    (strings, files)
  }
}

/** Task with file output. */
trait FileOutputTask extends Task {
  final override val artifactType: Artifact.Type = Artifact.FileType

  // TODO: Consider adding helper execute method here so that subclasses can ignore FilesArtifact.
}

/** Task with string output. */
trait StringOutputTask extends Task {
  final override val artifactType: Artifact.Type = Artifact.StringType

  // TODO: Consider adding helper execute method here so that subclasses can ignore StringArtifact.
}
