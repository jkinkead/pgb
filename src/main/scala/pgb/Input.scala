package pgb

/** Holds input validation types. */
object Input {
  /** An argument type.
    * @param isRequired true if the argument is required
    * @param allowMultiples true if the argument can contain multiple values
    * @param artifactType the expected type of this argument
    */
  case class Type(isRequired: Boolean, allowMultiples: Boolean, artifactType: Artifact.Type)

  val OptionalString = Type(isRequired = false, allowMultiples = false, Artifact.StringType)
  val OptionalStringList = Type(isRequired = false, allowMultiples = true, Artifact.StringType)
  val RequiredString = Type(isRequired = true, allowMultiples = false, Artifact.StringType)
  val RequiredStringList = Type(isRequired = true, allowMultiples = true, Artifact.StringType)
  val OptionalFile = Type(isRequired = false, allowMultiples = false, Artifact.FileType)
  val OptionalFileList = Type(isRequired = false, allowMultiples = true, Artifact.FileType)
  val RequiredFile = Type(isRequired = true, allowMultiples = false, Artifact.FileType)
  val RequiredFileList = Type(isRequired = true, allowMultiples = true, Artifact.FileType)
}
