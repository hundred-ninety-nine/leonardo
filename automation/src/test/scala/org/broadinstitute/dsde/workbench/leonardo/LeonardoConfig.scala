package org.broadinstitute.dsde.workbench.leonardo

import com.google.pubsub.v1.ProjectTopicName
import org.broadinstitute.dsde.workbench.config.CommonConfig
import org.broadinstitute.dsde.workbench.google2.{Location, PublisherConfig}

import java.util.UUID

object LeonardoConfig extends CommonConfig {
  private val leonardo = config.getConfig("leonardo")
  private val azure = config.getConfig("azure")
  private val gcs = config.getConfig("gcs")

  object Leonardo {
    val apiUrl: String = leonardo.getString("apiUrl")
    val notebooksServiceAccountEmail: String = leonardo.getString("notebooksServiceAccountEmail")
    val rImageUrl: String = leonardo.getString("rImageUrl")
    val pythonImageUrl: String = leonardo.getString("pythonImageUrl")
    val hailImageUrl: String = leonardo.getString("hailImageUrl")
    val gatkImageUrl: String = leonardo.getString("gatkImageUrl")
    val aouImageUrl: String = leonardo.getString("aouImageUrl")
    val baseImageUrl: String = leonardo.getString("baseImageUrl")
    val rstudioBioconductorImage =
      ContainerImage(leonardo.getString("rstudioBioconductorImageUrl"), ContainerRegistry.GCR)

    private val topic = ProjectTopicName.of(gcs.getString("serviceProject"), leonardo.getString("topicName"))
    val location: Location = Location(leonardo.getString("location"))

    val publisherConfig: PublisherConfig = PublisherConfig(GCS.pathToQAJson, topic)

    val tenantId = azure.getString("tenantId")
    val subscriptionId = azure.getString("subscriptionId")
    val managedResourceGroup = azure.getString("managedResourceGroup")
    val workspaceId = WorkspaceId(UUID.fromString(azure.getString("workspaceId")))
  }

  // for qaEmail and pathToQAPem and pathToQAJson
  object GCS extends CommonGCS {
    val pathToQAJson = gcs.getString("qaJsonFile")
  }

  // TODO: this should be updated once we're able to run azure automation tests as part of CI
  object WSM {
    val wsmUri: String = "https://workspace.dsde-dev.broadinstitute.org"
  }

  // for NotebooksWhitelisted
  object Users extends CommonUsers
}
