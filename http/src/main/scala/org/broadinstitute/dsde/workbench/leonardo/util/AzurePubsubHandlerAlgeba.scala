package org.broadinstitute.dsde.workbench.leonardo.util

import org.broadinstitute.dsde.workbench.leonardo.{
  AppContext,
  PersistentDisk,
  Runtime,
  RuntimeConfig,
  RuntimeImage,
  WorkspaceId
}
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.leonardo.dao.WsmJobId
import org.broadinstitute.dsde.workbench.leonardo.monitor.LeoPubsubMessage.{
  CreateAzureRuntimeMessage,
  DeleteAzureRuntimeMessage
}

trait AzureAlgebra[F[_]] {

  /** Creates an Azure VM but doesn't wait for its completion.
   * This includes creation of all child Azure resources (disk, network, ip), and assumes these are created syncronously
   * */
  def createAndPollRuntime(msg: CreateAzureRuntimeMessage)(implicit ev: Ask[F, AppContext]): F[Unit]

  def deleteAndPollRuntime(msg: DeleteAzureRuntimeMessage)(implicit ev: Ask[F, AppContext]): F[Unit]
}

final case class CreateAzureRuntimeParams(workspaceId: WorkspaceId,
                                          runtime: Runtime,
                                          runtimeConfig: RuntimeConfig.AzureVmConfig,
                                          pd: PersistentDisk,
                                          vmImage: RuntimeImage)
final case class DeleteAzureRuntimeParams(workspaceId: WorkspaceId, runtime: Runtime)

final case class PollRuntimeParams(workspaceId: WorkspaceId, runtime: Runtime, jobId: WsmJobId)