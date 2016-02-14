package pgb.path

import pgb.ConfigException

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.{ DefaultRepositorySystemSession, RepositorySystem }
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.installation.InstallRequest
import org.eclipse.aether.repository.{ LocalRepository, RemoteRepository }
import org.eclipse.aether.resolution.{ ArtifactRequest, ArtifactResult }
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory

import java.io.File
import java.net.URI

import scala.collection.JavaConverters._

/** Handler for pgb-style maven URIs.  These are of the format
  * {{{mvn:/$groupdId/$artifactId/$version/$packaging/$classifier}}}. Note that this is nearly
  * idendical to Maven's coordinates, except its ordering is closer to hierarchical, and it uses URI
  * path separators instead of colons.
  */
class MavenHandler {
  val Slash = "/".r

  def remoteRepository(name: String, url: String): RemoteRepository = {
    new RemoteRepository.Builder(name, "default", url).build
  }

  // TODO: Allow this to be injected or something. We need it expandable.
  // TODO: Figure out if we need to also use a local repository.
  val repositories = Seq(
    remoteRepository("Maven Central", "https://repo1.maven.org/maven2")
  //remoteRepository("Sonatype Snapshots", "https://oss.sonatype.org/content/repositories/snapshots")
  /*
    remoteRepository("Sonatype", "https://oss.sonatype.org/content/repositories/public"),
    remoteRepository("Sonatype Releases", "https://oss.sonatype.org/content/repositories/releases"),
    remoteRepository("Typesafe Ivy Releases", "https://repo.typesafe.com/typesafe/ivy-releases"),
    remoteRepository("SBT Plugins", "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases"),
    remoteRepository("Bintray JCenter", "https://jcenter.bintray.com/")
    */
  )

  /** Aether repository system for accessing maven repositories. */
  val repositorySystem: RepositorySystem = {
    // The locator used to find jars.
    val locator = MavenRepositorySystemUtils.newServiceLocator
    locator.addService(
      classOf[RepositoryConnectorFactory],
      classOf[BasicRepositoryConnectorFactory]
    )
    locator.addService(classOf[TransporterFactory], classOf[FileTransporterFactory])
    locator.addService(classOf[TransporterFactory], classOf[HttpTransporterFactory])

    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      override def serviceCreationFailed(
        requestedType: Class[_],
        implementationType: Class[_],
        exception: Throwable
      ): Unit = {
        // TODO: Make this do something more sensible.
        throw exception;
      }
    })

    locator.getService(classOf[RepositorySystem])
  }

  val session: DefaultRepositorySystemSession = {
    val session = MavenRepositorySystemUtils.newSession

    // Destination repository.
    // TODO: Make global; add a lock.
    val localRepo = new LocalRepository("target/local-repo")
    session.setLocalRepositoryManager(
      repositorySystem.newLocalRepositoryManager(session, localRepo)
    )

    // Listeners can be installed like so:
    //   session.setTransferListener(new TransferListener)
    //   session.setRepositoryListener(new RepositoryListener)

    session
  }

  def resolveAbolutePath(mavenUri: URI): File = {
    require(mavenUri.getScheme == "mvn")

    val pathElements = Slash.split(mavenUri.getSchemeSpecificPart)
    if (pathElements.length < 3) {
      throw new ConfigException(s"maven URI must have at least three path elements: $mavenUri")
    }
    val groupId = pathElements(0)
    val artifactId = pathElements(1)
    val version = pathElements(2)
    // "jar" is the default packaging.
    val packaging = if (pathElements.length > 3) { pathElements(3) } else { "jar" }
    val jarArtifact = pathElements.length match {
      case 3 | 4 => new DefaultArtifact(groupId, artifactId, packaging, version)
      case 5 => {
        val classifier = pathElements(4)
        new DefaultArtifact(groupId, artifactId, classifier, packaging, version)
      }
      case _ => throw new ConfigException(
        s"maven URI must have between three and five path elements: $mavenUri"
      )
    }

    val artifactRequest = new ArtifactRequest
    artifactRequest.setArtifact(jarArtifact)
    artifactRequest.setRepositories(repositories.asJava)

    val artifactResult: ArtifactResult = repositorySystem.resolveArtifact(session, artifactRequest)

    val artifact = artifactResult.getArtifact()

    println(s"$artifact resolved to  ${artifact.getFile}")

    /*
    // The installation example installs a jar in the local dir into the installed location. Not
    // sure why that's useful.
    val installRequest = new InstallRequest
    installRequest.addArtifact(jarArtifact)

    repositorySystem.install(session, installRequest)
    */

    artifact.getFile
  }
}
